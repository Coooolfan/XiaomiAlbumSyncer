package com.coooolfan.xiaomialbumsyncer.e2e

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.time.Duration

class LegacyConfigStartupSuite {

    private data class LegacyCrontab(val id: Long, val name: String, val resource: String)

    private data class CrontabRow(val id: Long, val name: String, val description: String, val configJson: String)

    private val legacyCrontabs = listOf(
        LegacyCrontab(9001L, "Legacy Minimal", "legacy-config/minimal.json"),
        LegacyCrontab(9002L, "Legacy Before Notify", "legacy-config/before-notify.json"),
    )

    private val inputMapper = ObjectMapper()

    @Test
    fun startsWithLegacyCrontabConfigs(@TempDir workDir: Path) {
        val target = System.getProperty("xiaomi.e2e.target", "jvm")
        val logPath = Path.of("build/reports/legacy-config-startup/$target.log")
        println("历史配置启动回归 target=$target workDir=$workDir")

        val databasePath = workDir.resolve("db/xiaomialbumsyncer.db")
        Files.createDirectories(databasePath.parent)
        val databaseUrl = "jdbc:sqlite:${databasePath.toAbsolutePath()}"

        migrateDatabase(databaseUrl)
        seedLegacyCrontabs(databaseUrl)

        MockXiaomiApiServer.start().use { mock ->
            val application = ApplicationProcess.start(mock.baseUrl, workDir)
            try {
                verifyStartup(application, target)
            } catch (e: Throwable) {
                throw AssertionError("历史配置启动回归失败: ${e.message}\n\n应用日志:\n${application.logs()}", e)
            } finally {
                application.close()
                Files.createDirectories(logPath.parent)
                Files.writeString(logPath, application.logs())
                println("应用日志已写入 ${logPath.toAbsolutePath()}")
            }
            mock.assertNoUnexpectedRequests()
        }
    }

    @Test
    fun startsWithEveryCrontabConfigCombination(@TempDir root: Path) {
        val target = System.getProperty("xiaomi.e2e.target", "jvm")
        val total = CrontabConfigCases.total
        val batchCount = (total + COMBINATION_BATCH_SIZE - 1) / COMBINATION_BATCH_SIZE
        val reportDir = Path.of("build/reports/legacy-config-startup/$target-combinations")
        Files.createDirectories(reportDir)
        println("全量组合启动回归 target=$target total=$total batches=$batchCount")

        var totalPersisted = 0
        var totalVerified = 0
        var completedBatches = 0
        writeSummary(reportDir, target, total, totalPersisted, totalVerified, completedBatches, batchCount)

        MockXiaomiApiServer.start().use { mock ->
            CrontabConfigCases.cases.chunked(COMBINATION_BATCH_SIZE).forEach { batch ->
                val batchDir = root.resolve("batch-%03d".format(completedBatches))
                val batchLog = reportDir.resolve("batch-%03d.log".format(completedBatches))
                runCombinationBatch(batch, batchDir, batchLog, mock, target)
                totalPersisted += batch.size
                totalVerified += batch.size
                completedBatches += 1
                println("batch $completedBatches/$batchCount verified=$totalVerified/$total")
                writeSummary(reportDir, target, total, totalPersisted, totalVerified, completedBatches, batchCount)
            }
            mock.assertNoUnexpectedRequests()
        }

        assertEquals(total, totalVerified)
        assertEquals(batchCount, completedBatches)
        println("全部 $totalVerified 个组合启动验证通过 target=$target")
    }

    private fun runCombinationBatch(
        batch: List<CrontabConfigCases.Case>,
        batchDir: Path,
        batchLog: Path,
        mock: MockXiaomiApiServer,
        target: String,
    ) {
        val databasePath = batchDir.resolve("db/xiaomialbumsyncer.db")
        Files.createDirectories(databasePath.parent)
        val databaseUrl = "jdbc:sqlite:${databasePath.toAbsolutePath()}"
        migrateDatabase(databaseUrl)
        seedGeneratedCrontabs(databaseUrl, batch)

        val application = ApplicationProcess.start(mock.baseUrl, batchDir)
        try {
            verifyCombinationStartup(application, target, batch)
        } catch (e: Throwable) {
            throw AssertionError(
                "组合批次 ${batch.first().index}-${batch.last().index} 验证失败: ${e.message}\n\n应用日志:\n${application.logs()}",
                e,
            )
        } finally {
            application.close()
            Files.writeString(batchLog, application.logs())
        }
    }

    private fun migrateDatabase(databaseUrl: String) {
        Flyway.configure()
            .dataSource(databaseUrl, null, null)
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }

    private fun insertProviderAccount(connection: Connection) {
        connection.prepareStatement(
            "INSERT INTO provider_account (id, nickname, user_id, provider, credentials) " +
                    "VALUES (9001, 'Legacy startup fixture', 'legacy-user', 'XIAOMI', '{\"passToken\":\"mock-pass-token\"}')"
        ).use { statement ->
            assertEquals(1, statement.executeUpdate())
        }
    }

