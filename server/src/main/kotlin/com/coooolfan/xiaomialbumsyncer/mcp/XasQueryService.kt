package com.coooolfan.xiaomialbumsyncer.mcp

import com.coooolfan.xiaomialbumsyncer.exception.BadRequestException
import com.coooolfan.xiaomialbumsyncer.model.*
import com.coooolfan.xiaomialbumsyncer.service.CrontabService
import com.coooolfan.xiaomialbumsyncer.service.SystemConfigService
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.asc
import org.babyfish.jimmer.sql.kt.ast.expression.desc
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.noear.solon.annotation.Managed

/**
 * 为 MCP xas_query 工具提供数据查询和受权限控制的任务触发。
 */
@Managed
class XasQueryService(
    private val sql: KSqlClient,
    private val crontabService: CrontabService,
) {

    fun listAlbums(fetcher: Fetcher<Album>): AlbumListOutput {
        val albums = sql.executeQuery(Album::class) {
            select(table.fetch(fetcher))
        }.map { album ->
            AlbumItem(
                id = album.id.toString(),
                remoteId = album.remoteId.toString(),
                name = album.name,
                assetCount = album.assetCount,
                lastUpdateTime = album.lastUpdateTime.toString(),
                shadow = album.shadow,
                accountNickname = album.account.nickname,
            )
        }
        return AlbumListOutput(HINT_ALBUM, albums)
    }

    fun listCrontabs(fetcher: Fetcher<Crontab>): CrontabListOutput {
        val crontabs = sql.executeQuery(Crontab::class) {
            select(table.fetch(fetcher))
        }.map { crontab ->
            CrontabSummaryItem(
                id = crontab.id.toString(),
                name = crontab.name,
                enabled = crontab.enabled,
                running = crontab.running,
                lastRunTime = crontab.histories.firstOrNull()?.startTime?.toString(),
            )
        }
        return CrontabListOutput(HINT_CRONTAB_LIST, crontabs)
    }

    fun getCrontab(crontabId: Long, fetcher: Fetcher<Crontab>): CrontabGetOutput {
        val crontab = sql.findById(fetcher, crontabId)
            ?: throw BadRequestException("定时任务不存在: $crontabId")
        val stats = crontabService.getCrontabCurrentStats(crontabId)

        return CrontabGetOutput(
            hint = HINT_CRONTAB_GET,
            crontab = CrontabOverview(
                id = crontab.id.toString(),
                name = crontab.name,
                description = crontab.description,
                enabled = crontab.enabled,
                running = crontab.running,
                albumIds = crontab.albumIds.map(Long::toString),
                lastRunTime = crontab.histories.firstOrNull()?.startTime?.toString(),
                config = crontab.config,
            ),
            currentStats = CrontabCurrentStatsOutput(
                ts = stats.ts?.toEpochMilli(),
                assetCount = stats.assetCount,
                downloadCompletedCount = stats.downloadCompletedCount,
                sha1VerifiedCount = stats.sha1VerifiedCount,
                exifFilledCount = stats.exifFilledCount,
                fsTimeUpdatedCount = stats.fsTimeUpdatedCount,
            ),
        )
    }

    /**
     * 触发指定定时任务立即执行（异步）；运行中的任务不重复触发，triggered=false。
     */
    fun triggerCrontab(crontabId: Long, fetcher: Fetcher<Crontab>): CrontabTriggerOutput {
        val crontab = sql.findById(fetcher, crontabId)
            ?: throw BadRequestException("定时任务不存在: $crontabId")

        val triggered = if (crontabService.isCrontabRunning(crontabId)) {
            false
        } else {
            crontabService.executeCrontab(crontabId)
            true
        }

        return CrontabTriggerOutput(
            hint = if (triggered) HINT_CRONTAB_TRIGGER else HINT_CRONTAB_TRIGGER_SKIPPED,
            triggered = triggered,
            crontab = TriggeredCrontab(id = crontab.id.toString(), name = crontab.name),
        )
    }

    fun listCrontabHistories(
        crontabId: Long?,
        pageIndex: Int,
        pageSize: Int,
        fetcher: Fetcher<CrontabHistory>,
    ): CrontabHistoryListOutput {
        val page = sql.createQuery(CrontabHistory::class) {
            crontabId?.let { where(table.crontabId eq it) }
            orderBy(table.startTime.desc(), table.id.desc())
            select(table.fetch(fetcher))
        }.fetchPage(pageIndex, pageSize)

        return CrontabHistoryListOutput(
            hint = HINT_CRONTAB_HISTORY,
            totalCount = page.totalRowCount,
            pageIndex = pageIndex,
            pageSize = pageSize,
            rows = page.rows.map { history ->
                CrontabHistoryItem(
                    id = history.id.toString(),
                    crontabId = history.crontab.id.toString(),
                    crontabName = history.crontab.name,
                    startTime = history.startTime.toString(),
                    endTime = history.endTime?.toString(),
                    isCompleted = history.isCompleted,
                    detailsCount = history.detailsCount,
                )
            },
        )
    }

    fun listCrontabHistoryDetails(
        historyId: Long,
        pageIndex: Int,
        pageSize: Int,
        historyFetcher: Fetcher<CrontabHistory>,
        detailFetcher: Fetcher<CrontabHistoryDetail>,
    ): CrontabHistoryDetailListOutput {
        val history = sql.findById(historyFetcher, historyId)
            ?: throw BadRequestException("执行历史不存在: $historyId")

        val page = sql.createQuery(CrontabHistoryDetail::class) {
            where(table.crontabHistoryId eq historyId)
            orderBy(table.id.asc())
            select(table.fetch(detailFetcher))
        }.fetchPage(pageIndex, pageSize)

        return CrontabHistoryDetailListOutput(
            hint = HINT_CRONTAB_HISTORY_DETAIL,
            history = CrontabHistoryOverview(
                id = history.id.toString(),
                startTime = history.startTime.toString(),
                endTime = history.endTime?.toString(),
                isCompleted = history.isCompleted,
            ),
            totalCount = page.totalRowCount,
            pageIndex = pageIndex,
            pageSize = pageSize,
            rows = page.rows.map { detail ->
                HistoryDetailItem(
                    asset = HistoryDetailAsset(
                        fileName = detail.asset.fileName,
                        type = detail.asset.type.name,
                        albumName = detail.asset.album.name,
                    ),
                    filePath = detail.filePath,
                    downloadCompleted = detail.downloadCompleted,
                    sha1Verified = detail.sha1Verified,
                    exifFilled = detail.exifFilled,
                    fsTimeUpdated = detail.fsTimeUpdated,
                    message = detail.message,
                )
            },
        )
    }

    fun listSystem(accountFetcher: Fetcher<XiaomiAccount>): SystemListOutput {
        val timeZone = sql.executeQuery(SystemConfig::class) {
            where(table.id eq SystemConfigService.CONFIG_ID)
            select(table.assetsDateMapTimeZone)
        }.firstOrNull()

        val accounts = sql.executeQuery(XiaomiAccount::class) {
            select(table.fetch(accountFetcher))
        }.map { account ->
            McpAccountItem(nickname = account.nickname, userId = account.userId)
        }

        return SystemListOutput(
            hint = HINT_SYSTEM,
            accounts = accounts,
            info = McpSystemInfo(
                initialized = timeZone != null,
                assetsDateMapTimeZone = timeZone,
                appVersion = loadAppVersion(),
            ),
        )
    }

    private fun loadAppVersion(): String {
        return try {
            val props = java.util.Properties()
            XasQueryService::class.java.classLoader
                .getResourceAsStream("version.properties")
                ?.use { props.load(it) }
            props.getProperty("app.version", "dev")
        } catch (_: Exception) {
            "dev"
        }
    }

    companion object {
        private const val HINT_ALBUM =
            "相册列表为只读快照，shadow=true 表示远程已不存在的本地相册；定时任务运行情况可查询 domain=crontab"

        private const val HINT_CRONTAB_LIST =
            "使用 domain=crontab&action=get 配合 id=<任务id> 查看任务概况与实时统计；domain=crontab_history 查看运行历史"

        private const val HINT_CRONTAB_TRIGGER =
            "任务已异步触发；可用 domain=crontab&action=get 配合 id=<任务id> 查看实时统计，" +
                "或 domain=crontab_history 查看运行历史"

        private const val HINT_CRONTAB_TRIGGER_SKIPPED =
            "任务正在运行中，本次触发被跳过；可用 domain=crontab&action=get 配合 id=<任务id> 查看实时统计"

        private const val HINT_CRONTAB_GET =
            "使用 domain=crontab_history 配合 id=<任务id> 查看该任务的运行历史，" +
                "再以 domain=crontab_history_detail&id=<历史id> 查看某次运行的下载明细"

        private const val HINT_CRONTAB_HISTORY =
            "使用 domain=crontab_history_detail 配合 id=<历史id> 查看该次运行的下载明细"

        private const val HINT_CRONTAB_HISTORY_DETAIL = "通过 pageIndex/pageSize 翻页查看更多明细"

        private const val HINT_SYSTEM = "仅展示脱敏信息；密码/passToken/通知配置不通过 MCP 暴露"
    }
}
