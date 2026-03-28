package com.coooolfan.xiaomialbumsyncer.service

import com.coooolfan.xiaomialbumsyncer.model.*
import com.coooolfan.xiaomialbumsyncer.service.SystemConfigService.Companion.CONFIG_ID
import com.coooolfan.xiaomialbumsyncer.utils.client
import com.coooolfan.xiaomialbumsyncer.utils.executeWithRetry
import com.coooolfan.xiaomialbumsyncer.utils.throwIfNotSuccess
import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.*
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.noear.solon.annotation.Managed
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

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

        val renderedBody = renderTemplate(notifyConfig.body, mapOf(
            "crontab.name" to crontab.name,
            "crontab.id" to crontab.id.toString(),
            "success" to success.toString(),
            "total" to total.toString()
        ))

        sendRequest(url, renderedBody, notifyConfig.headers, "crontabId=${crontab.id}, success=$success/$total")
    }

    fun sendDailySummary() {
        val notifyConfig = sql.findOneById(SystemConfig::class, CONFIG_ID).notifyConfig
        val url = notifyConfig.url.trim()
        val template = notifyConfig.dailySummaryBody?.trim()

        if (url.isEmpty()) {
            log.warn("通知 URL 未配置, 将跳过通知发送")
            return
        }

        if (template.isNullOrEmpty()) {
            log.info("日报通知未配置，跳过")
            return
        }

        val cutoff = Instant.now().minus(24, ChronoUnit.HOURS)

        val histories = sql.executeQuery(CrontabHistory::class) {
            where(table.startTime ge cutoff)
            orderBy(table.startTime.desc())
            select(table.fetch(DAILY_SUMMARY_HISTORY_FETCHER))
        }

        if (histories.isEmpty()) {
            log.info("过去 24h 内无执行记录，跳过日报通知")
            return
        }

        val historyIds = histories.map { it.id }.toSet()

        val totalCounts = sql.createQuery(CrontabHistoryDetail::class) {
            where(table.crontabHistoryId valueIn historyIds)
            groupBy(table.crontabHistoryId)
            select(table.crontabHistoryId, count(table))
        }.execute().associate { it._1 to it._2 }

        val successCounts = sql.createQuery(CrontabHistoryDetail::class) {
            where(table.crontabHistoryId valueIn historyIds)
            where(table.downloadCompleted eq true)
            groupBy(table.crontabHistoryId)
            select(table.crontabHistoryId, count(table))
        }.execute().associate { it._1 to it._2 }

        val zoneId = ZoneId.of(notifyConfig.dailySummaryTimeZone?.trim() ?: "UTC")
        val dateStr = LocalDate.now(zoneId).toString()
        val summaryText = buildSummaryText(histories, totalCounts, successCounts, dateStr)
        val renderedBody = renderTemplate(template, mapOf("summary" to summaryText, "date" to dateStr))

        sendRequest(url, renderedBody, notifyConfig.headers, "daily-summary")
    }

    private fun buildSummaryText(
        histories: List<CrontabHistory>,
        totalCounts: Map<Long, Long>,
        successCounts: Map<Long, Long>,
        dateStr: String
    ): String {
        val grouped = histories.groupBy { it.crontab.id }
        val sb = StringBuilder()
        sb.appendLine("[$dateStr 汇总]")

        grouped.forEach { (_, runs) ->
            val name = runs.first().crontab.name
            val runCount = runs.size
            val incompleteCount = runs.count { it.endTime == null }
            val totalSuccess = runs.sumOf { successCounts[it.id] ?: 0L }
            val totalItems = runs.sumOf { totalCounts[it.id] ?: 0L }
            val incompleteNote = if (incompleteCount > 0) "（有 $incompleteCount 次未完成）" else ""
            sb.appendLine("- $name：共执行 $runCount 次，合计成功 $totalSuccess/$totalItems$incompleteNote")
        }

        return sb.toString().trimEnd()
    }

    private fun sendRequest(url: String, body: String, headers: Map<String, String>, logContext: String) {
        val reqBuilder = Request.Builder().url(url).post(body.toRequestBody())
        headers.forEach { (key, value) ->
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
            log.info("通知已发送, {}", logContext)
        } catch (e: Exception) {
            log.error("发送通知失败, {}, url={}", logContext, url, e)
        }
    }

    private fun renderTemplate(template: String, values: Map<String, String>): String {
        val isJson = template.trimStart().startsWith("{") || template.trimStart().startsWith("[")
        return tokenRegex.replace(template) { match ->
            val raw = values[match.groupValues[1]] ?: return@replace match.value
            if (isJson) jsonEscapeValue(raw) else raw
        }
    }

    private fun jsonEscapeValue(value: String): String {
        val quoted = ObjectMapper().writeValueAsString(value)
        return quoted.substring(1, quoted.length - 1)
    }

    companion object {
        private val DAILY_SUMMARY_HISTORY_FETCHER = newFetcher(CrontabHistory::class).by {
            startTime()
            endTime()
            crontab {
                name()
            }
        }
    }
}
