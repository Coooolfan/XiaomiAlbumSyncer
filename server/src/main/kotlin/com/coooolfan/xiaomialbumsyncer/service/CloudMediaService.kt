package com.coooolfan.xiaomialbumsyncer.service

import com.coooolfan.xiaomialbumsyncer.icloud.ICloudPhotos
import com.coooolfan.xiaomialbumsyncer.model.*
import com.coooolfan.xiaomialbumsyncer.xiaomicloud.XiaomiCloudApi
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.noear.solon.annotation.Managed
import java.nio.file.Path

@Managed
class CloudMediaService(private val sql: KSqlClient, private val xiaomi: XiaomiCloudApi, private val icloud: ICloudPhotos) {
    fun provider(accountId: Long): CloudProvider = requireNotNull(sql.findById(ProviderAccount::class, accountId)) {
        "账号不存在: $accountId"
    }.provider

    fun fetchAlbums(accountId: Long): List<Album> = when (provider(accountId)) {
        CloudProvider.XIAOMI -> xiaomi.fetchAllAlbums(accountId)
        CloudProvider.ICLOUD -> icloud.fetchAlbums(accountId)
    }

    fun fetchAssets(album: Album, handler: (List<Asset>) -> Unit): Long = when (provider(album.accountId)) {
        CloudProvider.XIAOMI -> xiaomi.fetchAssetsByAlbumId(album, handler = handler)
        CloudProvider.ICLOUD -> icloud.fetchAssets(album, handler)
    }

    fun download(accountId: Long, asset: Asset, path: Path): Boolean {
        val album = requireNotNull(sql.findById(Album::class, asset.album.id)) { "资产所属相册不存在" }
        require(album.accountId == accountId) { "资产所属账号与下载账号不一致" }
        return when (provider(accountId)) {
            CloudProvider.XIAOMI -> xiaomi.downloadAsset(accountId, asset, path)
            CloudProvider.ICLOUD -> icloud.download(accountId, album, asset, path)
        }
    }
}
