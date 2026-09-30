package com.coooolfan.xiaomialbumsyncer.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import com.coooolfan.xiaomialbumsyncer.icloud.*
import org.babyfish.jimmer.client.meta.Api
import org.noear.solon.annotation.*
import org.noear.solon.core.handle.MethodType

@Api
@Managed
@Controller
@Mapping("/api/icloud")
class ICloudController(private val accounts: ICloudAccountService) {
    @Api
    @SaCheckLogin
    @Mapping("/login", method = [MethodType.POST])
    fun login(@Body input: ICloudLoginInput): ICloudAccountStatus = validated { accounts.login(input) }

    @Api
    @SaCheckLogin
    @Mapping("/{id}/verify", method = [MethodType.POST])
    fun verify(@Path id: Long, @Body input: ICloudVerifyInput): ICloudAccountStatus = validated { accounts.verify(id, input.code) }

    @Api
    @SaCheckLogin
    @Mapping("/{id}/status", method = [MethodType.GET])
    fun status(@Path id: Long): ICloudAccountStatus = accounts.status(id)
    private fun <T> validated(action: () -> T): T = try { action() } catch (e: IllegalArgumentException) {
        throw com.coooolfan.xiaomialbumsyncer.exception.BadRequestException(e.message ?: "iCloud 请求参数无效")
    }
}
