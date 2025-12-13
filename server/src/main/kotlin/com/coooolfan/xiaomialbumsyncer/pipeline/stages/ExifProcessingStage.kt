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
        if (context.detailId == null || !Files.exists(context.targetPath)) {
            log.warn("资源 {} 缺少文件或明细记录，跳过 EXIF 处理阶段", context.asset.id)
            return context
        }

        if (context.crontabConfig.rewriteExifTime) {
            if (context.crontabConfig.rewriteExifTimeZone == null) {
                log.warn("未指定有效的时区，填充 EXIF 时间操作将被跳过。时区字符串：${context.crontabConfig.rewriteExifTimeZone}")
                return context
            }

            val config = ExifRewriteConfig(
                Path(systemConfigService.getConfig(NORMAL_SYSTEM_CONFIG).exifToolPath),
                context.crontabConfig.rewriteExifTimeZone.toTimeZone()
            )

            try {
                rewriteExifTime(context.asset, context.targetPath, config)
            } catch (e: RuntimeException) {
                if (e.message?.contains("Not a valid JPG") ?: false) {
                    log.warn("资源 {} 的 EXIF 处理失败, 将跳过后续处理", context.asset.id, e)
                } else {
                    throw e
                }
            }
        }

        sql.executeUpdate(CrontabHistoryDetail::class) {
            set(table.exifFilled, true)
            where(table.id eq context.detailId)
        }
        return context
    }

}
