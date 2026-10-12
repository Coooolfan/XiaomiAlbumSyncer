package com.coooolfan.xiaomialbumsyncer.config.flyway

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.FlywayException
import org.flywaydb.core.api.callback.Callback
import org.flywaydb.core.api.callback.Context
import org.flywaydb.core.api.callback.Event
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.nio.file.Files
import java.security.MessageDigest
import java.sql.Connection

class AlbumDeduplicationMigrationTest {
    @Test
    fun migratesFreshDatabase(@TempDir directory: Path) = database(directory) { source ->
        val migrated = DatabaseMigration().migrate(source)
        assertEquals("0.20.0", migrated.info().current().version.version)
        assertEquals(0, migrated.migrate().migrationsExecuted)
        source.connection.use { connection ->
            assertEquals(0L, connection.number("SELECT COUNT(*) FROM album"))
            assertHealthy(connection)
        }
    }

    @Test
    fun upgradesPopulatedDatabaseWithoutDuplicates(@TempDir directory: Path) = database(directory) { source ->
        prepare(source, duplicates = false)
        DatabaseMigration().migrate(source)
        assertData(source, duplicates = false)
    }

    @Test
    fun retriesPublishedMigrationAfterUniqueConstraintFailure(@TempDir directory: Path) = database(directory) { source ->
        prepare(source, duplicates = true)
        val before = source.connection.use(::snapshot)
        val failure = assertThrows(FlywayException::class.java) { published(source).migrate() }
        assertTrue(generateSequence<Throwable>(failure) { it.cause }.any {
            it.message.orEmpty().contains("UNIQUE constraint failed: album.account_id, album.remote_key")
        })
        source.connection.use { connection ->
            assertEquals(before, snapshot(connection))
            assertEquals(0L, connection.number("SELECT COUNT(*) FROM flyway_schema_history WHERE success=0"))
            assertEquals(0L, connection.number("SELECT COUNT(*) FROM flyway_schema_history WHERE version='0.20.0'"))
            val columns = connection.rows("PRAGMA table_info(album)").map { it[1] }
            assertTrue("remote_id" in columns)
            assertFalse("remote_key" in columns)
        }
        assertEquals("0.19.0", published(source).info().current().version.version)
        DatabaseMigration().migrate(source)
        assertData(source, duplicates = true)
    }

    @Test
    fun upgradesPopulatedDatabaseWithDuplicates(@TempDir directory: Path) = database(directory) { source ->
        prepare(source, duplicates = true)
        DatabaseMigration().migrate(source)
        assertData(source, duplicates = true)
    }

    @Test
    fun preservesDatabaseAlreadyUpgradedByPublishedBetas(@TempDir directory: Path) = database(directory) { source ->
        prepare(source, duplicates = false)
        published(source).migrate()
        source.connection.use { connection ->
            connection.execute("UPDATE album SET asset_count=NULL,last_update_time=NULL WHERE id=100")
            connection.execute("INSERT INTO provider_account(id,nickname,user_id,provider,credentials) VALUES(300,'Apple','apple','ICLOUD','{}')")
            connection.execute("INSERT INTO album(id,remote_key,name,account_id,shadow) VALUES(300,'icloud-album','Apple photos',300,0)")
        }
        val before = source.connection.use(::snapshot)
        val historyBefore = source.connection.use { it.rows("SELECT * FROM flyway_schema_history ORDER BY installed_rank") }
        val migrated = DatabaseMigration().migrate(source)
        assertEquals(0, migrated.migrate().migrationsExecuted)
        source.connection.use { connection ->
            assertEquals(before, snapshot(connection))
            assertEquals(historyBefore, connection.rows("SELECT * FROM flyway_schema_history ORDER BY installed_rank"))
            assertHealthy(connection)
        }
    }

