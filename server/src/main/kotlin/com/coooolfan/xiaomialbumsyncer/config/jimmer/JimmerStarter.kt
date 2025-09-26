package com.coooolfan.xiaomialbumsyncer.config.jimmer

import org.babyfish.jimmer.sql.dialect.SQLiteDialect
import org.babyfish.jimmer.sql.exception.DatabaseValidationException
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.newKSqlClient
import org.babyfish.jimmer.sql.runtime.*
import org.flywaydb.core.Flyway
import org.noear.solon.annotation.Configuration
import org.noear.solon.annotation.Managed
import org.slf4j.LoggerFactory
import javax.sql.DataSource


@Configuration
class JimmerStarter {

    private val log = LoggerFactory.getLogger(JimmerStarter::class.java)

    @Managed
    fun sql(dataSource: DataSource, flyway: Flyway): KSqlClient {
        // flyway 的注入仅用于确保在初始化 KSqlClient 之前执行 Flyway 迁移
        try {
            val kSqlClient = newKSqlClient {
                log.info("初始化 kSqlClient 并校验表结构")
                setDialect(SQLiteDialect())
                setConnectionManager(ConnectionManager.simpleConnectionManager(dataSource))
                setDatabaseNamingStrategy(DefaultDatabaseNamingStrategy.LOWER_CASE)
                setExecutor(Executor.log())
                setSqlFormatter(SqlFormatter.PRETTY)
                setDatabaseValidationMode(
                    DatabaseValidationMode.ERROR
                )
            }
            return kSqlClient
        } catch (e: DatabaseValidationException) {
            log.info("数据库校验失败: " + e.message)
            log.info(
                "1. 如果您正处于开发环境，请确保已执行最新的迁移脚本，并根据报错信息手动调整数据库或Flyway迁移文件。\n" +
                        "2. 如果您正处于生产环境，请前往Github仓库提交issue寻求帮助。此报错不应该出现在生产环境。"
            )
            throw e
        }
    }
}

