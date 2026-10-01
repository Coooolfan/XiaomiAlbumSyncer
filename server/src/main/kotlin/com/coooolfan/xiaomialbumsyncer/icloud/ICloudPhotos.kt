package com.coooolfan.xiaomialbumsyncer.icloud

import com.coooolfan.xiaomialbumsyncer.model.*
import com.fasterxml.jackson.databind.JsonNode
import org.noear.solon.annotation.Managed
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.io.OutputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64

@Managed
class ICloudPhotos(private val accounts: ICloudAccountService) {
    fun fetchAlbums(accountId: Long): List<Album> = accounts.withClient(accountId) { fetchAlbums(accountId, it) }

    internal fun fetchAlbums(accountId: Long, client: ICloudClient): List<Album> {
        val zones = client.cloud("zones/list", emptyMap<String, String>()).path("zones")
            .filterNot { it.path("deleted").asBoolean() }.map { it.at("/zoneID/zoneName").asText() }
            .filter { it == "PrimarySync" || it.startsWith("SharedSync-") }
        require(zones.isNotEmpty()) { "iCloud 照片图库不可用，请检查网页数据访问和高级数据保护设置" }
        return zones.flatMap { zone ->
            checkIndex(client, zone)
            val prefix = if (zone == "PrimarySync") "" else "共享图库 / "
            val result = mutableListOf(album(accountId, "${prefix}所有照片", ICloudAlbumRef(zone, "__all__", "CPLAssetAndMasterByAssetDateWithoutHiddenOrDeleted")))
            result += album(accountId, "${prefix}隐藏", ICloudAlbumRef(zone, "__hidden__", "CPLAssetAndMasterHiddenByAssetDate"))
            result += album(accountId, "${prefix}个人收藏", ICloudAlbumRef(zone, "__favorites__", "CPLAssetAndMasterInSmartAlbumByAssetDate", "FAVORITE"))
            var marker: JsonNode? = null
            val seenMarkers = mutableSetOf<String>()
            do {
                val body = mutableMapOf<String, Any>("query" to mapOf("recordType" to "CPLAlbumByPositionLive"), "zoneID" to zoneId(zone))
                marker?.let { body["continuationMarker"] = it }
                val page = client.cloud("records/query", body)
                for (record in page.path("records")) {
                    val name = record.path("recordName").asText()
                    if (name in setOf("----Root-Folder----", "----Project-Root-Folder----") || field(record, "isDeleted").asBoolean()) continue
                    val title = decodeName(field(record, "albumNameEnc").asText()).ifBlank { name }
                    result += album(accountId, prefix + title, ICloudAlbumRef(zone, name, "CPLContainerRelationLiveByAssetDate"))
                }
                marker = page.get("continuationMarker")?.takeUnless { it.isNull }
                if (marker != null) require(seenMarkers.add(marker.toString())) { "iCloud 相册分页没有推进" }
            } while (marker != null)
            result
        }
    }

    fun fetchAssets(album: Album, handler: (List<Asset>) -> Unit): Long = accounts.withClient(album.accountId) { fetchAssets(album, it, handler) }

