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
    @Api
    @Mapping("/{albumId}/lastest", method = [MethodType.GET])
    fun refreshAssets(@Path albumId: Long): List<@FetchBy("DEFAULT_ASSET") Asset> {
        return service.refreshAssets(albumId, DEFAULT_ASSET)
    }

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