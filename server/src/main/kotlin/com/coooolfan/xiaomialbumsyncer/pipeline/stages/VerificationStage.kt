package com.coooolfan.xiaomialbumsyncer.pipeline.stages

import com.coooolfan.xiaomialbumsyncer.model.CrontabHistoryDetail
import com.coooolfan.xiaomialbumsyncer.model.id
import com.coooolfan.xiaomialbumsyncer.model.sha1Verified
import com.coooolfan.xiaomialbumsyncer.pipeline.AssetPipelineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.noear.solon.annotation.Managed
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * 校验阶段处理器
 */
@Managed
class VerificationStage(
    private val sql: KSqlClient,
) {

    private val log = LoggerFactory.getLogger(VerificationStage::class.java)

    fun process(context: AssetPipelineContext): Flow<AssetPipelineContext> = flow {
        val detailId = context.detailId
        val filePath = context.targetPath

        if (detailId == null || !Files.exists(filePath)) {
            log.warn("资源 {} 缺少文件或明细记录，跳过校验阶段", context.asset.id)
            context.lastError = IllegalStateException("缺少文件")
            emit(context)
            return@flow
        }

        try {
            val sha1 = computeSha1(filePath)
            if (!sha1.equals(context.asset.sha1, ignoreCase = true)) {
                log.warn("资源 {} 的 SHA1 校验失败，期望 {} 实际 {}", context.asset.id, context.asset.sha1, sha1)
                Files.deleteIfExists(filePath)
                context.lastError = IllegalStateException("SHA1 不匹配")
                emit(context)
                return@flow
            }

            context.sha1Verified = true
            context.lastError = null
            markSha1Verified(detailId)
            emit(context)
        } catch (ex: Exception) {
            log.error("资源 {} 校验失败", context.asset.id, ex)
            context.lastError = ex
            emit(context)
        }
    }

    private fun markSha1Verified(detailId: Long) {
        sql.executeUpdate(CrontabHistoryDetail::class) {
            set(table.sha1Verified, true)
            where(table.id eq detailId)
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
