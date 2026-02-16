package com.coooolfan.xiaomialbumsyncer.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.io.path.Path

class CrontabHistoryDetailTest {

    @Test
    fun legacyPathIsUsedWhenExpressionIsNull() {
        val config = buildConfig(targetPath = "/data/downloads", expressionTargetPath = "")
        val history = buildHistory(config, Instant.parse("2024-05-06T12:00:00Z"))
        val album = buildAlbum("Trip")
        val asset = buildAsset(AssetType.IMAGE, "photo.jpg", album)

        val detail = CrontabHistoryDetail.init(history, asset)

        val expected = Path(config.targetPath, album.name, asset.fileName).toString()
        assertEquals(expected, detail.filePath)
    }

    @Test
    fun expressionPathInterpolatesVariablesAndResolvesRelativePath() {
        val template = "./\${album}/\${download_YYYYMM}/\${fileName}"
        val config = buildConfig(targetPath = "/base", expressionTargetPath = template, timeZone = "Asia/Shanghai")
        val history = buildHistory(config, Instant.parse("2024-05-06T12:00:00Z"))
        val album = buildAlbum("Travel")
        val asset = buildAsset(AssetType.VIDEO, "clip.mp4", album)

        val detail = CrontabHistoryDetail.init(history, asset)

        val expected = Path("Travel/202405/clip.mp4").normalize().toString()
        assertEquals(expected, detail.filePath)
    }

    @Test
    fun audioFileNameKeepsOriginalNameInExpressions() {
        val template = "\${album}/\${fileName}"
        val config = buildConfig(targetPath = "/base", expressionTargetPath = template)
        val history = buildHistory(config, Instant.parse("2024-05-06T12:00:00Z"))
        val album = buildAlbum("Recordings")
        val asset = buildAsset(AssetType.AUDIO, "rec.m4a", album, id = 9L)

        val detail = CrontabHistoryDetail.init(history, asset)

        val expected = Path("Recordings/rec.m4a").toString()
        assertEquals(expected, detail.filePath)
    }

    // ==================== Legacy 路径逻辑测试 ====================

    @Test
    fun legacyPathHandlesEmptyExpression() {
        val config = buildConfig(targetPath = "/data", expressionTargetPath = "")
        val history = buildHistory(config, Instant.parse("2024-05-06T12:00:00Z"))
        val album = buildAlbum("MyAlbum")
        val asset = buildAsset(AssetType.IMAGE, "test.jpg", album)

        val detail = CrontabHistoryDetail.init(history, asset)

        val expected = Path("/data", "MyAlbum", "test.jpg").toString()
        assertEquals(expected, detail.filePath)
    }

    @Test
    fun legacyPathHandlesBlankExpression() {
        val config = buildConfig(targetPath = "/data", expressionTargetPath = "   ")
        val history = buildHistory(config, Instant.parse("2024-05-06T12:00:00Z"))
        val album = buildAlbum("Photos")
        val asset = buildAsset(AssetType.VIDEO, "video.mp4", album)

        val detail = CrontabHistoryDetail.init(history, asset)

        val expected = Path("/data", "Photos", "video.mp4").toString()
        assertEquals(expected, detail.filePath)
    }

    @Test
    fun legacyPathHandlesExpressionWithoutInterpolation() {
        val config = buildConfig(targetPath = "/data", expressionTargetPath = "/app/download")
        val history = buildHistory(config, Instant.parse("2024-05-06T12:00:00Z"))
        val album = buildAlbum("Photos")
        val asset = buildAsset(AssetType.IMAGE, "photo.jpg", album)

        val detail = CrontabHistoryDetail.init(history, asset)

        val expected = Path("/data", "Photos", "photo.jpg").toString()
        assertEquals(expected, detail.filePath)
    }

    @Test
    fun legacyPathKeepsAudioFileName() {
        val config = buildConfig(targetPath = "/storage", expressionTargetPath = "")
        val history = buildHistory(config, Instant.parse("2024-05-06T12:00:00Z"))
        val album = buildAlbum("Voice")
        val asset = buildAsset(AssetType.AUDIO, "recording.m4a", album, id = 42L)

        val detail = CrontabHistoryDetail.init(history, asset)

        val expected = Path("/storage", "Voice", "42_recording.m4a").toString()
        assertEquals(expected, detail.filePath)
    }

