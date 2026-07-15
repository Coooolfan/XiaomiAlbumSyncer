package com.coooolfan.xiaomialbumsyncer.xiaomicloud

import com.coooolfan.xiaomialbumsyncer.config.XiaomiApiProperties
import com.coooolfan.xiaomialbumsyncer.model.Album
import com.coooolfan.xiaomialbumsyncer.model.AlbumTimeline
import com.coooolfan.xiaomialbumsyncer.model.Asset
import com.coooolfan.xiaomialbumsyncer.model.AssetType
import com.coooolfan.xiaomialbumsyncer.model.RecordingType
import com.coooolfan.xiaomialbumsyncer.utils.*
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.ResponseBody
import org.noear.solon.Solon
import org.noear.solon.annotation.Inject
import org.noear.solon.annotation.Managed
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter.BASIC_ISO_DATE
import kotlin.io.path.Path

@Managed
class XiaoMiApi(private val tokenManager: TokenManager) {

    @Inject
    private lateinit var apiProperties: XiaomiApiProperties

    private val log = LoggerFactory.getLogger(XiaoMiApi::class.java)

    fun fetchAllAlbums(accountId: Long): List<Album> {
        val allAlbums = mutableListOf<Album>()
        var pageNum = 0
        var hasMorePages = true

        while (hasMorePages) {
            val req = Request.Builder()
                .url(apiProperties.url("gallery/user/album/list?ts=${System.currentTimeMillis()}&pageNum=$pageNum&pageSize=10&isShared=false&numOfThumbnails=1"))
                .ua()
                .authHeader(tokenManager.getAuthPair(accountId))
                .get()
                .build()

            val responseTree = client().executeWithRetry(req).use { res ->
                throwIfNotSuccess(res.code)
                Solon.context().objectMapper.readTree(res.body)
            }
            val albumArrayJson = responseTree.at("/data/albums")

            log.info("解析第 ${pageNum + 1} 页相册数据，此页共 ${albumArrayJson.size()} 个相册")
            // 处理当前页数据
            for (albumJson in albumArrayJson) {
                val albumId = albumJson.get("albumId").asLong()

                var albumName: String? = null
                if (albumId == 1000L) continue // 私密相册，跳过
                else if (albumId == 1L) albumName = "相机"
                else if (albumId == 2L) albumName = "屏幕截图"

                allAlbums.add(Album {
                    remoteId = albumId
                    name = albumName ?: albumJson.get("name")?.asText() ?: "Unknown Album"
                    assetCount = albumJson.get("mediaCount").asLong()
                    lastUpdateTime = Instant.ofEpochMilli(albumJson.get("lastUpdateTime")?.asLong() ?: 0L)
                    this.accountId = accountId
                    shadow = false
                })
            }

            // 检查是否还有更多页面
            hasMorePages = !responseTree.at("/data/isLastPage").asBoolean()
            pageNum++
        }

        allAlbums.add(Album {
            remoteId = -1
            name = "录音"
            assetCount = 0
            lastUpdateTime = Instant.now()
            this.accountId = accountId
        })

        return allAlbums.toList() // 返回不可变列表
    }

    /**
     * 流式获取相册资源，每获取一页就调用 handler 处理，避免大相册内存溢出
     * @return 总资源数量
     */
    fun fetchAssetsByAlbumId(album: Album, day: LocalDate? = null, handler: (List<Asset>) -> Unit): Long {
        var totalCount = 0L
        var pageNum = 0
        var hasMorePages = true
        val pageSize = if (album.isAudioAlbum()) 500 else 200

        val urlDayParams =
            if (day == null) "" else "&startDate=${day.format(BASIC_ISO_DATE)}&endDate=${day.format(BASIC_ISO_DATE)}"

        while (hasMorePages) {
            val url =
                if (album.isAudioAlbum())
                    apiProperties.url("sfs/ns/recorder/dir/0/list?ts=${System.currentTimeMillis()}&limit=$pageSize&offset=${pageNum * pageSize}")
                else
                    apiProperties.url("gallery/user/galleries?ts=${System.currentTimeMillis()}&pageNum=$pageNum&pageSize=$pageSize&albumId=${album.remoteId}")


            val req = Request.Builder()
                .url(url + urlDayParams)
                .ua()
                .authHeader(tokenManager.getAuthPair(album.accountId))
                .get()
                .build()

            val page = client().executeWithRetry(req).use { res ->
                throwIfNotSuccess(res.code)
                parseXiaomiAssetPage(Solon.context().objectMapper, res.body, album.id, album.isAudioAlbum())
            }

            log.info("解析用户 ${album.accountId} 的相册 ${album.name} ID=${album.remoteId}${if (day != null) " day=$day" else ""} 第 ${pageNum + 1} 页数据，此页共 ${page.assets.size} 个资源")

            handler(page.assets)
            totalCount += page.assets.size

            hasMorePages = shouldFetchNextAssetPage(album.isAudioAlbum(), page.assets.size, pageSize, page.isLastPage)
            pageNum++
        }

        return totalCount
    }

