package com.coooolfan.xiaomialbumsyncer.model

import org.babyfish.jimmer.sql.Serialized
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@Serialized
@JsonIgnoreProperties(ignoreUnknown = true)
data class NotifyConfig(

    val url: String,

    val headers: Map<String, String>,

    val body: String,

    val dailySummaryBody: String?,

    val dailySummaryCron: String?,

    val dailySummaryTimeZone: String?,

    val passTokenExpiredBody: String?,
)