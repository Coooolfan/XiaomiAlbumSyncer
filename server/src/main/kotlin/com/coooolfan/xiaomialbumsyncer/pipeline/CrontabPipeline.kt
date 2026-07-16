package com.coooolfan.xiaomialbumsyncer.pipeline

import com.coooolfan.xiaomialbumsyncer.controller.SystemConfigController.Companion.NORMAL_SYSTEM_CONFIG
import com.coooolfan.xiaomialbumsyncer.model.AlbumTimeline
import com.coooolfan.xiaomialbumsyncer.model.Crontab
import com.coooolfan.xiaomialbumsyncer.model.CrontabHistoryDetail
import com.coooolfan.xiaomialbumsyncer.pipeline.stages.DownloadStage
import com.coooolfan.xiaomialbumsyncer.pipeline.stages.ExifProcessingStage
import com.coooolfan.xiaomialbumsyncer.pipeline.stages.FileTimeStage
import com.coooolfan.xiaomialbumsyncer.pipeline.stages.VerificationStage
import com.coooolfan.xiaomialbumsyncer.service.AssetService
import com.coooolfan.xiaomialbumsyncer.service.CrontabService
import com.coooolfan.xiaomialbumsyncer.service.NotifyService
import com.coooolfan.xiaomialbumsyncer.service.SystemConfigService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.noear.solon.annotation.Managed
import org.slf4j.LoggerFactory

/**
 * 计划任务流水线管理器
 */
