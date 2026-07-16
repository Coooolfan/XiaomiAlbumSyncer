package com.coooolfan.xiaomialbumsyncer.e2e

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration
import java.util.Collections
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class MockXiaomiApiServer private constructor(
    private val process: Process,
    private val output: MutableList<String>,
    val baseUrl: String,
) : AutoCloseable {

    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
    private val objectMapper = jacksonObjectMapper()

    val mediaBytes: ByteArray = "xiaomi-album-syncer-api-e2e\n".toByteArray(StandardCharsets.UTF_8)
    val mediaSha1: String = MessageDigest.getInstance("SHA-1")
        .digest(mediaBytes)
        .joinToString("") { "%02x".format(it) }

    fun assertNoUnexpectedRequests() {
        val stats = stats()
        check(stats.path("unexpectedRequests").asLong() == 0L) {
            "Mock Xiaomi 收到未预期请求。stats=$stats\nlogs=${logs()}"
        }
    }

    fun routeCount(path: String): Long = stats().path("routeCounts").path(path).asLong()

    fun routePrefixCount(prefix: String): Long = stats().path("routeCounts").properties().asSequence()
        .filter { it.key.startsWith(prefix) }
        .sumOf { it.value.asLong() }

    fun timelineCount(albumId: Long): Long = stats().path("timelineAlbumIds").path(albumId.toString()).asLong()

    fun awaitRequest(path: String, timeout: Duration = Duration.ofSeconds(5)) {
        val deadline = System.nanoTime() + timeout.toNanos()
        while (System.nanoTime() < deadline) {
            if (routeCount(path) > 0) return
            Thread.sleep(25)
        }
        error("等待请求超时: $path，stats=${stats()}\nlogs=${logs()}")
    }

    private fun stats(): JsonNode {
        val request = HttpRequest.newBuilder(URI.create("$baseUrl/_control/v1/stats"))
            .timeout(Duration.ofSeconds(2))
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        check(response.statusCode() == 200) { "读取 mock stats 失败: ${response.statusCode()} ${response.body()}" }
        return objectMapper.readTree(response.body())
    }

    private fun awaitReady(timeout: Duration = Duration.ofSeconds(10)) {
        val deadline = System.nanoTime() + timeout.toNanos()
        var lastError: Exception? = null
        while (System.nanoTime() < deadline) {
            if (!process.isAlive) error("Mock Xiaomi 进程提前退出，exit=${process.exitValue()}\n${logs()}")
            try {
                val request = HttpRequest.newBuilder(URI.create("$baseUrl/_control/v1/health"))
                    .timeout(Duration.ofSeconds(1))
                    .GET()
                    .build()
                if (client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200) return
            } catch (e: Exception) {
                lastError = e
            }
            Thread.sleep(50)
        }
        error("等待 Mock Xiaomi 启动超时: ${lastError?.message}\n${logs()}")
    }

    private fun logs(): String = synchronized(output) { output.joinToString(System.lineSeparator()) }

    override fun close() {
        if (!process.isAlive) return
        process.destroy()
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            process.waitFor(5, TimeUnit.SECONDS)
        }
    }

    companion object {
        fun start(): MockXiaomiApiServer {
            val executable = requiredProperty("xiaomi.e2e.mockExecutable")
            val scenario = requiredProperty("xiaomi.e2e.mockScenario")
            check(Files.isRegularFile(Path.of(executable))) { "Mock Xiaomi 可执行文件不存在: $executable" }
            check(Files.isRegularFile(Path.of(scenario))) { "Mock Xiaomi 场景不存在: $scenario" }
            val port = ServerSocket(0, 0, InetAddress.getLoopbackAddress()).use { it.localPort }
            val output = Collections.synchronizedList(mutableListOf<String>())
            val process = ProcessBuilder(
                executable,
                "--listen", "127.0.0.1:$port",
                "--scenario", scenario,
            ).redirectErrorStream(true).start()
            thread(name = "xiaomi-cloud-mock-output", isDaemon = true) {
                process.inputStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                    lines.forEach { output += it }
                }
            }
            return MockXiaomiApiServer(process, output, "http://127.0.0.1:$port").also {
                try {
                    it.awaitReady()
                } catch (e: Exception) {
                    it.close()
                    throw e
                }
            }
        }

        private fun requiredProperty(name: String): String =
            System.getProperty(name)?.takeIf { it.isNotBlank() }
                ?: error("缺少系统属性: $name")
    }
}
