package com.coooolfan.xiaomialbumsyncer.mcp

import com.coooolfan.xiaomialbumsyncer.exception.BadRequestException
import com.coooolfan.xiaomialbumsyncer.model.McpTokenPermission
import com.coooolfan.xiaomialbumsyncer.service.McpTokenService
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import org.noear.solon.ai.chat.tool.FunctionTool
import org.noear.solon.core.handle.Context
import java.lang.reflect.Type

/**
 * XAS 唯一对外暴露的 MCP 查询工具。
 *
 * inputSchema 为手写 JSON（统一信封：domain + action + 分页 + filter），
 * 入参在本类用 Jackson 解析，不走 solon-ai 的注解参数绑定。
 */
class XasQueryTool(
    private val service: XasQueryService,
    private val tokenService: McpTokenService,
    objectMapper: ObjectMapper,
) : FunctionTool {

    // LLM 可能臆造 schema 外的字段，宽松忽略而非解析失败
    private val mapper = objectMapper.copy()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    override fun name(): String = TOOL_NAME

    override fun title(): String = TOOL_TITLE

    override fun description(): String = TOOL_DESCRIPTION

    override fun returnDirect(): Boolean = false

    override fun inputSchema(): String = INPUT_SCHEMA

    override fun returnType(): Type = String::class.java

    /**
     * 业务校验错误直接抛出，由 solon-ai 框架转为 isError=true 的 CallToolResult（MCP 规范推荐）。
     */
    override fun handle(args: Map<String, Any>): String {
        val input = try {
            mapper.convertValue(args, XasQueryInput::class.java)
        } catch (e: Exception) {
            throw BadRequestException("入参解析失败: ${e.message}")
        }

        return dispatch(input)
    }

    private fun dispatch(input: XasQueryInput): String {
        val domain = requireEnum(input.domain, "domain", DOMAIN_METAS.keys)
        val action = requireEnum(input.action, "action", listOf(ACTION_LIST, ACTION_GET, ACTION_TRIGGER))
        val filterId = input.filter?.id?.trim()?.takeIf { it.isNotEmpty() }

        return when (domain) {
            DOMAIN_HELP -> {
                requireAction(domain, action, ACTION_LIST, ACTION_GET)
                help(filterId)
            }

            DOMAIN_ALBUM -> {
                requireOnlyList(domain, action)
                requireNoFilterId(domain, filterId)
                toJson(service.listAlbums())
            }

            DOMAIN_CRONTAB -> when (action) {
                ACTION_LIST -> {
                    requireNoFilterId(domain, filterId)
                    toJson(service.listCrontabs())
                }

                ACTION_GET -> toJson(service.getCrontab(requireNumericId(domain, filterId, "任务")))
                ACTION_TRIGGER -> {
                    requireTriggerPermission()
                    toJson(service.triggerCrontab(requireNumericId(domain, filterId, "任务")))
                }
                else -> unsupportedAction(domain, action)
            }

            DOMAIN_CRONTAB_HISTORY -> {
                requireOnlyList(domain, action)
                val crontabId = filterId?.let { parseNumericId(it, "任务") }
                toJson(service.listCrontabHistories(crontabId, pageIndex(input), pageSize(input)))
            }

            DOMAIN_CRONTAB_HISTORY_DETAIL -> {
                requireOnlyList(domain, action)
                val historyId = requireNumericId(domain, filterId, "历史")
                toJson(service.listCrontabHistoryDetails(historyId, pageIndex(input), pageSize(input)))
            }

            DOMAIN_SYSTEM -> {
                requireOnlyList(domain, action)
                requireNoFilterId(domain, filterId)
                toJson(service.listSystem())
            }

            else -> throw BadRequestException("不支持的 domain: $domain")
        }
    }

    /**
     * help 的 list 与 get 走同一实现；filter.id 为 domain 枚举项时只返回对应 domain 的文档。
     */
    private fun help(filterId: String?): String {
        val domains = if (filterId != null) {
            if (filterId !in DOMAIN_METAS) {
                throw BadRequestException(
                    "domain=help 时 filter.id 必须是 domain 枚举项之一: ${DOMAIN_METAS.keys.joinToString()}"
                )
            }
            mapOf(filterId to DOMAIN_METAS.getValue(filterId))
        } else {
            DOMAIN_METAS
        }

        return toJson(
            HelpOutput(
                hint = HINT_HELP,
                usage = UsageDoc(
                    description = TOOL_DESCRIPTION,
                    input = InputDoc(
                        domain = "必填，枚举: ${DOMAIN_METAS.keys.joinToString()}",
                        action = "必填，枚举: $ACTION_LIST（集合视图）/ $ACTION_GET（单资源详情）/ $ACTION_TRIGGER（触发任务执行，仅 crontab）",
                        pageIndex = "可选，从 0 开始，默认 0；仅分页查询生效",
                        pageSize = "可选，默认 50，上限 $MAX_PAGE_SIZE；仅分页查询生效",
                        filter = "可选对象，字段 id 的语义随 domain 变化，见各 domain 的 filter 说明",
                    ),
                    domains = domains,
                    pagination = PaginationDoc(
                        pageIndex = "从 0 开始，默认 0",
                        pageSize = "默认 50，上限 $MAX_PAGE_SIZE",
                    ),
                    notes = NOTES,
                ),
            )
        )
    }

    private fun requireEnum(value: String?, name: String, allowed: Collection<String>): String {
        val trimmed = value?.trim()?.lowercase()
        if (trimmed.isNullOrEmpty() || trimmed !in allowed) {
            throw BadRequestException("参数 $name 必须是 ${allowed.joinToString()} 之一，收到: $value")
        }
        return trimmed
    }

    private fun requireOnlyList(domain: String, action: String) {
        requireAction(domain, action, ACTION_LIST)
    }

    private fun requireAction(domain: String, action: String, vararg allowed: String) {
        if (action !in allowed) {
            throw BadRequestException("domain=$domain 仅支持 action=${allowed.joinToString("/")}")
        }
    }

    private fun requireNoFilterId(domain: String, filterId: String?) {
        if (filterId != null) {
            throw BadRequestException("domain=$domain 不支持 filter.id")
        }
    }

    private fun requireNumericId(domain: String, filterId: String?, idName: String): Long {
        return filterId?.let { parseNumericId(it, idName) }
            ?: throw BadRequestException("domain=$domain 时 filter.id=$idName id 为必填")
    }

    private fun parseNumericId(value: String, idName: String): Long {
        return value.toLongOrNull()
            ?: throw BadRequestException("filter.id 必须是数字 $idName id，收到: $value")
    }

    private fun unsupportedAction(domain: String, action: String): String {
        throw BadRequestException("domain=$domain 不支持 action=$action")
    }

    private fun requireTriggerPermission() {
        val authorization = Context.current()?.header(XasMcpServer.AUTH_HEADER)
        if (tokenService.resolvePermission(authorization) != McpTokenPermission.ALLOW_TRIGGER) {
            throw BadRequestException("当前 MCP Token 为只读权限，不允许触发定时任务")
        }
    }

    private fun pageIndex(input: XasQueryInput): Int = (input.pageIndex ?: DEFAULT_PAGE_INDEX).coerceAtLeast(0)

    private fun pageSize(input: XasQueryInput): Int =
        (input.pageSize ?: DEFAULT_PAGE_SIZE).coerceIn(1, MAX_PAGE_SIZE)

    private fun toJson(value: Any): String = mapper.writeValueAsString(value)

    companion object {
        const val TOOL_NAME = "xas_query"
        const val TOOL_TITLE = "Xiaomi Album Syncer 查询与任务触发"
        const val DEFAULT_PAGE_INDEX = 0
        const val DEFAULT_PAGE_SIZE = 50
        const val MAX_PAGE_SIZE = 200

        const val DOMAIN_HELP = "help"
        const val DOMAIN_ALBUM = "album"
        const val DOMAIN_CRONTAB = "crontab"
        const val DOMAIN_CRONTAB_HISTORY = "crontab_history"
        const val DOMAIN_CRONTAB_HISTORY_DETAIL = "crontab_history_detail"
        const val DOMAIN_SYSTEM = "system"

        const val ACTION_LIST = "list"
        const val ACTION_GET = "get"
        const val ACTION_TRIGGER = "trigger"

        const val TOOL_DESCRIPTION =
            "Xiaomi Album Syncer（XAS）查询工具。统一信封入参：domain（help=使用说明 / album=相册 / " +
                "crontab=定时任务 / crontab_history=运行历史 / crontab_history_detail=运行明细 / system=账号与系统信息）" +
                "+ action（list=集合视图 / get=单资源详情 / trigger=触发任务立即执行，仅 crontab 支持）" +
                "+ 分页 pageIndex（从 0 开始）/ pageSize（默认 50，上限 200）" +
                "+ filter.id（语义随 domain：crontab 的 get/trigger 传任务 id；crontab_history 传任务 id 可选；" +
                "crontab_history_detail 传历史 id 必填；help 时为 domain 枚举项，可缩小文档范围）。" +
                "所有响应顶层带 hint 提示下一步操作。不确定用法时先调用 {\"domain\":\"help\",\"action\":\"list\"}。" +
                "除 crontab 的 trigger 为写操作外均为只读；trigger 仅允许使用 ALLOW_TRIGGER 权限的 Token。" +
                "不含密码/passToken/通知配置等敏感信息。"

        private val INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "domain": {
                  "type": "string",
                  "enum": ["help", "album", "crontab", "crontab_history", "crontab_history_detail", "system"],
                  "description": "查询域：help=使用说明，album=相册，crontab=定时任务，crontab_history=运行历史，crontab_history_detail=运行明细，system=账号与系统信息"
                },
                "action": {
                  "type": "string",
                  "enum": ["list", "get", "trigger"],
                  "description": "list=集合视图；get=单资源详情（仅 crontab 支持 get）；trigger=触发任务立即执行（仅 crontab 支持，写操作）；help 下 list 与 get 等价"
                },
                "pageIndex": {
                  "type": "integer",
                  "minimum": 0,
                  "default": 0,
                  "description": "页码，从 0 开始；仅分页查询（crontab_history / crontab_history_detail）生效"
                },
                "pageSize": {
                  "type": "integer",
                  "minimum": 1,
                  "maximum": 200,
                  "default": 50,
                  "description": "每页条数，默认 50，上限 200；仅分页查询生效"
                },
                "filter": {
                  "type": "object",
                  "properties": {
                    "id": {
                      "type": "string",
                      "description": "语义随 domain 变化：crontab 的 get/trigger 传任务 id（必填）；crontab_history 传任务 id（可选）；crontab_history_detail 传历史 id（必填）；help 时为 domain 枚举项（可选）；album/system 不支持"
                    }
                  },
                  "description": "通用过滤对象"
                }
              },
              "required": ["domain", "action"]
            }
        """.trimIndent()

        private val NOTES = listOf(
            "除 domain=crontab&action=trigger（仅 ALLOW_TRIGGER 权限 Token 可用）外均为只读",
            "时间戳为 ISO-8601 字符串（currentStats.ts 为毫秒时间戳）",
            "不暴露密码/passToken/通知配置等敏感信息",
        )

        /**
         * help 文档与入参校验共用的 domain 元数据
         */
        private val DOMAIN_METAS: Map<String, DomainDoc> = linkedMapOf(
            DOMAIN_HELP to DomainDoc(
                actions = listOf(ACTION_LIST, ACTION_GET),
                desc = "本工具的使用说明；list 与 get 返回相同内容",
                filter = "filter.id 可选，取值为 domain 枚举项，仅返回对应 domain 的说明",
                example = mapOf("domain" to DOMAIN_HELP, "action" to ACTION_LIST),
            ),
            DOMAIN_ALBUM to DomainDoc(
                actions = listOf(ACTION_LIST),
                desc = "全部相册（永远包含影子相册，shadow=true 表示远程已不存在的本地相册）",
                filter = null,
                example = mapOf("domain" to DOMAIN_ALBUM, "action" to ACTION_LIST),
            ),
            DOMAIN_CRONTAB to DomainDoc(
                actions = listOf(ACTION_LIST, ACTION_GET, ACTION_TRIGGER),
                desc = "list 返回定时任务极简摘要；get 返回任务概况（含 cron 表达式等 config）与当前执行统计；" +
                    "trigger 触发任务立即执行（异步，运行中的任务会跳过本次触发）",
                filter = "action=get/trigger 时 filter.id=任务id（必填，数字）",
                example = mapOf(
                    "domain" to DOMAIN_CRONTAB,
                    "action" to ACTION_TRIGGER,
                    "filter" to mapOf("id" to "1"),
                ),
            ),
            DOMAIN_CRONTAB_HISTORY to DomainDoc(
                actions = listOf(ACTION_LIST),
                desc = "定时任务运行历史（分页，按开始时间倒序；行内含 crontabId/crontabName 归属信息）",
                filter = "filter.id=任务id（可选，不传返回全部任务的历史）",
                example = mapOf(
                    "domain" to DOMAIN_CRONTAB_HISTORY,
                    "action" to ACTION_LIST,
                    "filter" to mapOf("id" to "1"),
                ),
            ),
            DOMAIN_CRONTAB_HISTORY_DETAIL to DomainDoc(
                actions = listOf(ACTION_LIST),
                desc = "某次运行历史的下载明细（分页）",
                filter = "filter.id=历史id（必填，数字）",
                example = mapOf(
                    "domain" to DOMAIN_CRONTAB_HISTORY_DETAIL,
                    "action" to ACTION_LIST,
                    "filter" to mapOf("id" to "7"),
                ),
            ),
            DOMAIN_SYSTEM to DomainDoc(
                actions = listOf(ACTION_LIST),
                desc = "绑定的小米账号（脱敏）与系统信息",
                filter = null,
                example = mapOf("domain" to DOMAIN_SYSTEM, "action" to ACTION_LIST),
            ),
        )
    }
}
