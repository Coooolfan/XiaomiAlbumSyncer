package com.coooolfan.xiaomialbumsyncer.e2e

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.Comparator
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

@Tag("memory-benchmark")
class ApiMemoryBenchmarkSuite {

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun initialEmptyAndIncrementalDownload() {
        val outputDir = Path.of(requiredProperty("xiaomi.benchmark.outputDir"))
        Files.createDirectories(outputDir)
        val workRoot = outputDir.resolve("work")
        Files.createDirectories(workRoot)
        val workDir = Files.createTempDirectory(workRoot, "xiaomi-memory-benchmark-")
        val keepWorkDir = booleanProperty("xiaomi.benchmark.keepWorkDir", false)
        var succeeded = false

        try {
            MockXiaomiApiServer.start().use { mock ->
                ApplicationProcess.start(
                    mockBaseUrl = mock.baseUrl,
                    workDir = workDir,
                    databaseProfile = ApplicationProcess.DatabaseProfile.PRODUCTION_DEFAULT,
                    extraJvmArgs = benchmarkJvmArgs(outputDir),
                    extraEnvironment = mapOf("MALLOC_ARENA_MAX" to "2"),
                ).use { application ->
                    ProcessMemorySampler(application.pid).use { sampler ->
                        val benchmark = BenchmarkRun(
                            api = application.api,
                            mock = mock,
                            workDir = workDir,
                            outputDir = outputDir,
                            applicationPid = application.pid,
                            sampler = sampler,
                        )
                        try {
                            benchmark.execute()
                        } catch (e: Throwable) {
                            throw AssertionError(
                                "内存基准失败: ${e.message}\nworkDir=$workDir" +
                                        "\n\n应用日志（最后 500 行）:\n${application.logsTail(500)}",
                                e,
                            )
                        }
                    }
                }
                mock.assertNoUnexpectedRequests()
            }
            succeeded = true
        } finally {
            if (succeeded && !keepWorkDir) deleteRecursively(workDir)
        }
    }

