package com.coooolfan.xiaomialbumsyncer.validation

import com.coooolfan.xiaomialbumsyncer.model.ArchiveMode
import com.coooolfan.xiaomialbumsyncer.model.CrontabConfig
import com.coooolfan.xiaomialbumsyncer.model.SyncMode
import org.noear.solon.annotation.Component
import org.slf4j.LoggerFactory

/**
 * 配置验证器
 * 
 * 负责验证 CrontabConfig 中的各种配置项，确保配置的有效性和一致性
 */
@Component
class ConfigValidator {
    
    private val logger = LoggerFactory.getLogger(ConfigValidator::class.java)

    /**
     * 验证同步模式配置
     * 
     * @param config 定时任务配置
     * @return 验证结果
     */
    fun validateSyncMode(config: CrontabConfig): ValidationResult {
        // syncMode 必须是有效的枚举值
        if (config.syncMode !in SyncMode.values()) {
            return ValidationResult.error("无效的同步模式：${config.syncMode}")
        }
        
        logger.debug("同步模式验证通过：${config.syncMode}")
        return ValidationResult.success()
    }

    /**
     * 验证归档模式配置
     * 
     * @param config 定时任务配置
     * @return 验证结果
     */
    fun validateArchiveMode(config: CrontabConfig): ValidationResult {
        // archiveMode 必须是有效的枚举值
        if (config.archiveMode !in ArchiveMode.values()) {
            return ValidationResult.error("无效的归档模式：${config.archiveMode}")
        }
        
        // 如果是 TIME 模式，archiveDays 必须是正整数
        if (config.archiveMode == ArchiveMode.TIME && config.archiveDays <= 0) {
            return ValidationResult.error("保留天数必须是正整数，当前值：${config.archiveDays}")
        }
        
        // 如果是 SPACE 模式，cloudSpaceThreshold 必须在 1-100 之间
        if (config.archiveMode == ArchiveMode.SPACE) {
            if (config.cloudSpaceThreshold < 1 || config.cloudSpaceThreshold > 100) {
                return ValidationResult.error("空间阈值必须在 1-100 之间，当前值：${config.cloudSpaceThreshold}")
            }
        }
        
        logger.debug("归档模式验证通过：${config.archiveMode}")
        return ValidationResult.success()
    }

    /**
     * 验证配置一致性
     * 
     * 检查 enableArchive 和 archiveMode 之间的一致性
     * 
     * @param config 定时任务配置
     * @return 验证结果
     */
    fun validateConfigConsistency(config: CrontabConfig): ValidationResult {
        // 如果 enableArchive 为 false，archiveMode 应该是 DISABLED
        if (!config.enableArchive && config.archiveMode != ArchiveMode.DISABLED) {
            logger.warn("配置不一致：enableArchive 为 false 但 archiveMode 不是 DISABLED，将自动修正")
            // 这是一个警告，不是错误，因为我们会自动修正
        }
        
        // 如果 archiveMode 是 DISABLED，enableArchive 应该是 false
        if (config.archiveMode == ArchiveMode.DISABLED && config.enableArchive) {
            logger.warn("配置不一致：archiveMode 为 DISABLED 但 enableArchive 为 true，将自动修正")
            // 这是一个警告，不是错误，因为我们会自动修正
        }
        
        logger.debug("配置一致性验证通过")
        return ValidationResult.success()
    }

    /**
     * 验证完整的配置
     * 
     * 执行所有验证规则
     * 
     * @param config 定时任务配置
     * @return 验证结果
     */
    fun validateConfig(config: CrontabConfig): ValidationResult {
        // 验证同步模式
        val syncModeResult = validateSyncMode(config)
        if (!syncModeResult.isSuccess) {
            return syncModeResult
        }
        
        // 验证归档模式
        val archiveModeResult = validateArchiveMode(config)
        if (!archiveModeResult.isSuccess) {
            return archiveModeResult
        }
        
        // 验证配置一致性
        val consistencyResult = validateConfigConsistency(config)
        if (!consistencyResult.isSuccess) {
            return consistencyResult
        }
        
        logger.info("配置验证全部通过")
        return ValidationResult.success()
    }

    /**
     * 自动修正配置不一致问题
     * 
     * @param config 原始配置
     * @return 修正后的配置
     */
    fun autoCorrectConfig(config: CrontabConfig): CrontabConfig {
        var correctedConfig = config
        
        // 如果 enableArchive 为 false，自动设置 archiveMode 为 DISABLED
        if (!config.enableArchive && config.archiveMode != ArchiveMode.DISABLED) {
            logger.info("自动修正：enableArchive 为 false，设置 archiveMode 为 DISABLED")
            correctedConfig = correctedConfig.copy(archiveMode = ArchiveMode.DISABLED)
        }
        
        // 如果 archiveMode 为 DISABLED，自动设置 enableArchive 为 false
        if (config.archiveMode == ArchiveMode.DISABLED && config.enableArchive) {
            logger.info("自动修正：archiveMode 为 DISABLED，设置 enableArchive 为 false")
            correctedConfig = correctedConfig.copy(enableArchive = false)
        }
        
        return correctedConfig
    }
}

/**
 * 验证结果数据类
 */
data class ValidationResult(
    val isSuccess: Boolean,
    val errorMessage: String? = null
) {
    companion object {
        /**
         * 创建成功的验证结果
         */
        fun success(): ValidationResult = ValidationResult(true)
        
        /**
         * 创建失败的验证结果
         * 
         * @param message 错误消息
         */
        fun error(message: String): ValidationResult = ValidationResult(false, message)
    }
}