    @Test
    fun legacyPathDoesNotAddIdPrefixForImage() {
        val config = buildConfig(targetPath = "/storage", expressionTargetPath = "")
        val history = buildHistory(config, Instant.parse("2024-05-06T12:00:00Z"))
        val album = buildAlbum("Camera")
        val asset = buildAsset(AssetType.IMAGE, "IMG_001.jpg", album, id = 99L)

        val detail = CrontabHistoryDetail.init(history, asset)

        val expected = Path("/storage", "Camera", "IMG_001.jpg").toString()
        assertEquals(expected, detail.filePath)
    }

    @Test
    fun legacyPathDoesNotAddIdPrefixForVideo() {
        val config = buildConfig(targetPath = "/storage", expressionTargetPath = "")
        val history = buildHistory(config, Instant.parse("2024-05-06T12:00:00Z"))
        val album = buildAlbum("Movies")
        val asset = buildAsset(AssetType.VIDEO, "VID_001.mp4", album, id = 77L)

        val detail = CrontabHistoryDetail.init(history, asset)

        val expected = Path("/storage", "Movies", "VID_001.mp4").toString()
        assertEquals(expected, detail.filePath)
    }

    // ==================== 变量插值测试 ====================

    @Test
    fun expressionInterpolatesCrontabIdAndName() {
        val template = "\${crontabId}_\${crontabName}/\${fileName}"
        val config = buildConfig(targetPath = "/base", expressionTargetPath = template)
        val history = buildHistory(config, Instant.parse("2024-05-06T12:00:00Z"))
        val album = buildAlbum("Album")
        val asset = buildAsset(AssetType.IMAGE, "photo.jpg", album)

        val detail = CrontabHistoryDetail.init(history, asset)

        val expected = Path("1_Test/photo.jpg").toString()
        assertEquals(expected, detail.filePath)
    }

    @Test
    fun expressionInterpolatesHistoryId() {
        val template = "history_\${historyId}/\${fileName}"
        val config = buildConfig(targetPath = "/base", expressionTargetPath = template)
        val history = buildHistory(config, Instant.parse("2024-05-06T12:00:00Z"))
        val album = buildAlbum("Album")
        val asset = buildAsset(AssetType.IMAGE, "photo.jpg", album)

        val detail = CrontabHistoryDetail.init(history, asset)

        val expected = Path("history_1/photo.jpg").toString()
        assertEquals(expected, detail.filePath)
    }

    @Test
    fun expressionInterpolatesAlbumAndAlbumName() {
        val template = "\${album}/sub/\${albumName}/\${fileName}"
        val config = buildConfig(targetPath = "/base", expressionTargetPath = template)
        val history = buildHistory(config, Instant.parse("2024-05-06T12:00:00Z"))
        val album = buildAlbum("Vacation")
        val asset = buildAsset(AssetType.IMAGE, "photo.jpg", album)

        val detail = CrontabHistoryDetail.init(history, asset)

        val expected = Path("Vacation/sub/Vacation/photo.jpg").toString()
        assertEquals(expected, detail.filePath)
    }

    @Test
    fun expressionInterpolatesAlbum() {
        val template = "\${album}/\${fileName}"
        val config = buildConfig(targetPath = "/base", expressionTargetPath = template)
        val history = buildHistory(config, Instant.parse("2024-05-06T12:00:00Z"))
        val album = buildAlbum("My:Album*With<Special>Chars")
        val asset = buildAsset(AssetType.IMAGE, "photo.jpg", album)

        val detail = CrontabHistoryDetail.init(history, asset)

        val expected = Path("My_Album_With_Special_Chars/photo.jpg").toString()
        assertEquals(expected, detail.filePath)
    }

    @Test
    fun expressionInterpolatesFileStemAndExt() {
        val template = "\${album}/\${fileStem}_backup.\${fileExt}"
        val config = buildConfig(targetPath = "/base", expressionTargetPath = template)
        val history = buildHistory(config, Instant.parse("2024-05-06T12:00:00Z"))
        val album = buildAlbum("Photos")
        val asset = buildAsset(AssetType.IMAGE, "IMG_20240501.jpg", album)

        val detail = CrontabHistoryDetail.init(history, asset)

        val expected = Path("Photos/IMG_20240501_backup.jpg").toString()
        assertEquals(expected, detail.filePath)
    }

