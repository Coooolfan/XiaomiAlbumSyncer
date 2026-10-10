package com.coooolfan.xiaomialbumsyncer.icloud

import com.coooolfan.xiaomialbumsyncer.model.*
import com.coooolfan.xiaomialbumsyncer.service.NotifyService
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sun.net.httpserver.HttpServer
import org.babyfish.jimmer.sql.dialect.SQLiteDialect
import org.babyfish.jimmer.sql.kt.newKSqlClient
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.time.Instant

class ICloudChangesTest {
    private val mapper = jacksonObjectMapper()
    private fun album(id: Long, name: String) = Album {
        this.id = id; remoteKey = ICloudAlbumKey("PrimarySync", name).encode(); accountId = 1; this.name = name
        shadow = false; lastUpdateTime = Instant.EPOCH; assetCount = 0
    }
    private fun record(type: String, name: String, fields: Map<String, Any> = emptyMap()): JsonNode = mapper.valueToTree(mapOf(
        "recordType" to type, "recordName" to name, "fields" to fields.mapValues { mapOf("value" to it.value) }))
    private fun asset(name: String, hidden: Int = 0, favorite: Int = 0) = record("CPLAsset", name, mapOf(
        "masterRef" to mapOf("recordName" to "master-$name"), "assetDate" to 1000,
        "isHidden" to hidden, "isFavorite" to favorite))
    private fun master(name: String) = record("CPLMaster", "master-$name", mapOf(
        "filenameEnc" to "cGhvdG8uanBn", "resOriginalFileType" to "public.jpeg",
        "resOriginalRes" to mapOf("size" to 10, "fileChecksum" to "checksum-$name")))
    private fun page(token: String, more: Boolean, records: List<JsonNode>) = mapOf("zones" to listOf(mapOf(
        "zoneID" to mapOf("zoneName" to "PrimarySync"), "syncToken" to token, "moreComing" to more, "records" to records)))

    private fun withServer(respond: (String, JsonNode) -> Any, test: (ICloudPhotos, ICloudClient) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            try {
                val body = mapper.readTree(exchange.requestBody.readAllBytes())
                val response = if (exchange.requestURI.path == "/setup/validate") mapOf(
                    "dsInfo" to mapOf("hsaVersion" to 2, "dsid" to "id"), "hsaTrustedBrowser" to true,
                    "webservices" to mapOf("ckdatabasews" to mapOf("url" to "http://127.0.0.1:${server.address.port}")))
                else respond(exchange.requestURI.path.substringAfterLast('/'), body)
                val bytes = mapper.writeValueAsBytes(response)
                exchange.sendResponseHeaders(200, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
            } catch (e: Throwable) {
                exchange.sendResponseHeaders(500, -1); exchange.close()
            }
        }
        server.start()
        try {
            val sql = newKSqlClient { setDialect(SQLiteDialect()) }
            val root = "http://127.0.0.1:${server.address.port}"
            val client = ICloudClient(ICloudCredentials("apple", "password", "com", headers = mutableMapOf("X-Apple-Session-Token" to "session")),
                ICloudClient.Endpoints("$root/auth", "$root/setup", root))
            test(ICloudPhotos(ICloudAccountService(sql, NotifyService(sql))), client)
        } finally { server.stop(0) }
    }

    @Test fun lookupDeduplicatesAndSplitsRequestsAtTheCloudKitLimit() {
        val batches = mutableListOf<List<String>>()
        withServer({ path, body ->
            assertEquals("lookup", path)
            val names = body.path("records").map { it.path("recordName").asText() }
            batches += names
            mapOf("records" to names.map { record("CPLAsset", it) })
        }) { _, client ->
            val names = (0..200).map { "asset-$it" }
            assertTrue(lookupRecords(client, "PrimarySync", emptyList()).isEmpty())
            val records = lookupRecords(client, "PrimarySync", names + names.first())
            assertEquals(listOf(200, 1), batches.map { it.size })
            assertEquals(names, records.map { it.path("recordName").asText() })
        }
    }

    @Test fun lookupFiltersUnavailableRecordsAndRejectsIncompleteOrFailedResponses() {
        withServer({ _, _ -> mapOf("records" to listOf(
            mapOf("recordName" to "live"), mapOf("recordName" to "deleted", "deleted" to true),
            mapOf("recordName" to "missing", "serverErrorCode" to "NOT_FOUND"),
            mapOf("recordName" to "unknown", "serverErrorCode" to "UNKNOWN_ITEM"),
        )) }) { _, client ->
            assertEquals(listOf("live"), lookupRecords(client, "PrimarySync", listOf("live", "deleted", "missing", "unknown"))
                .map { it.path("recordName").asText() })
        }
        for (response in listOf(
            mapOf("records" to emptyList<Any>()),
            mapOf("records" to listOf(mapOf("recordName" to "asset", "serverErrorCode" to "THROTTLED"))),
            emptyMap<String, Any>(),
        )) {
            withServer({ _, _ -> response }) { _, client ->
                assertThrows(IllegalArgumentException::class.java) { lookupRecords(client, "PrimarySync", listOf("asset")) }
            }
        }
    }

