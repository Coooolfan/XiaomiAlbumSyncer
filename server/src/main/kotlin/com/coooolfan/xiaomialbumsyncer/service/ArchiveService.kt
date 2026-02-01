package com.coooolfan.xiaomialbumsyncer.service

import com.coooolfan.xiaomialbumsyncer.model.*
import com.coooolfan.xiaomialbumsyncer.xiaomicloud.XiaoMiApi
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.asc
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.kt.ast.expression.lt
import org.babyfish.jimmer.sql.kt.ast.expression.valueIn
import org.noear.solon.annotation.Managed
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.io.path.Path
import kotlin.io.path.exists

/**
 * 归档服务
 * 实现智能归档功能
 */
@Managed
class ArchiveService(
    private val sql: KSqlClient,
    private val xiaoMiApi: XiaoMiApi,
    private val fileService: FileService
) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    /**
     * 归档计划
     */
    data class ArchivePlan(
        val archiveBeforeDate: LocalDate,
        val assetsToArchive: List<Asset>,
        val estimatedFreedSpace: Long
    )

    /**
     * 归档未确认异常
     */
    class ArchiveNotConfirmedException(message: String) : Exception(message)

    /**
     * 文件完整性异常
     */
    class FileIntegrityException(message: String) : Exception(message)

    /**
     * 计算基于时间的归档计划
     * @param crontabId 定时任务 ID
     * @return 归档计划
     */
    fun calculateTimeBasedArchive(crontabId: Long): ArchivePlan {
        log.info("计算基于时间的归档计划，定时任务 ID=$crontabId")

        val crontab = sql.findById(Crontab::class, crontabId)
            ?: throw IllegalArgumentException("定时任务不存在: $crontabId")

        val config = crontab.config

        // 计算归档截止日期
        val archiveBeforeDate = LocalDate.now().minusDays(config.archiveDays.toLong())
        val archiveBeforeInstant = archiveBeforeDate.atStartOfDay().toInstant(ZoneOffset.UTC)

        // 查询需要归档的资产
        val assetsToArchive = sql.createQuery(Asset::class) {
            where(table.album.id valueIn crontab.albums.map { it.id })
            where(table.dateTaken lt archiveBeforeInstant)
            select(table)
        }.execute()

        // 计算释放的空间
        val estimatedFreedSpace = assetsToArchive.sumOf { it.size }

        log.info("基于时间的归档计划：归档日期=$archiveBeforeDate，资产数=${assetsToArchive.size}，释放空间=$estimatedFreedSpace 字节")

        return ArchivePlan(archiveBeforeDate, assetsToArchive, estimatedFreedSpace)
    }

    /**
     * 计算基于空间阈值的归档计划
     * @param crontabId 定时任务 ID
     * @return 归档计划
     */
    fun calculateSpaceBasedArchive(crontabId: Long): ArchivePlan {
        log.info("计算基于空间阈值的归档计划，定时任务 ID=$crontabId")

        val crontab = sql.findById(Crontab::class, crontabId)
            ?: throw IllegalArgumentException("定时任务不存在: $crontabId")

        val config = crontab.config

        // 获取云端空间使用情况
        val spaceInfo = xiaoMiApi.getCloudSpace(crontab.accountId)

        // 计算需要释放的空间
        val targetUsed = (spaceInfo.totalQuota * config.cloudSpaceThreshold / 100.0).toLong()
        val needToFree = spaceInfo.galleryUsed - targetUsed

        if (needToFree <= 0) {
            // 不需要归档
            log.info("云端空间充足，无需归档")
            return ArchivePlan(LocalDate.now(), emptyList(), 0)
        }

        // 按时间从旧到新排序资产
        val allAssets = sql.createQuery(Asset::class) {
            where(table.album.id valueIn crontab.albums.map { it.id })
            orderBy(table.dateTaken.asc())
            select(table)
        }.execute()

        // 累加资产大小直到达到需要释放的空间
        var freedSpace = 0L
        val assetsToArchive = mutableListOf<Asset>()

        for (asset in allAssets) {
            assetsToArchive.add(asset)
            freedSpace += asset.size

            if (freedSpace >= needToFree) {
                break
            }
        }

        // 确定归档截止日期
        val archiveBeforeDate = if (assetsToArchive.isNotEmpty()) {
            assetsToArchive.last().dateTaken.atZone(ZoneOffset.UTC).toLocalDate()
        } else {
            LocalDate.now()
        }

        log.info("基于空间阈值的归档计划：归档日期=$archiveBeforeDate，资产数=${assetsToArchive.size}，释放空间=$freedSpace 字节")

        return ArchivePlan(archiveBeforeDate, assetsToArchive, freedSpace)
    }

    /**
     * 预览归档计划
     * @param crontabId 定时任务 ID
     * @return 归档计划
     */
    fun previewArchive(crontabId: Long): ArchivePlan {
        val crontab = sql.findById(Crontab::class, crontabId)
            ?: throw IllegalArgumentException("定时任务不存在: $crontabId")

        return when (crontab.config.archiveMode) {
            ArchiveMode.TIME -> calculateTimeBasedArchive(crontabId)
            ArchiveMode.SPACE -> calculateSpaceBasedArchive(crontabId)
        }
    }

    /**
     * 执行归档任务
     * @param crontabId 定时任务 ID
     * @param confirmed 是否已确认
     * @return 归档记录 ID
     */
    suspend fun executeArchive(crontabId: Long, confirmed: Boolean): Long {
        log.info("开始执行归档任务，定时任务 ID=$crontabId，已确认=$confirmed")

        val crontab = sql.findById(Crontab::class, crontabId)
            ?: throw IllegalArgumentException("定时任务不存在: $crontabId")

        val config = crontab.config

        if (!config.enableArchive) {
            throw IllegalStateException("定时任务未启用归档功能: $crontabId")
        }

        // 检查是否需要确认
        if (config.confirmBeforeArchive && !confirmed) {
            throw ArchiveNotConfirmedException("归档操作需要用户确认")
        }

        // 生成归档计划
        val plan = when (config.archiveMode) {
            ArchiveMode.TIME -> calculateTimeBasedArchive(crontabId)
            ArchiveMode.SPACE -> calculateSpaceBasedArchive(crontabId)
        }

        if (plan.assetsToArchive.isEmpty()) {
            log.info("没有需要归档的资产")
            throw IllegalStateException("没有需要归档的资产")
        }

        // 创建归档记录
        val archiveRecord = createArchiveRecord(crontab, plan)

        try {
            val syncFolder = Path(config.targetPath, config.syncFolder)
            val backupFolder = Path(config.targetPath, config.backupFolder)

            // 移动文件到 backup 文件夹
            updateArchiveStatus(archiveRecord.id, ArchiveStatus.MOVING_FILES, null)
            plan.assetsToArchive.forEach { asset ->
                try {
                    val detail = moveToBackup(asset, syncFolder, backupFolder)
                    recordArchiveDetail(archiveRecord.id, detail)
                } catch (e: Exception) {
                    log.error("移动文件到 backup 失败: ${asset.fileName}", e)
                    recordArchiveDetail(
                        archiveRecord.id,
                        ArchiveDetail {
                            this.archiveRecordId = archiveRecord.id
                            this.assetId = asset.id
                            sourcePath = Path(syncFolder.toString(), asset.album.name, asset.fileName).toString()
                            targetPath = Path(backupFolder.toString(), asset.album.name, asset.fileName).toString()
                            isMovedToBackup = false
                            isDeletedFromCloud = false
                            errorMessage = e.message
                        }
                    )
                }
            }

            // 删除云端照片
            if (config.deleteCloudAfterArchive) {
                updateArchiveStatus(archiveRecord.id, ArchiveStatus.DELETING_CLOUD, null)

                // 只删除成功移动到 backup 的照片
                val successfullyMovedAssetIds = getSuccessfullyMovedAssetIds(archiveRecord.id)

                if (successfullyMovedAssetIds.isNotEmpty()) {
                    val deletedIds = xiaoMiApi.batchDeleteAssets(crontab.accountId, successfullyMovedAssetIds)

                    // 更新归档详情
                    deletedIds.forEach { assetId ->
                        updateArchiveDetailCloudDeleted(archiveRecord.id, assetId)
                    }
                }
            }

            // 更新归档记录状态
            updateArchiveStatus(archiveRecord.id, ArchiveStatus.COMPLETED, null)

            log.info("归档任务完成，归档记录 ID=${archiveRecord.id}")

            return archiveRecord.id
        } catch (e: Exception) {
            log.error("归档任务失败", e)
            updateArchiveStatus(archiveRecord.id, ArchiveStatus.FAILED, e.message)
            throw e
        }
    }

    /**
     * 移动文件到 backup 文件夹
     */
    private fun moveToBackup(asset: Asset, syncFolder: Path, backupFolder: Path): ArchiveDetail {
        val sourcePath = Path(syncFolder.toString(), asset.album.name, asset.fileName)
        val targetPath = Path(backupFolder.toString(), asset.album.name, asset.fileName)

        // 移动文件
        fileService.moveFile(sourcePath, targetPath)

        // 验证文件完整性
        val isValid = fileService.verifySha1(targetPath, asset.sha1)

        if (!isValid) {
            // 回滚：移动回 sync 文件夹
            fileService.moveFile(targetPath, sourcePath)
            throw FileIntegrityException("文件完整性验证失败：${asset.fileName}")
        }

        return ArchiveDetail {
            this.assetId = asset.id
            this.sourcePath = sourcePath.toString()
            this.targetPath = targetPath.toString()
            this.isMovedToBackup = true
            this.isDeletedFromCloud = false
            this.errorMessage = null
        }
    }

    /**
     * 创建归档记录
     */
    private fun createArchiveRecord(crontab: Crontab, plan: ArchivePlan): ArchiveRecord {
        val record = ArchiveRecord {
            this.crontab = crontab
            archiveTime = Instant.now()
            archiveMode = crontab.config.archiveMode
            archiveBeforeDate = plan.archiveBeforeDate
            archivedCount = plan.assetsToArchive.size
            freedSpaceBytes = plan.estimatedFreedSpace
            status = ArchiveStatus.PLANNING
            errorMessage = null
        }

        val saved = sql.save(record)
        return sql.findById(ArchiveRecord::class, saved.modifiedEntity.id)!!
    }

    /**
     * 更新归档记录状态
     */
    private fun updateArchiveStatus(archiveRecordId: Long, status: ArchiveStatus, errorMessage: String?) {
        sql.createUpdate(ArchiveRecord::class) {
            set(table.status, status)
            if (errorMessage != null) {
                set(table.errorMessage, errorMessage)
            }
            where(table.id eq archiveRecordId)
        }.execute()
    }

    /**
     * 记录归档详情
     */
    private fun recordArchiveDetail(archiveRecordId: Long, detail: ArchiveDetail) {
        val detailToSave = ArchiveDetail(detail) {
            this.archiveRecordId = archiveRecordId
        }
        sql.save(detailToSave)
    }

    /**
     * 获取成功移动到 backup 的资产 ID 列表
     */
    private fun getSuccessfullyMovedAssetIds(archiveRecordId: Long): List<Long> {
        return sql.createQuery(ArchiveDetail::class) {
            where(table.archiveRecordId eq archiveRecordId)
            where(table.isMovedToBackup eq true)
            select(table.assetId)
        }.execute()
    }

    /**
     * 更新归档详情的云端删除状态
     */
    private fun updateArchiveDetailCloudDeleted(archiveRecordId: Long, assetId: Long) {
        sql.createUpdate(ArchiveDetail::class) {
            set(table.isDeletedFromCloud, true)
            where(table.archiveRecordId eq archiveRecordId)
            where(table.assetId eq assetId)
        }.execute()
    }
}