    private inner class BenchmarkRun(
        private val api: ApiClient,
        private val mock: MockXiaomiApiServer,
        private val workDir: Path,
        private val outputDir: Path,
        private val applicationPid: Long,
        private val sampler: ProcessMemorySampler,
    ) {
        private val timeout = Duration.ofMinutes(longProperty("xiaomi.benchmark.timeoutMinutes", 20))
        private val downloaders = intProperty("xiaomi.benchmark.downloaders", 8)
        private val fetchFromDbSize = intProperty("xiaomi.benchmark.fetchFromDbSize", 2)
        private val verifiers = intProperty("xiaomi.benchmark.verifiers", 2)
        private val exifProcessors = intProperty("xiaomi.benchmark.exifProcessors", 2)
        private val fileTimeWorkers = intProperty("xiaomi.benchmark.fileTimeWorkers", 2)
        private val incrementalCount = intProperty("xiaomi.benchmark.incrementalCount", 100)
        private val jfrEnabled = booleanProperty("xiaomi.benchmark.jfr", false)
        private val rewriteExifTime = booleanProperty("xiaomi.benchmark.rewriteExifTime", false)
        private val contentMode = stringProperty("xiaomi.benchmark.contentMode", "")

        fun execute() {
            val crontabId = initializeApplication()
            Thread.sleep(2_000)

            if (jfrEnabled) {
                runJcmd(
                    "JFR.start",
                    "name=xiaomi-memory-profile",
                    "settings=profile",
                    "filename=${outputDir.resolve("xiaomi-memory-profile.jfr")}",
                    "maxsize=256m",
                    "dumponexit=true",
                )
            }

            try {
                val phases = mutableListOf<PhaseResult>()
                phases += runPhase("initial", crontabId)
                phases += runPhase("empty", crontabId)

                val mutation = mock.mutate(
                    mapOf(
                        "operations" to listOf(
                            mapOf(
                                "op" to "addAssets",
                                "userId" to "memory-user",
                                "albumId" to 1,
                                "count" to incrementalCount,
                                "template" to mapOf(
                                    "type" to "image",
                                    "fileName" to "increment.jpg",
                                    "dateTaken" to 1784217600000L,
                                    "size" to 16_777_216L,
                                    "contentMode" to contentMode,
                                ),
                            )
                        )
                    )
                )
                phases += runPhase("incremental", crontabId)

                val report = linkedMapOf<String, Any?>(
                    "generatedAt" to Instant.now().toString(),
                    "applicationPid" to applicationPid,
                    "workDir" to workDir.toString(),
                    "config" to mapOf(
                        "downloaders" to downloaders,
                        "fetchFromDbSize" to fetchFromDbSize,
                        "verifiers" to verifiers,
                        "exifProcessors" to exifProcessors,
                        "fileTimeWorkers" to fileTimeWorkers,
                        "incrementalCount" to incrementalCount,
                        "checkSha1" to false,
                        "rewriteExifTime" to rewriteExifTime,
                        "contentMode" to contentMode,
                        "databaseProfile" to "production-default",
                        "jfrEnabled" to jfrEnabled,
                        "maxHeap" to stringProperty("xiaomi.benchmark.maxHeap", "512m"),
                        "softMaxHeap" to stringProperty("xiaomi.benchmark.softMaxHeap", "384m"),
                        "periodicGcInterval" to longProperty("xiaomi.benchmark.periodicGcInterval", 60_000),
                    ),
                    "phases" to phases,
                    "mutation" to mutation,
                    "mockStats" to mock.stats(),
                )
                objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(outputDir.resolve("benchmark.json").toFile(), report)
            } finally {
                if (jfrEnabled) {
                    runJcmd(
                        "JFR.stop",
                        "name=xiaomi-memory-profile",
                        "filename=${outputDir.resolve("xiaomi-memory-profile.jfr")}",
                    )
                }
            }
        }

        private fun initializeApplication(): Long {
            api.post("/api/system-config", mapOf("password" to "benchmark-password")).expect(200)
            api.get("/api/token?password=${api.encode("benchmark-password")}").expect(200)
            api.post("/api/system-config/mount-path", mapOf("path" to workDir.toString())).expect(200)

            val account = api.json(
                api.post(
                    "/api/account",
                    mapOf(
                        "nickname" to "Memory Benchmark",
                        "passToken" to "memory-pass-token",
                        "userId" to "memory-user",
                    )
                ).expect(200)
            )
            val accountId = account.path("id").asLong()
            val albums = api.json(api.get("/api/album/latest/$accountId").expect(200))
            val cameraAlbumId = albums.firstOrNull { it.path("remoteId").asLong() == 1L }
                ?.path("id")
                ?.asLong()
                ?: error("内存场景未返回相机相册: $albums")

            val config = linkedMapOf<String, Any?>(
                "expression" to "0 0 0 1 1 ? 2099",
                "timeZone" to "UTC",
                "targetPath" to workDir.resolve("downloads").toString(),
                "downloadImages" to true,
                "downloadVideos" to true,
                "downloadAudios" to false,
                "rewriteExifTime" to rewriteExifTime,
                "diffByTimeline" to true,
                "rewriteExifTimeZone" to "UTC",
                "skipExistingFile" to true,
                "rewriteFileSystemTime" to false,
                "checkSha1" to false,
                "fetchFromDbSize" to fetchFromDbSize,
                "downloaders" to downloaders,
                "verifiers" to verifiers,
                "exifProcessors" to exifProcessors,
                "fileTimeWorkers" to fileTimeWorkers,
                "expressionTargetPath" to "",
                "notify" to false,
            )
            val crontab = api.json(
                api.post(
                    "/api/crontab",
                    linkedMapOf<String, Any?>(
                        "name" to "Memory Benchmark",
                        "description" to "Stateful mock download benchmark",
                        "enabled" to false,
                        "config" to config,
                        "accountId" to accountId,
                        "albumIds" to listOf(cameraAlbumId),
                    )
                ).expect(200)
            )
            return crontab.path("id").asLong()
        }

        private fun runPhase(name: String, crontabId: Long): PhaseResult {
            val existingHistoryIds = historyIds(crontabId)
            val marker = sampler.beginPhase()
            val startedAt = System.nanoTime()
            api.post("/api/crontab/$crontabId/executions").expect(200)
            val historyId = awaitNewCompletedHistory(crontabId, existingHistoryIds)
            val elapsedMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis()
            val memory = sampler.endPhase(marker)
            writeNativeMemorySummary(name)
            return PhaseResult(
                name = name,
                historyId = historyId,
                elapsedMillis = elapsedMillis,
                peakRssBytes = memory.peakRssBytes,
                finalRssBytes = memory.finalRssBytes,
                peakProcessTreeRssBytes = memory.peakProcessTreeRssBytes,
                finalProcessTreeRssBytes = memory.finalProcessTreeRssBytes,
                lifetimeHighWaterRssBytes = readProcStatusBytes(applicationPid, "VmHWM"),
            )
        }

        private fun historyIds(crontabId: Long): Set<Long> = api.json(api.get("/api/crontab").expect(200))
            .first { it.path("id").asLong() == crontabId }
            .path("histories")
            .mapTo(mutableSetOf()) { it.path("id").asLong() }

        private fun awaitNewCompletedHistory(crontabId: Long, existingHistoryIds: Set<Long>): Long {
            val deadline = System.nanoTime() + timeout.toNanos()
            while (System.nanoTime() < deadline) {
                val crontab = api.json(api.get("/api/crontab").expect(200))
                    .firstOrNull { it.path("id").asLong() == crontabId }
                val history = crontab?.path("histories")?.firstOrNull {
                    it.path("id").asLong() !in existingHistoryIds && it.path("endTime").asText().isNotBlank()
                }
                if (history != null) return history.path("id").asLong()
                Thread.sleep(500)
            }
            error("等待 $timeout 后任务仍未完成，current=${api.post("/api/crontab/$crontabId/current").body}")
        }

        private fun writeNativeMemorySummary(phase: String) {
            Files.writeString(
                outputDir.resolve("$phase-nmt.txt"),
                runJcmd("VM.native_memory", "summary", "scale=KB"),
            )
        }

        private fun runJcmd(vararg arguments: String): String {
            val javaExecutable = Path.of(requiredProperty("xiaomi.e2e.javaExecutable"))
            val jcmd = javaExecutable.parent.resolve("jcmd")
            if (!Files.isExecutable(jcmd)) return "jcmd 不可用: $jcmd"
            val process = ProcessBuilder(
                jcmd.toString(),
                applicationPid.toString(),
                *arguments,
            ).redirectErrorStream(true).start()
            val text = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            check(process.waitFor() == 0) { "jcmd ${arguments.joinToString(" ")} 失败: $text" }
            return text
        }
    }

