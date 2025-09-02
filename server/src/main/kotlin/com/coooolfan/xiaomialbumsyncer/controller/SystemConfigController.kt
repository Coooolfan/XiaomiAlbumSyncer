package com.coooolfan.xiaomialbumsyncer.controller

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
    fun createConfig(@Body create: CreateConfigRequest) {
        return service.createConfig(create)
    }
}

data class CreateConfigRequest(
    val password: String
)

data class IsInitResponse(
    val isInit: Boolean
)