    internal fun fetchAssets(album: Album, client: ICloudClient, handler: (List<Asset>) -> Unit): Long {
        val ref = requireNotNull(album.cloudAlbum) { "iCloud 相册信息缺失" }
        checkIndex(client, ref.zone)
        var rank = 0L
        var total = 0L
        val seen = mutableSetOf<String>()
        while (true) {
            val filters = mutableListOf(filter("startRank", "INT64", rank), filter("direction", "STRING", "ASCENDING"))
            if (ref.smartAlbum != null) filters += filter("smartAlbum", "STRING", ref.smartAlbum)
            else if (!ref.recordName.startsWith("__")) filters += filter("parentId", "STRING", ref.recordName)
            val page = client.cloud("records/query", mapOf("query" to mapOf("recordType" to ref.queryType, "filterBy" to filters),
                "resultsLimit" to 200, "zoneID" to zoneId(ref.zone)))
            val records = page.path("records").toList()
            val assets = records.filter { it.path("recordType").asText() == "CPLAsset" }
            if (assets.isEmpty()) break
            val masters = records.filter { it.path("recordType").asText() == "CPLMaster" }.associateBy { it.path("recordName").asText() }
            val missing = assets.map { field(it, "masterRef").path("recordName").asText() }.filterNot(masters::containsKey).distinct()
            val allMasters = masters + if (missing.isEmpty()) emptyMap() else lookup(client, ref.zone, missing).associateBy { it.path("recordName").asText() }
            val rows = assets.flatMap { asset ->
                val recordName = asset.path("recordName").asText()
                require(seen.add(recordName)) { "iCloud 资产分页重复，请重试同步" }
                val masterName = field(asset, "masterRef").path("recordName").asText()
                val master = allMasters[masterName] ?: error("iCloud 资产缺少原件记录")
                parseResources(album, asset, master)
            }
            handler(rows)
            total += rows.size
            rank += assets.size
        }
        return total
    }

    fun fetchIncremental(albums: List<Album>, cursor: String?, handler: (List<Asset>) -> Unit, commitCursor: (String) -> Unit) {
        require(albums.isNotEmpty())
        accounts.withClient(albums.first().accountId) { client ->
            fetchIncremental(albums, client, cursor, handler, commitCursor)
        }
    }

    fun download(accountId: Long, asset: Asset, path: Path): Boolean {
        val ref = requireNotNull(asset.cloudAsset)
        repeat(2) { attempt ->
            val (client, url) = accounts.withClient(accountId) { client ->
                val records = lookup(client, ref.zone, listOf(ref.recordName, ref.masterName))
                val resource = records.firstOrNull { it.path("recordName").asText() == ref.recordName }
                    ?.let { field(it, ref.resource + "Res") }?.takeUnless { it.isMissingNode || it.isNull }
                    ?: records.firstOrNull { it.path("recordName").asText() == ref.masterName }?.let { field(it, ref.resource + "Res") }
                    ?: error("iCloud 文件资源已不可用")
                val url = resource.path("downloadURL").asText()
                require(url.isNotBlank()) { "iCloud 文件没有下载地址" }
                client to url
            }
            // CDN 下载不占用账号认证锁，多个下载器可以并行传输。
            try {
                client.download(url, path)
                require(Files.size(path) == asset.size) { "iCloud 文件大小不一致，请重试下载" }
                return true
            } catch (e: ICloudApiException) {
                if (attempt != 0 || e.status !in setOf(401, 403, 410)) throw e
            }
        }
        error("iCloud 下载失败")
    }

    private fun checkIndex(client: ICloudClient, zone: String) {
        val state = client.cloud("records/query", mapOf("query" to mapOf("recordType" to "CheckIndexingState"), "zoneID" to zoneId(zone)))
            .at("/records/0/fields/state/value").asText()
        require(state == "FINISHED") { "iCloud 照片仍在建立索引，请稍后重试" }
    }

    private fun lookup(client: ICloudClient, zone: String, names: List<String>): List<JsonNode> =
        client.cloud("records/lookup", mapOf("zoneID" to zoneId(zone), "records" to names.map { mapOf("recordName" to it) })).path("records").toList()

