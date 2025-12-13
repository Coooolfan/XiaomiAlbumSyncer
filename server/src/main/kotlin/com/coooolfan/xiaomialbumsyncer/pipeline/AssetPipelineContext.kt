package com.coooolfan.xiaomialbumsyncer.pipeline

import com.coooolfan.xiaomialbumsyncer.model.Asset
import com.coooolfan.xiaomialbumsyncer.model.CrontabConfig
import com.coooolfan.xiaomialbumsyncer.model.CrontabHistoryDetail

/**
 * 资产处理任务上下文
 *
 * 作用：
 * 1. 承载单个 Asset 在整个流水线中流转的所有状态信息
 * 2. 包含 Asset 本身的元数据、目标路径等
 * 3. 记录任务在各个阶段的处理状态和时间戳
 * 4. 降低各处理阶段之间的耦合度
 *
 * 生命周期：创建 -> 下载 -> 校验 -> EXIF 处理 -> 文件时间处理 -> 完成/失败
 */
// TODO)) 感觉这个完全可以用 CrontabHistoryDetail 替代
data class AssetPipelineContext(
    val asset: Asset,
    val crontabConfig: CrontabConfig,
    var detail: CrontabHistoryDetail
)
