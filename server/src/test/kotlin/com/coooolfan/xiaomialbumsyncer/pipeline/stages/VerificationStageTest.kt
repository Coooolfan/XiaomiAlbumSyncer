package com.coooolfan.xiaomialbumsyncer.pipeline.stages

import com.coooolfan.xiaomialbumsyncer.icloud.ICloudAccountService
import com.coooolfan.xiaomialbumsyncer.icloud.ICloudPhotos
import com.coooolfan.xiaomialbumsyncer.model.*
import com.coooolfan.xiaomialbumsyncer.service.CloudMediaService
import com.coooolfan.xiaomialbumsyncer.service.NotifyService
import com.coooolfan.xiaomialbumsyncer.xiaomicloud.TokenManager
import com.coooolfan.xiaomialbumsyncer.xiaomicloud.XiaoMiApi
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.dialect.SQLiteDialect
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.newKSqlClient
import org.babyfish.jimmer.sql.runtime.ConnectionManager
import org.babyfish.jimmer.sql.runtime.DefaultDatabaseNamingStrategy
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64

class VerificationStageTest {
    @TempDir lateinit var directory: Path
    private val content = "original photo".toByteArray()
    private val digest = MessageDigest.getInstance("SHA-1").digest(content)
    private val checksums = listOf(
        CloudProvider.ICLOUD to Base64.getEncoder().encodeToString(digest),
        CloudProvider.ICLOUD to Base64.getEncoder().encodeToString(byteArrayOf(1) + digest),
    )

    @Test fun corruptionClearsDownloadStateAndRemovesFile() {
        checksums.forEach { (provider, checksum) ->
            withDetail(provider, checksum) { sql, stage, detail ->
                Files.writeString(Path.of(detail.filePath), "corrupted")
                assertThrows(IllegalStateException::class.java) { stage.process(detail) }
                val stored = sql.findById(CrontabHistoryDetail::class, detail.id)!!
                assertFalse(stored.downloadCompleted)
                assertFalse(stored.sha1Verified)
                assertFalse(Files.exists(Path.of(detail.filePath)))
            }
        }
    }

    @Test fun validContentKeepsDownloadAndMarksVerified() {
        checksums.forEach { (provider, checksum) ->
            withDetail(provider, checksum) { sql, stage, detail ->
                val result = stage.process(detail)
                val stored = sql.findById(CrontabHistoryDetail::class, detail.id)!!
                assertTrue(result.sha1Verified)
                assertTrue(stored.downloadCompleted)
                assertTrue(stored.sha1Verified)
                assertArrayEquals(content, Files.readAllBytes(Path.of(detail.filePath)))
            }
        }
    }

    @Test fun unsupportedChecksumDoesNotDeleteFile() {
        listOf(
            CloudProvider.ICLOUD to "invalid-base64",
            CloudProvider.ICLOUD to Base64.getEncoder().encodeToString(byteArrayOf(2, 3)),
        ).forEach { (provider, checksum) ->
            withDetail(provider, checksum) { sql, stage, detail ->
                assertThrows(RuntimeException::class.java) { stage.process(detail) }
                assertTrue(sql.findById(CrontabHistoryDetail::class, detail.id)!!.downloadCompleted)
                assertArrayEquals(content, Files.readAllBytes(Path.of(detail.filePath)))
            }
        }
    }

    @Test fun unreadableFileDoesNotResetDownloadState() {
        withDetail(checksums.first().first, checksums.first().second) { sql, stage, detail ->
            Files.delete(Path.of(detail.filePath))
            assertThrows(java.nio.file.NoSuchFileException::class.java) { stage.process(detail) }
            assertTrue(sql.findById(CrontabHistoryDetail::class, detail.id)!!.downloadCompleted)
        }
    }

    @Test fun fileIsRemovedEvenWhenStateUpdateFails() {
        withDetail(checksums.first().first, checksums.first().second) { sql, stage, detail ->
            sql.javaClient.connectionManager.execute { connection ->
                connection.createStatement().use {
                    it.execute("CREATE TRIGGER fail_reset BEFORE UPDATE ON crontab_history_detail BEGIN SELECT RAISE(ABORT, 'reset failed'); END")
                }
            }
            Files.writeString(Path.of(detail.filePath), "corrupted")
            val failure = assertThrows(IllegalStateException::class.java) { stage.process(detail) }
            assertEquals(1, failure.suppressed.size)
            assertFalse(Files.exists(Path.of(detail.filePath)))
        }
    }

