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
    /**
     * 刷新相册列表
     * 
     * 此接口用于从远程服务获取最新的相册列表并更新到本地数据库
     * 需要用户登录认证才能访问
     * 
     * @return List<Album> 返回刷新后的相册列表，包含所有相册的基本信息
     * 
     * @api GET /api/album/lastest
     * @permission 需要登录认证
     * @description 调用AlbumsService.refreshAlbums()方法获取最新相册数据
     */
    @Api
    @Mapping("/lastest", method = [MethodType.GET])
    @SaCheckLogin
    fun refreshAlbums(): List<Album> {
        return service.refreshAlbums()
    }

    /**
     * 获取所有相册列表
     * 
     * 此接口用于获取数据库中存储的所有相册信息
     * 需要用户登录认证才能访问
     * 
     * @return List<Album> 返回所有相册的列表，包含相册的基本信息
     * 
     * @api GET /api/album
     * @permission 需要登录认证
     * @description 调用AlbumsService.getAllAlbums()方法获取所有相册数据
     */
    @Api
    @Mapping(method = [MethodType.GET])
    @SaCheckLogin
    fun listAlbums(): List<Album> {
        return service.getAllAlbums()
    }
}