    @Test
    fun expressionInterpolatesAssetId() {
        val template = "\${album}/\${assetId}_\${fileName}"
        val config = buildConfig(targetPath = "/base", expressionTargetPath = template)
        val history = buildHistory(config, Instant.parse("2024-05-06T12:00:00Z"))
        val album = buildAlbum("Photos")
        val asset = buildAsset(AssetType.IMAGE, "photo.jpg", album, id = 12345L)

        val detail = CrontabHistoryDetail.init(history, asset)

        val expected = Path("Photos/12345_photo.jpg").toString()
        assertEquals(expected, detail.filePath)
    }

    @Test
    fun expressionInterpolatesAssetType() {
        val template = "\${assetType}/\${album}/\${fileName}"
        val config = buildConfig(targetPath = "/base", expressionTargetPath = template)
        val history = buildHistory(config, Instant.parse("2024-05-06T12:00:00Z"))
        val album = buildAlbum("Media")
        val asset = buildAsset(AssetType.VIDEO, "clip.mp4", album)

        val detail = CrontabHistoryDetail.init(history, asset)

        val expected = Path("video/Media/clip.mp4").toString()
        assertEquals(expected, detail.filePath)
    }

    @Test
    fun expressionInterpolatesSha1() {
        val template = "\${album}/\${sha1}_\${fileName}"
        val config = buildConfig(targetPath = "/base", expressionTargetPath = template)
        val history = buildHistory(config, Instant.parse("2024-05-06T12:00:00Z"))
        val album = buildAlbum("Photos")
        val asset = buildAsset(AssetType.IMAGE, "photo.jpg", album)

        val detail = CrontabHistoryDetail.init(history, asset)

        val expected = Path("Photos/deadbeef_photo.jpg").toString()
        assertEquals(expected, detail.filePath)
    }

    @Test
    fun expressionInterpolatesTitle() {
        val template = "\${title}/\${fileName}"
        val config = buildConfig(targetPath = "/base", expressionTargetPath = template)
        val history = buildHistory(config, Instant.parse("2024-05-06T12:00:00Z"))
        val album = buildAlbum("Photos")
        val asset = buildAsset(AssetType.IMAGE, "photo.jpg", album)

        val detail = CrontabHistoryDetail.init(history, asset)

        val expected = Path("title/photo.jpg").toString()
        assertEquals(expected, detail.filePath)
    }

    @Test
    fun expressionInterpolatesSize() {
        val template = "\${album}/\${size}_\${fileName}"
        val config = buildConfig(targetPath = "/base", expressionTargetPath = template)
        val history = buildHistory(config, Instant.parse("2024-05-06T12:00:00Z"))
        val album = buildAlbum("Photos")
        val asset = buildAsset(AssetType.IMAGE, "photo.jpg", album)

        val detail = CrontabHistoryDetail.init(history, asset)

        val expected = Path("Photos/123_photo.jpg").toString()
        assertEquals(expected, detail.filePath)
    }

    @Test
    fun expressionInterpolatesEpochTimestamps() {
        val template = "\${downloadEpochSeconds}_\${takenEpochSeconds}/\${fileName}"
        val config = buildConfig(targetPath = "/base", expressionTargetPath = template)
        val downloadTime = Instant.parse("2024-05-06T12:00:00Z")
        val history = buildHistory(config, downloadTime)
        val album = buildAlbum("Photos")
        val asset = buildAsset(AssetType.IMAGE, "photo.jpg", album)

        val detail = CrontabHistoryDetail.init(history, asset)

        val expected = Path("${downloadTime.epochSecond}_${asset.dateTaken.epochSecond}/photo.jpg").toString()
        assertEquals(expected, detail.filePath)
    }

    // ==================== 时间格式化测试 ====================

