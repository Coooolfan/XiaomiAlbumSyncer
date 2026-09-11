package com.coooolfan.xiaomialbumsyncer.mcp

import com.coooolfan.xiaomialbumsyncer.model.CrontabConfig

/**
 * xas_query 工具的出参 DTO。
 *
 * 时间戳统一输出 ISO-8601 字符串（currentStats.ts 为毫秒时间戳），
 * 出参不含密码/passToken/通知配置等敏感信息。
 */

// ---------- 各 domain 的 hint（下一步操作提示） ----------

internal const val HINT_HELP = "除 domain=crontab&action=trigger 外均为只读；随时可调用 domain=help 查看完整用法"

internal const val HINT_ALBUM =
    "相册列表为只读快照，shadow=true 表示远程已不存在的本地相册；定时任务运行情况可查询 domain=crontab"

internal const val HINT_CRONTAB_LIST =
    "使用 domain=crontab&action=get 配合 filter.id=<任务id> 查看任务概况与实时统计；domain=crontab_history 查看运行历史"

internal const val HINT_CRONTAB_TRIGGER =
    "任务已异步触发；可用 domain=crontab&action=get 配合 filter.id=<任务id> 查看实时统计，" +
        "或 domain=crontab_history 查看运行历史"

internal const val HINT_CRONTAB_TRIGGER_SKIPPED =
    "任务正在运行中，本次触发被跳过；可用 domain=crontab&action=get 配合 filter.id=<任务id> 查看实时统计"

internal const val HINT_CRONTAB_GET =
    "使用 domain=crontab_history 配合 filter.id=<任务id> 查看该任务的运行历史，" +
        "再以 domain=crontab_history_detail&filter.id=<历史id> 查看某次运行的下载明细"

internal const val HINT_CRONTAB_HISTORY =
    "使用 domain=crontab_history_detail 配合 filter.id=<历史id> 查看该次运行的下载明细"

internal const val HINT_CRONTAB_HISTORY_DETAIL = "通过 pageIndex/pageSize 翻页查看更多明细"

internal const val HINT_SYSTEM = "仅展示脱敏信息；密码/passToken/通知配置不通过 MCP 暴露"

// ---------- help ----------

data class HelpOutput(
    val hint: String,
    val usage: UsageDoc,
)

data class UsageDoc(
    val description: String,
    val input: InputDoc,
    val domains: Map<String, DomainDoc>,
    val pagination: PaginationDoc,
    val notes: List<String>,
)

data class InputDoc(
    val domain: String,
    val action: String,
    val pageIndex: String,
    val pageSize: String,
    val filter: String,
)

data class DomainDoc(
    val actions: List<String>,
    val desc: String,
    val filter: String?,
    val example: Map<String, Any?>,
)

data class PaginationDoc(
    val pageIndex: String,
    val pageSize: String,
)

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
