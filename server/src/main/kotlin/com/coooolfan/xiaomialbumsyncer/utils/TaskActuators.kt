package com.coooolfan.xiaomialbumsyncer.utils

import com.coooolfan.xiaomialbumsyncer.model.*
import com.coooolfan.xiaomialbumsyncer.xiaomicloud.XiaoMiApi
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.desc
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.kt.ast.expression.ne
import org.babyfish.jimmer.sql.kt.ast.expression.notExists
import org.babyfish.jimmer.sql.kt.ast.expression.valueIn
import org.noear.solon.annotation.Managed
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone
import kotlin.io.path.Path

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
        refreshAssets(crontab, crontabHistory, crontab.config.diffByTimeline)

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

        val assetPathMap = mutableMapOf<Asset, java.nio.file.Path>()

        // 4. 对新增的 Asset 进行下载
        // 5. 更新 CrontabHistory 记录的状态
        needDownloadAssets.forEach {
            try {
                val targetPath = Path(crontab.config.targetPath, it.album.name, it.fileName)
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
        if (crontab.config.rewriteExifTime) assetPathMap.forEach {
            val rewriteZone = TimeZone.getTimeZone(ZoneId.of(crontab.config.rewriteExifTimeZone))

            try {
                rewriteExifTime(
                    it.key, it.value, ExifRewriteConfig(
                        Path(systemConfig.exifToolPath), rewriteZone
                    )
                )
            } catch (e: Exception) {
                log.error("修改文件 EXIF 时间失败，跳过此文件，Asset ID: ${it.key.id}")
                e.printStackTrace()
            }
        }

        // 6. 写入 CrontabHistoryDetails 记录
        sql.executeUpdate(CrontabHistory::class) {
            set(table.endTime, Instant.now())
            where(table.id eq crontabHistory.id)
        }
        log.info("定时任务执行完毕: [${crontab.id}:${crontab.name}]，共下载 ${needDownloadAssets.size} 个文件")

    }

    fun refreshAssets(crontab: Crontab, crontabHistory: CrontabHistory, diffByTimeline: Boolean = false) {
        if (diffByTimeline) {
            // 1. 取到最新的一次 CrontabHistory 的 timelineSnapshot

            val albumTimelinesHistory = sql.executeQuery(CrontabHistory::class) {
                orderBy(table.startTime.desc())
                where(table.crontabId eq crontab.id)
                select(table.timelineSnapshot)
            }.firstOrNull() ?: throw IllegalStateException("Cannot find last crontab history for crontab ${crontab.id}")

            // 2. 获取这些相册最新的 timeline

            val albumTimelinesLastest = mutableMapOf<Long, AlbumTimeline>()
            crontab.albumIds.forEach { albumId ->
                albumTimelinesLastest[albumId] = api.fetchAlbumTimeline(albumId)
            }
            sql.executeUpdate(CrontabHistory::class) {
                set(table.timelineSnapshot, albumTimelinesLastest)
                where(table.id eq crontabHistory.id)
            }

            // 3. 对比 timeline，找出有变更的日期

            val albumsDayCountNeedRefresh = mutableMapOf<Long, List<LocalDate>>()
            for ((albumId, timelineLastest) in albumTimelinesLastest) {
                val timelineHistory = albumTimelinesHistory[albumId] ?: EMPTY_ALBUM_TIMELINE
                albumsDayCountNeedRefresh[albumId] = (timelineLastest - timelineHistory).keys.toList()
            }

            // 4. 只刷新这些日期的 Asset

            albumsDayCountNeedRefresh.forEach { (albumId, dayList) ->
                val album =
                    sql.findById(Album::class, albumId) ?: throw IllegalStateException("Cannot find album $albumId")

                dayList.forEach { day ->
                    log.info("相册 $albumId 在 $day 有更新，开始刷新此日期的资源")
                    val assets = api.fetchAllAssetsByAlbumId(album, day)
                    sql.saveEntitiesCommand(assets, SaveMode.UPSERT).execute()
                }
            }


        } else {
            val albums = sql.executeQuery(Album::class) {
                where(table.id valueIn crontab.albumIds)
                select(table)
            }

            albums.forEach {
                val assets = api.fetchAllAssetsByAlbumId(it)
                sql.saveEntitiesCommand(assets, SaveMode.UPSERT).execute()
            }
        }
    }
}