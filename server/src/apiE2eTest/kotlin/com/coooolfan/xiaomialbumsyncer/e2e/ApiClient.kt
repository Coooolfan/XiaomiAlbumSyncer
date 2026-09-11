package com.coooolfan.xiaomialbumsyncer.e2e

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

class ApiClient(private val baseUrl: String) {

    data class Response(
        val status: Int,
        val body: String,
        val headers: java.net.http.HttpHeaders,
    ) {
        fun expect(expectedStatus: Int): Response {
            check(status == expectedStatus) {
                "HTTP 状态不符合预期，期望 $expectedStatus，实际 $status，响应: $body"
            }
            return this
        }
    }

    private val objectMapper = ObjectMapper()
    private val cookieManager = CookieManager(null, CookiePolicy.ACCEPT_ALL)
    private val httpClient = HttpClient.newBuilder()
        .cookieHandler(cookieManager)
        .connectTimeout(Duration.ofSeconds(2))
        .build()

    fun get(path: String): Response = request("GET", path)

    fun post(path: String, body: Any? = null, headers: Map<String, String> = emptyMap()): Response =
        request("POST", path, body, headers)

    /**
     * 使用一次性连接的 POST：/mcp 鉴权失败时服务端会直接关闭 keep-alive 连接，
     * 复用连接池会让下一个请求读到 EOF。
     */
    fun postOnNewConnection(
        path: String,
        body: Any? = null,
        headers: Map<String, String> = emptyMap(),
    ): Response = request("POST", path, body, headers, HttpClient.newHttpClient())

    fun put(path: String, body: Any): Response = request("PUT", path, body)

    fun delete(path: String, headers: Map<String, String> = emptyMap()): Response = request("DELETE", path, null, headers)

    fun json(response: Response): JsonNode = objectMapper.readTree(response.body)

    fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private fun request(
        method: String,
        path: String,
        body: Any? = null,
        headers: Map<String, String> = emptyMap(),
        client: HttpClient = httpClient,
    ): Response {
        val builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
            .timeout(Duration.ofSeconds(35))

        headers.forEach { (name, value) -> builder.header(name, value) }

        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody())
        } else {
            builder.header("Content-Type", "application/json; charset=utf-8")
            builder.method(method, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
        }

        val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        return Response(response.statusCode(), response.body(), response.headers())
    }
}
