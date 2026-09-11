package com.coooolfan.xiaomialbumsyncer.mcp

import com.coooolfan.xiaomialbumsyncer.model.*
import com.coooolfan.xiaomialbumsyncer.service.SystemConfigService
import com.fasterxml.jackson.databind.ObjectMapper
import io.modelcontextprotocol.server.transport.ServerTransportSecurityException
import io.modelcontextprotocol.server.transport.ServerTransportSecurityValidator
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.noear.solon.ai.chat.tool.FunctionTool
import org.noear.solon.ai.chat.tool.ToolProvider
import org.noear.solon.ai.mcp.McpChannel
import org.noear.solon.ai.mcp.server.annotation.McpServerEndpoint
import java.security.MessageDigest

/**
 * XAS 的 MCP 服务端点（streamable HTTP）。
 *
 * 未实现 @ToolMapping 注解方法，工具通过 ToolProvider 手工注册，
 * 以便完全掌控 inputSchema 与入参解析。
 *
 * 鉴权：独立静态 token（system_config.mcp_token），
 * 请求需携带 `Authorization: Bearer <token>`；未配置 token 时拒绝所有请求。
 */
@McpServerEndpoint(channel = McpChannel.STREAMABLE, mcpEndpoint = "/mcp")
class XasMcpServer(
    private val sql: KSqlClient,
    private val queryService: XasQueryService,
    objectMapper: ObjectMapper,
) : ToolProvider, ServerTransportSecurityValidator {

    private val queryTool = XasQueryTool(queryService, objectMapper)

    override fun getTools(): Collection<FunctionTool> = listOf(queryTool)

    override fun validateHeaders(headers: Map<String, List<String>>) {
        val supplied = headers.entries
            .firstOrNull { it.key.equals(AUTH_HEADER, ignoreCase = true) }
            ?.value?.firstOrNull()
            ?.let { raw ->
                if (raw.startsWith(BEARER_PREFIX, ignoreCase = true)) {
                    raw.substring(BEARER_PREFIX.length).trim()
                } else {
                    raw.trim()
                }
            }

        val expected = sql.executeQuery(SystemConfig::class) {
            where(table.id eq SystemConfigService.CONFIG_ID)
            select(table.mcpToken)
        }.firstOrNull()

        when {
            expected.isNullOrBlank() ->
                throw ServerTransportSecurityException(401, "MCP token is not configured")

            supplied.isNullOrBlank() || !MessageDigest.isEqual(
                supplied.toByteArray(Charsets.UTF_8),
                expected.toByteArray(Charsets.UTF_8),
            ) ->
                throw ServerTransportSecurityException(401, "Invalid MCP token")
        }
    }

    companion object {
        private const val AUTH_HEADER = "Authorization"
        private const val BEARER_PREFIX = "Bearer "
    }
}
