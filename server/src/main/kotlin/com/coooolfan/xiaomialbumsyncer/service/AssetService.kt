package com.coooolfan.xiaomialbumsyncer.service

import com.coooolfan.xiaomialbumsyncer.model.Asset
import com.coooolfan.xiaomialbumsyncer.xiaomicloud.XiaoMiApi
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.noear.solon.annotation.Managed

@Managed
class AssetService(private val sql: KSqlClient, private val api: XiaoMiApi) {
    fun refreshAssets(albumId: Long): List<Asset> {
        val fetchAssetList = api.fetchAssetsByAlbumId(albumId)
        sql.saveEntitiesCommand(fetchAssetList).execute()
        return fetchAssetList
    }
}