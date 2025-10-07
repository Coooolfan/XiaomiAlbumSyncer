package com.coooolfan.xiaomialbumsyncer.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import com.coooolfan.xiaomialbumsyncer.model.Asset
import com.coooolfan.xiaomialbumsyncer.model.by
import com.coooolfan.xiaomialbumsyncer.service.AssetService
import org.babyfish.jimmer.client.FetchBy
import org.babyfish.jimmer.client.meta.Api
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.noear.solon.annotation.Controller
import org.noear.solon.annotation.Managed
import org.noear.solon.annotation.Mapping
import org.noear.solon.annotation.Path
import org.noear.solon.core.handle.MethodType

@Api
@Managed
@Mapping("/api/asset")
@Controller
@SaCheckLogin
class AssetController(private val service: AssetService) {
    /**
     * 刷新指定相册的媒体资源列表
     * 
     * 此接口用于从远程服务获取指定相册的最新媒体资源列表并更新到本地数据库
     * 需要用户登录认证才能访问（类级别注解）
     * 
     * @param albumId 相册ID，用于指定要刷新哪个相册的媒体资源
     * @return List<Asset> 返回刷新后的媒体资源列表，包含所有媒体资源的基本信息
     * 
     * @api GET /api/asset/{albumId}/lastest
     * @permission 需要登录认证
     * @description 调用AssetService.refreshAssets()方法获取指定相册的最新媒体数据
     */
    @Api
    @Mapping("/{albumId}/lastest", method = [MethodType.GET])
    fun refreshAssets(@Path albumId: Long): List<@FetchBy("DEFAULT_ASSET") Asset> {
        return service.refreshAssets(albumId, DEFAULT_ASSET)
    }

    /**
     * 获取指定相册的媒体资源列表
     * 
     * 此接口用于获取数据库中存储的指定相册的所有媒体资源信息
     * 需要用户登录认证才能访问（类级别注解）
     * 
     * @param albumId 相册ID，用于指定要获取哪个相册的媒体资源
     * @return List<Asset> 返回指定相册的所有媒体资源列表
     * 
     * @api GET /api/asset/{albumId}
     * @permission 需要登录认证
     * @description 调用AssetService.getAssets()方法获取指定相册的媒体数据
     */
    @Api
    @Mapping("/{albumId}", method = [MethodType.GET])
    fun listAssets(@Path albumId: Long): List<@FetchBy("DEFAULT_ASSET") Asset> {
        return service.getAssets(albumId, DEFAULT_ASSET)
    }


    companion object {
        private val DEFAULT_ASSET = newFetcher(Asset::class).by {
            allScalarFields()
        }
    }
}