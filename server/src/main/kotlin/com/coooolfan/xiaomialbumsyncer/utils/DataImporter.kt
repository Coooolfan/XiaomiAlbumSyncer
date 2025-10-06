package com.coooolfan.xiaomialbumsyncer.utils

import com.coooolfan.xiaomialbumsyncer.config.buildSQLiteUrl
import com.coooolfan.xiaomialbumsyncer.model.Album
import com.coooolfan.xiaomialbumsyncer.model.Asset
import com.coooolfan.xiaomialbumsyncer.model.AssetType
import com.coooolfan.xiaomialbumsyncer.model.Crontab
import com.coooolfan.xiaomialbumsyncer.model.CrontabConfig
import com.coooolfan.xiaomialbumsyncer.model.CrontabHistory
import com.coooolfan.xiaomialbumsyncer.model.CrontabHistoryDetail
import com.coooolfan.xiaomialbumsyncer.model.endTime
import com.coooolfan.xiaomialbumsyncer.model.id
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.noear.solon.annotation.Managed
import java.nio.file.Paths
import java.time.Instant
import java.util.Locale
import java.util.Locale.getDefault
import javax.sql.DataSource

@Managed
class DataImporter(private val sql: KSqlClient, private val ds: DataSource) {

    private val oldDsUrl = "./old.db"

    fun exec() {
        val oldDsConfig = HikariConfig()
        oldDsConfig.jdbcUrl = buildSQLiteUrl(Paths.get(oldDsUrl))
        oldDsConfig.driverClassName = "org.sqlite.JDBC"
        oldDsConfig.maximumPoolSize = 1
        oldDsConfig.connectionTestQuery = "SELECT 1"
        oldDsConfig.poolName = "SQLitePool"
        val oldDs = HikariDataSource(oldDsConfig)
        val oldDsStatement = oldDs.connection.createStatement()

        // 相册
        val albums = mutableListOf<Album>()
        val selectedAlbums = mutableListOf<Long>()
        val albumsResultSet = oldDsStatement.executeQuery("SELECT * FROM album")
        while (albumsResultSet.next()) {
            val album = Album {
                id = albumsResultSet.getLong("id")
                name = albumsResultSet.getString("name")
                assetCount = albumsResultSet.getInt("media_count")
                lastUpdateTime = Instant.now()
            }
            albums.add(album)
            if (albumsResultSet.getBoolean("selected")) {
                selectedAlbums.add(album.id)
            }
        }
        sql.saveEntitiesCommand(albums, SaveMode.INSERT_ONLY).execute()

        // 资产
        val assets = mutableListOf<Asset>()
        val downloadedAssets = mutableListOf<Long>()
        val assetsResultSet = oldDsStatement.executeQuery("SELECT * FROM media")
        while (assetsResultSet.next()) {
            val asset = Asset {
                id = assetsResultSet.getLong("id")
                fileName = assetsResultSet.getString("filename")
                type = AssetType.valueOf(assetsResultSet.getString("media_type").uppercase(getDefault()))
                dateTaken = Instant.ofEpochMilli(assetsResultSet.getLong("date_modified"))
                albumId = assetsResultSet.getLong("album_id")
                sha1 = assetsResultSet.getString("sha1")
                mimeType = assetsResultSet.getString("mime_type")
                title = assetsResultSet.getString("filename").substringBeforeLast('.')
                size = 0L
            }
            assets.add(asset)

            if (assetsResultSet.getBoolean("downloaded"))
                downloadedAssets.add(asset.id)

        }
        sql.saveEntitiesCommand(assets, SaveMode.INSERT_ONLY).execute()

        // mock 一个下载计划任务和下载历史出来，用于无缝迁移
        val crontab = Crontab {
            name = "旧版本导入"
            description = "由旧版本导入时自动创建，仅用于迁移下载历史。此计划任务默认不启用。"
            enabled = false
            config = CrontabConfig(
                expression = "0 0 * * * ?",
                timeZone = "Asia/Shanghai",
                targetPath = "./download",
                downloadImages = true,
                downloadVideos = true,
                rewriteExifTime = true,
                rewriteExifTimeZone = "Asia/Shanghai"
            )
            albumIds = selectedAlbums
        }
        val crontabId = sql.saveCommand(crontab, SaveMode.INSERT_ONLY).execute().modifiedEntity.id

        val crontabHistory = CrontabHistory {
            this.crontabId = crontabId
            startTime = Instant.now()
        }
        val crontabHistoryId = sql.saveCommand(crontabHistory, SaveMode.INSERT_ONLY).execute().modifiedEntity.id

        // 下载历史
        val historyDetails = mutableListOf<CrontabHistoryDetail>()
        downloadedAssets.forEach {
            historyDetails.add(
                CrontabHistoryDetail {
                    this.crontabHistoryId = crontabHistoryId
                    this.assetId = it
                    filePath = "./unknown"
                    downloadTime = Instant.now()
                }
            )
        }
        sql.saveEntitiesCommand(historyDetails, SaveMode.INSERT_ONLY).execute()

        sql.executeUpdate(CrontabHistory::class) {
            set(table.endTime, Instant.now())
            where(table.id eq crontabHistoryId)
        }

        oldDs.close()
    }
}