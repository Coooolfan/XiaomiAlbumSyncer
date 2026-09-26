package com.coooolfan.xiaomialbumsyncer.xiaomicloud

import com.coooolfan.xiaomialbumsyncer.service.XiaomiAccountService
import com.coooolfan.xiaomialbumsyncer.utils.client
import com.coooolfan.xiaomialbumsyncer.utils.objectMapper
import com.coooolfan.xiaomialbumsyncer.utils.requiredText
import com.coooolfan.xiaomialbumsyncer.utils.textOrNull
import com.coooolfan.xiaomialbumsyncer.utils.ua
import com.coooolfan.xiaomialbumsyncer.utils.withCookie
import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.noear.solon.Solon
import org.noear.solon.annotation.Managed
import org.slf4j.LoggerFactory
import java.io.InterruptedIOException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 小米账号扫码登录管理器
 *
 * 通过小米通行证的 longPolling 机制实现扫码登录：
 * 1. serviceLogin 获取签名参数
 * 2. longPolling/loginUrl 获取二维码图片地址与长轮询地址
 * 3. 后台协程持有 lp 长轮询直至用户扫码确认或超时
 * 4. 成功后 upsert 账号凭证（userId + passToken）
 */
@Managed
class QrLoginManager(private val accountService: XiaomiAccountService) {

    private val log = LoggerFactory.getLogger(QrLoginManager::class.java)
    private val mapper by lazy { Solon.context().objectMapper }

