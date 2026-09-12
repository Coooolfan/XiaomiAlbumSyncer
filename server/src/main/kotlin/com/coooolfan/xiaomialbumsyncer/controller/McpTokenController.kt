package com.coooolfan.xiaomialbumsyncer.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import com.coooolfan.xiaomialbumsyncer.service.McpTokenCreateRequest
import com.coooolfan.xiaomialbumsyncer.service.McpTokenCreatedResponse
import com.coooolfan.xiaomialbumsyncer.service.McpTokenInfo
import com.coooolfan.xiaomialbumsyncer.service.McpTokenService
import org.babyfish.jimmer.client.meta.Api
import org.noear.solon.annotation.Body
import org.noear.solon.annotation.Controller
import org.noear.solon.annotation.Mapping
import org.noear.solon.annotation.Path
import org.noear.solon.core.handle.Context
import org.noear.solon.core.handle.MethodType

/**
 * MCP Token 管理接口。Token 由服务端生成，原文只在创建响应中返回一次。
 */
@Api
@Controller
@Mapping("/api/mcp-token")
class McpTokenController(private val service: McpTokenService) {

    @Api
    @Mapping(method = [MethodType.GET])
    @SaCheckLogin
    fun list(): List<McpTokenInfo> = service.list()

    @Api
    @Mapping(method = [MethodType.POST])
    @SaCheckLogin
    fun create(@Body request: McpTokenCreateRequest): McpTokenCreatedResponse {
        Context.current()?.cacheControl("no-store")
        return service.create(request)
    }

    @Api
    @Mapping("/{id}", method = [MethodType.DELETE])
    @SaCheckLogin
    fun revoke(@Path id: Long) {
        service.revoke(id)
    }
}
