package com.coooolfan.xiaomialbumsyncer.service

import com.coooolfan.xiaomialbumsyncer.config.TaskScheduler
import com.coooolfan.xiaomialbumsyncer.controller.CrontabController.Companion.CRONTAB_WITH_ALBUM_IDS_FETCHER
import com.coooolfan.xiaomialbumsyncer.model.*
import com.coooolfan.xiaomialbumsyncer.model.dto.CrontabCreateInput
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.noear.solon.annotation.Managed
import java.nio.file.Path
import kotlin.io.path.Path

@Managed
class CrontabService(private val sql: KSqlClient, private val taskScheduler: TaskScheduler) {
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

    fun updateCrontab(crontab: Crontab, fetcher: Fetcher<Crontab>): Crontab {
        val execute = sql.saveCommand(crontab, SaveMode.UPDATE_ONLY).execute(fetcher)
        taskScheduler.initJobs()
        return execute.modifiedEntity
    }

    fun executeCrontab(crontabId: Long) {
        val crontab =
            sql.findById(
                CRONTAB_WITH_ALBUM_IDS_FETCHER,
                crontabId
            ) ?: throw IllegalArgumentException("定时任务不存在: $crontabId")

        taskScheduler.executeCrontab(crontab, true)
    }

    fun executeCrontabExifTime(crontabId: Long) {
        val crontab =
            sql.findById(
                CRONTAB_WITH_ALBUM_IDS_FETCHER,
                crontabId
            ) ?: throw IllegalArgumentException("定时任务不存在: $crontabId")

        val systemConfig = sql.findById(SystemConfig::class, 0)
            ?: throw IllegalStateException("System is not initialized")

        val crontabHistoryDetails = sql.createQuery(CrontabHistoryDetail::class) {
            where(table.crontabHistoryId eq crontabId)
            select(table.fetch(newFetcher(CrontabHistoryDetail::class).by {
                asset { allTableFields() }
                filePath()
            }))
        }.distinct().execute()

        val assetPathMap = mutableMapOf<Asset, Path>()
        crontabHistoryDetails.forEach { detail ->
            assetPathMap[detail.asset] = Path(detail.filePath)
        }

        taskScheduler.executeCrontabExifTime(crontab, true, assetPathMap, systemConfig)
    }
}