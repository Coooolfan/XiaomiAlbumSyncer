package com.coooolfan.xiaomialbumsyncer.pipeline

import com.coooolfan.xiaomialbumsyncer.model.Crontab
import com.coooolfan.xiaomialbumsyncer.model.CrontabHistory
import com.coooolfan.xiaomialbumsyncer.model.SystemConfig
import com.coooolfan.xiaomialbumsyncer.pipeline.stages.DownloadStage
import com.coooolfan.xiaomialbumsyncer.pipeline.stages.ExifProcessingStage
import com.coooolfan.xiaomialbumsyncer.pipeline.stages.FileTimeStage
import com.coooolfan.xiaomialbumsyncer.pipeline.stages.VerificationStage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
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
) {

    private val log = LoggerFactory.getLogger(CrontabPipeline::class.java)

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun execute(
        request: PipelineRequest,
        concurrency: PipelineConcurrency = PipelineConcurrency(),
    ) {
        if (request.tasks.isEmpty()) {
            log.info("Crontab {} 无待处理的资源", request.crontab.id)
            return
        }

        request.tasks.asFlow()
            .flatMapMerge(concurrency.downloaders) { context ->
                flow {
                    emit(downloadStage.process(context))
                }.catch {
                    log.error("资源 {} 的下载失败, 将跳过后续处理", context.asset.id, it)
                }
            }
            .flatMapMerge(concurrency.verifiers) { context ->
                flow {
                    emit(verificationStage.process(context))
                }.catch {
                    log.error("资源 {} 的校验失败, 将跳过后续处理", context.asset.id, it)
                }
            }
            .flatMapMerge(concurrency.exifProcessors) { context ->
                flow {
                    emit(exifProcessingStage.process(context, request.systemConfig))
                }.catch {
                    log.error("资源 {} 的 EXIF 处理失败, 将跳过后续处理", context.asset.id, it)
                }
            }
            .flatMapMerge(concurrency.fileTimeWorkers) { context ->
                flow {
                    emit(fileTimeStage.process(context))
                }.catch {
                    log.error("资源 {} 的文件时间阶段处理失败, 将跳过后续处理", context.asset.id, it)
                }
            }
            .onEach { context ->
                log.info("资源 {} 处理完成", context.asset.id)
            }
            .collect()

        log.info("Crontab {} 的流水线执行完毕", request.crontab.id)
    }
}

data class PipelineRequest(
    val crontab: Crontab,
    val systemConfig: SystemConfig,
    val crontabHistory: CrontabHistory,
    val tasks: List<AssetPipelineContext>,
)

data class PipelineConcurrency(
    val downloaders: Int = 4,
    val verifiers: Int = 2,
    val exifProcessors: Int = 1,
    val fileTimeWorkers: Int = 1,
)
