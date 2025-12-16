package com.coooolfan.xiaomialbumsyncer.pipeline.stages

import com.coooolfan.xiaomialbumsyncer.model.CrontabHistoryDetail
import com.coooolfan.xiaomialbumsyncer.model.fsTimeUpdated
import com.coooolfan.xiaomialbumsyncer.model.id
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

    fun process(context: CrontabHistoryDetail): CrontabHistoryDetail {
        if (context.fsTimeUpdated) {
            log.info("资源 {} 的文件时间已更新或者被标记为无需处理，跳过文件时间阶段", context.asset.id)
            return context
        }

        if (context.crontabHistory.crontab.config.rewriteFileSystemTime) {
            log.info("开始处理资源 {} 的文件系统时间", context.asset.id)
            rewriteFSTime(Path(context.filePath), context.asset.dateTaken)
            log.info("资源 {} 的文件系统时间处理完成", context.asset.id)
        }

        sql.executeUpdate(CrontabHistoryDetail::class) {
            set(table.fsTimeUpdated, true)
            where(table.id eq context.id)
        }
        return CrontabHistoryDetail(context) {
            fsTimeUpdated = true
        }
    }
}
