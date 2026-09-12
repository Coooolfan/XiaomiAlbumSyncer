package com.coooolfan.xiaomialbumsyncer.model

import org.babyfish.jimmer.sql.*

@Entity
@Table(name = "mcp_token")
interface McpToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long

    val name: String

    @Key
    val tokenHash: String

    val permission: McpTokenPermission

    val createdAt: Long
}

@EnumType(EnumType.Strategy.NAME)
enum class McpTokenPermission {
    READ_ONLY,
    ALLOW_TRIGGER,
}
