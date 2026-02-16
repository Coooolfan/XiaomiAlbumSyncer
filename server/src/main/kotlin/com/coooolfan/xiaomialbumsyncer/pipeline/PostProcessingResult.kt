package com.coooolfan.xiaomialbumsyncer.pipeline

/**
 * 后处理结果
 * 
 * 用于记录同步文件后处理的执行结果，包括各个处理步骤的状态
 * 
 * @property success 后处理是否整体成功
 * @property errorMessage 错误信息（如果失败）
 * @property sha1Verified SHA1 是否已校验
 * @property exifFilled EXIF 时间是否已填充
 * @property fsTimeUpdated 文件系统时间是否已更新
 */
data class PostProcessingResult(
    val success: Boolean,
    val errorMessage: String? = null,
    val sha1Verified: Boolean = false,
    val exifFilled: Boolean = false,
    val fsTimeUpdated: Boolean = false
)
