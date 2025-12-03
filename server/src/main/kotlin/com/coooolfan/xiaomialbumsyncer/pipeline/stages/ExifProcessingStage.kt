package com.coooolfan.xiaomialbumsyncer.pipeline.stages

import com.coooolfan.xiaomialbumsyncer.model.CrontabHistoryDetail
import com.coooolfan.xiaomialbumsyncer.model.exifFilled
import com.coooolfan.xiaomialbumsyncer.model.id
import com.coooolfan.xiaomialbumsyncer.pipeline.AssetPipelineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
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

    fun start(
        scope: CoroutineScope,
        exifChannel: Channel<AssetPipelineContext>,
        fileTimeChannel: Channel<AssetPipelineContext>,
        workerCount: Int,
    ): Job = scope.launch {
        repeat(workerCount) {
            launch {
                for (context in exifChannel) {
                    handleExif(context, exifChannel, fileTimeChannel)
                }
            }
        }
    }

    private suspend fun handleExif(
        context: AssetPipelineContext,
        exifChannel: Channel<AssetPipelineContext>,
        fileTimeChannel: Channel<AssetPipelineContext>,
    ) {
        val detailId = context.detailId
        val downloadedPath = context.downloadedPath ?: context.targetPath

        if (detailId == null || !Files.exists(downloadedPath)) {
            log.warn("Exif stage skipped due to missing file/detail for asset {}", context.asset.id)
            fileTimeChannel.send(context)
            return
        }

        try {
            if (context.rewriteExifTime) {
                val config = context.exifRewriteConfig
                    ?: throw IllegalStateException("Exif rewrite enabled but config missing")
                rewriteExifTime(context.asset, downloadedPath, config)
            }

            context.lastError = null
            markExifFilled(detailId)
            fileTimeChannel.send(context)
        } catch (ex: Exception) {
            log.error("Exif processing failed for asset {}", context.asset.id, ex)
            context.lastError = ex
            exifChannel.send(context)
        }
    }

    private fun markExifFilled(detailId: Long) {
        sql.executeUpdate(CrontabHistoryDetail::class) {
            set(table.exifFilled, true)
            where(table.id eq detailId)
        }
    }
}
