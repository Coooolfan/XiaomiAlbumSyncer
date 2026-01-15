package com.coooolfan.xiaomialbumsyncer.model

import org.babyfish.jimmer.sql.DissociateAction
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.GenerationType
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.ManyToOne
import org.babyfish.jimmer.sql.OnDissociate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.io.path.Path

@Entity
interface CrontabHistoryDetail {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long

    @OnDissociate(DissociateAction.DELETE)
    @ManyToOne
    val crontabHistory: CrontabHistory

    // TODO)) 这个字段可以删掉了
    val downloadTime: Instant

    @OnDissociate(DissociateAction.DELETE)
    @ManyToOne
    val asset: Asset

    val filePath: String

    // 下载
    val downloadCompleted: Boolean

    // 校验
    val sha1Verified: Boolean

    // EXIF 填充
    val exifFilled: Boolean

    // 修改时间更新
    val fsTimeUpdated: Boolean

    companion object {
        private val TOKEN_REGEX = Regex("""\$\{([^}]+)}""")

        fun init(
            history: CrontabHistory,
            asset: Asset
        ): CrontabHistoryDetail {
            return CrontabHistoryDetail {
                this.crontabHistory = history
                this.downloadTime = Instant.now()
                this.asset = asset
                this.filePath = genFilePath(history, asset)
                this.downloadCompleted = false
                this.sha1Verified = !history.crontab.config.checkSha1
                this.exifFilled = !history.crontab.config.rewriteExifTime
                this.fsTimeUpdated = !history.crontab.config.rewriteFileSystemTime
            }
        }
    }

    /**
     * 生成下载目标路径。
     * - 支持 expressionTargetPath 里的 ${} 插值（含时间格式化前缀）。
     * - 若模板为空、无插值项或解析后为空，则回退到旧逻辑。
     */
    fun genFilePath(history: CrontabHistory, asset: Asset): String {
        val config = history.crontab.config
        val expression = config.expressionTargetPath?.trim()?.takeIf { it.isNotEmpty() }

        if (expression == null) {
            return legacyFilePath(history, asset)
        }

        val zoneId = resolveZoneId(config.timeZone)
        val downloadTime = history.startTime
        val takenTime = asset.dateTaken

        val fileNameRaw = asset.fileName
        val fileName = fileNameRaw
        val fileNameSafe = sanitizeSegment(fileName)
        val fileStem = fileNameSafe.substringBeforeLast('.', fileNameSafe)
        val fileExt = fileNameRaw.substringAfterLast('.', "")

        val replacements = buildMap {
            put("crontabId", history.crontab.id.toString())
            put("crontabName", history.crontab.name)
            put("historyId", history.id.toString())
            put("album", sanitizeSegment(asset.album.name))
            put("albumName", sanitizeSegment(asset.album.name))
            put("fileName", fileNameSafe)
            put("fileStem", fileStem)
            put("fileExt", fileExt)
            put("assetId", asset.id.toString())
            put("assetType", asset.type.name.lowercase(Locale.ROOT))
            put("sha1", asset.sha1)
            put("title", sanitizeSegment(asset.title))
            put("size", asset.size.toString())
            put("downloadTime", DateTimeFormatter.ISO_INSTANT.format(downloadTime))
            put("taken", DateTimeFormatter.ISO_INSTANT.format(takenTime))
            put("downloadEpochMillis", downloadTime.toEpochMilli().toString())
            put("takenEpochMillis", takenTime.toEpochMilli().toString())
            put("downloadEpochSeconds", downloadTime.epochSecond.toString())
            put("takenEpochSeconds", takenTime.epochSecond.toString())
        }

        if (!containsSupportedInterpolation(expression, replacements.keys)) {
            return legacyFilePath(history, asset)
        }

        val resolved = interpolateExpression(expression, replacements, downloadTime, takenTime, zoneId).trim()
        if (resolved.isEmpty()) {
            return legacyFilePath(history, asset)
        }

        return Path(resolved).normalize().toString()
    }

    // 旧逻辑：按 targetPath/album/fileName 生成，录音自动加 id 前缀
    private fun legacyFilePath(history: CrontabHistory, asset: Asset): String {
        return if (asset.type != AssetType.AUDIO)
            Path(crontabHistory.crontab.config.targetPath, asset.album.name, asset.fileName).toString()
        else
        // 录音文件会有普遍的文件名重复，需要在文件名前加上 id 以避免冲突
            Path(
                crontabHistory.crontab.config.targetPath,
                asset.album.name,
                "${asset.id}_${asset.fileName}"
            ).toString()
    }

    // 将模板中的 ${key} 替换为对应值，并解析 download_/taken_ 时间格式。
    private fun interpolateExpression(
        template: String,
        values: Map<String, String>,
        downloadTime: Instant,
        takenTime: Instant,
        zoneId: ZoneId,
    ): String {
        return TOKEN_REGEX.replace(template) { match ->
            val key = match.groupValues[1]
            when {
                key.startsWith("download_") -> {
                    val pattern = key.removePrefix("download_")
                    formatInstant(downloadTime, zoneId, pattern) ?: match.value
                }

                key.startsWith("taken_") -> {
                    val pattern = key.removePrefix("taken_")
                    formatInstant(takenTime, zoneId, pattern) ?: match.value
                }

                else -> values[key] ?: match.value
            }
        }
    }

    // 使用 DateTimeFormatter 处理时间格式
    private fun formatInstant(instant: Instant, zoneId: ZoneId, pattern: String): String? {
        if (pattern.isBlank()) return null
        return runCatching {
            DateTimeFormatter.ofPattern(pattern).withLocale(Locale.ROOT).format(instant.atZone(zoneId))
        }.getOrNull()
    }

    // 解析时区；解析失败时回退系统默认时区。
    private fun resolveZoneId(timeZone: String?): ZoneId {
        if (timeZone.isNullOrBlank()) {
            return ZoneId.systemDefault()
        }
        return runCatching { ZoneId.of(timeZone) }.getOrDefault(ZoneId.systemDefault())
    }

    // 用于生成安全路径片段，避免非法字符。
    private fun sanitizeSegment(value: String): String {
        if (value.isEmpty()) return value
        return value.replace(Regex("""[\\/:*?"<>|\r\n\t]"""), "_")
    }

    private fun containsSupportedInterpolation(template: String, supportedKeys: Set<String>): Boolean {
        return TOKEN_REGEX.findAll(template).any { match ->
            val key = match.groupValues[1]
            key in supportedKeys ||
                    key.startsWith("download_") ||
                    key.startsWith("taken_")
        }
    }

}
