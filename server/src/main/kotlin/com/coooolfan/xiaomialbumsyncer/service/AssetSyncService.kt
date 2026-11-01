package com.coooolfan.xiaomialbumsyncer.service

import com.coooolfan.xiaomialbumsyncer.icloud.ICloudAssetSyncService
import com.coooolfan.xiaomialbumsyncer.model.*
import com.coooolfan.xiaomialbumsyncer.xiaomicloud.XiaomiAssetSyncService
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.noear.solon.annotation.Managed
import org.slf4j.LoggerFactory

@Managed
class AssetSyncService(
    private val sql: KSqlClient,
    private val assets: AssetService,
    private val media: CloudMediaService,
    private val xiaomi: XiaomiAssetSyncService,
    private val icloud: ICloudAssetSyncService,
    private val histories: CrontabService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun refreshAssets(albumId: Long, fetcher: Fetcher<Asset>): List<Asset> {
        val album = sql.findById(Album::class, albumId)
            ?: throw IllegalArgumentException("Album $albumId not found, please refresh albums first")
        log.info("手动刷新资产开始 accountId={} albumId={}", album.accountId, albumId)
        val count = media.fetchAssets(album) { assets.saveAssets(it) }
        log.info("手动刷新资产完成 accountId={} albumId={} assets={}", album.accountId, albumId, count)
        return assets.getAssets(albumId, fetcher)
    }

    fun refresh(crontab: Crontab, history: CrontabHistory) {
        val provider = media.provider(crontab.accountId)
        val started = System.nanoTime()
        log.info("同步枚举开始 taskId={} historyId={} accountId={} provider={} mode={} albums={}",
            crontab.id, history.id, crontab.accountId, provider, crontab.syncMode, crontab.albumIds.size)
        provider.validateSyncMode(crontab.syncMode)
        try {
            when (provider) {
                CloudProvider.XIAOMI -> when (crontab.syncMode) {
                    CrontabSyncMode.FULL -> xiaomi.refreshFull(crontab, history)
                    CrontabSyncMode.CURSOR -> xiaomi.refreshByCursor(crontab, history, histories.getSyncCursorsHistory(history))
                    CrontabSyncMode.TIMELINE -> xiaomi.refreshByTimeline(crontab, history, histories.getAlbumTimelinesHistory(history))
                }
                CloudProvider.ICLOUD -> when (crontab.syncMode) {
                    CrontabSyncMode.FULL -> icloud.refreshFull(crontab)
                    CrontabSyncMode.CURSOR -> icloud.refreshByCursor(crontab, history, histories.getSyncCursorsHistory(history))
                    CrontabSyncMode.TIMELINE -> error("iCloud 不支持时间线同步")
                }
            }
            log.info("同步枚举完成 taskId={} historyId={} elapsedMs={}", crontab.id, history.id, (System.nanoTime() - started) / 1_000_000)
        } catch (e: Exception) {
            log.error("同步枚举失败 taskId={} historyId={} accountId={} provider={} mode={} errorType={} elapsedMs={}",
                crontab.id, history.id, crontab.accountId, provider, crontab.syncMode, e.javaClass.simpleName, (System.nanoTime() - started) / 1_000_000)
            throw e
        }
    }
}
