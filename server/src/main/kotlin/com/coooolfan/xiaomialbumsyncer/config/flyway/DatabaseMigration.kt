package com.coooolfan.xiaomialbumsyncer.config.flyway

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationInfoService
import org.noear.solon.annotation.Configuration
import org.noear.solon.annotation.Managed
import org.noear.solon.core.runtime.NativeDetector.inNativeImage
import org.noear.solon.core.runtime.NativeDetector.isAotRuntime
import org.slf4j.LoggerFactory
import javax.sql.DataSource


@Configuration
class DatabaseMigration {

    private val log = LoggerFactory.getLogger(DatabaseMigration::class.java)

    @Managed
    fun migrate(dataSource: DataSource): Flyway {
        // 创建Flyway实例
        val configuration = Flyway.configure()
            .dataSource(dataSource)
            .locations(MIGRATION_SQL_PATH_IN_JVM)
            .baselineOnMigrate(true)
            .validateOnMigrate(true)

        val flyway =
            if (inNativeImage() || isAotRuntime()) {
                log.info("似乎正在 native-image 环境中运行，使用 IndexedResourceProvider 进行资源加载")
                configuration
                    .resourceProvider(
                        IndexedResourceProvider(
                            classLoader = Thread.currentThread().contextClassLoader,
                            encoding = Charsets.UTF_8
                        )
                    )
                    .load()
            } else {
                configuration.load()
            }


        // 执行迁移
        try {
            // 获取迁移信息
            val infoService: MigrationInfoService = flyway.info()
            infoService.all().forEach { info ->
                log.info("发现迁移: 版本: ${info.version}, 描述: ${info.description}, 状态: ${info.state}")
            }
            log.info("开始数据库迁移...")
            // 执行迁移
            val migrationsExecuted: Int = flyway.migrate().migrationsExecuted
            log.info("成功执行了 $migrationsExecuted 个迁移")
            return flyway
        } catch (e: Exception) {
            log.error("数据库迁移失败: " + e.message)
            throw RuntimeException("数据库迁移失败", e)
        }
    }

    companion object {
        const val MIGRATION_SQL_PATH_IN_JVM = "classpath:db/migration"

        const val MIGRATION_SQL_PATTERN_IN_NATIVE = "db/.*"
    }
}