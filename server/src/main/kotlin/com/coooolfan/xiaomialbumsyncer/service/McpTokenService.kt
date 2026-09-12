package com.coooolfan.xiaomialbumsyncer.service

import com.coooolfan.xiaomialbumsyncer.exception.BadRequestException
import com.coooolfan.xiaomialbumsyncer.model.*
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.desc
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.noear.solon.annotation.Managed
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.HexFormat

@Managed
class McpTokenService(private val sql: KSqlClient) {

    private val secureRandom = SecureRandom()

    fun create(request: McpTokenCreateRequest): McpTokenCreatedResponse {
        val name = request.name.trim()
        if (name.isEmpty()) {
            throw BadRequestException("MCP Token 名称不能为空")
        }
        if (name.length > MAX_NAME_LENGTH) {
            throw BadRequestException("MCP Token 名称不能超过 $MAX_NAME_LENGTH 个字符")
        }

        val rawToken = generateToken()
        val createdAt = System.currentTimeMillis()
        val saved = sql.saveCommand(McpToken {
            this.name = name
            tokenHash = hashToken(rawToken)
            permission = request.permission
            this.createdAt = createdAt
        }, SaveMode.INSERT_ONLY).execute().modifiedEntity

        return McpTokenCreatedResponse(
            id = saved.id,
            name = name,
            permission = request.permission,
            createdAt = createdAt,
            token = rawToken,
        )
    }

    fun list(): List<McpTokenInfo> = sql.executeQuery(McpToken::class) {
        orderBy(table.createdAt.desc(), table.id.desc())
        select(table.fetchBy {
            name()
            permission()
            createdAt()
        })
    }.map { token ->
        McpTokenInfo(
            id = token.id,
            name = token.name,
            permission = token.permission,
            createdAt = token.createdAt,
        )
    }

    fun revoke(id: Long) {
        val affectedRows = sql.deleteById(McpToken::class, id).affectedRowCount(McpToken::class)
        if (affectedRows == 0) {
            throw BadRequestException("MCP Token 不存在: $id")
        }
    }

    /**
     * 校验标准 Bearer 凭据并返回其固定权限。原始 Token 从不持久化。
     */
    fun resolvePermission(authorization: String?): McpTokenPermission? {
        val rawToken = authorization
            ?.takeIf { it.startsWith(BEARER_PREFIX, ignoreCase = true) }
            ?.substring(BEARER_PREFIX.length)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return null

        return sql.executeQuery(McpToken::class) {
            where(table.tokenHash eq hashToken(rawToken))
            select(table.permission)
        }.firstOrNull()
    }

    private fun generateToken(): String {
        val bytes = ByteArray(TOKEN_RANDOM_BYTES)
        secureRandom.nextBytes(bytes)
        return TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun hashToken(token: String): String = MessageDigest.getInstance("SHA-256")
        .digest(token.toByteArray(Charsets.UTF_8))
        .let(HexFormat.of()::formatHex)

    private companion object {
        const val BEARER_PREFIX = "Bearer "
        const val TOKEN_PREFIX = "xas_mcp_"
        const val TOKEN_RANDOM_BYTES = 32
        const val MAX_NAME_LENGTH = 100
    }
}

data class McpTokenCreateRequest(
    val name: String,
    val permission: McpTokenPermission,
)

data class McpTokenInfo(
    val id: Long,
    val name: String,
    val permission: McpTokenPermission,
    val createdAt: Long,
)

data class McpTokenCreatedResponse(
    val id: Long,
    val name: String,
    val permission: McpTokenPermission,
    val createdAt: Long,
    val token: String,
)
