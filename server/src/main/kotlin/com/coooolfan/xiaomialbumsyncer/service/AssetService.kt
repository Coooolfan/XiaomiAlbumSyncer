package com.coooolfan.xiaomialbumsyncer.service

import com.coooolfan.xiaomialbumsyncer.model.Album
import com.coooolfan.xiaomialbumsyncer.model.AlbumTimeline
import com.coooolfan.xiaomialbumsyncer.model.Asset
import com.coooolfan.xiaomialbumsyncer.model.AssetType
import com.coooolfan.xiaomialbumsyncer.model.Crontab
import com.coooolfan.xiaomialbumsyncer.model.CrontabHistory
import com.coooolfan.xiaomialbumsyncer.model.CrontabHistoryDetail
import com.coooolfan.xiaomialbumsyncer.model.EMPTY_ALBUM_TIMELINE
import com.coooolfan.xiaomialbumsyncer.model.albumId
import com.coooolfan.xiaomialbumsyncer.model.assetCount
import com.coooolfan.xiaomialbumsyncer.model.assetId
import com.coooolfan.xiaomialbumsyncer.model.crontabHistory
import com.coooolfan.xiaomialbumsyncer.model.crontabId
import com.coooolfan.xiaomialbumsyncer.model.fetchBy
import com.coooolfan.xiaomialbumsyncer.model.id
import com.coooolfan.xiaomialbumsyncer.model.minus
import com.coooolfan.xiaomialbumsyncer.model.timelineSnapshot
import com.coooolfan.xiaomialbumsyncer.model.type
import com.coooolfan.xiaomialbumsyncer.xiaomicloud.XiaoMiApi
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.kt.ast.expression.ne
import org.babyfish.jimmer.sql.kt.ast.expression.notExists
import org.babyfish.jimmer.sql.kt.ast.expression.valueIn
import org.noear.solon.annotation.Managed
import java.time.LocalDate
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.collections.iterator

@Managed
class AssetService(private val sql: KSqlClient, private val api: XiaoMiApi) {
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

        // 3. 只刷新这些日期的 Asset
        albumsDayCountNeedRefresh.forEach { (albumId, dayList) ->
            val album =
                sql.findById(Album::class, albumId) ?: throw IllegalStateException("Cannot find album $albumId")

            dayList.forEach { day ->
//                log.info("相册 $albumId 在 $day 有新增，开始刷新此日期的资源")
                val assets = api.fetchAllAssetsByAlbumId(album, day)
                sql.saveEntitiesCommand(assets, SaveMode.UPSERT).execute()
            }
        }
    }

    fun refreshAssetsFull(crontab: Crontab, crontabHistory: CrontabHistory) {
        val albums = sql.executeQuery(Album::class) {
            where(table.id valueIn crontab.albumIds)
            select(table)
        }

        albums.forEach {
            val assets = api.fetchAllAssetsByAlbumId(it)
            sql.saveEntitiesCommand(assets, SaveMode.UPSERT).execute()
            sql.executeUpdate(Album::class) {
                set(table.assetCount, assets.size.toLong())
                where(table.id eq it.id)
            }
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