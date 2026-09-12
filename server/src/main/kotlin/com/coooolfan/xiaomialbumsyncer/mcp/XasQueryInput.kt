package com.coooolfan.xiaomialbumsyncer.mcp

/**
 * xas_query 工具的统一信封入参
 *
 * domain + action 决定查询内容；pageIndex/pageSize 仅分页查询生效；
 * id 的语义随 domain 变化（详见 XasQueryTool.INPUT_SCHEMA）。
 */
data class XasQueryInput(
    val domain: String? = null,
    val action: String? = null,
    val pageIndex: Int? = null,
    val pageSize: Int? = null,
    val id: String? = null,
)
