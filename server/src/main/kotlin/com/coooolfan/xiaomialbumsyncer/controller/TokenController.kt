package com.coooolfan.xiaomialbumsyncer.controller

import cn.dev33.satoken.stp.StpUtil
import com.coooolfan.xiaomialbumsyncer.service.SystemConfigService
import org.babyfish.jimmer.client.meta.Api
import org.noear.solon.annotation.Controller
import org.noear.solon.annotation.Mapping
import org.noear.solon.annotation.Param
import org.noear.solon.core.handle.MethodType

/**
 * 令牌管理控制器
 *
 * 提供用户登录认证相关的API接口，包括用户登录、登出等功能
 * 登录接口为公开接口，无需认证；登出接口需要用户登录认证（通过方法级别注解控制）
 *
 * @property service 系统配置服务，用于处理用户认证相关的业务逻辑
 */
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

/**
 * 登录请求数据类
 *
 * @property password 用户密码
 */
data class LoginRequest(
    val password: String
)