package com.coooolfan.xiaomialbumsyncer.service

import org.noear.solon.annotation.Managed
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.io.path.exists
import kotlin.io.path.inputStream

/**
 * 文件服务
 * 提供文件操作相关功能
 */
@Managed
class FileService {

    private val log = LoggerFactory.getLogger(this.javaClass)

    /**
     * 移动文件
     * @param source 源文件路径
     * @param target 目标文件路径
     * @param createDirectories 是否自动创建目标目录
     * @throws IOException 文件操作失败
     */
    fun moveFile(source: Path, target: Path, createDirectories: Boolean = true) {
        if (!source.exists()) {
            throw IOException("源文件不存在: $source")
        }

        if (createDirectories) {
            Files.createDirectories(target.parent)
        }

        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
            log.debug("文件移动成功: $source -> $target")
        } catch (e: Exception) {
            log.error("文件移动失败: $source -> $target", e)
            throw IOException("文件移动失败: ${e.message}", e)
        }
    }

    /**
     * 复制文件
     * @param source 源文件路径
     * @param target 目标文件路径
     * @param createDirectories 是否自动创建目标目录
     * @throws IOException 文件操作失败
     */
    fun copyFile(source: Path, target: Path, createDirectories: Boolean = true) {
        if (!source.exists()) {
            throw IOException("源文件不存在: $source")
        }

        if (createDirectories) {
            Files.createDirectories(target.parent)
        }

        try {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
            log.debug("文件复制成功: $source -> $target")
        } catch (e: Exception) {
            log.error("文件复制失败: $source -> $target", e)
            throw IOException("文件复制失败: ${e.message}", e)
        }
    }

    /**
     * 删除文件
     * @param file 文件路径
     * @throws IOException 文件操作失败
     */
    fun deleteFile(file: Path) {
        if (!file.exists()) {
            log.warn("文件不存在，跳过删除: $file")
            return
        }

        try {
            Files.delete(file)
            log.debug("文件删除成功: $file")
        } catch (e: Exception) {
            log.error("文件删除失败: $file", e)
            throw IOException("文件删除失败: ${e.message}", e)
        }
    }

    /**
     * 验证文件 SHA1
     * @param file 文件路径
     * @param expectedSha1 期望的 SHA1 值
     * @return 是否匹配
     */
    fun verifySha1(file: Path, expectedSha1: String): Boolean {
        if (!file.exists()) {
            log.warn("文件不存在，无法验证 SHA1: $file")
            return false
        }

        return try {
            val actualSha1 = calculateSha1(file)
            val isValid = actualSha1.equals(expectedSha1, ignoreCase = true)

            if (isValid) {
                log.debug("SHA1 验证成功: $file")
            } else {
                log.warn("SHA1 验证失败: $file, 期望=$expectedSha1, 实际=$actualSha1")
            }

            isValid
        } catch (e: Exception) {
            log.error("SHA1 验证异常: $file", e)
            false
        }
    }

    /**
     * 计算文件 SHA1
     * @param file 文件路径
     * @return SHA1 值（小写十六进制字符串）
     */
    fun calculateSha1(file: Path): String {
        val digest = MessageDigest.getInstance("SHA-1")

        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int

            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }

        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * 批量移动文件
     * @param operations 文件移动操作列表（源路径 -> 目标路径）
     * @return 成功移动的文件数量
     */
    fun batchMoveFiles(operations: List<Pair<Path, Path>>): Int {
        var successCount = 0

        operations.forEach { (source, target) ->
            try {
                moveFile(source, target)
                successCount++
            } catch (e: Exception) {
                log.error("批量移动文件失败: $source -> $target", e)
            }
        }

        log.info("批量移动文件完成，成功 $successCount/${operations.size}")
        return successCount
    }

    /**
     * 批量删除文件
     * @param files 文件路径列表
     * @return 成功删除的文件数量
     */
    fun batchDeleteFiles(files: List<Path>): Int {
        var successCount = 0

        files.forEach { file ->
            try {
                deleteFile(file)
                successCount++
            } catch (e: Exception) {
                log.error("批量删除文件失败: $file", e)
            }
        }

        log.info("批量删除文件完成，成功 $successCount/${files.size}")
        return successCount
    }

    /**
     * 检查文件是否存在
     * @param path 文件路径
     * @return 文件是否存在且为常规文件
     */
    fun fileExists(path: Path): Boolean {
        return try {
            path.exists() && Files.isRegularFile(path)
        } catch (e: Exception) {
            log.warn("检查文件存在性失败: $path", e)
            false
        }
    }

    /**
     * 获取文件大小
     * @param path 文件路径
     * @return 文件大小（字节），如果文件不存在或访问失败返回 0
     */
    fun getFileSize(path: Path): Long {
        return try {
            if (fileExists(path)) {
                Files.size(path)
            } else {
                0L
            }
        } catch (e: Exception) {
            log.warn("获取文件大小失败: $path", e)
            0L
        }
    }
}
