package com.coooolfan.xiaomialbumsyncer.xiaomicloud

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
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDate

@Managed
class XiaomiAssetSyncService(private val sql: KSqlClient, private val api: XiaomiCloudApi, private val assets: AssetService) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun refreshByTimeline(crontab: Crontab, history: CrontabHistory, baseline: Map<Long, AlbumTimeline>) {
        val reason = checkTimelineDiffUsable(crontab, baseline)
        if (reason != null) {
            log.warn("时间线对比模式不可用，原因：{}，将使用完整刷新模式", reason)
            refreshFull(crontab, history, captureTimelineSnapshot = true)
        } else {
            log.info("时间线对比模式可用，仅对有变更的日期进行刷新")
            refreshByDiffTimeline(crontab, history, baseline)
        }
    }

    fun refreshFull(
        crontab: Crontab,
        crontabHistory: CrontabHistory,
        captureTimelineSnapshot: Boolean = false,
    ) {
        val albums = crontabAlbums(crontab)

        runBlocking(Dispatchers.IO) {
            val semaphore = Semaphore(5)
            albums.map { album ->
                async { semaphore.withPermit { refreshAlbumFull(album) } }
            }.awaitAll()
        }
        if (captureTimelineSnapshot) {
            sql.executeUpdate(CrontabHistory::class) {
                set(table.timelineSnapshot, fetchAlbumsTimelineSnapshot(crontab.accountId, albums))
                where(table.id eq crontabHistory.id)
            }
        }
    }

    /**
     * 位点同步模式：album/full 预检筛出变化的相册，allitems 按位点拉流。
     * 位点为空即全量回放；每页提交后把位点写回当前 CrontabHistory，崩溃后下次运行可续拉。
     */
    fun refreshByCursor(
        crontab: Crontab,
        crontabHistory: CrontabHistory,
        cursorBaseline: Map<String, String>
    ) {
        val accountId = crontab.accountId

        // 录音不在相册位点体系内，维持全量路径
        val (audioAlbums, galleryAlbums) = crontabAlbums(crontab).partition { it.isXiaomiRecordingAlbum() }
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
                        val remote = remoteInfos[album.remoteKey.toLong()]
                        if (remote == null) {
                            log.warn("相册 {} (remoteKey={}) 未出现在 album/full 快照中，本次跳过", album.name, album.remoteKey)
                        } else {
                            refreshAlbumBySyncTag(album, remote, cursorBaseline[album.remoteKey]) { syncTag ->
                                synchronized(cursors) {
                                    cursors[album.remoteKey] = syncTag
                                    sql.executeUpdate(CrontabHistory::class) {
                                        set(table.syncCursors, cursors.toMap())
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

    private fun refreshByDiffTimeline(
        crontab: Crontab,
        crontabHistory: CrontabHistory,
        albumTimelinesHistory: Map<Long, AlbumTimeline>
    ) {
        val accountId = crontab.accountId

        // 获取相册列表（需要 remoteKey）
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
                where(table.remoteKey eq albumRemoteId.toString())
                where(table.accountId eq accountId)
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
                    where(table.remoteKey eq albumRemoteId.toString())
                    where(table.accountId eq accountId)
                    select(table)
                }.firstOrNull() ?: throw IllegalStateException("Cannot find album $albumRemoteId")

                dayList.map { day ->
                    async {
                        semaphore.withPermit {
                            log.info("开始刷新相册 {} 的 {} 日的 Asset", albumRemoteId, day)
                            api.fetchAssetsByAlbumId(album, day) { pageAssets ->
                                assets.saveAssets(pageAssets)
                            }
                        }
                    }
                }
            }.awaitAll()
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
                assets.saveAssets(page.assets)
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
        log.info("相册 {} (remoteKey={}) 位点同步完成，当前位点 {}", album.name, album.remoteKey, syncTag)
    }

    private fun crontabAlbums(crontab: Crontab): List<Album> = sql.executeQuery(Album::class) {
        where(table.id valueIn crontab.albumIds)
        select(table)
    }

    private fun refreshAlbumFull(album: Album) {
        log.info("开始刷新相册 {} 的 Asset", album.id)
        val count = api.fetchAssetsByAlbumId(album) { assets.saveAssets(it) }
        sql.executeUpdate(Album::class) {
            set(table.assetCount, count)
            where(table.id eq album.id)
        }
    }

    private fun fetchAlbumsTimelineSnapshot(accountId: Long, albums: List<Album>): Map<Long, AlbumTimeline> {
        val albumTimelines = mutableMapOf<Long, AlbumTimeline>()
        albums.filterNot { it.isXiaomiRecordingAlbum() }.forEach { album ->
            // remoteID -> AlbumTimeline
            albumTimelines[album.remoteKey.toLong()] = api.fetchAlbumTimeline(accountId, album.remoteKey.toLong())
        }
        return albumTimelines
    }

    private fun checkTimelineDiffUsable(crontab: Crontab, albumTimelinesHistory: Map<Long, AlbumTimeline>): String? {
        if (albumTimelinesHistory.isEmpty()) {
            return "该任务的最新执行记录无可用于对比的时间线数据"
        }

        val crontabAlbumRemoteIds = crontab.albums.mapTo(mutableSetOf()) { it.remoteKey.toLong() }
        if (crontabAlbumRemoteIds.contains(-1L)) {
            return "\"录音\"不支持时间线对比"
        }
        if (albumTimelinesHistory.keys != crontabAlbumRemoteIds) {
            return "相册列表有变更"
        }
        return null
    }
}
