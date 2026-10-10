package com.coooolfan.xiaomialbumsyncer.model

import org.babyfish.jimmer.sql.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
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

    val message: String?

    companion object {
        private val TOKEN_REGEX = Regex("""\$\{([^}]+)}""")

        // 目录未包含插值时补全的默认相册/文件名模板
        const val DEFAULT_FILE_TEMPLATE = "\${album}/\${downloadFileName}"

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
     * targetPath 始终是完整路径表达式，支持 ${} 插值（含时间格式化前缀）。
     * 为兼容直接调用 API 的旧客户端，不含插值的目录会自动补全默认相册/文件名模板。
     */
    fun genFilePath(history: CrontabHistory, asset: Asset): String {
        val config = history.crontab.config
        val configuredPath = config.targetPath.trim()
        val expression = if (TOKEN_REGEX.containsMatchIn(configuredPath)) configuredPath
        else Path(configuredPath, DEFAULT_FILE_TEMPLATE).toString()

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
            put(
                "downloadFileName",
                if (asset.type == AssetType.AUDIO) "${asset.id}_$fileNameSafe" else fileNameSafe
            )
            put("fileStem", fileStem)
            put("fileExt", fileExt)
            put("assetId", asset.id.toString())
            put("assetType", asset.type.name.lowercase(Locale.ROOT))
            put("recordingTypeId", asset.recordingType?.code?.toString() ?: "")
            put("recordingType", asset.recordingType?.label ?: "")
            put("checksum", asset.checksum)
            put("sha1", asset.checksum)
            put("title", sanitizeSegment(asset.title))
            put("size", asset.size.toString())
            put("downloadEpochMillis", downloadTime.toEpochMilli().toString())
            put("takenEpochMillis", takenTime.toEpochMilli().toString())
            put("downloadEpochSeconds", downloadTime.epochSecond.toString())
            put("takenEpochSeconds", takenTime.epochSecond.toString())
        }

        val resolved = interpolateExpression(expression, replacements, downloadTime, takenTime, zoneId).trim()
        require(resolved.isNotEmpty()) { "targetPath 解析结果不能为空" }
        return Path(resolved).normalize().toString()
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

}
