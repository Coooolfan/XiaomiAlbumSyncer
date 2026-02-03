package com.coooolfan.xiaomialbumsyncer.service

import com.coooolfan.xiaomialbumsyncer.controller.CrontabController.Companion.CRONTAB_WITH_ALBUMS_FETCHER
import com.coooolfan.xiaomialbumsyncer.controller.CrontabController.Companion.CRONTAB_WITH_ALBUMS_AND_ACCOUNT_FETCHER
import com.coooolfan.xiaomialbumsyncer.model.*
import com.coooolfan.xiaomialbumsyncer.xiaomicloud.XiaoMiApi
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
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
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries

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

    @Inject
    private lateinit var crontabService: CrontabService

    @Inject
    private lateinit var archiveService: ArchiveService

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
     * 检测云端变化（针对同步功能）
     * @param crontabId 定时任务 ID
     * @return 变化摘要
     */
    fun detectSyncChanges(crontabId: Long): ChangeSummary {
        log.info("开始检测同步变化，定时任务 ID=$crontabId")

        // 获取定时任务配置（包含关联的相册和账号）
        val crontab = sql.findById(CRONTAB_WITH_ALBUMS_AND_ACCOUNT_FETCHER, crontabId)
            ?: throw IllegalArgumentException("定时任务不存在: $crontabId")

        val syncFolder = Path(crontab.config.targetPath, crontab.config.syncFolder)
        log.info("同步文件夹路径: $syncFolder")

        // 获取云端资产列表
        val cloudAssets = mutableListOf<Asset>()
        crontab.albums.forEach { album ->
            log.info("获取相册 ${album.name} (ID=${album.id}) 的云端资产")
            // 由于使用了 CRONTAB_WITH_ALBUMS_AND_ACCOUNT_FETCHER，album 对象应该包含 accountId
            // 但为了确保，我们创建一个包含 accountId 的临时对象
            val albumWithAccountId = object : Album by album {
                override val accountId: Long = crontab.accountId
            }
            val assets = xiaoMiApi.fetchAllAssetsByAlbumId(albumWithAccountId)
            log.info("相册 ${album.name} 云端资产数量: ${assets.size}")
            cloudAssets.addAll(assets)
        }
        log.info("云端资产总数: ${cloudAssets.size}")

        // 获取同步文件夹中的实际文件列表
        val syncFolderAssets = mutableListOf<Asset>()
        val orphanFiles = mutableListOf<Path>() // 记录孤儿文件（在文件系统中存在但数据库中没有记录）
        
        crontab.albums.forEach { album ->
            val albumFolder = Path(syncFolder.toString(), album.name)
            log.info("检查相册文件夹: $albumFolder")
            if (albumFolder.exists() && albumFolder.isDirectory()) {
                val files = albumFolder.listDirectoryEntries()
                log.info("相册 ${album.name} 本地文件数量: ${files.size}")
                files.forEach { filePath ->
                    if (filePath.isRegularFile()) {
                        // 根据文件名查找对应的资产记录
                        val fileName = filePath.fileName.toString()
                        val asset = sql.createQuery(Asset::class) {
                            where(table.fileName eq fileName)
                            where(table.album.id eq album.id)
                            select(table)
                        }.execute().firstOrNull()
                        
                        if (asset != null) {
                            syncFolderAssets.add(asset)
                            log.debug("找到匹配的资产: ${asset.fileName}")
                        } else {
                            log.warn("文件 $fileName 在数据库中找不到对应的资产记录，标记为孤儿文件")
                            orphanFiles.add(filePath)
                        }
                    }
                }
            } else {
                log.info("相册文件夹不存在或不是目录: $albumFolder")
            }
        }
        
        // 清理孤儿文件
        if (orphanFiles.isNotEmpty()) {
            log.info("发现 ${orphanFiles.size} 个孤儿文件，开始清理...")
            orphanFiles.forEach { orphanFile ->
                try {
                    fileService.deleteFile(orphanFile)
                    log.info("已删除孤儿文件: ${orphanFile.fileName}")
                } catch (e: Exception) {
                    log.error("删除孤儿文件失败: ${orphanFile.fileName}", e)
                }
            }
        }
        
        log.info("同步文件夹资产总数: ${syncFolderAssets.size}")
        if (orphanFiles.isNotEmpty()) {
            log.info("已清理 ${orphanFiles.size} 个孤儿文件，这些文件对应的云端资产将被重新下载")
        }

        // 比较两个列表
        val cloudAssetMap = cloudAssets.associateBy { it.id }
        val syncAssetMap = syncFolderAssets.associateBy { it.id }

        // 新增：云端有但同步文件夹没有
        val added = cloudAssets.filter { it.id !in syncAssetMap }

        // 删除：同步文件夹有但云端没有
        val deleted = syncFolderAssets.filter { it.id !in cloudAssetMap }

        // 修改：云端和同步文件夹都有，但 sha1 或 dateTaken 不同
        val updated = cloudAssets.filter { cloudAsset ->
            val syncAsset = syncAssetMap[cloudAsset.id]
            syncAsset != null && (
                    cloudAsset.sha1 != syncAsset.sha1 ||
                            cloudAsset.dateTaken != syncAsset.dateTaken
                    )
        }

        log.info("同步变化检测完成：新增=${added.size}，删除=${deleted.size}，修改=${updated.size}")
        
        // 详细记录删除的资产
        if (deleted.isNotEmpty()) {
            log.info("检测到需要删除的资产:")
            deleted.forEach { asset ->
                log.info("  - ${asset.fileName} (ID=${asset.id})")
            }
        }

        return ChangeSummary(added, deleted, updated)
    }

    /**
     * 检测云端变化（原有的下载功能）
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

        // 获取定时任务配置（包含关联的相册和账号信息）
        val crontab = sql.findById(CRONTAB_WITH_ALBUMS_AND_ACCOUNT_FETCHER, crontabId)
            ?: throw IllegalArgumentException("定时任务不存在: $crontabId")

        // 记录同步模式
        log.info("同步模式: ${crontab.config.syncMode}")

        // 创建 CrontabHistory 记录（用于前端显示）
        val crontabHistory = crontabService.createCrontabHistory(crontab)
        
        // 创建同步记录
        val syncRecord = createSyncRecord(crontab)

        try {
            // 检测同步变化
            val changes = detectSyncChanges(crontabId)

            val syncFolder = Path(crontab.config.targetPath, crontab.config.syncFolder)

            // 处理新增（两种模式都需要处理）
            changes.addedAssets.forEach { asset ->
                try {
                    // 使用 asset.album.id 来查找相册
                    val album = crontab.albums.find { it.id == asset.album.id }
                        ?: throw IllegalStateException("找不到资产对应的相册: ${asset.album.id}")
                    
                    downloadToSync(asset, album, syncFolder, crontab.accountId)
                    recordSyncDetail(syncRecord.id, asset, album, SyncOperation.ADD, syncFolder, true, null)
                } catch (e: Exception) {
                    log.error("下载新增资产失败: ${asset.fileName}", e)
                    // 使用 asset.album.id 来查找相册
                    val album = crontab.albums.find { it.id == asset.album.id }
                    if (album != null) {
                        recordSyncDetail(syncRecord.id, asset, album, SyncOperation.ADD, syncFolder, false, e.message)
                    }
                }
            }

            // 根据同步模式决定是否处理删除和修改
            when (crontab.config.syncMode) {
                SyncMode.ADD_ONLY -> {
                    // 仅新增模式：不处理删除和修改操作
                    log.info("同步模式为 ADD_ONLY，跳过 ${changes.deletedAssets.size} 个删除操作和 ${changes.updatedAssets.size} 个修改操作")
                }
                
                SyncMode.SYNC_ALL_CHANGES -> {
                    // 同步所有变化模式：处理删除和修改操作
                    log.info("同步模式为 SYNC_ALL_CHANGES，处理所有变化：删除 ${changes.deletedAssets.size} 个，修改 ${changes.updatedAssets.size} 个")
                    
                    // 处理删除
                    changes.deletedAssets.forEach { asset ->
                        try {
                            val album = crontab.albums.find { it.id == asset.album.id }
                                ?: throw IllegalStateException("找不到资产对应的相册: ${asset.album.id}")
                            
                            deleteFromSync(asset, album, syncFolder)
                            recordSyncDetail(syncRecord.id, asset, album, SyncOperation.DELETE, syncFolder, true, null)
                        } catch (e: Exception) {
                            log.error("删除资产失败: ${asset.fileName}", e)
                            val album = crontab.albums.find { it.id == asset.album.id }
                            if (album != null) {
                                recordSyncDetail(syncRecord.id, asset, album, SyncOperation.DELETE, syncFolder, false, e.message)
                            }
                        }
                    }

                    // 处理修改
                    changes.updatedAssets.forEach { asset ->
                        try {
                            val album = crontab.albums.find { it.id == asset.album.id }
                                ?: throw IllegalStateException("找不到资产对应的相册: ${asset.album.id}")
                            
                            deleteFromSync(asset, album, syncFolder)
                            downloadToSync(asset, album, syncFolder, crontab.accountId)
                            recordSyncDetail(syncRecord.id, asset, album, SyncOperation.UPDATE, syncFolder, true, null)
                        } catch (e: Exception) {
                            log.error("更新资产失败: ${asset.fileName}", e)
                            val album = crontab.albums.find { it.id == asset.album.id }
                            if (album != null) {
                                recordSyncDetail(syncRecord.id, asset, album, SyncOperation.UPDATE, syncFolder, false, e.message)
                            }
                        }
                    }
                }
            }

            // 根据同步模式计算实际处理的数量
            val actualDeletedCount = when (crontab.config.syncMode) {
                SyncMode.ADD_ONLY -> 0
                SyncMode.SYNC_ALL_CHANGES -> changes.deletedAssets.size
            }
            
            val actualUpdatedCount = when (crontab.config.syncMode) {
                SyncMode.ADD_ONLY -> 0
                SyncMode.SYNC_ALL_CHANGES -> changes.updatedAssets.size
            }

            // 更新同步记录状态
            updateSyncRecord(
                syncRecord.id,
                SyncStatus.COMPLETED,
                changes.addedAssets.size,
                actualDeletedCount,
                actualUpdatedCount,
                null
            )

            // 完成 CrontabHistory 记录，并存储同步统计信息
            var archivedCount = 0
            
            // 同步完成后，检查是否需要自动归档
            log.info("检查自动归档条件：archiveMode=${crontab.config.archiveMode}")
            
            if (crontab.config.archiveMode != ArchiveMode.DISABLED) {
                log.info("同步完成后检查自动归档，归档模式=${crontab.config.archiveMode}")
                try {
                    // 预览归档计划
                    val archivePlan = archiveService.previewArchive(crontabId)
                    
                    if (archivePlan.assetsToArchive.isNotEmpty()) {
                        log.info("检测到需要归档的资产 ${archivePlan.assetsToArchive.size} 个，开始自动归档")
                        
                        // 直接执行归档，无需确认
                        val archiveRecordId = archiveService.executeArchive(crontabId, confirmed = true)
                        archivedCount = archivePlan.assetsToArchive.size
                        log.info("自动归档完成，归档记录 ID=$archiveRecordId，归档资产数量=$archivedCount")
                    } else {
                        log.info("没有需要归档的资产，跳过自动归档")
                    }
                } catch (e: Exception) {
                    // 归档失败不应该影响同步操作的成功状态
                    log.error("自动归档失败，但同步操作已成功完成", e)
                }
            } else {
                log.info("归档模式为 DISABLED，跳过自动归档检查。archiveMode=${crontab.config.archiveMode}")
            }
            
            // 存储同步统计信息（包含归档数量）
            val syncStats = mapOf(
                -1L to AlbumTimeline(
                    "SYNC:${crontab.config.syncMode.name}:${changes.addedAssets.size}:${actualDeletedCount}:${actualUpdatedCount}:${archivedCount}",
                    emptyMap()
                )
            )
            
            // 更新 CrontabHistory 的 timelineSnapshot 来存储同步统计信息
            sql.createUpdate(CrontabHistory::class) {
                set(table.endTime, Instant.now())
                set(table.timelineSnapshot, syncStats)
                where(table.id eq crontabHistory.id)
            }.execute()

            log.info("同步任务完成，同步记录 ID=${syncRecord.id}，模式=${crontab.config.syncMode}，新增=${changes.addedAssets.size}，删除=${actualDeletedCount}，修改=${actualUpdatedCount}，归档=${archivedCount}")

            return syncRecord.id
        } catch (e: Exception) {
            log.error("同步任务失败", e)
            updateSyncRecord(syncRecord.id, SyncStatus.FAILED, 0, 0, 0, e.message)
            
            // 完成 CrontabHistory 记录（即使失败也要完成）
            val syncStats = mapOf(
                -1L to AlbumTimeline(
                    "SYNC:${crontab.config.syncMode.name}:0:0:0:0:ERROR:${e.message ?: "未知错误"}",
                    emptyMap()
                )
            )
            
            // 更新 CrontabHistory 的 timelineSnapshot 来存储同步统计信息
            sql.createUpdate(CrontabHistory::class) {
                set(table.endTime, Instant.now())
                set(table.timelineSnapshot, syncStats)
                where(table.id eq crontabHistory.id)
            }.execute()
            
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
        val syncRecord = SyncRecord {
            this.crontabId = crontab.id  // 只设置 ID，不设置整个对象
            this.syncTime = Instant.now()
            this.addedCount = 0
            this.deletedCount = 0
            this.updatedCount = 0
            this.status = SyncStatus.RUNNING
            this.errorMessage = null
        }

        // 使用 INSERT_ONLY 模式
        val saveResult = sql.save(syncRecord, SaveMode.INSERT_ONLY)
        return saveResult.modifiedEntity
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
        album: Album,
        operation: SyncOperation,
        syncFolder: Path,
        isCompleted: Boolean,
        errorMessage: String?
    ) {
        val filePath = Path(syncFolder.toString(), album.name, asset.fileName).toString()

        val detail = SyncRecordDetail {
            this.syncRecordId = syncRecordId
            this.assetId = asset.id
            this.operation = operation
            this.filePath = filePath
            this.isCompleted = isCompleted
            this.errorMessage = errorMessage
        }

        // 使用 INSERT_ONLY 模式
        sql.save(detail, SaveMode.INSERT_ONLY)
    }

    /**
     * 下载到 sync 文件夹
     */
    private fun downloadToSync(asset: Asset, album: Album, syncFolder: Path, accountId: Long) {
        val targetPath = Path(syncFolder.toString(), album.name, asset.fileName)
        xiaoMiApi.downloadAsset(accountId, asset, targetPath)
        
        // 将 Asset 记录保存到数据库中，以便下次同步时能够找到
        sql.saveCommand(asset, SaveMode.UPSERT).execute()
        
        log.debug("下载到 sync 文件夹: ${asset.fileName}")
    }

    /**
     * 从 sync 文件夹删除
     */
    private fun deleteFromSync(asset: Asset, album: Album, syncFolder: Path) {
        val filePath = Path(syncFolder.toString(), album.name, asset.fileName)
        if (filePath.exists()) {
            fileService.deleteFile(filePath)
            log.debug("从 sync 文件夹删除: ${asset.fileName}")
        }
        
        // 从数据库中删除对应的 Asset 记录
        sql.deleteById(Asset::class, asset.id)
    }
}
