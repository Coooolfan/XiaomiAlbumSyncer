package com.coooolfan.xiaomialbumsyncer.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import com.coooolfan.xiaomialbumsyncer.model.Album
import com.coooolfan.xiaomialbumsyncer.service.AlbumsService
import org.babyfish.jimmer.client.meta.Api
import org.noear.solon.annotation.Controller
import org.noear.solon.annotation.Managed
import org.noear.solon.annotation.Mapping
import org.noear.solon.annotation.Param
import org.noear.solon.core.handle.MethodType
import java.time.Instant
import java.time.LocalDate

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

    /**
     * 获取指定相册的日期映射
     *
     * 此接口用于获取指定相册中照片的日期分布情况
     * 需要用户登录认证才能访问
     *
     * @param albumIds List<Long> 需要查询的相册ID列表
     * @return Map<LocalDate, Long> 返回一个映射，键为日期，值为该日期下的照片数量
     *
     * @api GET /api/album/date-map
     * @permission 需要登录认证
     * @description 调用AlbumsService.fetchDateMap(albumIds)方法获取日期映射数据
     */
    @Api
    @Mapping("/date-map", method = [MethodType.GET])
    @SaCheckLogin
    fun fetchDateMap(
        @Param(required = false) albumIds: List<Long>?,
        @Param(required = false) start: Instant?,
        @Param(required = false) end: Instant?
    ): Map<LocalDate, Long> {
        return service.fetchDateMap(albumIds ?: emptyList(), start ?: Instant.EPOCH, end ?: Instant.now())
    }
}