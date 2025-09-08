package com.coooolfan.xiaomialbumsyncer.xiaomicloud

import com.coooolfan.xiaomialbumsyncer.model.Album
import com.coooolfan.xiaomialbumsyncer.utils.client
import com.coooolfan.xiaomialbumsyncer.utils.throwIfNotSuccess
import com.coooolfan.xiaomialbumsyncer.utils.ua
import com.coooolfan.xiaomialbumsyncer.utils.withCookie
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.Request
import org.noear.solon.annotation.Managed
import org.slf4j.LoggerFactory

@Managed
class XiaoMiApi(private val tokenManager: TokenManager) {

    private val log = LoggerFactory.getLogger(XiaoMiApi::class.java)

    fun fetchAlbumList(): List<Album> {
        val req = Request.Builder()
            .url("https://i.mi.com/gallery/user/album/list?ts=${System.currentTimeMillis()}&pageNum=0&pageSize=10&isShared=false&numOfThumbnails=1")
            .ua()
            .header(
                "cookie",
                withCookie(
                    "userId" to tokenManager.getUserId().toString(),
                    "serviceToken" to tokenManager.getServiceToken()
                )
            )
            .get()
            .build()
        val res = client().newCall(req).execute()
        throwIfNotSuccess(res.code)
        val resBodyString = res.body.string()
        val albumArrayJson = jacksonObjectMapper().readTree(resBodyString).at("/data/albums")
        val albums = mutableListOf<Album>()
        for (albumJson in albumArrayJson) {
            val albumId = albumJson.get("albumId").asLong()
            var albumName: String? = null
            if (albumId == 1000L) continue
            else if (albumId == 1L) albumName = "相机"
            else if (albumId == 2L) albumName = "屏幕截图"

            albums.add(Album {
                cloudId = albumId.toString()
                name = albumName ?: albumJson.get("name").asText()
                assetCount = albumJson.get("mediaCount").asInt()
            })
        }

        return albums
    }
}

