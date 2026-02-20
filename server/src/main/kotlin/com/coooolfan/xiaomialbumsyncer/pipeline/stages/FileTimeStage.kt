package com.coooolfan.xiaomialbumsyncer.pipeline.stages

import com.coooolfan.xiaomialbumsyncer.model.Asset
import com.coooolfan.xiaomialbumsyncer.model.CrontabHistoryDetail
import com.coooolfan.xiaomialbumsyncer.model.fsTimeUpdated
import com.coooolfan.xiaomialbumsyncer.model.id
import com.coooolfan.xiaomialbumsyncer.utils.rewriteFSTime
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.noear.solon.annotation.Managed
import org.slf4j.LoggerFactory
import kotlin.io.path.Path

@Managed
class FileTimeStage(
    private val sql: KSqlClient,
) {

    private val log = LoggerFactory.getLogger(FileTimeStage::class.java)

    fun process(context: CrontabHistoryDetail): CrontabHistoryDetail {
        if (context.fsTimeUpdated) {
            return context
        }

        if (context.crontabHistory.crontab.config.rewriteFileSystemTime) {
            rewriteFSTime(Path(context.filePath), context.asset.dateTaken)
        }

        sql.executeUpdate(CrontabHistoryDetail::class) {
            set(table.fsTimeUpdated, true)
            where(table.id eq context.id)
        }
        return CrontabHistoryDetail(context) {
            fsTimeUpdated = true
        }
    }

    fun updateFileSystemTime(asset: Asset, filePath: java.nio.file.Path) {
        rewriteFSTime(filePath, asset.dateTaken)
    }
}
