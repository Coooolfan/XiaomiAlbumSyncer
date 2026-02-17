package com.coooolfan.xiaomialbumsyncer.pipeline

import com.coooolfan.xiaomialbumsyncer.controller.SystemConfigController.Companion.NORMAL_SYSTEM_CONFIG
import com.coooolfan.xiaomialbumsyncer.model.*
import com.coooolfan.xiaomialbumsyncer.pipeline.stages.DownloadStage
import com.coooolfan.xiaomialbumsyncer.pipeline.stages.ExifProcessingStage
import com.coooolfan.xiaomialbumsyncer.pipeline.stages.FileTimeStage
import com.coooolfan.xiaomialbumsyncer.pipeline.stages.VerificationStage
import com.coooolfan.xiaomialbumsyncer.service.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.noear.solon.annotation.Managed
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

/**
 * 计划任务流水线管理器
 */
@Managed
class CrontabPipeline(
    private val downloadStage: DownloadStage,
    private val verificationStage: VerificationStage,
    private val exifProcessingStage: ExifProcessingStage,
    private val fileTimeStage: FileTimeStage,
    private val systemConfigService: SystemConfigService,
    private val assetService: AssetService,
    private val crontabService: CrontabService,
    private val notifyService: NotifyService,
    private val syncService: SyncService,
    private val fileService: FileService,
    private val postProcessingCoordinator: PostProcessingCoordinator,
    private val xiaoMiApi: com.coooolfan.xiaomialbumsyncer.xiaomicloud.XiaoMiApi,
    private val archiveService: ArchiveService,
) {

    private val log = LoggerFactory.getLogger(CrontabPipeline::class.java)

    // 从 Crontab 创建一个任务
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun execute(
        crontab: Crontab,
    ) {
        // 对资产的刷新操作作为独立步骤执行，不混入后续的并发流，避免状态管理复杂化
        val crontabHistory = crontabService.createCrontabHistory(crontab)

        // 对 crontab.albums 进行同步操作, 重新刷新这些相册的所有 Asset 取到上次的 CrontabHistory 的 timelineSnapshot
        val albumTimelinesHistory = crontabService.getAlbumTimelinesHistory(crontabHistory)

        // 仅在上次有记录且相册列表未变更的情况下，才使用时间线对比模式
        // 理论上相册列表变动不影响逻辑正确，但是会导致实际发起的查询大于请求完整刷新模式，所以还是要求相册列表一致
        val timelineDiffUsable = checkTimelineDiffUsable(crontab, albumTimelinesHistory)
        if (timelineDiffUsable != null) {
            if (crontab.config.diffByTimeline) log.warn("时间线对比模式不可用，原因：$timelineDiffUsable，将使用完整刷新模式")
            assetService.refreshAssetsFull(crontab, crontabHistory)
        } else {
            log.info("时间线对比模式可用，仅对有变更的日期进行刷新")
            assetService.refreshAssetsByDiffTimeline(crontab, crontabHistory, albumTimelinesHistory)
        }

        // 记录一下，以后如果支持恢复暂停的任务可以从这开始
        crontabService.finishCrontabHistoryFetchedAllAssets(crontabHistory)

        // 检测变更（新增、修改、删除）
        val changes = syncService.detectSyncChanges(crontab.id)
        log.info("检测到变更：新增 ${changes.addedAssets.size}，修改 ${changes.updatedAssets.size}，删除 ${changes.deletedAssets.size}")

        val systemConfig = systemConfigService.getConfig(NORMAL_SYSTEM_CONFIG)
        val syncFolder = Path.of(crontab.config.targetPath, crontab.config.syncFolder)

        var total = 0
        var success = 0
        var deletedCount = 0
        var updatedCount = 0

        // 根据 syncMode 决定
        when (crontab.config.syncMode) {
            SyncMode.ADD_ONLY -> {
                log.info("仅新增模式：跳过删除和修改操作")
            }
            SyncMode.SYNC_ALL_CHANGES -> {
                changes.deletedAssets.forEach { deletedInfo ->
                    try {
                        val filePath = Path.of(deletedInfo.filePath)
                        if (filePath.exists()) {
                            fileService.deleteFile(filePath)
                            log.info("删除文件：${deletedInfo.asset.fileName}，路径：${deletedInfo.filePath}")
                        } else {
                            log.warn("文件不存在，跳过删除：${deletedInfo.filePath}")
                        }
                        // 删除 Asset 记录（会级联删除关联的 CrontabHistoryDetail），
                        // 避免 getAssetsUndownloadByCrontab 将其当作未下载资产重新处理
                        assetService.deleteAsset(deletedInfo.asset.id)
                        deletedCount++
                    } catch (e: Exception) {
                        log.error("删除资产失败: ${deletedInfo.asset.fileName}", e)
                    }
                }

                changes.updatedAssets.forEach { asset ->
                    try {
                        val album = crontab.albums.find { it.id == asset.album.id }
                            ?: throw IllegalStateException("找不到资产对应的相册: ${asset.album.id}")
                        
                        val filePath = Path.of(syncFolder.toString(), album.name, asset.fileName)
                        
                        if (fileService.fileExists(filePath)) {
                            fileService.deleteFile(filePath)
                        }
                        
                        xiaoMiApi.downloadAsset(crontab.accountId, asset, filePath)
                        
                        val postProcessingResult = postProcessingCoordinator.process(
                            asset,
                            filePath,
                            crontab.config
                        )
                        
                        if (postProcessingResult.success) {
                            updatedCount++
                            log.info("更新文件：${asset.fileName}")
                        } else {
                            log.error("更新文件后处理失败：${asset.fileName}, ${postProcessingResult.errorMessage}")
                        }
                    } catch (e: Exception) {
                        log.error("更新资产失败: ${asset.fileName}", e)
                    }
                }
            }
        }

        channelFlow {
            var currentRows: Int
            var lastId = 0L
            val pageSize = crontab.config.fetchFromDbSize

            do {
                // 1. 查询需要下载的资产(已经从远程同步好了本地数据库中的资产)
                val assets = assetService.getAssetsUndownloadByCrontab(
                    crontabHistory.crontab,
                    pageSize,
                    lastId
                )

                val details = mutableListOf<CrontabHistoryDetail>()

                assets.forEach { asset ->
                    val detail = CrontabHistoryDetail.init(crontabHistory, asset)
                    details.add(detail)
                }

                // 2. 保存 CrontabHistoryDetail，落库
                val details2Emit = crontabService.insertCrontabHistoryDetails(details)

                currentRows = assets.size
                if (currentRows != 0) lastId = assets.last().id

                details2Emit.forEach { send(it) }
            } while (currentRows > 0)
        }.onEach {
            total++
        }.flatMapMerge(crontab.config.downloaders) { context ->
            flow {
                emit(downloadStage.process(context))
            }.catch {
                log.error("资源 {} 的下载失败, 将跳过后续处理", context.asset.id, it)
            }
        }.flatMapMerge(crontab.config.verifiers) { context ->
            flow {
                emit(verificationStage.process(context))
            }.catch {
                log.error("资源 {} 的校验失败, 将跳过后续处理", context.asset.id, it)
            }
        }.flatMapMerge(crontab.config.exifProcessors) { context ->
            flow {
                emit(exifProcessingStage.process(context, systemConfig))
            }.catch {
                log.error("资源 {} 的 EXIF 处理失败, 将跳过后续处理", context.asset.id, it)
            }
        }.flatMapMerge(crontab.config.fileTimeWorkers) { context ->
            flow {
                emit(fileTimeStage.process(context))
            }.catch {
                log.error("资源 {} 的文件时间阶段处理失败, 将跳过后续处理", context.asset.id, it)
            }
        }.onEach { context ->
            success++
            log.info("资源 {} 处理完成", context.asset.id)
        }.collect()

        crontabService.finishCrontabHistory(crontabHistory)

        // 同步完成后，如果启用了归档，自动执行归档
        if (crontab.config.archiveMode != ArchiveMode.DISABLED) {
            try {
                log.info("开始自动归档，模式: ${crontab.config.archiveMode}")
                val archiveRecordId = archiveService.executeArchive(crontab.id, true)
                log.info("自动归档完成，归档记录 ID=$archiveRecordId")
            } catch (e: Exception) {
                log.warn("自动归档跳过或失败: ${e.message}")
            }
        }

        // 异步发送通知
        CoroutineScope(Dispatchers.IO).launch { notifyService.send(crontab, success, total) }

        log.info(
            "Crontab {} 的流水线执行完毕, 新增成功 {}/{}, 删除 {}, 更新 {}",
            crontab.id, success, total, deletedCount, updatedCount
        )
    }


    private fun checkTimelineDiffUsable(crontab: Crontab, albumTimelinesHistory: Map<Long, AlbumTimeline>): String? {
        if (!crontab.config.diffByTimeline) {
            return "时间线对比模式未打开"
        }
        if (albumTimelinesHistory.isEmpty()) {
            return "该任务的最新执行记录无可用于对比的时间线数据"
        }

        val crontabAlbumRemoteIds = crontab.albums.mapTo(mutableSetOf()) { it.remoteId }
        if (albumTimelinesHistory.keys != crontabAlbumRemoteIds) {
            return "相册列表有变更"
        }
        if (crontabAlbumRemoteIds.contains(-1L)) {
            return "\"录音\"不支持时间线对比"
        }
        return null
    }
}
