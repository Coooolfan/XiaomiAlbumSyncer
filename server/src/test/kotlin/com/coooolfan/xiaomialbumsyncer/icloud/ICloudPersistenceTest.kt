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
            val accounts = ICloudAccountService(sql, notify)
            val media = CloudMediaService(sql, XiaoMiApi(TokenManager(sql, notify)), ICloudPhotos(accounts))
            val account = sql.saveCommand(ProviderAccount {
                nickname = "Apple"
                provider = CloudProvider.ICLOUD
                userId = "apple@example.com"
                credentials = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().writeValueAsString(
                    ICloudCredentials("apple@example.com", "password", "cn", headers = mutableMapOf("X-Apple-TwoSV-Trust-Token" to "trust")))
            }, org.babyfish.jimmer.sql.ast.mutation.SaveMode.INSERT_ONLY).execute().modifiedEntity
            source.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeUpdate("CREATE TABLE credential_writes (account_id INTEGER)")
                    statement.executeUpdate("CREATE TRIGGER audit_credentials AFTER UPDATE OF credentials ON provider_account BEGIN INSERT INTO credential_writes VALUES (NEW.id); END")
                }
            }
            fun credentialWrites(): Int = source.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT count(*) FROM credential_writes").use { rows -> rows.next(); rows.getInt(1) }
                }
            }
            assertEquals("cn", accounts.status(account.id).domain)
            accounts.withClient(account.id) { }
            assertEquals(0, credentialWrites())
            accounts.withClient(account.id) { client ->
                client.credentials.cookies = listOf("session-cookie")
                client.credentials.headers["X-Apple-Session-Token"] = "renewed-session"
            }
            assertEquals(1, credentialWrites())
            val restored = ICloudAccountService(sql, notify)
            restored.withClient(account.id) { client ->
                assertEquals("trust", client.credentials.headers["X-Apple-TwoSV-Trust-Token"])
                assertEquals("renewed-session", client.credentials.headers["X-Apple-Session-Token"])
                assertEquals(listOf("session-cookie"), client.credentials.cookies)
            }
            assertEquals(1, credentialWrites())
            assertThrows(com.coooolfan.xiaomialbumsyncer.exception.BadRequestException::class.java) {
                restored.withClient(account.id) { throw ICloudApiException(503) }
            }
            assertEquals("READY", restored.status(account.id).state)
            assertEquals(1, credentialWrites())
            repeat(2) {
                assertThrows(com.coooolfan.xiaomialbumsyncer.exception.BadRequestException::class.java) {
                    restored.withClient(account.id) { throw ICloudApiException(401) }
                }
            }
            assertEquals("SESSION_EXPIRED", restored.status(account.id).state)
            assertEquals(2, credentialWrites())
            assertEquals("Apple", sql.findById(ProviderAccount::class, account.id)!!.nickname)
            assertFalse(Files.exists(directory.resolve("icloud.key")))
            source.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeUpdate("INSERT INTO crontab(id,name,config,description,enabled,account_id,sync_mode) VALUES (100,'cursor','{}','',0,${account.id},'CURSOR')")
                    statement.executeUpdate("""INSERT INTO crontab_history(id,crontab_id,start_time,timeline_snapshot,album_sync_cursors,fetched_all_assets) VALUES (100,100,0,NULL,'{"42":"legacy-tag"}',0)""")
                }
            }
            assertEquals(mapOf("42" to "legacy-tag"), sql.findById(CrontabHistory::class, 100L)!!.syncCursors)
            val album = sql.saveCommand(Album {
                accountId = account.id
                remoteKey = ICloudAlbumKey("PrimarySync", "album").encode()
                name = "Photos"
                shadow = false
                lastUpdateTime = Instant.EPOCH
                assetCount = 1
            }, org.babyfish.jimmer.sql.ast.mutation.SaveMode.INSERT_ONLY).execute().modifiedEntity
            fun resource(checksum: String) = Asset {
                remoteKey = ICloudResourceKey("record", "resOriginal").encode()
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
            val mixed = media.saveAssets(listOf(Asset(resource("first")) { title = "updated" }, resource("second")))
            assertEquals(first.id, mixed.first { it.sha1 == first.sha1 }.id)
            assertEquals("updated", sql.findById(Asset::class, first.id)!!.title)
            assertNotEquals(first.id, mixed.first { it.sha1 == "icloud:second" }.id)
            assertEquals("record", ICloudResourceKey.decode(sql.findById(Asset::class, first.id)!!.remoteKey).assetId)
            val xiaomi = Asset(resource("xiaomi")) {
                remoteKey = first.id.toString()
            }
            val isolated = media.saveAssets(listOf(xiaomi)).single()
            assertNotEquals(first.id, isolated.id)
            assertEquals(first.id.toString(), isolated.remoteKey)
            assertEquals(isolated.id, media.saveAssets(listOf(xiaomi)).single().id)
            assertEquals(first.remoteKey, sql.findById(Asset::class, first.id)!!.remoteKey)

            val otherAlbum = sql.saveCommand(Album(album) {
                id = album.id + 1
                remoteKey = ICloudAlbumKey("PrimarySync", "other-album").encode()
                name = "Other"
            }, org.babyfish.jimmer.sql.ast.mutation.SaveMode.INSERT_ONLY).execute().modifiedEntity
            val otherMembership = media.saveAssets(listOf(Asset(resource("first")) { albumId = otherAlbum.id })).single()
            assertNotEquals(first.id, otherMembership.id)
            assertEquals(first.remoteKey, otherMembership.remoteKey)
            assertEquals(otherMembership.id, media.saveAssets(listOf(Asset(resource("first")) { albumId = otherAlbum.id })).single().id)
            assertEquals(1, media.saveAssets(listOf(resource("first"), resource("first"))).size)

            val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
            val ranks = mutableListOf<Long>()
            val assetQueries = mutableListOf<com.fasterxml.jackson.databind.JsonNode>()
            val lookups = mutableListOf<Pair<String, String>>()
            val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext("/") { exchange ->
                val body = mapper.readTree(exchange.requestBody.readAllBytes())
                val response: Any = if (exchange.requestURI.path == "/setup/validate") {
                    mapOf("dsInfo" to mapOf("hsaVersion" to 2, "dsid" to "id"), "hsaTrustedBrowser" to true,
                        "webservices" to mapOf("ckdatabasews" to mapOf("url" to "http://127.0.0.1:${server.address.port}")))
                } else if (exchange.requestURI.path.endsWith("/zones/list")) {
                    mapOf("zones" to listOf("PrimarySync", "SharedSync-example").map { mapOf("zoneID" to mapOf("zoneName" to it)) })
                } else if (body.at("/query/recordType").asText() == "CPLAlbumByPositionLive") {
                    mapper.readTree("""{"records":[{"recordType":"CPLAlbum","recordName":"album","fields":{"albumNameEnc":{"value":"UGhvdG9z"}}}]}""")
                } else if (body.path("records").isArray) {
                    val name = body.at("/records/0/recordName").asText()
                    lookups += body.at("/zoneID/zoneName").asText() to name
                    val fields = when (name) {
                        "direct" -> mapOf("resOriginalRes" to mapOf("value" to mapOf("downloadURL" to "https://example.com/direct")))
                        "record" -> mapOf("masterRef" to mapOf("value" to mapOf("recordName" to "current-master")))
                        "current-master" -> mapOf("resOriginalRes" to mapOf("value" to mapOf("downloadURL" to "https://example.com/master")))
                        else -> emptyMap<String, Any>()
                    }
                    mapOf("records" to listOf(mapOf("recordName" to name, "fields" to fields)))
                } else if (body.at("/query/recordType").asText() == "CheckIndexingState") {
                    mapper.readTree("""{"records":[{"fields":{"state":{"value":"FINISHED"}}}]}""")
                } else {
                    assetQueries.add(body)
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
                val photos = ICloudPhotos(accounts)
                val masterResource = photos.resolveResource(client, "PrimarySync", ICloudResourceKey.decode(first.remoteKey))
                assertEquals("https://example.com/master", masterResource.path("downloadURL").asText())
                assertEquals(listOf("PrimarySync" to "record", "PrimarySync" to "current-master"), lookups)
                lookups.clear()
                val direct = photos.resolveResource(client, "SharedSync-example", ICloudResourceKey("direct", "resOriginal"))
                assertEquals("https://example.com/direct", direct.path("downloadURL").asText())
                assertEquals(listOf("SharedSync-example" to "direct"), lookups)
                val files = mutableListOf<Asset>()
                val count = ICloudPhotos(accounts).fetchAssets(album, client) { files += it }
                assertEquals(2L, count)
                assertEquals(listOf(0L, 1L, 2L), ranks)
                assertEquals(2, files.map { it.remoteKey }.toSet().size)
                val remoteAlbums = photos.fetchAlbums(account.id, client)
                assertEquals(8, remoteAlbums.size)
                assertTrue(remoteAlbums.all { it.assetCount == null && it.lastUpdateTime == null })
                assertEquals(8, remoteAlbums.map { it.remoteKey }.toSet().size)
                for (name in listOf("__all__", "__hidden__", "__favorites__", "album")) {
                    assetQueries.clear()
                    val selected = Album(remoteAlbums.first { ICloudAlbumKey.decode(it.remoteKey) == ICloudAlbumKey("PrimarySync", name) }) { id = album.id }
                    photos.fetchAssets(selected, client) { }
                    val query = assetQueries.first().path("query")
                    val expected = when (name) {
                        "__all__" -> "CPLAssetAndMasterByAssetDateWithoutHiddenOrDeleted"
                        "__hidden__" -> "CPLAssetAndMasterHiddenByAssetDate"
                        "__favorites__" -> "CPLAssetAndMasterInSmartAlbumByAssetDate"
                        else -> "CPLContainerRelationLiveByAssetDate"
                    }
                    assertEquals(expected, query.path("recordType").asText())
                    val filters = query.path("filterBy").associate { it.path("fieldName").asText() to it.at("/fieldValue/value").asText() }
                    assertEquals(if (name == "__favorites__") "FAVORITE" else null, filters["smartAlbum"])
                    assertEquals(if (name == "album") "album" else null, filters["parentId"])
                }

            } finally { server.stop(0) }

        }
    }
}
