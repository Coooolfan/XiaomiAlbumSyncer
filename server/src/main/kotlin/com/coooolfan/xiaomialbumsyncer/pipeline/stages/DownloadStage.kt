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
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
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
            log.info("资产 {} 的下载已完成或者被标记为无需处理，跳过下载阶段", context.asset.id)
            return context
        }

        val targetPath = Path(context.filePath)
        targetPath.parent?.let { parent ->
            // Files.createDirectories 会先尝试 mkdir，并以 FileAlreadyExistsException 走正常分支。
            // 下载器并发处理同一目录里的大量资产时，预检查可避免每个文件都分配一次异常及其堆栈。
            if (!Files.isDirectory(parent)) Files.createDirectories(parent)
        }
        val tempPath = targetPath.resolveSibling("${targetPath.fileName}.${context.id}.tmp")
        cleanupTempFile(tempPath)

        if (context.crontabHistory.crontab.config.skipExistingFile && Files.exists(targetPath))
            log.info("跳过已存在文件 {}", targetPath)
        else {
            log.info("开始下载资产 {}", context.asset.id)
            val downloaded = try {
                api.downloadAsset(
                    context.crontabHistory.crontab.accountId,
                    context.asset,
                    tempPath
                )
            } catch (e: Exception) {
                cleanupTempFile(tempPath)
                throw e
            }
            if (downloaded) {
                moveCompletedDownload(tempPath, targetPath)
            }
            log.info("下载资产 {} 完成", context.asset.id)
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

    private fun cleanupTempFile(tempPath: Path) {
        try {
            if (Files.deleteIfExists(tempPath)) {
                log.info("已清理下载临时文件 {}", tempPath)
            }
        } catch (e: Exception) {
            log.warn("清理下载临时文件失败: {}", tempPath, e)
        }
    }

    private fun moveCompletedDownload(tempPath: Path, targetPath: Path) {
        try {
            Files.move(tempPath, targetPath, ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            // 兼容不支持原子移动的网络或挂载文件系统。
            Files.move(tempPath, targetPath, REPLACE_EXISTING)
        }
    }

}
