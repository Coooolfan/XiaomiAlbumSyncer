package com.coooolfan.xiaomialbumsyncer.pipeline

import com.coooolfan.xiaomialbumsyncer.model.Asset
import com.coooolfan.xiaomialbumsyncer.model.Crontab
import java.nio.file.Path
import java.time.Instant

/**
 * 资产处理任务上下文
 *
 * 作用：
 * 1. 承载单个 Asset 在整个流水线中流转的所有状态信息
 * 2. 包含 Asset 本身的元数据、目标路径、临时文件位置等
 * 3. 记录任务在各个阶段的处理状态和时间戳
 * 4. 支持重试计数和错误信息记录
 * 5. 降低各处理阶段之间的耦合度
 *
 * 生命周期：创建 -> 下载 -> 校验 -> EXIF 处理 -> 文件时间处理 -> 完成/失败
 */
data class AssetPipelineContext(
    val asset: Asset,
    val crontab: Crontab,
    val crontabHistoryId: Long,
    val targetPath: Path,
    val maxRetry: Int = 3,
    val createdAt: Instant = Instant.now(),
    var retry: Int = 0,
    var downloadedPath: Path? = null,
    var sha1Verified: Boolean = false,
    var finalPath: Path? = null,
    var lastError: Throwable? = null,
    var detailId: Long? = null,
    var abandoned: Boolean = false,
)

val AssetPipelineContext.config
    get() = crontab.config
