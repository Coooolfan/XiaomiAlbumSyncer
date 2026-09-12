package com.coooolfan.xiaomialbumsyncer.mcp

import com.coooolfan.xiaomialbumsyncer.exception.BadRequestException
import com.coooolfan.xiaomialbumsyncer.model.*
import com.coooolfan.xiaomialbumsyncer.service.CrontabService
import com.coooolfan.xiaomialbumsyncer.service.SystemConfigService
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.asc
import org.babyfish.jimmer.sql.kt.ast.expression.desc
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.noear.solon.annotation.Managed

/**
 * 为 MCP xas_query 工具提供查询和受权限控制的任务触发服务。
 *
 * 除复用 CrontabService 的实时统计外，其余查询均在本层用 Jimmer DSL 实现，
 * 不改动现有 HTTP API。
 */
@Managed
class XasQueryService(
    private val sql: KSqlClient,
    private val crontabService: CrontabService,
) {

    fun listAlbums(): AlbumListOutput {
        val albums = sql.executeQuery(Album::class) {
            select(table.fetch(ALBUM_FETCHER))
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

    fun listCrontabs(): CrontabListOutput {
        val crontabs = sql.executeQuery(Crontab::class) {
            select(table.fetch(CRONTAB_SUMMARY_FETCHER))
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

    fun getCrontab(crontabId: Long): CrontabGetOutput {
        val crontab = sql.findById(CRONTAB_OVERVIEW_FETCHER, crontabId)
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
    fun triggerCrontab(crontabId: Long): CrontabTriggerOutput {
        val crontab = sql.findById(CRONTAB_TRIGGER_FETCHER, crontabId)
            ?: throw BadRequestException("定时任务不存在: $crontabId")

        val triggered = crontabService.executeCrontab(crontabId)

        return CrontabTriggerOutput(
            hint = if (triggered) HINT_CRONTAB_TRIGGER else HINT_CRONTAB_TRIGGER_SKIPPED,
            triggered = triggered,
            crontab = TriggeredCrontab(id = crontab.id.toString(), name = crontab.name),
        )
    }

    fun listCrontabHistories(crontabId: Long?, pageIndex: Int, pageSize: Int): CrontabHistoryListOutput {
        val page = sql.createQuery(CrontabHistory::class) {
            crontabId?.let { where(table.crontabId eq it) }
            orderBy(table.startTime.desc(), table.id.desc())
            select(table.fetch(CRONTAB_HISTORY_LIST_FETCHER))
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

    fun listCrontabHistoryDetails(historyId: Long, pageIndex: Int, pageSize: Int): CrontabHistoryDetailListOutput {
        val history = sql.findById(CRONTAB_HISTORY_OVERVIEW_FETCHER, historyId)
            ?: throw BadRequestException("执行历史不存在: $historyId")

        val page = sql.createQuery(CrontabHistoryDetail::class) {
            where(table.crontabHistoryId eq historyId)
            orderBy(table.id.asc())
            select(table.fetch(CRONTAB_HISTORY_DETAIL_LIST_FETCHER))
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

    fun listSystem(): SystemListOutput {
        val timeZone = sql.executeQuery(SystemConfig::class) {
            where(table.id eq SystemConfigService.CONFIG_ID)
            select(table.assetsDateMapTimeZone)
        }.firstOrNull()

        val accounts = sql.executeQuery(XiaomiAccount::class) {
            select(table.fetchBy {
                nickname()
                userId()
            })
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
        private val ALBUM_FETCHER = newFetcher(Album::class).by {
            allScalarFields()
            account { nickname() }
        }

        private val CRONTAB_SUMMARY_FETCHER = newFetcher(Crontab::class).by {
            name()
            enabled()
            running()
            histories({
                filter { orderBy(table.startTime.desc()) }
                batch(1)
                limit(1)
            }) {
                startTime()
            }
        }

        private val CRONTAB_OVERVIEW_FETCHER = newFetcher(Crontab::class).by {
            name()
            description()
            enabled()
            running()
            albumIds()
            config()
            histories({
                filter { orderBy(table.startTime.desc()) }
                batch(1)
                limit(1)
            }) {
                startTime()
            }
        }

        private val CRONTAB_TRIGGER_FETCHER = newFetcher(Crontab::class).by {
            name()
        }

        private val CRONTAB_HISTORY_LIST_FETCHER = newFetcher(CrontabHistory::class).by {
            startTime()
            endTime()
            isCompleted()
            detailsCount()
            crontab {
                name()
            }
        }

        private val CRONTAB_HISTORY_OVERVIEW_FETCHER = newFetcher(CrontabHistory::class).by {
            startTime()
            endTime()
            isCompleted()
        }

        private val CRONTAB_HISTORY_DETAIL_LIST_FETCHER = newFetcher(CrontabHistoryDetail::class).by {
            filePath()
            downloadCompleted()
            sha1Verified()
            exifFilled()
            fsTimeUpdated()
            message()
            asset {
                fileName()
                type()
                album { name() }
            }
        }
    }
}
