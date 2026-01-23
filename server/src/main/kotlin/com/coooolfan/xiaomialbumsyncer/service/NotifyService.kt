package com.coooolfan.xiaomialbumsyncer.service

import com.coooolfan.xiaomialbumsyncer.model.Crontab
import com.coooolfan.xiaomialbumsyncer.model.SystemConfig
import com.coooolfan.xiaomialbumsyncer.service.SystemConfigService.Companion.CONFIG_ID
import com.coooolfan.xiaomialbumsyncer.utils.client
import com.coooolfan.xiaomialbumsyncer.utils.executeWithRetry
import okhttp3.FormBody
import okhttp3.Request
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.noear.solon.annotation.Managed
import org.slf4j.LoggerFactory

@Managed
class NotifyService(private val sql: KSqlClient) {

    private val log = LoggerFactory.getLogger(NotifyService::class.java)

    fun send(crontab: Crontab, success: Int, total: Int) {
        val ftqqKey = sql.findOneById(SystemConfig::class, CONFIG_ID).ftqqKey

        if (ftqqKey.length < 2) {
            log.warn("SendKey 未配置, 将跳过通知发送")
            return
        }

        // 同时兼容 Server酱 Turbo 与 Server酱 3
        val url = if (ftqqKey.startsWith("sctp")) {
            val uid = ftqqKey.substring(4).substringBefore("t")
            "https://$uid.push.ft07.com/send/$ftqqKey.send"
        } else {
            "https://sctapi.ftqq.com/$ftqqKey.send"
        }

        val req = Request.Builder()
            .url(url)
            .post(
                FormBody.Builder()
                    .add("text", "Xiaomi Album Syncer")
                    .add("desp", "定时任务 ${crontab.name} 已完成同步，成功 ${success}/${total}")
                    .build()
            )
            .build()

        log.info("正在发送通知")
        client().executeWithRetry(req).close()
        log.info("通知已发送")
    }
}