    private fun album(accountId: Long, title: String, ref: ICloudAlbumRef): Album = Album {
        remoteId = ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest("${ref.zone}/${ref.recordName}".toByteArray())).long and Long.MAX_VALUE
        name = title
        cloudAlbum = ref
        assetCount = 0
        lastUpdateTime = Instant.EPOCH
        shadow = false
        this.accountId = accountId
    }

}

    internal fun parseResources(album: Album, asset: JsonNode, master: JsonNode): List<Asset> {
        val ref = requireNotNull(album.cloudAlbum)
        val recordName = asset.path("recordName").asText()
        val masterName = master.path("recordName").asText()
        val originalName = decodeName(field(master, "filenameEnc").asText()).ifBlank { "$recordName.bin" }
        return listOf("resOriginal", "resOriginalAlt", "resOriginalVidCompl").mapNotNull { variant ->
            val resource = field(master, variant + "Res")
            if (resource.isMissingNode || resource.isNull) return@mapNotNull null
            val checksum = resource.path("fileChecksum").asText()
            val uti = field(master, variant + "FileType").asText().ifBlank { field(master, "itemType").asText() }
            val (mime, extension) = mediaType(uti)
            val safeName = originalName.substringAfterLast('/').substringAfterLast('\\').replace(Regex("[\\r\\n]"), "_")
            val stem = safeName.substringBeforeLast('.', safeName)
            val ext = if (variant == "resOriginal") safeName.substringAfterLast('.', extension) else extension
            // 内容和资源标识参与文件名，避免同名照片被 skipExistingFile 错误跳过。
            val identity = "$recordName/$variant/$checksum"
            val suffix = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).take(6).joinToString("") { "%02x".format(it) }
            Asset {
                xiaomiId = null
                remoteKey = "icloud:${album.accountId}:${album.id}:$identity"
                cloudAsset = ICloudAssetRef(ref.zone, recordName, masterName, variant, checksum)
                fileName = "${stem}_$suffix.$ext"
                type = if (mime.startsWith("video/")) AssetType.VIDEO else AssetType.IMAGE
                recordingType = null
                dateTaken = Instant.ofEpochMilli(field(asset, "assetDate").asLong())
                albumId = album.id
                sha1 = "icloud:$checksum"
                mimeType = mime
                title = stem
                size = resource.path("size").asLong()
            }
        }
    }
    private fun decodeName(encoded: String) = runCatching { String(Base64.getDecoder().decode(encoded), Charsets.UTF_8) }.getOrDefault("")
    private fun field(record: JsonNode, key: String): JsonNode = record.path("fields").path(key).path("value")
    private fun zoneId(zone: String) = mapOf("zoneName" to zone)
    private fun filter(name: String, type: String, value: Any) = mapOf("fieldName" to name, "comparator" to "EQUALS", "fieldValue" to mapOf("type" to type, "value" to value))
    private fun mediaType(uti: String): Pair<String, String> = when (uti) {
        "public.heic", "public.heif" -> "image/heic" to "heic"
        "public.jpeg" -> "image/jpeg" to "jpg"
        "public.png" -> "image/png" to "png"
        "com.compuserve.gif" -> "image/gif" to "gif"
        "public.tiff" -> "image/tiff" to "tiff"
        "com.apple.quicktime-movie" -> "video/quicktime" to "mov"
        "public.mpeg-4" -> "video/mp4" to "mp4"
        "com.adobe.raw-image" -> "image/x-adobe-dng" to "dng"
        "com.canon.cr2-raw-image" -> "image/x-canon-cr2" to "cr2"
        "com.canon.cr3-raw-image" -> "image/x-canon-cr3" to "cr3"
        "com.sony.arw-raw-image" -> "image/x-sony-arw" to "arw"
        "com.nikon.raw-image", "com.nikon.nef-raw-image" -> "image/x-nikon-nef" to "nef"
        "com.fuji.raw-image" -> "image/x-fuji-raf" to "raf"
        else -> "application/octet-stream" to "bin"
    }

internal fun verifyICloudChecksum(path: Path, checksum: String) {
    require(checksum.isNotBlank()) { "iCloud 文件缺少校验值" }
    val encoded = Base64.getDecoder().decode(checksum)
    // CloudKit 的文件校验值包含一个算法标记字节（SHA-1 为 0x01）。
    val expected = when {
        encoded.size == 21 && encoded[0] == 1.toByte() -> encoded.copyOfRange(1, 21)
        encoded.size == 20 -> encoded
        else -> error("不支持的 iCloud 文件校验格式")
    }
    val digest = MessageDigest.getInstance("SHA-1")
    DigestInputStream(Files.newInputStream(path), digest).use {
        it.transferTo(OutputStream.nullOutputStream())
    }
    require(MessageDigest.isEqual(expected, digest.digest())) { "iCloud 文件校验失败，请重试下载" }
}
