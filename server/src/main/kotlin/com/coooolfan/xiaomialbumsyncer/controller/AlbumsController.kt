package com.coooolfan.xiaomialbumsyncer.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import com.coooolfan.xiaomialbumsyncer.model.Album
import com.coooolfan.xiaomialbumsyncer.service.AlbumsService
import org.babyfish.jimmer.client.meta.Api
import org.noear.solon.annotation.Controller
import org.noear.solon.annotation.Managed
import org.noear.solon.annotation.Mapping
import org.noear.solon.core.handle.MethodType

@Api
@Managed
@Mapping("/api/album")
@Controller
class AlbumsController(private val service: AlbumsService) {
    @Api
    @Mapping("/lastest", method = [MethodType.GET])
    @SaCheckLogin
    fun refreshAlbumn(): List<Album> {
        return service.refreshAlbums()
    }

    @Api
    @Mapping(method = [MethodType.GET])
    @SaCheckLogin
    fun listAlbums(): List<Album> {
        return service.getAllAlbums()
    }
}