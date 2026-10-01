package com.coooolfan.xiaomialbumsyncer.icloud

import com.coooolfan.xiaomialbumsyncer.utils.UA
import com.coooolfan.xiaomialbumsyncer.utils.saveToFile
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.nio.file.Path
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit

internal data class ICloudCredentials(
    val appleId: String,
    val password: String,
    val domain: String,
    val clientId: String = UUID.randomUUID().toString(),
    val headers: MutableMap<String, String> = mutableMapOf(),
    var cookies: List<String> = emptyList(),
    val state: String = "READY",
)

internal class ICloudApiException(val status: Int, val serverCode: String = "") : RuntimeException(
    when {
        status == 401 -> "Apple 账号密码或验证码无效，请重新登录"
        status == 409 -> "iCloud 需要双重认证，请在账号设置中输入验证码"
        status == 412 -> "Apple 账号需要在 iCloud 网页完成账号设置或条款确认"
        serverCode == "ACCESS_DENIED" -> "iCloud 拒绝访问，请开启网页数据访问并确认高级数据保护已关闭"
        else -> "iCloud 请求失败（HTTP $status${if (serverCode.isBlank()) "" else ", $serverCode"}）"
    }
)

/** 基于 icloudpd 的私有 Web 协议，出处及许可见 THIRD_PARTY_NOTICES.md。 */
internal class ICloudClient(val credentials: ICloudCredentials, endpoints: Endpoints = Endpoints.forDomain(credentials.domain)) {
    internal data class Endpoints(val auth: String, val setup: String, val home: String) {
        companion object {
            fun forDomain(domain: String): Endpoints {
                require(domain in setOf("com", "cn")) { "iCloud 区域必须为 com 或 cn" }
                val suffix = if (domain == "cn") ".com.cn" else ".com"
                return Endpoints("https://idmsa.apple$suffix/appleauth/auth", "https://setup.icloud$suffix/setup/ws/1", "https://www.icloud$suffix")
            }
        }
    }
    private val mapper = jacksonObjectMapper()
    private val auth = endpoints.auth
    private val setup = endpoints.setup
    private val home = endpoints.home
    private val jar = object : CookieJar {
        val cookies = credentials.cookies.mapNotNull { encoded ->
            val host = encoded.substringBefore('\n')
            Cookie.parse("https://$host/".toHttpUrl(), encoded.substringAfter('\n'))
        }.toMutableList()
        @Synchronized override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            for (cookie in cookies) {
                this.cookies.removeAll { it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path }
                if (cookie.expiresAt > System.currentTimeMillis()) this.cookies.add(cookie)
            }
            credentials.cookies = this.cookies.map { "${it.domain}\n$it" }
        }
        @Synchronized override fun loadForRequest(url: HttpUrl): List<Cookie> = cookies.filter { it.expiresAt > System.currentTimeMillis() && it.matches(url) }
    }
    private val http = OkHttpClient.Builder().cookieJar(jar).connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS).followRedirects(false).build()
    // CDN 请求不携带 Apple 认证头或 cookie，允许下载重定向。
    private val downloads = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS).build()
    private var accountData: JsonNode? = null
    private var validatedAt = 0L
    var mfaRequired: Boolean = false
        private set

    @Synchronized fun login(): Boolean {
        val srp = AppleSrp(credentials.appleId)
        val init = request("$auth/signin/init", mapOf("a" to b64(srp.publicKey), "accountName" to credentials.appleId, "protocols" to listOf("s2k", "s2k_fo")), authHeaders = true)
        val (m1, m2) = srp.proof(credentials.password, Base64.getDecoder().decode(init.path("salt").asText()),
            Base64.getDecoder().decode(init.path("b").asText()), init.path("iteration").asInt(), init.path("protocol").asText())
        val complete = request("$auth/signin/complete?isRememberMeEnabled=true", mapOf(
            "accountName" to credentials.appleId, "c" to init.path("c").asText(), "m1" to b64(m1), "m2" to b64(m2),
            "rememberMe" to true, "trustTokens" to listOfNotNull(credentials.headers["X-Apple-TwoSV-Trust-Token"])), authHeaders = true, accepted = setOf(409))
        // 新的 Apple 流程需要显式触发受信任设备验证码。
        if (complete.path("_httpStatus").asInt() == 409) {
            request("$auth/verify/trusteddevice/securitycode", method = "PUT", authHeaders = true)
            mfaRequired = true
            accountData = null
            validatedAt = 0
            return false
        }
        accountLogin()
        mfaRequired = requiresMfa(accountData!!)
        if (mfaRequired) request("$auth/verify/trusteddevice/securitycode", method = "PUT", authHeaders = true)
        return !mfaRequired
    }

    @Synchronized fun verify(code: String) {
        require(Regex("[0-9]{6}").matches(code)) { "请输入六位 Apple 验证码" }
        request("$auth/verify/trusteddevice/securitycode", mapOf("securityCode" to mapOf("code" to code)), authHeaders = true)
        request("$auth/2sv/trust", method = "GET", authHeaders = true)
        accountLogin()
        mfaRequired = requiresMfa(accountData!!)
        if (mfaRequired) throw ICloudApiException(409)
    }

    @Synchronized fun ensureAuthenticated(force: Boolean = false) {
        if (!force && validatedAt > System.currentTimeMillis() - 10 * 60_000 && accountData != null) {
            if (mfaRequired) throw ICloudApiException(409)
            return
        }
        if (!force && credentials.headers["X-Apple-Session-Token"] != null) {
            try {
                accountData = request("$setup/validate", null)
                mfaRequired = requiresMfa(accountData!!)
                validatedAt = System.currentTimeMillis()
                if (mfaRequired) throw ICloudApiException(409)
                return
            } catch (e: ICloudApiException) {
                if (e.status !in setOf(401, 421, 450)) throw e
            }
        }
        if (!login()) throw ICloudApiException(409)
    }

    private fun accountLogin() {
        accountData = request("$setup/accountLogin", mapOf("accountCountryCode" to credentials.headers["X-Apple-ID-Account-Country"],
            "dsWebAuthToken" to credentials.headers["X-Apple-Session-Token"], "extended_login" to true,
            "trustToken" to credentials.headers["X-Apple-TwoSV-Trust-Token"].orEmpty()))
        val domain = accountData!!.path("domainToUse").asText()
        require(domain.isBlank()) { "该 Apple 账号需要使用 $domain，请切换 iCloud 区域" }
        validatedAt = System.currentTimeMillis()
    }

    @Synchronized fun cloud(path: String, body: Any): JsonNode {
        ensureAuthenticated()
        fun call(): JsonNode {
            val root = accountData!!.at("/webservices/ckdatabasews/url").asText()
            require(root.isNotBlank()) { "该账号未启用 iCloud 照片服务" }
            return request("${root.trimEnd('/')}/database/1/com.apple.photos.cloud/production/private/$path?" +
                "clientId=${credentials.clientId}&dsid=${accountData!!.at("/dsInfo/dsid").asText()}&getCurrentSyncToken=true&remapEnums=true", body, contentType = "text/plain")
        }
        return try { call() } catch (e: ICloudApiException) {
            if (e.status !in setOf(421, 450)) throw e
            ensureAuthenticated(force = true)
            call()
        }
    }

    fun download(url: String, target: Path) {
        val parsed = url.toHttpUrl()
        require(parsed.isHttps) { "iCloud 下载地址必须使用 HTTPS" }
        downloads.newCall(Request.Builder().url(parsed).header("User-Agent", UA).build()).execute().use { response ->
            if (!response.isSuccessful) throw ICloudApiException(response.code)
            response.body.saveToFile(target)
        }
    }

    private fun requiresMfa(data: JsonNode): Boolean = data.at("/dsInfo/hsaVersion").asInt() >= 1 &&
        (data.path("hsaChallengeRequired").asBoolean() || !data.path("hsaTrustedBrowser").asBoolean())

    private fun request(url: String, body: Any? = null, method: String = "POST", authHeaders: Boolean = false,
                        contentType: String = "application/json", accepted: Set<Int> = emptySet()): JsonNode {
        val builder = Request.Builder().url(url).header("User-Agent", UA).header("Accept", "application/json")
            .header("Origin", if (authHeaders) auth.substringBefore("/appleauth") else home)
            .header("Referer", if (authHeaders) auth.substringBefore("/appleauth") + "/" else "$home/")
        if (authHeaders) {
            val widget = "d39ba9916b7251055b22c7f910e2ea796ee65e98b2ddecea8f5dde8d9d1a815d"
            mapOf("X-Apple-OAuth-Client-Id" to widget, "X-Apple-Widget-Key" to widget,
                "X-Apple-OAuth-Client-Type" to "firstPartyAuth", "X-Apple-OAuth-Redirect-URI" to home,
                "X-Apple-OAuth-Require-Grant-Code" to "true", "X-Apple-OAuth-Response-Mode" to "web_message",
                "X-Apple-OAuth-Response-Type" to "code", "X-Apple-OAuth-State" to credentials.clientId).forEach { (k, v) -> builder.header(k, v) }
            listOf("scnt", "X-Apple-ID-Session-Id").forEach { key -> credentials.headers[key]?.let { builder.header(key, it) } }
        }
        builder.method(method, if (method == "GET") null else mapper.writeValueAsString(body).toRequestBody(contentType.toMediaType()))
        return http.newCall(builder.build()).execute().use { response ->
            listOf("scnt", "X-Apple-ID-Session-Id", "X-Apple-Session-Token", "X-Apple-TwoSV-Trust-Token", "X-Apple-ID-Account-Country").forEach { key ->
                response.header(key)?.let { credentials.headers[key] = it }
            }
            val text = response.body.string()
            val json = if (text.isBlank()) mapper.createObjectNode() else runCatching { mapper.readTree(text) }.getOrElse {
                throw ICloudApiException(response.code)
            }
            val error = json.path("serverErrorCode").asText()
            if ((!response.isSuccessful && response.code !in accepted) || error.isNotBlank()) throw ICloudApiException(response.code, error)
            if (response.code in accepted && json is com.fasterxml.jackson.databind.node.ObjectNode) json.put("_httpStatus", response.code)
            json
        }
    }
    private fun b64(value: ByteArray) = Base64.getEncoder().encodeToString(value)
}
