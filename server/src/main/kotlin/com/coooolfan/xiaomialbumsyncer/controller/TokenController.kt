package com.coooolfan.xiaomialbumsyncer.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import cn.dev33.satoken.stp.StpUtil
import com.coooolfan.xiaomialbumsyncer.service.SystemConfigService
import org.babyfish.jimmer.client.meta.Api
import org.noear.solon.annotation.Controller
import org.noear.solon.annotation.Mapping
import org.noear.solon.annotation.Param
import org.noear.solon.core.handle.MethodType

@Api
@Controller
@Mapping("/api/token")
class TokenController(private val service: SystemConfigService) {
    @Api
    @Mapping("", method = [MethodType.GET])
    fun login(@Param login: String) {
        return service.login(LoginRequest(login))
    }

    @Api
    @SaCheckLogin
    @Mapping("", method = [MethodType.DELETE])
    fun logout() {
        StpUtil.logout()
    }
}

data class LoginRequest(
    val password: String
)