    @Test
    fun rollsBackCleanupTogetherWithMigration(@TempDir directory: Path) = database(directory) { source ->
        prepare(source, duplicates = true)
        val before = source.connection.use(::snapshot)
        var rejected = false
        val reject = object : Callback {
            override fun supports(event: Event, context: Context?) =
                event == Event.AFTER_EACH_MIGRATE && context?.migrationInfo?.version?.version == "0.20.0"
            override fun canHandleInTransaction(event: Event, context: Context) = true
            override fun getCallbackName() = "reject-upgrade"
            override fun handle(event: Event, context: Context) {
                assertEquals(4L, context.connection.number("SELECT COUNT(*) FROM album"))
                assertEquals(4L, context.connection.number("SELECT COUNT(remote_key) FROM album"))
                rejected = true
                error("reject-upgrade")
            }
        }
        assertThrows(Exception::class.java) {
            Flyway.configure().dataSource(source).locations("classpath:db/migration")
                .callbacks(AlbumDeduplicationCallback(), reject).load().migrate()
        }
        assertTrue(rejected)
        source.connection.use { connection ->
            assertEquals(before, snapshot(connection))
            assertEquals(0L, connection.number("SELECT COUNT(*) FROM flyway_schema_history WHERE version='0.20.0'"))
        }
        DatabaseMigration().migrate(source)
        assertData(source, duplicates = true)
    }

    @Test
    fun rollsBackFailuresInsideEachCleanupStep(@TempDir directory: Path) {
        val mutations = listOf(
            "AFTER UPDATE OF album_id ON asset",
            "AFTER INSERT ON crontab_album_mapping",
            "AFTER DELETE ON album",
        )
        mutations.forEachIndexed { index, mutation ->
            database(Files.createDirectory(directory.resolve("step-$index"))) { source ->
                prepare(source, duplicates = true)
                source.connection.use {
                    it.execute("CREATE TRIGGER reject_cleanup $mutation BEGIN SELECT RAISE(ABORT, 'reject-cleanup-$index'); END")
                }
                val before = source.connection.use(::snapshot)
                val schemaBefore = source.connection.use { it.rows("SELECT type,name,sql FROM sqlite_master ORDER BY type,name") }
                val failure = assertThrows(RuntimeException::class.java) { DatabaseMigration().migrate(source) }
                assertTrue(generateSequence<Throwable>(failure) { it.cause }.any {
                    it.message.orEmpty().contains("reject-cleanup-$index")
                })
                source.connection.use { connection ->
                    assertEquals(before, snapshot(connection))
                    assertEquals(schemaBefore, connection.rows("SELECT type,name,sql FROM sqlite_master ORDER BY type,name"))
                    assertTrue(connection.rows("PRAGMA foreign_key_check").isEmpty())
                    assertEquals(0L, connection.number("SELECT COUNT(*) FROM sqlite_temp_master WHERE name='album_deduplication'"))
                    connection.execute("DROP TRIGGER reject_cleanup")
                }
                DatabaseMigration().migrate(source)
                assertData(source, duplicates = true)
            }
        }
    }

