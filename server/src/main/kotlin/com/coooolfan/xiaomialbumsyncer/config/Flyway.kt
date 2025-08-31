package com.coooolfan.xiaomialbumsyncer.config

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationInfoService
import org.noear.solon.annotation.Bean
import org.noear.solon.annotation.Configuration
import org.slf4j.LoggerFactory
import javax.sql.DataSource


@Configuration
class DatabaseMigration {

    private val log = LoggerFactory.getLogger(DatabaseMigration::class.java)

    @Bean
    fun migrate(dataSource: DataSource) {
        // 创建Flyway实例
        val flyway: Flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration") // 迁移脚本位置
            .baselineOnMigrate(true) // 如果数据库不为空，自动基线化
            .validateOnMigrate(true) // 验证迁移
            .load()

        // 执行迁移
        try {
            // 获取迁移信息
            val infoService: MigrationInfoService = flyway.info()
            infoService.all().forEach { info ->
                log.info("迁移版本: ${info.version}, 描述: ${info.description}, 状态: ${info.state}")
            }
            log.info("开始数据库迁移...")
            // 执行迁移
            val migrationsExecuted: Int = flyway.migrate().migrationsExecuted
            log.info("成功执行了 $migrationsExecuted 个迁移")
        } catch (e: Exception) {
            log.error("数据库迁移失败: " + e.message)
            throw RuntimeException("数据库迁移失败", e)
        }
    }
}