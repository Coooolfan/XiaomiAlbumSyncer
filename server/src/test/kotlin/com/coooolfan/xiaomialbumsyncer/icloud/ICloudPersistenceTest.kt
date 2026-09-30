package com.coooolfan.xiaomialbumsyncer.icloud

import com.coooolfan.xiaomialbumsyncer.model.*
import com.coooolfan.xiaomialbumsyncer.service.CloudMediaService
import com.coooolfan.xiaomialbumsyncer.service.NotifyService
import com.coooolfan.xiaomialbumsyncer.xiaomicloud.TokenManager
import com.coooolfan.xiaomialbumsyncer.xiaomicloud.XiaoMiApi
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.babyfish.jimmer.sql.dialect.SQLiteDialect
import org.babyfish.jimmer.sql.kt.newKSqlClient
import org.babyfish.jimmer.sql.runtime.ConnectionManager
import org.babyfish.jimmer.sql.runtime.DefaultDatabaseNamingStrategy
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

class ICloudPersistenceTest {
    @Test fun encryptsWithReusableKeyAndRejectsTampering(@TempDir directory: Path) {
        fun secrets() = ICloudSecrets().also {
            ICloudSecrets::class.java.getDeclaredField("databasePath").apply { isAccessible = true }.set(it, directory.resolve("db.sqlite").toString())
        }
        val encrypted = secrets().encrypt("password-and-cookies")
        assertFalse(encrypted.contains("password"))
        assertEquals("password-and-cookies", secrets().decrypt(encrypted))
        val bytes = java.util.Base64.getDecoder().decode(encrypted)
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        assertThrows(javax.crypto.AEADBadTagException::class.java) { secrets().decrypt(java.util.Base64.getEncoder().encodeToString(bytes)) }
        val key = directory.resolve("icloud.key")
        assertEquals(32L, Files.size(key))
        if (key.fileSystem.supportedFileAttributeViews().contains("posix")) {
            assertEquals("rw-------", java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(key)))
        }
    }

    @Test fun upsertsRemoteResourcesWithoutChangingDownloadIdentity(@TempDir directory: Path) {
        val source = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:sqlite:${directory.resolve("icloud.db")}"
            maximumPoolSize = 1
        })
        source.use {
            Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate()
            val sql = newKSqlClient {
                setDialect(SQLiteDialect())
                setConnectionManager(ConnectionManager.simpleConnectionManager(source))
                setDatabaseNamingStrategy(DefaultDatabaseNamingStrategy.LOWER_CASE)
            }
            val notify = NotifyService(sql)
            val accounts = ICloudAccountService(sql, ICloudSecrets(), notify)
            val media = CloudMediaService(sql, XiaoMiApi(TokenManager(sql, notify)), ICloudPhotos(accounts))
            val account = sql.saveCommand(XiaomiAccount {
                nickname = "Apple"
                provider = CloudProvider.ICLOUD
                userId = "apple@example.com"
                passToken = ""
            }, org.babyfish.jimmer.sql.ast.mutation.SaveMode.INSERT_ONLY).execute().modifiedEntity
            val album = sql.saveCommand(Album {
                accountId = account.id
                remoteId = 1
                name = "Photos"
                shadow = false
                lastUpdateTime = Instant.EPOCH
                assetCount = 1
                cloudAlbum = ICloudAlbumRef("PrimarySync", "album", "query")
            }, org.babyfish.jimmer.sql.ast.mutation.SaveMode.INSERT_ONLY).execute().modifiedEntity
            fun resource(checksum: String) = Asset {
                xiaomiId = null
                remoteKey = "icloud:${account.id}:${album.id}:record:$checksum"
                cloudAsset = ICloudAssetRef("PrimarySync", "record", "master", "resOriginal", checksum)
                albumId = album.id
                fileName = "photo.jpg"
                type = AssetType.IMAGE
                recordingType = null
                dateTaken = Instant.EPOCH
                sha1 = "icloud:$checksum"
                mimeType = "image/jpeg"
                title = "photo"
                size = 10
            }
            val first = media.saveAssets(listOf(resource("first"))).single()
            val repeated = media.saveAssets(listOf(resource("first"))).single()
            val replaced = media.saveAssets(listOf(resource("changed"))).single()
            assertEquals(first.id, repeated.id)
            assertNotEquals(first.id, replaced.id)
            assertEquals("PrimarySync", sql.findById(Asset::class, first.id)!!.cloudAsset!!.zone)
            val xiaomi = Asset(resource("xiaomi")) {
                id = first.id
                xiaomiId = first.id
                remoteKey = "xiaomi:${account.id}:${first.id}"
                cloudAsset = null
            }
            val isolated = media.saveAssets(listOf(xiaomi)).single()
            assertNotEquals(first.id, isolated.id)
            assertEquals(first.id, isolated.xiaomiId)
            assertEquals(isolated.id, media.saveAssets(listOf(xiaomi)).single().id)
            assertNotNull(sql.findById(Asset::class, first.id)!!.cloudAsset)

            val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
            val ranks = mutableListOf<Long>()
            val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext("/") { exchange ->
                val body = mapper.readTree(exchange.requestBody.readAllBytes())
                val response: Any = if (exchange.requestURI.path == "/setup/validate") {
                    mapOf("dsInfo" to mapOf("hsaVersion" to 2, "dsid" to "id"), "hsaTrustedBrowser" to true,
                        "webservices" to mapOf("ckdatabasews" to mapOf("url" to "http://127.0.0.1:${server.address.port}")))
                } else if (body.at("/query/recordType").asText() == "CheckIndexingState") {
                    mapper.readTree("""{"records":[{"fields":{"state":{"value":"FINISHED"}}}]}""")
                } else {
                    val rank = body.at("/query/filterBy/0/fieldValue/value").asLong()
                    ranks += rank
                    if (rank < 2) {
                        mapper.readTree("""{"records":[
                            {"recordType":"CPLAsset","recordName":"asset-$rank","fields":{"masterRef":{"value":{"recordName":"master-$rank"}},"assetDate":{"value":1000}}},
                            {"recordType":"CPLMaster","recordName":"master-$rank","fields":{"filenameEnc":{"value":"YS5qcGc="},"resOriginalFileType":{"value":"public.jpeg"},"resOriginalRes":{"value":{"size":10,"fileChecksum":"checksum-$rank"}}}}
                        ]}""")
                    } else mapOf("records" to emptyList<String>())
                }
                val bytes = mapper.writeValueAsBytes(response)
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            server.start()
            try {
                val root = "http://127.0.0.1:${server.address.port}"
                val client = ICloudClient(ICloudCredentials("apple", "password", "com", headers = mutableMapOf("X-Apple-Session-Token" to "session")),
                    ICloudClient.Endpoints("$root/auth", "$root/setup", root))
                val files = mutableListOf<Asset>()
                val count = ICloudPhotos(accounts).fetchAssets(album, client) { files += it }
                assertEquals(2L, count)
                assertEquals(listOf(0L, 1L, 2L), ranks)
                assertEquals(2, files.map { it.remoteKey }.toSet().size)
            } finally { server.stop(0) }

        }
    }
}
