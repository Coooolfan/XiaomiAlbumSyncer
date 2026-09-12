package com.coooolfan.xiaomialbumsyncer.mcp

import com.coooolfan.xiaomialbumsyncer.model.CrontabConfig

/**
 * xas_query 工具的出参 DTO。
 *
 * 时间戳统一输出 ISO-8601 字符串（currentStats.ts 为毫秒时间戳），
 * 出参不含密码/passToken/通知配置等敏感信息。
 */

// ---------- album ----------

data class AlbumListOutput(
    val hint: String,
    val albums: List<AlbumItem>,
)

data class AlbumItem(
    val id: String,
    val remoteId: String,
    val name: String,
    val assetCount: Long,
    val lastUpdateTime: String,
    val shadow: Boolean,
    val accountNickname: String?,
)

// ---------- crontab ----------

data class CrontabListOutput(
    val hint: String,
    val crontabs: List<CrontabSummaryItem>,
)

data class CrontabSummaryItem(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val running: Boolean,
    val lastRunTime: String?,
)

data class CrontabGetOutput(
    val hint: String,
    val crontab: CrontabOverview,
    val currentStats: CrontabCurrentStatsOutput,
)

data class CrontabTriggerOutput(
    val hint: String,
    val triggered: Boolean,
    val crontab: TriggeredCrontab,
)

data class TriggeredCrontab(
    val id: String,
    val name: String,
)

data class CrontabOverview(
    val id: String,
    val name: String,
    val description: String,
    val enabled: Boolean,
    val running: Boolean,
    val albumIds: List<String>,
    val lastRunTime: String?,
    val config: CrontabConfig,
)

data class CrontabCurrentStatsOutput(
    val ts: Long?,
    val assetCount: Long?,
    val downloadCompletedCount: Long?,
    val sha1VerifiedCount: Long?,
    val exifFilledCount: Long?,
    val fsTimeUpdatedCount: Long?,
)

// ---------- crontab_history ----------

data class CrontabHistoryListOutput(
    val hint: String,
    val totalCount: Long,
    val pageIndex: Int,
    val pageSize: Int,
    val rows: List<CrontabHistoryItem>,
)

data class CrontabHistoryItem(
    val id: String,
    val crontabId: String,
    val crontabName: String,
    val startTime: String,
    val endTime: String?,
    val isCompleted: Boolean,
    val detailsCount: Long,
)

// ---------- crontab_history_detail ----------

data class CrontabHistoryDetailListOutput(
    val hint: String,
    val history: CrontabHistoryOverview,
    val totalCount: Long,
    val pageIndex: Int,
    val pageSize: Int,
    val rows: List<HistoryDetailItem>,
)

data class CrontabHistoryOverview(
    val id: String,
    val startTime: String,
    val endTime: String?,
    val isCompleted: Boolean,
)

data class HistoryDetailItem(
    val asset: HistoryDetailAsset,
    val filePath: String,
    val downloadCompleted: Boolean,
    val sha1Verified: Boolean,
    val exifFilled: Boolean,
    val fsTimeUpdated: Boolean,
    val message: String?,
)

data class HistoryDetailAsset(
    val fileName: String,
    val type: String,
    val albumName: String?,
)

// ---------- system ----------

data class SystemListOutput(
    val hint: String,
    val accounts: List<McpAccountItem>,
    val info: McpSystemInfo,
)

data class McpAccountItem(
    val nickname: String,
    val userId: String,
)

data class McpSystemInfo(
    val initialized: Boolean,
    val assetsDateMapTimeZone: String?,
    val appVersion: String,
)
