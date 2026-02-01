package com.coooolfan.xiaomialbumsyncer.xiaomicloud

/**
 * 云端空间使用情况信息
 */
data class CloudSpaceInfo(
    /**
     * 总空间（字节）
     */
    val totalQuota: Long,

    /**
     * 已用空间（字节）
     */
    val used: Long,

    /**
     * 相册使用空间（字节）
     */
    val galleryUsed: Long,

    /**
     * 使用百分比
     */
    val usagePercent: Int
)
