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
     * 同步模式
     * ADD_ONLY: 仅新增模式，只下载云端新增的文件到本地
     * SYNC_ALL_CHANGES: 同步所有变化模式，同步云端的新增、修改、删除到本地
     * 默认为 ADD_ONLY 以保持向后兼容
     */
    val syncMode: SyncMode = SyncMode.ADD_ONLY,

    /**
     * 同步文件夹名称（相对于 targetPath）
     */
    val syncFolder: String = "sync",

    // ========== 归档配置 ==========

    /**
     * 归档模式
     * DISABLED: 关闭归档，不执行任何归档操作
     * TIME: 基于时间归档，归档超过指定天数的照片
     * SPACE: 基于空间阈值归档，当云端空间不足时自动归档旧照片
     * 默认为 DISABLED
     */
    val archiveMode: ArchiveMode = ArchiveMode.DISABLED,

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