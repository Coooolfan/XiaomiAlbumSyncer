package com.coooolfan.xiaomialbumsyncer.mcp

import com.coooolfan.xiaomialbumsyncer.service.McpTokenService
import com.fasterxml.jackson.databind.ObjectMapper
import io.modelcontextprotocol.server.transport.ServerTransportSecurityException
import io.modelcontextprotocol.server.transport.ServerTransportSecurityValidator
import org.noear.solon.ai.chat.tool.FunctionTool
import org.noear.solon.ai.chat.tool.ToolProvider
import org.noear.solon.ai.mcp.McpChannel
import org.noear.solon.ai.mcp.server.annotation.McpServerEndpoint

/**
 * XAS 的 MCP 服务端点（streamable HTTP）。
 *
 * 未实现 @ToolMapping 注解方法，工具通过 ToolProvider 手工注册，
 * 以便完全掌控 inputSchema 与入参解析。
 *
 * 鉴权：请求需携带通过管理页创建的 `Authorization: Bearer <token>`。
 * Token 权限决定它是否可以触发定时任务。
 */
@McpServerEndpoint(channel = McpChannel.STREAMABLE, mcpEndpoint = "/mcp")
class XasMcpServer(
    private val queryService: XasQueryService,
    private val tokenService: McpTokenService,
    objectMapper: ObjectMapper,
) : ToolProvider, ServerTransportSecurityValidator {

    private val queryTool = XasQueryTool(queryService, tokenService, objectMapper)

    override fun getTools(): Collection<FunctionTool> = listOf(queryTool)

    override fun validateHeaders(headers: Map<String, List<String>>) {
        val authorization = headers.entries
            .firstOrNull { it.key.equals(AUTH_HEADER, ignoreCase = true) }
            ?.value?.firstOrNull()

        if (tokenService.resolvePermission(authorization) == null) {
            throw ServerTransportSecurityException(401, "Invalid MCP token")
        }
    }

    companion object {
        internal const val AUTH_HEADER = "Authorization"
    }
}
