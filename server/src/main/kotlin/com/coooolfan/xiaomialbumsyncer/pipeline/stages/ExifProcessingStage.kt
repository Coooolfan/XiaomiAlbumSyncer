package com.coooolfan.xiaomialbumsyncer.pipeline.stages

import com.coooolfan.xiaomialbumsyncer.model.CrontabHistoryDetail
import com.coooolfan.xiaomialbumsyncer.model.SystemConfig
import com.coooolfan.xiaomialbumsyncer.model.exifFilled
import com.coooolfan.xiaomialbumsyncer.model.id
import com.coooolfan.xiaomialbumsyncer.utils.ExifRewriteConfig
import com.coooolfan.xiaomialbumsyncer.utils.rewriteExifTime
import com.coooolfan.xiaomialbumsyncer.utils.toTimeZone
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.noear.solon.annotation.Managed
import org.slf4j.LoggerFactory
import kotlin.io.path.Path

/**
 * EXIF 处理阶段
 */
@Managed
class ExifProcessingStage(
    private val sql: KSqlClient,
) {

    private val log = LoggerFactory.getLogger(ExifProcessingStage::class.java)

    fun process(context: CrontabHistoryDetail, systemConfig: SystemConfig): CrontabHistoryDetail {
        if (context.exifFilled) {
            log.info("资源 {} 的 EXIF 已处理或者被标记为无需处理，跳过 EXIF 处理阶段", context.asset.id)
            return context
        }

        if (context.crontabHistory.crontab.config.rewriteExifTime) {
            val rewriteExifTimeZone = context.crontabHistory.crontab.config.rewriteExifTimeZone
            if (rewriteExifTimeZone == null) {
                log.warn("未指定有效的时区，填充 EXIF 时间操作将被跳过。时区字符串：$rewriteExifTimeZone")
                return context
            }

            val config = ExifRewriteConfig(
                Path(systemConfig.exifToolPath),
                rewriteExifTimeZone.toTimeZone()
            )

            try {
                rewriteExifTime(context.asset, Path(context.filePath), config)
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
            where(table.id eq context.id)
        }
        return CrontabHistoryDetail(context) {
            exifFilled = true
        }
    }

}