    private data class PhaseResult(
        val name: String,
        val historyId: Long,
        val elapsedMillis: Long,
        val peakRssBytes: Long,
        val finalRssBytes: Long,
        val peakProcessTreeRssBytes: Long,
        val finalProcessTreeRssBytes: Long,
        val lifetimeHighWaterRssBytes: Long,
    )

    private data class PhaseMarker(val generation: Long)
    private data class PhaseMemory(
        val peakRssBytes: Long,
        val finalRssBytes: Long,
        val peakProcessTreeRssBytes: Long,
        val finalProcessTreeRssBytes: Long,
    )

    private class ProcessMemorySampler(private val pid: Long) : AutoCloseable {
        private val running = AtomicBoolean(true)
        private val generation = AtomicLong(0)
        private val peak = AtomicLong(0)
        private val current = AtomicLong(0)
        private val processTreePeak = AtomicLong(0)
        private val processTreeCurrent = AtomicLong(0)
        private val samplerThread = thread(name = "xiaomi-memory-sampler", isDaemon = true) {
            while (running.get()) {
                val sampledGeneration = generation.get()
                val rss = readProcStatusBytes(pid, "VmRSS")
                val processTreeRss = readProcessTreeRssBytes(pid)
                if (sampledGeneration != generation.get()) continue
                if (rss > 0) {
                    current.set(rss)
                    peak.accumulateAndGet(rss, ::maxOf)
                }
                if (processTreeRss > 0) {
                    processTreeCurrent.set(processTreeRss)
                    processTreePeak.accumulateAndGet(processTreeRss, ::maxOf)
                }
                Thread.sleep(100)
            }
        }

