package com.coooolfan.xiaomialbumsyncer.xiaomicloud.e2ee

import com.coooolfan.xiaomialbumsyncer.config.XiaomiApiProperties
import com.coooolfan.xiaomialbumsyncer.service.NotifyService
import com.coooolfan.xiaomialbumsyncer.xiaomicloud.XiaomiTokenManager
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.babyfish.jimmer.sql.dialect.SQLiteDialect
import org.babyfish.jimmer.sql.kt.newKSqlClient
import org.babyfish.jimmer.sql.runtime.ConnectionManager
import org.babyfish.jimmer.sql.runtime.DefaultDatabaseNamingStrategy
import org.flywaydb.core.Flyway
import java.nio.file.Path
import java.time.Instant

internal class E2eeTestDatabase(path: Path, baseUrl: String = "http://127.0.0.1:1") : AutoCloseable {
    private val source = HikariDataSource(HikariConfig().apply {
        jdbcUrl = "jdbc:sqlite:$path"; maximumPoolSize = 1
    })
    init { Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate() }
    val sql = newKSqlClient {
        setDialect(SQLiteDialect())
        setConnectionManager(ConnectionManager.simpleConnectionManager(source))
        setDatabaseNamingStrategy(DefaultDatabaseNamingStrategy.LOWER_CASE)
    }
    val notifications = NotifyService(sql)
    val tokens = XiaomiTokenManager(sql, notifications)
    val properties = XiaomiApiProperties().also {
        XiaomiApiProperties::class.java.getDeclaredField("configuredBaseUrl").apply { isAccessible = true }.set(it, baseUrl)
    }
    fun service() = XiaomiE2eeService(sql, tokens, properties, notifications)
    fun cacheToken(accountId: Long, userId: String) {
        @Suppress("UNCHECKED_CAST")
        val cache = XiaomiTokenManager::class.java.getDeclaredField("tokenCache").apply { isAccessible = true }.get(tokens) as MutableMap<Long, XiaomiTokenManager.CachedToken>
        cache[accountId] = XiaomiTokenManager.CachedToken("test-service-token", userId, Instant.now())
    }
    override fun close() { source.close() }
}
