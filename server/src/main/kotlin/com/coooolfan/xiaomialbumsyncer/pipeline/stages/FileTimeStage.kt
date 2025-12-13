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
import kotlin.io.path.Path

/**
 * 文件时间处理阶段处理器
 */
@Managed
class FileTimeStage(
    private val sql: KSqlClient,
) {

    private val log = LoggerFactory.getLogger(FileTimeStage::class.java)

    fun process(context: AssetPipelineContext): AssetPipelineContext {
        if (context.detail.fsTimeUpdated) {
            log.info("资源 {} 的文件时间已更新或者被标记为无需处理，跳过文件时间阶段", context.asset.id)
            return context
        }

        if (context.crontabConfig.rewriteFileSystemTime) {
            rewriteFSTime(Path(context.detail.filePath), context.asset.dateTaken)
        }

        sql.executeUpdate(CrontabHistoryDetail::class) {
            set(table.fsTimeUpdated, true)
            where(table.id eq context.detail.id)
        }
        context.detail = CrontabHistoryDetail(context.detail) {
            fsTimeUpdated = true
        }
        return context
    }
}
