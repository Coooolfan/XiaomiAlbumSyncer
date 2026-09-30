package com.coooolfan.xiaomialbumsyncer.service

import com.coooolfan.xiaomialbumsyncer.icloud.ICloudPhotos
import com.coooolfan.xiaomialbumsyncer.model.*
import com.coooolfan.xiaomialbumsyncer.xiaomicloud.XiaoMiApi
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.valueIn
import org.noear.solon.annotation.Managed
import java.nio.file.Path

/** 按账号来源分派相册查询和下载；iCloud 资源按相册成员保留独立下载历史。 */
@Managed
class CloudMediaService(private val sql: KSqlClient, private val xiaomi: XiaoMiApi, private val icloud: ICloudPhotos) {
    fun isICloud(accountId: Long): Boolean = sql.findById(XiaomiAccount::class, accountId)?.provider == CloudProvider.ICLOUD
    fun fetchAlbums(accountId: Long): List<Album> = if (isICloud(accountId)) icloud.fetchAlbums(accountId) else xiaomi.fetchAllAlbums(accountId)
    fun fetchAssets(album: Album, handler: (List<Asset>) -> Unit): Long =
        if (isICloud(album.accountId)) icloud.fetchAssets(album, handler) else xiaomi.fetchAssetsByAlbumId(album, handler = handler)

    @Synchronized fun saveAssets(assets: List<Asset>): List<Asset> {
        if (assets.isEmpty()) return emptyList()
        val keys = assets.mapNotNull { it.remoteKey }
        if (keys.isEmpty()) {
            sql.saveEntitiesCommand(assets, SaveMode.UPSERT).execute()
            return assets
        }
        val existing = sql.executeQuery(Asset::class) {
            where(table.remoteKey valueIn keys)
            select(table)
        }.associateBy { it.remoteKey }
        val sourceIds = assets.filter { it.xiaomiId != null && it.remoteKey !in existing }.map { it.id }
        val occupiedIds = if (sourceIds.isEmpty()) emptySet() else sql.executeQuery(Asset::class) {
            where(table.id valueIn sourceIds)
            select(table.id)
        }.toSet()
        val entries = assets.map { asset ->
            val previous = existing[asset.remoteKey]
            val entity = when {
                previous != null -> Asset(asset) { id = previous.id }
                asset.xiaomiId != null && asset.id in occupiedIds -> Asset {
                    // 保留小米远端标识，另分配本地 ID，防止覆盖其他来源的资产。
                    xiaomiId = asset.xiaomiId
                    remoteKey = asset.remoteKey
                    cloudAsset = null
                    album = asset.album
                    fileName = asset.fileName
                    type = asset.type
                    recordingType = asset.recordingType
                    dateTaken = asset.dateTaken
                    sha1 = asset.sha1
                    mimeType = asset.mimeType
                    title = asset.title
                    size = asset.size
                }
                else -> asset
            }
            (previous == null) to entity
        }
        return entries.groupBy { it.first }.flatMap { (insert, group) ->
            sql.saveEntitiesCommand(group.map { it.second }, if (insert) SaveMode.INSERT_ONLY else SaveMode.UPDATE_ONLY)
                .execute().items.map { it.modifiedEntity }
        }
    }

    fun download(accountId: Long, asset: Asset, path: Path): Boolean =
        if (isICloud(accountId)) icloud.download(accountId, asset, path) else xiaomi.downloadAsset(accountId, asset, path)
}
