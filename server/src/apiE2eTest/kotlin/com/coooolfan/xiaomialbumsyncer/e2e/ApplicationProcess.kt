package com.coooolfan.xiaomialbumsyncer.e2e

import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Collections
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class ApplicationProcess private constructor(
    private val process: Process,
    private val output: MutableList<String>,
    val api: ApiClient,
) : AutoCloseable {

    fun logs(): String = synchronized(output) { output.joinToString(System.lineSeparator()) }

    private fun awaitReady(timeout: Duration = Duration.ofSeconds(30)) {
        val deadline = System.nanoTime() + timeout.toNanos()
        var lastError: Exception? = null
        while (System.nanoTime() < deadline) {
            if (!process.isAlive) {
                error("应用进程提前退出，exit=${process.exitValue()}\n${logs()}")
            }
            try {
                if (api.get("/api/system-config").status == 200) return
            } catch (e: Exception) {
                lastError = e
            }
            Thread.sleep(100)
        }
        error("等待应用启动超时: ${lastError?.message}\n${logs()}")
    }

    override fun close() {
        if (!process.isAlive) return
        process.destroy()
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            process.waitFor(5, TimeUnit.SECONDS)
        }
    }

    companion object {
        private const val MAIN_CLASS = "com.coooolfan.xiaomialbumsyncer.App"

        fun start(mockBaseUrl: String, workDir: Path): ApplicationProcess {
            Files.createDirectories(workDir)
            val port = findFreePort()
            val command = buildCommand()
            val output = Collections.synchronizedList(mutableListOf<String>())
            val processBuilder = ProcessBuilder(command)
                .directory(workDir.toFile())
                .redirectErrorStream(true)

            processBuilder.environment().apply {
                put("SERVER_PORT", port.toString())
                put("APP_DB_PATH", workDir.resolve("db/xiaomialbumsyncer.db").toString())
                put("SQLITE_JOURNAL_MODE", "WAL")
                put("SQLITE_SYNCHRONOUS", "FULL")
                put("SQLITE_CACHE_SIZE", "-8192")
                put("SQLITE_TEMP_STORE", "file")
                put("SQLITE_MMAP_SIZE", "67108864")
                put("XIAOMI_API_BASE_URL", mockBaseUrl)
                put("WEBAUTHN_RP_ID", "localhost")
            }

            val process = processBuilder.start()
            thread(name = "xiaomi-e2e-app-output", isDaemon = true) {
                process.inputStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                    lines.forEach { output += it }
                }
            }

            val application = ApplicationProcess(process, output, ApiClient("http://127.0.0.1:$port"))
            try {
                application.awaitReady()
            } catch (e: Exception) {
                application.close()
                throw e
            }
            return application
        }

        private fun buildCommand(): List<String> {
            return when (val target = System.getProperty("xiaomi.e2e.target", "jvm")) {
                "jvm" -> buildJvmCommand()
                "native" -> {
                    val executable = requiredProperty("xiaomi.e2e.appExecutable")
                    check(Files.isRegularFile(Path.of(executable))) { "Native 可执行文件不存在: $executable" }
                    listOf(executable)
                }

                else -> error("未知 E2E 目标: $target")
            }
        }

        private fun buildJvmCommand(): List<String> {
            val command = mutableListOf(
                requiredProperty("xiaomi.e2e.javaExecutable"),
                "--enable-native-access=ALL-UNNAMED",
                "-Dfile.encoding=UTF-8",
            )
            System.getProperty("xiaomi.e2e.agentConfigDir")
                ?.takeIf { it.isNotBlank() }
                ?.let { configDir ->
                    Files.createDirectories(Path.of(configDir))
                    command += "-agentlib:native-image-agent=config-merge-dir=$configDir"
                }
            command += listOf("-cp", requiredProperty("xiaomi.e2e.appClasspath"), MAIN_CLASS)
            return command
        }

        private fun requiredProperty(name: String): String {
            return System.getProperty(name)?.takeIf { it.isNotBlank() }
                ?: error("缺少系统属性: $name")
        }

        private fun findFreePort(): Int {
            return ServerSocket(0, 0, InetAddress.getLoopbackAddress()).use { it.localPort }
        }
    }
}
