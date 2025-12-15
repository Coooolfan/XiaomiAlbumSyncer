package com.coooolfan.xiaomialbumsyncer.pipeline

import com.coooolfan.xiaomialbumsyncer.model.Crontab
import com.coooolfan.xiaomialbumsyncer.model.CrontabHistoryDetail
import com.coooolfan.xiaomialbumsyncer.model.SystemConfig
import com.coooolfan.xiaomialbumsyncer.pipeline.stages.DownloadStage
import com.coooolfan.xiaomialbumsyncer.pipeline.stages.ExifProcessingStage
import com.coooolfan.xiaomialbumsyncer.pipeline.stages.FileTimeStage
import com.coooolfan.xiaomialbumsyncer.pipeline.stages.VerificationStage
import com.coooolfan.xiaomialbumsyncer.service.AssetService
import com.coooolfan.xiaomialbumsyncer.service.CrontabService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
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
    private val assetService: AssetService,
    private val crontabService: CrontabService,
) {

    private val log = LoggerFactory.getLogger(CrontabPipeline::class.java)

    // 从 Crontab 创建一个任务
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun execute(
        request: PipelineRequest,
        concurrency: PipelineConcurrency = PipelineConcurrency(),
    ) {
        // 对资产的刷新操作作为独立步骤执行，不混入后续的并发流，避免状态管理复杂化
        val crontabHistory = crontabService.createCrontabHistory(request.crontab)

        // 对 crontab.albums 进行同步操作, 重新刷新这些相册的所有 Asset 取到上次的 CrontabHistory 的 timelineSnapshot
        val albumTimelinesHistory = crontabService.getAlbumTimelinesHistory(crontabHistory)

        // 仅在上次有记录且相册列表未变更的情况下，才使用时间线对比模式
        // 理论上相册列表变动不影响逻辑正确，但是会导致实际发起的查询大于请求完整刷新模式，所以还是要求相册列表一致
        if (request.crontab.config.diffByTimeline && albumTimelinesHistory.isNotEmpty() && albumTimelinesHistory.keys == request.crontab.albumIds.toSet()) {
            log.info("时间线对比模式可用，仅对有变更的日期进行刷新")
            assetService.refreshAssetsByDiffTimeline(request.crontab, crontabHistory, albumTimelinesHistory)
        } else {
            if (request.crontab.config.diffByTimeline)
                log.warn("时间线对比模式不可用，原因：${if (albumTimelinesHistory.isEmpty()) "该任务的最新执行记录无可用于对比的时间线数据" else "相册列表有变更"}，将使用完整刷新模式")
            assetService.refreshAssetsFull(request.crontab, crontabHistory)
        }

        // 记录一下，以后如果支持恢复暂停的任务可以从这开始
        crontabService.finishCrontabHistoryFetchedAllAssets(crontabHistory)

        var total = 0
        var success = 0
        listOf(crontabHistory).asFlow()
        .transform {
            var currentRows: Int
            var pageIndex = 0
            val pageSize = 10

            do {
                // 1. 查询需要下载的资产(已经从远程同步好了本地数据库中的资产)
                val assets = assetService.getAssetsUndownloadByCrontab(it.crontab, pageIndex, pageSize)

                val details = mutableListOf<CrontabHistoryDetail>()
                val contexts = mutableListOf<AssetPipelineContext>()

                assets.forEach { asset ->
                    val detail = CrontabHistoryDetail.init(it, asset)
                    val context = AssetPipelineContext(asset, it.crontab.config, detail)
                    details.add(detail)
                    contexts.add(context)
                }

                // 2. 保存 CrontabHistoryDetail，落库
                crontabService.insertCrontabHistoryDetails(details)

                currentRows = assets.size
                pageIndex++

                emitAll(contexts.asFlow())
            } while (currentRows > 0)
        }.onEach {
            total++
        }.flatMapMerge(concurrency.downloaders) { context ->
            flow {
                emit(downloadStage.process(context))
            }.catch {
                log.error("资源 {} 的下载失败, 将跳过后续处理", context.asset.id, it)
            }
        }.flatMapMerge(concurrency.verifiers) { context ->
            flow {
                emit(verificationStage.process(context))
            }.catch {
                log.error("资源 {} 的校验失败, 将跳过后续处理", context.asset.id, it)
            }
        }.flatMapMerge(concurrency.exifProcessors) { context ->
            flow {
                emit(exifProcessingStage.process(context, request.systemConfig))
            }.catch {
                log.error("资源 {} 的 EXIF 处理失败, 将跳过后续处理", context.asset.id, it)
            }
        }.flatMapMerge(concurrency.fileTimeWorkers) { context ->
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

        log.info("Crontab {} 的流水线执行完毕, 成功 {}/{}", request.crontab.id, success, total)
    }
}

data class PipelineRequest(
    val crontab: Crontab,
    val systemConfig: SystemConfig,
)

data class PipelineConcurrency(
    val downloaders: Int = 4,
    val verifiers: Int = 2,
    val exifProcessors: Int = 1,
    val fileTimeWorkers: Int = 1,
)
