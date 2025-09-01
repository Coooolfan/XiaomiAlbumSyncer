package com.coooolfan.xiaomialbumsyncer.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import cn.dev33.satoken.stp.StpUtil
import com.coooolfan.xiaomialbumsyncer.service.SystemConfigService
import org.noear.solon.annotation.Body
import org.noear.solon.annotation.Controller
import org.noear.solon.annotation.Mapping
import org.noear.solon.core.handle.MethodType

@Controller
@Mapping("/api/token")
class TokenController(private val service: SystemConfigService) {

    @Mapping("", method = [MethodType.GET])
    fun login(@Body login: LoginRequest) {
        return service.login(login)
    }

    @SaCheckLogin
    @Mapping("", method = [MethodType.GET])
    fun logout() {
        StpUtil.logout()
    }
}

data class LoginRequest(
    val password: String
)