package com.coooolfan.xiaomialbumsyncer.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.noear.solon.annotation.Configuration
import org.noear.solon.annotation.Inject
import org.noear.solon.annotation.Managed
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.sql.DataSource

@Configuration
class DataSource {

    @Inject(value = $$"${solon.app.db}")
    lateinit var sqlite: String

    @Managed(index = 100)
    fun defaultDataSource(sqliteUrlProperties: SQLiteUrlProperties): DataSource {
        val config = HikariConfig()
        config.jdbcUrl = resolveSQLiteUrl(sqliteUrlProperties.url) {
            val dbPath = Paths.get(sqlite)
            Files.createDirectories(dbPath.parent)
            buildSQLiteUrl(dbPath, sqliteUrlProperties.toOptions())
        }
        config.driverClassName = "org.sqlite.JDBC"
        config.maximumPoolSize = validateSQLiteMaximumPoolSize(sqliteUrlProperties.maximumPoolSize)
        config.connectionTestQuery = "SELECT 1"
        config.poolName = "SQLitePool"
        return HikariDataSource(config)
    }

}

@Managed
class SQLiteUrlProperties {

    @Inject(value = $$"${solon.app.sqlite.url}")
    var url: String = ""

    @Inject(value = $$"${solon.app.sqlite.journal-mode}")
    lateinit var journalMode: String

    @Inject(value = $$"${solon.app.sqlite.synchronous}")
    lateinit var synchronous: String

    @Inject(value = $$"${solon.app.sqlite.cache-size}")
    var cacheSize: Int = 10_000

    @Inject(value = $$"${solon.app.sqlite.temp-store}")
    lateinit var tempStore: String

    @Inject(value = $$"${solon.app.sqlite.mmap-size}")
    var mmapSize: Long = 0

    @Inject(value = $$"${solon.app.sqlite.busy-timeout}")
    var busyTimeout: Int = 30_000

    @Inject(value = $$"${solon.app.sqlite.maximum-pool-size}")
    var maximumPoolSize: Int = 4

    fun toOptions() = SQLiteUrlOptions(
        journalMode = journalMode,
        synchronous = synchronous,
        cacheSize = cacheSize,
        tempStore = tempStore,
        mmapSize = mmapSize,
        busyTimeout = busyTimeout,
    )
}

class SQLiteUrlOptions(
    journalMode: String = "WAL",
    synchronous: String = "NORMAL",
    val cacheSize: Int = 10_000,
    tempStore: String = "memory",
    val mmapSize: Long = 0,
    val busyTimeout: Int = 30_000,
) {
    val journalMode = validateChoice("journal_mode", journalMode, JOURNAL_MODES)
    val synchronous = validateChoice("synchronous", synchronous, SYNCHRONOUS_MODES)
    val tempStore = validateChoice("temp_store", tempStore, TEMP_STORE_MODES).lowercase()

    init {
        require(mmapSize >= 0) { "mmap_size must not be negative: $mmapSize" }
        require(busyTimeout >= 0) { "busy_timeout must not be negative: $busyTimeout" }
    }

    private fun validateChoice(name: String, value: String, choices: Set<String>): String {
        val normalized = value.trim().uppercase()
        require(normalized in choices) {
            "$name must be one of ${choices.joinToString()}, but was: $value"
        }
        return normalized
    }

    private companion object {
        val JOURNAL_MODES = setOf("DELETE", "TRUNCATE", "PERSIST", "MEMORY", "WAL", "OFF")
        val SYNCHRONOUS_MODES = setOf("OFF", "NORMAL", "FULL", "EXTRA", "0", "1", "2", "3")
        val TEMP_STORE_MODES = setOf("DEFAULT", "FILE", "MEMORY", "0", "1", "2")
    }
}

fun resolveSQLiteUrl(configuredUrl: String?, fallback: () -> String): String {
    val url = configuredUrl?.trim().orEmpty()
    if (url.isEmpty()) {
        return fallback()
    }
    require(url.startsWith(SQLITE_JDBC_PREFIX) && url.length > SQLITE_JDBC_PREFIX.length) {
        "SQLITE_URL must be a complete SQLite JDBC URL starting with $SQLITE_JDBC_PREFIX"
    }
    return url
}

private const val SQLITE_JDBC_PREFIX = "jdbc:sqlite:"

fun validateSQLiteMaximumPoolSize(value: Int): Int {
    require(value in 1..64) { "SQLITE_MAXIMUM_POOL_SIZE must be between 1 and 64: $value" }
    return value
}

fun buildSQLiteUrl(dbPath: Path, options: SQLiteUrlOptions = SQLiteUrlOptions()): String {
    return "jdbc:sqlite:${dbPath.toAbsolutePath()}" +
            "?journal_mode=${options.journalMode}" +
            "&synchronous=${options.synchronous}" +
            "&cache_size=${options.cacheSize}" +
            "&temp_store=${options.tempStore}" +
            "&mmap_size=${options.mmapSize}" +
            "&busy_timeout=${options.busyTimeout}"
}
