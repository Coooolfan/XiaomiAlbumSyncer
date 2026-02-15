package com.coooolfan.xiaomialbumsyncer.service

import com.coooolfan.xiaomialbumsyncer.controller.CrontabController.Companion.CRONTAB_WITH_ALBUMS_FETCHER
import com.coooolfan.xiaomialbumsyncer.controller.CrontabController.Companion.CRONTAB_WITH_ALBUMS_AND_ACCOUNT_FETCHER
import com.coooolfan.xiaomialbumsyncer.model.*
import com.coooolfan.xiaomialbumsyncer.pipeline.PostProcessingCoordinator
import com.coooolfan.xiaomialbumsyncer.xiaomicloud.XiaoMiApi
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.desc
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.kt.ast.expression.ne
import org.babyfish.jimmer.sql.kt.ast.expression.valueIn
import org.noear.solon.annotation.Inject
import org.noear.solon.annotation.Managed
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
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
    private val fileService: FileService,
    private val postProcessingCoordinator: PostProcessingCoordinator
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
     * @param crontabHistory 同步历史记录（用于时间线差异对比）
     * @return 变化摘要
     */
    fun detectSyncChanges(crontabId: Long, crontabHistory: CrontabHistory? = null): ChangeSummary {
        log.info("开始检测同步变化，定时任务 ID=$crontabId")

        val crontab = sql.findById(CRONTAB_WITH_ALBUMS_AND_ACCOUNT_FETCHER, crontabId)
            ?: throw IllegalArgumentException("定时任务不存在: $crontabId")

        val syncFolder = Path(crontab.config.targetPath, crontab.config.syncFolder)
        log.info("同步文件夹路径: $syncFolder")

        // 时间线差异对比优化
        if (crontab.config.diffByTimeline && crontabHistory != null) {
            log.info("使用时间线差异对比模式刷新资产数据")
            
            // 获取历史时间线快照
            val albumTimelinesHistory = getHistoricalTimelineSnapshot(crontabId)
            
            // 使用时间线差异对比刷新资产
            assetService.refreshAssetsByDiffTimeline(crontab, crontabHistory, albumTimelinesHistory)
        } else {
            if (crontab.config.diffByTimeline) {
                log.warn("时间线差异对比模式已启用，但未提供 crontabHistory，回退到全量刷新")
            } else {
                log.info("使用全量刷新模式刷新资产数据")
            }
            
            // 全量刷新资产（如果提供了 crontabHistory）
            if (crontabHistory != null) {
                assetService.refreshAssetsFull(crontab, crontabHistory)
            }
        }

        val cloudAssets = mutableListOf<Asset>()
        crontab.albums.forEach { album ->
            log.info("获取相册 ${album.name} (ID=${album.id}) 的云端资产")
            val albumWithAccountId = object : Album by album {
                override val accountId: Long = crontab.accountId
            }
            val assets = xiaoMiApi.fetchAllAssetsByAlbumId(albumWithAccountId)
            log.info("相册 ${album.name} 云端资产数量: ${assets.size}")
            cloudAssets.addAll(assets)
        }
        log.info("云端资产总数: ${cloudAssets.size}")
        
        // 应用文件类型过滤
        val filteredCloudAssets = cloudAssets.filter { asset ->
            val includeImage = crontab.config.downloadImages || asset.type != AssetType.IMAGE
            val includeVideo = crontab.config.downloadVideos || asset.type != AssetType.VIDEO
            val includeAudio = crontab.config.downloadAudios || asset.type != AssetType.AUDIO
            includeImage && includeVideo && includeAudio
        }
        
        val filteredCount = cloudAssets.size - filteredCloudAssets.size
        if (filteredCount > 0) {
            log.info("文件类型过滤：过滤掉 $filteredCount 个资产（downloadImages=${crontab.config.downloadImages}, downloadVideos=${crontab.config.downloadVideos}, downloadAudios=${crontab.config.downloadAudios}）")
        }
        
        // 使用过滤后的资产列表替换原始列表
        cloudAssets.clear()
        cloudAssets.addAll(filteredCloudAssets)

        val syncFolderAssets = mutableListOf<Asset>()
        val orphanFiles = mutableListOf<Path>()
        
        crontab.albums.forEach { album ->
            val albumFolder = Path(syncFolder.toString(), album.name)
            log.info("检查相册文件夹: $albumFolder")
            if (albumFolder.exists() && albumFolder.isDirectory()) {
                val files = albumFolder.listDirectoryEntries()
                log.info("相册 ${album.name} 本地文件数量: ${files.size}")
                files.forEach { filePath ->
                    if (filePath.isRegularFile()) {
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

        val cloudAssetMap = cloudAssets.associateBy { it.id }
        val syncAssetMap = syncFolderAssets.associateBy { it.id }

        val added = cloudAssets.filter { it.id !in syncAssetMap }

        val deleted = syncFolderAssets.filter { it.id !in cloudAssetMap }

        val updated = cloudAssets.filter { cloudAsset ->
            val syncAsset = syncAssetMap[cloudAsset.id]
            syncAsset != null && (
                    cloudAsset.sha1 != syncAsset.sha1 ||
                            cloudAsset.dateTaken != syncAsset.dateTaken
                    )
        }

        log.info("同步变化检测完成：新增=${added.size}，删除=${deleted.size}，修改=${updated.size}")
        
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

        val crontab = sql.findById(Crontab::class, crontabId)
            ?: throw IllegalArgumentException("定时任务不存在: $crontabId")

        val cloudAssets = mutableListOf<Asset>()
        crontab.albums.forEach { album ->
            val assets = xiaoMiApi.fetchAllAssetsByAlbumId(album)
            cloudAssets.addAll(assets)
        }

        val localAssets = sql.createQuery(Asset::class) {
            where(table.album.id valueIn crontab.albums.map { it.id })
            select(table)
        }.execute()

        val cloudAssetMap = cloudAssets.associateBy { it.id }
        val localAssetMap = localAssets.associateBy { it.id }

        val added = cloudAssets.filter { it.id !in localAssetMap }

        val deleted = localAssets.filter { it.id !in cloudAssetMap }

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
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun executeSync(crontabId: Long): Long {
        val startTime = Instant.now()
        log.info("========== 开始执行同步任务 ==========")
        log.info("定时任务 ID=$crontabId")

        val crontab = sql.findById(CRONTAB_WITH_ALBUMS_AND_ACCOUNT_FETCHER, crontabId)
            ?: throw IllegalArgumentException("定时任务不存在: $crontabId")

        log.info("同步配置：")
        log.info("  - 同步模式: ${crontab.config.syncMode}")
        log.info("  - 文件类型过滤: 图片=${crontab.config.downloadImages}, 视频=${crontab.config.downloadVideos}, 音频=${crontab.config.downloadAudios}")
        log.info("  - 跳过已存在文件: ${crontab.config.skipExistingFile}")
        log.info("  - 自定义路径表达式: ${if (crontab.config.expressionTargetPath.isNotEmpty()) crontab.config.expressionTargetPath else "未配置（使用默认路径）"}")
        log.info("  - 并发下载数: ${crontab.config.downloaders}")
        log.info("  - 时间线差异对比: ${crontab.config.diffByTimeline}")
        log.info("  - 归档模式: ${crontab.config.archiveMode}")

        val crontabHistory = crontabService.createCrontabHistory(crontab)
        
        val syncRecord = createSyncRecord(crontab)

        try {
            val changes = detectSyncChanges(crontabId, crontabHistory)

            val syncFolder = Path(crontab.config.targetPath, crontab.config.syncFolder)

            // 并发处理 ADD 操作
            changes.addedAssets
                .asFlow()
                .flatMapMerge(crontab.config.downloaders) { asset ->
                    flow {
                        try {
                            val album = crontab.albums.find { it.id == asset.album.id }
                                ?: throw IllegalStateException("找不到资产对应的相册: ${asset.album.id}")
                            
                            // 使用自定义路径表达式生成文件路径
                            val filePath = generateFilePath(asset, album, syncFolder, crontab.config)
                            
                            // 跳过已存在文件检查
                            if (crontab.config.skipExistingFile && fileService.fileExists(filePath)) {
                                val fileSize = fileService.getFileSize(filePath)
                                if (fileSize == asset.size) {
                                    log.info("文件已存在且大小匹配，跳过下载: ${asset.fileName} (大小=${fileSize})")
                                    recordSyncDetail(
                                        syncRecord.id,
                                        asset,
                                        album,
                                        SyncOperation.ADD,
                                        syncFolder,
                                        true,
                                        "跳过下载：文件已存在且大小匹配"
                                    )
                                    emit(Unit)
                                    return@flow
                                } else {
                                    log.info("文件已存在但大小不匹配，重新下载: ${asset.fileName} (本地=${fileSize}, 云端=${asset.size})")
                                }
                            }
                            
                            // 下载文件（使用生成的路径）
                            xiaoMiApi.downloadAsset(crontab.accountId, asset, filePath)
                            sql.saveCommand(asset, SaveMode.UPSERT).execute()
                            log.debug("下载到 sync 文件夹: ${asset.fileName} -> $filePath")
                            
                            // 执行后处理
                            val postProcessingResult = postProcessingCoordinator.process(
                                asset, 
                                filePath, 
                                crontab.config
                            )
                            
                            // 记录同步详情
                            recordSyncDetail(
                                syncRecord.id, 
                                asset, 
                                album, 
                                SyncOperation.ADD, 
                                syncFolder, 
                                postProcessingResult.success, 
                                postProcessingResult.errorMessage
                            )
                            
                            emit(Unit)
                        } catch (e: Exception) {
                            log.error("处理新增资产失败: ${asset.fileName}", e)
                            val album = crontab.albums.find { it.id == asset.album.id }
                            if (album != null) {
                                recordSyncDetail(
                                    syncRecord.id, 
                                    asset, 
                                    album, 
                                    SyncOperation.ADD, 
                                    syncFolder, 
                                    false, 
                                    e.message
                                )
                            }
                            // 错误隔离：不抛出异常，继续处理其他资产
                            emit(Unit)
                        }
                    }
                }
                .collect()

            when (crontab.config.syncMode) {
                SyncMode.ADD_ONLY -> {
                    log.info("同步模式为 ADD_ONLY，跳过 ${changes.deletedAssets.size} 个删除操作和 ${changes.updatedAssets.size} 个修改操作")
                }
                
                SyncMode.SYNC_ALL_CHANGES -> {
                    log.info("同步模式为 SYNC_ALL_CHANGES，处理所有变化：删除 ${changes.deletedAssets.size} 个，修改 ${changes.updatedAssets.size} 个")
                    
                    // 处理 DELETE 操作
                    // 注意：DELETE 操作不执行后处理（SHA1 校验、EXIF 时间填充、文件系统时间更新）
                    // 原因：
                    // 1. 文件已被删除，无法进行后处理操作
                    // 2. 后处理步骤（SHA1 校验、EXIF 填充、文件时间更新）仅适用于存在的文件
                    // 3. DELETE 操作的成功与否仅取决于文件删除是否成功
                    // 参考需求：7.1, 7.2, 7.3
                    changes.deletedAssets.forEach { asset ->
                        try {
                            val album = crontab.albums.find { it.id == asset.album.id }
                                ?: throw IllegalStateException("找不到资产对应的相册: ${asset.album.id}")
                            
                            // 仅执行文件删除操作，不调用 postProcessingCoordinator.process()
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

                    // 并发处理 UPDATE 操作
                    changes.updatedAssets
                        .asFlow()
                        .flatMapMerge(crontab.config.downloaders) { asset ->
                            flow {
                                try {
                                    val album = crontab.albums.find { it.id == asset.album.id }
                                        ?: throw IllegalStateException("找不到资产对应的相册: ${asset.album.id}")
                                    
                                    // 使用自定义路径表达式生成文件路径
                                    val filePath = generateFilePath(asset, album, syncFolder, crontab.config)
                                    
                                    // 跳过已存在文件检查（UPDATE 操作）
                                    // 注意：UPDATE 操作表示云端文件已修改（SHA1 或 dateTaken 不同）
                                    // 但如果本地文件大小与云端匹配，可能是误判或已经是最新版本
                                    if (crontab.config.skipExistingFile && fileService.fileExists(filePath)) {
                                        val fileSize = fileService.getFileSize(filePath)
                                        if (fileSize == asset.size) {
                                            log.info("UPDATE 操作：文件已存在且大小匹配，跳过下载: ${asset.fileName} (大小=${fileSize})")
                                            recordSyncDetail(
                                                syncRecord.id,
                                                asset,
                                                album,
                                                SyncOperation.UPDATE,
                                                syncFolder,
                                                true,
                                                "跳过下载：文件已存在且大小匹配"
                                            )
                                            emit(Unit)
                                            return@flow
                                        } else {
                                            log.info("UPDATE 操作：文件已存在但大小不匹配，重新下载: ${asset.fileName} (本地=${fileSize}, 云端=${asset.size})")
                                        }
                                    }
                                    
                                    // 删除旧文件（如果存在）
                                    if (fileService.fileExists(filePath)) {
                                        fileService.deleteFile(filePath)
                                        log.debug("删除旧文件: $filePath")
                                    }
                                    
                                    // 下载新文件（使用生成的路径）
                                    xiaoMiApi.downloadAsset(crontab.accountId, asset, filePath)
                                    sql.saveCommand(asset, SaveMode.UPSERT).execute()
                                    log.debug("下载到 sync 文件夹: ${asset.fileName} -> $filePath")
                                    
                                    // 执行后处理
                                    val postProcessingResult = postProcessingCoordinator.process(
                                        asset, 
                                        filePath, 
                                        crontab.config
                                    )
                                    
                                    // 记录同步详情
                                    recordSyncDetail(
                                        syncRecord.id, 
                                        asset, 
                                        album, 
                                        SyncOperation.UPDATE, 
                                        syncFolder, 
                                        postProcessingResult.success, 
                                        postProcessingResult.errorMessage
                                    )
                                    
                                    emit(Unit)
                                } catch (e: Exception) {
                                    log.error("更新资产失败: ${asset.fileName}", e)
                                    val album = crontab.albums.find { it.id == asset.album.id }
                                    if (album != null) {
                                        recordSyncDetail(
                                            syncRecord.id, 
                                            asset, 
                                            album, 
                                            SyncOperation.UPDATE, 
                                            syncFolder, 
                                            false, 
                                            e.message
                                        )
                                    }
                                    // 错误隔离：不抛出异常，继续处理其他资产
                                    emit(Unit)
                                }
                            }
                        }
                        .collect()
                }
            }

            val actualDeletedCount = when (crontab.config.syncMode) {
                SyncMode.ADD_ONLY -> 0
                SyncMode.SYNC_ALL_CHANGES -> changes.deletedAssets.size
            }
            
            val actualUpdatedCount = when (crontab.config.syncMode) {
                SyncMode.ADD_ONLY -> 0
                SyncMode.SYNC_ALL_CHANGES -> changes.updatedAssets.size
            }

            updateSyncRecord(
                syncRecord.id,
                SyncStatus.COMPLETED,
                changes.addedAssets.size,
                actualDeletedCount,
                actualUpdatedCount,
                null
            )

            var archivedCount = 0
            
            log.info("检查自动归档条件：archiveMode=${crontab.config.archiveMode}")
            
            if (crontab.config.archiveMode != ArchiveMode.DISABLED) {
                log.info("同步完成后检查自动归档，归档模式=${crontab.config.archiveMode}")
                try {
                    val archivePlan = archiveService.previewArchive(crontabId)
                    
                    if (archivePlan.assetsToArchive.isNotEmpty()) {
                        log.info("检测到需要归档的资产 ${archivePlan.assetsToArchive.size} 个，开始自动归档")
                        
                        val archiveRecordId = archiveService.executeArchive(crontabId, confirmed = true)
                        archivedCount = archivePlan.assetsToArchive.size
                        log.info("自动归档完成，归档记录 ID=$archiveRecordId，归档资产数量=$archivedCount")
                    } else {
                        log.info("没有需要归档的资产，跳过自动归档")
                    }
                } catch (e: Exception) {
                    log.error("自动归档失败，但同步操作已成功完成", e)
                }
            } else {
                log.info("归档模式为 DISABLED，跳过自动归档检查。archiveMode=${crontab.config.archiveMode}")
            }
            
            val syncStats = mapOf(
                -1L to AlbumTimeline(
                    "SYNC:${crontab.config.syncMode.name}:${changes.addedAssets.size}:${actualDeletedCount}:${actualUpdatedCount}:${archivedCount}",
                    emptyMap()
                )
            )
            
            sql.createUpdate(CrontabHistory::class) {
                set(table.endTime, Instant.now())
                set(table.timelineSnapshot, syncStats)
                where(table.id eq crontabHistory.id)
            }.execute()

            val endTime = Instant.now()
            val duration = endTime.toEpochMilli() - startTime.toEpochMilli()
            
            log.info("========== 同步任务完成 ==========")
            log.info("同步记录 ID: ${syncRecord.id}")
            log.info("同步模式: ${crontab.config.syncMode}")
            log.info("处理结果:")
            log.info("  - 新增: ${changes.addedAssets.size} 个文件")
            log.info("  - 删除: ${actualDeletedCount} 个文件")
            log.info("  - 修改: ${actualUpdatedCount} 个文件")
            log.info("  - 归档: ${archivedCount} 个文件")
            log.info("总耗时: ${duration} ms (${duration / 1000.0} 秒)")
            log.info("=====================================")

            return syncRecord.id
        } catch (e: Exception) {
            val endTime = Instant.now()
            val duration = endTime.toEpochMilli() - startTime.toEpochMilli()
            
            log.error("========== 同步任务失败 ==========")
            log.error("定时任务 ID: $crontabId")
            log.error("错误信息: ${e.message}")
            log.error("耗时: ${duration} ms (${duration / 1000.0} 秒)")
            log.error("=====================================", e)
            
            updateSyncRecord(syncRecord.id, SyncStatus.FAILED, 0, 0, 0, e.message)
            
            val syncStats = mapOf(
                -1L to AlbumTimeline(
                    "SYNC:${crontab.config.syncMode.name}:0:0:0:0:ERROR:${e.message ?: "未知错误"}",
                    emptyMap()
                )
            )
            
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

        sql.save(detail, SaveMode.INSERT_ONLY)
    }

    /**
     * 下载到 sync 文件夹
     */
    private fun downloadToSync(asset: Asset, album: Album, syncFolder: Path, accountId: Long) {
        val targetPath = Path(syncFolder.toString(), album.name, asset.fileName)
        xiaoMiApi.downloadAsset(accountId, asset, targetPath)
        
        sql.saveCommand(asset, SaveMode.UPSERT).execute()
        
        log.debug("下载到 sync 文件夹: ${asset.fileName}")
    }

    /**
     * 获取历史时间线快照
     * @param crontabId 定时任务 ID
     * @return 历史时间线快照（相册 remoteId -> AlbumTimeline）
     */
    private fun getHistoricalTimelineSnapshot(crontabId: Long): Map<Long, AlbumTimeline> {
        // 获取最近一次成功的同步记录
        val lastSuccessfulHistory = sql.createQuery(CrontabHistory::class) {
            where(table.crontab.id eq crontabId)
            where(table.endTime ne null)
            orderBy(table.startTime.desc())
            select(table)
        }.limit(1).execute().firstOrNull()

        if (lastSuccessfulHistory == null) {
            log.info("没有找到历史时间线快照，使用空时间线作为基准")
            return emptyMap()
        }

        val timelineSnapshot = lastSuccessfulHistory.timelineSnapshot
        if (timelineSnapshot == null || timelineSnapshot.isEmpty()) {
            log.info("历史时间线快照为空，使用空时间线作为基准")
            return emptyMap()
        }
        
        log.info("找到历史时间线快照，包含 ${timelineSnapshot.size} 个相册")
        
        return timelineSnapshot
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
        
        sql.deleteById(Asset::class, asset.id)
    }

    /**
     * 生成文件路径（支持自定义路径表达式）
     * @param asset 资产
     * @param album 相册
     * @param syncFolder 同步文件夹
     * @param config 配置
     * @return 文件路径
     */
    private fun generateFilePath(
        asset: Asset,
        album: Album,
        syncFolder: Path,
        config: CrontabConfig
    ): Path {
        val expression = config.expressionTargetPath.trim().takeIf { it.isNotEmpty() }

        // 如果没有配置表达式，使用默认路径
        if (expression == null) {
            return Path(syncFolder.toString(), album.name, asset.fileName)
        }

        val zoneId = resolveZoneId(config.timeZone)
        val downloadTime = Instant.now()
        val takenTime = asset.dateTaken

        val fileNameRaw = asset.fileName
        val fileName = fileNameRaw
        val fileNameSafe = sanitizeSegment(fileName)
        val fileStem = fileNameSafe.substringBeforeLast('.', fileNameSafe)
        val fileExt = fileNameRaw.substringAfterLast('.', "")

        val replacements = buildMap {
            put("album", sanitizeSegment(album.name))
            put("albumName", sanitizeSegment(album.name))
            put("fileName", fileNameSafe)
            put("fileStem", fileStem)
            put("fileExt", fileExt)
            put("assetId", asset.id.toString())
            put("assetType", asset.type.name.lowercase(Locale.ROOT))
            put("sha1", asset.sha1)
            put("title", sanitizeSegment(asset.title))
            put("size", asset.size.toString())
            put("downloadEpochMillis", downloadTime.toEpochMilli().toString())
            put("takenEpochMillis", takenTime.toEpochMilli().toString())
            put("downloadEpochSeconds", downloadTime.epochSecond.toString())
            put("takenEpochSeconds", takenTime.epochSecond.toString())
        }

        // 检查表达式是否包含支持的插值
        if (!containsSupportedInterpolation(expression, replacements.keys)) {
            log.warn("路径表达式不包含支持的变量，回退到默认路径: $expression")
            return Path(syncFolder.toString(), album.name, asset.fileName)
        }

        // 解析表达式
        val resolved = try {
            interpolateExpression(expression, replacements, downloadTime, takenTime, zoneId).trim()
        } catch (e: Exception) {
            log.warn("路径表达式解析失败，回退到默认路径: $expression", e)
            return Path(syncFolder.toString(), album.name, asset.fileName)
        }

        if (resolved.isEmpty()) {
            log.warn("路径表达式解析结果为空，回退到默认路径: $expression")
            return Path(syncFolder.toString(), album.name, asset.fileName)
        }

        // 返回相对于 syncFolder 的完整路径
        return Path(syncFolder.toString(), resolved).normalize()
    }

    /**
     * 替换表达式中的变量
     */
    private fun interpolateExpression(
        template: String,
        values: Map<String, String>,
        downloadTime: Instant,
        takenTime: Instant,
        zoneId: ZoneId,
    ): String {
        val tokenRegex = Regex("""\$\{([^}]+)}""")
        return tokenRegex.replace(template) { match ->
            val key = match.groupValues[1]
            when {
                key.startsWith("download_") -> {
                    val pattern = key.removePrefix("download_")
                    formatInstant(downloadTime, zoneId, pattern) ?: match.value
                }

                key.startsWith("taken_") -> {
                    val pattern = key.removePrefix("taken_")
                    formatInstant(takenTime, zoneId, pattern) ?: match.value
                }

                else -> values[key] ?: match.value
            }
        }
    }

    /**
     * 格式化时间
     */
    private fun formatInstant(instant: Instant, zoneId: ZoneId, pattern: String): String? {
        if (pattern.isBlank()) return null
        return runCatching {
            DateTimeFormatter.ofPattern(pattern).withLocale(Locale.ROOT).format(instant.atZone(zoneId))
        }.getOrNull()
    }

    /**
     * 解析时区
     */
    private fun resolveZoneId(timeZone: String?): ZoneId {
        if (timeZone.isNullOrBlank()) {
            return ZoneId.systemDefault()
        }
        return runCatching { ZoneId.of(timeZone) }.getOrDefault(ZoneId.systemDefault())
    }

    /**
     * 清理路径片段中的非法字符
     */
    private fun sanitizeSegment(value: String): String {
        if (value.isEmpty()) return value
        return value.replace(Regex("""[\\/:*?"<>|\r\n\t]"""), "_")
    }

    /**
     * 检查表达式是否包含支持的插值
     */
    private fun containsSupportedInterpolation(template: String, supportedKeys: Set<String>): Boolean {
        val tokenRegex = Regex("""\$\{([^}]+)}""")
        return tokenRegex.findAll(template).any { match ->
            val key = match.groupValues[1]
            key in supportedKeys ||
                    key.startsWith("download_") ||
                    key.startsWith("taken_")
        }
    }
}