    /**
     * 获取相册全部资源并返回列表（适用于小相册或需要返回值的场景）
     */
    fun fetchAllAssetsByAlbumId(album: Album, day: LocalDate? = null): List<Asset> {
        val allAssets = mutableListOf<Asset>()
        fetchAssetsByAlbumId(album, day) { pageAssets ->
            allAssets.addAll(pageAssets)
        }
        return allAssets.toList()
    }

    fun fetchAlbumTimeline(accountId: Long, albumId: Long): AlbumTimeline {
        val req = Request.Builder()
            .url(apiProperties.url("gallery/user/timeline?ts=${System.currentTimeMillis()}&albumId=$albumId"))
            .ua()
            .authHeader(tokenManager.getAuthPair(accountId))
            .get()
            .build()

        val responseTree = client().executeWithRetry(req).use { res ->
            throwIfNotSuccess(res.code)
            Solon.context().objectMapper.readTree(res.body)
        }
        val indexHash = responseTree.at("/data/indexHash").asText()
        val dayCountMap = responseTree.at("/data/dayCount").properties().asSequence().map {
            LocalDate.parse(it.key, BASIC_ISO_DATE) to it.value.asLong()
        }.toMap()
        log.info("从远程解析相册 ID=$albumId 时间线")
        return AlbumTimeline(indexHash, dayCountMap)
    }

    fun downloadAsset(accountId: Long, asset: Asset, targetPath: Path): Boolean {
        val url =
            if (asset.type == AssetType.AUDIO)
                apiProperties.url("sfs/ns/recorder/file/${asset.id}/cb/dl_sfs_cb_${System.currentTimeMillis()}_0/storage?ts=${System.currentTimeMillis()}")
            else
                apiProperties.url("gallery/storage?ts=${System.currentTimeMillis()}&id=${asset.id}")

        // 这里的 resp 还是需要 close 一下，因为后面的 saveToFile 可能会阻塞很久，okhttp3 会报 warning
        // 1. 获取 OSS URL
        val fetchOssUrlReq = Request.Builder()
            .url(url)
            .ua()
            .authHeader(tokenManager.getAuthPair(accountId))
            .get()
            .build()
        val fetchOssUrlJson = client().executeWithRetry(fetchOssUrlReq).use { resp ->
            throwIfNotSuccess(resp.code)
            Solon.context().objectMapper.readTree(resp.body)
        }

        // 文件已经被删掉了，返回未下载，避免后续反复请求
        if (fetchOssUrlJson.at("/code").asInt() == 50050) {
            log.warn("文件: ${asset.fileName} id: ${asset.id} 已经被删除，跳过下载")
            return false
        }

        val ossUrl = fetchOssUrlJson.at("/data/url").asText()

        // 2. 请求签名直链
        val fetchSignedUrlReq = Request.Builder().url(ossUrl).ua().get().build()
        val fetchSignedUrlJson = client().executeWithRetry(fetchSignedUrlReq).use { resp ->
            throwIfNotSuccess(resp.code)
            Solon.context().objectMapper.readJsonpTree(resp.body)
        }

        // 3. 下载文件
        val downloadUrl = fetchSignedUrlJson.get("url").asText()
        val downloadMeta = fetchSignedUrlJson.get("meta").asText()
        val formBody = FormBody.Builder()
            .add("meta", downloadMeta)
            .build()
        val downloadReq = Request.Builder().url(downloadUrl).ua().post(formBody).build()
        client().executeWithRetry(downloadReq).use { downloadResp ->
            throwIfNotSuccess(downloadResp.code)
            // 4. 保存文件
            downloadResp.body.saveToFile(targetPath)
        }

        return true
    }

}

internal fun shouldFetchNextAssetPage(
    audioAlbum: Boolean,
    assetCount: Int,
    pageSize: Int,
    isLastPage: Boolean,
): Boolean =
    if (audioAlbum) assetCount == pageSize
    else assetCount > 0 && !isLastPage

internal data class XiaomiAssetPage(
    val assets: List<Asset>,
    val isLastPage: Boolean,
)

internal fun parseXiaomiAssetPage(
    objectMapper: ObjectMapper,
    body: ResponseBody,
    albumId: Long,
    audioAlbum: Boolean,
): XiaomiAssetPage =
    body.byteStream().use { input ->
        objectMapper.factory.createParser(input).use { parser ->
            check(parser.nextToken() == JsonToken.START_OBJECT) { "Xiaomi asset response must be a JSON object" }
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                val fieldName = parser.currentName()
                val token = parser.nextToken()
                if (fieldName == "data" && token == JsonToken.START_OBJECT) {
                    return@use parseXiaomiAssetData(parser, albumId, audioAlbum)
                }
                parser.skipChildren()
            }
            error("Xiaomi asset response data object not found")
        }
    }

