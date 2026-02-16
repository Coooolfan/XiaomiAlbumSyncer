package com.coooolfan.xiaomialbumsyncer.xiaomicloud

import com.coooolfan.xiaomialbumsyncer.model.Album
import com.coooolfan.xiaomialbumsyncer.model.AlbumTimeline
import com.coooolfan.xiaomialbumsyncer.model.Asset
import com.coooolfan.xiaomialbumsyncer.model.AssetType
import com.coooolfan.xiaomialbumsyncer.utils.*
import com.fasterxml.jackson.databind.JsonNode
import okhttp3.FormBody
import okhttp3.Request
import org.noear.solon.Solon
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

    fun fetchAllAlbums(accountId: Long): List<Album> {
        val allAlbums = mutableListOf<Album>()
        var pageNum = 0
        var hasMorePages = true

        while (hasMorePages) {
            val req = Request.Builder()
                .url("https://i.mi.com/gallery/user/album/list?ts=${System.currentTimeMillis()}&pageNum=$pageNum&pageSize=10&isShared=false&numOfThumbnails=1")
                .ua()
                .authHeader(tokenManager.getAuthPair(accountId))
                .get()
                .build()

            val responseTree = client().executeWithRetry(req).use { res ->
                throwIfNotSuccess(res.code)
                Solon.context().objectMapper.readTree(res.body.string())
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
                    name = albumName ?: albumJson.get("name").asText()
                    assetCount = albumJson.get("mediaCount").asLong()
                    lastUpdateTime = Instant.ofEpochMilli(albumJson.get("lastUpdateTime")?.asLong() ?: 0L)
                    this.accountId = accountId
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
                    "https://i.mi.com/sfs/ns/recorder/dir/0/list?ts=${System.currentTimeMillis()}&limit=$pageSize&offset=${pageNum * pageSize}"
                else
                    "https://i.mi.com/gallery/user/galleries?ts=${System.currentTimeMillis()}&pageNum=$pageNum&pageSize=$pageSize&albumId=${album.remoteId}"


            val req = Request.Builder()
                .url(url + urlDayParams)
                .ua()
                .authHeader(tokenManager.getAuthPair(album.accountId))
                .get()
                .build()

            val responseTree = client().executeWithRetry(req).use { res ->
                throwIfNotSuccess(res.code)
                Solon.context().objectMapper.readTree(res.body.string())
            }
            val assetArrayJson =
                if (album.isAudioAlbum())
                    responseTree.at("/data/list")
                else
                    responseTree.at("/data/galleries")

            log.info("解析用户 ${album.accountId} 的相册 ${album.name} ID=${album.remoteId}${if (day != null) " day=$day" else ""} 第 ${pageNum + 1} 页数据，此页共 ${assetArrayJson.size()} 个资源")

            // 处理当前页数据
            val pageAssets = assetArrayJson.map { parseJsonNode(it, album) }

            // 立即处理这一页，避免累积
            handler(pageAssets)
            totalCount += pageAssets.size


            // 检查是否还有更多页面
            hasMorePages = !responseTree.at("/data/isLastPage").asBoolean()

            // 兜底，免得小米骗我。录音也用这个结束循环
            if (assetArrayJson.isEmpty) break

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
            .url("https://i.mi.com/gallery/user/timeline?ts=${System.currentTimeMillis()}&albumId=$albumId")
            .ua()
            .authHeader(tokenManager.getAuthPair(accountId))
            .get()
            .build()

        val responseTree = client().executeWithRetry(req).use { res ->
            throwIfNotSuccess(res.code)
            Solon.context().objectMapper.readTree(res.body.string())
        }
        val indexHash = responseTree.at("/data/indexHash").asText()
        val dayCountMap = responseTree.at("/data/dayCount").properties().asSequence().map {
            LocalDate.parse(it.key, BASIC_ISO_DATE) to it.value.asLong()
        }.toMap()
        log.info("从远程解析相册 ID=$albumId 时间线")
        return AlbumTimeline(indexHash, dayCountMap)
    }

    fun downloadAsset(accountId: Long, asset: Asset, targetPath: Path): Path {
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
            .authHeader(tokenManager.getAuthPair(accountId))
            .get()
            .build()
        val fetchOssUrlJson = client().executeWithRetry(fetchOssUrlReq).use { resp ->
            throwIfNotSuccess(resp.code)
            Solon.context().objectMapper.readTree(resp.body.string())
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
            Solon.context().objectMapper.readTree(resp.body.string().substringAfter('(').substringBefore(')'))
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

    /**
     * 删除云端照片
     * @param accountId 账号 ID
     * @param assetId 资产 ID
     * @return 是否成功删除
     */
    fun deleteAsset(accountId: Long, assetId: Long): Boolean {
        val req = Request.Builder()
            .url("https://i.mi.com/gallery/info/delete")
            .ua()
            .authHeader(tokenManager.getAuthPair(accountId))
            .post(
                FormBody.Builder()
                    .add("id", assetId.toString())
                    .add("serviceToken", tokenManager.getAuthPair(accountId).second)
                    .build()
            )
            .build()

        return try {
            val responseTree = client().executeWithRetry(req).use { res ->
                throwIfNotSuccess(res.code)
                Solon.context().objectMapper.readTree(res.body.string())
            }

            val result = responseTree.get("result")?.asText()
            val code = responseTree.get("code")?.asInt()

            if (result == "ok" && code == 0) {
                log.info("成功删除云端照片 ID=$assetId")
                true
            } else {
                log.warn("删除云端照片失败 ID=$assetId, result=$result, code=$code")
                false
            }
        } catch (e: Exception) {
            log.error("删除云端照片异常 ID=$assetId", e)
            false
        }
    }

    /**
     * 批量删除云端照片
     * @param accountId 账号 ID
     * @param assetIds 资产 ID 列表
     * @return 成功删除的资产 ID 列表
     */
    fun batchDeleteAssets(accountId: Long, assetIds: List<Long>): List<Long> {
        val successIds = mutableListOf<Long>()

        assetIds.forEach { assetId ->
            if (deleteAsset(accountId, assetId)) {
                successIds.add(assetId)
            }
            Thread.sleep(100)
        }

        log.info("批量删除完成，成功删除 ${successIds.size}/${assetIds.size} 个照片")

        return successIds
    }

    /**
     * 获取云端空间使用情况
     * @param accountId 账号 ID
     * @return 云端空间信息
     */
    fun getCloudSpace(accountId: Long): CloudSpaceInfo {
        val req = Request.Builder()
            .url("https://i.mi.com/status/lite/alldetail?ts=${System.currentTimeMillis()}")
            .ua()
            .authHeader(tokenManager.getAuthPair(accountId))
            .get()
            .build()

        val responseTree = client().executeWithRetry(req).use { res ->
            throwIfNotSuccess(res.code)
            Solon.context().objectMapper.readTree(res.body.string())
        }

        val data = responseTree.get("data")
        val totalQuota = data.get("totalQuota")?.asLong() ?: 0L
        val used = data.get("used")?.asLong() ?: 0L
        val usedDetailNode = data.get("usedDetail")
        val usedDetailMap = mutableMapOf<String, SpaceUsageItem>()
        
        if (usedDetailNode != null && usedDetailNode.isObject) {
            usedDetailNode.fields().forEach { (key, value) ->
                val size = value.get("size")?.asLong() ?: 0L
                var text = value.get("text")?.asText() ?: key
                
                text = when (text) {
                    "桌面图标布局" -> "云备份"
                    "Creation" -> "小米创作"
                    "相册图片" -> "相册"
                    "录音备份" -> "录音"
                    else -> text
                }
                
                usedDetailMap[key] = SpaceUsageItem(size, text)
            }
        }

        val galleryUsed = usedDetailMap["GalleryImage"]?.size ?: 0L
        val usagePercent = if (totalQuota > 0) {
            ((used.toDouble() / totalQuota) * 100).toInt()
        } else {
            0
        }
        log.info("获取云端空间信息成功，账号 ID=$accountId，总空间=$totalQuota，已用=$used，相册=$galleryUsed，使用率=$usagePercent%")
        return CloudSpaceInfo(
            totalQuota = totalQuota,
            used = used,
            galleryUsed = galleryUsed,
            usagePercent = usagePercent,
            usedDetail = usedDetailMap
        )
    }

    private fun parseJsonNode(jsonNode: JsonNode, album: Album): Asset {
        return when (album.isAudioAlbum()) {
            false -> {
                val fullFileName = jsonNode.get("fileName").asText()

                Asset {
                    id = jsonNode.get("id").asLong()
                    fileName = fullFileName
                    type = AssetType.valueOf(jsonNode.get("type").asText().uppercase())
                    dateTaken = Instant.ofEpochMilli(jsonNode.get("dateTaken").asLong())
                    this.album = Album { id = album.id }
                    sha1 = jsonNode.get("sha1").asText()
                    mimeType = jsonNode.get("mimeType").asText()
                    title = jsonNode.get("title")?.asText() ?: fullFileName.substringBeforeLast('.')
                    size = jsonNode.get("size")?.asLong() ?: 0L
                }
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
                    this.album = Album { id = album.id }
                    this.sha1 = jsonNode.get("sha1").asText()
                    this.mimeType = Files.probeContentType(Path(fileName)) ?: "application/octet-stream"
                    this.title = fileName.substringBeforeLast(".")
                    this.size = jsonNode.get("size").asLong()
                }
            }
        }
    }
}

