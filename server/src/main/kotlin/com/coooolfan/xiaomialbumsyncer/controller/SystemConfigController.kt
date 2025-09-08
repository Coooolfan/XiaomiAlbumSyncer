package com.coooolfan.xiaomialbumsyncer.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import com.coooolfan.xiaomialbumsyncer.model.Album
import com.coooolfan.xiaomialbumsyncer.model.dto.SystemConfigInit
import com.coooolfan.xiaomialbumsyncer.model.dto.SystemConfigUpdate
import com.coooolfan.xiaomialbumsyncer.service.SystemConfigService
import org.babyfish.jimmer.client.meta.Api
import org.noear.solon.annotation.Body
import org.noear.solon.annotation.Controller
import org.noear.solon.annotation.Mapping
import org.noear.solon.core.handle.MethodType

@Api
@Controller
@Mapping("/api/system-config")
class SystemConfigController(private val service: SystemConfigService) {
    @Api
    @Mapping(method = [MethodType.GET])
    fun isInit(): IsInitResponse {
        return IsInitResponse(service.isInit())
    }

    @Api
    @Mapping(method = [MethodType.POST])
    fun createConfig(@Body create: SystemConfigInit) {
        return service.createConfig(create)
    }

    @Api
    @Mapping("/pass-token", method = [MethodType.POST])
    @SaCheckLogin
    fun updatePassToken(@Body update: SystemConfigUpdate) {
        return service.updateConfig(update.toEntity())
    }
}

data class IsInitResponse(
    val isInit: Boolean,
    val testString: String = "rawString",
    val testAblum: Album = Album {
        id = -1L
        name = "testAlbumName"
        assetCount = 123
        cloudId = "testCloudId"
    }
)