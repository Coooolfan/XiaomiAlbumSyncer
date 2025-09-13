package com.coooolfan.xiaomialbumsyncer.xiaomicloud

import com.coooolfan.xiaomialbumsyncer.model.Album
import com.coooolfan.xiaomialbumsyncer.model.Asset
import com.coooolfan.xiaomialbumsyncer.model.AssetType
import com.coooolfan.xiaomialbumsyncer.utils.authHeader
import com.coooolfan.xiaomialbumsyncer.utils.client
import com.coooolfan.xiaomialbumsyncer.utils.saveToFile
import com.coooolfan.xiaomialbumsyncer.utils.throwIfNotSuccess
import com.coooolfan.xiaomialbumsyncer.utils.ua
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.FormBody
import okhttp3.Request
import org.noear.solon.annotation.Managed
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.time.Instant

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
                if (albumId == 1000L) continue
                else if (albumId == 1L) albumName = "相机"
                else if (albumId == 2L) albumName = "屏幕截图"

                allAlbums.add(Album {
                    id = albumId
                    name = albumName ?: albumJson.get("name").asText()
                    assetCount = albumJson.get("mediaCount").asInt()
                    lastUpdateTime = Instant.ofEpochMilli(albumJson.get("lastUpdateTime").asLong())
                })
            }

            // 检查是否还有更多页面
            hasMorePages = !responseTree.at("/data/isLastPage").asBoolean()
            pageNum++
        }

        return allAlbums.toList() // 返回不可变列表
    }

    fun fetchAssetsByAlbumId(album: Album): List<Asset> {
        val allAssets = mutableListOf<Asset>()
        var pageNum = 0
        var hasMorePages = true
        val pageSize = 200

        while (hasMorePages) {
            val req = Request.Builder()
                .url("https://i.mi.com/gallery/user/galleries?ts=${System.currentTimeMillis()}&pageNum=$pageNum&pageSize=$pageSize&albumId=${album.id}")
                .ua()
                .authHeader(tokenManager.getAuthPair())
                .get()
                .build()

            val res = client().newCall(req).execute()
            throwIfNotSuccess(res.code)
            val resBodyString = res.body.string()
            val responseTree = jacksonObjectMapper().readTree(resBodyString)
            val assetArrayJson = responseTree.at("/data/galleries")

            log.info("解析相册 ${album.name} ID=${album.id} 第 ${pageNum + 1} 页数据，此页共 ${assetArrayJson.size()} 个资源")
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

