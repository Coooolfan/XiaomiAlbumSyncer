package com.coooolfan.xiaomialbumsyncer.config.flyway

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

class DatabaseMigrationTest {

    @Test
    fun addsQueryIndexes(@TempDir tempDir: Path) {
        val databaseUrl = "jdbc:sqlite:${tempDir.resolve("migration.db").toAbsolutePath()}"

        flyway(databaseUrl, target = "0.16.2").migrate()
        DriverManager.getConnection(databaseUrl).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """
                    INSERT INTO crontab_history
                        (crontab_id, start_time, end_time, timeline_snapshot, fetched_all_assets)
                    VALUES
                        (1, 100, 110, '{"id":1}', 1),
                        (1, 200, 210, '{"id":2}', 1),
                        (1, 300, NULL, '{"id":3}', 0)
                    """.trimIndent()
                )
                statement.executeUpdate(
                    """
                    INSERT INTO asset
                        (id, file_name, type, date_taken, album_id, sha1, mime_type, title, size)
                    VALUES
                        (1, 'one.jpg', 'IMAGE', 100, 1, 'sha1-1', 'image/jpeg', 'one', 100),
                        (2, 'two.jpg', 'IMAGE', 200, 1, 'sha1-2', 'image/jpeg', 'two', 200)
                    """.trimIndent()
                )
                statement.executeUpdate(
                    """
                    INSERT INTO crontab_history_detail
                        (crontab_history_id, asset_id, download_time, file_path,
                         download_completed, sha1_verified, exif_filled, fs_time_updated)
                    VALUES
                        (1, 1, 100, '/one.jpg', 1, 1, 1, 1),
                        (2, 2, 200, '/two.jpg', 1, 1, 1, 1)
                    """.trimIndent()
                )
            }
        }

        assertEquals(1, flyway(databaseUrl, target = "0.16.3").migrate().migrationsExecuted)

        DriverManager.getConnection(databaseUrl).use { connection ->
            val indexNames = connection.indexNames()
            assertTrue(indexNames.containsAll(EXPECTED_INDEX_NAMES))
            assertFalse(LEGACY_INDEX_NAME in indexNames)
            assertTrue(connection.analyzedIndexNames().containsAll(EXPECTED_INDEX_NAMES))

            connection.assertUsesIndex(LATEST_COMPLETED_HISTORY_QUERY, HISTORY_INDEX_NAME)
            connection.assertUsesIndex(ASSET_BY_ALBUM_QUERY, ASSET_ALBUM_INDEX_NAME)
            connection.assertUsesIndex(ASSET_BY_DATE_QUERY, ASSET_DATE_INDEX_NAME)
            connection.assertUsesIndex(DETAIL_BY_HISTORY_QUERY, DETAIL_HISTORY_INDEX_NAME)
            connection.assertUsesIndex(DETAIL_BY_ASSET_QUERY, DETAIL_ASSET_HISTORY_INDEX_NAME)

            val latestHistoryPlan = connection.queryPlan(LATEST_COMPLETED_HISTORY_QUERY)
            assertFalse(latestHistoryPlan.any { it.contains("USE TEMP B-TREE") })

            val snapshot = connection.createStatement().use { statement ->
                statement.executeQuery(LATEST_COMPLETED_HISTORY_QUERY).use { result ->
                    assertTrue(result.next())
                    result.getString("timeline_snapshot")
                }
            }
            assertEquals("{\"id\":2}", snapshot)
        }
    }

    @Test
    fun replacesIndexCreatedBeforeFlywayMigration(@TempDir tempDir: Path) {
        val databaseUrl = "jdbc:sqlite:${tempDir.resolve("preindexed.db").toAbsolutePath()}"

        flyway(databaseUrl, target = "0.16.2").migrate()
        DriverManager.getConnection(databaseUrl).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """
                    CREATE INDEX $LEGACY_INDEX_NAME
                        ON crontab_history (crontab_id, start_time DESC)
                        WHERE end_time IS NOT NULL
                    """.trimIndent()
                )
            }
        }

        assertEquals(1, flyway(databaseUrl, target = "0.16.3").migrate().migrationsExecuted)

        DriverManager.getConnection(databaseUrl).use { connection ->
            val indexNames = connection.indexNames()
            assertTrue(indexNames.containsAll(EXPECTED_INDEX_NAMES))
            assertFalse(LEGACY_INDEX_NAME in indexNames)

            val appliedVersion = connection.createStatement().use { statement ->
                statement.executeQuery(
                    "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1"
                ).use { result ->
                    assertTrue(result.next())
                    result.getString("version")
                }
            }
            assertEquals("0.16.3", appliedVersion)
        }
    }

    @Test
    fun createsMcpTokenTable(@TempDir tempDir: Path) {
        val databaseUrl = "jdbc:sqlite:${tempDir.resolve("mcp-token.db").toAbsolutePath()}"

        flyway(databaseUrl).migrate()

        DriverManager.getConnection(databaseUrl).use { connection ->
            assertEquals(
                setOf("id", "name", "token_hash", "permission", "created_at"),
                connection.columnNames("mcp_token"),
            )
            assertFalse("mcp_token" in connection.columnNames("system_config"))

            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """
                    INSERT INTO mcp_token (name, token_hash, permission, created_at)
                    VALUES ('readonly', 'hash-1', 'READ_ONLY', 1),
                           ('trigger', 'hash-2', 'ALLOW_TRIGGER', 2)
                    """.trimIndent()
                )
            }
        }
    }

    @Test
    fun unifiesTargetPathExpression(@TempDir tempDir: Path) {
        val databaseUrl = "jdbc:sqlite:${tempDir.resolve("target-path.db").toAbsolutePath()}"

        flyway(databaseUrl, target = "0.18.0").migrate()
        DriverManager.getConnection(databaseUrl).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """
                    INSERT INTO crontab (name, config, description, enabled, account_id)
                    VALUES
                        ('plain', '{"targetPath":"/data","expressionTargetPath":""}', '', 0, 1),
                        ('missing', '{"targetPath":"/legacy","notify":false}', '', 0, 1),
                        ('null', '{"targetPath":"/nullable","expressionTargetPath":null}', '', 0, 1),
                        ('blank', '{"targetPath":"/trailing/","expressionTargetPath":"   "}', '', 0, 1),
                        ('relative', '{"targetPath":"/data/","expressionTargetPath":"${'$'}{album}/${'$'}{fileName}"}', '', 0, 1),
                        ('dot-relative', '{"targetPath":"/","expressionTargetPath":"./${'$'}{album}/${'$'}{fileName}"}', '', 0, 1),
                        ('parent-relative', '{"targetPath":"/data","expressionTargetPath":"../archive/${'$'}{fileName}"}', '', 0, 1),
                        ('hidden-relative', '{"targetPath":"/data","expressionTargetPath":".archive/${'$'}{fileName}"}', '', 0, 1),
                        ('absolute', '{"targetPath":"/data","expressionTargetPath":"  /archive/${'$'}{fileName}  "}', '', 0, 1)
                    """.trimIndent()
                )
            }
        }

        assertEquals(1, flyway(databaseUrl, target = "0.19.0").migrate().migrationsExecuted)

        DriverManager.getConnection(databaseUrl).use { connection ->
            val paths = connection.createStatement().use { statement ->
                statement.executeQuery(
                    "SELECT name, json_extract(config, '$.targetPath'), json_type(config, '$.expressionTargetPath') FROM crontab ORDER BY id"
                ).use { result ->
                    buildMap {
                        while (result.next()) {
                            assertEquals(null, result.getString(3))
                            put(result.getString(1), result.getString(2))
                        }
                    }
                }
            }
            assertEquals("/data/${'$'}{album}/${'$'}{downloadFileName}", paths.getValue("plain"))
            assertEquals("/legacy/${'$'}{album}/${'$'}{downloadFileName}", paths.getValue("missing"))
            assertEquals("/nullable/${'$'}{album}/${'$'}{downloadFileName}", paths.getValue("null"))
            assertEquals("/trailing/${'$'}{album}/${'$'}{downloadFileName}", paths.getValue("blank"))
            assertEquals("${'$'}{album}/${'$'}{fileName}", paths.getValue("relative"))
            assertEquals("./${'$'}{album}/${'$'}{fileName}", paths.getValue("dot-relative"))
            assertEquals("../archive/${'$'}{fileName}", paths.getValue("parent-relative"))
            assertEquals(".archive/${'$'}{fileName}", paths.getValue("hidden-relative"))
            assertEquals("/archive/${'$'}{fileName}", paths.getValue("absolute"))

            val missingConfig = connection.createStatement().use { statement ->
                statement.executeQuery("SELECT config FROM crontab WHERE name = 'missing'").use { result ->
                    assertTrue(result.next())
                    result.getString(1)
                }
            }
            assertEquals(0, connection.createStatement().use { statement ->
                statement.executeQuery("SELECT json_extract('$missingConfig', '$.notify')").use { result ->
                    assertTrue(result.next())
                    result.getInt(1)
                }
            })
        }
    }

    @Test
    fun upgradesReleasedSchemaToCursorSyncMode(@TempDir tempDir: Path) {
        val databaseUrl = "jdbc:sqlite:${tempDir.resolve("album-sync-cursors.db").toAbsolutePath()}"

        // 0.18.1 发布时的最新数据库迁移版本为 0.18.0。
        flyway(databaseUrl, target = "0.18.0").migrate()
        DriverManager.getConnection(databaseUrl).use { connection ->
            assertFalse("sync_mode" in connection.columnNames("crontab"))
            assertFalse("album_sync_cursors" in connection.columnNames("crontab_history"))
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """
                    INSERT INTO crontab (id, name, config, description, enabled, account_id)
                    VALUES
                        (1, 'timeline', '{"targetPath":"/data","diffByTimeline":true}', '', 0, 1),
                        (2, 'full', '{"targetPath":"/data","diffByTimeline":false}', '', 0, 1),
                        (3, 'default', '{"targetPath":"/data"}', '', 0, 1)
                    """.trimIndent()
                )
                statement.executeUpdate(
                    """
                    INSERT INTO crontab_history
                        (id, crontab_id, start_time, timeline_snapshot, fetched_all_assets)
                    VALUES
                        (1, 1, 100, '{"10":{"2026-09-01":2}}', 1)
                    """.trimIndent()
                )
            }
        }

        assertEquals(1, flyway(databaseUrl).migrate().migrationsExecuted)
        assertEquals("0.19.0", flyway(databaseUrl).info().current().version.version)
        assertEquals(0, flyway(databaseUrl).migrate().migrationsExecuted)

        DriverManager.getConnection(databaseUrl).use { connection ->
            val modes = connection.createStatement().use { statement ->
                statement.executeQuery("SELECT name, sync_mode FROM crontab").use { result ->
                    buildMap {
                        while (result.next()) put(result.getString(1), result.getString(2))
                    }
                }
            }
            assertEquals(mapOf("timeline" to "TIMELINE", "full" to "FULL", "default" to "FULL"), modes)
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT json_type(config, '$.diffByTimeline') FROM crontab").use { result ->
                    while (result.next()) assertEquals(null, result.getString(1))
                }
            }
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT * FROM crontab_history WHERE id = 1").use { result ->
                    assertTrue(result.next())
                    assertEquals(null, result.getString("album_sync_cursors"))
                    assertEquals("""{"10":{"2026-09-01":2}}""", result.getString("timeline_snapshot"))
                    assertTrue(result.getBoolean("fetched_all_assets"))
                }
                statement.executeUpdate("""UPDATE crontab_history SET album_sync_cursors = '{"10":"42"}' WHERE id = 1""")
                statement.executeQuery("SELECT album_sync_cursors FROM crontab_history WHERE id = 1").use { result ->
                    assertTrue(result.next())
                    assertEquals("""{"10":"42"}""", result.getString(1))
                }
                statement.executeUpdate(
                    """
                    INSERT INTO crontab_history
                        (crontab_id, start_time, timeline_snapshot, fetched_all_assets)
                    VALUES
                        (2, 200, NULL, 0)
                    """.trimIndent()
                )
            }
        }
    }

    private fun flyway(databaseUrl: String, target: String? = null): Flyway {
        val configuration = Flyway.configure()
            .dataSource(databaseUrl, null, null)
            .locations(DatabaseMigration.MIGRATION_SQL_PATH_IN_JVM)
            .baselineOnMigrate(true)
            .validateOnMigrate(true)
        target?.let(configuration::target)
        return configuration.load()
    }

    private fun Connection.indexNames(): Set<String> =
        createStatement().use { statement ->
            statement.executeQuery("SELECT name FROM sqlite_master WHERE type = 'index'").use { result ->
                buildSet {
                    while (result.next()) {
                        add(result.getString("name"))
                    }
                }
            }
        }

    private fun Connection.columnNames(tableName: String): Set<String> =
        createStatement().use { statement ->
            statement.executeQuery("PRAGMA table_info($tableName)").use { result ->
                buildSet {
                    while (result.next()) {
                        add(result.getString("name"))
                    }
                }
            }
        }

    private fun Connection.analyzedIndexNames(): Set<String> =
        createStatement().use { statement ->
            statement.executeQuery("SELECT idx FROM sqlite_stat1 WHERE idx IS NOT NULL").use { result ->
                buildSet {
                    while (result.next()) {
                        add(result.getString("idx"))
                    }
                }
            }
        }

    private fun Connection.assertUsesIndex(query: String, indexName: String) {
        assertTrue(
            queryPlan(query).any { it.contains("USING") && it.contains("INDEX $indexName") },
            "Expected query plan to use $indexName",
        )
    }

    private fun Connection.queryPlan(query: String): List<String> =
        createStatement().use { statement ->
            statement.executeQuery("EXPLAIN QUERY PLAN $query").use { result ->
                buildList {
                    while (result.next()) {
                        add(result.getString("detail"))
                    }
                }
            }
        }

    private companion object {
        const val LEGACY_INDEX_NAME = "idx_crontab_history_latest_completed"
        const val HISTORY_INDEX_NAME = "idx_crontab_history_crontab_start_time"
        const val ASSET_ALBUM_INDEX_NAME = "idx_asset_album_id"
        const val ASSET_DATE_INDEX_NAME = "idx_asset_date_taken"
        const val DETAIL_HISTORY_INDEX_NAME = "idx_crontab_history_detail_history_id"
        const val DETAIL_ASSET_HISTORY_INDEX_NAME = "idx_crontab_history_detail_asset_history"

        val EXPECTED_INDEX_NAMES = setOf(
            HISTORY_INDEX_NAME,
            ASSET_ALBUM_INDEX_NAME,
            ASSET_DATE_INDEX_NAME,
            DETAIL_HISTORY_INDEX_NAME,
            DETAIL_ASSET_HISTORY_INDEX_NAME,
        )

        const val LATEST_COMPLETED_HISTORY_QUERY = """
            SELECT timeline_snapshot
            FROM crontab_history
            WHERE crontab_id = 1
              AND id <> 3
              AND end_time IS NOT NULL
            ORDER BY start_time DESC
            LIMIT 1
        """
        const val ASSET_BY_ALBUM_QUERY = "SELECT id FROM asset WHERE album_id = 1"
        const val ASSET_BY_DATE_QUERY = "SELECT id FROM asset WHERE date_taken BETWEEN 0 AND 1"
        const val DETAIL_BY_HISTORY_QUERY =
            "SELECT id FROM crontab_history_detail WHERE crontab_history_id = 1"
        const val DETAIL_BY_ASSET_QUERY =
            "SELECT id FROM crontab_history_detail WHERE asset_id = 1 AND crontab_history_id = 1"
    }
}
