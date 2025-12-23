package com.coooolfan.xiaomialbumsyncer.utils

import com.coooolfan.xiaomialbumsyncer.model.Asset
import com.coooolfan.xiaomialbumsyncer.model.AssetType
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import java.util.*
import kotlin.concurrent.thread

fun rewriteExifTime(asset: Asset, path: Path, config: ExifRewriteConfig) {

    if (path.startsWith("/tmp")) return

    when (asset.type) {
        AssetType.IMAGE -> rewriteImageExifTime(asset, path, config)
        AssetType.VIDEO -> rewriteVideoExifTime(asset, path, config)
        AssetType.AUDIO -> {}
    }
}

fun rewriteImageExifTime(asset: Asset, path: Path, config: ExifRewriteConfig) {
    val imageExistTagJson =
        jacksonObjectMapper().readTree(runExifTool(config.exifToolPath, listOf("-j", "-G", path.toString())))[0]
    val tagValue = imageExistTagJson.get(IMAGE_DATA_TAG)?.asText()

    if (tagValue != null && !tagValue.startsWith("0000:00:00 00:00:00")) return

    //  格式为 YYYY:MM:DD HH:MM:SS 的字符串(UTC时间)
    val zoneOffset = config.timeZone.toZoneId().rules.getOffset(asset.dateTaken)
    val rewriteTagValue: String =
        asset.dateTaken.formatExif(zoneOffset)
    val rewriteArgs =
        listOf(
            "-$IMAGE_DATA_TAG=$rewriteTagValue",
            "-$IMAGE_TIMEZONE_TAG=$zoneOffset",
            "-overwrite_original",
            path.toString()
        )

    runExifTool(config.exifToolPath, rewriteArgs)
}

fun rewriteVideoExifTime(asset: Asset, path: Path, config: ExifRewriteConfig) {
    val videoExistTagJson =
        jacksonObjectMapper().readTree(runExifTool(config.exifToolPath, listOf("-j", "-G", path.toString())))[0]
    val needRewriteTags = VIDEO_DATA_TAG.filter {
        val tagValue = videoExistTagJson.get(it)?.asText()
        tagValue == null || tagValue.startsWith("0000:00:00 00:00:00")
    }

    if (needRewriteTags.isEmpty()) return

    //  格式为 YYYY:MM:DD HH:MM:SS 的字符串(UTC时间)
    val tagValue: String = asset.dateTaken.formatExif()
    val rewriteArgs =
        needRewriteTags.map { "-$it=$tagValue" } + listOf("-overwrite_original", path.toString())

    runExifTool(config.exifToolPath, rewriteArgs)
}

fun runExifTool(binPath: Path, args: List<String>): String {
    val command = listOf(binPath.toString()) + args

    val process = ProcessBuilder(command)
        .redirectErrorStream(true)
        .start()

    val output = StringBuilder()
    val readerThread = thread(name = "exiftool-output-reader", isDaemon = true) {
        process.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                output.appendLine(line)
            }
        }
    }

    val finished = process.waitFor(120, TimeUnit.SECONDS)
    if (!finished) {
        process.destroyForcibly()
        readerThread.join(1000)
        throw RuntimeException("ExifTool timeout")
    }

    readerThread.join(1000)

    val exitCode = process.exitValue()
    val outputText = output.toString().trim()
    if (exitCode != 0) {
        throw RuntimeException("ExifTool failed (exit code: $exitCode): $outputText")
    }

    return outputText
}

fun Instant.formatExif(zone: ZoneOffset = ZoneOffset.UTC): String {
    return this.atZone(zone).format(DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss"))
}

data class ExifRewriteConfig(
    val exifToolPath: Path,
    val timeZone: TimeZone
)

const val IMAGE_DATA_TAG: String = "EXIF:DateTimeOriginal"

const val IMAGE_TIMEZONE_TAG: String = "EXIF:OffsetTimeOriginal"

val VIDEO_DATA_TAG = listOf(
    "QuickTime:MediaCreateDate",
    "QuickTime:MediaModifyDate",
    "QuickTime:TrackCreateDate",
    "QuickTime:TrackModifyDate",
    "QuickTime:CreateDate",
    "QuickTime:ModifyDate",
)

// 没找到能让 immich 识别的时区标签
val VIDEO_TIMEZONE_TAG = "?"