    @Test
    fun expressionFormatsDownloadTimeWithPattern() {
        val template = "\${download_yyyy-MM-dd}/\${fileName}"
        val config = buildConfig(targetPath = "/base", expressionTargetPath = template, timeZone = "UTC")
        val history = buildHistory(config, Instant.parse("2024-05-06T12:00:00Z"))
        val album = buildAlbum("Photos")
        val asset = buildAsset(AssetType.IMAGE, "photo.jpg", album)

        val detail = CrontabHistoryDetail.init(history, asset)

        val expected = Path("2024-05-06/photo.jpg").toString()
        assertEquals(expected, detail.filePath)
    }

    @Test
    fun expressionFormatsTakenWithPattern() {
        val template = "\${taken_yyyy}/\${taken_MM}/\${fileName}"
        val config = buildConfig(targetPath = "/base", expressionTargetPath = template, timeZone = "UTC")
        val history = buildHistory(config, Instant.parse("2024-05-06T12:00:00Z"))
        val album = buildAlbum("Photos")
        val asset = buildAsset(AssetType.IMAGE, "photo.jpg", album)

        val detail = CrontabHistoryDetail.init(history, asset)

        val expected = Path("2024/04/photo.jpg").toString()
        assertEquals(expected, detail.filePath)
    }

    @Test
    fun expressionFormatsTakenWithAliasPrefix() {
        val template = "\${taken_yyyyMMdd}/\${fileName}"
        val config = buildConfig(targetPath = "/base", expressionTargetPath = template, timeZone = "UTC")
        val history = buildHistory(config, Instant.parse("2024-05-06T12:00:00Z"))
        val album = buildAlbum("Photos")
        val asset = buildAsset(AssetType.IMAGE, "photo.jpg", album)

        val detail = CrontabHistoryDetail.init(history, asset)

        val expected = Path("20240401/photo.jpg").toString()
        assertEquals(expected, detail.filePath)
    }

    @Test
    fun expressionNormalizesYYYYToyyyy() {
        val template = "\${download_YYYY}/\${fileName}"
        val config = buildConfig(targetPath = "/base", expressionTargetPath = template, timeZone = "UTC")
        val history = buildHistory(config, Instant.parse("2024-05-06T12:00:00Z"))
        val album = buildAlbum("Photos")
        val asset = buildAsset(AssetType.IMAGE, "photo.jpg", album)

        val detail = CrontabHistoryDetail.init(history, asset)

        val expected = Path("2024/photo.jpg").toString()
        assertEquals(expected, detail.filePath)
    }

    @Test
    fun expressionFormatsTimeWithHourMinuteSecond() {
        val template = "\${download_HH-mm-ss}/\${fileName}"
        val config = buildConfig(targetPath = "/base", expressionTargetPath = template, timeZone = "UTC")
        val history = buildHistory(config, Instant.parse("2024-05-06T14:30:45Z"))
        val album = buildAlbum("Photos")
        val asset = buildAsset(AssetType.IMAGE, "photo.jpg", album)

        val detail = CrontabHistoryDetail.init(history, asset)

        val expected = Path("14-30-45/photo.jpg").toString()
        assertEquals(expected, detail.filePath)
    }

    // ==================== 时区处理测试 ====================

    @Test
    fun expressionRespectsTimeZone() {
        val template = "\${download_yyyy-MM-dd_HH}/\${fileName}"
        val config = buildConfig(targetPath = "/base", expressionTargetPath = template, timeZone = "Asia/Shanghai")
        // UTC 12:00 = Asia/Shanghai 20:00
        val history = buildHistory(config, Instant.parse("2024-05-06T12:00:00Z"))
        val album = buildAlbum("Photos")
        val asset = buildAsset(AssetType.IMAGE, "photo.jpg", album)

        val detail = CrontabHistoryDetail.init(history, asset)

        val expected = Path("2024-05-06_20/photo.jpg").toString()
        assertEquals(expected, detail.filePath)
    }

    @Test
    fun expressionHandlesDateCrossoverWithTimeZone() {
        val template = "\${download_yyyy-MM-dd}/\${fileName}"
        val config = buildConfig(targetPath = "/base", expressionTargetPath = template, timeZone = "Asia/Shanghai")
        // UTC 2024-05-06 20:00 = Asia/Shanghai 2024-05-07 04:00
        val history = buildHistory(config, Instant.parse("2024-05-06T20:00:00Z"))
        val album = buildAlbum("Photos")
        val asset = buildAsset(AssetType.IMAGE, "photo.jpg", album)

        val detail = CrontabHistoryDetail.init(history, asset)

        val expected = Path("2024-05-07/photo.jpg").toString()
        assertEquals(expected, detail.filePath)
    }

