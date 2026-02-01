package com.coooolfan.xiaomialbumsyncer.service

import com.coooolfan.xiaomialbumsyncer.model.*
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.noear.solon.annotation.Managed
import org.noear.solon.annotation.Init
import org.slf4j.LoggerFactory

/**
 * 配置迁移服务
 * 
 * 在应用启动时执行，确保所有现有配置都有正确的默认值。
 * 主要负责：
 * 1. 为旧配置设置默认 syncMode = ADD_ONLY
 * 2. 根据 enableArchive 设置 archiveMode
 * 3. 确保配置的一致性
 */
@Managed
class ConfigMigrationService(
    private val sql: KSqlClient
) {
    
    private val log = LoggerFactory.getLogger(this.javaClass)
    
    /**
     * 应用启动时自动执行配置迁移
     */
    @Init
    fun init() {
        log.info("开始执行配置迁移...")
        try {
            migrateConfigs()
            log.info("配置迁移完成")
        } catch (e: Exception) {
            log.error("配置迁移失败", e)
            // 不抛出异常，避免影响应用启动
        }
    }
    
    /**
     * 执行配置迁移
     * 
     * 迁移逻辑：
     * 1. 查询所有 Crontab 配置
     * 2. 检查每个配置是否需要迁移
     * 3. 为需要迁移的配置设置默认值
     * 4. 保存更新后的配置
     */
    fun migrateConfigs() {
        // 查询所有 Crontab
        val allCrontabs = sql.executeQuery(Crontab::class) {
            select(table.fetchBy {
                allScalarFields()
                config()
            })
        }
        
        log.info("找到 ${allCrontabs.size} 个 Crontab 配置，开始检查是否需要迁移")
        
        var migratedCount = 0
        
        allCrontabs.forEach { crontab ->
            try {
                val originalConfig = crontab.config
                val migratedConfig = migrateConfig(originalConfig)
                
                // 检查是否有变化
                if (configNeedsMigration(originalConfig, migratedConfig)) {
                    // 更新配置
                    sql.executeUpdate(Crontab::class) {
                        set(table.config, migratedConfig)
                        where(table.id eq crontab.id)
                    }
                    
                    migratedCount++
                    log.info("已迁移 Crontab ${crontab.id} (${crontab.name}) 的配置")
                    log.debug("迁移详情 - Crontab ${crontab.id}: syncMode=${originalConfig.syncMode} -> ${migratedConfig.syncMode}, archiveMode=${originalConfig.archiveMode} -> ${migratedConfig.archiveMode}")
                }
            } catch (e: Exception) {
                log.error("迁移 Crontab ${crontab.id} (${crontab.name}) 配置时发生错误", e)
                // 继续处理其他配置，不中断整个迁移过程
            }
        }
        
        log.info("配置迁移完成，共迁移了 $migratedCount 个配置")
    }
    
    /**
     * 迁移单个配置
     * 
     * @param originalConfig 原始配置
     * @return 迁移后的配置
     */
    private fun migrateConfig(originalConfig: CrontabConfig): CrontabConfig {
        var updatedConfig = originalConfig
        
        // 1. 迁移 syncMode：确保所有配置都有 syncMode = ADD_ONLY（向后兼容）
        // 根据需求 7.1：为现有 Crontab 设置默认的同步模式为 ADD_ONLY
        // 由于 Kotlin 默认值机制，这里主要是为了确保一致性和日志记录
        
        // 2. 迁移 archiveMode：根据 enableArchive 设置 archiveMode
        if (needsArchiveModeMigration(originalConfig)) {
            val newArchiveMode = if (originalConfig.enableArchive) {
                // 需求 7.4：如果 enableArchive = true，保留原有的归档模式（TIME 或 SPACE）
                // 如果原来是 DISABLED，改为 TIME（默认模式）
                if (originalConfig.archiveMode == ArchiveMode.DISABLED) {
                    ArchiveMode.TIME
                } else {
                    originalConfig.archiveMode
                }
            } else {
                // 需求 7.3：如果 enableArchive = false，将归档模式设置为 DISABLED
                ArchiveMode.DISABLED
            }
            
            updatedConfig = updatedConfig.copy(archiveMode = newArchiveMode)
            log.debug("根据 enableArchive=${originalConfig.enableArchive} 设置 archiveMode: ${originalConfig.archiveMode} -> $newArchiveMode")
        }
        
        return updatedConfig
    }
    
    /**
     * 检查是否需要迁移 syncMode
     * 
     * 根据需求 7.1：系统升级时，为现有 Crontab 设置默认的同步模式为 ADD_ONLY
     * 由于 Kotlin 的默认值机制，syncMode 字段总是有值，所以这里返回 false
     * 但保留此方法用于未来可能的扩展和一致性检查
     * 
     * @param config 配置对象
     * @return 是否需要迁移
     */
    private fun needsSyncModeMigration(config: CrontabConfig): Boolean {
        // 由于 Kotlin 的默认值机制，syncMode 总是有值（默认为 ADD_ONLY）
        // 这符合需求 7.1 的要求：为现有 Crontab 设置默认的同步模式为 ADD_ONLY
        return false
    }
    
    /**
     * 检查是否需要迁移 archiveMode
     * 
     * 根据需求 7.3 和 7.4：
     * - 需求 7.3：如果 enableArchive = false，将归档模式设置为 DISABLED
     * - 需求 7.4：如果 enableArchive = true，保留原有的归档模式（TIME 或 SPACE）
     * 
     * @param config 配置对象
     * @return 是否需要迁移
     */
    private fun needsArchiveModeMigration(config: CrontabConfig): Boolean {
        return when {
            // 需求 7.3：如果 enableArchive = false 但 archiveMode != DISABLED，需要迁移
            !config.enableArchive && config.archiveMode != ArchiveMode.DISABLED -> {
                log.debug("检测到需要迁移：enableArchive=false 但 archiveMode=${config.archiveMode}")
                true
            }
            
            // 需求 7.4：如果 enableArchive = true 但 archiveMode = DISABLED，需要迁移为 TIME
            config.enableArchive && config.archiveMode == ArchiveMode.DISABLED -> {
                log.debug("检测到需要迁移：enableArchive=true 但 archiveMode=DISABLED")
                true
            }
            
            // 其他情况不需要迁移
            else -> false
        }
    }
    
    /**
     * 检查配置是否需要迁移（通过比较原始配置和迁移后配置）
     * 
     * @param originalConfig 原始配置
     * @param migratedConfig 迁移后配置
     * @return 是否需要迁移
     */
    private fun configNeedsMigration(originalConfig: CrontabConfig, migratedConfig: CrontabConfig): Boolean {
        return originalConfig.syncMode != migratedConfig.syncMode ||
               originalConfig.archiveMode != migratedConfig.archiveMode
    }
}