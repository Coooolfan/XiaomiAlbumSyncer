package com.coooolfan.xiaomialbumsyncer.pipeline.stages

import com.coooolfan.xiaomialbumsyncer.model.CrontabHistoryDetail
import com.coooolfan.xiaomialbumsyncer.model.id
import com.coooolfan.xiaomialbumsyncer.model.sha1Verified
import com.coooolfan.xiaomialbumsyncer.pipeline.AssetPipelineContext
import com.coooolfan.xiaomialbumsyncer.pipeline.PipelineCoordinator
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
import java.security.MessageDigest

/**
 * 校验阶段处理器
 */
@Managed
class VerificationStage(
    private val sql: KSqlClient,
) {

    private val log = LoggerFactory.getLogger(VerificationStage::class.java)

    fun start(
        scope: CoroutineScope,
        downloadChannel: Channel<AssetPipelineContext>,
        verificationChannel: Channel<AssetPipelineContext>,
        exifChannel: Channel<AssetPipelineContext>,
        workerCount: Int,
        coordinator: PipelineCoordinator,
    ): Job = scope.launch {
        repeat(workerCount) {
            launch {
                for (context in verificationChannel) {
                    handleVerification(context, downloadChannel, exifChannel, coordinator)
                }
            }
        }
    }

    private suspend fun handleVerification(
        context: AssetPipelineContext,
        downloadChannel: Channel<AssetPipelineContext>,
        exifChannel: Channel<AssetPipelineContext>,
        coordinator: PipelineCoordinator,
    ) {
        val detailId = context.detailId
        val downloadedPath = context.downloadedPath

        if (detailId == null || downloadedPath == null || !Files.exists(downloadedPath)) {
            log.warn("Verification skipped due to missing file/detail for asset {}", context.asset.id)
            handleRetry(context, downloadChannel, coordinator, IllegalStateException("missing file"))
            return
        }

        try {
            val sha1 = computeSha1(downloadedPath)
            if (!sha1.equals(context.asset.sha1, ignoreCase = true)) {
                log.warn("SHA1 mismatch for asset {} expected {} actual {}", context.asset.id, context.asset.sha1, sha1)
                Files.deleteIfExists(downloadedPath)
                context.downloadedPath = null
                handleRetry(context, downloadChannel, coordinator, IllegalStateException("sha1 mismatch"))
                return
            }

            context.sha1Verified = true
            context.lastError = null
            markSha1Verified(detailId)
            exifChannel.send(context)
        } catch (ex: Exception) {
            log.error("Verification failed for asset {}", context.asset.id, ex)
            handleRetry(context, downloadChannel, coordinator, ex)
        }
    }

    private fun markSha1Verified(detailId: Long) {
        sql.executeUpdate(CrontabHistoryDetail::class) {
            set(table.sha1Verified, true)
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
        context.sha1Verified = false

        if (context.retry >= context.maxRetry) {
            log.error("Abandon asset {} after {} retries during verification", context.asset.id, context.retry)
            coordinator.markCompleted()
            return
        }

        downloadChannel.send(context)
    }

    private fun computeSha1(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-1")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var read: Int
            while (input.read(buffer).also { read = it } > 0) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }
}
