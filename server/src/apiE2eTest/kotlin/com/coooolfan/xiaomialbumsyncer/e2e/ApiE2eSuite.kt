package com.coooolfan.xiaomialbumsyncer.e2e

import com.fasterxml.jackson.databind.JsonNode
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Comparator

class ApiE2eSuite {

    @Test
    fun fullApiWorkflow() {
        val workDir = Files.createTempDirectory("xiaomi-api-e2e-")
        try {
            MockXiaomiApiServer.start().use { mock ->
                ApplicationProcess.start(mock.baseUrl, workDir).use { application ->
                    try {
                        executeWorkflow(application.api, mock, workDir)
                    } catch (e: Throwable) {
                        throw AssertionError("API E2E 执行失败: ${e.message}\n\n应用日志:\n${application.logs()}", e)
                    }
                }
                mock.assertNoUnexpectedRequests()
            }
        } finally {
            deleteRecursively(workDir)
        }
    }

    private fun executeWorkflow(api: ApiClient, mock: MockXiaomiApiServer, workDir: Path) {
        assertFalse(api.json(api.get("/api/system-config").expect(200)).path("init").asBoolean())
        api.post("/api/system-config", mapOf("password" to "e2e-password")).expect(200)
        api.get("/api/token?password=${api.encode("wrong-password")}").expect(401)
        api.get("/api/token?password=${api.encode("e2e-password")}").expect(200)

        assertTrue(api.json(api.get("/api/system-config").expect(200)).path("init").asBoolean())
        api.get("/api/system-config/normal").expect(200)
        api.post("/api/system-config/normal", mapOf("exifToolPath" to "exiftool")).expect(200)
        api.get("/api/system-config/info").expect(200)
        api.get("/api/system-config/info/debug").expect(200)
        api.post("/api/system-config/mount-path", mapOf("path" to workDir.toString())).expect(200)
        api.get("/api/system-config/notify-config").expect(200)
        api.post(
            "/api/system-config/notify-config",
            mapOf(
                "notifyConfig" to linkedMapOf<String, Any?>(
                    "url" to "${mock.baseUrl}/_control/v1/notify",
                    "headers" to mapOf("Content-Type" to "application/json"),
                    "body" to "{\"text\":\"${'$'}{crontab.name}\",\"success\":\"${'$'}{success}/${'$'}{total}\"}",
                    "dailySummaryBody" to null,
                    "dailySummaryCron" to null,
                    "dailySummaryTimeZone" to null,
                )
            )
        ).expect(200)

        api.get("/api/openapi.yml").expect(200)
        api.get("/api/openapi.zip").expect(200)
        api.get("/api/openapi.html").expect(200)

        api.get("/api/passkey/available").expect(200)
        api.get("/api/passkey").expect(200)
        val registerStart = api.post(
            "/api/passkey/register/start",
            mapOf("password" to "e2e-password", "credentialName" to "e2e-passkey")
        ).expect(200)
        assertTrue(api.json(registerStart).path("challenge").asText().isNotBlank())
        api.post("/api/passkey/authenticate/start").expect(500)

        val account = api.json(
            api.post(
                "/api/account",
                mapOf(
                    "nickname" to "Mock Xiaomi",
                    "passToken" to "mock-pass-token",
                    "userId" to "mock-user",
                )
            ).expect(200)
        )
        val accountId = account.path("id").asLong()
        api.get("/api/account").expect(200)
        api.put(
            "/api/account/$accountId",
            mapOf(
                "nickname" to "Mock Xiaomi Updated",
                "passToken" to "mock-pass-token",
                "userId" to "mock-user",
            )
        ).expect(200)

        val albums = api.json(api.get("/api/album/latest/$accountId").expect(200))
        assertEquals(3, albums.size())
        val cameraAlbumId = findAlbumId(albums, "1")
        val audioAlbumId = findAlbumId(albums, "-1")
        api.get("/api/album").expect(200)

        val cameraAssets = api.json(api.get("/api/asset/$cameraAlbumId/latest").expect(200))
        assertEquals(mock.mediaSha1, cameraAssets.first().path("sha1").asText())
        api.get("/api/asset/$cameraAlbumId").expect(200)
        val audioAssets = api.json(api.get("/api/asset/$audioAlbumId/latest").expect(200))
        assertEquals("sample-audio.m4a", audioAssets.first().path("fileName").asText())
        api.get("/api/album/date-map?albumIds=$cameraAlbumId").expect(200)

        val downloadDir = workDir.resolve("downloads")
        val config = linkedMapOf<String, Any?>(
            "expression" to "0 0 0 1 1 ? 2099",
            "timeZone" to "UTC",
            "targetPath" to downloadDir.toString(),
            "downloadImages" to true,
            "downloadVideos" to false,
            "rewriteExifTime" to false,
            "diffByTimeline" to false,
            "rewriteExifTimeZone" to "UTC",
            "skipExistingFile" to false,
            "rewriteFileSystemTime" to false,
            "checkSha1" to true,
            "fetchFromDbSize" to 2,
            "downloaders" to 1,
            "verifiers" to 1,
            "exifProcessors" to 1,
            "fileTimeWorkers" to 1,
            "downloadAudios" to false,
            "expressionTargetPath" to "",
            "notify" to true,
        )
        val crontabBody = linkedMapOf<String, Any?>(
            "name" to "API E2E",
            "description" to "Native metadata coverage",
            "enabled" to false,
            "config" to config,
            "accountId" to accountId,
            "albumIds" to listOf(cameraAlbumId),
        )
        val crontab = api.json(api.post("/api/crontab", crontabBody).expect(200))
        val crontabId = crontab.path("id").asLong()
        api.put("/api/crontab/$crontabId", mapOf("description" to "Native metadata E2E")).expect(200)
        api.get("/api/crontab").expect(200)
        api.post("/api/crontab/$crontabId/current").expect(200)
        api.post("/api/crontab/$crontabId/executions").expect(200)

        val historyId = awaitCompletedHistory(api, crontabId)
        val detailPage = api.json(
            api.get("/api/crontab/history/$historyId/details?pageIndex=0&pageSize=10").expect(200)
        )
        assertEquals(1, detailPage.path("totalRowCount").asInt())
        val detail = detailPage.path("rows").first()
        assertTrue(detail.path("downloadCompleted").asBoolean())
        assertTrue(detail.path("sha1Verified").asBoolean())
        assertTrue(detail.path("exifFilled").asBoolean())
        assertTrue(detail.path("fsTimeUpdated").asBoolean())
        assertTrue(detail.path("message").isMissingNode || detail.path("message").isNull)

        val downloadedFile = Path.of(detail.path("filePath").asText())
        assertTrue(Files.isRegularFile(downloadedFile), "下载文件不存在: $downloadedFile")
        assertArrayEquals(mock.mediaBytes, Files.readAllBytes(downloadedFile))
        mock.awaitRequest("/_control/v1/notify", Duration.ofSeconds(10))

        assertEquals(1, mock.routeCount("/gallery/user/album/list"))
        assertTrue(mock.routeCount("/gallery/user/galleries") >= 2)
        assertTrue(mock.routeCount("/gallery/user/timeline") >= 1)
        assertTrue(mock.routeCount("/gallery/storage") >= 1)
        assertTrue(mock.routeCount("/mock/oss/101") >= 1)
        assertTrue(mock.routeCount("/mock/download/101") >= 1)

        api.delete("/api/crontab/$crontabId/histories").expect(200)
        val historiesAfterClear = api.json(api.get("/api/crontab").expect(200))
            .first { it.path("id").asLong() == crontabId }
            .path("histories")
        assertTrue(
            historiesAfterClear.isArray && historiesAfterClear.size() == 0,
            "清理历史后 histories 应为空: $historiesAfterClear"
        )

        api.delete("/api/crontab/$crontabId").expect(200)

        executeRecordingWorkflows(
            api = api,
            mock = mock,
            workDir = workDir,
            accountId = accountId,
            cameraAlbumId = cameraAlbumId,
            audioAlbumId = audioAlbumId,
            baseConfig = config,
        )

        api.delete("/api/account/$accountId").expect(200)
        api.post(
            "/api/system-config/password",
            mapOf("oldPassword" to "e2e-password", "password" to "e2e-password-updated")
        ).expect(200)
        api.delete("/api/token").expect(200)
        api.get("/api/account").expect(401)
    }

