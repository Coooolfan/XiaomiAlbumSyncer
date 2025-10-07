package com.coooolfan.xiaomialbumsyncer.controller

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
/**
     * 用户登录接口
     * 
     * 此接口用于用户登录认证，验证密码后生成并返回认证令牌
     * 无需登录认证即可访问（公开接口）
     * 
     * @param password 用户密码，用于登录认证
     * 
     * @api GET /api/token
     * @permission 公开接口，无需认证
     * @description 调用SystemConfigService.login()方法进行用户认证
     */
    @Api
    @Mapping("", method = [MethodType.GET])
    fun login(@Param password: String) {
        return service.login(LoginRequest(password))
    }

/**
     * 用户登出接口
     * 
     * 此接口用于用户登出，清除当前会话的认证信息
     * 无需登录认证即可访问（公开接口）
     * 
     * @api DELETE /api/token
     * @permission 公开接口，无需认证
     * @description 调用StpUtil.logout()方法清除用户会话
     */
    @Api
    @Mapping("", method = [MethodType.DELETE])
    fun logout() {
        StpUtil.logout()
    }
}

data class LoginRequest(
    val password: String
)