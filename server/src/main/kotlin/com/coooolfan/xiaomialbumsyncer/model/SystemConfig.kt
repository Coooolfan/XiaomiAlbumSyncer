package com.coooolfan.xiaomialbumsyncer.model

import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.GenerationType
import org.babyfish.jimmer.sql.Id

@Entity
interface SystemConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long

    val password: String

    val exifToolPath: String

    val assetsDateMapTimeZone: String

    val notifyConfig: NotifyConfig

    // MCP 只读端点的独立访问令牌；为空表示 MCP 端点拒绝所有请求
    val mcpToken: String?
}