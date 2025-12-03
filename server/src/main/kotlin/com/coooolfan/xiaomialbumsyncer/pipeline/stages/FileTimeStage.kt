package com.coooolfan.xiaomialbumsyncer.pipeline.stages

import com.coooolfan.xiaomialbumsyncer.model.CrontabHistoryDetail
import com.coooolfan.xiaomialbumsyncer.model.fsTimeUpdated
import com.coooolfan.xiaomialbumsyncer.model.id
import com.coooolfan.xiaomialbumsyncer.pipeline.AssetPipelineContext
import com.coooolfan.xiaomialbumsyncer.pipeline.PipelineCoordinator
import com.coooolfan.xiaomialbumsyncer.pipeline.PipelineRequest
import com.coooolfan.xiaomialbumsyncer.utils.rewriteFSTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.noear.solon.annotation.Managed
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 文件时间处理阶段处理器
 */
@Managed
class FileTimeStage(
    private val sql: KSqlClient,
) {

    private val log = LoggerFactory.getLogger(FileTimeStage::class.java)

    fun start(
        scope: CoroutineScope,
        fileTimeChannel: Channel<AssetPipelineContext>,
        request: PipelineRequest,
        workerCount: Int,
        coordinator: PipelineCoordinator,
    ): Job = scope.launch {
        repeat(workerCount) {
            launch {
                for (context in fileTimeChannel) {
                    handleFileTime(context, coordinator)
                }
            }
        }
    }

    private fun handleFileTime(
        context: AssetPipelineContext,
        coordinator: PipelineCoordinator,
    ) {
        val detailId = context.detailId
        val downloadedPath = context.downloadedPath ?: context.targetPath

        if (detailId == null || !Files.exists(downloadedPath)) {
            log.warn("FileTime stage skipped due to missing file/detail for asset {}", context.asset.id)
            coordinator.markCompleted()
            return
        }

        try {
            val finalPath = context.targetPath
            if (downloadedPath != finalPath) {
                Files.createDirectories(finalPath.parent)
                Files.move(downloadedPath, finalPath, StandardCopyOption.REPLACE_EXISTING)
            }

            context.finalPath = finalPath

            if (context.rewriteFileSystemTime) {
                rewriteFSTime(finalPath, context.asset.dateTaken)
            }

            markFsUpdated(detailId)
            coordinator.markCompleted()
        } catch (ex: Exception) {
            log.error("File time stage failed for asset {}", context.asset.id, ex)
            context.lastError = ex
            coordinator.markCompleted()
        }
    }

    private fun markFsUpdated(detailId: Long) {
        sql.executeUpdate(CrontabHistoryDetail::class) {
            set(table.fsTimeUpdated, true)
            where(table.id eq detailId)
        }
    }
}