    @Test
    fun expressionHandlesNegativeOffsetTimeZone() {
        val template = "\${download_yyyy-MM-dd_HH}/\${fileName}"
        val config = buildConfig(targetPath = "/base", expressionTargetPath = template, timeZone = "America/New_York")
        // UTC 12:00 = America/New_York 08:00 (EDT, -4)
        val history = buildHistory(config, Instant.parse("2024-05-06T12:00:00Z"))
        val album = buildAlbum("Photos")
        val asset = buildAsset(AssetType.IMAGE, "photo.jpg", album)

        val detail = CrontabHistoryDetail.init(history, asset)

        val expected = Path("2024-05-06_08/photo.jpg").toString()
        assertEquals(expected, detail.filePath)
    }

    // ==================== 路径解析测试 ====================

    @Test
    fun expressionWithAbsolutePathIsNotPrependedWithTargetPath() {
        val template = "/absolute/path/\${album}/\${fileName}"
        val config = buildConfig(targetPath = "/base", expressionTargetPath = template)
        val history = buildHistory(config, Instant.parse("2024-05-06T12:00:00Z"))
        val album = buildAlbum("Photos")
        val asset = buildAsset(AssetType.IMAGE, "photo.jpg", album)

        val detail = CrontabHistoryDetail.init(history, asset)

        assertEquals("/absolute/path/Photos/photo.jpg", detail.filePath)
    }

    @Test
    fun expressionWithRelativePathKeepsRelativePath() {
        val template = "relative/\${album}/\${fileName}"
        val config = buildConfig(targetPath = "/base", expressionTargetPath = template)
        val history = buildHistory(config, Instant.parse("2024-05-06T12:00:00Z"))
        val album = buildAlbum("Photos")
        val asset = buildAsset(AssetType.IMAGE, "photo.jpg", album)

        val detail = CrontabHistoryDetail.init(history, asset)

        val expected = Path("relative/Photos/photo.jpg").toString()
        assertEquals(expected, detail.filePath)
    }

    @Test
    fun expressionNormalizesParentDirectoryReferences() {
        val template = "./foo/../bar/\${fileName}"
        val config = buildConfig(targetPath = "/base", expressionTargetPath = template)
        val history = buildHistory(config, Instant.parse("2024-05-06T12:00:00Z"))
        val album = buildAlbum("Photos")
        val asset = buildAsset(AssetType.IMAGE, "photo.jpg", album)

        val detail = CrontabHistoryDetail.init(history, asset)

        val expected = Path("bar/photo.jpg").toString()
        assertEquals(expected, detail.filePath)
    }

    @Test
    fun expressionNormalizesMultipleSlashes() {
        val template = "foo//bar///\${fileName}"
        val config = buildConfig(targetPath = "/base", expressionTargetPath = template)
        val history = buildHistory(config, Instant.parse("2024-05-06T12:00:00Z"))
        val album = buildAlbum("Photos")
        val asset = buildAsset(AssetType.IMAGE, "photo.jpg", album)

        val detail = CrontabHistoryDetail.init(history, asset)

        val expected = Path("foo/bar/photo.jpg").toString()
        assertEquals(expected, detail.filePath)
    }

    // ==================== 安全路径处理测试 ====================

    @Test
    fun expressionSanitizesColonInAlbumName() {
        val template = "\${album}/\${fileName}"
        val config = buildConfig(targetPath = "/base", expressionTargetPath = template)
        val history = buildHistory(config, Instant.parse("2024-05-06T12:00:00Z"))
        val album = buildAlbum("2024:05:06 Trip")
        val asset = buildAsset(AssetType.IMAGE, "photo.jpg", album)

        val detail = CrontabHistoryDetail.init(history, asset)

        val expected = Path("2024_05_06 Trip/photo.jpg").toString()
        assertEquals(expected, detail.filePath)
    }

