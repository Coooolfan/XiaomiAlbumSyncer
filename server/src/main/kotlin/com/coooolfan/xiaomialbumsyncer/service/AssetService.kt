package com.coooolfan.xiaomialbumsyncer.service

import com.coooolfan.xiaomialbumsyncer.model.*
import com.coooolfan.xiaomialbumsyncer.utils.isAudioAlbum
import com.coooolfan.xiaomialbumsyncer.xiaomicloud.AlbumSyncInfo
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
import org.babyfish.jimmer.sql.kt.ast.expression.*
import org.noear.solon.annotation.Managed
import org.slf4j.LoggerFactory
import java.time.Instant
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
        val accountId = crontab.accountId

        // 获取相册列表（需要 remoteId）
        val albums = crontabAlbums(crontab)

        // 1. 获取这些相册最新的 timeline
        val albumTimelinesLatest = fetchAlbumsTimelineSnapshot(accountId, albums)
        val storageTimelineStartAt = Instant.now()
        sql.executeUpdate(CrontabHistory::class) {
            set(table.timelineSnapshot, albumTimelinesLatest)
            where(table.id eq crontabHistory.id)
        }
        log.info(
            "持久化 CrontabHistory ${crontabHistory.id} 的 timelineSnapshot，耗时 {} ms",
            Instant.now().toEpochMilli() - storageTimelineStartAt.toEpochMilli()
        )

        // 2. 对比 timeline，找出有变更的日期
        // remoteID -> AlbumTimeline
        val compareDiffStartAt = Instant.now()
        val albumsDayCountNeedRefresh = mutableMapOf<Long, Set<LocalDate>>()
        for ((albumRemoteId, timelineLatest) in albumTimelinesLatest) {
            val timelineHistory = albumTimelinesHistory[albumRemoteId] ?: EMPTY_ALBUM_TIMELINE
            albumsDayCountNeedRefresh[albumRemoteId] = (timelineLatest - timelineHistory).filter { it.value > 0 }.keys
            sql.executeUpdate(Album::class) {
                set(table.assetCount, timelineLatest.dayCount.values.sum())
                where(table.id eq albumRemoteId)
            }
        }
        log.info(
            "对比 CrontabHistory ${crontabHistory.id} 的 timelineSnapshot，耗时 {} ms",
            Instant.now().toEpochMilli() - compareDiffStartAt.toEpochMilli()
        )

        // 3. 只刷新这些日期的 Asset（并发执行）
        runBlocking(Dispatchers.IO) {
            val semaphore = Semaphore(10)
            albumsDayCountNeedRefresh.flatMap { (albumRemoteId, dayList) ->
                val album = sql.executeQuery(Album::class) {
                    where(table.remoteId eq albumRemoteId)
                    where(table.accountId eq accountId)
                    select(table)
                }.firstOrNull() ?: throw IllegalStateException("Cannot find album $albumRemoteId")

                dayList.map { day ->
                    async {
                        semaphore.withPermit {
                            log.info("开始刷新相册 {} 的 {} 日的 Asset", albumRemoteId, day)
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
        val accountId = crontab.accountId
        val albums = crontabAlbums(crontab)

        runBlocking(Dispatchers.IO) {
            val semaphore = Semaphore(5)
            albums.map { album ->
                async { semaphore.withPermit { refreshAlbumFull(album) } }
            }.awaitAll()
        }
        sql.executeUpdate(CrontabHistory::class) {
            set(table.timelineSnapshot, fetchAlbumsTimelineSnapshot(accountId, albums))
            where(table.id eq crontabHistory.id)
        }
    }

    /**
     * 位点同步模式：album/full 预检筛出变化的相册，allitems 按位点拉流。
     * 位点为空即全量回放；每页提交后把位点写回当前 CrontabHistory，崩溃后下次运行可续拉。
     */
    fun refreshAssetsBySyncTag(
        crontab: Crontab,
        crontabHistory: CrontabHistory,
        cursorBaseline: Map<Long, String>
    ) {
        val accountId = crontab.accountId

        // 录音不在相册位点体系内，维持全量路径
        val (audioAlbums, galleryAlbums) = crontabAlbums(crontab).partition { it.isAudioAlbum() }
        audioAlbums.forEach(::refreshAlbumFull)
        if (galleryAlbums.isEmpty()) return

        // 预检：一次请求取全部相册水位头
        val remoteInfos = api.fetchAlbumSyncSnapshot(accountId).associateBy { it.albumId }
        // 各相册并发拉取，位点写回经由该 Map 的锁串行化，保证落库的总是最新快照
        val cursors = cursorBaseline.toMutableMap()

        runBlocking(Dispatchers.IO) {
            val semaphore = Semaphore(5)
            galleryAlbums.map { album ->
                async {
                    semaphore.withPermit {
                        val remote = remoteInfos[album.remoteId]
                        if (remote == null) {
                            log.warn("相册 {} (remoteId={}) 未出现在 album/full 快照中，本次跳过", album.name, album.remoteId)
                        } else {
                            refreshAlbumBySyncTag(album, remote, cursorBaseline[album.remoteId]) { syncTag ->
                                synchronized(cursors) {
                                    cursors[album.remoteId] = syncTag
                                    sql.executeUpdate(CrontabHistory::class) {
                                        set(table.albumSyncCursors, cursors.toMap())
                                        where(table.id eq crontabHistory.id)
                                    }
                                }
                            }
                        }
                    }
                }
            }.awaitAll()
        }
    }

    private fun refreshAlbumFull(album: Album) {
        log.info("开始刷新相册 {} 的 Asset", album.id)
        val assetCount = api.fetchAssetsByAlbumId(album) { assets ->
            sql.saveEntitiesCommand(assets, SaveMode.UPSERT).execute()
        }
        sql.executeUpdate(Album::class) {
            set(table.assetCount, assetCount)
            where(table.id eq album.id)
        }
    }

    /**
     * 从 cursor 起按位点拉取单个相册，每页 UPSERT 后通过 commitCursor 提交续拉位点
     */
    private fun refreshAlbumBySyncTag(
        album: Album,
        remote: AlbumSyncInfo,
        cursor: String?,
        commitCursor: (String) -> Unit
    ) {
        if (album.assetCount != remote.totalImageCount) {
            sql.executeUpdate(Album::class) {
                set(table.assetCount, remote.totalImageCount)
                where(table.id eq album.id)
            }
        }
        if (cursor == remote.incrementalTag) return // 续拉位点已到达当前相册水位

        val accountId = album.accountId
        var syncTag = cursor ?: "0"
        // 存量位点被服务端拒绝（失效/参数错误）：首页失败回退 tag=0 全量重放，不中断运行
        var page = try {
            api.fetchAllItemsPage(accountId, album, syncTag)
        } catch (e: Exception) {
            if (syncTag == "0") throw e
            log.warn("相册 {} 位点 {} 拉取失败（{}），回退全量重放", album.name, syncTag, e.message)
            syncTag = "0"
            api.fetchAllItemsPage(accountId, album, syncTag)
        }
        while (true) {
            if (page.assets.isNotEmpty()) {
                sql.saveEntitiesCommand(page.assets, SaveMode.UPSERT).execute()
            }
            val stalled = page.syncTag == syncTag
            syncTag = page.syncTag
            // 每页提交续拉位点，崩溃后从已处理的最后一页继续
            commitCursor(syncTag)

            if (page.lastPage) break
            // 防御：syncTag 不推进时退出，避免服务端异常导致死循环
            if (stalled) {
                log.warn("相册 {} 的 syncTag 未推进（{}），终止本轮拉取", album.name, syncTag)
                break
            }
            page = api.fetchAllItemsPage(accountId, album, syncTag)
        }
        log.info("相册 {} (remoteId={}) 位点同步完成，当前位点 {}", album.name, album.remoteId, syncTag)
    }

    private fun crontabAlbums(crontab: Crontab): List<Album> = sql.executeQuery(Album::class) {
        where(table.id valueIn crontab.albumIds)
        select(table)
    }

    fun getAssets(albumId: Long, fetcher: Fetcher<Asset>): List<Asset> {
        return sql.executeQuery(Asset::class) {
            where(table.albumId eq albumId)
            select(table.fetch(fetcher))
        }
    }

    fun getAssetsUndownloadByCrontab(
        crontab: Crontab,
        pageSize: Int,
        lastId: Long
    ): List<Asset> {
        return sql.createQuery(Asset::class) {
            where(table.albumId valueIn crontab.albumIds)
            where(table.id gt lastId)
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
            if (!crontab.config.downloadAudios) where(table.type ne AssetType.AUDIO)
            orderBy(table.id.asc())
            select(table.fetchBy {
                allScalarFields()
                album { name() }
            })
        }.limit(pageSize).execute()
    }

    private fun fetchAlbumsTimelineSnapshot(accountId: Long, albums: List<Album>): Map<Long, AlbumTimeline> {
        val albumTimelines = mutableMapOf<Long, AlbumTimeline>()
        albums.filterNot { it.isAudioAlbum() }.forEach { album ->
            // remoteID -> AlbumTimeline
            albumTimelines[album.remoteId] = api.fetchAlbumTimeline(accountId, album.remoteId)
        }
        return albumTimelines
    }
}