    private fun insertCrontabs(connection: Connection, rows: List<CrontabRow>) {
        connection.prepareStatement(
            "INSERT INTO crontab (id, name, config, description, enabled, account_id, sync_mode) " +
                    "VALUES (?, ?, ?, ?, 1, 9001, 'FULL')"
        ).use { statement ->
            rows.forEach { row ->
                statement.setLong(1, row.id)
                statement.setString(2, row.name)
                statement.setString(3, row.configJson)
                statement.setString(4, row.description)
                statement.addBatch()
            }
            val results = statement.executeBatch()
            assertTrue(results.all { it == 1 }, "crontab fixture 写入行数不符")
        }
    }

    private fun seedLegacyCrontabs(databaseUrl: String) {
        DriverManager.getConnection(databaseUrl).use { connection ->
            insertProviderAccount(connection)
            val rows = legacyCrontabs.map {
                CrontabRow(it.id, it.name, "Historical JSON startup regression", readFixture(it.resource))
            }
            insertCrontabs(connection, rows)
            rows.forEach { row ->
                connection.prepareStatement("SELECT config FROM crontab WHERE id = ?").use { statement ->
                    statement.setLong(1, row.id)
                    statement.executeQuery().use { result ->
                        assertTrue(result.next())
                        assertEquals(row.configJson, result.getString(1), "fixture 未按原文落库")
                    }
                }
            }
        }
    }

    private fun seedGeneratedCrontabs(databaseUrl: String, cases: List<CrontabConfigCases.Case>) {
        val configs = cases.associate { it.crontabId to inputMapper.writeValueAsString(it.input) }
        DriverManager.getConnection(databaseUrl).use { connection ->
            connection.autoCommit = false
            insertProviderAccount(connection)
            insertCrontabs(connection, cases.map {
                CrontabRow(it.crontabId, it.crontabName, "Generated combination regression", configs.getValue(it.crontabId))
            })
            var persisted = 0
            connection.prepareStatement("SELECT id, config FROM crontab").use { statement ->
                statement.executeQuery().use { result ->
                    while (result.next()) {
                        persisted += 1
                        assertEquals(
                            configs.getValue(result.getLong(1)),
                            result.getString(2),
                            "case id=${result.getLong(1)} fixture 未按原文落库",
                        )
                    }
                }
            }
            assertEquals(cases.size, persisted)
            connection.commit()
        }
    }

    private fun verifyStartup(application: ApplicationProcess, target: String) {
        awaitRegisteredJobs(application, 2)

        val api = application.api
        initializeAndLogin(api)
        assertNativeImageFlag(api, target)

        val crontabs = api.json(api.get("/api/crontab").expect(200))
        assertEquals(2, crontabs.size(), "crontab 列表: $crontabs")
        val byId = crontabs.associateBy { it.path("id").asLong() }
        assertMinimalCrontab(byId[9001L] ?: error("缺少 id=9001 的任务: $crontabs"))
        assertBeforeNotifyCrontab(byId[9002L] ?: error("缺少 id=9002 的任务: $crontabs"))
    }

    private fun verifyCombinationStartup(
        application: ApplicationProcess,
        target: String,
        batch: List<CrontabConfigCases.Case>,
    ) {
        awaitRegisteredJobs(application, batch.size)

        val api = application.api
        initializeAndLogin(api)
        assertNativeImageFlag(api, target)

        val crontabs = api.json(api.get("/api/crontab").expect(200))
        assertEquals(batch.size, crontabs.size(), "crontab 数量不符")
        val byId = crontabs.associateBy { it.path("id").asLong() }
        assertEquals(batch.mapTo(HashSet()) { it.crontabId }, byId.keys, "crontab id 集合不符")
        batch.forEach { case ->
            val node = byId.getValue(case.crontabId)
            assertEquals(case.crontabName, node.path("name").asText(), "case ${case.index} name 不符")
            assertTrue(node.path("enabled").asBoolean(), "case ${case.index} enabled 不符")
            assertConfigMatches(case, node.path("config"))
        }
    }

    private fun awaitRegisteredJobs(application: ApplicationProcess, count: Int) {
        val registeredLog = "载入定时任务完成，共注册 $count 个任务"
        val deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos()
        while (!application.logs().contains(registeredLog) && System.nanoTime() < deadline) {
            Thread.sleep(50)
        }
        assertTrue(application.logs().contains(registeredLog), "应用日志缺少定时任务注册记录: $registeredLog")
    }

    private fun initializeAndLogin(api: ApiClient) {
        api.post("/api/system-config", mapOf("password" to "e2e-password")).expect(200)
        api.get("/api/token?password=${api.encode("e2e-password")}").expect(200)
    }

    private fun assertNativeImageFlag(api: ApiClient, target: String) {
        val info = api.json(api.get("/api/system-config/info").expect(200))
        println("GET /api/system-config/info -> $info")
        assertEquals(target == "native", info.path("nativeImage").asBoolean())
    }

