package com.coooolfan.xiaomialbumsyncer.utils

import com.coooolfan.xiaomialbumsyncer.model.*
import com.coooolfan.xiaomialbumsyncer.xiaomicloud.XiaoMiApi
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.kt.ast.expression.ne
import org.babyfish.jimmer.sql.kt.ast.expression.notExists
import org.babyfish.jimmer.sql.kt.ast.expression.valueIn
import org.noear.solon.annotation.Managed
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZoneId
import java.util.TimeZone
import kotlin.io.path.Path

@Managed
class TaskActuators(private val sql: KSqlClient, private val api: XiaoMiApi) {

    private val log = LoggerFactory.getLogger(TaskActuators::class.java)

    fun doWork(crontab: Crontab) {

        log.info("执行定时任务: ${crontab.id}:${crontab.name}")

        // 1. 创建 CrontabHistory 记录的状态
        val crontabHistory = sql.saveCommand(CrontabHistory {
            crontabId = crontab.id
            startTime = Instant.now()
        }, SaveMode.INSERT_ONLY).execute().modifiedEntity

        // 2. 对 crontab.albums 进行同步操作, 重新刷新这些相册的所有 Asset
        val albums = sql.executeQuery(Album::class) {
            where(table.id valueIn crontab.albumIds)
            select(table)
        }

        val systemConfig =
            sql.findById(SystemConfig::class, 0) ?: throw IllegalStateException("System is not initialized")

        albums.forEach {
            val assets = api.fetchAssetsByAlbumId(it)
            sql.saveEntitiesCommand(assets, SaveMode.UPSERT).execute()
        }

        // 3. 与 CrontabHistoryDetails 对比，过滤出新增的 Asset
        val needDownloadAssets = sql.executeQuery(Asset::class) {
            where(table.albumId valueIn crontab.albumIds)
            where(
                notExists(
                    subQuery(CrontabHistoryDetail::class) {
                        where(table.crontabHistoryId eq crontab.id)
                        where(table.assetId eq parentTable.id)
                        select(table)
                    }
                )
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
                    crontabHistoryId = crontab.id
                    downloadTime = Instant.now()
                    filePath = path.toString()
                    assetId = it.id
                }, SaveMode.INSERT_ONLY).execute()
                assetPathMap[it] = path
            } catch (e: Exception) {
                log.error("下载文件失败，跳过此文件，Asset ID: ${it.id}, 错误信息: ${e.message}")
            }
        }

        // 5.1 可选：批量修改图片 EXIF 时间
        if (crontab.config.rewriteExifTime)
            assetPathMap.forEach {
                val rewriteZone = TimeZone.getTimeZone(ZoneId.of(crontab.config.rewriteExifTimeZone))

                try {
                    rewriteExifTime(
                        it.key,
                        it.value,
                        ExifRewriteConfig(
                            Path(systemConfig.exifToolPath),
                            rewriteZone
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
}