package com.coooolfan.xiaomialbumsyncer.xiaomicloud


import com.coooolfan.xiaomialbumsyncer.config.XiaomiApiProperties
import com.coooolfan.xiaomialbumsyncer.model.XiaomiAccount
import com.coooolfan.xiaomialbumsyncer.service.NotifyService
import com.coooolfan.xiaomialbumsyncer.utils.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Request
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.noear.solon.Solon
import org.noear.solon.annotation.Inject
import org.noear.solon.annotation.Managed
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@Managed
class TokenManager(private val sql: KSqlClient, private val notifyService: NotifyService) {

    @Inject
    private lateinit var apiProperties: XiaomiApiProperties

    private val log = org.slf4j.LoggerFactory.getLogger(TokenManager::class.java)

    private val tokenCache = ConcurrentHashMap<Long, CachedToken>()

    // passToken 失效告警为一次性事件，避免每次定时任务失败都重复推送
    private val passTokenAlerted = ConcurrentHashMap.newKeySet<Long>()

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

            val serviceToken = genServiceToken(account)
            passTokenAlerted.remove(accountId)
            tokenCache[accountId] = CachedToken(serviceToken, account.userId, Instant.now())

            return account.userId to serviceToken
        }
    }

    fun invalidateToken(accountId: Long) {
        tokenCache.remove(accountId)
        passTokenAlerted.remove(accountId)
        log.info("账号 {} 的 token 缓存已清除", accountId)
    }

    private fun needRefresh(lastFreshenTime: Instant): Boolean {
        // serviceToken 的过期时间非常短，10 分钟强制刷新
        return Instant.now().isAfter(lastFreshenTime.plusSeconds(60 * 10))
    }

    private fun genServiceToken(account: XiaomiAccount): String {

        val passToken = account.passToken
        val userId = account.userId
        val deviceId = "wb_" + UUID.randomUUID().toString()

        // 步骤一 ：获取 loginUrl
        val followUp = URLEncoder.encode(apiProperties.baseUrl.toString(), StandardCharsets.UTF_8)
        val preLoginReq = Request.Builder().url(
            apiProperties.url("api/user/login?ts=${System.currentTimeMillis()}&followUp=$followUp&_locale=zh_CN")
        ).ua()
            .header("Cookie", withCookie("userId" to userId, "deviceId" to deviceId, "passToken" to passToken)).get()
            .build()
        val loginUrl = client().newCall(preLoginReq).execute().use { res ->
            throwIfNotSuccess(res.code)
            Solon.context().objectMapper.readTree(res.body)
        }.at("/data/loginUrl").asText()

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

        // passToken 失效的特征：响应的 Set-Cookie 不再下发 serviceToken
        if (serviceToken == null) {
            onPassTokenInvalid(account)
            error("no serviceToken from remote")
        }

        log.info("serviceToken 获取成功")

        return serviceToken

    }

    private fun onPassTokenInvalid(account: XiaomiAccount) {
        if (!passTokenAlerted.add(account.id)) return
        log.error("账号 {}({}) 的 passToken 已失效，请在设置中更新", account.id, account.userId)
        CoroutineScope(Dispatchers.IO).launch {
            notifyService.sendPassTokenExpired(account)
        }
    }

}