    @Test
    fun expressionSanitizesQuestionMarkAndAsterisk() {
        val template = "\${album}/\${fileName}"
        val config = buildConfig(targetPath = "/base", expressionTargetPath = template)
        val history = buildHistory(config, Instant.parse("2024-05-06T12:00:00Z"))
        val album = buildAlbum("What*Ever?")
        val asset = buildAsset(AssetType.IMAGE, "photo.jpg", album)

        val detail = CrontabHistoryDetail.init(history, asset)

        val expected = Path("What_Ever_/photo.jpg").toString()
        assertEquals(expected, detail.filePath)
    }

    @Test
    fun expressionSanitizesAngleBrackets() {
        val template = "\${album}/\${fileName}"
        val config = buildConfig(targetPath = "/base", expressionTargetPath = template)
        val history = buildHistory(config, Instant.parse("2024-05-06T12:00:00Z"))
        val album = buildAlbum("<Album>")
        val asset = buildAsset(AssetType.IMAGE, "photo.jpg", album)

        val detail = CrontabHistoryDetail.init(history, asset)

        val expected = Path("_Album_/photo.jpg").toString()
        assertEquals(expected, detail.filePath)
    }

    @Test
    fun expressionSanitizesPipeAndQuotes() {
        val template = "\${album}/\${fileName}"
        val config = buildConfig(targetPath = "/base", expressionTargetPath = template)
        val history = buildHistory(config, Instant.parse("2024-05-06T12:00:00Z"))
        val album = buildAlbum("Test|\"Album\"")
        val asset = buildAsset(AssetType.IMAGE, "photo.jpg", album)

        val detail = CrontabHistoryDetail.init(history, asset)

        val expected = Path("Test__Album_/photo.jpg").toString()
        assertEquals(expected, detail.filePath)
    }

    @Test
    fun expressionSanitizesFileName() {
        val template = "\${album}/\${fileName}"
        val config = buildConfig(targetPath = "/base", expressionTargetPath = template)
        val history = buildHistory(config, Instant.parse("2024-05-06T12:00:00Z"))
        val album = buildAlbum("Photos")
        val asset = buildAssetWithFileName(AssetType.IMAGE, "photo:test?.jpg", album)

        val detail = CrontabHistoryDetail.init(history, asset)

        val expected = Path("Photos/photo_test_.jpg").toString()
        assertEquals(expected, detail.filePath)
    }

    // ==================== 边界情况测试 ====================

    @Test
    fun expressionHandlesFileWithoutExtension() {
        val template = "\${album}/\${fileStem}_copy.\${fileExt}"
        val config = buildConfig(targetPath = "/base", expressionTargetPath = template)
        val history = buildHistory(config, Instant.parse("2024-05-06T12:00:00Z"))
        val album = buildAlbum("Files")
        val asset = buildAsset(AssetType.IMAGE, "README", album)

        val detail = CrontabHistoryDetail.init(history, asset)

        val expected = Path("Files/README_copy.").toString()
        assertEquals(expected, detail.filePath)
    }

    @Test
    fun expressionHandlesFileWithMultipleDots() {
        val template = "\${album}/\${fileStem}.\${fileExt}"
        val config = buildConfig(targetPath = "/base", expressionTargetPath = template)
        val history = buildHistory(config, Instant.parse("2024-05-06T12:00:00Z"))
        val album = buildAlbum("Files")
        val asset = buildAsset(AssetType.IMAGE, "file.name.with.dots.jpg", album)

        val detail = CrontabHistoryDetail.init(history, asset)

        val expected = Path("Files/file.name.with.dots.jpg").toString()
        assertEquals(expected, detail.filePath)
    }

    @Test
    fun expressionPreservesUnknownTokens() {
        val template = "\${album}/\${unknownToken}/\${fileName}"
        val config = buildConfig(targetPath = "/base", expressionTargetPath = template)
        val history = buildHistory(config, Instant.parse("2024-05-06T12:00:00Z"))
        val album = buildAlbum("Photos")
        val asset = buildAsset(AssetType.IMAGE, "photo.jpg", album)

        val detail = CrontabHistoryDetail.init(history, asset)

        val expected = Path("Photos/\${unknownToken}/photo.jpg").toString()
        assertEquals(expected, detail.filePath)
    }

