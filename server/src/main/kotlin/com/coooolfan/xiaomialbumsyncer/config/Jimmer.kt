package com.coooolfan.xiaomialbumsyncer.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.babyfish.jimmer.sql.dialect.SQLiteDialect
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.newKSqlClient
import org.babyfish.jimmer.sql.runtime.*
import org.noear.solon.annotation.Bean
import org.noear.solon.annotation.Configuration
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.sql.DataSource


@Configuration
class Jimmer {

    private val log = LoggerFactory.getLogger(Jimmer::class.java)

    @Bean
    fun defaultDataSource(): DataSource {
        val dbPath = determineDbPath()
        Files.createDirectories(dbPath.parent)
        val config = HikariConfig()
        config.jdbcUrl = buildSQLiteUrl(dbPath)
        config.driverClassName = "org.sqlite.JDBC"
        config.maximumPoolSize = 1 // SQLite通常不需要太多连接
        config.connectionTestQuery = "SELECT 1"
        config.poolName = "SQLitePool"
        return HikariDataSource(config)
    }

    @Bean
    fun sql(dataSource: DataSource): KSqlClient {
        val kSqlClient = newKSqlClient {
            log.info("初始化Jimmer")
            setDialect(SQLiteDialect())
            setConnectionManager(ConnectionManager.simpleConnectionManager(dataSource))
            setDatabaseNamingStrategy(DefaultDatabaseNamingStrategy.LOWER_CASE)
            setExecutor(Executor.log())
            setSqlFormatter(SqlFormatter.PRETTY)
            setDatabaseValidationMode(
                DatabaseValidationMode.ERROR
            )
        }
        kSqlClient.validateDatabase()
        return kSqlClient
    }

    private fun determineDbPath(): Path {
        val dbPath = "./xiaomialbumsyncer.db"

        return Paths.get(dbPath)
    }

    private fun buildSQLiteUrl(dbPath: Path): String {
        return "jdbc:sqlite:${dbPath.toAbsolutePath()}" +
                "?journal_mode=WAL" +           // WAL模式，更好的并发性能
                "&synchronous=NORMAL" +         // 平衡性能和安全性
                "&cache_size=10000" +           // 缓存大小
                "&temp_store=memory" +          // 临时表存储在内存
                "&mmap_size=268435456"          // 内存映射大小(256MB)
    }
}