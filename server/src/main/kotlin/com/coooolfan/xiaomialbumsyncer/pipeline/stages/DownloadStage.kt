package com.coooolfan.xiaomialbumsyncer.pipeline.stages

import com.coooolfan.xiaomialbumsyncer.model.CrontabHistoryDetail
import com.coooolfan.xiaomialbumsyncer.model.downloadCompleted
import com.coooolfan.xiaomialbumsyncer.model.filePath
import com.coooolfan.xiaomialbumsyncer.model.id
import com.coooolfan.xiaomialbumsyncer.pipeline.AssetPipelineContext
import com.coooolfan.xiaomialbumsyncer.pipeline.PipelineCoordinator
import com.coooolfan.xiaomialbumsyncer.xiaomicloud.XiaoMiApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.noear.solon.annotation.Managed
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

/**
 * 下载阶段处理器
 */
@Managed
class DownloadStage(
    private val api: XiaoMiApi,
    private val sql: KSqlClient,
) {

    private val log = LoggerFactory.getLogger(DownloadStage::class.java)

    fun start(
        scope: CoroutineScope,
        downloadChannel: Channel<AssetPipelineContext>,
        verificationChannel: Channel<AssetPipelineContext>,
        workerCount: Int,
        coordinator: PipelineCoordinator,
    ): Job = scope.launch {
        repeat(workerCount) {
            launch {
                for (context in downloadChannel) {
                    handleDownload(context, downloadChannel, verificationChannel, coordinator)
                }
            }
        }
    }

    private suspend fun handleDownload(
        context: AssetPipelineContext,
        downloadChannel: Channel<AssetPipelineContext>,
        verificationChannel: Channel<AssetPipelineContext>,
        coordinator: PipelineCoordinator,
    ) {
        val detailId = context.detailId
        if (detailId == null) {
            log.error("Missing detail record for asset {}", context.asset.id)
            coordinator.markCompleted()
            return
        }

        try {
            val targetPath = context.targetPath
            targetPath.parent?.let { Files.createDirectories(it) }
            val exists = context.skipExistingFile && Files.exists(targetPath)
            val downloadedPath = if (exists) targetPath else api.downloadAsset(context.asset, targetPath)

            context.downloadedPath = downloadedPath
            context.lastError = null

            markDownloadCompleted(detailId, downloadedPath)
            verificationChannel.send(context)
        } catch (ex: Exception) {
            log.error("Download failed for asset {}", context.asset.id, ex)
            handleRetry(context, downloadChannel, coordinator, ex)
        }
    }

    private fun markDownloadCompleted(detailId: Long, filePath: Path) {
        sql.executeUpdate(CrontabHistoryDetail::class) {
            set(table.downloadCompleted, true)
            set(table.filePath, filePath.toString())
            where(table.id eq detailId)
        }
    }

    private suspend fun handleRetry(
        context: AssetPipelineContext,
        downloadChannel: Channel<AssetPipelineContext>,
        coordinator: PipelineCoordinator,
        error: Throwable,
    ) {
        context.retry += 1
        context.lastError = error
        context.downloadedPath = null

        if (context.retry >= context.maxRetry) {
            log.error("Abandon asset {} after {} retries", context.asset.id, context.retry)
            coordinator.markCompleted()
            return
        }

        downloadChannel.send(context)
    }
}