        fun beginPhase(): PhaseMarker {
            val nextGeneration = generation.incrementAndGet()
            val rss = readProcStatusBytes(pid, "VmRSS").takeIf { it > 0 } ?: current.get()
            current.set(rss)
            peak.set(rss)
            val processTreeRss = readProcessTreeRssBytes(pid).takeIf { it > 0 } ?: processTreeCurrent.get()
            processTreeCurrent.set(processTreeRss)
            processTreePeak.set(processTreeRss)
            return PhaseMarker(nextGeneration)
        }

        fun endPhase(marker: PhaseMarker): PhaseMemory {
            check(marker.generation == generation.get()) { "内存采样阶段发生重叠" }
            val finalRss = readProcStatusBytes(pid, "VmRSS").takeIf { it > 0 } ?: current.get()
            val finalProcessTreeRss = readProcessTreeRssBytes(pid).takeIf { it > 0 } ?: processTreeCurrent.get()
            return PhaseMemory(peak.get(), finalRss, processTreePeak.get(), finalProcessTreeRss)
        }

        override fun close() {
            running.set(false)
            samplerThread.join(2_000)
        }
    }

    private fun benchmarkJvmArgs(outputDir: Path): List<String> = listOf(
        "-XX:+UseG1GC",
        "-XX:G1PeriodicGCInterval=${longProperty("xiaomi.benchmark.periodicGcInterval", 60_000)}",
        "-XX:-G1PeriodicGCInvokesConcurrent",
        "-XX:G1PeriodicGCSystemLoadThreshold=0",
        "-XX:MaxHeapFreeRatio=20",
        "-XX:MinHeapFreeRatio=5",
        "-XX:TrimNativeHeapInterval=60000",
        "-XX:+UseCompactObjectHeaders",
        "-XX:TieredStopAtLevel=1",
        "-XX:ReservedCodeCacheSize=48m",
        "-Xmx${stringProperty("xiaomi.benchmark.maxHeap", "512m")}",
        "-XX:SoftMaxHeapSize=${stringProperty("xiaomi.benchmark.softMaxHeap", "384m")}",
        "-XX:NativeMemoryTracking=summary",
    ) + if (booleanProperty("xiaomi.benchmark.jfr", false)) {
        listOf("-XX:FlightRecorderOptions=repository=${outputDir.resolve("jfr-repository")}")
    } else {
        emptyList()
    }

    private fun requiredProperty(name: String): String = System.getProperty(name)?.takeIf { it.isNotBlank() }
        ?: error("缺少系统属性: $name")

    private fun intProperty(name: String, default: Int): Int = System.getProperty(name)?.toIntOrNull() ?: default
    private fun longProperty(name: String, default: Long): Long = System.getProperty(name)?.toLongOrNull() ?: default
    private fun stringProperty(name: String, default: String): String =
        System.getProperty(name)?.takeIf { it.isNotBlank() } ?: default
    private fun booleanProperty(name: String, default: Boolean): Boolean =
        System.getProperty(name)?.toBooleanStrictOrNull() ?: default

    private fun deleteRecursively(path: Path) {
        if (!Files.exists(path)) return
        Files.walk(path).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    companion object {
        private fun readProcStatusBytes(pid: Long, field: String): Long {
            val status = Path.of("/proc", pid.toString(), "status")
            if (!Files.isRegularFile(status)) return 0
            val line = Files.readAllLines(status).firstOrNull { it.startsWith("$field:") } ?: return 0
            val kibibytes = line.substringAfter(':').trim().substringBefore(' ').toLongOrNull() ?: return 0
            return kibibytes * 1024
        }

        private fun readProcessTreeRssBytes(pid: Long): Long {
            val process = ProcessHandle.of(pid).orElse(null) ?: return 0
            val descendantRss = process.descendants().use { descendants ->
                descendants.mapToLong { readProcStatusBytes(it.pid(), "VmRSS") }.sum()
            }
            return readProcStatusBytes(pid, "VmRSS") + descendantRss
        }
    }
}