    private fun executeRecordingWorkflows(
        api: ApiClient,
        mock: MockXiaomiApiServer,
        workDir: Path,
        accountId: Long,
        cameraAlbumId: Long,
        audioAlbumId: Long,
        baseConfig: LinkedHashMap<String, Any?>,
    ) {
        val recordingConfig = LinkedHashMap(baseConfig).apply {
            this["targetPath"] = workDir.resolve("recording-downloads").toString()
            this["downloadImages"] = false
            this["downloadVideos"] = false
            this["downloadAudios"] = true
            this["notify"] = false
            this["diffByTimeline"] = true
        }
        val recordingCrontab = api.json(
            api.post(
                "/api/crontab",
                linkedMapOf<String, Any?>(
                    "name" to "Recording API E2E",
                    "description" to "Recorder is independent from gallery",
                    "enabled" to false,
                    "config" to recordingConfig,
                    "accountId" to accountId,
                    "albumIds" to listOf(audioAlbumId),
                )
            ).expect(200)
        )
        val recordingCrontabId = recordingCrontab.path("id").asLong()
        api.post("/api/crontab/$recordingCrontabId/executions").expect(200)
        val recordingHistoryId = awaitCompletedHistory(api, recordingCrontabId)
        assertCompletedDetailCount(api, recordingHistoryId, 1)
        api.delete("/api/crontab/$recordingCrontabId").expect(200)

        val mixedConfig = LinkedHashMap(baseConfig).apply {
            this["targetPath"] = workDir.resolve("mixed-downloads").toString()
            this["downloadImages"] = true
            this["downloadVideos"] = false
            this["downloadAudios"] = true
            this["notify"] = false
            this["diffByTimeline"] = true
        }
        val mixedCrontab = api.json(
            api.post(
                "/api/crontab",
                linkedMapOf<String, Any?>(
                    "name" to "Mixed API E2E",
                    "description" to "Gallery and recorder full refresh",
                    "enabled" to false,
                    "config" to mixedConfig,
                    "accountId" to accountId,
                    "albumIds" to listOf(cameraAlbumId, audioAlbumId),
                )
            ).expect(200)
        )
        val mixedCrontabId = mixedCrontab.path("id").asLong()
        api.post("/api/crontab/$mixedCrontabId/executions").expect(200)
        val mixedHistoryId = awaitCompletedHistory(api, mixedCrontabId)
        assertCompletedDetailCount(api, mixedHistoryId, 2)
        api.delete("/api/crontab/$mixedCrontabId").expect(200)

        assertTrue(mock.routeCount("/sfs/ns/recorder/dir/0/list") >= 3)
        assertTrue(mock.routePrefixCount("/sfs/ns/recorder/file/201/cb/") >= 2)
        assertEquals(0, mock.timelineCount(-1), "录音是独立远端资源，不应请求 gallery timeline 的 -1")
        assertTrue(mock.timelineCount(1) >= 2, "混合任务应只为真实相册请求时间线")
    }

