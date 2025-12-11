package com.coooolfan.xiaomialbumsyncer.pipeline.stages

import com.coooolfan.xiaomialbumsyncer.model.CrontabHistoryDetail
import com.coooolfan.xiaomialbumsyncer.model.downloadCompleted
import com.coooolfan.xiaomialbumsyncer.model.filePath
import com.coooolfan.xiaomialbumsyncer.model.id
import com.coooolfan.xiaomialbumsyncer.pipeline.AssetPipelineContext
import com.coooolfan.xiaomialbumsyncer.xiaomicloud.XiaoMiApi
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.noear.solon.annotation.Managed
import org.slf4j.LoggerFactory
import java.nio.file.Files

/**
 * 下载阶段处理器
 */
@Managed
class DownloadStage(
    private val api: XiaoMiApi,
    private val sql: KSqlClient,
) {

    private val log = LoggerFactory.getLogger(DownloadStage::class.java)

    fun process(context: AssetPipelineContext) {
        if (context.detailId == null) throw IllegalStateException("缺失明细记录: ${context.asset.id}")

        val targetPath = context.targetPath
        targetPath.parent?.let { Files.createDirectories(it) }

        if (context.crontabConfig.skipExistingFile && Files.exists(targetPath))
            log.info("跳过已存在文件 {}", targetPath)
        else
            api.downloadAsset(context.asset, targetPath)

        sql.executeUpdate(CrontabHistoryDetail::class) {
            set(table.downloadCompleted, true)
            set(table.filePath, context.targetPath.toString())
            where(table.id eq context.detailId)
        }
    }


}