    private val sessions = ConcurrentHashMap<String, Session>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // lp 为挂起式长轮询，读超时需覆盖默认 client 的 30s
    private val longPollClient: OkHttpClient by lazy {
        client().newBuilder()
            .readTimeout(75, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .build()
    }

    class Session(
        val id: String,
        val expiresAt: Instant,
    ) {
        @Volatile
        var status: QrLoginStatus = QrLoginStatus.WAITING

        @Volatile
        var error: String? = null

        @Volatile
        var nickname: String? = null

        @Volatile
        var accountUserId: String? = null

        val cookies = ConcurrentHashMap<String, String>()

        fun cookieHeader(): String = withCookie(*cookies.entries.map { it.key to it.value }.toTypedArray())
    }

    /**
     * 创建扫码登录会话，返回二维码地址
     */
    fun createSession(): QrLoginSessionView {
        sessions.values.removeIf { Instant.now().isAfter(it.expiresAt) }
        val deviceId = "wb_" + UUID.randomUUID().toString()
        val session = Session(
            id = UUID.randomUUID().toString(),
            expiresAt = Instant.now().plusSeconds(SESSION_TTL_SECONDS),
        )
        session.cookies["deviceId"] = deviceId
        session.cookies["sdkVersion"] = "3.4.1"

        // 步骤一：获取签名参数
        val step1 = getJson("$PASS_BASE/serviceLogin?sid=$SID&_json=true&_locale=zh_CN", session)
        val qs = step1.requiredText("qs")
        val callback = step1.requiredText("callback")
        val sign = step1.requiredText("_sign")
        val serviceParam = step1.at("/serviceParam").asText("")

        // 步骤二：获取二维码与长轮询地址（longPolling 挂在根路径，不在 /pass 下）
        val loginUrl = "$ACCOUNT_BASE/longPolling/loginUrl".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("_qrsize", "480")
            .addQueryParameter("qs", qs)
            .addQueryParameter("callback", callback)
            .addQueryParameter("_sign", sign)
            .addQueryParameter("sid", SID)
            .addQueryParameter("serviceParam", serviceParam)
            .addQueryParameter("_locale", "zh_CN")
            .addQueryParameter("_dc", System.currentTimeMillis().toString())
            .addQueryParameter("needTheme", "false")
            .addQueryParameter("showActiveX", "false")
            .addQueryParameter("bizDeviceType", "")
            .build()

        val step2 = getJson(loginUrl.toString(), session)
        val qr = step2.requiredText("qr")
        val lp = step2.requiredText("lp")
        val timeout = step2.at("/timeout").asLong(QR_DEFAULT_TIMEOUT_SECONDS)

        sessions[session.id] = session
        scope.launch { pollLp(session, lp, timeout) }

        log.info("扫码登录会话 {} 已创建，二维码有效期 {} 秒", session.id, timeout)
        return QrLoginSessionView(session.id, qr)
    }

    /**
     * 查询扫码登录会话状态
     */
    fun getStatus(sessionId: String): QrLoginStatusView {
        val session = sessions[sessionId]
            ?: return QrLoginStatusView(status = QrLoginStatus.EXPIRED, error = "会话不存在或已过期")

        if (session.status == QrLoginStatus.WAITING && Instant.now().isAfter(session.expiresAt)) {
            session.status = QrLoginStatus.EXPIRED
        }

        return QrLoginStatusView(
            status = session.status,
            nickname = session.nickname,
            userId = session.accountUserId,
            error = session.error,
        )
    }

    private suspend fun pollLp(session: Session, lpUrl: String, timeoutSeconds: Long) {
        val deadline = Instant.now().plusSeconds(minOf(timeoutSeconds, QR_DEFAULT_TIMEOUT_SECONDS) + 15)
        while (Instant.now().isBefore(deadline)) {
            if (session.status != QrLoginStatus.WAITING) return
            try {
                val request = Request.Builder().url(lpUrl).ua()
                    .header("Cookie", session.cookieHeader())
                    .get().build()
                longPollClient.newCall(request).execute().use { res ->
                    mergeCookies(session, res)
                    if (res.code == 200) {
                        if (handleLpResult(session, res)) return
                        // 响应未携带登录凭证，视为中间状态继续轮询
                    } else {
                        log.debug("会话 {} 长轮询响应码 {}，继续等待", session.id, res.code)
                    }
                }
            } catch (_: InterruptedIOException) {
                // 长轮询超时属正常，重试直至 deadline
            } catch (e: Exception) {
                log.warn("会话 {} 长轮询异常", session.id, e)
                fail(session, e.message ?: "长轮询异常")
                return
            }
            delay(POLL_RETRY_DELAY_MS)
        }
        session.status = QrLoginStatus.EXPIRED
        log.info("会话 {} 二维码已过期", session.id)
    }

    /**
     * 处理 lp 成功响应。返回 true 表示流程终结（成功或失败），false 表示中间状态需继续轮询
     */
    private fun handleLpResult(session: Session, res: Response): Boolean {
        val body = res.body.string()
        val json = runCatching { mapper.readTree(body.removePrefix(JSON_PREFIX)) }.getOrNull()
            ?: run {
                log.warn("会话 {} 长轮询响应非 JSON: {}", session.id, body.take(200))
                fail(session, "扫码响应解析失败")
                return true
            }

        val userId = json.textOrNull("userId") ?: session.cookies["userId"]
        val passToken = json.textOrNull("passToken") ?: session.cookies["passToken"]

        if (userId.isNullOrBlank() || passToken.isNullOrBlank()) {
            // 可能是中间状态（如已扫码未确认），继续等待
            log.debug("会话 {} 长轮询响应缺少凭证字段: {}", session.id, body.take(200))
            return false
        }

        return try {
            val account = accountService.upsertCredentials(userId, passToken)
            session.status = QrLoginStatus.SUCCESS
            session.nickname = account.nickname
            session.accountUserId = account.userId
            log.info("会话 {} 扫码成功，账号 {}({}) 凭证已写入", session.id, account.id, userId)
            true
        } catch (e: Exception) {
            log.error("会话 {} 写入账号凭证失败", session.id, e)
            fail(session, "账号写入失败: ${e.message}")
            true
        }
    }

    private fun fail(session: Session, message: String) {
        session.status = QrLoginStatus.FAILED
        session.error = message
    }

    private fun getJson(url: String, session: Session): JsonNode {
        val request = Request.Builder().url(url).ua()
            .header("Cookie", session.cookieHeader())
            .get().build()
        return client().newCall(request).execute().use { res ->
            mergeCookies(session, res)
            if (res.code / 100 !in 2..3) {
                error("小米登录接口请求失败: GET $url -> HTTP ${res.code}")
            }
            mapper.readTree(res.body.string().removePrefix(JSON_PREFIX))
        }
    }

    private fun mergeCookies(session: Session, res: Response) {
        Cookie.parseAll(res.request.url, res.headers).forEach {
            session.cookies[it.name] = it.value
        }
    }

    companion object {
        private const val ACCOUNT_BASE = "https://account.xiaomi.com"
        private const val PASS_BASE = "$ACCOUNT_BASE/pass"
        private const val SID = "i.mi.com"
        private const val JSON_PREFIX = "&&&START&&&"
        private const val QR_DEFAULT_TIMEOUT_SECONDS = 300L
        private const val SESSION_TTL_SECONDS = 330L
        private const val POLL_RETRY_DELAY_MS = 500L
    }
}

enum class QrLoginStatus {
    WAITING, SUCCESS, EXPIRED, FAILED
}

data class QrLoginSessionView(
    val sessionId: String,
    val qrUrl: String,
)

data class QrLoginStatusView(
    val status: QrLoginStatus,
    val nickname: String? = null,
    val userId: String? = null,
    val error: String? = null,
)
