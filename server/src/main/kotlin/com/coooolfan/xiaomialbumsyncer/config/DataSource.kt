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
    fun defaultDataSource(): DataSource {
        val dbPath = Paths.get(sqlite)
        val parentDir = dbPath.parent
        
        // 确保父目录存在且是目录
        if (parentDir != null) {
            if (Files.exists(parentDir)) {
                // 如果存在但不是目录，删除它
                if (!Files.isDirectory(parentDir)) {
                    Files.delete(parentDir)
                    Files.createDirectories(parentDir)
                }
            } else {
                // 不存在则创建
                Files.createDirectories(parentDir)
            }
        }
        
        val config = HikariConfig()
        config.jdbcUrl = buildSQLiteUrl(dbPath)
        config.driverClassName = "org.sqlite.JDBC"
        config.maximumPoolSize = 4 // SQLite通常不需要太多连接
        config.connectionTestQuery = "SELECT 1"
        config.poolName = "SQLitePool"
        return HikariDataSource(config)
    }

}

fun buildSQLiteUrl(dbPath: Path): String {
    return "jdbc:sqlite:${dbPath.toAbsolutePath()}" +
            "?journal_mode=WAL" +           // WAL模式，更好的并发性能
            "&synchronous=NORMAL" +         // 平衡性能和安全性
            "&cache_size=10000" +           // 缓存大小
            "&temp_store=memory" +          // 临时表存储在内存
            "&mmap_size=268435456"          // 内存映射大小(256MB)
}
