package com.coooolfan.xiaomialbumsyncer.service

import com.coooolfan.xiaomialbumsyncer.config.TaskScheduler
import com.coooolfan.xiaomialbumsyncer.controller.CrontabController.Companion.CRONTAB_WITH_ALBUMS_FETCHER
import com.coooolfan.xiaomialbumsyncer.controller.CrontabCurrentStats
import com.coooolfan.xiaomialbumsyncer.controller.CrontabHistoryGroup
import com.coooolfan.xiaomialbumsyncer.model.*
import com.coooolfan.xiaomialbumsyncer.model.dto.CrontabCreateInput
import org.babyfish.jimmer.Page
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.*
import org.babyfish.jimmer.sql.kt.ast.query.baseTableSymbol
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

    // 避免一下循环依赖
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

    fun clearCrontabHistory(crontabId: Long) {
        sql.executeDelete(CrontabHistory::class) {
            where(table.crontabId eq crontabId)
        }
    }

    fun isCrontabRunning(crontabId: Long): Boolean = taskScheduler.checkIsRunning(crontabId)

    fun getCrontabCurrentStats(crontabId: Long): CrontabCurrentStats {
        if (!taskScheduler.checkIsRunning(crontabId))
            return CrontabCurrentStats() // 没有正在运行

        val runningCrontabHistory = sql.createQuery(CrontabHistory::class) {
            where(table.crontabId eq crontabId)
            where(table.endTime.isNull())
            orderBy(table.startTime.desc())
            select(table)
        }.limit(1).execute().firstOrNull() ?: return CrontabCurrentStats() // 没有正在运行

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
        return sql.createQuery(CrontabHistory::class) {
            orderBy(table.startTime.desc())
            where(table.crontabId eq history.crontab.id)
            where(table.id ne history.id)
            where(table.endTime ne null)
            select(table.timelineSnapshot)
        }.limit(1).execute().firstOrNull() ?: emptyMap()
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

        val assetPathMap = fetchAssetPathMapBy(crontab.id)

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

        val assetPathMap = fetchAssetPathMapBy(crontab.id)

        taskScheduler.executeCrontabRewriteFileSystemTime(true, assetPathMap)
    }

    fun listCrontabHistoryDetails(
        historyId: Long,
        pageIndex: Int,
        pageSize: Int,
        fetcher: Fetcher<CrontabHistoryDetail>
    ): Page<CrontabHistoryDetail> {
        return sql.createQuery(CrontabHistoryDetail::class) {
            where(table.crontabHistoryId eq historyId)
            select(table.fetch(fetcher))
        }.fetchPage(pageIndex, pageSize)
    }

    /**
     * 分页获取定时任务的执行历史，按“连续 0 资产”折叠。
     *
     * 连续的已完成且资产明细数为 0 的执行记录会合并为一个分组，
     * 其余记录（有资产/进行中）各自单独成组。
     */
    fun listCrontabHistoryGroups(
        crontabId: Long,
        pageIndex: Int,
        pageSize: Int
    ): Page<CrontabHistoryGroup> {
        // L1: id, startTime, endTime, detailsCount（@Transient 不是列，用 count 子查询代替）
        val src = baseTableSymbol {
            sql.createBaseQuery(CrontabHistory::class) {
                where(table.crontabId eq crontabId)
                selections
                    .add(table.id)
                    .add(table.startTime)
                    .add(table.endTime)
                    .add(
                        subQuery(CrontabHistoryDetail::class) {
                            where(table.crontabHistoryId eq parentTable.id)
                            select(count(table))
                        }
                    )
            }
        }
        // L2: + foldable, prevFoldable（窗口函数不能嵌套，LAG 需要物化一层）
        val marked = baseTableSymbol {
            sql.createBaseQuery(src) {
                selections
                    .add(table._1)
                    .add(table._2)
                    .add(table._3)
                    .add(table._4)
                    .add(
                        sql(
                            Int::class,
                            "case when %e is not null and %e = 0 then 1 else 0 end",
                            table._3, table._4
                        )
                    )
                    .add(
                        sqlNullable(
                            Int::class,
                            "lag(case when %e is not null and %e = 0 then 1 else 0 end) over(order by %e desc)",
                            table._3, table._4, table._2
                        )
                    )
            }
        }
        // L3: + 组号（非折叠行或前一行非折叠时开启新组）
        val grouped = baseTableSymbol {
            sql.createBaseQuery(marked) {
                selections
                    .add(table._1)
                    .add(table._2)
                    .add(table._3)
                    .add(table._4)
                    .add(
                        sql(
                            Long::class,
                            "sum(case when %e = 0 or %e is null or %e = 0 then 1 else 0 end) over(order by %e desc rows unbounded preceding)",
                            table._5, table._6, table._6, table._2
                        )
                    )
            }
        }
        val page = sql.createQuery(grouped) {
            orderBy(table._5.asc())
            groupBy(table._5)
            select(
                max(table._1),
                min(table._2),
                max(table._3),
                count(table._1),
                sum(table._4)
            )
        }.fetchPage(pageIndex, pageSize)
        return Page(
            page.rows.map {
                CrontabHistoryGroup(
                    historyId = it._1 ?: 0,
                    startTime = it._2 ?: Instant.EPOCH,
                    endTime = it._3,
                    runCount = it._4,
                    detailsCount = it._5 ?: 0
                )
            },
            page.totalRowCount,
            page.totalPageCount
        )
    }

    fun updateDetailMessage(detailId: Long, message: String) {
        sql.executeUpdate(CrontabHistoryDetail::class) {
            set(table.message, message)
            where(table.id eq detailId)
        }
    }

    private fun fetchAssetPathMapBy(crontabId: Long): Map<Asset, Path> {
        val crontabHistoryDetails = sql.createQuery(CrontabHistoryDetail::class) {
            where(table.crontabHistoryId eq crontabId)
            select(table.fetchBy {
                asset { allTableFields() }
                filePath()
            })
        }.distinct().execute()

        val assetPathMap = mutableMapOf<Asset, Path>()
        crontabHistoryDetails.forEach { detail ->
            assetPathMap[detail.asset] = Path(detail.filePath)
        }
        return assetPathMap
    }


}
