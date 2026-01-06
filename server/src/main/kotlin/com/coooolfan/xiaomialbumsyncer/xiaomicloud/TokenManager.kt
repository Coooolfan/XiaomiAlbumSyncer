package com.coooolfan.xiaomialbumsyncer.xiaomicloud


import com.coooolfan.xiaomialbumsyncer.model.XiaomiAccount
import com.coooolfan.xiaomialbumsyncer.utils.client
import com.coooolfan.xiaomialbumsyncer.utils.throwIfNotSuccess
import com.coooolfan.xiaomialbumsyncer.utils.ua
import com.coooolfan.xiaomialbumsyncer.utils.withCookie
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.Request
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.noear.solon.annotation.Managed
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Managed
class TokenManager(private val sql: KSqlClient) {

    private val log = org.slf4j.LoggerFactory.getLogger(TokenManager::class.java)

    private val tokenCache = ConcurrentHashMap<Long, CachedToken>()

    data class CachedToken(
        val serviceToken: String,
        val userId: String,
        val lastFreshenTime: Instant
    )

    fun getAuthPair(accountId: Long): Pair<String, String> {
        val cached = tokenCache[accountId]
        if (cached != null && !needRefresh(cached.lastFreshenTime)) {
            return cached.userId to cached.serviceToken
        }

        synchronized(this) {
            // 双重检查
            val cachedAgain = tokenCache[accountId]
            if (cachedAgain != null && !needRefresh(cachedAgain.lastFreshenTime)) {
                return cachedAgain.userId to cachedAgain.serviceToken
            }

            log.info("账号 {} 的 Service token 已过期或不存在，重新获取中...", accountId)

            val account = sql.findById(XiaomiAccount::class, accountId)
                ?: throw IllegalStateException("Account not found: $accountId")

            val serviceToken = genServiceToken(account.passToken, account.userId)
            tokenCache[accountId] = CachedToken(serviceToken, account.userId, Instant.now())

            return account.userId to serviceToken
        }
    }

    fun invalidateToken(accountId: Long) {
        tokenCache.remove(accountId)
        log.info("账号 {} 的 token 缓存已清除", accountId)
    }

    private fun needRefresh(lastFreshenTime: Instant): Boolean {
        // serviceToken 的过期时间非常短，10 分钟强制刷新
        return Instant.now().isAfter(lastFreshenTime.plusSeconds(60 * 10))
    }

    private fun genServiceToken(passToken: String, userId: String): String {

        val deviceId = "wb_" + UUID.randomUUID().toString()

        // 步骤一 ：获取 loginUrl
        val preLoginReq = Request.Builder().url(
            "https://i.mi.com/api/user/login?ts=${System.currentTimeMillis()}&followUp=https%3A%2F%2Fi.mi.com%2F&_locale=zh_CN"
        ).ua()
            .header("Cookie", withCookie("userId" to userId, "deviceId" to deviceId, "passToken" to passToken)).get()
            .build()
        val preLoginBodyString = client().newCall(preLoginReq).execute().use { res ->
            throwIfNotSuccess(res.code)
            res.body.string()
        }
        val loginUrl = jacksonObjectMapper()
            .readTree(preLoginBodyString)
            .at("/data/loginUrl")
            .asText()

        // 步骤二：向 loginUrl 发起请求，获取 签名参数
        val loginReq = Request.Builder().url(loginUrl).ua()
            .header("Cookie", withCookie("userId" to userId, "deviceId" to deviceId, "passToken" to passToken)).get()
            .build()
        val (location, loginResMeta) = client().newCall(loginReq).execute().use { res ->
            val code = res.code
            val headers = res.headers.toString()
            val body = res.body.string()
            throwIfNotSuccess(code)
            res.header("Location") to Triple(code, headers, body)
        }
        val (loginResCode, loginResHeaders, loginResDebugBody) = loginResMeta

        if (location == null) {
            log.error("loginResStatusCode: $loginResCode body: $loginResDebugBody headers: $loginResHeaders")
            error("no Location header")
        }

        // 步骤三：获取 serviceToken
        val tokenReq = Request.Builder().url(location).ua()
            .header("Cookie", withCookie("userId" to userId, "deviceId" to deviceId, "passToken" to passToken)).get()
            .build()
        val setCookies = client().newCall(tokenReq).execute().use { res ->
            throwIfNotSuccess(res.code)
            res.headers("Set-Cookie")
        }

        log.info("cookiesSize: ${setCookies.size}")

        val serviceToken = setCookies.firstOrNull { it.startsWith("serviceToken=") }?.substringAfter("serviceToken=")
            ?.substringBefore(";")
            ?: error("no serviceToken")

        log.info("serviceToken 获取成功")

        return serviceToken

    }

}
