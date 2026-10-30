package com.coooolfan.xiaomialbumsyncer.xiaomicloud.e2ee

import com.coooolfan.xiaomialbumsyncer.config.XiaomiApiProperties
import com.coooolfan.xiaomialbumsyncer.model.dto.xiaomiCredentialsMapper
import com.coooolfan.xiaomialbumsyncer.utils.client
import com.coooolfan.xiaomialbumsyncer.utils.executeWithRetry
import com.coooolfan.xiaomialbumsyncer.utils.ua
import com.coooolfan.xiaomialbumsyncer.xiaomicloud.XiaomiTokenManager
import com.fasterxml.jackson.databind.JsonNode
import okhttp3.Cookie
import okhttp3.FormBody
import okhttp3.Request

/** 一次账号级串行握手；POST 不自动重发，避免重复消费 nonce 或验证码。 */
internal class XiaomiKeybagClient(
    private val accountId: Long,
    private val userId: String,
    private val tokens: XiaomiTokenManager,
    private val properties: XiaomiApiProperties,
    trustCookie: String? = null,
    private val hsmKeys: Map<Int, String> = hsmPublicKeys,
) {
    private val cookies = mutableMapOf<String, Cookie>()
    private var sessionChecked = false
    init {
        trustCookie?.let { Cookie.parse(properties.baseUrl, it) }?.let { cookies[it.name] = it }
    }
    val trustCookie: String? get() = cookies["trust_device_auth_token"]?.toString()

    fun info(): JsonNode = request("getEncInfo?hsid=2&appId=micloud")
    fun push() { request("keybagCaptchaPush", mapOf("hsid" to 2, "from" to "pc")) }

    fun galleryKeys(code: String): Map<Long, String> {
        require(Regex("[0-9]{6}").matches(code)) { "请输入 6 位数字验证码" }
        val info = info()
        check(info.requiredText("e2eeStatus") == "open") { "该账号未开启高级数据保护" }
        val first = exchange(info, code = code)
        val payload = first.first
        val token = payload.requiredText("encryptToken")
        val keys = linkedMapOf<Long, String>()
        addKeys(payload, keys)
        val total = first.second.path("totalAppKeyCnt").takeIf { it.isIntegralNumber }?.asInt()
            ?: throw IllegalStateException("keybag 响应缺少 totalAppKeyCnt")
        check(total > 0 && keys.isNotEmpty() && keys.size <= total) { "keybag 返回的密钥数量无效" }
        var lastVersion = keys.keys.min()
        while (keys.size < total) {
            val previousCount = keys.size
            val (page, _) = exchange(info(), token = token, lastVersion = lastVersion)
            addKeys(page, keys)
            val nextVersion = keys.keys.min()
            check(keys.size > previousCount && nextVersion < lastVersion && keys.size <= total) { "keybag 密钥分页未推进" }
            lastVersion = nextVersion
        }
        return keys
    }

    private fun addKeys(payload: JsonNode, keys: MutableMap<Long, String>) {
        val list = payload.path("appKey")
        check(list.isArray) { "keybag 响应缺少 appKey 列表" }
        list.forEach {
            val version = it.path("appKeyVersion")
            check(version.isIntegralNumber && version.canConvertToLong() && version.asLong() > 0) { "appKeyVersion 无效" }
            val key = it.requiredText("appKey")
            check(E2eeCrypto.decode(key).size == 32) { "appkey 长度错误" }
            val old = keys.put(version.asLong(), key)
            check(old == null || old == key) { "同版本 appkey 不一致" }
        }
    }

    private fun exchange(info: JsonNode, code: String? = null, token: String? = null, lastVersion: Long = 0): Pair<JsonNode, JsonNode> {
        val zone = info.path("zone").asInt()
        val hsm = E2eeCrypto.importHsmKey(hsmKeys[zone] ?: throw IllegalStateException("暂不支持小米密钥区域 $zone"))
        val nonce = info.requiredText("nonce")
        val serverSignZone = info.path("serverSignZone").takeIf { it.isIntegralNumber }?.asInt()
            ?: throw IllegalStateException("keybag 响应缺少 serverSignZone")
        val pair = E2eeCrypto.rsaKeyPair()
        val pem = E2eeCrypto.publicPem(pair.public)
        val numericUserId = userId.toLongOrNull() ?: throw IllegalArgumentException("小米 userId 必须是数字")
        val fields = if (code != null) listOf("hsid", "userId", "clientSecureLevel", "tag", "tpubString", "captchaCode")
            else listOf("hsid", "userId", "clientSecureLevel", "nonce", "serverSignZone", "tpubString", "encryptToken")
        val data = linkedMapOf<String, Any>("hsid" to 2, "userId" to numericUserId, "clientSecureLevel" to 900000,
            "tag" to "1", "nonce" to nonce, "serverSignZone" to serverSignZone, "tpubString" to pem)
        if (code != null) data["captchaCode"] = code else data["encryptToken"] = requireNotNull(token)
        data["clientSignField"] = fields
        data["clientSign"] = E2eeCrypto.sha256(fields.joinToString("~") { data[it].toString() })
        val ticket = E2eeCrypto.ticket(zone, hsm, xiaomiCredentialsMapper.writeValueAsBytes(data))
        val params = linkedMapOf<String, Any>("hsid" to 2, "shaType" to "SHA1", "nameSpace" to "gallery",
            "appId" to "micloud", "nonce" to nonce, "tpubString" to pem, "clientSecureLevel" to 900000)
        params[if (code != null) "clientTicket" else "clientTokenTicket"] = ticket
        if (code == null) params["lastAppKeyVersion"] = lastVersion
        val response = request(if (code != null) "getAppkeyTokenByCaptcha" else "getAppkeyByToken", params)
        val plain = E2eeCrypto.unwrapResponse(response, if (code != null) "tokenAppkeyTicket" else "appkeyTicket", pair.private)
        return try { xiaomiCredentialsMapper.readTree(plain) to response } finally { plain.fill(0) }
    }

    private fun request(path: String, data: Map<String, Any>? = null): JsonNode {
        if (!sessionChecked) {
            requestUrl("common/check", null)
            sessionChecked = true
        }
        return requestUrl("mic/keybag/v1/$path", data)
    }

    private fun requestUrl(path: String, data: Map<String, Any>?): JsonNode {
        val url = properties.baseUrl.resolve(path) ?: error("keybag URL 无效")
        val baseCookie = tokens.cookieHeader(accountId, url)
        val extraCookie = cookies.values.filter { it.matches(url) && it.expiresAt > System.currentTimeMillis() }
            .joinToString("; ") { "${it.name}=${it.value}" }
        val request = Request.Builder().url(url).ua().header("Cookie", "$baseCookie $extraCookie").apply {
            if (data == null) get() else post(FormBody.Builder()
                .add("data", xiaomiCredentialsMapper.writeValueAsString(data))
                .add("serviceToken", tokens.getAuthPair(accountId).second).build())
        }.build()
        val call = if (data == null) client().executeWithRetry(request) else client().newCall(request).execute()
        return call.use { response ->
            if (response.code == 401) {
                tokens.invalidateToken(accountId)
                throw XiaomiKeybagException("小米登录已失效，请更新账号凭据后重试", XiaomiE2eeState.SESSION_EXPIRED)
            }
            if (response.code == 429) throw XiaomiKeybagException("小米密钥服务请求过于频繁，请稍后再试", XiaomiE2eeState.RATE_LIMITED)
            if (!response.isSuccessful) throw XiaomiKeybagException("小米密钥服务请求失败（HTTP ${response.code}）")
            val body = xiaomiCredentialsMapper.readTree(response.body.byteStream())
            val code = body.path("code")
            check(code.isIntegralNumber) { "keybag 响应缺少业务状态码" }
            if (code.asInt() != 0) throw when (code.asInt()) {
                14000000 -> XiaomiKeybagException("验证码错误，请重新输入", XiaomiE2eeState.VERIFICATION_REQUIRED)
                32002 -> XiaomiKeybagException("验证码已过期，请重新发送", XiaomiE2eeState.VERIFICATION_REQUIRED)
                10000000 -> XiaomiKeybagException("今日验证码错误次数已达上限，请稍后再试", XiaomiE2eeState.RATE_LIMITED)
                else -> XiaomiKeybagException("小米密钥服务返回错误（${code.asInt()}）")
            }
            Cookie.parseAll(response.request.url, response.headers).forEach { cookies[it.name] = it }
            body.path("data")
        }
    }
}

internal class XiaomiKeybagException(message: String, val state: XiaomiE2eeState? = null) : RuntimeException(message)
