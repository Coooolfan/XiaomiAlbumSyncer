package com.coooolfan.xiaomialbumsyncer.pipeline.stages

import com.coooolfan.xiaomialbumsyncer.model.CrontabHistoryDetail
import com.coooolfan.xiaomialbumsyncer.model.id
import com.coooolfan.xiaomialbumsyncer.model.sha1Verified
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.noear.solon.annotation.Managed
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.Path

/**
 * 校验阶段处理器
 */
@Managed
class VerificationStage(
    private val sql: KSqlClient,
) {

    private val log = LoggerFactory.getLogger(VerificationStage::class.java)

    fun process(context: CrontabHistoryDetail): CrontabHistoryDetail {
        if (context.sha1Verified) {
            log.info("资源 {} 的 SHA1 校验已完成或者被标记为无需处理，跳过校验阶段", context.asset.id)
            return context
        }

        log.info("开始校验资源 {} 的 SHA1", context.asset.id)
        val sha1 = computeSha1(Path(context.filePath))
        if (!sha1.equals(context.asset.sha1, ignoreCase = true)) {
            log.warn("资源 {} 的 SHA1 校验失败，期望 {} 实际 {}", context.asset.id, context.asset.sha1, sha1)
            Files.deleteIfExists(Path(context.filePath))
            // TODO: 这里需要思考一下怎么从头再来
            return context
        }
        log.info("资源 {} 的 SHA1 校验成功", context.asset.id)

        sql.executeUpdate(CrontabHistoryDetail::class) {
            set(table.sha1Verified, true)
            where(table.id eq context.id)
        }
        return CrontabHistoryDetail(context) {
            sha1Verified = true
        }
    }


    private fun computeSha1(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-1")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var read: Int
            while (input.read(buffer).also { read = it } > 0) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

}
