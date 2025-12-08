package com.coooolfan.xiaomialbumsyncer.pipeline.stages

import com.coooolfan.xiaomialbumsyncer.model.CrontabHistoryDetail
import com.coooolfan.xiaomialbumsyncer.model.exifFilled
import com.coooolfan.xiaomialbumsyncer.model.id
import com.coooolfan.xiaomialbumsyncer.pipeline.AssetPipelineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.noear.solon.annotation.Managed
import org.slf4j.LoggerFactory
import java.nio.file.Files
import com.coooolfan.xiaomialbumsyncer.utils.rewriteExifTime

/**
 * EXIF 处理阶段
 */
@Managed
class ExifProcessingStage(
    private val sql: KSqlClient,
) {

    private val log = LoggerFactory.getLogger(ExifProcessingStage::class.java)

    fun process(context: AssetPipelineContext): Flow<AssetPipelineContext> = flow {
        val detailId = context.detailId
        val downloadedPath = context.downloadedPath ?: context.targetPath

        if (detailId == null || !Files.exists(downloadedPath)) {
            log.warn("资源 {} 缺少文件或明细记录，跳过 EXIF 处理阶段", context.asset.id)
            emit(context)
            return@flow
        }

        try {
            if (context.rewriteExifTime) {
                val config = context.exifRewriteConfig
                    ?: throw IllegalStateException("Exif rewrite enabled but config missing")
                rewriteExifTime(context.asset, downloadedPath, config)
            }

            context.lastError = null
            markExifFilled(detailId)
            emit(context)
        } catch (ex: Exception) {
            log.error("资源 {} 的 EXIF 处理失败，跳过此步骤", context.asset.id, ex)
            context.lastError = ex
            // 失败直接跳过，继续流水线
            emit(context)
        }
    }

    private fun markExifFilled(detailId: Long) {
        sql.executeUpdate(CrontabHistoryDetail::class) {
            set(table.exifFilled, true)
            where(table.id eq detailId)
        }
    }
}
