package com.coooolfan.xiaomialbumsyncer.xiaomicloud

/**
 * 云端空间使用详情项
 */
data class SpaceUsageItem(
    val size: Long,     // 使用空间（字节）
    val text: String    // 显示文本
)

/**
 * 云端空间使用情况信息
 */
data class CloudSpaceInfo(
    val totalQuota: Long,     // 总空间（字节）
    val used: Long, // 已用空间（字节）
    val galleryUsed: Long, // 相册使用空间（字节）
    val usagePercent: Int, // 使用百分比
    val usedDetail: Map<String, SpaceUsageItem> // 详细使用信息
)