@Managed
class CrontabPipeline(
    private val downloadStage: DownloadStage,
    private val verificationStage: VerificationStage,
    private val exifProcessingStage: ExifProcessingStage,
    private val fileTimeStage: FileTimeStage,
    private val systemConfigService: SystemConfigService,
    private val assetService: AssetService,
    private val crontabService: CrontabService,
    private val notifyService: NotifyService,
) {

    private val log = LoggerFactory.getLogger(CrontabPipeline::class.java)

    // 从 Crontab 创建一个任务
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun execute(
        crontab: Crontab,
    ) {
        // 对资产的刷新操作作为独立步骤执行，不混入后续的并发流，避免状态管理复杂化
        val crontabHistory = crontabService.createCrontabHistory(crontab)

        // 对 crontab.albums 进行同步操作, 重新刷新这些相册的所有 Asset 取到上次的 CrontabHistory 的 timelineSnapshot
        val albumTimelinesHistory = crontabService.getAlbumTimelinesHistory(crontabHistory)

        // 仅在上次有记录且相册列表未变更的情况下，才使用时间线对比模式
        // 理论上相册列表变动不影响逻辑正确，但是会导致实际发起的查询大于请求完整刷新模式，所以还是要求相册列表一致
        val timelineDiffUsable = checkTimelineDiffUsable(crontab, albumTimelinesHistory)
        if (timelineDiffUsable != null) {
            if (crontab.config.diffByTimeline) log.warn("时间线对比模式不可用，原因：$timelineDiffUsable，将使用完整刷新模式")
            assetService.refreshAssetsFull(crontab, crontabHistory)
        } else {
            log.info("时间线对比模式可用，仅对有变更的日期进行刷新")
            assetService.refreshAssetsByDiffTimeline(crontab, crontabHistory, albumTimelinesHistory)
        }

        // 记录一下，以后如果支持恢复暂停的任务可以从这开始
        crontabService.finishCrontabHistoryFetchedAllAssets(crontabHistory)

        val systemConfig = systemConfigService.getConfig(NORMAL_SYSTEM_CONFIG)

        var total = 0
        var success = 0
        var pipeline: Flow<CrontabHistoryDetail> = channelFlow {
            var currentRows: Int
            var lastId = 0L
            val pageSize = crontab.config.fetchFromDbSize

            do {
                // 1. 查询需要下载的资产(已经从远程同步好了本地数据库中的资产)
                val assets = assetService.getAssetsUndownloadByCrontab(
                    crontabHistory.crontab,
                    pageSize,
                    lastId
                )

                val details = mutableListOf<CrontabHistoryDetail>()

                assets.forEach { asset ->
                    val detail = CrontabHistoryDetail.init(crontabHistory, asset)
                    details.add(detail)
                }

                // 2. 保存 CrontabHistoryDetail，落库
                val details2Emit = crontabService.insertCrontabHistoryDetails(details)

                currentRows = assets.size
                if (currentRows != 0) lastId = assets.last().id

                details2Emit.forEach { send(it) }
            } while (currentRows > 0)
        }.onEach {
            total++
        }.processStage("下载", crontab.config.downloaders, downloadStage::process)

        // init 已将关闭功能对应的完成标记设为 true，无需再让每个资产穿过空的并发阶段。
        if (crontab.config.checkSha1) {
            pipeline = pipeline.processStage("校验", crontab.config.verifiers, verificationStage::process)
        }
        if (crontab.config.rewriteExifTime) {
            pipeline = pipeline.processStage("EXIF", crontab.config.exifProcessors) { context ->
                exifProcessingStage.process(context, systemConfig)
            }
        }
        if (crontab.config.rewriteFileSystemTime) {
            pipeline = pipeline.processStage("文件时间", crontab.config.fileTimeWorkers, fileTimeStage::process)
        }

        pipeline.onEach { context ->
            success++
            log.info("资产 {} 处理完成", context.asset.id)
        }.collect()

        crontabService.finishCrontabHistory(crontabHistory)

        // 异步发送通知
        if (crontab.config.notify)
            CoroutineScope(Dispatchers.IO).launch { notifyService.send(crontab, success, total) }

        log.info("Crontab {} 的流水线执行完毕, 成功 {}/{}", crontab.id, success, total)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun Flow<CrontabHistoryDetail>.processStage(
        stage: String,
        concurrency: Int,
        process: (CrontabHistoryDetail) -> CrontabHistoryDetail,
    ): Flow<CrontabHistoryDetail> {
        require(concurrency > 0) { "$stage 阶段并发数必须大于 0" }
        if (concurrency == 1) {
            return transform { context ->
                processStageContext(stage, context, process)?.let { emit(it) }
            }
        }
        return flatMapMerge(concurrency) { context ->
            flow {
                processStageContext(stage, context, process)?.let { emit(it) }
            }
        }
    }

    private suspend fun processStageContext(
        stage: String,
        context: CrontabHistoryDetail,
        process: (CrontabHistoryDetail) -> CrontabHistoryDetail,
    ): CrontabHistoryDetail? = try {
        process(context)
    } catch (err: Throwable) {
        // Flow.catch 不处理协程取消；串行快路径也保持相同语义。
        currentCoroutineContext().ensureActive()
        saveAndLogErr(stage, context, err)
        null
    }

    private fun saveAndLogErr(stageStr: String, context: CrontabHistoryDetail, err: Throwable) {
        val errMsg = "资产 ${context.asset.id} 的${stageStr}阶段处理失败, 将跳过后续处理."
        log.error(errMsg, err)
        try {
            crontabService.updateDetailMessage(context.id, err.message ?: errMsg)
        } catch (persistError: Exception) {
            log.error("记录资产 ${context.asset.id} 的失败信息时发生异常，流水线将继续处理其他资产", persistError)
        }
    }


    private fun checkTimelineDiffUsable(crontab: Crontab, albumTimelinesHistory: Map<Long, AlbumTimeline>): String? {
        if (!crontab.config.diffByTimeline) {
            return "时间线对比模式未打开"
        }
        if (albumTimelinesHistory.isEmpty()) {
            return "该任务的最新执行记录无可用于对比的时间线数据"
        }

        val crontabAlbumRemoteIds = crontab.albums.mapTo(mutableSetOf()) { it.remoteId }
        if (crontabAlbumRemoteIds.contains(-1L)) {
            return "\"录音\"不支持时间线对比"
        }
        if (albumTimelinesHistory.keys != crontabAlbumRemoteIds) {
            return "相册列表有变更"
        }
        return null
    }
}
