package com.coooolfan.xiaomialbumsyncer.service

import com.coooolfan.xiaomialbumsyncer.config.TaskScheduler
import com.coooolfan.xiaomialbumsyncer.controller.CrontabController.Companion.CRONTAB_WITH_ALBUMS_FETCHER
import com.coooolfan.xiaomialbumsyncer.controller.CrontabCurrentStats
import com.coooolfan.xiaomialbumsyncer.model.*
import com.coooolfan.xiaomialbumsyncer.model.dto.CrontabCreateInput
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.*
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.noear.solon.annotation.Inject
import org.noear.solon.annotation.Managed
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.util.*
import kotlin.io.path.Path

@Managed
class CrontabService(private val sql: KSqlClient) {

    @Inject
    private lateinit var taskScheduler: TaskScheduler

    private val log = LoggerFactory.getLogger(this.javaClass)

    fun createCrontab(input: CrontabCreateInput, fetcher: Fetcher<Crontab>): Crontab {
        val execute = sql.saveCommand(input, SaveMode.INSERT_ONLY).execute(fetcher)
        taskScheduler.initJobs()
        return execute.modifiedEntity
    }

    fun queryCrontab(fetcher: Fetcher<Crontab>): List<Crontab> {
        return sql.executeQuery(Crontab::class) {
            select(table.fetch(fetcher))
        }
    }

    fun deleteCrontab(id: Long) {
        sql.executeDelete(Crontab::class) {
            where(table.id eq id)
        }
        taskScheduler.initJobs()
    }

    fun getCrontabCurrentStats(crontabId: Long): CrontabCurrentStats {
        if (!taskScheduler.checkIsRunning(crontabId))
            return CrontabCurrentStats()

        val runningCrontabHistory = sql.createQuery(CrontabHistory::class) {
            where(table.crontabId eq crontabId)
            orderBy(table.startTime.desc())
            select(table)
        }.limit(1).execute().firstOrNull() ?: return CrontabCurrentStats()

        if (!runningCrontabHistory.fetchedAllAssets) {
            return CrontabCurrentStats(Instant.now())
        }

        val assetCount = sql.createQuery(CrontabHistoryDetail::class) {
            where(table.crontabHistoryId eq runningCrontabHistory.id)
            select(count(table))
        }.execute().firstOrNull()

        val downloadCompletedCount = sql.createQuery(CrontabHistoryDetail::class) {
            where(table.crontabHistoryId eq runningCrontabHistory.id)
            where(table.downloadCompleted eq true)
            select(count(table))
        }.execute().firstOrNull()

        val sha1VerifiedCount = sql.createQuery(CrontabHistoryDetail::class) {
            where(table.crontabHistoryId eq runningCrontabHistory.id)
            where(table.downloadCompleted eq true)
            where(table.sha1Verified eq true)
            select(count(table))
        }.execute().firstOrNull()

        val exifFilledCount = sql.createQuery(CrontabHistoryDetail::class) {
            where(table.crontabHistoryId eq runningCrontabHistory.id)
            where(table.exifFilled eq true)
            where(table.downloadCompleted eq true)
            where(table.sha1Verified eq true)
            select(count(table))
        }.execute().firstOrNull()

        val fsTimeUpdatedCount = sql.createQuery(CrontabHistoryDetail::class) {
            where(table.crontabHistoryId eq runningCrontabHistory.id)
            where(table.fsTimeUpdated eq true)
            where(table.exifFilled eq true)
            where(table.downloadCompleted eq true)
            where(table.sha1Verified eq true)
            select(count(table))
        }.execute().firstOrNull()

        return CrontabCurrentStats(
            Instant.now(),
            assetCount,
            downloadCompletedCount,
            sha1VerifiedCount,
            exifFilledCount,
            fsTimeUpdatedCount,
        )
    }

    fun updateCrontab(crontab: Crontab, fetcher: Fetcher<Crontab>): Crontab {
        val execute = sql.saveCommand(crontab, SaveMode.UPDATE_ONLY).execute(fetcher)
        taskScheduler.initJobs()
        return execute.modifiedEntity
    }

    fun executeCrontab(crontabId: Long) {
        val crontab =
            sql.findById(
                CRONTAB_WITH_ALBUMS_FETCHER,
                crontabId
            ) ?: throw IllegalArgumentException("定时任务不存在: $crontabId")

        taskScheduler.executeCrontab(crontab, true)
    }

    fun createCrontabHistory(crontab: Crontab): CrontabHistory {
        val crontabHistoryId = sql.saveCommand(CrontabHistory {
            crontabId = crontab.id
            startTime = Instant.now()
        }, SaveMode.INSERT_ONLY).execute().modifiedEntity.id

        return sql.findOneById(
            newFetcher(CrontabHistory::class).by {
                allScalarFields()
                crontab {
                    allScalarFields()
                    albumIds()
                }
            },
            crontabHistoryId,
        )
    }

    fun getAlbumTimelinesHistory(history: CrontabHistory): Map<Long, AlbumTimeline> {
        return sql.executeQuery(CrontabHistory::class) {
            orderBy(table.startTime.desc())
            where(table.crontabId eq history.crontab.id)
            where(table.id ne history.id)
            where(table.endTime ne null)
            select(table.timelineSnapshot)
        }.firstOrNull() ?: emptyMap()
    }

