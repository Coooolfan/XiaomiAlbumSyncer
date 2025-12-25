package com.coooolfan.xiaomialbumsyncer.xiaomicloud

import com.coooolfan.xiaomialbumsyncer.model.Album
import com.coooolfan.xiaomialbumsyncer.model.AlbumTimeline
import com.coooolfan.xiaomialbumsyncer.model.Asset
import com.coooolfan.xiaomialbumsyncer.model.AssetType
import com.coooolfan.xiaomialbumsyncer.utils.*
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.FormBody
import okhttp3.Request
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

    private val log = LoggerFactory.getLogger(XiaoMiApi::class.java)

    fun fetchAllAlbums(): List<Album> {
        val allAlbums = mutableListOf<Album>()
        var pageNum = 0
        var hasMorePages = true

        while (hasMorePages) {
            val req = Request.Builder()
                .url("https://i.mi.com/gallery/user/album/list?ts=${System.currentTimeMillis()}&pageNum=$pageNum&pageSize=10&isShared=false&numOfThumbnails=1")
                .ua()
                .authHeader(tokenManager.getAuthPair())
                .get()
                .build()

            val responseTree = client().executeWithRetry(req).use { res ->
                throwIfNotSuccess(res.code)
                jacksonObjectMapper().readTree(res.body.string())
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
                    id = albumId
                    name = albumName ?: albumJson.get("name").asText()
                    assetCount = albumJson.get("mediaCount").asLong()
                    lastUpdateTime = Instant.ofEpochMilli(albumJson.get("lastUpdateTime")?.asLong() ?: 0L)
                })
            }

            // 检查是否还有更多页面
            hasMorePages = !responseTree.at("/data/isLastPage").asBoolean()
            pageNum++
        }

        allAlbums.add(Album {
            id = -1
            name = "录音"
            assetCount = 0
            lastUpdateTime = Instant.now()
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
                    "https://i.mi.com/sfs/ns/recorder/dir/0/list?ts=${System.currentTimeMillis()}&limit=$pageSize&offset=${pageNum * pageSize}"
                else
                    "https://i.mi.com/gallery/user/galleries?ts=${System.currentTimeMillis()}&pageNum=$pageNum&pageSize=$pageSize&albumId=${album.id}"


            val req = Request.Builder()
                .url(url + urlDayParams)
                .ua()
                .authHeader(tokenManager.getAuthPair())
                .get()
                .build()

            val responseTree = client().executeWithRetry(req).use { res ->
                throwIfNotSuccess(res.code)
                jacksonObjectMapper().readTree(res.body.string())
            }
            val assetArrayJson =
                if (album.isAudioAlbum())
                    responseTree.at("/data/list")
                else
                    responseTree.at("/data/galleries")

            log.info("解析相册 ${album.name} ID=${album.id}${if (day != null) " day=$day" else ""} 第 ${pageNum + 1} 页数据，此页共 ${assetArrayJson.size()} 个资源")

            // 处理当前页数据
            val pageAssets = assetArrayJson.map { parseJsonNode(it, album) }

            // 立即处理这一页，避免累积
            handler(pageAssets)
            totalCount += pageAssets.size

            // 检查是否还有更多页面
            hasMorePages =
                if (album.isAudioAlbum()) // 录音好像没有这种标记
                    assetArrayJson.size() != 0
                else
                    !responseTree.at("/data/isLastPage").asBoolean()

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

    fun fetchAlbumTimeline(albumId: Long): AlbumTimeline {
        val req = Request.Builder()
            .url("https://i.mi.com/gallery/user/timeline?ts=${System.currentTimeMillis()}&albumId=$albumId")
            .ua()
            .authHeader(tokenManager.getAuthPair())
            .get()
            .build()

        val responseTree = client().executeWithRetry(req).use { res ->
            throwIfNotSuccess(res.code)
            jacksonObjectMapper().readTree(res.body.string())
        }
        val indexHash = responseTree.at("/data/indexHash").asText()
        val dayCountMap = responseTree.at("/data/dayCount").properties().asSequence().map {
            LocalDate.parse(it.key, BASIC_ISO_DATE) to it.value.asLong()
        }.toMap()
        log.info("从远程解析相册 ID=$albumId 时间线")
        return AlbumTimeline(indexHash, dayCountMap)
    }

    fun downloadAsset(asset: Asset, targetPath: Path): Path {
        val url =
            if (asset.type == AssetType.AUDIO)
                "https://i.mi.com/sfs/ns/recorder/file/${asset.id}/cb/dl_sfs_cb_${System.currentTimeMillis()}_0/storage?ts=${System.currentTimeMillis()}"
            else
                "https://i.mi.com/gallery/storage?ts=${System.currentTimeMillis()}&id=${asset.id}"

        // 这里的 resp 还是需要 close 一下，因为后面的 saveToFile 可能会阻塞很久，okhttp3 会报 warning
        // 1. 获取 OSS URL
        val fetchOssUrlReq = Request.Builder()
            .url(url)
            .ua()
            .authHeader(tokenManager.getAuthPair())
            .get()
            .build()
        val fetchOssUrlJson = client().executeWithRetry(fetchOssUrlReq).use { resp ->
            throwIfNotSuccess(resp.code)
            jacksonObjectMapper().readTree(resp.body.string())
        }

        // 文件已经被删掉了，直接返回一个无效值，避免后续反复请求
        if (fetchOssUrlJson.at("/code").asInt() == 50050) {
            log.warn("文件: ${asset.fileName} id: ${asset.id} 已经被删除，跳过下载")
            return Path("/tmp/DELETED")
        }

        val ossUrl = fetchOssUrlJson.at("/data/url").asText()

        // 2. 请求签名直链
        val fetchSignedUrlReq = Request.Builder().url(ossUrl).ua().get().build()
        val fetchSignedUrlJson = client().executeWithRetry(fetchSignedUrlReq).use { resp ->
            throwIfNotSuccess(resp.code)
            jacksonObjectMapper().readTree(resp.body.string().substringAfter('(').substringBefore(')'))
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

        return targetPath
    }

    private fun parseJsonNode(jsonNode: JsonNode, album: Album): Asset {
        return when (album.isAudioAlbum()) {
            false -> Asset {
                id = jsonNode.get("id").asLong()
                fileName = jsonNode.get("fileName").asText()
                type = AssetType.valueOf(jsonNode.get("type").asText().uppercase())
                dateTaken = Instant.ofEpochMilli(jsonNode.get("dateTaken").asLong())
                albumId = album.id
                sha1 = jsonNode.get("sha1").asText()
                mimeType = jsonNode.get("mimeType").asText()
                title = jsonNode.get("title").asText()
                size = jsonNode.get("size").asLong()
            }

            true -> {
                val name = jsonNode.get("name").asText()
                // 从名字尾部开始匹配可以避免文件名中包含“_”的场景
                val fileName =
                    name.substringBeforeLast("_").substringBeforeLast("_").substringBeforeLast("_")
                        .substringBeforeLast("_")
                Asset {
                    this.id = jsonNode.get("id").asLong()
                    this.fileName = fileName
                    this.type = AssetType.AUDIO
                    this.dateTaken = Instant.ofEpochMilli(jsonNode.get("create_time").asLong())
                    this.albumId = album.id
                    this.sha1 = jsonNode.get("sha1").asText()
                    this.mimeType = Files.probeContentType(Path(fileName)) ?: "application/octet-stream"
                    this.title = fileName.substringBeforeLast(".")
                    this.size = jsonNode.get("size").asLong()
                }
            }
        }
    }
}

