package com.coooolfan.xiaomialbumsyncer.service

import com.coooolfan.xiaomialbumsyncer.model.Album
import com.coooolfan.xiaomialbumsyncer.model.Asset
import com.coooolfan.xiaomialbumsyncer.model.SystemConfig
import com.coooolfan.xiaomialbumsyncer.model.albumId
import com.coooolfan.xiaomialbumsyncer.model.assetsDateMapTimeZone
import com.coooolfan.xiaomialbumsyncer.model.dateTaken
import com.coooolfan.xiaomialbumsyncer.model.id
import com.coooolfan.xiaomialbumsyncer.xiaomicloud.XiaoMiApi
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.kt.ast.expression.ge
import org.babyfish.jimmer.sql.kt.ast.expression.le
import org.babyfish.jimmer.sql.kt.ast.expression.valueIn
import org.babyfish.jimmer.sql.kt.ast.query.whereIfNotEmpty
import org.noear.solon.annotation.Managed
import java.time.Instant
import java.time.LocalDate

@Managed
class AlbumsService(private val sql: KSqlClient, private val api: XiaoMiApi) {
    fun refreshAlbums(): List<Album> {
        val fetchAlbumList = api.fetchAllAlbums()
        sql.saveEntitiesCommand(fetchAlbumList, SaveMode.UPSERT).execute()
        return fetchAlbumList
    }

    fun getAllAlbums(): List<Album> {
        return sql.executeQuery(Album::class) { select(table) }
    }

    fun fetchDateMap(albumIds: List<Long>, start: Instant, end: Instant): Map<LocalDate, Long> {
        val instants = sql.executeQuery(Asset::class) {
            whereIfNotEmpty(albumIds) { table.albumId valueIn it }
            where(table.dateTaken ge start)
            where(table.dateTaken le end)
            select(table.dateTaken)
        }

        val timeZone = sql.executeQuery(SystemConfig::class) {
            where(table.id eq 0L)
            select(table.assetsDateMapTimeZone)
        }.firstOrNull() ?: throw IllegalStateException("SystemConfig not initialized")

        return instants.groupBy {
            it.atZone(java.time.ZoneId.of(timeZone)).toLocalDate()
        }.mapValues {
            it.value.size.toLong()
        }
    }
}