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
            log.info("资源 {} 的下载已完成或者被标记为无需处理，跳过下载阶段", context.asset.id)
            return context
        }

        val targetPath = Path(context.filePath)
        targetPath.parent?.let { Files.createDirectories(it) }

        if (context.crontabHistory.crontab.config.skipExistingFile && Files.exists(targetPath))
            log.info("跳过已存在文件 {}", targetPath)
        else {
            log.info("开始下载资源 {}", context.asset.id)
            val accountId = context.crontabHistory.crontab.accountId
            api.downloadAsset(accountId, context.asset, targetPath)
            log.info("下载资源 {} 完成", context.asset.id)
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
