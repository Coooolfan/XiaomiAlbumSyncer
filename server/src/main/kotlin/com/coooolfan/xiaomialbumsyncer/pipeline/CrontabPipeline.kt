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
import com.coooolfan.xiaomialbumsyncer.service.SystemConfigService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import org.noear.solon.annotation.Managed
import org.slf4j.LoggerFactory
import kotlin.collections.toSet

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
        channelFlow {
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
                    val detail = CrontabHistoryDetail.init(crontabHistory, asset, crontabHistory.crontab.config)
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
        }.flatMapMerge(crontab.config.downloaders) { context ->
            flow {
                emit(downloadStage.process(context))
            }.catch {
                log.error("资源 {} 的下载失败, 将跳过后续处理", context.asset.id, it)
            }
        }.flatMapMerge(crontab.config.verifiers) { context ->
            flow {
                emit(verificationStage.process(context))
            }.catch {
                log.error("资源 {} 的校验失败, 将跳过后续处理", context.asset.id, it)
            }
        }.flatMapMerge(crontab.config.exifProcessors) { context ->
            flow {
                emit(exifProcessingStage.process(context, systemConfig))
            }.catch {
                log.error("资源 {} 的 EXIF 处理失败, 将跳过后续处理", context.asset.id, it)
            }
        }.flatMapMerge(crontab.config.fileTimeWorkers) { context ->
            flow {
                emit(fileTimeStage.process(context))
            }.catch {
                log.error("资源 {} 的文件时间阶段处理失败, 将跳过后续处理", context.asset.id, it)
            }
        }.onEach { context ->
            success++
            log.info("资源 {} 处理完成", context.asset.id)
        }.collect()

        crontabService.finishCrontabHistory(crontabHistory)

        log.info("Crontab {} 的流水线执行完毕, 成功 {}/{}", crontab.id, success, total)
    }


    private fun checkTimelineDiffUsable(crontab: Crontab, albumTimelinesHistory: Map<Long, AlbumTimeline>): String? {
        if (!crontab.config.diffByTimeline) {
            return "时间线对比模式未打开"
        }
        if (albumTimelinesHistory.isEmpty()) {
            return "该任务的最新执行记录无可用于对比的时间线数据"
        }
        if (albumTimelinesHistory.keys != crontab.albumIds.toSet()) {
            return "相册列表有变更"
        }
        if (crontab.albumIds.contains(-1L)) {
            return "\"录音\"不支持时间线对比"
        }
        return null
    }
}
