package com.coooolfan.xiaomialbumsyncer.service

import com.coooolfan.xiaomialbumsyncer.model.*
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.*
import org.noear.solon.annotation.Managed

@Managed
class AssetService(private val sql: KSqlClient) {
    @Synchronized fun saveAssets(assets: List<Asset>): List<Asset> {
        if (assets.isEmpty()) return emptyList()
        val distinct = assets.distinctBy { Triple(it.album.id, it.remoteKey, it.checksum) }
        return sql.saveEntitiesCommand(distinct, SaveMode.UPSERT).execute().items.map { it.modifiedEntity }
    }

    fun getAssets(albumId: Long, fetcher: Fetcher<Asset>): List<Asset> {
        return sql.executeQuery(Asset::class) {
            where(table.albumId eq albumId)
            select(table.fetch(fetcher))
        }
    }

    fun getAssetsUndownloadByCrontab(
        crontab: Crontab,
        pageSize: Int,
        lastId: Long
    ): List<Asset> {
        return sql.createQuery(Asset::class) {
            where(table.albumId valueIn crontab.albumIds)
            where(table.id gt lastId)
            where(
                notExists(
                    subQuery(CrontabHistoryDetail::class) {
                        where(table.crontabHistory.crontabId eq crontab.id)
                        where(table.assetId eq parentTable.id)
                        where(table.downloadCompleted eq true)
                        where(table.sha1Verified eq true)
                        where(table.exifFilled eq true)
                        where(table.fsTimeUpdated eq true)
                        select(table)
                    })
            )
            if (!crontab.config.downloadImages) where(table.type ne AssetType.IMAGE)
            if (!crontab.config.downloadVideos) where(table.type ne AssetType.VIDEO)
            if (!crontab.config.downloadAudios) where(table.type ne AssetType.AUDIO)
            orderBy(table.id.asc())
            select(table.fetchBy {
                allScalarFields()
                album { name() }
            })
        }.limit(pageSize).execute()
    }

}
