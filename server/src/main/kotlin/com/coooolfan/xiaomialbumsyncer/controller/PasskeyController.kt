package com.coooolfan.xiaomialbumsyncer.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import cn.dev33.satoken.stp.StpUtil
import com.coooolfan.xiaomialbumsyncer.service.*
import org.babyfish.jimmer.client.meta.Api
import org.noear.solon.annotation.Body
import org.noear.solon.annotation.Controller
import org.noear.solon.annotation.Mapping
import org.noear.solon.annotation.Path
import org.noear.solon.core.handle.MethodType

/**
 * Passkey 管理控制器
 *
 * 提供 Passkey 注册、认证与管理相关的API接口
 * 注册与管理接口需要用户登录认证；认证接口为公开接口
 *
 * @property service Passkey 服务，用于处理 Passkey 相关业务逻辑
 */
@Api
@Controller
@Mapping("/api/passkey")
class PasskeyController(private val service: PasskeyService) {

    /**
     * 开始 Passkey 注册接口
     *
     * 此接口用于发起 Passkey 注册，验证密码后生成注册挑战与选项
     * 需要用户登录认证才能访问
     *
     * @param request Passkey 注册请求参数，包含密码与凭据名称
     * @return PasskeyRegisterStartResponse 返回注册选项与会话信息
     *
     * @api POST /api/passkey/register/start
     * @permission 需要登录认证
     * @description 调用PasskeyService.startRegistration()生成注册参数
     */
    @Api
    @Mapping("/register/start", method = [MethodType.POST])
    @SaCheckLogin
    fun startRegistration(@Body request: PasskeyRegisterStartRequest): PasskeyRegisterStartResponse {
        return service.startRegistration(request.password)
    }

    /**
     * 完成 Passkey 注册接口
     *
     * 此接口用于提交注册结果并保存 Passkey 凭据
     * 需要用户登录认证才能访问
     *
     * @param request Passkey 注册完成请求参数
     * @return PasskeyCredentialInfo 返回已保存的 Passkey 信息
     *
     * @api POST /api/passkey/register/finish
     * @permission 需要登录认证
     * @description 调用PasskeyService.finishRegistration()校验并持久化凭据
     */
    @Api
    @Mapping("/register/finish", method = [MethodType.POST])
    @SaCheckLogin
    fun finishRegistration(@Body request: PasskeyRegisterFinishRequest): PasskeyCredentialInfo {
        return service.finishRegistration(request)
    }

    /**
     * 开始 Passkey 认证接口
     *
     * 此接口用于发起 Passkey 认证，返回浏览器所需的 WebAuthn 选项
     * 无需登录认证即可访问（公开接口）
     *
     * @return PasskeyAuthStartResponse 返回认证选项与会话信息
     *
     * @api POST /api/passkey/authenticate/start
     * @permission 公开接口，无需认证
     * @description 调用PasskeyService.startAuthentication()生成认证参数
     */
    @Api
    @Mapping("/authenticate/start", method = [MethodType.POST])
    fun startAuthentication(): PasskeyAuthStartResponse {
        return service.startAuthentication()
    }

    /**
     * 完成 Passkey 认证接口
     *
     * 此接口用于验证 Passkey 认证结果并登录用户
     * 无需登录认证即可访问（公开接口）
     *
     * @param request Passkey 认证完成请求参数
     *
     * @api POST /api/passkey/authenticate/finish
     * @permission 公开接口，无需认证
     * @description 调用PasskeyService.finishAuthentication()校验断言并登录
     */
    @Api
    @Mapping("/authenticate/finish", method = [MethodType.POST])
    fun finishAuthentication(@Body request: PasskeyAuthFinishRequest) {
        service.finishAuthentication(request)
        StpUtil.login(0)  // 单用户系统，用户 ID 始终为 0
    }

    /**
     * 获取已注册的 Passkey 列表接口
     *
     * 此接口用于查询当前系统已注册的 Passkey 列表
     * 需要用户登录认证才能访问
     *
     * @return List<PasskeyCredentialInfo> 返回已注册的 Passkey 列表
     *
     * @api GET /api/passkey
     * @permission 需要登录认证
     * @description 调用PasskeyService.listCredentials()获取 Passkey 列表
     */
    @Api
    @Mapping("", method = [MethodType.GET])
    @SaCheckLogin
    fun listCredentials(): List<PasskeyCredentialInfo> {
        return service.listCredentials()
    }

    /**
     * 删除 Passkey 接口
     *
     * 此接口用于删除指定 Passkey
     * 需要用户登录认证才能访问
     *
     * @param credentialId Passkey 凭据ID
     *
     * @api DELETE /api/passkey/{credentialId}
     * @permission 需要登录认证
     * @description 调用PasskeyService.deleteCredential()删除指定 Passkey
     */
    @Api
    @Mapping("/{credentialId}", method = [MethodType.DELETE])
    @SaCheckLogin
    fun deleteCredential(@Path credentialId: String) {
        service.deleteCredential(credentialId)
    }

    /**
     * 更新 Passkey 名称接口
     *
     * 此接口用于更新指定 Passkey 的名称
     * 需要用户登录认证才能访问
     *
     * @param credentialId Passkey 凭据ID
     * @param request Passkey 名称更新请求参数
     *
     * @api POST /api/passkey/{credentialId}/name
     * @permission 需要登录认证
     * @description 调用PasskeyService.updateCredentialName()更新 Passkey 名称
     */
    @Api
    @Mapping("/{credentialId}/name", method = [MethodType.POST])
    @SaCheckLogin
    fun updateCredentialName(@Path credentialId: String, @Body request: PasskeyUpdateNameRequest) {
        service.updateCredentialName(credentialId, request.name)
    }

    /**
     * 检查是否已注册 Passkey 接口
     *
     * 此接口用于判断当前系统是否已注册 Passkey
     * 无需登录认证即可访问（公开接口）
     *
     * @return HasPasskeysResponse 返回是否存在 Passkey 的标记
     *
     * @api GET /api/passkey/available
     * @permission 公开接口，无需认证
     * @description 调用PasskeyService.hasPasskeys()检查 Passkey 是否存在
     */
    @Api
    @Mapping("/available", method = [MethodType.GET])
    fun hasPasskeys(): HasPasskeysResponse {
        return HasPasskeysResponse(service.hasPasskeys())
    }
}

/**
 * Passkey 注册请求数据类
 *
 * @property password 当前登录密码
 * @property credentialName Passkey 名称
 */
data class PasskeyRegisterStartRequest(
    val password: String,
    val credentialName: String
)
