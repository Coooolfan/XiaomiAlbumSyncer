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

@Managed
class FileService {

    private val log = LoggerFactory.getLogger(this.javaClass)

    fun moveFile(source: Path, target: Path, createDirectories: Boolean = true) {
        if (!source.exists()) {
            throw IOException("源文件不存在: $source")
        }

        if (createDirectories) {
            Files.createDirectories(target.parent)
        }

        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Exception) {
            log.error("文件移动失败: $source -> $target", e)
            throw IOException("文件移动失败: ${e.message}", e)
        }
    }

    fun copyFile(source: Path, target: Path, createDirectories: Boolean = true) {
        if (!source.exists()) {
            throw IOException("源文件不存在: $source")
        }

        if (createDirectories) {
            Files.createDirectories(target.parent)
        }

        try {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Exception) {
            log.error("文件复制失败: $source -> $target", e)
            throw IOException("文件复制失败: ${e.message}", e)
        }
    }

    fun deleteFile(file: Path) {
        if (!file.exists()) {
            return
        }

        try {
            Files.delete(file)
        } catch (e: Exception) {
            log.error("文件删除失败: $file", e)
            throw IOException("文件删除失败: ${e.message}", e)
        }
    }

    fun verifySha1(file: Path, expectedSha1: String): Boolean {
        if (!file.exists()) {
            return false
        }

        return try {
            val actualSha1 = calculateSha1(file)
            val isValid = actualSha1.equals(expectedSha1, ignoreCase = true)

            if (!isValid) {
                log.warn("SHA1 验证失败: $file, 期望=$expectedSha1, 实际=$actualSha1")
            }

            isValid
        } catch (e: Exception) {
            log.error("SHA1 验证异常: $file", e)
            false
        }
    }

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

        return successCount
    }

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

        return successCount
    }

    fun fileExists(path: Path): Boolean {
        return try {
            path.exists() && Files.isRegularFile(path)
        } catch (e: Exception) {
            false
        }
    }

    fun getFileSize(path: Path): Long {
        return try {
            if (fileExists(path)) {
                Files.size(path)
            } else {
                0L
            }
        } catch (e: Exception) {
            0L
        }
    }
}
