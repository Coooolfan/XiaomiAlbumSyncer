package com.coooolfan.xiaomialbumsyncer.service

import com.coooolfan.xiaomialbumsyncer.controller.CrontabController.Companion.CRONTAB_WITH_ALBUMS_AND_ACCOUNT_FETCHER
import com.coooolfan.xiaomialbumsyncer.model.*
import com.coooolfan.xiaomialbumsyncer.xiaomicloud.XiaoMiApi
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.desc
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.kt.ast.expression.valueIn
import org.noear.solon.annotation.Managed
import org.slf4j.LoggerFactory
import java.time.Instant

@Managed
class SyncService(
    private val sql: KSqlClient,
    private val xiaoMiApi: XiaoMiApi,
) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    /**
     * 待删除资产信息（包含实际下载路径和对应的 CrontabHistoryDetail ID）
     */
    data class DeletedAssetInfo(
        val asset: Asset,
        val filePath: String,
        val historyDetailIds: List<Long>
    )

    data class ChangeSummary(
        val addedAssets: List<Asset>,
        val deletedAssets: List<DeletedAssetInfo>,
        val updatedAssets: List<Asset>
    )

    data class SyncStatusInfo(
        val isRunning: Boolean = false,
        val lastSyncTime: Instant? = null,
        val lastSyncResult: SyncStatus? = null
    )

    /**
     * 检测云端变化（针对同步功能）
     *
     * 通过对比云端资产列表和数据库中已成功下载的记录（CrontabHistoryDetail）来检测变更。
     * 注意：调用此方法前，Pipeline 应已完成资产刷新（refreshAssetsFull / refreshAssetsByDiffTimeline）。
     *
     * @param crontabId 定时任务 ID
     * @return 变化摘要
     */
    fun detectSyncChanges(crontabId: Long): ChangeSummary {
        val crontab = sql.findById(CRONTAB_WITH_ALBUMS_AND_ACCOUNT_FETCHER, crontabId)
            ?: throw IllegalArgumentException("定时任务不存在: $crontabId")

        // 从云端获取当前资产列表
        val cloudAssets = mutableListOf<Asset>()
        crontab.albums.forEach { album ->
            val albumWithAccountId = object : Album by album {
                override val accountId: Long = crontab.accountId
            }
            cloudAssets.addAll(xiaoMiApi.fetchAllAssetsByAlbumId(albumWithAccountId))
        }

        // 应用文件类型过滤
        val filteredCloudAssets = cloudAssets.filter { asset ->
            val includeImage = crontab.config.downloadImages || asset.type != AssetType.IMAGE
            val includeVideo = crontab.config.downloadVideos || asset.type != AssetType.VIDEO
            val includeAudio = crontab.config.downloadAudios || asset.type != AssetType.AUDIO
            includeImage && includeVideo && includeAudio
        }

        cloudAssets.clear()
        cloudAssets.addAll(filteredCloudAssets)

        // 从数据库查询该定时任务已成功下载过的资产 ID 集合
        val downloadedAssetIds = sql.createQuery(CrontabHistoryDetail::class) {
            where(table.crontabHistory.crontabId eq crontabId)
            where(table.downloadCompleted eq true)
            where(table.sha1Verified eq true)
            where(table.exifFilled eq true)
            where(table.fsTimeUpdated eq true)
            select(table.asset.id)
        }.execute().toSet()

        // 从数据库查询已下载资产的详细信息（用于对比 SHA1 等）
        val downloadedAssets = if (downloadedAssetIds.isNotEmpty()) {
            sql.createQuery(Asset::class) {
                where(table.id valueIn downloadedAssetIds.toList())
                select(table)
            }.execute()
        } else {
            emptyList()
        }

        val cloudAssetMap = cloudAssets.associateBy { it.id }
        val downloadedAssetMap = downloadedAssets.associateBy { it.id }

        // 云端有但未成功下载过 → 新增
        val added = cloudAssets.filter { it.id !in downloadedAssetIds }

        // 查询已归档且云端已删除的资产 ID（这些不应该被当作"需要删除"）
        val archivedAndDeletedFromCloudIds = sql.createQuery(ArchiveDetail::class) {
            where(table.archiveRecord.crontabId eq crontabId)
            where(table.isDeletedFromCloud eq true)
            select(table.assetId)
        }.execute().toSet()

        // 已成功下载过但云端已不存在 → 删除（排除已归档的资产）
        val deleted = downloadedAssets
            .filter { it.id !in cloudAssetMap }
            .filter { it.id !in archivedAndDeletedFromCloudIds }
            .map { asset ->
                val details = sql.createQuery(CrontabHistoryDetail::class) {
                    where(table.crontabHistory.crontabId eq crontabId)
                    where(table.asset.id eq asset.id)
                    where(table.downloadCompleted eq true)
                    select(table)
                }.execute()
                // 取最新一条记录的文件路径
                val latestDetail = details.maxByOrNull { it.downloadTime }
                DeletedAssetInfo(
                    asset = asset,
                    filePath = latestDetail?.filePath ?: "",
                    historyDetailIds = details.map { it.id }
                )
            }

        // 云端和已下载都有但内容不同 → 修改
        val updated = cloudAssets.filter { cloudAsset ->
            val downloadedAsset = downloadedAssetMap[cloudAsset.id]
            downloadedAsset != null && (
                    cloudAsset.sha1 != downloadedAsset.sha1 ||
                            cloudAsset.dateTaken != downloadedAsset.dateTaken
                    )
        }

        return ChangeSummary(added, deleted, updated)
    }

    fun detectChanges(crontabId: Long): ChangeSummary {
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
        val deleted = localAssets.filter { it.id !in cloudAssetMap }.map { asset ->
            DeletedAssetInfo(asset = asset, filePath = "", historyDetailIds = emptyList())
        }
        val updated = cloudAssets.filter { cloudAsset ->
            val localAsset = localAssetMap[cloudAsset.id]
            localAsset != null && (
                    cloudAsset.sha1 != localAsset.sha1 ||
                            cloudAsset.dateTaken != localAsset.dateTaken
                    )
        }

        return ChangeSummary(added, deleted, updated)
    }

    fun getSyncStatus(crontabId: Long): SyncStatusInfo {
        // 查询最新的 CrontabHistory 记录
        val lastHistory = sql.createQuery(CrontabHistory::class) {
            where(table.crontab.id eq crontabId)
            orderBy(table.startTime.desc())
            select(table)
        }.limit(1).execute().firstOrNull()

        return if (lastHistory != null) {
            val isRunning = lastHistory.endTime == null
            val lastSyncResult = if (lastHistory.endTime != null) SyncStatus.COMPLETED else SyncStatus.RUNNING
            
            SyncStatusInfo(
                isRunning = isRunning,
                lastSyncTime = lastHistory.startTime,
                lastSyncResult = lastSyncResult
            )
        } else {
            SyncStatusInfo()
        }
    }

    /**
     * 创建同步记录
     */
    fun createSyncRecord(
        crontabId: Long,
        addedCount: Int,
        deletedCount: Int,
        updatedCount: Int
    ): SyncRecord {
        val record = SyncRecord {
            this.crontabId = crontabId
            this.syncTime = Instant.now()
            this.addedCount = addedCount
            this.deletedCount = deletedCount
            this.updatedCount = updatedCount
            this.status = SyncStatus.COMPLETED
        }
        return sql.save(record, SaveMode.INSERT_ONLY).modifiedEntity
    }
}