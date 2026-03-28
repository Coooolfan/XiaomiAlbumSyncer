package com.coooolfan.xiaomialbumsyncer.model

import org.babyfish.jimmer.sql.Serialized

@Serialized
data class NotifyConfig(

    val url: String,

    val headers: Map<String, String>,

    val body: String,

    val dailySummaryBody: String?,

    val dailySummaryCron: String?,

    val dailySummaryTimeZone: String?,
)