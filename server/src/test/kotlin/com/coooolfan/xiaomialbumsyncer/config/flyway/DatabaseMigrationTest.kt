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

        assertEquals(1, flyway(databaseUrl).migrate().migrationsExecuted)

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

        assertEquals(1, flyway(databaseUrl).migrate().migrationsExecuted)

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