    private fun assertConfigMatches(case: CrontabConfigCases.Case, config: JsonNode) {
        case.expected.forEach { (field, value) ->
            val node = config.path(field)
            val matched = when (value) {
                null -> node.isMissingNode || node.isNull
                is Boolean -> node.isBoolean && node.asBoolean() == value
                is Int -> node.isIntegralNumber && node.intValue() == value
                is String -> node.isTextual && node.textValue() == value
                else -> false
            }
            if (!matched) {
                fail<Unit>("case ${case.index} 字段 $field 不符: expected=$value actual=$node input=${case.input}")
            }
        }
        val extras = config.fieldNames().asSequence().toSet() - case.expected.keys
        if (extras.isNotEmpty()) {
            fail<Unit>("case ${case.index} 响应含非预期字段 $extras input=${case.input}")
        }
    }

    private fun assertCrontabEntry(node: JsonNode, id: Long, name: String) {
        assertEquals(id, node.path("id").asLong())
        assertEquals(name, node.path("name").asText())
        assertTrue(node.path("enabled").asBoolean())
        assertEquals("Historical JSON startup regression", node.path("description").asText())
        assertEquals(9001L, node.path("accountId").asLong())
        assertFalse(node.path("config").has("diffByTimeline"))
        assertFalse(node.path("config").has("expressionTargetPath"))
    }

    private fun assertMinimalCrontab(node: JsonNode) {
        assertCrontabEntry(node, 9001L, "Legacy Minimal")
        val config = node.path("config")
        assertEquals("0 0 0 1 1 ? 2099", config.path("expression").asText())
        assertEquals("UTC", config.path("timeZone").asText())
        assertEquals("./downloads", config.path("targetPath").asText())
        assertTrue(config.path("downloadImages").asBoolean())
        assertFalse(config.path("downloadVideos").asBoolean())
        assertFalse(config.path("rewriteExifTime").asBoolean())
        assertTrue(config.path("rewriteExifTimeZone").let { it.isMissingNode || it.isNull })
        assertTrue(config.path("skipExistingFile").asBoolean())
        assertFalse(config.path("rewriteFileSystemTime").asBoolean())
        assertFalse(config.path("checkSha1").asBoolean())
        assertEquals(2, config.path("fetchFromDbSize").asInt())
        assertEquals(8, config.path("downloaders").asInt())
        assertEquals(2, config.path("verifiers").asInt())
        assertEquals(2, config.path("exifProcessors").asInt())
        assertEquals(2, config.path("fileTimeWorkers").asInt())
        assertTrue(config.path("downloadAudios").asBoolean())
        assertTrue(config.path("notify").asBoolean())
    }

    private fun assertBeforeNotifyCrontab(node: JsonNode) {
        assertCrontabEntry(node, 9002L, "Legacy Before Notify")
        val config = node.path("config")
        assertEquals("0 0 0 1 1 ? 2099", config.path("expression").asText())
        assertEquals("UTC", config.path("timeZone").asText())
        assertEquals("./downloads", config.path("targetPath").asText())
        assertFalse(config.path("downloadImages").asBoolean())
        assertTrue(config.path("downloadVideos").asBoolean())
        assertTrue(config.path("rewriteExifTime").asBoolean())
        assertEquals("UTC", config.path("rewriteExifTimeZone").asText())
        assertFalse(config.path("skipExistingFile").asBoolean())
        assertTrue(config.path("rewriteFileSystemTime").asBoolean())
        assertTrue(config.path("checkSha1").asBoolean())
        assertEquals(3, config.path("fetchFromDbSize").asInt())
        assertEquals(4, config.path("downloaders").asInt())
        assertEquals(5, config.path("verifiers").asInt())
        assertEquals(6, config.path("exifProcessors").asInt())
        assertEquals(7, config.path("fileTimeWorkers").asInt())
        assertFalse(config.path("downloadAudios").asBoolean())
        assertTrue(config.path("notify").asBoolean())
    }

    private fun writeSummary(
        reportDir: Path,
        target: String,
        totalGenerated: Int,
        totalPersisted: Int,
        totalVerified: Int,
        completedBatches: Int,
        totalBatches: Int,
    ) {
        val summary = linkedMapOf<String, Any?>(
            "target" to target,
            "totalGenerated" to totalGenerated,
            "totalPersisted" to totalPersisted,
            "totalVerified" to totalVerified,
            "completedBatches" to completedBatches,
            "totalBatches" to totalBatches,
            "batchSize" to COMBINATION_BATCH_SIZE,
            "success" to (completedBatches == totalBatches && totalVerified == totalGenerated),
        )
        Files.writeString(
            reportDir.resolve("summary.json"),
            inputMapper.writerWithDefaultPrettyPrinter().writeValueAsString(summary),
        )
    }

    private fun readFixture(resource: String): String =
        String(
            javaClass.classLoader.getResourceAsStream(resource)?.readBytes()
                ?: error("fixture 不存在: $resource"),
            StandardCharsets.UTF_8,
        ).trimEnd('\n')

    companion object {
        private const val COMBINATION_BATCH_SIZE = 4096
    }
}
