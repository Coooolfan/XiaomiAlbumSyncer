package com.coooolfan.xiaomialbumsyncer.pipeline.stages

import com.coooolfan.xiaomialbumsyncer.icloud.verifyICloudChecksum
import com.coooolfan.xiaomialbumsyncer.service.CloudMediaService
import com.coooolfan.xiaomialbumsyncer.model.CrontabHistoryDetail
import com.coooolfan.xiaomialbumsyncer.model.downloadCompleted
import com.coooolfan.xiaomialbumsyncer.model.id
import com.coooolfan.xiaomialbumsyncer.model.sha1Verified
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.noear.solon.annotation.Managed
import org.slf4j.LoggerFactory
import java.nio.file.Files
import kotlin.io.path.Path

/**
 * 校验阶段处理器
 */
@Managed
class VerificationStage(
    private val sql: KSqlClient,
    private val media: CloudMediaService,
) {

    private val log = LoggerFactory.getLogger(VerificationStage::class.java)

    fun process(context: CrontabHistoryDetail): CrontabHistoryDetail {
        if (context.sha1Verified) {
            log.info("资产 {} 的文件校验已完成或者被标记为无需处理，跳过校验阶段", context.asset.id)
            return context
        }

        if (media.isICloud(context.crontabHistory.crontab.accountId)) {
            log.info("开始校验 iCloud 资产 {} 的文件", context.asset.id)
            val path = Path(context.filePath)
            if (!verifyICloudChecksum(path, context.asset.checksum)) {
                val failure = IllegalStateException("资产 ${context.asset.id} 的 iCloud 文件校验失败，请重试下载")
                // 两项清理分别尝试，避免重试时复用损坏文件。
                try {
                    sql.executeUpdate(CrontabHistoryDetail::class) {
                        set(table.downloadCompleted, false)
                        set(table.sha1Verified, false)
                        where(table.id eq context.id)
                    }
                } catch (cleanupError: Exception) {
                    failure.addSuppressed(cleanupError)
                }
                try {
                    Files.deleteIfExists(path)
                } catch (cleanupError: Exception) {
                    failure.addSuppressed(cleanupError)
                }
                throw failure
            }
            log.info("iCloud 资产 {} 的文件校验成功", context.asset.id)
        } else {
            log.info("资产 {} 来自小米云，跳过 SHA-1 校验", context.asset.id)
        }

        sql.executeUpdate(CrontabHistoryDetail::class) {
            set(table.sha1Verified, true)
            where(table.id eq context.id)
        }
        return CrontabHistoryDetail(context) {
            sha1Verified = true
        }
    }

}
