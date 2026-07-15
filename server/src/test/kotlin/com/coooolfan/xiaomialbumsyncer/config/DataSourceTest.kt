package com.coooolfan.xiaomialbumsyncer.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

class DataSourceTest {

    @Test
    fun buildsUrlWithExistingDefaults(@TempDir tempDir: Path) {
        val dbPath = tempDir.resolve("default.db")

        assertEquals(
            "jdbc:sqlite:${dbPath.toAbsolutePath()}" +
                    "?journal_mode=WAL" +
                    "&synchronous=NORMAL" +
                    "&cache_size=10000" +
                    "&temp_store=memory" +
                    "&mmap_size=268435456",
            buildSQLiteUrl(dbPath),
        )
    }

    @Test
    fun usesConfiguredUrlWithoutEvaluatingFallback() {
        val configuredUrl = "jdbc:sqlite:file:custom.db?busy_timeout=5000&foreign_keys=on"

        assertEquals(
            configuredUrl,
            resolveSQLiteUrl("  $configuredUrl  ") {
                error("Fallback must not be evaluated when SQLITE_URL is configured")
            },
        )
    }

    @Test
    fun appliesConfiguredUrlToSQLite(@TempDir tempDir: Path) {
        val configuredUrl = "jdbc:sqlite:${tempDir.resolve("configured.db").toAbsolutePath()}" +
                "?journal_mode=DELETE&busy_timeout=5000&foreign_keys=on"

        DriverManager.getConnection(
            resolveSQLiteUrl(configuredUrl) { error("Fallback must not be evaluated") }
        ).use { connection ->
            assertEquals("delete", connection.pragmaString("journal_mode"))
            assertEquals(5_000, connection.pragmaLong("busy_timeout"))
            assertEquals(1, connection.pragmaLong("foreign_keys"))
        }
    }

    @Test
    fun fallsBackWhenConfiguredUrlIsBlank(@TempDir tempDir: Path) {
        val dbPath = tempDir.resolve("fallback.db")

        assertEquals(
            buildSQLiteUrl(dbPath),
            resolveSQLiteUrl("   ") { buildSQLiteUrl(dbPath) },
        )
    }

    @Test
    fun rejectsInvalidConfiguredUrl() {
        assertThrows(IllegalArgumentException::class.java) {
            resolveSQLiteUrl("sqlite:custom.db") { error("Fallback must not be evaluated") }
        }
        assertThrows(IllegalArgumentException::class.java) {
            resolveSQLiteUrl("jdbc:sqlite:") { error("Fallback must not be evaluated") }
        }
    }

    @Test
    fun buildsUrlWithCustomOptions(@TempDir tempDir: Path) {
        val dbPath = tempDir.resolve("custom.db")
        val options = SQLiteUrlOptions(
            journalMode = "delete",
            synchronous = "full",
            cacheSize = -8_192,
            tempStore = "file",
            mmapSize = 67_108_864,
        )

        assertEquals(
            "jdbc:sqlite:${dbPath.toAbsolutePath()}" +
                    "?journal_mode=DELETE" +
                    "&synchronous=FULL" +
                    "&cache_size=-8192" +
                    "&temp_store=file" +
                    "&mmap_size=67108864",
            buildSQLiteUrl(dbPath, options),
        )
    }

    @Test
    fun rejectsInvalidOptions() {
        assertThrows(IllegalArgumentException::class.java) {
            SQLiteUrlOptions(journalMode = "invalid")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SQLiteUrlOptions(synchronous = "sometimes")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SQLiteUrlOptions(tempStore = "network")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SQLiteUrlOptions(mmapSize = -1)
        }
    }

    @Test
    fun appliesCustomOptionsToSQLite(@TempDir tempDir: Path) {
        val options = SQLiteUrlOptions(
            journalMode = "delete",
            synchronous = "full",
            cacheSize = -8_192,
            tempStore = "file",
            mmapSize = 67_108_864,
        )

        DriverManager.getConnection(buildSQLiteUrl(tempDir.resolve("pragma.db"), options)).use { connection ->
            assertEquals("delete", connection.pragmaString("journal_mode"))
            assertEquals(2, connection.pragmaLong("synchronous"))
            assertEquals(-8_192, connection.pragmaLong("cache_size"))
            assertEquals(1, connection.pragmaLong("temp_store"))
            assertEquals(67_108_864, connection.pragmaLong("mmap_size"))
        }
    }

    private fun Connection.pragmaString(name: String): String =
        createStatement().use { statement ->
            statement.executeQuery("PRAGMA $name").use { result ->
                result.next()
                result.getString(1)
            }
        }

    private fun Connection.pragmaLong(name: String): Long =
        createStatement().use { statement ->
            statement.executeQuery("PRAGMA $name").use { result ->
                result.next()
                result.getLong(1)
            }
        }
}
