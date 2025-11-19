package com.coooolfan.xiaomialbumsyncer.utils

import com.coooolfan.xiaomialbumsyncer.model.*
import com.coooolfan.xiaomialbumsyncer.xiaomicloud.XiaoMiApi
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.*
import org.noear.solon.annotation.Managed
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.exists

@Managed
class TaskActuators(private val sql: KSqlClient, private val api: XiaoMiApi) {

    private val log = LoggerFactory.getLogger(TaskActuators::class.java)

    fun doWork(crontab: Crontab) {

        log.info("执行定时任务: ${crontab.id}:${crontab.name}")

        val systemConfig =
            sql.findById(SystemConfig::class, 0) ?: throw IllegalStateException("System is not initialized")

        // 1. 创建 CrontabHistory 记录的状态
        val crontabHistory = sql.saveCommand(CrontabHistory {
            crontabId = crontab.id
            startTime = Instant.now()
        }, SaveMode.INSERT_ONLY).execute().modifiedEntity


        // 2. 对 crontab.albums 进行同步操作, 重新刷新这些相册的所有 Asset

        // 2.1 取到上次的 CrontabHistory 的 timelineSnapshot
        val albumTimelinesHistory = sql.executeQuery(CrontabHistory::class) {
            orderBy(table.startTime.desc())
            where(table.crontabId eq crontab.id)
            where(table.id ne crontabHistory.id)
            where(table.endTime ne null)
            select(table.timelineSnapshot)
        }.firstOrNull() ?: emptyMap()

        // 仅在上次有记录且相册列表未变更的情况下，才使用时间线对比模式
        // 理论上相册列表变动不影响逻辑正确，但是会导致实际发起的查询大于请求完整刷新模式，所以还是要求相册列表一致
        if (crontab.config.diffByTimeline && albumTimelinesHistory.isNotEmpty() && albumTimelinesHistory.keys == crontab.albumIds.toSet()) {
            log.info("时间线对比模式可用，仅对有变更的日期进行刷新")
            refreshAssetsByDiffTimeline(crontab, crontabHistory, albumTimelinesHistory)
        } else {
            if (crontab.config.diffByTimeline)
                log.warn("时间线对比模式不可用，原因：${if (albumTimelinesHistory.isEmpty()) "该任务的最新执行记录无可用于对比的时间线数据" else "相册列表有变更"}，将使用完整刷新模式")
            refreshAssetsFull(crontab, crontabHistory)
        }

        // 3. 与 CrontabHistoryDetails 对比，过滤出新增的 Asset
        val needDownloadAssets = sql.executeQuery(Asset::class) {
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
        }

        log.info("共发现 ${needDownloadAssets.size} 个需要下载的文件")

        val assetPathMap = mutableMapOf<Asset, Path>()

        // 4. 对新增的 Asset 进行下载
        // 5. 更新 CrontabHistory 记录的状态
        val step = maxOf(needDownloadAssets.size / 10, 1)
        for ((i, it) in needDownloadAssets.withIndex()) {
            if ((i + 1) % step == 0)
                log.info("正在尝试处理第 ${i + 1} 个文件，总进度：${(i + 1).percentOf(needDownloadAssets.size)}")

            try {
                val targetPath = Path(crontab.config.targetPath, it.album.name, it.fileName)

                if (crontab.config.skipExistingFile && targetPath.exists()) continue

                val path = api.downloadAsset(it, targetPath)
                sql.saveCommand(CrontabHistoryDetail {
                    crontabHistoryId = crontabHistory.id
                    downloadTime = Instant.now()
                    filePath = path.toString()
                    assetId = it.id
                }, SaveMode.INSERT_ONLY).execute()
                assetPathMap[it] = path
            } catch (e: Exception) {
                log.error("下载文件失败，跳过此文件，Asset ID: ${it.id}")
                e.printStackTrace()
            }
        }

        // 5.1 可选：批量修改图片 EXIF 时间
        if (crontab.config.rewriteExifTime) {
            var timeZone: TimeZone? = null
            try {
                timeZone = TimeZone.getTimeZone(ZoneId.of(crontab.config.rewriteExifTimeZone))
            } catch (e: Exception) {
                log.error("解析时区失败，填充 EXIF 时间操作将被跳过，时区字符串：${crontab.config.rewriteExifTimeZone}")
                e.printStackTrace()
            }

            if (timeZone == null)
                log.warn("未指定有效的时区，填充 EXIF 时间操作将被跳过")
            else
                fillExifTime(assetPathMap, systemConfig, timeZone)

        }

        // 5.2 可选：修改文件系统时间
        if (crontab.config.rewriteFileSystemTime) {
            log.info("开始修改下载文件的文件系统时间，共 ${assetPathMap.size} 个文件需要处理")
            rewriteFileSystemTime(assetPathMap)
        }

        // 6. 写入 CrontabHistoryDetails 记录
        sql.executeUpdate(CrontabHistory::class) {
            set(table.endTime, Instant.now())
            where(table.id eq crontabHistory.id)
        }
        log.info("定时任务执行完毕: [${crontab.id}:${crontab.name}]，共下载 ${needDownloadAssets.size} 个文件")

    }

    fun fillExifTime(
        assetPathMap: Map<Asset, Path>,
        systemConfig: SystemConfig,
        timeZone: TimeZone
    ) {

        log.info("开始填充下载文件的 EXIF 时间，共 ${assetPathMap.size} 个文件需要处理")

        var i = 0
        val step = maxOf(assetPathMap.size / 10, 1)
        for (it in assetPathMap) {
            if ((i + 1) % step == 0)
                log.info("正在尝试填充第 ${i + 1} 个文件的 Exif 数据，总进度：${(i + 1).percentOf(assetPathMap.size)}")

            try {
                rewriteExifTime(
                    it.key, it.value,
                    ExifRewriteConfig(Path(systemConfig.exifToolPath), timeZone)
                )
            } catch (e: Exception) {
                log.error("修改文件 EXIF 时间失败，跳过此文件，Asset ID: ${it.key.id}")
                e.printStackTrace()
            }
            i++
        }
    }

    fun rewriteFileSystemTime(assetPathMap: Map<Asset, Path>) {
        log.info("开始重写下载文件的文件系统时间，共 ${assetPathMap.size} 个文件需要处理")

        var i = 0
        val step = maxOf(assetPathMap.size / 10, 1)
        for (it in assetPathMap) {
            if ((i + 1) % step == 0)
                log.info("正在尝试重写第 ${i + 1} 个文件的文件系统时间，总进度：${(i + 1).percentOf(assetPathMap.size)}")

            try {
                rewriteFSTime(
                    it.value,
                    it.key.dateTaken
                )
            } catch (e: Exception) {
                log.error("修改文件系统时间未能正确返回，此操作的结果不可知，Asset ID: ${it.key.id}，Asset Path: ${it.value}")
                e.printStackTrace()
            }
            i++
        }
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
                log.info("相册 $albumId 在 $day 有新增，开始刷新此日期的资源")
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

    fun fetchAlbumsTimelineSnapshot(albumIds: List<Long>): Map<Long, AlbumTimeline> {
        val albumTimelines = mutableMapOf<Long, AlbumTimeline>()
        albumIds.forEach { albumId ->
            albumTimelines[albumId] = api.fetchAlbumTimeline(albumId)
        }
        return albumTimelines
    }
}