package com.coooolfan.xiaomialbumsyncer.config.flyway

import org.flywaydb.core.api.MigrationVersion
import org.flywaydb.core.api.callback.Callback
import org.flywaydb.core.api.callback.Context
import org.flywaydb.core.api.callback.Event
import org.slf4j.LoggerFactory

/** 在 0.20.0 迁移前清理未使用的占位账号，并合并重复相册，保留资产和任务关联。 */
class AlbumDeduplicationCallback : Callback {
    private val log = LoggerFactory.getLogger(AlbumDeduplicationCallback::class.java)

    override fun supports(event: Event, context: Context?): Boolean =
        event == Event.BEFORE_EACH_MIGRATE &&
            context?.migrationInfo?.version == MigrationVersion.fromVersion("0.20.0")

    override fun canHandleInTransaction(event: Event, context: Context) = true

    override fun getCallbackName() = "album-deduplication"

    override fun handle(event: Event, context: Context) {
        context.connection.createStatement().use { statement ->
            val placeholders = statement.executeUpdate("""
                DELETE FROM xiaomi_account
                WHERE nickname = '默认账号' AND pass_token = '-' AND user_id = '-'
                  AND NOT EXISTS (SELECT 1 FROM album WHERE account_id = xiaomi_account.id)
                  AND NOT EXISTS (SELECT 1 FROM crontab WHERE account_id = xiaomi_account.id)
            """.trimIndent())
            if (placeholders > 0) {
                log.info("已清理 {} 条未使用的占位账号记录", placeholders)
            }
            // 优先保留非影子相册，同等条件下保留最小 ID。
            statement.executeUpdate("""
                CREATE TEMP TABLE album_deduplication AS
                SELECT id AS old_id,
                       FIRST_VALUE(id) OVER (
                           PARTITION BY account_id, remote_id ORDER BY shadow, id
                       ) AS keep_id
                FROM album
            """.trimIndent())
            statement.executeUpdate("DELETE FROM album_deduplication WHERE old_id = keep_id")
            val duplicates = statement.executeQuery("SELECT COUNT(*) FROM album_deduplication").use {
                it.next()
                it.getInt(1)
            }
            if (duplicates == 0) {
                statement.executeUpdate("DROP TABLE album_deduplication")
                return
            }
            statement.executeUpdate("""
                UPDATE asset
                SET album_id = (SELECT keep_id FROM album_deduplication WHERE old_id = asset.album_id)
                WHERE album_id IN (SELECT old_id FROM album_deduplication)
            """.trimIndent())
            statement.executeUpdate("""
                INSERT INTO crontab_album_mapping (crontab_id, album_id)
                SELECT m.crontab_id, d.keep_id
                FROM crontab_album_mapping m
                JOIN album_deduplication d ON d.old_id = m.album_id
                WHERE true
                ON CONFLICT (crontab_id, album_id) DO NOTHING
            """.trimIndent())
            statement.executeUpdate("DELETE FROM crontab_album_mapping WHERE album_id IN (SELECT old_id FROM album_deduplication)")
            statement.executeUpdate("DELETE FROM album WHERE id IN (SELECT old_id FROM album_deduplication)")
            log.info("已合并 {} 条重复相册记录", duplicates)
            statement.executeUpdate("DROP TABLE album_deduplication")
        }
    }
}
