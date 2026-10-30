package com.coooolfan.xiaomialbumsyncer.service

import com.coooolfan.xiaomialbumsyncer.icloud.ICloudAssetSyncService
import com.coooolfan.xiaomialbumsyncer.model.*
import com.coooolfan.xiaomialbumsyncer.xiaomicloud.XiaomiAssetSyncService
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.noear.solon.annotation.Managed

@Managed
class AssetSyncService(
    private val sql: KSqlClient,
    private val assets: AssetService,
    private val media: CloudMediaService,
    private val xiaomi: XiaomiAssetSyncService,
    private val icloud: ICloudAssetSyncService,
    private val histories: CrontabService,
) {
    fun refreshAssets(albumId: Long, fetcher: Fetcher<Asset>): List<Asset> {
        val album = sql.findById(Album::class, albumId)
            ?: throw IllegalArgumentException("Album $albumId not found, please refresh albums first")
        media.fetchAssets(album) { assets.saveAssets(it) }
        return assets.getAssets(albumId, fetcher)
    }

    fun refresh(crontab: Crontab, history: CrontabHistory) {
        val provider = media.provider(crontab.accountId)
        provider.validateSyncMode(crontab.syncMode)
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
    }
}
