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
