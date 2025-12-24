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
    )