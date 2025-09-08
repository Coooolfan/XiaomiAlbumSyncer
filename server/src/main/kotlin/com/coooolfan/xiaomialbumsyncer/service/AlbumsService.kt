package com.coooolfan.xiaomialbumsyncer.service

import com.coooolfan.xiaomialbumsyncer.model.Album
import com.coooolfan.xiaomialbumsyncer.xiaomicloud.XiaoMiApi
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.noear.solon.annotation.Managed

@Managed
class AlbumsService(private val sql: KSqlClient, private val api: XiaoMiApi) {
    fun refreshAlbums(): List<Album> {
        val fetchAlbumList = api.fetchAlbumList()
        sql.saveEntitiesCommand(fetchAlbumList, SaveMode.UPSERT).execute()
        return fetchAlbumList
    }

    fun getAllAlbums(): List<Album> {
        return sql.executeQuery(Album::class) { select(table) }
    }
}