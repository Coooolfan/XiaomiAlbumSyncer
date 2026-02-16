package com.coooolfan.xiaomialbumsyncer.service

import com.coooolfan.xiaomialbumsyncer.exception.BadRequestException
import org.noear.solon.annotation.Managed
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

@Managed
class MountPathService {
    private val inDockerCached: Boolean = Files.exists(DOCKER_ENV_PATH)
    private val mountPointsCached: Set<Path> = loadMountPoints(inDockerCached)

    fun isRunningInDockerContainer(): Boolean {
        return inDockerCached
    }

    fun checkExplicitMountPoint(path: String): Boolean {
        val normalizedPath = normalizeAbsolutePath(path)

        if (!inDockerCached) {
            return false
        }

        return isMountedPathOrSubPath(normalizedPath)
    }

    private fun normalizeAbsolutePath(path: String): Path {
        val normalizedPath = try {
            Path.of(path).normalize()
        } catch (e: InvalidPathException) {
            throw BadRequestException("非法路径: $path", e)
        }

        if (!normalizedPath.isAbsolute) {
            throw BadRequestException("path 必须为绝对路径: $path")
        }

        return normalizedPath
    }

    private fun loadMountPoints(inDocker: Boolean): Set<Path> {
        if (!inDocker) {
            return emptySet()
        }

        return try {
            readMountPoints()
        } catch (e: Exception) {
            log.warn("读取 {} 失败，将挂载点缓存降级为空集合", MOUNT_INFO_PATH, e)
            emptySet()
        }
    }

    private fun readMountPoints(): Set<Path> {
        return Files.readAllLines(MOUNT_INFO_PATH)
            .asSequence()
            .mapNotNull { line -> extractMountPoint(line) }
            .toSet()
    }

    private fun extractMountPoint(line: String): Path? {
        val fields = line.split(' ')
        if (fields.size < 5) {
            return null
        }

        val mountPoint = decodeMountInfoField(fields[4])
        return try {
            Path.of(mountPoint).normalize()
        } catch (_: InvalidPathException) {
            null
        }
    }

    private fun decodeMountInfoField(field: String): String {
        return MOUNT_INFO_ESCAPE_REGEX.replace(field) { match ->
            match.groupValues[1].toInt(8).toChar().toString()
        }
    }

    private fun isMountedPathOrSubPath(path: Path): Boolean {
        return mountPointsCached.any { mountPoint ->
            path == mountPoint || (mountPoint.nameCount > 0 && path.startsWith(mountPoint))
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(MountPathService::class.java)
        private val DOCKER_ENV_PATH: Path = Path.of("/.dockerenv")
        private val MOUNT_INFO_PATH: Path = Path.of("/proc/self/mountinfo")
        private val MOUNT_INFO_ESCAPE_REGEX = Regex("""\\([0-7]{3})""")
    }
}
