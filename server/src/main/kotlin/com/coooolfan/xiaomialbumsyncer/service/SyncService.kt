package com.coooolfan.xiaomialbumsyncer.service

import com.coooolfan.xiaomialbumsyncer.controller.CrontabController.Companion.CRONTAB_WITH_ALBUMS_FETCHER
import com.coooolfan.xiaomialbumsyncer.controller.CrontabController.Companion.CRONTAB_WITH_ALBUMS_AND_ACCOUNT_FETCHER
import com.coooolfan.xiaomialbumsyncer.model.*
import com.coooolfan.xiaomialbumsyncer.xiaomicloud.XiaoMiApi
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

    data class ChangeSummary(
        val addedAssets: List<Asset>,
        val deletedAssets: List<Asset>,
        val updatedAssets: List<Asset>
    )

    data class SyncStatusInfo(
        val isRunning: Boolean = false,
        val lastSyncTime: Instant? = null,
        val lastSyncResult: SyncStatus? = null
    )

    fun detectSyncChanges(crontabId: Long, crontabHistory: CrontabHistory? = null): ChangeSummary {
        val crontab = sql.findById(CRONTAB_WITH_ALBUMS_AND_ACCOUNT_FETCHER, crontabId)
            ?: throw IllegalArgumentException("定时任务不存在: $crontabId")

        val syncFolder = Path(crontab.config.targetPath, crontab.config.syncFolder)

        if (crontab.config.diffByTimeline && crontabHistory != null) {
            val albumTimelinesHistory = getHistoricalTimelineSnapshot(crontabId)
            assetService.refreshAssetsByDiffTimeline(crontab, crontabHistory, albumTimelinesHistory)
        } else {
            if (crontab.config.diffByTimeline) {
                log.warn("时间线差异对比模式已启用，但未提供 crontabHistory，回退到全量刷新")
            }
            if (crontabHistory != null) {
                assetService.refreshAssetsFull(crontab, crontabHistory)
            }
        }

        val cloudAssets = mutableListOf<Asset>()
        crontab.albums.forEach { album ->
            val albumWithAccountId = object : Album by album {
                override val accountId: Long = crontab.accountId
            }
            val assets = xiaoMiApi.fetchAllAssetsByAlbumId(albumWithAccountId)
            cloudAssets.addAll(assets)
        }
        
        val filteredCloudAssets = cloudAssets.filter { asset ->
            val includeImage = crontab.config.downloadImages || asset.type != AssetType.IMAGE
            val includeVideo = crontab.config.downloadVideos || asset.type != AssetType.VIDEO
            val includeAudio = crontab.config.downloadAudios || asset.type != AssetType.AUDIO
            includeImage && includeVideo && includeAudio
        }
        
        cloudAssets.clear()
        cloudAssets.addAll(filteredCloudAssets)

        val syncFolderAssets = mutableListOf<Asset>()
        val orphanFiles = mutableListOf<Path>()
        
        crontab.albums.forEach { album ->
            val albumFolder = Path(syncFolder.toString(), album.name)
            if (albumFolder.exists() && albumFolder.isDirectory()) {
                val files = albumFolder.listDirectoryEntries()
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
                        } else {
                            orphanFiles.add(filePath)
                        }
                    }
                }
            }
        }
        
        if (orphanFiles.isNotEmpty()) {
            orphanFiles.forEach { orphanFile ->
                try {
                    fileService.deleteFile(orphanFile)
                } catch (e: Exception) {
                    log.error("删除孤儿文件失败: ${orphanFile.fileName}", e)
                }
            }
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
        val deleted = localAssets.filter { it.id !in cloudAssetMap }
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

    private fun getHistoricalTimelineSnapshot(crontabId: Long): Map<Long, AlbumTimeline> {
        val lastSuccessfulHistory = sql.createQuery(CrontabHistory::class) {
            where(table.crontab.id eq crontabId)
            where(table.endTime ne null)
            orderBy(table.startTime.desc())
            select(table)
        }.limit(1).execute().firstOrNull()

        if (lastSuccessfulHistory == null) {
            return emptyMap()
        }

        val timelineSnapshot = lastSuccessfulHistory.timelineSnapshot
        if (timelineSnapshot == null || timelineSnapshot.isEmpty()) {
            return emptyMap()
        }
        
        return timelineSnapshot
    }

    private fun deleteFromSync(asset: Asset, album: Album, syncFolder: Path) {
        val filePath = Path(syncFolder.toString(), album.name, asset.fileName)
        if (filePath.exists()) {
            fileService.deleteFile(filePath)
        }
        
        sql.deleteById(Asset::class, asset.id)
    }

    private fun generateFilePath(
        asset: Asset,
        album: Album,
        syncFolder: Path,
        config: CrontabConfig
    ): Path {
        val expression = config.expressionTargetPath.trim().takeIf { it.isNotEmpty() }

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

        if (!containsSupportedInterpolation(expression, replacements.keys)) {
            return Path(syncFolder.toString(), album.name, asset.fileName)
        }

        val resolved = try {
            interpolateExpression(expression, replacements, downloadTime, takenTime, zoneId).trim()
        } catch (e: Exception) {
            return Path(syncFolder.toString(), album.name, asset.fileName)
        }

        if (resolved.isEmpty()) {
            return Path(syncFolder.toString(), album.name, asset.fileName)
        }

        return Path(syncFolder.toString(), resolved).normalize()
    }

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

    private fun formatInstant(instant: Instant, zoneId: ZoneId, pattern: String): String? {
        if (pattern.isBlank()) return null
        return runCatching {
            DateTimeFormatter.ofPattern(pattern).withLocale(Locale.ROOT).format(instant.atZone(zoneId))
        }.getOrNull()
    }

    private fun resolveZoneId(timeZone: String?): ZoneId {
        if (timeZone.isNullOrBlank()) {
            return ZoneId.systemDefault()
        }
        return runCatching { ZoneId.of(timeZone) }.getOrDefault(ZoneId.systemDefault())
    }

    private fun sanitizeSegment(value: String): String {
        if (value.isEmpty()) return value
        return value.replace(Regex("""[\\/:*?"<>|\r\n\t]"""), "_")
    }

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