    @Test
    fun expressionHandlesInvalidTimePattern() {
        val template = "\${download_INVALID}/\${fileName}"
        val config = buildConfig(targetPath = "/base", expressionTargetPath = template, timeZone = "UTC")
        val history = buildHistory(config, Instant.parse("2024-05-06T12:00:00Z"))
        val album = buildAlbum("Photos")
        val asset = buildAsset(AssetType.IMAGE, "photo.jpg", album)

        val detail = CrontabHistoryDetail.init(history, asset)

        // Invalid pattern is preserved as-is
        val expected = Path("\${download_INVALID}/photo.jpg").toString()
        assertEquals(expected, detail.filePath)
    }

    @Test
    fun expressionHandlesEmptyAlbumName() {
        val template = "\${album}/\${fileName}"
        val config = buildConfig(targetPath = "/base", expressionTargetPath = template)
        val history = buildHistory(config, Instant.parse("2024-05-06T12:00:00Z"))
        val album = buildAlbum("")
        val asset = buildAsset(AssetType.IMAGE, "photo.jpg", album)

        val detail = CrontabHistoryDetail.init(history, asset)

        val expected = Path("/photo.jpg").normalize().toString()
        assertEquals(expected, detail.filePath)
    }

    @Test
    fun expressionHandlesChineseAlbumName() {
        val template = "\${album}/\${fileName}"
        val config = buildConfig(targetPath = "/base", expressionTargetPath = template)
        val history = buildHistory(config, Instant.parse("2024-05-06T12:00:00Z"))
        val album = buildAlbum("我的相册")
        val asset = buildAsset(AssetType.IMAGE, "photo.jpg", album)

        val detail = CrontabHistoryDetail.init(history, asset)

        val expected = Path("我的相册/photo.jpg").toString()
        assertEquals(expected, detail.filePath)
    }

    @Test
    fun expressionHandlesChineseFileName() {
        val template = "\${album}/\${fileName}"
        val config = buildConfig(targetPath = "/base", expressionTargetPath = template)
        val history = buildHistory(config, Instant.parse("2024-05-06T12:00:00Z"))
        val album = buildAlbum("Photos")
        val asset = buildAsset(AssetType.IMAGE, "照片.jpg", album)

        val detail = CrontabHistoryDetail.init(history, asset)

        val expected = Path("Photos/照片.jpg").toString()
        assertEquals(expected, detail.filePath)
    }

    // ==================== 初始化状态测试 ====================

    @Test
    fun initSetsDownloadCompletedToFalse() {
        val config = buildConfig(targetPath = "/base", expressionTargetPath = "")
        val history = buildHistory(config, Instant.parse("2024-05-06T12:00:00Z"))
        val album = buildAlbum("Photos")
        val asset = buildAsset(AssetType.IMAGE, "photo.jpg", album)

        val detail = CrontabHistoryDetail.init(history, asset)

        assertEquals(false, detail.downloadCompleted)
    }

    @Test
    fun initSetsSha1VerifiedBasedOnConfig() {
        val config = buildConfig(targetPath = "/base", expressionTargetPath = "", checkSha1 = true)
        val history = buildHistory(config, Instant.parse("2024-05-06T12:00:00Z"))
        val album = buildAlbum("Photos")
        val asset = buildAsset(AssetType.IMAGE, "photo.jpg", album)

        val detail = CrontabHistoryDetail.init(history, asset)

        assertEquals(false, detail.sha1Verified)
    }

    @Test
    fun initSetsSha1VerifiedToTrueWhenNotRequired() {
        val config = buildConfig(targetPath = "/base", expressionTargetPath = "", checkSha1 = false)
        val history = buildHistory(config, Instant.parse("2024-05-06T12:00:00Z"))
        val album = buildAlbum("Photos")
        val asset = buildAsset(AssetType.IMAGE, "photo.jpg", album)

        val detail = CrontabHistoryDetail.init(history, asset)

        assertEquals(true, detail.sha1Verified)
    }

    @Test
    fun initSetsExifFilledBasedOnConfig() {
        val config = buildConfig(targetPath = "/base", expressionTargetPath = "", rewriteExifTime = true)
        val history = buildHistory(config, Instant.parse("2024-05-06T12:00:00Z"))
        val album = buildAlbum("Photos")
        val asset = buildAsset(AssetType.IMAGE, "photo.jpg", album)

        val detail = CrontabHistoryDetail.init(history, asset)

        assertEquals(false, detail.exifFilled)
    }

