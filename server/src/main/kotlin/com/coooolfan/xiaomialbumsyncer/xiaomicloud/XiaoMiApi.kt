package com.coooolfan.xiaomialbumsyncer.xiaomicloud

import com.coooolfan.xiaomialbumsyncer.model.Album
import com.coooolfan.xiaomialbumsyncer.model.AlbumTimeline
import com.coooolfan.xiaomialbumsyncer.model.Asset
import com.coooolfan.xiaomialbumsyncer.model.AssetType
import com.coooolfan.xiaomialbumsyncer.model.EMPTY_ALBUM_TIMELINE
import com.coooolfan.xiaomialbumsyncer.utils.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.FormBody
import okhttp3.Request
import org.noear.solon.annotation.Managed
import org.slf4j.LoggerFactory
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

            val res = client().newCall(req).execute()
            throwIfNotSuccess(res.code)
            val resBodyString = res.body.string()
            val responseTree = jacksonObjectMapper().readTree(resBodyString)
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
                    assetCount = albumJson.get("mediaCount").asInt()
                    lastUpdateTime = Instant.ofEpochMilli(albumJson.get("lastUpdateTime")?.asLong() ?: 0L)
                })
            }

            // 检查是否还有更多页面
            hasMorePages = !responseTree.at("/data/isLastPage").asBoolean()
            pageNum++
        }

        return allAlbums.toList() // 返回不可变列表
    }

    fun fetchAllAssetsByAlbumId(album: Album, day: LocalDate? = null): List<Asset> {
        val allAssets = mutableListOf<Asset>()
        var pageNum = 0
        var hasMorePages = true
        val pageSize = 200

        var url =
            "https://i.mi.com/gallery/user/galleries?ts=${System.currentTimeMillis()}&pageNum=$pageNum&pageSize=$pageSize&albumId=${album.id}"
        if (day != null)
            url += "&startDate=${day.format(BASIC_ISO_DATE)}&endDate=${day.format(BASIC_ISO_DATE)}"

        while (hasMorePages) {
            val req = Request.Builder()
                .url(url)
                .ua()
                .authHeader(tokenManager.getAuthPair())
                .get()
                .build()

            val res = client().newCall(req).execute()
            throwIfNotSuccess(res.code)
            val resBodyString = res.body.string()
            val responseTree = jacksonObjectMapper().readTree(resBodyString)
            val assetArrayJson = responseTree.at("/data/galleries")

            log.info("解析相册 ${album.name} ID=${album.id}${if (day != null) " day=$day" else ""} 第 ${pageNum + 1} 页数据，此页共 ${assetArrayJson.size()} 个资源")
            // 处理当前页数据
            for (assetJson in assetArrayJson) {
                allAssets.add(Asset {
                    id = assetJson.get("id").asLong()
                    fileName = assetJson.get("fileName").asText()
                    type = AssetType.valueOf(assetJson.get("type").asText().uppercase())
                    dateTaken = Instant.ofEpochMilli(assetJson.get("dateTaken").asLong())
                    albumId = album.id
                    sha1 = assetJson.get("sha1").asText()
                    mimeType = assetJson.get("mimeType").asText()
                    title = assetJson.get("title").asText()
                    size = assetJson.get("size").asLong()
                })
            }

            // 检查是否还有更多页面
            hasMorePages = !responseTree.at("/data/isLastPage").asBoolean()
            pageNum++
        }

        return allAssets.toList() // 返回不可变列表
    }

    fun fetchAlbumTimeline(albumId: Long): AlbumTimeline {
        val req = Request.Builder()
            .url("https://i.mi.com/gallery/user/timeline?ts=${System.currentTimeMillis()}&albumId=$albumId")
            .ua()
            .authHeader(tokenManager.getAuthPair())
            .get()
            .build()

        val res = client().newCall(req).execute()
        throwIfNotSuccess(res.code)
        val resBodyString = res.body.string()
        val responseTree = jacksonObjectMapper().readTree(resBodyString)
        val indexHash = responseTree.at("/data/indexHash").asText()
        val dayCountMap = responseTree.at("/data/dayCount").properties().asSequence().map {
            LocalDate.parse(it.key.toString(), BASIC_ISO_DATE) to it.value.asLong()
        }.toMap()
        log.info("从远程解析相册 ID=$albumId 时间线")
        return AlbumTimeline(indexHash, dayCountMap)
    }

    fun downloadAsset(asset: Asset, targetPath: Path): Path {
        // 这里的 resp 还是需要 close 一下，因为后面的 saveToFile 可能会阻塞很久，okhttp3 会报 warning
        // 1. 获取 OSS URL
        val fetchOssUrlReq = Request.Builder()
            .url("https://i.mi.com/gallery/storage?ts=${System.currentTimeMillis()}&id=${asset.id}")
            .ua()
            .authHeader(tokenManager.getAuthPair())
            .get()
            .build()
        val fetchOssUrlResp = client().newCall(fetchOssUrlReq).execute()
        throwIfNotSuccess(fetchOssUrlResp.code)
        val fetchOssUrlBodyString = fetchOssUrlResp.body.string()
        fetchOssUrlResp.close()
        val fetchOssUrlJson = jacksonObjectMapper().readTree(fetchOssUrlBodyString)

        // 文件已经被删掉了，直接返回一个无效值，避免后续反复请求
        if (fetchOssUrlJson.at("/code").asInt() == 50050) {
            log.warn("文件: ${asset.fileName} id: ${asset.id} 已经被删除，跳过下载")
            return Path("/tmp/DELETED")
        }

        val ossUrl = fetchOssUrlJson.at("/data/url").asText()

        // 2. 请求签名直链
        val fetchSignedUrlReq = Request.Builder().url(ossUrl).ua().get().build()
        val fetchSignedUrlResp = client().newCall(fetchSignedUrlReq).execute()
        throwIfNotSuccess(fetchSignedUrlResp.code)
        val fetchSignedUrlBodyString = fetchSignedUrlResp.body.string()
        fetchSignedUrlResp.close()
        val fetchSignedUrlJson =
            jacksonObjectMapper().readTree(fetchSignedUrlBodyString.substringAfter('(').substringBefore(')'))

        // 3. 下载文件
        val downloadUrl = fetchSignedUrlJson.get("url").asText()
        val downloadMeta = fetchSignedUrlJson.get("meta").asText()
        val formBody = FormBody.Builder()
            .add("meta", downloadMeta)
            .build()
        val downloadReq = Request.Builder().url(downloadUrl).ua().post(formBody).build()
        val downloadResp = client().newCall(downloadReq).execute()
        throwIfNotSuccess(downloadResp.code)

        // 4. 保存文件
        downloadResp.body.saveToFile(targetPath)

        return targetPath
    }
}

