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

    enum class DatabaseProfile {
        E2E,
        PRODUCTION_DEFAULT,
    }

    val pid: Long
        get() = process.pid()

    fun logs(): String = synchronized(output) { output.joinToString(System.lineSeparator()) }

    fun logsTail(maxLines: Int): String = synchronized(output) {
        output.takeLast(maxLines).joinToString(System.lineSeparator())
    }

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

        fun start(
            mockBaseUrl: String,
            workDir: Path,
            databaseProfile: DatabaseProfile = DatabaseProfile.E2E,
            extraJvmArgs: List<String> = emptyList(),
            extraEnvironment: Map<String, String> = emptyMap(),
        ): ApplicationProcess {
            Files.createDirectories(workDir)
            val port = findFreePort()
            val databasePath = workDir.resolve("db/xiaomialbumsyncer.db")
            val fallbackDatabasePath = workDir.resolve("fallback/xiaomialbumsyncer.db")
            Files.createDirectories(databasePath.parent)
            val command = buildCommand(extraJvmArgs)
            val output = Collections.synchronizedList(mutableListOf<String>())
            val processBuilder = ProcessBuilder(command)
                .directory(workDir.toFile())
                .redirectErrorStream(true)

            processBuilder.environment().apply {
                put("SERVER_PORT", port.toString())
                when (databaseProfile) {
                    DatabaseProfile.E2E -> {
                        put("APP_DB_PATH", fallbackDatabasePath.toString())
                        put(
                            "SQLITE_URL",
                            "jdbc:sqlite:${databasePath.toAbsolutePath()}" +
                                    "?journal_mode=WAL&synchronous=FULL&cache_size=-8192" +
                                    "&temp_store=file&mmap_size=67108864"
                        )
                        put("SQLITE_JOURNAL_MODE", "WAL")
                        put("SQLITE_SYNCHRONOUS", "FULL")
                        put("SQLITE_CACHE_SIZE", "-8192")
                        put("SQLITE_TEMP_STORE", "file")
                        put("SQLITE_MMAP_SIZE", "67108864")
                        put("SQLITE_MAXIMUM_POOL_SIZE", "2")
                    }

                    DatabaseProfile.PRODUCTION_DEFAULT -> {
                        remove("SQLITE_URL")
                        put("APP_DB_PATH", databasePath.toString())
                        put("SQLITE_JOURNAL_MODE", "WAL")
                        put("SQLITE_SYNCHRONOUS", "NORMAL")
                        put("SQLITE_CACHE_SIZE", "10000")
                        put("SQLITE_TEMP_STORE", "memory")
                        put("SQLITE_MMAP_SIZE", "0")
                        put("SQLITE_BUSY_TIMEOUT", "30000")
                        put("SQLITE_MAXIMUM_POOL_SIZE", "4")
                    }
                }
                put("XIAOMI_API_BASE_URL", mockBaseUrl)
                put("WEBAUTHN_RP_ID", "localhost")
                putAll(extraEnvironment)
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
                check(Files.exists(databasePath)) { "应用数据库未创建: $databasePath" }
                if (databaseProfile == DatabaseProfile.E2E) {
                    check(Files.notExists(fallbackDatabasePath)) {
                        "SQLITE_URL 生效时不应创建 APP_DB_PATH 数据库: $fallbackDatabasePath"
                    }
                }
            } catch (e: Exception) {
                application.close()
                throw e
            }
            return application
        }

        private fun buildCommand(extraJvmArgs: List<String>): List<String> {
            return when (val target = System.getProperty("xiaomi.e2e.target", "jvm")) {
                "jvm" -> buildJvmCommand(extraJvmArgs)
                "native" -> {
                    val executable = requiredProperty("xiaomi.e2e.appExecutable")
                    check(Files.isRegularFile(Path.of(executable))) { "Native 可执行文件不存在: $executable" }
                    listOf(executable)
                }

                else -> error("未知 E2E 目标: $target")
            }
        }

        private fun buildJvmCommand(extraJvmArgs: List<String>): List<String> {
            val command = mutableListOf(
                requiredProperty("xiaomi.e2e.javaExecutable"),
                "--enable-native-access=ALL-UNNAMED",
                "-Dfile.encoding=UTF-8",
            )
            command += extraJvmArgs
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
