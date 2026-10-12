package com.coooolfan.xiaomialbumsyncer.service

import com.coooolfan.xiaomialbumsyncer.model.*
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.dialect.SQLiteDialect
import org.babyfish.jimmer.sql.kt.newKSqlClient
import org.babyfish.jimmer.sql.runtime.ConnectionManager
import org.babyfish.jimmer.sql.runtime.DefaultDatabaseNamingStrategy
import org.babyfish.jimmer.sql.runtime.Executor
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant

class AlbumUpsertTest {
    @Test
    fun preservesIdentityAndReferencesWhileUpdatingMetadata(@TempDir directory: Path) {
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:sqlite:${directory.resolve("albums.db")}"
            maximumPoolSize = 1
            connectionInitSql = "PRAGMA foreign_keys=ON"
        }).use { source ->
            Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate()
            val sql = newKSqlClient {
                setDialect(SQLiteDialect())
                setConnectionManager(ConnectionManager.simpleConnectionManager(source))
                setDatabaseNamingStrategy(DefaultDatabaseNamingStrategy.LOWER_CASE)
                setExecutor(Executor.log())
            }
            fun account(name: String) = sql.saveCommand(ProviderAccount {
                nickname = name
                provider = CloudProvider.XIAOMI
                userId = name
                credentials = "{}"
            }, SaveMode.INSERT_ONLY).execute().modifiedEntity.id
            val firstAccount = account("first")
            val secondAccount = account("second")
            fun album(accountId: Long, key: String, title: String) = Album {
                this.accountId = accountId
                remoteKey = key
                name = title
                assetCount = 10
                lastUpdateTime = Instant.EPOCH
                shadow = false
            }
            fun upsert(albums: List<Album>) = sql.saveEntitiesCommand(albums, SaveMode.UPSERT)
                .execute().items.map { it.modifiedEntity }
            val original = upsert(listOf(album(firstAccount, "1", "old"))).single()
            source.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeUpdate("INSERT INTO asset(id,remote_key,file_name,type,date_taken,album_id,checksum,mime_type,title,size) VALUES(100,'100','photo.jpg','IMAGE',0,${original.id},'checksum','image/jpeg','photo',10)")
                    statement.executeUpdate("INSERT INTO crontab(id,name,config,description,enabled,account_id,sync_mode) VALUES(100,'task','{}','',0,$firstAccount,'FULL')")
                    statement.executeUpdate("INSERT INTO crontab_album_mapping(crontab_id,album_id) VALUES(100,${original.id})")
                    statement.executeUpdate("INSERT INTO crontab_history(id,crontab_id,start_time) VALUES(100,100,0)")
                    statement.executeUpdate("INSERT INTO crontab_history_detail(id,crontab_history_id,asset_id,download_time,file_path) VALUES(100,100,100,0,'photo.jpg')")
                    statement.executeUpdate("UPDATE album SET shadow=1 WHERE account_id=$firstAccount")
                }
            }
            val changed = Album(album(firstAccount, "1", "renamed")) {
                assetCount = null
                lastUpdateTime = null
            }
            val mixed = upsert(listOf(changed, album(firstAccount, "2", "new"), album(secondAccount, "1", "other account")))
            assertEquals(original.id, mixed[0].id)
            assertNotEquals(original.id, mixed[1].id)
            assertNotEquals(original.id, mixed[2].id)
            val restored = sql.findById(Album::class, original.id)!!
            assertEquals("renamed", restored.name)
            assertNull(restored.assetCount)
            assertNull(restored.lastUpdateTime)
            assertFalse(restored.shadow)
            assertEquals(original.id, sql.findById(Asset::class, 100L)!!.album.id)
            source.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT album_id FROM crontab_album_mapping WHERE crontab_id=100").use {
                        assertTrue(it.next())
                        assertEquals(original.id, it.getLong(1))
                        assertFalse(it.next())
                    }
                    statement.executeQuery("SELECT asset_id,file_path FROM crontab_history_detail WHERE id=100").use {
                        assertTrue(it.next())
                        assertEquals(100L, it.getLong(1))
                        assertEquals("photo.jpg", it.getString(2))
                    }
                    statement.executeQuery("PRAGMA foreign_key_check").use { assertFalse(it.next()) }
                }
            }
            val duplicate = album(firstAccount, "3", "duplicate")
            upsert(listOf(duplicate, duplicate).distinctBy { it.remoteKey })
            upsert(listOf(duplicate, duplicate))
            val newDuplicate = album(firstAccount, "4", "new duplicate")
            val repeated = upsert(listOf(newDuplicate, newDuplicate))
            assertEquals(repeated[0].id, repeated[1].id)
            source.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT count(*) FROM album WHERE account_id=$firstAccount AND remote_key='3'").use {
                        assertTrue(it.next())
                        assertEquals(1, it.getInt(1))
                    }
                }
            }
        }
    }
}