private fun parseXiaomiAssetData(parser: JsonParser, albumId: Long, audioAlbum: Boolean): XiaomiAssetPage {
    val assetFieldName = if (audioAlbum) "list" else "galleries"
    val assets = ArrayList<Asset>(if (audioAlbum) 500 else 200)
    var assetsFound = false
    var isLastPage = false

    while (parser.nextToken() != JsonToken.END_OBJECT) {
        val fieldName = parser.currentName()
        val token = parser.nextToken()
        when {
            fieldName == assetFieldName && token == JsonToken.START_ARRAY -> {
                assetsFound = true
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    check(parser.currentToken == JsonToken.START_OBJECT) { "Xiaomi asset must be a JSON object" }
                    assets += parseXiaomiAsset(parser, albumId, audioAlbum)
                }
            }

            fieldName == "isLastPage" -> isLastPage = parser.valueAsBoolean
            else -> parser.skipChildren()
        }
    }

    check(assetsFound) { "Xiaomi asset response field data.$assetFieldName not found" }
    return XiaomiAssetPage(assets, isLastPage)
}

private fun parseXiaomiAsset(parser: JsonParser, albumId: Long, audio: Boolean): Asset {
    var id: Long? = null
    var fileName: String? = null
    var type: String? = null
    var dateTaken: Long? = null
    var sha1: String? = null
    var mimeType: String? = null
    var title: String? = null
    var size: Long? = null

    while (parser.nextToken() != JsonToken.END_OBJECT) {
        val fieldName = parser.currentName()
        parser.nextToken()
        when (fieldName) {
            "id" -> id = parser.valueAsLong
            "fileName", "name" -> fileName = parser.valueAsString
            "type" -> type = parser.valueAsString
            "dateTaken", "create_time" -> dateTaken = parser.valueAsLong
            "sha1" -> sha1 = parser.valueAsString
            "mimeType" -> mimeType = parser.valueAsString
            "title" -> title = parser.valueAsString
            "size" -> size = parser.valueAsLong
            else -> parser.skipChildren()
        }
    }

    val requiredId = requireNotNull(id) { "Xiaomi asset id not found" }
    val requiredFileName = requireNotNull(fileName) { "Xiaomi asset file name not found" }
    val requiredDateTaken = requireNotNull(dateTaken) { "Xiaomi asset date not found" }
    val requiredSha1 = requireNotNull(sha1) { "Xiaomi asset sha1 not found" }

    if (!audio) {
        return Asset {
            this.id = requiredId
            this.fileName = requiredFileName
            this.type = AssetType.valueOf(requireNotNull(type) { "Xiaomi asset type not found" }.uppercase())
            this.dateTaken = Instant.ofEpochMilli(requiredDateTaken)
            this.albumId = albumId
            this.sha1 = requiredSha1
            this.mimeType = requireNotNull(mimeType) { "Xiaomi asset mimeType not found" }
            this.title = title ?: requiredFileName.substringBeforeLast('.')
            this.size = size ?: 0L
        }
    }

    val recordingName = parseXiaomiRecordingName(requiredFileName)
    return Asset {
        this.id = requiredId
        this.fileName = recordingName.fileName
        this.type = AssetType.AUDIO
        this.recordingType = recordingName.recordingType
        this.dateTaken = Instant.ofEpochMilli(requiredDateTaken)
        this.albumId = albumId
        this.sha1 = requiredSha1
        this.mimeType = Files.probeContentType(Path(recordingName.fileName)) ?: "application/octet-stream"
        this.title = recordingName.fileName.substringBeforeLast(".")
        this.size = requireNotNull(size) { "Xiaomi recording size not found" }
    }
}

private val XIAOMI_RECORDING_NAME_REGEX = Regex("""^(.+)\.([^._]+)_(\d+)_(\d+)_(\d+)_(\d+)$""")

internal data class XiaomiRecordingName(
    val fileName: String,
    val recordingType: RecordingType,
)

internal fun parseXiaomiRecordingName(name: String): XiaomiRecordingName {
    val match = XIAOMI_RECORDING_NAME_REGEX.matchEntire(name)
    if (match == null) {
        // 兼容历史/异常格式：仍按旧逻辑从右侧剥离四段尾缀。
        return XiaomiRecordingName(
            fileName = name.substringBeforeLast("_").substringBeforeLast("_").substringBeforeLast("_")
                .substringBeforeLast("_"),
            recordingType = RecordingType.UNKNOWN,
        )
    }

    val (base, ext, _, typeCode) = match.destructured
    return XiaomiRecordingName(
        fileName = "$base.$ext",
        recordingType = RecordingType.fromCode(typeCode.toIntOrNull() ?: -1),
    )
}
