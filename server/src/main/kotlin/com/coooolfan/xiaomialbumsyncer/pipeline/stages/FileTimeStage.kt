package com.coooolfan.xiaomialbumsyncer.pipeline.stages

import com.coooolfan.xiaomialbumsyncer.model.CrontabHistoryDetail
import com.coooolfan.xiaomialbumsyncer.model.fsTimeUpdated
import com.coooolfan.xiaomialbumsyncer.model.id
import com.coooolfan.xiaomialbumsyncer.pipeline.AssetPipelineContext
import com.coooolfan.xiaomialbumsyncer.pipeline.config
import com.coooolfan.xiaomialbumsyncer.utils.rewriteFSTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.noear.solon.annotation.Managed
import org.slf4j.LoggerFactory
import java.nio.file.Files

/**
 * 文件时间处理阶段处理器
 */
@Managed
class FileTimeStage(
    private val sql: KSqlClient,
) {

    private val log = LoggerFactory.getLogger(FileTimeStage::class.java)

    fun process(context: AssetPipelineContext): Flow<AssetPipelineContext> = flow {
        val detailId = context.detailId
        val filePath = context.targetPath

        if (detailId == null || !Files.exists(filePath)) {
            log.warn("资源 {} 缺少文件或明细记录，跳过文件时间阶段", context.asset.id)
            emit(context)
            return@flow
        }

        try {
            if (context.config.rewriteFileSystemTime) {
                rewriteFSTime(filePath, context.asset.dateTaken)
            }

            markFsUpdated(detailId)
            emit(context)
        } catch (ex: Exception) {
            log.error("资源 {} 的文件时间阶段处理失败", context.asset.id, ex)
            context.lastError = ex
            // 失败也要 emit，确保任务被计数
            emit(context)
        }
    }

    private fun markFsUpdated(detailId: Long) {
        sql.executeUpdate(CrontabHistoryDetail::class) {
            set(table.fsTimeUpdated, true)
            where(table.id eq detailId)
        }
    }
}
