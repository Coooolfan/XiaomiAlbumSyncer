package com.coooolfan.xiaomialbumsyncer.service

import com.coooolfan.xiaomialbumsyncer.model.*
import com.coooolfan.xiaomialbumsyncer.xiaomicloud.XiaoMiApi
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.desc
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.kt.ast.expression.valueIn
import org.noear.solon.annotation.Inject
import org.noear.solon.annotation.Managed
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.Path
import kotlin.io.path.exists

/**
 * 同步服务
 * 实现云端到本地的同步功能
 */
@Managed
class SyncService(
    private val sql: KSqlClient,
    private val xiaoMiApi: XiaoMiApi,
    private val fileService: FileService
) {

    @Inject
    private lateinit var assetService: AssetService

    private val log = LoggerFactory.getLogger(this.javaClass)

    /**
     * 变化摘要
     */
    data class ChangeSummary(
        val addedAssets: List<Asset>,
        val deletedAssets: List<Asset>,
        val updatedAssets: List<Asset>
    )

    /**
     * 同步状态信息
     */
    data class SyncStatusInfo(
        val isRunning: Boolean = false,
        val lastSyncTime: Instant? = null,
        val lastSyncResult: SyncStatus? = null
    )

    /**
     * 检测云端变化
     * @param crontabId 定时任务 ID
     * @return 变化摘要
     */
    fun detectChanges(crontabId: Long): ChangeSummary {
        log.info("开始检测云端变化，定时任务 ID=$crontabId")

        // 获取定时任务配置
        val crontab = sql.findById(Crontab::class, crontabId)
            ?: throw IllegalArgumentException("定时任务不存在: $crontabId")

        // 获取云端资产列表
        val cloudAssets = mutableListOf<Asset>()
        crontab.albums.forEach { album ->
            val assets = xiaoMiApi.fetchAllAssetsByAlbumId(album)
            cloudAssets.addAll(assets)
        }

        // 获取本地 sync 文件夹中的资产列表（从数据库查询）
        val localAssets = sql.createQuery(Asset::class) {
            where(table.album.id valueIn crontab.albums.map { it.id })
            select(table)
        }.execute()

        // 比较两个列表
        val cloudAssetMap = cloudAssets.associateBy { it.id }
        val localAssetMap = localAssets.associateBy { it.id }

        // 新增：云端有但本地没有
        val added = cloudAssets.filter { it.id !in localAssetMap }

        // 删除：本地有但云端没有
        val deleted = localAssets.filter { it.id !in cloudAssetMap }

        // 修改：云端和本地都有，但 sha1 或 dateTaken 不同
        val updated = cloudAssets.filter { cloudAsset ->
            val localAsset = localAssetMap[cloudAsset.id]
            localAsset != null && (
                    cloudAsset.sha1 != localAsset.sha1 ||
                            cloudAsset.dateTaken != localAsset.dateTaken
                    )
        }

        log.info("变化检测完成：新增=${added.size}，删除=${deleted.size}，修改=${updated.size}")

        return ChangeSummary(added, deleted, updated)
    }

    /**
     * 执行同步任务
     * @param crontabId 定时任务 ID
     * @return 同步记录 ID
     */
    suspend fun executeSync(crontabId: Long): Long {
        log.info("开始执行同步任务，定时任务 ID=$crontabId")

        // 获取定时任务配置
        val crontab = sql.findById(Crontab::class, crontabId)
            ?: throw IllegalArgumentException("定时任务不存在: $crontabId")

        if (!crontab.config.enableSync) {
            throw IllegalStateException("定时任务未启用同步功能: $crontabId")
        }

        // 创建同步记录
        val syncRecord = createSyncRecord(crontab)

        try {
            // 检测变化
            val changes = detectChanges(crontabId)

            val syncFolder = Path(crontab.config.targetPath, crontab.config.syncFolder)

            // 处理新增
            changes.addedAssets.forEach { asset ->
                try {
                    downloadToSync(asset, syncFolder, crontab.accountId)
                    recordSyncDetail(syncRecord.id, asset, SyncOperation.ADD, syncFolder, true, null)
                } catch (e: Exception) {
                    log.error("下载新增资产失败: ${asset.fileName}", e)
                    recordSyncDetail(syncRecord.id, asset, SyncOperation.ADD, syncFolder, false, e.message)
                }
            }

            // 处理删除
            changes.deletedAssets.forEach { asset ->
                try {
                    deleteFromSync(asset, syncFolder)
                    recordSyncDetail(syncRecord.id, asset, SyncOperation.DELETE, syncFolder, true, null)
                } catch (e: Exception) {
                    log.error("删除资产失败: ${asset.fileName}", e)
                    recordSyncDetail(syncRecord.id, asset, SyncOperation.DELETE, syncFolder, false, e.message)
                }
            }

            // 处理修改
            changes.updatedAssets.forEach { asset ->
                try {
                    deleteFromSync(asset, syncFolder)
                    downloadToSync(asset, syncFolder, crontab.accountId)
                    recordSyncDetail(syncRecord.id, asset, SyncOperation.UPDATE, syncFolder, true, null)
                } catch (e: Exception) {
                    log.error("更新资产失败: ${asset.fileName}", e)
                    recordSyncDetail(syncRecord.id, asset, SyncOperation.UPDATE, syncFolder, false, e.message)
                }
            }

            // 更新同步记录状态
            updateSyncRecord(
                syncRecord.id,
                SyncStatus.COMPLETED,
                changes.addedAssets.size,
                changes.deletedAssets.size,
                changes.updatedAssets.size,
                null
            )

            log.info("同步任务完成，同步记录 ID=${syncRecord.id}")

            return syncRecord.id
        } catch (e: Exception) {
            log.error("同步任务失败", e)
            updateSyncRecord(syncRecord.id, SyncStatus.FAILED, 0, 0, 0, e.message)
            throw e
        }
    }

    /**
     * 获取同步状态
     * @param crontabId 定时任务 ID
     * @return 同步状态信息
     */
    fun getSyncStatus(crontabId: Long): SyncStatusInfo {
        val lastSyncRecord = sql.createQuery(SyncRecord::class) {
            where(table.crontabId eq crontabId)
            orderBy(table.syncTime.desc())
            select(table)
        }.limit(1).execute().firstOrNull()

        return if (lastSyncRecord != null) {
            SyncStatusInfo(
                isRunning = lastSyncRecord.status == SyncStatus.RUNNING,
                lastSyncTime = lastSyncRecord.syncTime,
                lastSyncResult = lastSyncRecord.status
            )
        } else {
            SyncStatusInfo()
        }
    }

    /**
     * 创建同步记录
     */
    private fun createSyncRecord(crontab: Crontab): SyncRecord {
        val record = SyncRecord {
            this.crontab = crontab
            syncTime = Instant.now()
            addedCount = 0
            deletedCount = 0
            updatedCount = 0
            status = SyncStatus.RUNNING
            errorMessage = null
        }

        val saved = sql.save(record)
        return sql.findById(SyncRecord::class, saved.modifiedEntity.id)!!
    }

    /**
     * 更新同步记录
     */
    private fun updateSyncRecord(
        syncRecordId: Long,
        status: SyncStatus,
        addedCount: Int,
        deletedCount: Int,
        updatedCount: Int,
        errorMessage: String?
    ) {
        sql.createUpdate(SyncRecord::class) {
            set(table.status, status)
            set(table.addedCount, addedCount)
            set(table.deletedCount, deletedCount)
            set(table.updatedCount, updatedCount)
            set(table.errorMessage, errorMessage)
            where(table.id eq syncRecordId)
        }.execute()
    }

    /**
     * 记录同步详情
     */
    private fun recordSyncDetail(
        syncRecordId: Long,
        asset: Asset,
        operation: SyncOperation,
        syncFolder: Path,
        isCompleted: Boolean,
        errorMessage: String?
    ) {
        val filePath = Path(syncFolder.toString(), asset.album.name, asset.fileName).toString()

        val detail = SyncRecordDetail {
            this.syncRecordId = syncRecordId
            this.assetId = asset.id
            this.operation = operation
            this.filePath = filePath
            this.isCompleted = isCompleted
            this.errorMessage = errorMessage
        }

        sql.save(detail)
    }

    /**
     * 下载到 sync 文件夹
     */
    private fun downloadToSync(asset: Asset, syncFolder: Path, accountId: Long) {
        val targetPath = Path(syncFolder.toString(), asset.album.name, asset.fileName)
        xiaoMiApi.downloadAsset(accountId, asset, targetPath)
        log.debug("下载到 sync 文件夹: ${asset.fileName}")
    }

    /**
     * 从 sync 文件夹删除
     */
    private fun deleteFromSync(asset: Asset, syncFolder: Path) {
        val filePath = Path(syncFolder.toString(), asset.album.name, asset.fileName)
        if (filePath.exists()) {
            fileService.deleteFile(filePath)
            log.debug("从 sync 文件夹删除: ${asset.fileName}")
        }
    }
}