    @Test fun establishesBaselineThenReadsOnlyDeltasIncludingEmptyPages() {
        val requests = mutableListOf<String>(); var delta = 0
        withServer({ path, body ->
            requests += path
            when (path) {
                "query" -> when {
                    body.path("resultsLimit").asInt() == 2 -> mapOf("syncToken" to "before-scan", "records" to emptyList<Any>())
                    body.at("/query/recordType").asText() == "CheckIndexingState" -> mapOf("records" to listOf(record("Index", "index", mapOf("state" to "FINISHED"))))
                    body.at("/query/filterBy/0/fieldValue/value").asInt() == 0 -> mapOf("records" to listOf(asset("old"), master("old")))
                    else -> mapOf("records" to emptyList<Any>())
                }
                "zone" -> when (delta++) {
                    0 -> { assertEquals("before-scan", body.at("/zones/0/syncToken").asText()); page("empty-page", true, emptyList()) }
                    1 -> page("caught-up", false, listOf(asset("new"), master("new")))
                    else -> { assertEquals("caught-up", body.at("/zones/0/syncToken").asText()); page("caught-up", false, emptyList()) }
                }
                else -> error("Unexpected request")
            }
        }) { photos, client ->
            val rows = mutableListOf<Asset>(); val tokens = mutableListOf<String>()
            photos.fetchIncremental(listOf(album(1, "__all__")), client, null, { rows += it }, { tokens += it })
            assertEquals(listOf("old", "new"), rows.map { ICloudResourceKey.decode(it.remoteKey).assetId })
            assertEquals(listOf("empty-page", "caught-up"), tokens)
            requests.clear()
            photos.fetchIncremental(listOf(album(1, "__all__")), client, tokens.last(), {}, {})
            assertEquals(listOf("zone"), requests)
        }
    }

    @Test fun routesFlagsAndAlbumAdditionsWithoutEnumeratingAlbums() {
        val records = listOf(asset("normal", favorite = 1), asset("hidden", hidden = 1), master("normal"), master("hidden"),
            record("CPLContainerRelation", "existing-IN-user-album", mapOf("containerId" to "user-album", "itemId" to "existing")))
        val lookup = listOf(asset("existing"), master("existing"), records.last()).associateBy { it.path("recordName").asText() }
        withServer({ path, body ->
            when (path) {
                "zone" -> page("next", false, records)
                "lookup" -> mapOf("records" to body.path("records").map {
                    val name = it.path("recordName").asText()
                    lookup[name] ?: mapper.valueToTree<JsonNode>(mapOf("recordName" to name, "serverErrorCode" to "NOT_FOUND"))
                })
                else -> error("A delta must not enumerate albums")
            }
        }) { photos, client ->
            val rows = mutableListOf<Asset>()
            photos.fetchIncremental(listOf(album(1, "__all__"), album(2, "__hidden__"), album(3, "__favorites__"), album(4, "user-album")),
                client, "previous", { rows += it }, {})
            assertEquals(setOf(1L, 3L), rows.filter { ICloudResourceKey.decode(it.remoteKey).assetId == "normal" }.map { it.album.id }.toSet())
            assertEquals(listOf(2L), rows.filter { ICloudResourceKey.decode(it.remoteKey).assetId == "hidden" }.map { it.album.id })
            assertEquals(setOf(1L, 4L), rows.filter { ICloudResourceKey.decode(it.remoteKey).assetId == "existing" }.map { it.album.id }.toSet())
        }
    }

    @Test fun invalidCursorRebuildsBaselineAndReplaysChanges() {
        for (expired in listOf(
            mapOf("zones" to listOf(mapOf("serverErrorCode" to "BAD_REQUEST", "reason" to "Unknown sync continuation type"))),
            mapOf("serverErrorCode" to "CHANGE_TOKEN_EXPIRED"),
        )) {
            val inputTokens = mutableListOf<String>()
            withServer({ path, body ->
                when (path) {
                    "zone" -> {
                        val token = body.at("/zones/0/syncToken").asText(); inputTokens += token
                        if (token == "expired") expired
                        else page("recovered", false, listOf(asset("new"), master("new")))
                    }
                    "query" -> when {
                        body.path("resultsLimit").asInt() == 2 -> mapOf("syncToken" to "fresh-baseline", "records" to emptyList<Any>())
                        body.at("/query/recordType").asText() == "CheckIndexingState" -> mapOf("records" to listOf(record("Index", "index", mapOf("state" to "FINISHED"))))
                        else -> mapOf("records" to emptyList<Any>())
                    }
                    else -> error("Unexpected request")
                }
            }) { photos, client ->
                val tokens = mutableListOf<String>(); val rows = mutableListOf<Asset>()
                photos.fetchIncremental(listOf(album(1, "__all__")), client, "expired", { rows += it }, { tokens += it })
                assertEquals(listOf("expired", "fresh-baseline"), inputTokens)
                assertEquals(listOf("recovered"), tokens)
                assertEquals("new", ICloudResourceKey.decode(rows.single().remoteKey).assetId)
            }
        }
    }

