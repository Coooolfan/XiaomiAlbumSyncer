package com.coooolfan.xiaomialbumsyncer.xiaomicloud


import com.coooolfan.xiaomialbumsyncer.model.SystemConfig
import com.coooolfan.xiaomialbumsyncer.model.id
import com.coooolfan.xiaomialbumsyncer.model.passToken
import com.coooolfan.xiaomialbumsyncer.model.userId
import com.coooolfan.xiaomialbumsyncer.utils.client
import com.coooolfan.xiaomialbumsyncer.utils.throwIfNotSuccess
import com.coooolfan.xiaomialbumsyncer.utils.ua
import com.coooolfan.xiaomialbumsyncer.utils.withCookie
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.Request
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.noear.solon.annotation.Managed
import java.time.Instant
import java.util.UUID

@Managed
class TokenManager(private val sql: KSqlClient) {

    private val log = org.slf4j.LoggerFactory.getLogger(TokenManager::class.java)

    @Volatile
    private var serviceToken: String? = null

    @Volatile
    private var userId: Long? = null

    @Volatile
    private var lastFreshedTime: Instant? = null
    fun getServiceToken(): String {
        // 第一次检查（无锁）
        if (needRefresh()) {

            // 进入同步块
            synchronized(this) {
                // 第二次检查（有锁）
                if (needRefresh()) {

                    log.info("Service token 已过期或不存在，重新获取中...")

                    val config = sql.executeQuery(SystemConfig::class) {
                        where(table.id eq 0)
                        select(table.passToken, table.userId)
                    }.firstOrNull() ?: throw IllegalStateException("System is not initialized")

                    serviceToken = genServiceToken(config._1, config._2)
                    lastFreshedTime = Instant.now()
                    userId = config._2.toLong()
                }
            }
        }
        return serviceToken!!
    }

    private fun needRefresh(): Boolean {
        return serviceToken == null || lastFreshedTime == null ||
                // serviceToken 的过期时间非常短，10 分钟强制刷新
                Instant.now().isAfter(lastFreshedTime!!.plusSeconds(60 * 10))
    }

    @Synchronized
    fun getUserId(): Long {
        if (userId == null) {
            val config = sql.executeQuery(SystemConfig::class) {
                where(table.id eq 0)
                select(table.userId)
            }.firstOrNull() ?: throw IllegalStateException("System is not initialized")
            userId = config.toLong()
        }
        return userId!!
    }

    fun getAuthPair(): Pair<String, String> {
        return getUserId().toString() to getServiceToken()
    }

    private fun genServiceToken(passToken: String, userId: String): String {

        val deviceId = "wb_" + UUID.randomUUID().toString()

        // 步骤一 ：获取 loginUrl
        val preLoginReq = Request.Builder().url(
            "https://i.mi.com/api/user/login?ts=${System.currentTimeMillis()}&followUp=https%3A%2F%2Fi.mi.com%2F&_locale=zh_CN"
        ).ua()
            .header("Cookie", withCookie("userId" to userId, "deviceId" to deviceId, "passToken" to passToken)).get()
            .build()
        val preLoginRes = client().newCall(preLoginReq).execute()
        throwIfNotSuccess(preLoginRes.code)
        val preLoginBodyString = preLoginRes.body.string()
        preLoginRes.close()
        val loginUrl = jacksonObjectMapper()
            .readTree(preLoginBodyString)
            .at("/data/loginUrl")
            .asText()

        // 步骤二：向 loginUrl 发起请求，获取 签名参数
        val loginReq = Request.Builder().url(loginUrl).ua()
            .header("Cookie", withCookie("userId" to userId, "deviceId" to deviceId, "passToken" to passToken)).get()
            .build()
        val loginRes = client().newCall(loginReq).execute()
        throwIfNotSuccess(loginRes.code)
        val location = loginRes.header("Location")
        loginRes.close()

        if (location == null) {
            log.error("loginResStatusCode: ${loginRes.code} body: ${loginRes.body.string()} headers: ${loginRes.headers}")
            error("no Location header")
        }

        // 步骤三：获取 serviceToken
        val tokenReq = Request.Builder().url(location).ua()
            .header("Cookie", withCookie("userId" to userId, "deviceId" to deviceId, "passToken" to passToken)).get()
            .build()
        val tokenRes = client().newCall(tokenReq).execute()
        throwIfNotSuccess(tokenRes.code)
        val setCookies = tokenRes.headers("Set-Cookie")
        tokenRes.close()

        log.info("cookiesSize: ${setCookies.size}")

        val serviceToken = setCookies.firstOrNull { it.startsWith("serviceToken=") }?.substringAfter("serviceToken=")
            ?.substringBefore(";")
            ?: error("no serviceToken")

        log.info("serviceToken 获取成功")

        return serviceToken

    }

}