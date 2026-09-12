package com.coooolfan.xiaomialbumsyncer.mcp

import com.coooolfan.xiaomialbumsyncer.exception.BadRequestException
import com.coooolfan.xiaomialbumsyncer.model.*
import com.coooolfan.xiaomialbumsyncer.service.McpTokenService
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import org.babyfish.jimmer.sql.kt.ast.expression.desc
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.noear.solon.ai.chat.tool.FunctionTool
import org.noear.solon.core.handle.Context
import java.lang.reflect.Type

/**
 * XAS 的 MCP 查询工具，输入契约为 domain + action + 分页 + id。
 */
class XasQueryTool(
    private val service: XasQueryService,
    private val tokenService: McpTokenService,
    objectMapper: ObjectMapper,
) : FunctionTool {

    // 忽略 inputSchema 之外的字段
    private val mapper = objectMapper.copy()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    override fun name(): String = TOOL_NAME

    override fun title(): String = TOOL_TITLE

    override fun description(): String = TOOL_DESCRIPTION

    override fun returnDirect(): Boolean = false

    override fun inputSchema(): String = INPUT_SCHEMA

    override fun returnType(): Type = String::class.java

    /**
     * 业务校验错误由 solon-ai 转换为 isError=true 的 CallToolResult。
     */
    override fun handle(args: Map<String, Any>): String {
        val input = try {
            mapper.convertValue(args, XasQueryInput::class.java)
        } catch (e: Exception) {
            throw BadRequestException("入参解析失败: ${e.message}")
        }

        val domain = input.domain?.trim()?.lowercase()
        val action = input.action?.trim()?.lowercase()
        val id = input.id?.trim()?.takeIf { it.isNotEmpty() }
        val pageIndex = (input.pageIndex ?: DEFAULT_PAGE_INDEX).coerceAtLeast(0)
        val pageSize = (input.pageSize ?: DEFAULT_PAGE_SIZE).coerceIn(1, MAX_PAGE_SIZE)

        val output: Any = when (domain to action) {
            DOMAIN_ALBUM to ACTION_LIST -> service.listAlbums(ALBUM_FETCHER)

            DOMAIN_CRONTAB to ACTION_LIST -> service.listCrontabs(CRONTAB_SUMMARY_FETCHER)

            DOMAIN_CRONTAB to ACTION_GET -> {
                val crontabId = id?.toLongOrNull()
                    ?: throw BadRequestException("domain=$domain 时 id=任务 id 为必填且必须是数字，收到: $id")
                service.getCrontab(crontabId, CRONTAB_OVERVIEW_FETCHER)
            }

            DOMAIN_CRONTAB to ACTION_TRIGGER -> {
                if (tokenService.resolvePermission(Context.current()?.header(XasMcpServer.AUTH_HEADER)) !=
                    McpTokenPermission.ALLOW_TRIGGER
                ) {
                    throw BadRequestException("当前 MCP Token 为只读权限，不允许触发定时任务")
                }
                val crontabId = id?.toLongOrNull()
                    ?: throw BadRequestException("domain=$domain 时 id=任务 id 为必填且必须是数字，收到: $id")
                service.triggerCrontab(crontabId, CRONTAB_TRIGGER_FETCHER)
            }

            DOMAIN_CRONTAB_HISTORY to ACTION_LIST -> {
                val crontabId = id?.toLongOrNull()
                if (id != null && crontabId == null) {
                    throw BadRequestException("id 必须是数字任务 id，收到: $id")
                }
                service.listCrontabHistories(crontabId, pageIndex, pageSize, CRONTAB_HISTORY_LIST_FETCHER)
            }

            DOMAIN_CRONTAB_HISTORY_DETAIL to ACTION_LIST -> {
                val historyId = id?.toLongOrNull()
                    ?: throw BadRequestException("domain=$domain 时 id=历史 id 为必填且必须是数字，收到: $id")
                service.listCrontabHistoryDetails(
                    historyId,
                    pageIndex,
                    pageSize,
                    CRONTAB_HISTORY_OVERVIEW_FETCHER,
                    CRONTAB_HISTORY_DETAIL_LIST_FETCHER,
                )
            }

            DOMAIN_SYSTEM to ACTION_LIST -> service.listSystem(XIAOMI_ACCOUNT_FETCHER)

            else -> throw BadRequestException("domain=$domain 不支持 action=$action")
        }
        return mapper.writeValueAsString(output)
    }

    companion object {
        const val TOOL_NAME = "xas_query"
        const val TOOL_TITLE = "Xiaomi Album Syncer 查询与任务触发"
        const val DEFAULT_PAGE_INDEX = 0
        const val DEFAULT_PAGE_SIZE = 50
        const val MAX_PAGE_SIZE = 200

        const val DOMAIN_ALBUM = "album"
        const val DOMAIN_CRONTAB = "crontab"
        const val DOMAIN_CRONTAB_HISTORY = "crontab_history"
        const val DOMAIN_CRONTAB_HISTORY_DETAIL = "crontab_history_detail"
        const val DOMAIN_SYSTEM = "system"

        const val ACTION_LIST = "list"
        const val ACTION_GET = "get"
        const val ACTION_TRIGGER = "trigger"

        private val ALBUM_FETCHER = newFetcher(Album::class).by {
            allScalarFields()
            account { nickname() }
        }

        private val CRONTAB_SUMMARY_FETCHER = newFetcher(Crontab::class).by {
            name()
            enabled()
            running()
            histories({
                filter { orderBy(table.startTime.desc()) }
                batch(1)
                limit(1)
            }) {
                startTime()
            }
        }

        private val CRONTAB_OVERVIEW_FETCHER = newFetcher(Crontab::class).by {
            name()
            description()
            enabled()
            running()
            albumIds()
            config()
            histories({
                filter { orderBy(table.startTime.desc()) }
                batch(1)
                limit(1)
            }) {
                startTime()
            }
        }

        private val CRONTAB_TRIGGER_FETCHER = newFetcher(Crontab::class).by { name() }

        private val CRONTAB_HISTORY_LIST_FETCHER = newFetcher(CrontabHistory::class).by {
            startTime()
            endTime()
            isCompleted()
            detailsCount()
            crontab { name() }
        }

        private val CRONTAB_HISTORY_OVERVIEW_FETCHER = newFetcher(CrontabHistory::class).by {
            startTime()
            endTime()
            isCompleted()
        }

        private val CRONTAB_HISTORY_DETAIL_LIST_FETCHER = newFetcher(CrontabHistoryDetail::class).by {
            filePath()
            downloadCompleted()
            sha1Verified()
            exifFilled()
            fsTimeUpdated()
            message()
            asset {
                fileName()
                type()
                album { name() }
            }
        }

        private val XIAOMI_ACCOUNT_FETCHER = newFetcher(XiaomiAccount::class).by {
            nickname()
            userId()
        }

        const val TOOL_DESCRIPTION =
            "Xiaomi Album Syncer（XAS）查询工具。统一信封入参：domain（album=相册 / " +
                "crontab=定时任务 / crontab_history=运行历史 / crontab_history_detail=运行明细 / system=账号与系统信息）" +
                "+ action（list=集合视图 / get=单资源详情 / trigger=触发任务立即执行，仅 crontab 支持）" +
                "+ 分页 pageIndex（从 0 开始）/ pageSize（默认 50，上限 200）" +
                "+ id（语义随 domain：crontab 的 get/trigger 传任务 id；crontab_history 传任务 id 可选；" +
                "crontab_history_detail 传历史 id 必填）。所有响应顶层带 hint 提示下一步操作。" +
                "除 crontab 的 trigger 为写操作外均为只读；trigger 仅允许使用 ALLOW_TRIGGER 权限的 Token。" +
                "不含密码/passToken/通知配置等敏感信息。"

        private val INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "domain": {
                  "type": "string",
                  "enum": ["album", "crontab", "crontab_history", "crontab_history_detail", "system"],
                  "description": "查询域：album=相册，crontab=定时任务，crontab_history=运行历史，crontab_history_detail=运行明细，system=账号与系统信息"
                },
                "action": {
                  "type": "string",
                  "enum": ["list", "get", "trigger"],
                  "description": "list=集合视图；get=单资源详情（仅 crontab 支持 get）；trigger=触发任务立即执行（仅 crontab 支持，写操作）"
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
                "id": {
                  "type": "string",
                  "description": "语义随 domain 变化：crontab 的 get/trigger 传任务 id（必填）；crontab_history 传任务 id（可选）；crontab_history_detail 传历史 id（必填）；album/system 不支持"
                }
              },
              "required": ["domain", "action"]
            }
        """.trimIndent()

    }
}

/**
 * xas_query 的请求参数；domain 与 action 选择操作，id 和分页参数由对应操作解释。
 */
data class XasQueryInput(
    val domain: String? = null,
    val action: String? = null,
    val pageIndex: Int? = null,
    val pageSize: Int? = null,
    val id: String? = null,
)
