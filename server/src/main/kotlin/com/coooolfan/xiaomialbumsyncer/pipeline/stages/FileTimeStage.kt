package com.coooolfan.xiaomialbumsyncer.pipeline.stages

import com.coooolfan.xiaomialbumsyncer.model.CrontabHistoryDetail
import com.coooolfan.xiaomialbumsyncer.model.fsTimeUpdated
import com.coooolfan.xiaomialbumsyncer.model.id
import com.coooolfan.xiaomialbumsyncer.pipeline.AssetPipelineContext
import com.coooolfan.xiaomialbumsyncer.utils.rewriteFSTime
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

    fun process(context: AssetPipelineContext): AssetPipelineContext {
        val detailId = context.detailId
        val filePath = context.targetPath

        if (detailId == null || !Files.exists(filePath)) {
            log.warn("资源 {} 缺少文件或明细记录，跳过文件时间阶段", context.asset.id)
            return context
        }

        if (context.crontabConfig.rewriteFileSystemTime) {
            rewriteFSTime(filePath, context.asset.dateTaken)
        }

        sql.executeUpdate(CrontabHistoryDetail::class) {
            set(table.fsTimeUpdated, true)
            where(table.id eq detailId)
        }
        return context
    }
}
