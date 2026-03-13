package com.coooolfan.xiaomialbumsyncer.service

import com.coooolfan.xiaomialbumsyncer.controller.CrontabController.Companion.CRONTAB_WITH_ALBUMS_FETCHER
import com.coooolfan.xiaomialbumsyncer.model.*
import com.coooolfan.xiaomialbumsyncer.model.by
import com.coooolfan.xiaomialbumsyncer.xiaomicloud.XiaoMiApi
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.asc
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.kt.ast.expression.lt
import org.babyfish.jimmer.sql.kt.ast.expression.valueIn
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.noear.solon.annotation.Managed
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.io.path.Path
import kotlin.io.path.exists

@Managed
class ArchiveService(
    private val sql: KSqlClient,
    private val xiaoMiApi: XiaoMiApi,
    private val fileService: FileService,
    private val fileTimeStage: com.coooolfan.xiaomialbumsyncer.pipeline.stages.FileTimeStage
) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    companion object {
        private val ASSET_WITH_ALBUM_FETCHER = newFetcher(Asset::class).by {
            allScalarFields()
            album {
                allScalarFields()
            }
        }
    }

    data class ArchivePlan(
        val archiveBeforeDate: LocalDate,
        val assetsToArchive: List<Asset>,
        val estimatedFreedSpace: Long
    )

    class ArchiveNotConfirmedException(message: String) : Exception(message)
    class ArchiveDisabledException(message: String) : Exception(message)
    class FileIntegrityException(message: String) : Exception(message)

    fun calculateTimeBasedArchive(crontabId: Long): ArchivePlan {
        val crontab = sql.findById(CRONTAB_WITH_ALBUMS_FETCHER, crontabId)
            ?: throw IllegalArgumentException("定时任务不存在: $crontabId")

        val config = crontab.config
        val archiveBeforeDate = LocalDate.now().minusDays(config.archiveDays.toLong())
        val archiveBeforeInstant = archiveBeforeDate.atStartOfDay().toInstant(ZoneOffset.UTC)
        val archivedAssetIds = getArchivedAssetIds(crontabId).toSet()

        val allAssets = sql.createQuery(Asset::class) {
            where(table.album.id valueIn crontab.albums.map { it.id })
            where(table.dateTaken lt archiveBeforeInstant)
            select(table.fetch(ASSET_WITH_ALBUM_FETCHER))
        }.execute()

        val assetsToArchive = allAssets.filter { it.id !in archivedAssetIds }
        val estimatedFreedSpace = assetsToArchive.sumOf { it.size }

        return ArchivePlan(archiveBeforeDate, assetsToArchive, estimatedFreedSpace)
    }

    fun calculateSpaceBasedArchive(crontabId: Long): ArchivePlan {
        val crontab = sql.findById(CRONTAB_WITH_ALBUMS_FETCHER, crontabId)
            ?: throw IllegalArgumentException("定时任务不存在: $crontabId")

        val config = crontab.config
        val spaceInfo = xiaoMiApi.getCloudSpace(crontab.accountId)
        val targetUsed = (spaceInfo.totalQuota * config.cloudSpaceThreshold / 100.0).toLong()
        val needToFree = spaceInfo.used - targetUsed

        if (needToFree <= 0) {
            return ArchivePlan(LocalDate.now(), emptyList(), 0)
        }

        val archivedAssetIds = getArchivedAssetIds(crontabId).toSet()

        val allAssets = sql.createQuery(Asset::class) {
            where(table.album.id valueIn crontab.albums.map { it.id })
            orderBy(table.dateTaken.asc())
            select(table.fetch(ASSET_WITH_ALBUM_FETCHER))
        }.execute()

        var freedSpace = 0L
        val assetsToArchive = mutableListOf<Asset>()

        for (asset in allAssets) {
            if (asset.id in archivedAssetIds) {
                continue
            }
            
            assetsToArchive.add(asset)
            freedSpace += asset.size

            if (freedSpace >= needToFree) {
                break
            }
        }

        val archiveBeforeDate = if (assetsToArchive.isNotEmpty()) {
            assetsToArchive.last().dateTaken.atZone(ZoneOffset.UTC).toLocalDate()
        } else {
            LocalDate.now()
        }

        return ArchivePlan(archiveBeforeDate, assetsToArchive, freedSpace)
    }

    private fun getArchivedAssetIds(crontabId: Long): List<Long> {
        return sql.createQuery(ArchiveDetail::class) {
            where(table.archiveRecord.crontabId eq crontabId)
            select(table.assetId)
        }.execute().distinct()
    }

    fun previewArchive(crontabId: Long): ArchivePlan {
        val crontab = sql.findById(CRONTAB_WITH_ALBUMS_FETCHER, crontabId)
            ?: throw IllegalArgumentException("定时任务不存在: $crontabId")

        return when (crontab.config.archiveMode) {
            ArchiveMode.DISABLED -> {
                ArchivePlan(
                    archiveBeforeDate = LocalDate.now(),
                    assetsToArchive = emptyList(),
                    estimatedFreedSpace = 0L
                )
            }
            ArchiveMode.TIME -> calculateTimeBasedArchive(crontabId)
            ArchiveMode.SPACE -> calculateSpaceBasedArchive(crontabId)
        }
    }

    suspend fun executeArchive(crontabId: Long, confirmed: Boolean): Int {
        log.info("开始执行归档任务，定时任务 ID=$crontabId")

        val crontab = sql.findById(CRONTAB_WITH_ALBUMS_FETCHER, crontabId)
            ?: throw IllegalArgumentException("定时任务不存在: $crontabId")

        val config = crontab.config

        if (config.archiveMode == ArchiveMode.DISABLED) {
            throw ArchiveDisabledException("归档功能已关闭")
        }

        val plan = when (config.archiveMode) {
            ArchiveMode.DISABLED -> throw IllegalStateException("Should not reach here")
            ArchiveMode.TIME -> calculateTimeBasedArchive(crontabId)
            ArchiveMode.SPACE -> calculateSpaceBasedArchive(crontabId)
        }

        if (plan.assetsToArchive.isEmpty()) {
            throw IllegalStateException("没有需要归档的资产")
        }

        val archiveRecord = createArchiveRecord(crontab, plan)

        try {
            val syncFolder = Path(config.targetPath, config.syncFolder)
            val backupFolder = Path(config.targetPath, config.backupFolder)

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

            if (config.deleteCloudAfterArchive) {
                updateArchiveStatus(archiveRecord.id, ArchiveStatus.DELETING_CLOUD, null)

                val successfullyMovedAssetIds = getSuccessfullyMovedAssetIds(archiveRecord.id)

                if (successfullyMovedAssetIds.isNotEmpty()) {
                    val deletedIds = xiaoMiApi.batchDeleteAssets(crontab.accountId, successfullyMovedAssetIds)

                    deletedIds.forEach { assetId ->
                        updateArchiveDetailCloudDeleted(archiveRecord.id, assetId)
                    }
                }
            }

            updateArchiveStatus(archiveRecord.id, ArchiveStatus.COMPLETED, null)

            return plan.assetsToArchive.size
        } catch (e: Exception) {
            log.error("归档任务失败", e)
            updateArchiveStatus(archiveRecord.id, ArchiveStatus.FAILED, e.message)
            throw e
        }
    }

    private fun moveToBackup(asset: Asset, syncFolder: Path, backupFolder: Path): ArchiveDetail {
        val sourcePath = Path(syncFolder.toString(), asset.album.name, asset.fileName)
        val targetPath = Path(backupFolder.toString(), asset.album.name, asset.fileName)

        if (!sourcePath.exists()) {
            if (targetPath.exists()) {
                val isValid = fileService.verifySha1(targetPath, asset.sha1)
                
                if (isValid) {
                    try {
                        fileTimeStage.updateFileSystemTime(asset, targetPath)
                    } catch (e: Exception) {
                        log.warn("更新 backup 文件的文件系统时间失败: ${asset.fileName}", e)
                    }
                    
                    return ArchiveDetail {
                        this.assetId = asset.id
                        this.sourcePath = sourcePath.toString()
                        this.targetPath = targetPath.toString()
                        this.isMovedToBackup = true
                        this.isDeletedFromCloud = false
                        this.errorMessage = "文件已存在于 backup 文件夹"
                    }
                } else {
                    throw FileIntegrityException("backup 文件夹中的文件完整性验证失败：${asset.fileName}")
                }
            } else {
                sql.deleteById(Asset::class, asset.id)
                throw IOException("文件不存在且已从数据库删除: ${asset.fileName}")
            }
        }

        fileService.moveFile(sourcePath, targetPath)
        val isValid = fileService.verifySha1(targetPath, asset.sha1)

        if (!isValid) {
            fileService.moveFile(targetPath, sourcePath)
            throw FileIntegrityException("文件完整性验证失败：${asset.fileName}")
        }

        try {
            fileTimeStage.updateFileSystemTime(asset, targetPath)
        } catch (e: Exception) {
            log.warn("更新归档文件的文件系统时间失败: ${asset.fileName}", e)
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

    private fun createArchiveRecord(crontab: Crontab, plan: ArchivePlan): ArchiveRecord {
        val record = ArchiveRecord {
            this.crontabId = crontab.id
            archiveTime = Instant.now()
            archiveMode = crontab.config.archiveMode
            archiveBeforeDate = plan.archiveBeforeDate
            archivedCount = plan.assetsToArchive.size
            freedSpaceBytes = plan.estimatedFreedSpace
            status = ArchiveStatus.PLANNING
            errorMessage = null
        }

        val saved = sql.save(record, SaveMode.INSERT_ONLY)
        return sql.findById(ArchiveRecord::class, saved.modifiedEntity.id)!!
    }

    private fun updateArchiveStatus(archiveRecordId: Long, status: ArchiveStatus, errorMessage: String?) {
        sql.createUpdate(ArchiveRecord::class) {
            set(table.status, status)
            if (errorMessage != null) {
                set(table.errorMessage, errorMessage)
            }
            where(table.id eq archiveRecordId)
        }.execute()
    }

    private fun recordArchiveDetail(archiveRecordId: Long, detail: ArchiveDetail) {
        val detailToSave = ArchiveDetail(detail) {
            this.archiveRecordId = archiveRecordId
        }
        sql.save(detailToSave, SaveMode.INSERT_ONLY)
    }

    private fun getSuccessfullyMovedAssetIds(archiveRecordId: Long): List<Long> {
        return sql.createQuery(ArchiveDetail::class) {
            where(table.archiveRecordId eq archiveRecordId)
            where(table.isMovedToBackup eq true)
            select(table.assetId)
        }.execute()
    }

    private fun updateArchiveDetailCloudDeleted(archiveRecordId: Long, assetId: Long) {
        sql.createUpdate(ArchiveDetail::class) {
            set(table.isDeletedFromCloud, true)
            where(table.archiveRecordId eq archiveRecordId)
            where(table.assetId eq assetId)
        }.execute()
    }
}
