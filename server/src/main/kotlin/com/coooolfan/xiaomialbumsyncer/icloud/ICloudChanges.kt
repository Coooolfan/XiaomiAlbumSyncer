package com.coooolfan.xiaomialbumsyncer.icloud

import com.coooolfan.xiaomialbumsyncer.model.Album
import com.coooolfan.xiaomialbumsyncer.model.Asset
import com.fasterxml.jackson.databind.JsonNode
import org.slf4j.LoggerFactory

/** CloudKit 图库变更流；协议参考 kei 的 syncToken 调研（见 THIRD_PARTY_NOTICES）。 */
internal fun ICloudPhotos.fetchIncremental(
    albums: List<Album>, client: ICloudClient, cursor: String?,
    handler: (List<Asset>) -> Unit, commitCursor: (String) -> Unit,
) {
    val zone = requireNotNull(albums.first().cloudAlbum).zone
    require(albums.all { it.accountId == albums.first().accountId && it.cloudAlbum?.zone == zone })
    fun establishBaseline(): String {
        // 在全量枚举之前取水位，再回放枚举期间的变更，防止漏掉同时上传的资产。
        val token = client.cloud("records/query", mapOf(
            "zoneID" to mapOf("zoneName" to zone), "resultsLimit" to 2,
            "query" to mapOf("recordType" to "CPLAssetAndMasterByAssetDateWithoutHiddenOrDeleted",
                "filterBy" to listOf(
                    mapOf("fieldName" to "startRank", "comparator" to "EQUALS", "fieldValue" to mapOf("type" to "INT64", "value" to 0)),
                    mapOf("fieldName" to "direction", "comparator" to "EQUALS", "fieldValue" to mapOf("type" to "STRING", "value" to "ASCENDING"))))))
            .path("syncToken").asText()
        require(token.isNotBlank()) { "iCloud 未返回同步位点，无法建立增量基线" }
        albums.forEach { fetchAssets(it, client, handler) }
        return token
    }
    try {
        readChanges(albums, client, cursor?.takeIf { it.isNotBlank() } ?: establishBaseline(), handler, commitCursor)
    } catch (e: InvalidICloudCursor) {
        if (cursor.isNullOrBlank()) throw e
        LoggerFactory.getLogger(ICloudPhotos::class.java).warn("iCloud 图库 {} 位点失效，重新建立全量基线", zone)
        readChanges(albums, client, establishBaseline(), handler, commitCursor)
    }
}

private class InvalidICloudCursor : RuntimeException("iCloud 同步位点失效")
private val expiredCursorCodes = setOf("CHANGE_TOKEN_EXPIRED", "SYNC_TOKEN_EXPIRED", "INVALID_SYNC_TOKEN", "ZONE_NOT_FOUND")