    @Test fun missingOriginalDoesNotAdvanceButDeletionDoes() {
        withServer({ path, body ->
            if (path == "zone") page("next", false, listOf(asset("new")))
            else mapOf("records" to body.path("records").map {
                val name = it.path("recordName").asText()
                if (name == "new") asset("new") else mapper.valueToTree<JsonNode>(mapOf("recordName" to name, "serverErrorCode" to "NOT_FOUND"))
            })
        }) { photos, client ->
            val tokens = mutableListOf<String>()
            assertThrows(IllegalStateException::class.java) {
                photos.fetchIncremental(listOf(album(1, "__all__")), client, "previous", {}, { tokens += it })
            }
            assertTrue(tokens.isEmpty())
        }
        val deleted = asset("deleted").deepCopy<JsonNode>().also { (it as com.fasterxml.jackson.databind.node.ObjectNode).put("deleted", true) }
        withServer({ _, _ -> page("next", false, listOf(deleted)) }) { photos, client ->
            val rows = mutableListOf<Asset>(); val tokens = mutableListOf<String>()
            photos.fetchIncremental(listOf(album(1, "__all__")), client, "previous", { rows += it }, { tokens += it })
            assertTrue(rows.isEmpty()); assertEquals(listOf("next"), tokens)
        }
    }

    @Test fun routesScreenshotsAndBurstsByCloudKitMetadataAndSkipsUnavailableAssets() {
        fun classified(name: String, fields: Map<String, Any>) = record("CPLAsset", name, mapOf(
            "masterRef" to mapOf("recordName" to "master-$name"), "assetDate" to 1000) + fields)
        val records = listOf(
            classified("screenshot", mapOf("assetSubtypeV2" to 3)),
            classified("burst", mapOf("burstId" to "burst-sequence")),
            classified("both", mapOf("assetSubtypeV2" to 3, "burstId" to "burst-sequence")),
            classified("ordinary", emptyMap()),
            classified("live-photo", mapOf("assetSubtypeV2" to 2)),
            classified("legacy-subtype", mapOf("assetSubtype" to 3)),
            classified("empty-burst", mapOf("burstId" to "")),
            classified("null-burst", mapOf("burstId" to mapper.nullNode())),
            classified("blank-burst", mapOf("burstId" to " ")),
            classified("hidden", mapOf("assetSubtypeV2" to 3, "burstId" to "burst-sequence", "isHidden" to 1)),
            classified("trashed", mapOf("assetSubtypeV2" to 3, "burstId" to "burst-sequence", "isDeleted" to 1)),
            classified("expunged", mapOf("assetSubtypeV2" to 3, "burstId" to "burst-sequence", "isExpunged" to 1)),
            classified("purged", mapOf("assetSubtypeV2" to 3)).also {
                (it as com.fasterxml.jackson.databind.node.ObjectNode).put("deleted", true)
            },
        )
        withServer({ path, _ ->
            assertEquals("zone", path)
            page("next", false, records + records.map { master(it.path("recordName").asText()) })
        }) { photos, client ->
            val rows = mutableListOf<Asset>(); val tokens = mutableListOf<String>()
            photos.fetchIncremental(listOf(album(1, "__screenshots__"), album(2, "__bursts__")), client,
                "previous", { rows += it }, { tokens += it })
            assertEquals(setOf(1L to "screenshot", 1L to "both", 2L to "burst", 2L to "both"),
                rows.map { it.album.id to ICloudResourceKey.decode(it.remoteKey).assetId }.toSet())
            assertEquals(4, rows.size)
            assertEquals(listOf("next"), tokens)
        }
    }

    @Test fun failedPersistenceDoesNotAdvanceCursor() {
        withServer({ _, _ -> page("next", false, listOf(asset("new"), master("new"))) }) { photos, client ->
            val tokens = mutableListOf<String>()
            assertThrows(IllegalStateException::class.java) {
                photos.fetchIncremental(listOf(album(1, "__all__")), client, "previous", { error("DB write failed") }, { tokens += it })
            }
            assertTrue(tokens.isEmpty())
        }
    }

    @Test fun rejectsStalledPaginationAndTransientErrorsWithoutFallback() {
        for (response in listOf(page("previous", true, emptyList()), mapOf("zones" to listOf(mapOf("serverErrorCode" to "THROTTLED"))))) {
            val requests = mutableListOf<String>()
            withServer({ path, _ -> requests += path; response }) { photos, client ->
                val tokens = mutableListOf<String>()
                assertThrows(RuntimeException::class.java) {
                    photos.fetchIncremental(listOf(album(1, "__all__")), client, "previous", {}, { tokens += it })
                }
                assertTrue(tokens.isEmpty())
                assertEquals(listOf("zone"), requests)
            }
        }
    }
}
