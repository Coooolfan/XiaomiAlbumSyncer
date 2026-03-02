package com.coooolfan.xiaomialbumsyncer.service

import com.coooolfan.xiaomialbumsyncer.model.Crontab
import com.coooolfan.xiaomialbumsyncer.model.SystemConfig
import com.coooolfan.xiaomialbumsyncer.service.SystemConfigService.Companion.CONFIG_ID
import com.coooolfan.xiaomialbumsyncer.utils.client
import com.coooolfan.xiaomialbumsyncer.utils.executeWithRetry
import com.coooolfan.xiaomialbumsyncer.utils.throwIfNotSuccess
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.noear.solon.annotation.Managed
import org.slf4j.LoggerFactory

@Managed
class NotifyService(private val sql: KSqlClient) {

    private val log = LoggerFactory.getLogger(NotifyService::class.java)
    private val tokenRegex = Regex("""\$\{([^}]+)}""")

    fun send(crontab: Crontab, success: Int, total: Int) {
        val notifyConfig = sql.findOneById(SystemConfig::class, CONFIG_ID).notifyConfig
        val url = notifyConfig.url.trim()

        if (url.isEmpty()) {
            log.warn("通知 URL 未配置, 将跳过通知发送")
            return
        }

        val renderedBody = renderBody(notifyConfig.body, crontab, success, total)
        val reqBuilder = Request.Builder().url(url).post(renderedBody.toRequestBody())

        notifyConfig.headers.forEach { (key, value) ->
            if (key.isBlank()) {
                log.warn("通知请求头存在空 key, 已忽略")
                return@forEach
            }
            reqBuilder.header(key, value)
        }

        try {
            client().executeWithRetry(reqBuilder.build()).use { resp ->
                throwIfNotSuccess(resp.code)
            }
            log.info("通知已发送, crontabId={}, success={}/{}", crontab.id, success, total)
        } catch (e: Exception) {
            log.error("发送通知失败, crontabId={}, url={}", crontab.id, url, e)
        }
    }

    private fun renderBody(template: String, crontab: Crontab, success: Int, total: Int): String {
        val values = mapOf(
            "crontab.name" to crontab.name,
            "crontab.id" to crontab.id.toString(),
            "success" to success.toString(),
            "total" to total.toString()
        )

        return tokenRegex.replace(template) { match ->
            values[match.groupValues[1]] ?: match.value
        }
    }
}
