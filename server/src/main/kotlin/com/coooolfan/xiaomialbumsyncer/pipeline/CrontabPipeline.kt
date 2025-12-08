package com.coooolfan.xiaomialbumsyncer.pipeline

import com.coooolfan.xiaomialbumsyncer.model.Crontab
import com.coooolfan.xiaomialbumsyncer.model.CrontabHistory
import com.coooolfan.xiaomialbumsyncer.pipeline.stages.DownloadStage
import com.coooolfan.xiaomialbumsyncer.pipeline.stages.ExifProcessingStage
import com.coooolfan.xiaomialbumsyncer.pipeline.stages.FileTimeStage
import com.coooolfan.xiaomialbumsyncer.pipeline.stages.VerificationStage
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.onEach
import org.noear.solon.annotation.Managed
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi

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

        val coordinator = PipelineCoordinator(request.tasks.size)

        request.tasks.asFlow()
            .flatMapMerge(concurrency.downloaders) { context ->
                downloadStage.process(context)
            }
            .flatMapMerge(concurrency.verifiers) { context ->
                verificationStage.process(context, downloadStage::process)
            }
            .flatMapMerge(concurrency.exifProcessors) { context ->
                exifProcessingStage.process(context)
            }
            .flatMapMerge(concurrency.fileTimeWorkers) { context ->
                fileTimeStage.process(context)
            }
            .onEach { context ->
                if (context.abandoned) {
                    log.warn("资源 {} 多次重试后被放弃", context.asset.id)
                } else {
                    log.debug("资源 {} 处理成功完成", context.asset.id)
                }
                coordinator.markCompleted()
            }
            .catch { ex ->
                log.error("流水线执行异常", ex)
                coordinator.markCompleted()
            }
            .collect()

        coordinator.awaitCompletion()
        log.info("Crontab {} 的流水线执行完毕", request.crontab.id)
    }
}

data class PipelineRequest(
    val crontab: Crontab,
    val crontabHistory: CrontabHistory,
    val tasks: List<AssetPipelineContext>,
)

data class PipelineConcurrency(
    val downloaders: Int = 4,
    val verifiers: Int = 2,
    val exifProcessors: Int = 1,
    val fileTimeWorkers: Int = 1,
)

class PipelineCoordinator(totalTasks: Int) {
    private val remaining = AtomicInteger(totalTasks)
    private val completion = CompletableDeferred<Unit>().apply {
        if (totalTasks == 0) complete(Unit)
    }

    fun markCompleted() {
        val left = remaining.decrementAndGet()
        if (left <= 0 && !completion.isCompleted) {
            completion.complete(Unit)
        }
    }

    suspend fun awaitCompletion() {
        completion.await()
    }
}
