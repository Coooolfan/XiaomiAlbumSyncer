package com.coooolfan.xiaomialbumsyncer.pipeline.stages

import com.coooolfan.xiaomialbumsyncer.controller.SystemConfigController.Companion.NORMAL_SYSTEM_CONFIG
import com.coooolfan.xiaomialbumsyncer.model.CrontabHistoryDetail
import com.coooolfan.xiaomialbumsyncer.model.exifFilled
import com.coooolfan.xiaomialbumsyncer.model.id
import com.coooolfan.xiaomialbumsyncer.pipeline.AssetPipelineContext
import com.coooolfan.xiaomialbumsyncer.service.SystemConfigService
import com.coooolfan.xiaomialbumsyncer.utils.ExifRewriteConfig
import com.coooolfan.xiaomialbumsyncer.utils.rewriteExifTime
import com.coooolfan.xiaomialbumsyncer.utils.toTimeZone
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.noear.solon.annotation.Managed
import org.slf4j.LoggerFactory
import java.nio.file.Files
import kotlin.io.path.Path

/**
 * EXIF 处理阶段
 */
@Managed
class ExifProcessingStage(
    private val sql: KSqlClient,
    private val systemConfigService: SystemConfigService,
) {

    private val log = LoggerFactory.getLogger(ExifProcessingStage::class.java)

    fun process(context: AssetPipelineContext): AssetPipelineContext {
        val detailId = context.detailId
        val filePath = context.targetPath

        if (detailId == null || !Files.exists(filePath)) {
            log.warn("资源 {} 缺少文件或明细记录，跳过 EXIF 处理阶段", context.asset.id)
            return context
        }

        if (context.crontabConfig.rewriteExifTime) {
            if (context.crontabConfig.rewriteExifTimeZone == null) {
                log.warn("未指定有效的时区，填充 EXIF 时间操作将被跳过。时区字符串：${context.crontabConfig.rewriteExifTimeZone}")

                return context
            }
            context.crontabConfig.rewriteExifTimeZone
            val config = ExifRewriteConfig(
                Path(systemConfigService.getConfig(NORMAL_SYSTEM_CONFIG).exifToolPath),
                context.crontabConfig.rewriteExifTimeZone.toTimeZone()
            )

            rewriteExifTime(context.asset, filePath, config)
        }

        sql.executeUpdate(CrontabHistoryDetail::class) {
            set(table.exifFilled, true)
            where(table.id eq detailId)
        }
        return context
    }

}
