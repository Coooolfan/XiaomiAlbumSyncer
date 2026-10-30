package com.coooolfan.xiaomialbumsyncer.icloud

import com.coooolfan.xiaomialbumsyncer.model.*
import com.coooolfan.xiaomialbumsyncer.service.AssetService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.*
import org.noear.solon.annotation.Managed

@Managed
class ICloudAssetSyncService(private val sql: KSqlClient, private val icloud: ICloudPhotos, private val assets: AssetService) {
    fun refreshFull(crontab: Crontab) {
        val albums = crontabAlbums(crontab)
        refreshAlbumCounts(albums)
        runBlocking(Dispatchers.IO) {
            val semaphore = Semaphore(5)
            albums.map { album ->
                async { semaphore.withPermit { icloud.fetchAssets(album) { assets.saveAssets(it) } } }
            }.awaitAll()
        }
    }

    fun refreshByCursor(crontab: Crontab, history: CrontabHistory, baseline: Map<String, String>) {
        val albums = crontabAlbums(crontab)
        refreshAlbumCounts(albums)
        val cursors = baseline.toMutableMap()
        for ((zone, selected) in albums.groupBy { ICloudAlbumKey.decode(it.remoteKey).zone }) {
            // 相册范围改变后建立新基线；账号之间、任务之间不共享同步进度。
            val key = "icloud:${crontab.accountId}:$zone:${selected.map { it.id }.sorted().joinToString(",")}"
            icloud.fetchIncremental(selected, baseline[key], { assets.saveAssets(it) }) { token ->
                cursors[key] = token
                sql.executeUpdate(CrontabHistory::class) {
                    set(table.syncCursors, cursors.toMap())
                    where(table.id eq history.id)
                }
            }
        }
    }

    private fun refreshAlbumCounts(albums: List<Album>) {
        for (album in icloud.fetchAlbumCounts(albums)) {
            sql.executeUpdate(Album::class) {
                set(table.assetCount, album.assetCount)
                where(table.id eq album.id)
            }
        }
    }

    private fun crontabAlbums(crontab: Crontab): List<Album> = sql.executeQuery(Album::class) {
        where(table.id valueIn crontab.albumIds)
        select(table)
    }

}
