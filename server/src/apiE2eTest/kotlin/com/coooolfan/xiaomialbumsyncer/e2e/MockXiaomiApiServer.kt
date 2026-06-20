package com.coooolfan.xiaomialbumsyncer.e2e

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

class MockXiaomiApiServer private constructor(
    private val server: HttpServer,
) : AutoCloseable {

    data class RecordedRequest(
        val method: String,
        val path: String,
        val query: Map<String, String>,
        val headers: Map<String, List<String>>,
        val body: ByteArray,
    )

    private val executor = Executors.newCachedThreadPool()
    private val recordedRequests = CopyOnWriteArrayList<RecordedRequest>()
    private val unexpectedRequests = CopyOnWriteArrayList<String>()

    val mediaBytes: ByteArray = "xiaomi-album-syncer-api-e2e\n".toByteArray(StandardCharsets.UTF_8)
    val mediaSha1: String = MessageDigest.getInstance("SHA-1")
        .digest(mediaBytes)
        .joinToString("") { "%02x".format(it) }

    val baseUrl: String
        get() = "http://127.0.0.1:${server.address.port}"

    init {
        server.executor = executor
        server.createContext("/") { exchange -> handle(exchange) }
        server.start()
    }

    fun requests(): List<RecordedRequest> = recordedRequests.toList()

    fun assertNoUnexpectedRequests() {
        check(unexpectedRequests.isEmpty()) {
            "Mock Xiaomi 收到未预期请求: ${unexpectedRequests.joinToString()}"
        }
    }

    fun awaitRequest(path: String, timeout: Duration = Duration.ofSeconds(5)): RecordedRequest {
        val deadline = System.nanoTime() + timeout.toNanos()
        while (System.nanoTime() < deadline) {
            requests().lastOrNull { it.path == path }?.let { return it }
            Thread.sleep(25)
        }
        error("等待请求超时: $path，已收到: ${requests().map { it.path }}")
    }

    private fun handle(exchange: HttpExchange) {
        try {
            val request = RecordedRequest(
                method = exchange.requestMethod,
                path = exchange.requestURI.path,
                query = parseQuery(exchange.requestURI.rawQuery),
                headers = exchange.requestHeaders.mapValues { it.value.toList() },
                body = exchange.requestBody.use { it.readBytes() },
            )
            recordedRequests += request
            dispatch(exchange, request)
        } catch (e: Exception) {
            unexpectedRequests += "${exchange.requestMethod} ${exchange.requestURI}: ${e.message}"
            runCatching { respond(exchange, 500, e.stackTraceToString(), "text/plain; charset=utf-8") }
        } finally {
            exchange.close()
        }
    }

    private fun dispatch(exchange: HttpExchange, request: RecordedRequest) {
        when {
            request.path == "/api/user/login" -> {
                requireMethod(request, "GET")
                requireCookie(request, "userId=mock-user", "passToken=mock-pass-token", "deviceId=wb_")
                respondJson(exchange, 200, """{"data":{"loginUrl":"$baseUrl/mock/login"}}""")
            }

            request.path == "/mock/login" -> {
                requireMethod(request, "GET")
                requireCookie(request, "userId=mock-user", "passToken=mock-pass-token")
                exchange.responseHeaders.add("Location", "$baseUrl/mock/token")
                exchange.sendResponseHeaders(302, -1)
            }

            request.path == "/mock/token" -> {
                requireMethod(request, "GET")
                requireCookie(request, "userId=mock-user", "passToken=mock-pass-token")
                exchange.responseHeaders.add("Set-Cookie", "serviceToken=mock-service-token; Path=/; HttpOnly")
                respondJson(exchange, 200, "{}")
            }

            request.path == "/gallery/user/album/list" -> {
                requireCloudAuth(request)
                when (request.query["pageNum"]) {
                    "0" -> respondJson(
                        exchange,
                        200,
                        """{"data":{"albums":[{"albumId":1,"name":"Camera","mediaCount":1,"lastUpdateTime":1714564800000}],"isLastPage":false}}"""
                    )

                    "1" -> respondJson(
                        exchange,
                        200,
                        """{"data":{"albums":[{"albumId":2,"name":"Screenshots","mediaCount":0,"lastUpdateTime":1714564800000}],"isLastPage":true}}"""
                    )

                    else -> error("未知相册页码: ${request.query}")
                }
            }

            request.path == "/gallery/user/galleries" -> {
                requireCloudAuth(request)
                check(request.query["albumId"] == "1") { "未知相册资源请求: ${request.query}" }
                respondJson(
                    exchange,
                    200,
                    """{"data":{"galleries":[{"id":101,"fileName":"sample-photo.jpg","type":"image","dateTaken":1714564800000,"sha1":"$mediaSha1","mimeType":"image/jpeg","title":"sample-photo","size":${mediaBytes.size}}],"isLastPage":true}}"""
                )
            }

            request.path == "/sfs/ns/recorder/dir/0/list" -> {
                requireCloudAuth(request)
                respondJson(
                    exchange,
                    200,
                    """{"data":{"list":[{"id":201,"name":"sample-audio.m4a_device_location_type_suffix","create_time":1714564800000,"sha1":"$mediaSha1","size":${mediaBytes.size}}],"isLastPage":true}}"""
                )
            }

            request.path == "/gallery/user/timeline" -> {
                requireCloudAuth(request)
                respondJson(
                    exchange,
                    200,
                    """{"data":{"indexHash":"mock-index-hash","dayCount":{"20240501":1}}}"""
                )
            }

            request.path == "/gallery/storage" -> {
                requireCloudAuth(request)
                val assetId = request.query["id"] ?: error("下载请求缺少 id")
                respondJson(exchange, 200, """{"code":0,"data":{"url":"$baseUrl/mock/oss/$assetId"}}""")
            }

            request.path.startsWith("/sfs/ns/recorder/file/") && request.path.endsWith("/storage") -> {
                requireCloudAuth(request)
                respondJson(exchange, 200, """{"code":0,"data":{"url":"$baseUrl/mock/oss/201"}}""")
            }

            request.path.startsWith("/mock/oss/") -> {
                requireMethod(request, "GET")
                val assetId = request.path.substringAfterLast('/')
                respond(
                    exchange,
                    200,
                    "dl_callback({\"url\":\"$baseUrl/mock/download/$assetId\",\"meta\":\"mock-meta\"})",
                    "application/javascript; charset=utf-8"
                )
            }

            request.path.startsWith("/mock/download/") -> {
                requireMethod(request, "POST")
                check(request.body.toString(StandardCharsets.UTF_8) == "meta=mock-meta") {
                    "下载 meta 不正确: ${request.body.toString(StandardCharsets.UTF_8)}"
                }
                respond(exchange, 200, mediaBytes, "application/octet-stream")
            }

            request.path == "/mock/notify" -> {
                requireMethod(request, "POST")
                check(request.body.isNotEmpty()) { "通知请求体不能为空" }
                exchange.sendResponseHeaders(204, -1)
            }

            else -> {
                unexpectedRequests += "${request.method} ${request.path}"
                respond(exchange, 404, "not found", "text/plain; charset=utf-8")
            }
        }
    }

    private fun requireCloudAuth(request: RecordedRequest) {
        requireMethod(request, "GET")
        requireCookie(request, "userId=mock-user", "serviceToken=mock-service-token")
    }

    private fun requireMethod(request: RecordedRequest, expected: String) {
        check(request.method == expected) { "${request.path} 应使用 $expected，实际为 ${request.method}" }
    }

    private fun requireCookie(request: RecordedRequest, vararg expectedParts: String) {
        val cookie = request.headers.entries
            .firstOrNull { it.key.equals("Cookie", ignoreCase = true) }
            ?.value
            ?.joinToString(";")
            .orEmpty()
        expectedParts.forEach { expected ->
            check(cookie.contains(expected)) { "${request.path} Cookie 缺少 $expected，实际为 $cookie" }
        }
    }

    private fun respondJson(exchange: HttpExchange, status: Int, body: String) {
        respond(exchange, status, body, "application/json; charset=utf-8")
    }

    private fun respond(exchange: HttpExchange, status: Int, body: String, contentType: String) {
        respond(exchange, status, body.toByteArray(StandardCharsets.UTF_8), contentType)
    }

    private fun respond(exchange: HttpExchange, status: Int, body: ByteArray, contentType: String) {
        exchange.responseHeaders.set("Content-Type", contentType)
        exchange.sendResponseHeaders(status, body.size.toLong())
        exchange.responseBody.use { it.write(body) }
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        return rawQuery.split('&').associate { part ->
            val key = part.substringBefore('=')
            val value = part.substringAfter('=', "")
            URLDecoder.decode(key, StandardCharsets.UTF_8) to URLDecoder.decode(value, StandardCharsets.UTF_8)
        }
    }

    override fun close() {
        server.stop(0)
        executor.shutdownNow()
    }

    companion object {
        fun start(): MockXiaomiApiServer {
            val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
            return MockXiaomiApiServer(server)
        }
    }
}
