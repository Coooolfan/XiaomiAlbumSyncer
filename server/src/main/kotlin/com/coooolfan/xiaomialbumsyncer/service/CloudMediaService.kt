package com.coooolfan.xiaomialbumsyncer.service

import com.coooolfan.xiaomialbumsyncer.icloud.ICloudPhotos
import com.coooolfan.xiaomialbumsyncer.icloud.ICloudAlbumKey
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
    fun isICloud(accountId: Long): Boolean = sql.findById(ProviderAccount::class, accountId)?.provider == CloudProvider.ICLOUD
    fun fetchAlbums(accountId: Long): List<Album> = if (isICloud(accountId)) icloud.fetchAlbums(accountId) else xiaomi.fetchAllAlbums(accountId)
    fun fetchAssets(album: Album, handler: (List<Asset>) -> Unit): Long =
        if (isICloud(album.accountId)) icloud.fetchAssets(album, handler) else xiaomi.fetchAssetsByAlbumId(album, handler = handler)

    fun fetchICloudIncremental(albums: List<Album>, cursor: String?, handler: (List<Asset>) -> Unit, commitCursor: (String) -> Unit) =
        icloud.fetchIncremental(albums, cursor, handler, commitCursor)

    @Synchronized fun saveAssets(assets: List<Asset>): List<Asset> {
        if (assets.isEmpty()) return emptyList()
        fun identity(asset: Asset) = Triple(asset.album.id, asset.remoteKey, asset.sha1)
        val distinct = assets.distinctBy(::identity)
        val existing = sql.executeQuery(Asset::class) {
            where(table.album.id valueIn distinct.map { it.album.id }.distinct())
            where(table.remoteKey valueIn distinct.map { it.remoteKey }.distinct())
            where(table.sha1 valueIn distinct.map { it.sha1 }.distinct())
            select(table)
        }.associateBy(::identity)
        val entries = distinct.map { asset ->
            val previous = existing[identity(asset)]
            (previous == null) to if (previous == null) asset else Asset(asset) { id = previous.id }
        }
        return entries.groupBy { it.first }.flatMap { (insert, group) ->
            sql.saveEntitiesCommand(group.map { it.second }, if (insert) SaveMode.INSERT_ONLY else SaveMode.UPDATE_ONLY)
                .execute().items.map { it.modifiedEntity }
        }
    }

    fun download(accountId: Long, asset: Asset, path: Path): Boolean {
        val album = requireNotNull(sql.findById(Album::class, asset.album.id)) { "资产所属相册不存在" }
        require(album.accountId == accountId) { "资产所属账号与下载账号不一致" }
        return if (isICloud(album.accountId)) {
            val zone = ICloudAlbumKey.decode(album.remoteKey).zone
            icloud.download(album.accountId, zone, asset, path)
        } else xiaomi.downloadAsset(album.accountId, asset, path)
    }
}
