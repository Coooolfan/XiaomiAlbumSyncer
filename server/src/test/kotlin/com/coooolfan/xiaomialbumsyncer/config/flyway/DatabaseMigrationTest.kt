package com.coooolfan.xiaomialbumsyncer.config.flyway

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.sql.DriverManager

class DatabaseMigrationTest {

    @Test
    fun addsIndexForLatestCompletedCrontabHistory(@TempDir tempDir: Path) {
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
            }
        }

        assertEquals(1, flyway(databaseUrl).migrate().migrationsExecuted)

        DriverManager.getConnection(databaseUrl).use { connection ->
            val indexSql = connection.prepareStatement(
                "SELECT sql FROM sqlite_master WHERE type = 'index' AND name = ?"
            ).use { statement ->
                statement.setString(1, INDEX_NAME)
                statement.executeQuery().use { result ->
                    assertTrue(result.next())
                    result.getString("sql")
                }
            }
            assertTrue(indexSql.contains("(crontab_id, start_time DESC)"))
            assertTrue(indexSql.contains("WHERE end_time IS NOT NULL"))

            val queryPlan = connection.createStatement().use { statement ->
                statement.executeQuery("EXPLAIN QUERY PLAN $LATEST_COMPLETED_HISTORY_QUERY").use { result ->
                    buildList {
                        while (result.next()) {
                            add(result.getString("detail"))
                        }
                    }
                }
            }
            assertTrue(queryPlan.any { it.contains("USING INDEX $INDEX_NAME") })
            assertFalse(queryPlan.any { it.contains("USE TEMP B-TREE") })

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
    fun acceptsIndexCreatedBeforeFlywayMigration(@TempDir tempDir: Path) {
        val databaseUrl = "jdbc:sqlite:${tempDir.resolve("preindexed.db").toAbsolutePath()}"

        flyway(databaseUrl, target = "0.16.2").migrate()
        DriverManager.getConnection(databaseUrl).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """
                    CREATE INDEX $INDEX_NAME
                        ON crontab_history (crontab_id, start_time DESC)
                        WHERE end_time IS NOT NULL
                    """.trimIndent()
                )
            }
        }

        assertEquals(1, flyway(databaseUrl).migrate().migrationsExecuted)

        DriverManager.getConnection(databaseUrl).use { connection ->
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

    private companion object {
        const val INDEX_NAME = "idx_crontab_history_latest_completed"
        const val LATEST_COMPLETED_HISTORY_QUERY = """
            SELECT timeline_snapshot
            FROM crontab_history
            WHERE crontab_id = 1
              AND id <> 3
              AND end_time IS NOT NULL
            ORDER BY start_time DESC
            LIMIT 1
        """
    }
}
