package com.coooolfan.xiaomialbumsyncer.xiaomicloud.e2ee

import com.coooolfan.xiaomialbumsyncer.config.XiaomiApiProperties
import com.coooolfan.xiaomialbumsyncer.exception.BadRequestException
import com.coooolfan.xiaomialbumsyncer.model.ProviderAccount
import com.coooolfan.xiaomialbumsyncer.model.dto.xiaomiCredentialsMapper
import com.coooolfan.xiaomialbumsyncer.service.NotifyService
import com.coooolfan.xiaomialbumsyncer.xiaomicloud.XiaomiAccountService
import com.coooolfan.xiaomialbumsyncer.xiaomicloud.XiaomiTokenManager
import com.fasterxml.jackson.databind.node.ObjectNode
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.noear.solon.annotation.Managed
import org.slf4j.LoggerFactory

@Managed
class XiaomiE2eeService(
    private val sql: KSqlClient,
    private val accounts: XiaomiAccountService,
    private val tokens: XiaomiTokenManager,
    private val properties: XiaomiApiProperties,
    private val notifications: NotifyService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun status(accountId: Long): XiaomiE2eeStatus = accounts.withAccount(accountId) { account, _ ->
        val credentials = xiaomiCredentialsMapper.readTree(account.credentials) as ObjectNode
        val e2ee = credentials.withObject("/xiaomiE2ee")
        val keys = e2ee.path("keys")
        try {
            val client = XiaomiKeybagClient(account.id, account.userId, tokens, properties, e2ee.path("trustCookie").textValue())
            val info = client.info()
            val state = when {
                info.requiredText("e2eeStatus") != "open" -> XiaomiE2eeState.DISABLED
                keys.hasNonNull(info.at("/maxAppkey/appKeyVersion").asText()) &&
                    e2ee.path("requiredKeyVersion").asText().let { it.isBlank() || keys.hasNonNull(it) } -> XiaomiE2eeState.READY
                else -> XiaomiE2eeState.VERIFICATION_REQUIRED
            }
            persistState(account, state)
            XiaomiE2eeStatus(accountId, state, keys.size())
        } catch (e: XiaomiKeybagException) {
            e.state?.let { persistState(account, it) }
            if (e.state == null) throw BadRequestException(e.message.orEmpty())
            XiaomiE2eeStatus(accountId, e.state, keys.size())
        }
    }

    fun push(accountId: Long) = accounts.withAccount(accountId) { account, session ->
        val e2ee = xiaomiCredentialsMapper.readTree(account.credentials).path("xiaomiE2ee")
        check(System.currentTimeMillis() - session.lastE2eeCaptchaPush >= 60_000) { "验证码发送过于频繁，请在 60 秒后重试" }
        try {
            val client = XiaomiKeybagClient(account.id, account.userId, tokens, properties, e2ee.path("trustCookie").textValue())
            check(client.info().requiredText("e2eeStatus") == "open") { "该账号未开启高级数据保护" }
            client.push()
            session.lastE2eeCaptchaPush = System.currentTimeMillis()
            persistState(account, XiaomiE2eeState.VERIFICATION_REQUIRED)
        } catch (e: XiaomiKeybagException) {
            e.state?.let { persistState(account, it) }
            throw BadRequestException(e.message.orEmpty())
        }
    }

    fun verify(accountId: Long, code: String): XiaomiE2eeStatus = accounts.withAccount(accountId) { account, session ->
        require(Regex("[0-9]{6}").matches(code)) { "请输入 6 位数字验证码" }
        val credentials = xiaomiCredentialsMapper.readTree(account.credentials) as ObjectNode
        val e2ee = credentials.withObject("/xiaomiE2ee")
        try {
            val client = XiaomiKeybagClient(account.id, account.userId, tokens, properties, e2ee.path("trustCookie").textValue())
            val fetched = client.galleryKeys(code)
            val keys = e2ee.withObject("/keys")
            fetched.forEach { (version, key) -> keys.put(version.toString(), key) }
            e2ee.put("trustCookie", client.trustCookie)
            e2ee.put("state", XiaomiE2eeState.READY.name)
            e2ee.remove(listOf("requiredKeyVersion", "sealedKeys"))
            saveCredentials(account, credentials)
            session.e2eeVerificationAlerted = false
            XiaomiE2eeStatus(accountId, XiaomiE2eeState.READY, keys.size())
        } catch (e: XiaomiKeybagException) {
            e.state?.let { persistState(account, it) }
            throw BadRequestException(e.message.orEmpty())
        }
    }

    /** 由文件 encryptInfo 决定是否解密；session 失效不会废弃已取得的 appkey。 */
    fun appKey(accountId: Long, version: Long): ByteArray = accounts.withAccount(accountId) { account, session ->
        val credentials = xiaomiCredentialsMapper.readTree(account.credentials) as ObjectNode
        val e2ee = credentials.withObject("/xiaomiE2ee")
        val key = e2ee.path("keys").path(version.toString()).textValue()
        if (key.isNullOrBlank()) {
            if (e2ee.path("requiredKeyVersion").asText() != version.toString() ||
                e2ee.path("state").asText() != XiaomiE2eeState.VERIFICATION_REQUIRED.name) {
                e2ee.put("state", XiaomiE2eeState.VERIFICATION_REQUIRED.name).put("requiredKeyVersion", version.toString())
                saveCredentials(account, credentials)
            }
            if (!session.e2eeVerificationAlerted) {
                session.e2eeVerificationAlerted = true
                log.warn("小米账号 {} 缺少文件所需的 E2EE 密钥，请在账号设置中验证", accountId)
                runCatching { notifications.sendAccountVerificationRequired(account) }
                    .onFailure { log.warn("E2EE 验证通知发送失败，账号 {}", accountId) }
            }
            throw IllegalStateException("小米高级数据保护需要验证，请在账号设置中输入信任设备收到的 6 位验证码后重试任务")
        }
        E2eeCrypto.decode(key)
    }

    private fun persistState(account: ProviderAccount, state: XiaomiE2eeState) {
        val credentials = xiaomiCredentialsMapper.readTree(account.credentials) as ObjectNode
        if (credentials.at("/xiaomiE2ee/state").asText() == state.name) return
        credentials.withObject("/xiaomiE2ee").put("state", state.name)
        saveCredentials(account, credentials)
    }

    private fun saveCredentials(account: ProviderAccount, node: ObjectNode) {
        sql.saveCommand(ProviderAccount {
            id = account.id
            credentials = xiaomiCredentialsMapper.writeValueAsString(node)
        }, SaveMode.UPDATE_ONLY).execute()
    }
}

enum class XiaomiE2eeState { DISABLED, VERIFICATION_REQUIRED, READY, SESSION_EXPIRED, RATE_LIMITED }
data class XiaomiE2eeStatus(val accountId: Long, val state: XiaomiE2eeState, val cachedKeyCount: Int)
