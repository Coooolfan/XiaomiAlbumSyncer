package com.coooolfan.xiaomialbumsyncer.pipeline.stages

import com.coooolfan.xiaomialbumsyncer.model.CrontabHistoryDetail
import com.coooolfan.xiaomialbumsyncer.model.downloadCompleted
import com.coooolfan.xiaomialbumsyncer.model.filePath
import com.coooolfan.xiaomialbumsyncer.model.id
import com.coooolfan.xiaomialbumsyncer.xiaomicloud.XiaoMiApi
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.noear.solon.annotation.Managed
import org.slf4j.LoggerFactory
import java.nio.file.Files
import kotlin.io.path.Path

/**
 * 下载阶段处理器
 */
@Managed
class DownloadStage(
    private val api: XiaoMiApi,
    private val sql: KSqlClient,
) {

    private val log = LoggerFactory.getLogger(DownloadStage::class.java)

    fun process(context: CrontabHistoryDetail): CrontabHistoryDetail {
        if (context.downloadCompleted) {
            return context
        }

        val targetPath = Path(context.filePath)
        targetPath.parent?.let { Files.createDirectories(it) }

        if (!(context.crontabHistory.crontab.config.skipExistingFile && Files.exists(targetPath))) {
            val accountId = context.crontabHistory.crontab.accountId
            api.downloadAsset(accountId, context.asset, targetPath)
        }

        sql.executeUpdate(CrontabHistoryDetail::class) {
            set(table.downloadCompleted, true)
            set(table.filePath, targetPath.toString())
            where(table.id eq context.id)
        }
        return CrontabHistoryDetail(context) {
            downloadCompleted = true
        }
    }


}
