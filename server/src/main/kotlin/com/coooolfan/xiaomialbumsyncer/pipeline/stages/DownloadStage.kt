package com.coooolfan.xiaomialbumsyncer.pipeline.stages

import com.coooolfan.xiaomialbumsyncer.model.CrontabHistoryDetail
import com.coooolfan.xiaomialbumsyncer.model.downloadCompleted
import com.coooolfan.xiaomialbumsyncer.model.filePath
import com.coooolfan.xiaomialbumsyncer.model.id
import com.coooolfan.xiaomialbumsyncer.pipeline.AssetPipelineContext
import com.coooolfan.xiaomialbumsyncer.pipeline.config
import com.coooolfan.xiaomialbumsyncer.xiaomicloud.XiaoMiApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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

    fun process(context: AssetPipelineContext): Flow<AssetPipelineContext> = flow {
        val detailId = context.detailId
        if (detailId == null) {
            log.error("资源 {} 缺失明细记录", context.asset.id)
            context.abandoned = true
            emit(context)
            return@flow
        }

        try {
            val targetPath = context.targetPath
            targetPath.parent?.let { Files.createDirectories(it) }
            val exists = context.config.skipExistingFile && Files.exists(targetPath)
            val downloadedPath = if (exists) targetPath else api.downloadAsset(context.asset, targetPath)

            context.downloadedPath = downloadedPath
            context.lastError = null

            markDownloadCompleted(detailId, downloadedPath)
            emit(context)
        } catch (ex: Exception) {
            log.error("资源 {} 下载失败", context.asset.id, ex)
            context.lastError = ex
            context.downloadedPath = null
            context.abandoned = true
            emit(context)
        }
    }

    private fun markDownloadCompleted(detailId: Long, filePath: Path) {
        sql.executeUpdate(CrontabHistoryDetail::class) {
            set(table.downloadCompleted, true)
            set(table.filePath, filePath.toString())
            where(table.id eq detailId)
        }
    }
}
