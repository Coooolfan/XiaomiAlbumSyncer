package com.coooolfan.xiaomialbumsyncer.pipeline

import com.coooolfan.xiaomialbumsyncer.model.Crontab
import com.coooolfan.xiaomialbumsyncer.model.CrontabHistory
import com.coooolfan.xiaomialbumsyncer.pipeline.stages.AssetCollectStage
import com.coooolfan.xiaomialbumsyncer.pipeline.stages.DownloadStage
import com.coooolfan.xiaomialbumsyncer.pipeline.stages.ExifProcessingStage
import com.coooolfan.xiaomialbumsyncer.pipeline.stages.FileTimeStage
import com.coooolfan.xiaomialbumsyncer.pipeline.stages.VerificationStage
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import org.noear.solon.annotation.Managed
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.joinAll

/**
 * 计划任务流水线管理器
 */
@Managed
class CrontabPipeline(
    private val assetCollectStage: AssetCollectStage,
    private val downloadStage: DownloadStage,
    private val verificationStage: VerificationStage,
    private val exifProcessingStage: ExifProcessingStage,
    private val fileTimeStage: FileTimeStage,
) {

    private val log = LoggerFactory.getLogger(CrontabPipeline::class.java)

    suspend fun execute(
        request: PipelineRequest,
        concurrency: PipelineConcurrency = PipelineConcurrency(),
    ) {
        if (request.tasks.isEmpty()) {
            log.info("Crontab {} has no assets to process", request.crontab.id)
            return
        }

        val coordinator = PipelineCoordinator(request.tasks.size)

        coroutineScope {
            val downloadChannel = Channel<AssetPipelineContext>(Channel.UNLIMITED)
            val verificationChannel = Channel<AssetPipelineContext>(Channel.UNLIMITED)
            val exifChannel = Channel<AssetPipelineContext>(Channel.UNLIMITED)
            val fileTimeChannel = Channel<AssetPipelineContext>(Channel.UNLIMITED)

            val jobs = mutableListOf<Job>()
            jobs += assetCollectStage.start(this, request.tasks, downloadChannel)
            jobs += downloadStage.start(
                scope = this,
                downloadChannel = downloadChannel,
                verificationChannel = verificationChannel,
                workerCount = concurrency.downloaders,
                coordinator = coordinator
            )
            jobs += verificationStage.start(
                scope = this,
                downloadChannel = downloadChannel,
                verificationChannel = verificationChannel,
                exifChannel = exifChannel,
                workerCount = concurrency.verifiers,
                coordinator = coordinator
            )
            jobs += exifProcessingStage.start(
                scope = this,
                exifChannel = exifChannel,
                fileTimeChannel = fileTimeChannel,
                workerCount = concurrency.exifProcessors
            )
            jobs += fileTimeStage.start(
                scope = this,
                fileTimeChannel = fileTimeChannel,
                request = request,
                workerCount = concurrency.fileTimeWorkers,
                coordinator = coordinator
            )

            coordinator.awaitCompletion()

            downloadChannel.close()
            verificationChannel.close()
            exifChannel.close()
            fileTimeChannel.close()

            jobs.joinAll()
        }
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
