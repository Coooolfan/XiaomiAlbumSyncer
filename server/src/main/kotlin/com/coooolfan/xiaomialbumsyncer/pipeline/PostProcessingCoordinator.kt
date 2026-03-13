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

@Managed
class PostProcessingCoordinator(
    private val verificationStage: VerificationStage,
    private val exifProcessingStage: ExifProcessingStage,
    private val fileTimeStage: FileTimeStage,
    private val systemConfigService: SystemConfigService
) {
    private val log = LoggerFactory.getLogger(this.javaClass)

    fun process(asset: Asset, filePath: Path, config: CrontabConfig): PostProcessingResult {
        var sha1Verified = false
        var exifFilled = false
        var fsTimeUpdated = false
        var errorMessage: String? = null

        try {
            if (config.checkSha1) {
                try {
                    verificationStage.verifySha1(asset, filePath)
                    sha1Verified = true
                } catch (e: Exception) {
                    log.error("SHA1 校验失败，资源 ID: ${asset.id}", e)
                    errorMessage = "SHA1 校验失败: ${e.message}"
                    return PostProcessingResult(
                        success = false,
                        errorMessage = errorMessage,
                        sha1Verified = false,
                        exifFilled = false,
                        fsTimeUpdated = false
                    )
                }
            }

            if (config.rewriteExifTime) {
                try {
                    val systemConfig = systemConfigService.getConfig(NORMAL_SYSTEM_CONFIG)
                    exifProcessingStage.fillExifTime(
                        asset,
                        filePath,
                        systemConfig,
                        config.rewriteExifTimeZone
                    )
                    exifFilled = true
                } catch (e: Exception) {
                    log.warn("EXIF 时间填充失败，资源 ID: ${asset.id}", e)
                }
            }

            if (config.rewriteFileSystemTime) {
                try {
                    fileTimeStage.updateFileSystemTime(asset, filePath)
                    fsTimeUpdated = true
                } catch (e: Exception) {
                    log.error("文件系统时间更新失败，资源 ID: ${asset.id}", e)
                }
            }

            return PostProcessingResult(
                success = true,
                errorMessage = null,
                sha1Verified = sha1Verified,
                exifFilled = exifFilled,
                fsTimeUpdated = fsTimeUpdated
            )

        } catch (e: Exception) {
            log.error("后处理流程发生未预期的错误，资源 ID: ${asset.id}", e)
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