    fun insertCrontabHistoryDetails(details: List<CrontabHistoryDetail>): List<CrontabHistoryDetail> {
        val details2Save = details.map { origin ->
            CrontabHistoryDetail(origin) {
                crontabHistory = CrontabHistory { id = origin.crontabHistory.id }
                asset = Asset { id = origin.asset.id }
            }
        }.toCollection(mutableListOf())

        val saveResult = sql.saveEntitiesCommand(details2Save, SaveMode.INSERT_ONLY).execute()

        val saveIds = saveResult.items.map { it.modifiedEntity.id }.toCollection(mutableListOf())

        return sql.executeQuery(CrontabHistoryDetail::class) {
            where(table.id valueIn saveIds)
            select(table.fetchBy {
                allScalarFields()
                crontabHistory {
                    allScalarFields()
                    crontab {
                        allScalarFields()
                        accountId()
                    }
                }
                asset { allScalarFields() }
            })
        }
    }

    fun finishCrontabHistoryFetchedAllAssets(crontabHistory: CrontabHistory) {
        sql.executeUpdate(CrontabHistory::class) {
            set(table.fetchedAllAssets, true)
            where(table.id eq crontabHistory.id)
        }
    }

    fun finishCrontabHistory(crontabHistory: CrontabHistory) {
        sql.executeUpdate(CrontabHistory::class) {
            set(table.endTime, Instant.now())
            where(table.id eq crontabHistory.id)
        }
    }

    fun executeCrontabExifTime(crontabId: Long) {
        val systemConfig = sql.findById(SystemConfig::class, 0)
            ?: throw IllegalStateException("System is not initialized")

        val crontab =
            sql.findById(
                CRONTAB_WITH_ALBUMS_FETCHER,
                crontabId
            ) ?: throw IllegalArgumentException("定时任务不存在: $crontabId")

        if (!crontab.config.rewriteExifTime) {
            throw IllegalArgumentException("定时任务未启用重写Exif时间选项: $crontabId")
        }

        var timeZone: TimeZone?
        try {
            timeZone = TimeZone.getTimeZone(ZoneId.of(crontab.config.rewriteExifTimeZone))
        } catch (e: Exception) {
            log.error("解析时区失败，填充 EXIF 时间操作将被取消，时区字符串：${crontab.config.rewriteExifTimeZone}")
            throw IllegalArgumentException(
                "解析时区失败，填充 EXIF 时间操作将被取消，时区字符串：${crontab.config.rewriteExifTimeZone}",
                e
            )
        }

        if (timeZone == null) {
            log.warn("未指定有效的时区，填充 EXIF 时间操作将被取消")
            throw IllegalArgumentException("未指定有效的时区，填充 EXIF 时间操作将被取消")
        }

        val assetPathMap = fetchAssetPathMapForExifTime(crontabId)

        taskScheduler.executeCrontabExifTime(true, assetPathMap, systemConfig, timeZone)
    }

    fun executeCrontabRewriteFileSystemTime(crontabId: Long) {
        val crontab =
            sql.findById(
                CRONTAB_WITH_ALBUMS_FETCHER,
                crontabId
            ) ?: throw IllegalArgumentException("定时任务不存在: $crontabId")

        if (!crontab.config.rewriteFileSystemTime) {
            throw IllegalArgumentException("定时任务未启用重写文件系统时间选项: $crontabId")
        }

        val assetPathMap = fetchAssetPathMapForFileSystemTime(crontabId)

        taskScheduler.executeCrontabRewriteFileSystemTime(true, assetPathMap)
    }

    /**
     * 获取需要重写 EXIF 时间的资产路径映射
     * @param crontabId 定时任务 ID
     * @return 资产到文件路径的映射
     */
    private fun fetchAssetPathMapForExifTime(crontabId: Long): Map<Asset, Path> {
        log.info("查询定时任务 $crontabId 中需要重写 EXIF 时间的资产")
        
        val crontabHistoryDetails = sql.createQuery(CrontabHistoryDetail::class) {
            where(table.crontabHistory.crontab.id eq crontabId)
            where(table.exifFilled eq false)
            where(table.downloadCompleted eq true)
            select(table.fetchBy {
                asset { allTableFields() }
                filePath()
            })
        }.distinct().execute()

        val assetPathMap = mutableMapOf<Asset, Path>()
        crontabHistoryDetails.forEach { detail ->
            assetPathMap[detail.asset] = Path(detail.filePath)
        }
        
        log.info("找到 ${assetPathMap.size} 个需要重写 EXIF 时间的资产")
        return assetPathMap
    }

    /**
     * 获取需要重写文件系统时间的资产路径映射
     * @param crontabId 定时任务 ID
     * @return 资产到文件路径的映射
     */
    private fun fetchAssetPathMapForFileSystemTime(crontabId: Long): Map<Asset, Path> {
        log.info("查询定时任务 $crontabId 中需要重写文件系统时间的资产")
        
        val crontabHistoryDetails = sql.createQuery(CrontabHistoryDetail::class) {
            where(table.crontabHistory.crontab.id eq crontabId)
            where(table.fsTimeUpdated eq false)
            where(table.downloadCompleted eq true)
            select(table.fetchBy {
                asset { allTableFields() }
                filePath()
            })
        }.distinct().execute()

        val assetPathMap = mutableMapOf<Asset, Path>()
        crontabHistoryDetails.forEach { detail ->
            assetPathMap[detail.asset] = Path(detail.filePath)
        }
        
        log.info("找到 ${assetPathMap.size} 个需要重写文件系统时间的资产")
        return assetPathMap
    }


}