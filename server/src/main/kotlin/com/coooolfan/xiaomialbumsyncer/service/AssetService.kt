package com.coooolfan.xiaomialbumsyncer.service

import com.coooolfan.xiaomialbumsyncer.model.Album
import com.coooolfan.xiaomialbumsyncer.model.Asset
import com.coooolfan.xiaomialbumsyncer.model.albumId
import com.coooolfan.xiaomialbumsyncer.model.id
import com.coooolfan.xiaomialbumsyncer.xiaomicloud.XiaoMiApi
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.noear.solon.annotation.Managed

@Managed
class AssetService(private val sql: KSqlClient, private val api: XiaoMiApi) {
    fun refreshAssets(albumId: Long, fetcher: Fetcher<Asset>): List<Asset> {
        val album = sql.executeQuery(Album::class) {
            where(table.id eq albumId)
            select(table)
        }.firstOrNull() ?: throw IllegalArgumentException("Album $albumId not found, please refresh albums first")

        val fetchAssetList = api.fetchAssetsByAlbumId(album)
        sql.saveEntitiesCommand(fetchAssetList, SaveMode.UPSERT).execute()

        // 此处的 fetchAssetList 形状已保证与 fetcher 一致
        return fetchAssetList
    }

    fun getAssets(albumId: Long, fetcher: Fetcher<Asset>): List<Asset> {
        return sql.executeQuery(Asset::class) {
            where(table.albumId eq albumId)
            select(table.fetch(fetcher))
        }
    }
}