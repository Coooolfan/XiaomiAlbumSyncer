package com.coooolfan.xiaomialbumsyncer.xiaomicloud

import com.coooolfan.xiaomialbumsyncer.model.Album
import com.coooolfan.xiaomialbumsyncer.utils.authHeader
import com.coooolfan.xiaomialbumsyncer.utils.client
import com.coooolfan.xiaomialbumsyncer.utils.throwIfNotSuccess
import com.coooolfan.xiaomialbumsyncer.utils.ua
import com.coooolfan.xiaomialbumsyncer.utils.withCookie
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.Request
import org.noear.solon.annotation.Managed
import org.slf4j.LoggerFactory
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
}