    private fun assertCompletedDetailCount(api: ApiClient, historyId: Long, expected: Int) {
        val detailPage = api.json(
            api.get("/api/crontab/history/$historyId/details?pageIndex=0&pageSize=10").expect(200)
        )
        assertEquals(expected, detailPage.path("totalRowCount").asInt())
        detailPage.path("rows").forEach { detail ->
            assertTrue(detail.path("downloadCompleted").asBoolean())
            assertTrue(detail.path("sha1Verified").asBoolean())
            assertTrue(detail.path("message").isMissingNode || detail.path("message").isNull)
        }
    }

    private fun findAlbumId(albums: JsonNode, remoteId: String): Long {
        return albums.firstOrNull { it.path("remoteId").asText() == remoteId }
            ?.path("id")
            ?.asLong()
            ?: error("未找到 remoteId=$remoteId 的相册，响应: $albums")
    }

    private fun awaitCompletedHistory(api: ApiClient, crontabId: Long): Long {
        val deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos()
        while (System.nanoTime() < deadline) {
            val crontabs = api.json(api.get("/api/crontab").expect(200))
            val crontab = crontabs.firstOrNull { it.path("id").asLong() == crontabId }
            val history = crontab?.path("histories")?.firstOrNull()
            if (history != null && history.path("endTime").asText().isNotBlank()) {
                return history.path("id").asLong()
            }
            Thread.sleep(100)
        }
        error("等待定时任务完成超时")
    }

    private fun deleteRecursively(path: Path) {
        if (!Files.exists(path)) return
        Files.walk(path).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
}
