package com.coooolfan.xiaomialbumsyncer.model

import org.babyfish.jimmer.sql.Serialized

@Serialized
data class CrontabConfig(

    // cron 表达式
    // eg: "0 0 * * * ?" 每天午夜
    val expression: String,

    val timeZone: String,

    val targetPath: String,

    val downloadImages: Boolean,

    val downloadVideos: Boolean,

    val rewriteExifTime: Boolean,

    val diffByTimeline: Boolean = false,

    val rewriteExifTimeZone: String?,

    val skipExistingFile: Boolean = true,

    val rewriteFileSystemTime: Boolean = false,

    val checkSha1: Boolean = false,

    val fetchFromDbSize: Int = 2,

    val downloaders: Int = 8,

    val verifiers: Int = 2,

    val exifProcessors: Int = 2,

    val fileTimeWorkers: Int = 2,

    val downloadAudios: Boolean = true,

    val expressionTargetPath: String = "",

    // ========== 同步配置 ==========

    /**
     * 是否启用同步功能
     */
    val enableSync: Boolean = false,

    /**
     * 同步文件夹名称（相对于 targetPath）
     */
    val syncFolder: String = "sync",

    // ========== 归档配置 ==========

    /**
     * 是否启用归档功能
     */
    val enableArchive: Boolean = false,

    /**
     * 归档模式（TIME: 基于时间, SPACE: 基于空间阈值）
     */
    val archiveMode: ArchiveMode = ArchiveMode.TIME,

    /**
     * 保留天数（时间模式）
     */
    val archiveDays: Int = 30,

    /**
     * 云空间阈值百分比（空间模式）
     */
    val cloudSpaceThreshold: Int = 90,

    /**
     * 归档文件夹名称（相对于 targetPath）
     */
    val backupFolder: String = "backup",

    /**
     * 归档后是否删除云端
     */
    val deleteCloudAfterArchive: Boolean = true,

    /**
     * 归档前是否需要确认
     */
    val confirmBeforeArchive: Boolean = true,
)