    private fun database(directory: Path, block: (HikariDataSource) -> Unit) {
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:sqlite:${directory.resolve("migration.db")}?foreign_keys=on"
            maximumPoolSize = 1
        }).use(block)
    }

    // beta.1 和 beta.2 发布的 0.20.0 SQL 相同；固定摘要防止测试随迁移文件修改而失效。
    private fun published(source: HikariDataSource): Flyway {
        val script = javaClass.getResourceAsStream("/db/migration/V0.20.0__icloud.sql")!!.use { it.readAllBytes() }
        val digest = MessageDigest.getInstance("SHA-256").digest(script).joinToString("") { "%02x".format(it) }
        assertEquals("309ba95f848384337ce8b58406be59e26c69c4c7338839e8ac6567f1bba3ae5e", digest)
        return Flyway.configure().dataSource(source).locations("classpath:db/migration").load()
    }

    private fun prepare(source: HikariDataSource, duplicates: Boolean) {
        Flyway.configure().dataSource(source).locations("classpath:db/migration").target("0.19.0").load().migrate()
        source.connection.use { connection ->
            connection.execute("INSERT INTO xiaomi_account(id,nickname,pass_token,user_id) VALUES(100,'first','token-100','user-100'),(200,'second','token-200','user-200')")
            val albums = mutableListOf(
                // 同一 remote_id 的不同账号必须保持独立。
                listOf(100, 1, 100, 1), listOf(110, 2, 100, 1),
                listOf(120, 3, 100, 1), listOf(130, 1, 200, 0),
            )
            if (duplicates) albums += listOf(
                listOf(101, 1, 100, 0), listOf(111, 2, 100, 1),
                listOf(121, 3, 100, 0), listOf(122, 3, 100, 0),
            )
            for ((id, remote, account, shadow) in albums) {
                connection.execute("INSERT INTO album(id,remote_id,name,asset_count,last_update_time,account_id,shadow) VALUES($id,$remote,'album-$id',$id,$id,$account,$shadow)")
                connection.execute("INSERT INTO asset(id,file_name,type,date_taken,album_id,sha1,mime_type,title,size) VALUES(${id + 1000},'photo-$id.jpg','IMAGE',$id,$id,'checksum-$id','image/jpeg','photo-$id',$id)")
            }
            connection.execute("INSERT INTO crontab(id,name,config,description,enabled,account_id,sync_mode) VALUES(1000,'all','{}','description',0,100,'CURSOR'),(1001,'duplicate only','{}','description',0,100,'FULL'),(1002,'other account','{}','description',0,200,'FULL')")
            for ((id, _, account, _) in albums) {
                connection.execute("INSERT INTO crontab_album_mapping(crontab_id,album_id) VALUES(${if (account == 100) 1000 else 1002},$id)")
            }
            connection.execute("INSERT INTO crontab_album_mapping(crontab_id,album_id) VALUES(1001,${if (duplicates) 122 else 120})")
            connection.execute("""INSERT INTO crontab_history(id,crontab_id,start_time,end_time,timeline_snapshot,album_sync_cursors,fetched_all_assets) VALUES(1000,1000,10,20,'{"1":{"2026-10-12":1}}','{"1":"cursor"}',1)""")
            for ((id) in albums) {
                connection.execute("INSERT INTO crontab_history_detail(id,crontab_history_id,asset_id,download_time,file_path,download_completed,sha1_verified,exif_filled,fs_time_updated,message) VALUES($id,1000,${id + 1000},30,'/photos/$id.jpg',1,1,1,1,'completed')")
            }
            assertEquals(1L, connection.number("PRAGMA foreign_keys"))
            assertFalse(connection.rows("SELECT * FROM asset").isEmpty())
        }
    }

    private fun assertData(source: HikariDataSource, duplicates: Boolean) {
        source.connection.use { connection ->
            val keep = if (duplicates) listOf(101L, 110L, 121L, 130L) else listOf(100L, 110L, 120L, 130L)
            assertEquals(keep, connection.rows("SELECT id FROM album ORDER BY id").map { it[0]!!.toLong() })
            for (id in keep) {
                assertEquals(listOf("album-$id", "$id", "$id"), connection.rows("SELECT name,asset_count,last_update_time FROM album WHERE id=$id").single())
            }
            assertEquals(if (duplicates) listOf("0", "1", "0", "0") else listOf("1", "1", "1", "0"),
                connection.rows("SELECT shadow FROM album ORDER BY id").map { it.single() })
            assertEquals(listOf(listOf("100", "1"), listOf("100", "2"), listOf("100", "3"), listOf("200", "1")),
                connection.rows("SELECT account_id,remote_key FROM album ORDER BY account_id,remote_key"))
            val mapping = mutableMapOf(100L to keep[0], 110L to keep[1], 120L to keep[2], 130L to keep[3])
            if (duplicates) mapping.putAll(mapOf(101L to 101L, 111L to 110L, 121L to 121L, 122L to 121L))
            for ((old, retained) in mapping) {
                assertEquals(listOf("${old + 1000}", "$retained", "${old + 1000}", "checksum-$old", "photo-$old.jpg"),
                    connection.rows("SELECT id,album_id,remote_key,checksum,file_name FROM asset WHERE id=${old + 1000}").single())
                assertEquals(listOf("${old + 1000}", "/photos/$old.jpg", "1", "1", "1", "1", "completed"),
                    connection.rows("SELECT asset_id,file_path,download_completed,sha1_verified,exif_filled,fs_time_updated,message FROM crontab_history_detail WHERE id=$old").single())
            }
            assertEquals(mapping.size.toLong(), connection.number("SELECT COUNT(*) FROM asset"))
            assertEquals(mapping.size.toLong(), connection.number("SELECT COUNT(*) FROM crontab_history_detail"))
            assertEquals(listOf(listOf("1000", "${keep[0]}"), listOf("1000", "${keep[1]}"), listOf("1000", "${keep[2]}"), listOf("1001", "${keep[2]}"), listOf("1002", "130")),
                connection.rows("SELECT crontab_id,album_id FROM crontab_album_mapping ORDER BY crontab_id,album_id"))
            assertEquals(listOf("{\"1\":{\"2026-10-12\":1}}", "{\"1\":\"cursor\"}"),
                connection.rows("SELECT timeline_snapshot,album_sync_cursors FROM crontab_history WHERE id=1000").single())
            assertEquals(listOf(listOf("100", "XIAOMI", "{\"passToken\":\"token-100\"}"), listOf("200", "XIAOMI", "{\"passToken\":\"token-200\"}")),
                connection.rows("SELECT id,provider,credentials FROM provider_account WHERE id IN (100,200) ORDER BY id"))
            assertHealthy(connection)
        }
        val before = source.connection.use(::snapshot)
        DatabaseMigration().migrate(source)
        source.connection.use { assertEquals(before, snapshot(it)) }
    }

    private fun assertHealthy(connection: Connection) {
        assertEquals(1L, connection.number("PRAGMA foreign_keys"))
        assertEquals(0L, connection.number("SELECT COUNT(*) FROM flyway_schema_history WHERE success=0"))
        assertEquals(1L, connection.number("SELECT COUNT(*) FROM flyway_schema_history WHERE version='0.20.0' AND success=1"))
        assertEquals(listOf(listOf("ok")), connection.rows("PRAGMA integrity_check"))
        assertTrue(connection.rows("PRAGMA foreign_key_check").isEmpty())
        assertEquals(0L, connection.number("SELECT COUNT(*) FROM sqlite_temp_master WHERE name='album_deduplication'"))
        val foreignKeys = mapOf(
            "album" to setOf(listOf("provider_account", "account_id", "id")),
            "asset" to setOf(listOf("album", "album_id", "id")),
            "crontab_album_mapping" to setOf(listOf("album", "album_id", "id"), listOf("crontab", "crontab_id", "id")),
            "crontab" to setOf(listOf("provider_account", "account_id", "id")),
            "crontab_history" to setOf(listOf("crontab", "crontab_id", "id")),
            "crontab_history_detail" to setOf(listOf("asset", "asset_id", "id"), listOf("crontab_history", "crontab_history_id", "id")),
        )
        foreignKeys.forEach { (table, expected) ->
            assertEquals(expected, connection.rows("PRAGMA foreign_key_list($table)").map { listOf(it[2], it[3], it[4]) }.toSet(), table)
        }
        if (connection.number("SELECT COUNT(*) FROM album") > 0L) {
            assertThrows(java.sql.SQLException::class.java) {
                connection.execute("INSERT INTO album(remote_key,name,account_id,shadow) SELECT remote_key,'duplicate',account_id,shadow FROM album LIMIT 1")
            }
        }
        if (connection.number("SELECT COUNT(*) FROM asset") > 0L) {
            val before = snapshot(connection)
            listOf(
                "UPDATE asset SET album_id=-999 WHERE id=(SELECT MIN(id) FROM asset)",
                "INSERT INTO crontab_album_mapping(crontab_id,album_id) VALUES(1000,-999)",
                "UPDATE crontab_history_detail SET asset_id=-999 WHERE id=(SELECT MIN(id) FROM crontab_history_detail)",
                "UPDATE crontab_history_detail SET crontab_history_id=-999 WHERE id=(SELECT MIN(id) FROM crontab_history_detail)",
                "UPDATE album SET account_id=-999 WHERE id=(SELECT MIN(id) FROM album)",
                "DELETE FROM album WHERE id=(SELECT MIN(album_id) FROM asset)",
                "DELETE FROM asset WHERE id=(SELECT MIN(asset_id) FROM crontab_history_detail)",
                "DELETE FROM crontab WHERE id=1000",
                "DELETE FROM provider_account WHERE id=100",
            ).forEach { sql ->
                val failure = assertThrows(java.sql.SQLException::class.java, { connection.execute(sql) }, sql)
                assertTrue(failure.message.orEmpty().contains("FOREIGN KEY constraint failed"), sql)
            }
            assertEquals(before, snapshot(connection))
        }
    }

    private fun snapshot(connection: Connection): Map<String, List<List<String?>>> =
        connection.rows("SELECT name FROM sqlite_master WHERE type='table' AND (name NOT LIKE 'sqlite_%' OR name='sqlite_sequence') ORDER BY name")
            .associate { row -> row[0]!! to connection.rows("SELECT * FROM \"${row[0]}\" ORDER BY 1") }

    private fun Connection.execute(sql: String) = createStatement().use { it.executeUpdate(sql) }
    private fun Connection.number(sql: String): Long = rows(sql).single().single()!!.toLong()
    private fun Connection.rows(sql: String): List<List<String?>> = createStatement().use { statement ->
        statement.executeQuery(sql).use { result ->
            buildList { while (result.next()) add((1..result.metaData.columnCount).map { result.getString(it) }) }
        }
    }
}
