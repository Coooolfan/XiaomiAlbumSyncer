package com.coooolfan.xiaomialbumsyncer.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import com.coooolfan.xiaomialbumsyncer.exception.BadRequestException
import com.coooolfan.xiaomialbumsyncer.xiaomicloud.e2ee.*
import org.babyfish.jimmer.client.meta.Api
import org.noear.solon.annotation.*
import org.noear.solon.core.handle.MethodType

@Api
@Managed
@Controller
@Mapping("/api/xiaomi/e2ee")
class XiaomiE2eeController(private val service: XiaomiE2eeService) {
    @Api
    @SaCheckLogin
    @Mapping("/{id}/status", method = [MethodType.GET])
    fun status(@Path id: Long): XiaomiE2eeStatus = validated { service.status(id) }

    @Api
    @SaCheckLogin
    @Mapping("/{id}/captcha", method = [MethodType.POST])
    fun push(@Path id: Long) { validated { service.push(id) } }

    @Api
    @SaCheckLogin
    @Mapping("/{id}/verify", method = [MethodType.POST])
    fun verify(@Path id: Long, @Body input: Map<String, String>): XiaomiE2eeStatus = validated { service.verify(id, input["code"].orEmpty()) }

    private fun <T> validated(action: () -> T): T = try { action() }
        catch (e: IllegalArgumentException) { throw BadRequestException(e.message ?: "小米 E2EE 参数无效") }
        catch (e: IllegalStateException) { throw BadRequestException(e.message ?: "小米 E2EE 请求失败") }
}
