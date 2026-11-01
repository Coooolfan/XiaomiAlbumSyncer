package com.coooolfan.xiaomialbumsyncer.service

import com.coooolfan.xiaomialbumsyncer.icloud.ICloudPhotos
import com.coooolfan.xiaomialbumsyncer.model.*
import com.coooolfan.xiaomialbumsyncer.xiaomicloud.XiaomiCloudApi
import java.nio.file.Path
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.noear.solon.annotation.Managed
import org.slf4j.LoggerFactory

@Managed
class CloudMediaService(private val sql: KSqlClient, private val xiaomi: XiaomiCloudApi, private val icloud: ICloudPhotos) {
    private val log = LoggerFactory.getLogger(javaClass)

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
        val source = provider(accountId)
        val started = System.nanoTime()
        log.info("云端下载开始 accountId={} albumId={} assetId={} provider={} type={} expectedBytes={}",
            accountId, album.id, asset.id, source, asset.type, asset.size)
        return when (source) {
            CloudProvider.XIAOMI -> xiaomi.downloadAsset(accountId, asset, path)
            CloudProvider.ICLOUD -> icloud.download(accountId, album, asset, path)
        }.also { downloaded ->
            log.info("云端下载完成 accountId={} assetId={} provider={} downloaded={} actualBytes={} elapsedMs={}",
                accountId, asset.id, source, downloaded, if (downloaded) path.toFile().length() else 0,
                (System.nanoTime() - started) / 1_000_000)
        }
    }
}
