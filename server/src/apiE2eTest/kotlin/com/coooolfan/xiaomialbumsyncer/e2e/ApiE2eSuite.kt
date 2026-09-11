package com.coooolfan.xiaomialbumsyncer.e2e

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
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

    private val mcpJson = ObjectMapper()

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

        executeMcpWorkflow(api, crontabId, historyId)

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

        executeDeletedMediaWorkflows(
            api = api,
            mock = mock,
            workDir = workDir,
            accountId = accountId,
            cameraAlbumId = cameraAlbumId,
            audioAlbumId = audioAlbumId,
            baseConfig = config,
        )

        executeTransientStorageErrorWorkflow(
            api = api,
            mock = mock,
            workDir = workDir,
            accountId = accountId,
            cameraAlbumId = cameraAlbumId,
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

    /**
     * 覆盖 /mcp 端点：Bearer 鉴权、initialize/tools/list/tools/call 全 domain 调用，
     * 让 Native Image tracing agent 采集到 MCP 链路的反射与资源元数据。
     */
    private fun executeMcpWorkflow(api: ApiClient, crontabId: Long, historyId: Long) {
        val mcpToken = "e2e-mcp-token"
        api.post(
            "/api/system-config/normal",
            mapOf("exifToolPath" to "exiftool", "mcpToken" to mcpToken)
        ).expect(200)

        // 未携带或携带错误 token 的请求一律 401
        mcpRequest(api, null, null, 0, "initialize").expect(401)
        mcpRequest(api, "wrong-token", null, 0, "initialize").expect(401)

        val init = mcpRequest(
            api, mcpToken, null, 0, "initialize",
            mapOf(
                "protocolVersion" to "2025-06-18",
                "capabilities" to emptyMap<String, Any>(),
                "clientInfo" to mapOf("name" to "e2e", "version" to "0"),
            )
        ).expect(200)
        val sessionId = init.headers.firstValue("mcp-session-id").orElse("").ifEmpty { null }

        mcpRequest(api, mcpToken, sessionId, null, "notifications/initialized").let {
            check(it.status == 200 || it.status == 202) { "notifications/initialized 返回 ${it.status}: ${it.body}" }
        }

        val tools = mcpResult(mcpRequest(api, mcpToken, sessionId, 1, "tools/list"))
        assertTrue(
            tools.path("result").path("tools").any { it.path("name").asText() == "xas_query" },
            "tools/list 应包含 xas_query: $tools"
        )

        var callId = 10
        fun callTool(arguments: Map<String, Any?>): JsonNode {
            val response = mcpResult(
                mcpRequest(
                    api, mcpToken, sessionId, callId++, "tools/call",
                    mapOf("name" to "xas_query", "arguments" to arguments)
                )
            )
            assertFalse(
                response.path("result").path("isError").asBoolean(),
                "tools/call $arguments 返回错误: $response"
            )
            return response
        }

        callTool(mapOf("domain" to "help", "action" to "list"))
        callTool(mapOf("domain" to "album", "action" to "list"))
        callTool(mapOf("domain" to "crontab", "action" to "list"))
        callTool(
            mapOf(
                "domain" to "crontab", "action" to "get",
                "filter" to mapOf("id" to crontabId.toString())
            )
        )
        callTool(mapOf("domain" to "crontab_history", "action" to "list"))
        callTool(
            mapOf(
                "domain" to "crontab_history_detail", "action" to "list",
                "filter" to mapOf("id" to historyId.toString())
            )
        )
        callTool(mapOf("domain" to "system", "action" to "list"))

        // 非法入参应返回 isError=true 的 CallToolResult，而非协议级错误
        val badCall = mcpResult(
            mcpRequest(
                api, mcpToken, sessionId, callId++, "tools/call",
                mapOf("name" to "xas_query", "arguments" to mapOf("domain" to "not_exist", "action" to "list"))
            )
        )
        assertTrue(
            badCall.path("result").path("isError").asBoolean(),
            "非法 domain 应返回 isError=true: $badCall"
        )

        // trigger 放最后：异步执行一轮流水线，不阻塞后续清理
        callTool(
            mapOf(
                "domain" to "crontab", "action" to "trigger",
                "filter" to mapOf("id" to crontabId.toString())
            )
        )

        api.delete(
            "/mcp",
            buildMap {
                put("Authorization", "Bearer $mcpToken")
                sessionId?.let { put("mcp-session-id", it) }
            }
        )
    }

    private fun mcpRequest(
        api: ApiClient,
        token: String?,
        sessionId: String?,
        id: Int?,
        method: String,
        params: Any? = null,
    ): ApiClient.Response {
        val body = linkedMapOf<String, Any?>("jsonrpc" to "2.0", "method" to method)
        id?.let { body["id"] = it }
        params?.let { body["params"] = it }
        val headers = buildMap {
            put("Accept", "application/json, text/event-stream")
            token?.let { put("Authorization", "Bearer $it") }
            sessionId?.let { put("mcp-session-id", it) }
        }
        return api.postOnNewConnection("/mcp", body, headers)
    }

    private fun mcpResult(response: ApiClient.Response): JsonNode {
        val contentType = response.headers.firstValue("Content-Type").orElse("")
        val payload = if (contentType.contains("text/event-stream")) {
            response.body.lineSequence()
                .filter { it.startsWith("data:") }
                .map { it.removePrefix("data:").trim() }
                .lastOrNull() ?: throw AssertionError("SSE 响应无 data 行: ${response.body}")
        } else {
            response.body
        }
        return mcpJson.readTree(payload)
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

    /**
     * 云端已删除资产（列表仍可见但 storage 不可用）的下载应被优雅跳过，
     * 而不是抛异常导致流水线在每个周期反复失败。
     * 相册删除 → code=50050；录音删除 → code=50202；两者均 retriable=false。
     */
    private fun executeDeletedMediaWorkflows(
        api: ApiClient,
        mock: MockXiaomiApiServer,
        workDir: Path,
        accountId: Long,
        cameraAlbumId: Long,
        audioAlbumId: Long,
        baseConfig: LinkedHashMap<String, Any?>,
    ) {
        // ============ 相册：storage 返回 code=50050 ============
        val galleryConfig = LinkedHashMap(baseConfig).apply {
            this["targetPath"] = workDir.resolve("deleted-gallery-downloads").toString()
            this["downloadImages"] = true
            this["downloadVideos"] = false
            this["downloadAudios"] = false
            this["notify"] = false
            this["diffByTimeline"] = false
            this["skipExistingFile"] = false
        }
        val galleryCrontab = api.json(
            api.post(
                "/api/crontab",
                linkedMapOf<String, Any?>(
                    "name" to "Deleted Gallery E2E",
                    "description" to "云端已删除的相册资产应被跳过而非反复失败",
                    "enabled" to false,
                    "config" to galleryConfig,
                    "accountId" to accountId,
                    "albumIds" to listOf(cameraAlbumId),
                )
            ).expect(200)
        )
        val galleryCrontabId = galleryCrontab.path("id").asLong()

        // 第一轮：正常下载资产 101
        api.post("/api/crontab/$galleryCrontabId/executions").expect(200)
        awaitCompletedHistory(api, galleryCrontabId)

        // 云端新增资产 103 后被删除：仍出现在列表中，但 storage 返回 code=50050
        mock.mutate(
            mapOf(
                "operations" to listOf(
                    mapOf(
                        "op" to "addAssets",
                        "userId" to "mock-user",
                        "albumId" to 1,
                        "assets" to listOf(
                            mapOf(
                                "id" to 103,
                                "type" to "image",
                                "fileName" to "deleted-photo.jpg",
                                "dateTaken" to 1714651200000L,
                                "size" to 28,
                                "sha1Mode" to "exact",
                                "contentPattern" to "xiaomi-album-syncer-api-e2e\n",
                            )
                        ),
                    ),
                    mapOf(
                        "op" to "markDeleted",
                        "userId" to "mock-user",
                        "albumId" to 1,
                        "ids" to listOf(103),
                    ),
                )
            )
        )

        // 清空下载历史，让 101 与 103 同时进入第二轮流水线
        api.delete("/api/crontab/$galleryCrontabId/histories").expect(200)
        api.post("/api/crontab/$galleryCrontabId/executions").expect(200)
        val galleryHistoryId = awaitCompletedHistory(api, galleryCrontabId)
        val galleryDetails = api.json(
            api.get("/api/crontab/history/$galleryHistoryId/details?pageIndex=0&pageSize=10").expect(200)
        )
        assertEquals(2, galleryDetails.path("totalRowCount").asInt())
        galleryDetails.path("rows").forEach { detail ->
            assertTrue(detail.path("downloadCompleted").asBoolean(), "资产 ${detail.path("asset").path("id")} 应标记下载完成")
            assertTrue(detail.path("message").isMissingNode || detail.path("message").isNull, "跳过不应产生错误消息")
        }
        val deletedGallery = galleryDetails.path("rows").first { it.path("asset").path("id").asText() == "103" }
        assertFalse(Files.exists(Path.of(deletedGallery.path("filePath").asText())), "已删除资产不应产出文件")
        assertEquals(0, mock.routePrefixCount("/mock/oss/103"), "已删除资产不应请求 OSS 签名直链")
        api.delete("/api/crontab/$galleryCrontabId").expect(200)

        // ============ 录音：storage 返回 code=50202 ============
        val recordingConfig = LinkedHashMap(baseConfig).apply {
            this["targetPath"] = workDir.resolve("deleted-recording-downloads").toString()
            this["downloadImages"] = false
            this["downloadVideos"] = false
            this["downloadAudios"] = true
            this["notify"] = false
            this["diffByTimeline"] = false
            this["skipExistingFile"] = false
        }
        val recordingCrontab = api.json(
            api.post(
                "/api/crontab",
                linkedMapOf<String, Any?>(
                    "name" to "Deleted Recording E2E",
                    "description" to "云端已删除的录音应被跳过而非反复失败",
                    "enabled" to false,
                    "config" to recordingConfig,
                    "accountId" to accountId,
                    "albumIds" to listOf(audioAlbumId),
                )
            ).expect(200)
        )
        val recordingCrontabId = recordingCrontab.path("id").asLong()

        api.post("/api/crontab/$recordingCrontabId/executions").expect(200)
        awaitCompletedHistory(api, recordingCrontabId)

        // 云端新增录音 203 后被删除：列表可见，storage 返回 code=50202
        mock.mutate(
            mapOf(
                "operations" to listOf(
                    mapOf(
                        "op" to "addRecordings",
                        "userId" to "mock-user",
                        "recordings" to listOf(
                            mapOf(
                                "id" to 203,
                                "fileName" to "deleted-recording.m4a",
                                "recordingType" to 0,
                                "createTime" to 1714651200000L,
                                "size" to 28,
                                "sha1Mode" to "exact",
                                "contentPattern" to "xiaomi-album-syncer-api-e2e\n",
                            )
                        ),
                    ),
                    mapOf(
                        "op" to "markDeleted",
                        "userId" to "mock-user",
                        "ids" to listOf(203),
                    ),
                )
            )
        )

        api.delete("/api/crontab/$recordingCrontabId/histories").expect(200)
        api.post("/api/crontab/$recordingCrontabId/executions").expect(200)
        val recordingHistoryId = awaitCompletedHistory(api, recordingCrontabId)
        val recordingDetails = api.json(
            api.get("/api/crontab/history/$recordingHistoryId/details?pageIndex=0&pageSize=10").expect(200)
        )
        assertEquals(2, recordingDetails.path("totalRowCount").asInt())
        recordingDetails.path("rows").forEach { detail ->
            assertTrue(detail.path("downloadCompleted").asBoolean(), "资产 ${detail.path("asset").path("id")} 应标记下载完成")
            assertTrue(detail.path("message").isMissingNode || detail.path("message").isNull, "跳过不应产生错误消息")
        }
        val deletedRecording = recordingDetails.path("rows").first { it.path("asset").path("id").asText() == "203" }
        assertFalse(Files.exists(Path.of(deletedRecording.path("filePath").asText())), "已删除录音不应产出文件")
        assertEquals(0, mock.routePrefixCount("/mock/oss/203"), "已删除录音不应请求 OSS 签名直链")
        api.delete("/api/crontab/$recordingCrontabId").expect(200)
    }

    /**
     * storage 返回 retriable=true 的瞬时错误时，资产应被记录为失败并在下一个周期重试，
     * 错误恢复后下载成功——区别于 retriable=false 的永久跳过。
     */
    private fun executeTransientStorageErrorWorkflow(
        api: ApiClient,
        mock: MockXiaomiApiServer,
        workDir: Path,
        accountId: Long,
        cameraAlbumId: Long,
        baseConfig: LinkedHashMap<String, Any?>,
    ) {
        val config = LinkedHashMap(baseConfig).apply {
            this["targetPath"] = workDir.resolve("transient-downloads").toString()
            this["downloadImages"] = true
            this["downloadVideos"] = false
            this["downloadAudios"] = false
            this["notify"] = false
            this["diffByTimeline"] = false
            this["skipExistingFile"] = false
        }
        val crontab = api.json(
            api.post(
                "/api/crontab",
                linkedMapOf<String, Any?>(
                    "name" to "Transient Storage Error E2E",
                    "description" to "retriable=true 的瞬时错误应在下个周期重试并最终成功",
                    "enabled" to false,
                    "config" to config,
                    "accountId" to accountId,
                    "albumIds" to listOf(cameraAlbumId),
                )
            ).expect(200)
        )
        val crontabId = crontab.path("id").asLong()

        // 第一轮：基线运行（相册中此时还有上一场景遗留的已删除资产，应被跳过而非失败）
        api.post("/api/crontab/$crontabId/executions").expect(200)
        val baselineHistoryId = awaitCompletedHistory(api, crontabId)

        // 注入瞬时错误：资产 104 的 storage 返回 code=50051, retriable=true
        mock.mutate(
            mapOf(
                "operations" to listOf(
                    mapOf(
                        "op" to "addAssets",
                        "userId" to "mock-user",
                        "albumId" to 1,
                        "assets" to listOf(
                            mapOf(
                                "id" to 104,
                                "type" to "image",
                                "fileName" to "transient-photo.jpg",
                                "dateTaken" to 1714651200000L,
                                "size" to 28,
                                "sha1Mode" to "exact",
                                "contentPattern" to "xiaomi-album-syncer-api-e2e\n",
                            )
                        ),
                    ),
                    mapOf(
                        "op" to "setStorageError",
                        "userId" to "mock-user",
                        "albumId" to 1,
                        "ids" to listOf(104),
                        "code" to 50051,
                        "retriable" to true,
                        "description" to "系统繁忙，请稍后重试",
                        "reason" to "service busy",
                    ),
                )
            )
        )

        // 第二轮：104 下载失败，应记录错误消息且不标记完成，但流水线整体正常结束
        api.post("/api/crontab/$crontabId/executions").expect(200)
        val failedHistoryId = awaitCompletedHistory(api, crontabId, afterHistoryId = baselineHistoryId)
        val failedDetails = api.json(
            api.get("/api/crontab/history/$failedHistoryId/details?pageIndex=0&pageSize=10").expect(200)
        )
        assertEquals(1, failedDetails.path("totalRowCount").asInt())
        val failedDetail = failedDetails.path("rows").first()
        assertEquals("104", failedDetail.path("asset").path("id").asText())
        assertFalse(failedDetail.path("downloadCompleted").asBoolean(), "瞬时错误不应标记下载完成")
        assertTrue(failedDetail.path("message").asText().contains("50051"), "失败消息应包含错误码")

        // 云端恢复后，下一周期应重试并下载成功
        mock.mutate(
            mapOf(
                "operations" to listOf(
                    mapOf(
                        "op" to "setStorageError",
                        "userId" to "mock-user",
                        "albumId" to 1,
                        "ids" to listOf(104),
                        "code" to 0,
                    )
                )
            )
        )
        api.post("/api/crontab/$crontabId/executions").expect(200)
        val recoveredHistoryId = awaitCompletedHistory(api, crontabId, afterHistoryId = failedHistoryId)
        val recoveredDetails = api.json(
            api.get("/api/crontab/history/$recoveredHistoryId/details?pageIndex=0&pageSize=10").expect(200)
        )
        assertEquals(1, recoveredDetails.path("totalRowCount").asInt())
        val recoveredDetail = recoveredDetails.path("rows").first()
        assertEquals("104", recoveredDetail.path("asset").path("id").asText())
        assertTrue(recoveredDetail.path("downloadCompleted").asBoolean(), "恢复后应下载成功")
        assertTrue(recoveredDetail.path("message").isMissingNode || recoveredDetail.path("message").isNull)
        assertTrue(Files.exists(Path.of(recoveredDetail.path("filePath").asText())), "恢复后应产出文件")
        assertTrue(mock.routePrefixCount("/mock/oss/104") >= 1, "恢复后应请求 OSS 签名直链")
        api.delete("/api/crontab/$crontabId").expect(200)
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

    private fun awaitCompletedHistory(api: ApiClient, crontabId: Long, afterHistoryId: Long? = null): Long {
        val deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos()
        while (System.nanoTime() < deadline) {
            val crontabs = api.json(api.get("/api/crontab").expect(200))
            val crontab = crontabs.firstOrNull { it.path("id").asLong() == crontabId }
            val history = crontab?.path("histories")?.firstOrNull {
                (afterHistoryId == null || it.path("id").asLong() > afterHistoryId) &&
                        it.path("endTime").asText().isNotBlank()
            }
            if (history != null) {
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
