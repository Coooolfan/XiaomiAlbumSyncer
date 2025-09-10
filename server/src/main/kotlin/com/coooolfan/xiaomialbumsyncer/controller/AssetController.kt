package com.coooolfan.xiaomialbumsyncer.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import com.coooolfan.xiaomialbumsyncer.model.Asset
import com.coooolfan.xiaomialbumsyncer.service.AssetService
import org.babyfish.jimmer.client.meta.Api
import org.noear.solon.annotation.Controller
import org.noear.solon.annotation.Managed
import org.noear.solon.annotation.Mapping
import org.noear.solon.annotation.Path
import org.noear.solon.core.handle.MethodType

@Api
@Managed
@Mapping("/api/asset")
@Controller
class AssetController(private val service: AssetService) {
    @Api
    @Mapping("/{albumId}/lastest", method = [MethodType.GET])
    @SaCheckLogin
    fun refreshAssets(@Path albumId: Long): List<Asset> {
        return service.refreshAssets(albumId)
    }
}