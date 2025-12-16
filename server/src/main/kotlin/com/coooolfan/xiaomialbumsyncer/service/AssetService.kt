package com.coooolfan.xiaomialbumsyncer.service

import com.coooolfan.xiaomialbumsyncer.model.*
import com.coooolfan.xiaomialbumsyncer.xiaomicloud.XiaoMiApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.kt.ast.expression.ne
import org.babyfish.jimmer.sql.kt.ast.expression.notExists
import org.babyfish.jimmer.sql.kt.ast.expression.valueIn
import org.noear.solon.annotation.Managed
import org.slf4j.LoggerFactory
import java.time.LocalDate

@Managed
class AssetService(private val sql: KSqlClient, private val api: XiaoMiApi) {

    private val log = LoggerFactory.getLogger(AssetService::class.java)

    fun refreshAssets(albumId: Long, fetcher: Fetcher<Asset>): List<Asset> {
        val album = sql.executeQuery(Album::class) {
            where(table.id eq albumId)
            select(table)
        }.firstOrNull() ?: throw IllegalArgumentException("Album $albumId not found, please refresh albums first")

        val fetchAssetList = api.fetchAllAssetsByAlbumId(album)
        sql.saveEntitiesCommand(fetchAssetList, SaveMode.UPSERT).execute()

        // 此处的 fetchAssetList 形状已保证与 fetcher 一致
        return fetchAssetList
    }

    fun refreshAssetsByDiffTimeline(
        crontab: Crontab,
        crontabHistory: CrontabHistory,
        albumTimelinesHistory: Map<Long, AlbumTimeline>
    ) {
        // 1. 获取这些相册最新的 timeline
        val albumTimelinesLatest = fetchAlbumsTimelineSnapshot(crontab.albumIds)
        sql.executeUpdate(CrontabHistory::class) {
            set(table.timelineSnapshot, albumTimelinesLatest)
            where(table.id eq crontabHistory.id)
        }

        // 2. 对比 timeline，找出有变更的日期
        val albumsDayCountNeedRefresh = mutableMapOf<Long, Set<LocalDate>>()
        for ((albumId, timelineLatest) in albumTimelinesLatest) {
            val timelineHistory = albumTimelinesHistory[albumId] ?: EMPTY_ALBUM_TIMELINE
            albumsDayCountNeedRefresh[albumId] = (timelineLatest - timelineHistory).filter { it.value > 0 }.keys
            sql.executeUpdate(Album::class) {
                set(table.assetCount, timelineLatest.dayCount.values.sum())
                where(table.id eq albumId)
            }
        }

        // 3. 只刷新这些日期的 Asset（并发执行）
        runBlocking(Dispatchers.IO) {
            val semaphore = Semaphore(10)
            albumsDayCountNeedRefresh.flatMap { (albumId, dayList) ->
                val album =
                    sql.findById(Album::class, albumId) ?: throw IllegalStateException("Cannot find album $albumId")

                dayList.map { day ->
                    async {
                        semaphore.withPermit {
                            log.info("开始刷新相册 {} 的 {} 日的 Asset", albumId, day)
                            api.fetchAssetsByAlbumId(album, day) { assets ->
                                sql.saveEntitiesCommand(assets, SaveMode.UPSERT).execute()
                            }
                        }
                    }
                }
            }.awaitAll()
        }
    }

    fun refreshAssetsFull(crontab: Crontab, crontabHistory: CrontabHistory) {
        val albums = sql.executeQuery(Album::class) {
            where(table.id valueIn crontab.albumIds)
            select(table)
        }

        runBlocking(Dispatchers.IO) {
            val semaphore = Semaphore(5)
            albums.map { album ->
                async {
                    semaphore.withPermit {
                        log.info("开始刷新相册 {} 的 Asset", album.id)
                        val assetCount = api.fetchAssetsByAlbumId(album) { assets ->
                            sql.saveEntitiesCommand(assets, SaveMode.UPSERT).execute()
                        }
                        sql.executeUpdate(Album::class) {
                            set(table.assetCount, assetCount)
                            where(table.id eq album.id)
                        }
                    }

                }
            }.awaitAll()
        }
        sql.executeUpdate(CrontabHistory::class) {
            set(table.timelineSnapshot, fetchAlbumsTimelineSnapshot(crontab.albumIds))
            where(table.id eq crontabHistory.id)
        }
    }

    fun getAssets(albumId: Long, fetcher: Fetcher<Asset>): List<Asset> {
        return sql.executeQuery(Asset::class) {
            where(table.albumId eq albumId)
            select(table.fetch(fetcher))
        }
    }

    fun getAssetsUndownloadByCrontab(crontab: Crontab, pageIndex: Int, pageSize: Int): List<Asset> {
        return sql.createQuery(Asset::class) {
            where(table.albumId valueIn crontab.albumIds)
            where(
                notExists(
                    subQuery(CrontabHistoryDetail::class) {
                        where(table.crontabHistory.crontabId eq crontab.id)
                        where(table.assetId eq parentTable.id)
                        where(table.downloadCompleted eq true)
                        where(table.sha1Verified eq true)
                        where(table.exifFilled eq true)
                        where(table.fsTimeUpdated eq true)
                        select(table)
                    })
            )
            if (!crontab.config.downloadImages) where(table.type ne AssetType.IMAGE)
            if (!crontab.config.downloadVideos) where(table.type ne AssetType.VIDEO)
            select(table.fetchBy {
                allScalarFields()
                album { name() }
            })
        }.fetchPage(pageIndex, pageSize).rows
    }

    private fun fetchAlbumsTimelineSnapshot(albumIds: List<Long>): Map<Long, AlbumTimeline> {
        val albumTimelines = mutableMapOf<Long, AlbumTimeline>()
        albumIds.forEach { albumId ->
            albumTimelines[albumId] = api.fetchAlbumTimeline(albumId)
        }
        return albumTimelines
    }
}