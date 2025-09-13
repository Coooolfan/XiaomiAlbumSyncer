package com.coooolfan.xiaomialbumsyncer.model

import org.babyfish.jimmer.sql.Serialized

@Serialized
data class CrontabConfig(

    // cron 表达式
    // eg: "0 0 * * *" 每天午夜
    val expression: String,

    val timeZone: String,

    val targetPath: String,

    val downloadImages: Boolean,

    val downloadVideos: Boolean,

    // 还没实现
    val rewriteExifTime: Boolean
)