private fun ICloudPhotos.readChanges(
    albums: List<Album>, client: ICloudClient, initial: String,
    handler: (List<Asset>) -> Unit, commitCursor: (String) -> Unit,
) {
    val zone = requireNotNull(albums.first().cloudAlbum).zone
    var token = initial
    val seen = mutableSetOf(initial)
    while (true) {
        val response = try {
            client.cloud("changes/zone", mapOf("zones" to listOf(mapOf(
                "zoneID" to mapOf("zoneName" to zone), "syncToken" to token, "resultsLimit" to 200))))
        } catch (e: ICloudApiException) {
            if (e.serverCode in expiredCursorCodes) throw InvalidICloudCursor()
            throw e
        }
        val page = response.path("zones").singleOrNull() ?: error("iCloud 图库变更响应无效")
        val error = page.path("serverErrorCode").asText()
        if (error.isNotBlank()) {
            val reason = page.path("reason").asText()
            if (error in expiredCursorCodes ||
                error == "BAD_REQUEST" && listOf("token", "continuation").any { reason.contains(it, ignoreCase = true) })
                throw InvalidICloudCursor()
            throw ICloudApiException(503, error)
        }
        val next = page.path("syncToken").asText()
        require(next.isNotBlank()) { "iCloud 变更响应缺少同步位点" }
        val more = page.path("moreComing")
        require(more.isBoolean && page.path("records").isArray) { "iCloud 图库变更响应不完整" }
        if (more.asBoolean()) require(seen.add(next)) { "iCloud 变更分页位点没有推进" }
        val records = page.path("records").toList()
        val assets = records.filter { it.path("recordType").asText() == "CPLAsset" }.associateBy { it.path("recordName").asText() }.toMutableMap()
        val userAlbums = albums.filter { !requireNotNull(it.cloudAlbum).recordName.startsWith("__") }
        val selectedContainers = userAlbums.map { requireNotNull(it.cloudAlbum).recordName }.toSet()
        val addedMembers = records.filter { it.path("recordType").asText() == "CPLContainerRelation" && live(it) }
            .filter { value(it, "containerId").asText() in selectedContainers }
            .map { value(it, "itemId").asText().also { id -> require(id.isNotBlank()) { "iCloud 相册成员缺少资产标识" } } }
        lookupChanges(client, zone, addedMembers.filterNot(assets::containsKey)).forEach { assets[it.path("recordName").asText()] = it }
        val liveAssets = assets.values.filter(::live)
        val masters = records.filter { it.path("recordType").asText() == "CPLMaster" }.associateBy { it.path("recordName").asText() }.toMutableMap()
        val masterNames = liveAssets.map { value(it, "masterRef").path("recordName").asText().also { name ->
            require(name.isNotBlank()) { "iCloud 变化资产缺少原件标识" }
        } }
        lookupChanges(client, zone, masterNames.filterNot(masters::containsKey)).forEach { masters[it.path("recordName").asText()] = it }
        // 成员关系的 recordName 可直接 lookup；查询的是当前成员状态，跨页关系也不会漏掉。
        val relationNames = liveAssets.flatMap { asset -> userAlbums.map { "${asset.path("recordName").asText()}-IN-${it.cloudAlbum!!.recordName}" } }
        val memberships = lookupChanges(client, zone, relationNames).filter(::live).map { it.path("recordName").asText() }.toSet()
        val rows = liveAssets.flatMap { changed ->
            val name = changed.path("recordName").asText()
            var asset = changed
            var master = masters[value(asset, "masterRef").path("recordName").asText()]
            if (master == null) {
                // 变更流中的资产可能已被永久删除，确认当前状态后再决定是否推进。
                asset = lookupChanges(client, zone, listOf(name)).singleOrNull()?.takeIf(::live)
                    ?: return@flatMap emptyList()
                master = lookupChanges(client, zone, listOf(value(asset, "masterRef").path("recordName").asText())).singleOrNull()
                    ?: error("iCloud 变化资产缺少原件记录，请稍后重试")
            }
            if (!live(master)) return@flatMap emptyList()
            albums.filter { album ->
                val ref = requireNotNull(album.cloudAlbum)
                when (ref.recordName) {
                    "__all__" -> !value(asset, "isHidden").asBoolean()
                    "__hidden__" -> value(asset, "isHidden").asBoolean()
                    "__favorites__" -> value(asset, "isFavorite").asBoolean() && !value(asset, "isHidden").asBoolean()
                    else -> "$name-IN-${ref.recordName}" in memberships
                }
            }.flatMap { parseResources(it, asset, master) }
        }
        handler(rows)
        // 只有该页所有资产已持久化后才能提交；空页仍依据 moreComing 继续。
        commitCursor(next)
        token = next
        if (!more.asBoolean()) break
    }
}

private fun value(record: JsonNode, key: String): JsonNode = record.path("fields").path(key).path("value")
private fun live(record: JsonNode): Boolean = !record.path("deleted").asBoolean() &&
    !value(record, "isDeleted").asBoolean() && !value(record, "isExpunged").asBoolean()

private fun lookupChanges(client: ICloudClient, zone: String, names: List<String>): List<JsonNode> =
    names.distinct().chunked(200).flatMap { batch ->
        val records = client.cloud("records/lookup", mapOf("zoneID" to mapOf("zoneName" to zone),
            "records" to batch.map { mapOf("recordName" to it) })).path("records")
        require(records.isArray) { "iCloud 记录查询响应无效" }
        require(batch.toSet() == records.map { it.path("recordName").asText() }.toSet()) { "iCloud 记录查询响应不完整" }
        records.filter { record ->
            val error = record.path("serverErrorCode").asText()
            require(error.isBlank() || error == "NOT_FOUND" || error == "UNKNOWN_ITEM") { "iCloud 记录查询失败：$error" }
            error.isBlank() && !record.path("deleted").asBoolean()
        }
    }