    @Test
    fun initSetsExifFilledToTrueWhenNotRequired() {
        val config = buildConfig(targetPath = "/base", expressionTargetPath = "", rewriteExifTime = false)
        val history = buildHistory(config, Instant.parse("2024-05-06T12:00:00Z"))
        val album = buildAlbum("Photos")
        val asset = buildAsset(AssetType.IMAGE, "photo.jpg", album)

        val detail = CrontabHistoryDetail.init(history, asset)

        assertEquals(true, detail.exifFilled)
    }

    @Test
    fun initSetsFsTimeUpdatedBasedOnConfig() {
        val config = buildConfig(targetPath = "/base", expressionTargetPath = "", rewriteFileSystemTime = true)
        val history = buildHistory(config, Instant.parse("2024-05-06T12:00:00Z"))
        val album = buildAlbum("Photos")
        val asset = buildAsset(AssetType.IMAGE, "photo.jpg", album)

        val detail = CrontabHistoryDetail.init(history, asset)

        assertEquals(false, detail.fsTimeUpdated)
    }

    @Test
    fun initSetsFsTimeUpdatedToTrueWhenNotRequired() {
        val config = buildConfig(targetPath = "/base", expressionTargetPath = "", rewriteFileSystemTime = false)
        val history = buildHistory(config, Instant.parse("2024-05-06T12:00:00Z"))
        val album = buildAlbum("Photos")
        val asset = buildAsset(AssetType.IMAGE, "photo.jpg", album)

        val detail = CrontabHistoryDetail.init(history, asset)

        assertEquals(true, detail.fsTimeUpdated)
    }

    private fun buildConfig(
        targetPath: String,
        expressionTargetPath: String,
        timeZone: String = "UTC",
        checkSha1: Boolean = false,
        rewriteExifTime: Boolean = false,
        rewriteFileSystemTime: Boolean = false,
    ): CrontabConfig {
        return CrontabConfig(
            expression = "0 0 * * * ?",
            timeZone = timeZone,
            targetPath = targetPath,
            downloadImages = true,
            downloadVideos = true,
            rewriteExifTime = rewriteExifTime,
            diffByTimeline = false,
            rewriteExifTimeZone = null,
            skipExistingFile = true,
            rewriteFileSystemTime = rewriteFileSystemTime,
            checkSha1 = checkSha1,
            fetchFromDbSize = 2,
            downloaders = 1,
            verifiers = 1,
            exifProcessors = 1,
            fileTimeWorkers = 1,
            downloadAudios = true,
            expressionTargetPath = expressionTargetPath,
        )
    }

    private fun buildHistory(config: CrontabConfig, startTime: Instant): CrontabHistory {
        val crontab = Crontab {
            id = 1L
            name = "Test"
            description = "Test"
            enabled = true
            this.config = config
        }

        return CrontabHistory {
            id = 1L
            this.crontab = crontab
            this.startTime = startTime
            timelineSnapshot = emptyMap()
            fetchedAllAssets = false
        }
    }

    private fun buildAlbum(name: String): Album {
        return Album {
            id = 10L
            remoteId = 20L
            this.name = name
            assetCount = 0L
            lastUpdateTime = Instant.parse("2024-01-01T00:00:00Z")
        }
    }

    private fun buildAsset(
        type: AssetType,
        fileName: String,
        album: Album,
        id: Long = 100L,
    ): Asset {
        return Asset {
            this.id = id
            this.fileName = fileName
            this.type = type
            dateTaken = Instant.parse("2024-04-01T00:00:00Z")
            this.album = album
            sha1 = "deadbeef"
            mimeType = "image/jpeg"
            title = "title"
            size = 123L
        }
    }

    private fun buildAssetWithFileName(
        type: AssetType,
        fileName: String,
        album: Album,
        id: Long = 100L,
    ): Asset {
        return Asset {
            this.id = id
            this.fileName = fileName
            this.type = type
            dateTaken = Instant.parse("2024-04-01T00:00:00Z")
            this.album = album
            sha1 = "deadbeef"
            mimeType = "image/jpeg"
            title = "title"
            size = 123L
        }
    }
}
