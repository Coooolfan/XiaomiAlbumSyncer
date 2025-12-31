package com.coooolfan.xiaomialbumsyncer.service

import com.coooolfan.xiaomialbumsyncer.model.*
import com.coooolfan.xiaomialbumsyncer.xiaomicloud.XiaoMiApi
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.kt.ast.expression.ge
import org.babyfish.jimmer.sql.kt.ast.expression.le
import org.babyfish.jimmer.sql.kt.ast.expression.valueIn
import org.babyfish.jimmer.sql.kt.ast.query.whereIfNotEmpty
import org.noear.solon.annotation.Managed
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDate

@Managed
class AlbumsService(
    private val sql: KSqlClient,
    private val api: XiaoMiApi,
    private val accountService: XiaomiAccountService
) {
    private val log = LoggerFactory.getLogger(AlbumsService::class.java)

    /**
     * 刷新所有账号的相册
     */
    fun refreshAlbums(): List<Album> {
        val allAlbums = mutableListOf<Album>()
        val accounts = accountService.listAll()

        for (account in accounts) {
            val albums = refreshAlbumsByAccount(account.id)
            allAlbums.addAll(albums)
        }

        return allAlbums
    }

    /**
     * 刷新指定账号的相册
     */
    fun refreshAlbumsByAccount(accountId: Long): List<Album> {
        log.info("刷新账号 {} 的相册列表", accountId)
        val remoteAlbums = api.fetchAllAlbums(accountId)

        // 获取当前账号已有的相册 (用于对比删除)
        val existingAlbums = sql.executeQuery(Album::class) {
            where(table.accountId eq accountId)
            select(table)
        }

        // 使用 remoteId + accountId 组合进行 upsert
        for (remoteAlbum in remoteAlbums) {
            val existing = existingAlbums.find { it.remoteId == remoteAlbum.remoteId }
            if (existing != null) {
                // 更新现有相册
                sql.executeUpdate(Album::class) {
                    set(table.name, remoteAlbum.name)
                    set(table.assetCount, remoteAlbum.assetCount)
                    set(table.lastUpdateTime, remoteAlbum.lastUpdateTime)
                    where(table.id eq existing.id)
                }
            } else {
                // 插入新相册
                sql.insert(remoteAlbum)
            }
        }

        // 删除远程不存在的相册
        val remoteIds = remoteAlbums.map { it.remoteId }.toSet()
        val toDelete = existingAlbums.filter { it.remoteId !in remoteIds }
        for (album in toDelete) {
            sql.deleteById(Album::class, album.id)
            log.info("删除不存在的相册: {} (remoteId={})", album.name, album.remoteId)
        }

        return sql.executeQuery(Album::class) {
            where(table.accountId eq accountId)
            select(table)
        }
    }

    fun getAllAlbums(): List<Album> {
        return sql.executeQuery(Album::class) { select(table) }
    }

    fun getAlbumsByAccount(accountId: Long): List<Album> {
        return sql.executeQuery(Album::class) {
            where(table.accountId eq accountId)
            select(table)
        }
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