    @Test fun xiaomiSkipsChecksumAndKeepsFile() {
        withDetail(CloudProvider.XIAOMI, "not-a-sha1") { sql, stage, detail ->
            Files.writeString(Path.of(detail.filePath), "different content")
            val result = stage.process(detail)
            val stored = sql.findById(CrontabHistoryDetail::class, detail.id)!!
            assertTrue(result.sha1Verified)
            assertTrue(stored.sha1Verified)
            assertTrue(stored.downloadCompleted)
            assertEquals("different content", Files.readString(Path.of(detail.filePath)))
        }
    }

    @Test fun xiaomiDoesNotReadFileDuringVerification() {
        withDetail(CloudProvider.XIAOMI, "unused") { sql, stage, detail ->
            Files.delete(Path.of(detail.filePath))
            assertTrue(stage.process(detail).sha1Verified)
            assertTrue(sql.findById(CrontabHistoryDetail::class, detail.id)!!.downloadCompleted)
        }
    }

    private fun withDetail(
        provider: CloudProvider,
        checksum: String,
        action: (KSqlClient, VerificationStage, CrontabHistoryDetail) -> Unit,
    ) {
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:sqlite:${Files.createTempFile(directory, "verification-", ".db")}"
            maximumPoolSize = 1
        }).use { source ->
            Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate()
            val sql = newKSqlClient {
                setDialect(SQLiteDialect())
                setConnectionManager(ConnectionManager.simpleConnectionManager(source))
                setDatabaseNamingStrategy(DefaultDatabaseNamingStrategy.LOWER_CASE)
            }
            val notify = NotifyService(sql)
            val media = CloudMediaService(sql, XiaoMiApi(TokenManager(sql, notify)), ICloudPhotos(ICloudAccountService(sql, notify)))
            val account = sql.saveCommand(ProviderAccount {
                this.provider = provider
                nickname = "test"
                userId = "test"
                credentials = "{}"
            }, SaveMode.INSERT_ONLY).execute().modifiedEntity
            val album = sql.saveCommand(Album {
                accountId = account.id
                remoteKey = "album"
                name = "album"
                assetCount = null
                lastUpdateTime = null
                shadow = false
            }, SaveMode.INSERT_ONLY).execute().modifiedEntity
            val asset = sql.saveCommand(Asset {
                albumId = album.id
                remoteKey = "asset"
                fileName = "photo.jpg"
                type = AssetType.IMAGE
                recordingType = null
                dateTaken = Instant.EPOCH
                this.checksum = checksum
                mimeType = "image/jpeg"
                title = "photo"
                size = content.size.toLong()
            }, SaveMode.INSERT_ONLY).execute().modifiedEntity
            val crontab = sql.saveCommand(Crontab {
                accountId = account.id
                name = "test"
                description = ""
                enabled = false
                syncMode = CrontabSyncMode.FULL
                config = CrontabConfig("0 0 * * * ?", "UTC", directory.toString(), true, true, false, null, checkSha1 = true)
            }, SaveMode.INSERT_ONLY).execute().modifiedEntity
            val history = sql.saveCommand(CrontabHistory {
                crontabId = crontab.id
                startTime = Instant.EPOCH
                endTime = null
                timelineSnapshot = null
                syncCursors = null
                fetchedAllAssets = true
            }, SaveMode.INSERT_ONLY).execute().modifiedEntity
            val file = Files.createTempFile(directory, "photo-", ".jpg")
            Files.write(file, content)
            val detail = sql.saveCommand(CrontabHistoryDetail {
                crontabHistoryId = history.id
                assetId = asset.id
                downloadTime = Instant.EPOCH
                filePath = file.toString()
                downloadCompleted = true
                sha1Verified = false
                exifFilled = true
                fsTimeUpdated = true
                message = null
            }, SaveMode.INSERT_ONLY).execute().modifiedEntity
            val context = CrontabHistoryDetail(detail) {
                this.asset = asset
                crontabHistory = CrontabHistory(history) { this.crontab = crontab }
            }
            action(sql, VerificationStage(sql, media), context)
        }
    }
}
