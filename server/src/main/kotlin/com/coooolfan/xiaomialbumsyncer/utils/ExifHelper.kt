package com.coooolfan.xiaomialbumsyncer.utils

import com.coooolfan.xiaomialbumsyncer.model.Asset
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

fun rewriteExifTime(asset: Asset, path: Path, binPath: Path) {
    when (asset.type) {
        com.coooolfan.xiaomialbumsyncer.model.AssetType.IMAGE -> rewriteImageExifTime(asset, path, binPath)
        com.coooolfan.xiaomialbumsyncer.model.AssetType.VIDEO -> rewriteVideoExifTime(asset, path, binPath)
    }
}

fun rewriteImageExifTime(asset: Asset, path: Path, binPath: Path) {
    TODO()
}

fun rewriteVideoExifTime(asset: Asset, path: Path, binPath: Path) {
    val videoExistTagJson = jacksonObjectMapper().readTree(runExifTool(binPath, listOf("-j", "-G", path.toString())))[0]
    val needRewriteTags = VIDEO_DATA_TAG.filter {
        val tagValue = videoExistTagJson.get(it)?.asText()
        tagValue == null || tagValue.startsWith("0000:00:00 00:00:00")
    }

    if (needRewriteTags.isEmpty()) return

    //  格式为 YYYY:MM:DD HH:MM:SS 的字符串(UTC时间)
    val tagValue: String = asset.dateTaken.formatUTC()
    val rewriteArgs =
        needRewriteTags.map { "-$it=$tagValue" } + listOf("-overwrite_original", path.toString())

    runExifTool(binPath, rewriteArgs)
}

fun runExifTool(binPath: Path, args: List<String>): String {
    val command = listOf(binPath.toString()) + args

    val process = ProcessBuilder(command).start()

    val output = process.inputStream.bufferedReader().readText()
    val exitCode = process.waitFor()

    if (exitCode != 0) {
        val error = process.errorStream.bufferedReader().readText()
        throw RuntimeException("ExifTool failed (exit code: $exitCode): $error")
    }

    return output.trim()
}

fun Instant.formatUTC(): String {
    return this.atZone(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss"))
}

val VIDEO_DATA_TAG = listOf(
    "MediaCreateDate",
    "MediaModifyDate",
    "TrackCreateDate",
    "TrackModifyDate",
    "CreateDate",
    "ModifyDate",
)