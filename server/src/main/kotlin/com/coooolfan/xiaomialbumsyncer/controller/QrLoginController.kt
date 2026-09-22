package com.coooolfan.xiaomialbumsyncer.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import com.coooolfan.xiaomialbumsyncer.xiaomicloud.QrLoginManager
import com.coooolfan.xiaomialbumsyncer.xiaomicloud.QrLoginSessionView
import com.coooolfan.xiaomialbumsyncer.xiaomicloud.QrLoginStatusView
import org.babyfish.jimmer.client.meta.Api
import org.noear.solon.annotation.Controller
import org.noear.solon.annotation.Managed
import org.noear.solon.annotation.Mapping
import org.noear.solon.annotation.Path
import org.noear.solon.core.handle.MethodType

/**
 * 小米账号扫码登录控制器
 *
 * 提供扫码登录会话的创建与状态查询接口。
 * 扫码确认成功后，服务端自动创建或更新对应小米账号的 passToken 凭据。
 * 所有接口均需要用户登录认证。
 *
 * @property manager 扫码登录管理器，负责与小米通行证服务交互
 */
@Api
@Managed
@Mapping("/api/account/qr-login")
@Controller
class QrLoginController(private val manager: QrLoginManager) {

    /**
     * 创建扫码登录会话
     *
     * 向小米通行证发起扫码登录初始化，返回二维码图片地址供前端展示。
     * 会话创建后服务端在后台持有长轮询，直至用户扫码确认或二维码过期。
     *
     * @return QrLoginSessionView 会话 ID 与二维码图片 URL
     *
     * @api POST /api/account/qr-login
     * @permission 需要登录认证
     * @description 调用 QrLoginManager.createSession() 创建扫码登录会话
     */
    @Api
    @Mapping(method = [MethodType.POST])
    @SaCheckLogin
    fun create(): QrLoginSessionView {
        return manager.createSession()
    }

    /**
     * 查询扫码登录会话状态
     *
     * 前端轮询此接口获取扫码进度。
     * status 为 SUCCESS 时表示扫码成功且账号凭据已写入（已存在的 userId 会更新其 passToken）。
     *
     * @param sessionId 创建会话时返回的会话 ID
     * @return QrLoginStatusView 会话状态：WAITING / SUCCESS / EXPIRED / FAILED
     *
     * @api GET /api/account/qr-login/{sessionId}
     * @permission 需要登录认证
     * @description 调用 QrLoginManager.getStatus() 查询会话状态
     */
    @Api
    @Mapping("/{sessionId}", method = [MethodType.GET])
    @SaCheckLogin
    fun status(@Path sessionId: String): QrLoginStatusView {
        return manager.getStatus(sessionId)
    }
}
