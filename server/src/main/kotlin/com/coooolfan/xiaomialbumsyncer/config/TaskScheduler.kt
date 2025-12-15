package com.coooolfan.xiaomialbumsyncer.config

import com.coooolfan.xiaomialbumsyncer.controller.CrontabController.Companion.CRONTAB_WITH_ALBUM_IDS_FETCHER
import com.coooolfan.xiaomialbumsyncer.model.Asset
import com.coooolfan.xiaomialbumsyncer.model.Crontab
import com.coooolfan.xiaomialbumsyncer.model.SystemConfig
import com.coooolfan.xiaomialbumsyncer.model.enabled
import com.coooolfan.xiaomialbumsyncer.pipeline.CrontabPipeline
import com.coooolfan.xiaomialbumsyncer.utils.TaskActuators
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.noear.solon.annotation.Init
import org.noear.solon.annotation.Managed
import org.noear.solon.scheduling.annotation.Scheduled
import org.noear.solon.scheduling.scheduled.manager.IJobManager
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.text.ParseException
import java.util.Collections
import java.util.TimeZone

@Managed
class TaskScheduler(
    private val jobManager: IJobManager,
    private val sql: KSqlClient,
    private val actuators: TaskActuators,
    private val pipeline: CrontabPipeline,
    private val thread: ThreadExecutor
) {

    private val log = LoggerFactory.getLogger(this.javaClass)
    private val runningCrontabs: MutableSet<Long> = Collections.synchronizedSet(mutableSetOf())

    /** 初始化并注册所有启用的定时任务
     * 该方法在应用启动时执行一次，也可以重复调用以重新载入所有任务
     *
     * 如果某个任务的 cron 表达式无效，则该任务不会被注册，且会在日志中记录错误
     */
    @Init
    @Synchronized
    fun initJobs() {
        val crontabs = sql.executeQuery(Crontab::class) {
            where(table.enabled eq true)
            select(table.fetch(CRONTAB_WITH_ALBUM_IDS_FETCHER))
        }
        val registeredJobs = mutableListOf<Crontab>()

        // 取不可变快照，避免并发修改
        jobManager.jobGetAll().keys.toList().forEach { jobManager.jobRemove(it) }

        for (crontab in crontabs)
            try {
                jobManager.jobAdd(
                    "${crontab.id}:${crontab.name}",
                    Scheduled(cron = crontab.config.expression, zone = crontab.config.timeZone)
                ) {
                    executeCrontab(crontab)
                }
                registeredJobs.add(crontab)
            } catch (e: IllegalArgumentException) {
                if (e.cause is ParseException)
                    log.error(
                        "定时任务[${crontab.id}:${crontab.name}]的 cron 表达式解析失败，该任务的注册被跳过，描述: ${e.message}. ${e.cause?.message}"
                    )
                else
                    throw e
            }


        log.info("载入定时任务完成，共注册 ${registeredJobs.size} 个任务")

    }

    /** 立即执行某个定时任务
     * @param crontab 要执行的定时任务实体
     * @param async 是否异步执行，默认 true。若为 false，则在当前线程中执行该任务，阻塞，直到任务完成才返回
     */
    fun executeCrontab(crontab: Crontab, async: Boolean = true) {
        if (async) {
            thread.taskExecutor().execute { executeWithGuard(crontab) }
        } else {
            executeWithGuard(crontab)
        }
    }

    private fun executeWithGuard(crontab: Crontab) {
        if (!runningCrontabs.add(crontab.id)) {
            log.warn("定时任务[${crontab.id}:${crontab.name}]正在运行中，跳过本次执行")
            return
        }
        try {
//            pipeline.execute()
            actuators.doWork(crontab)
        } finally {
            runningCrontabs.remove(crontab.id)
        }
    }

    fun executeCrontabExifTime(
        async: Boolean = true, assetPathMap: Map<Asset, Path>,
        systemConfig: SystemConfig, timeZone: TimeZone
    ) {
        if (async) {
            thread.taskExecutor().execute { actuators.fillExifTime(assetPathMap, systemConfig, timeZone) }
        } else {
            actuators.fillExifTime(assetPathMap, systemConfig, timeZone)
        }
    }

    fun executeCrontabRewriteFileSystemTime(
        async: Boolean = true, assetPathMap: Map<Asset, Path>
    ) {
        if (async) {
            thread.taskExecutor().execute { actuators.rewriteFileSystemTime(assetPathMap) }
        } else {
            actuators.rewriteFileSystemTime(assetPathMap)
        }
    }

}