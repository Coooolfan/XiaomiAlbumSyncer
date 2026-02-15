package com.coooolfan.xiaomialbumsyncer.pipeline

import com.coooolfan.xiaomialbumsyncer.controller.SystemConfigController.Companion.NORMAL_SYSTEM_CONFIG
import com.coooolfan.xiaomialbumsyncer.model.Asset
import com.coooolfan.xiaomialbumsyncer.model.CrontabConfig
import com.coooolfan.xiaomialbumsyncer.pipeline.stages.ExifProcessingStage
import com.coooolfan.xiaomialbumsyncer.pipeline.stages.FileTimeStage
import com.coooolfan.xiaomialbumsyncer.pipeline.stages.VerificationStage
import com.coooolfan.xiaomialbumsyncer.service.SystemConfigService
import org.noear.solon.annotation.Managed
import org.slf4j.LoggerFactory
import java.nio.file.Path

/**
 * 后处理协调器
 * 
 * 负责编排同步文件的后处理流程，包括 SHA1 校验、EXIF 时间填充和文件系统时间更新。
 * 按照固定顺序执行各个处理步骤，并根据配置决定是否执行每个步骤。
 * 
 * 处理顺序：SHA1 校验 → EXIF 时间填充 → 文件系统时间更新
 */
@Managed
class PostProcessingCoordinator(
    private val verificationStage: VerificationStage,
    private val exifProcessingStage: ExifProcessingStage,
    private val fileTimeStage: FileTimeStage,
    private val systemConfigService: SystemConfigService
) {
    private val log = LoggerFactory.getLogger(this.javaClass)

    /**
     * 执行后处理流程
     * 
     * @param asset 资产对象
     * @param filePath 文件路径
     * @param config 定时任务配置
     * @return 后处理结果
     */
    fun process(asset: Asset, filePath: Path, config: CrontabConfig): PostProcessingResult {
        var sha1Verified = false
        var exifFilled = false
        var fsTimeUpdated = false
        var errorMessage: String? = null

        try {
            // 步骤 1: SHA1 校验
            if (config.checkSha1) {
                log.info("开始执行 SHA1 校验步骤，资源 ID: {}", asset.id)
                try {
                    verificationStage.verifySha1(asset, filePath)
                    sha1Verified = true
                    log.info("SHA1 校验步骤成功完成，资源 ID: {}", asset.id)
                } catch (e: Exception) {
                    log.error("SHA1 校验步骤失败，资源 ID: {}，错误: {}", asset.id, e.message, e)
                    errorMessage = "SHA1 校验失败: ${e.message}"
                    // SHA1 校验失败是不可恢复的错误，停止后续处理
                    return PostProcessingResult(
                        success = false,
                        errorMessage = errorMessage,
                        sha1Verified = false,
                        exifFilled = false,
                        fsTimeUpdated = false
                    )
                }
            } else {
                log.info("SHA1 校验步骤已禁用，跳过，资源 ID: {}", asset.id)
            }

            // 步骤 2: EXIF 时间填充
            if (config.rewriteExifTime) {
                log.info("开始执行 EXIF 时间填充步骤，资源 ID: {}", asset.id)
                try {
                    val systemConfig = systemConfigService.getConfig(NORMAL_SYSTEM_CONFIG)
                    exifProcessingStage.fillExifTime(
                        asset,
                        filePath,
                        systemConfig,
                        config.rewriteExifTimeZone
                    )
                    exifFilled = true
                    log.info("EXIF 时间填充步骤成功完成，资源 ID: {}", asset.id)
                } catch (e: Exception) {
                    // EXIF 填充失败是可恢复的错误，记录警告但继续处理
                    log.warn("EXIF 时间填充步骤失败，资源 ID: {}，错误: {}，将继续后续处理", asset.id, e.message, e)
                    // 不设置 errorMessage，因为这是可恢复的错误
                }
            } else {
                log.info("EXIF 时间填充步骤已禁用，跳过，资源 ID: {}", asset.id)
            }

            // 步骤 3: 文件系统时间更新
            if (config.rewriteFileSystemTime) {
                log.info("开始执行文件系统时间更新步骤，资源 ID: {}", asset.id)
                try {
                    fileTimeStage.updateFileSystemTime(asset, filePath)
                    fsTimeUpdated = true
                    log.info("文件系统时间更新步骤成功完成，资源 ID: {}", asset.id)
                } catch (e: Exception) {
                    log.error("文件系统时间更新步骤失败，资源 ID: {}，错误: {}", asset.id, e.message, e)
                    // 文件时间更新失败不影响文件的可用性，记录错误但标记为成功
                    // 不设置 errorMessage，因为文件本身是可用的
                }
            } else {
                log.info("文件系统时间更新步骤已禁用，跳过，资源 ID: {}", asset.id)
            }

            // 所有步骤完成，返回成功结果
            return PostProcessingResult(
                success = true,
                errorMessage = null,
                sha1Verified = sha1Verified,
                exifFilled = exifFilled,
                fsTimeUpdated = fsTimeUpdated
            )

        } catch (e: Exception) {
            // 捕获任何未预期的异常
            log.error("后处理流程发生未预期的错误，资源 ID: {}", asset.id, e)
            return PostProcessingResult(
                success = false,
                errorMessage = "后处理失败: ${e.message}",
                sha1Verified = sha1Verified,
                exifFilled = exifFilled,
                fsTimeUpdated = fsTimeUpdated
            )
        }
    }
}
