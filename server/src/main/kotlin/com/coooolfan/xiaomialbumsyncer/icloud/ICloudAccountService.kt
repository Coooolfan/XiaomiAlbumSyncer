package com.coooolfan.xiaomialbumsyncer.icloud

import com.coooolfan.xiaomialbumsyncer.exception.BadRequestException
import com.coooolfan.xiaomialbumsyncer.model.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.noear.solon.annotation.Managed
import java.util.concurrent.ConcurrentHashMap

@Managed
class ICloudAccountService(private val sql: KSqlClient, private val secrets: ICloudSecrets, private val notifications: com.coooolfan.xiaomialbumsyncer.service.NotifyService) {
    private val mapper = jacksonObjectMapper()
    private val alerted = ConcurrentHashMap.newKeySet<Long>()
    private val clients = ConcurrentHashMap<Long, ICloudClient>()

    @Synchronized fun login(input: ICloudLoginInput): ICloudAccountStatus {
        val appleId = input.appleId.trim()
        require(appleId.isNotBlank() && input.password.isNotBlank()) { "Apple 账号和密码不能为空" }
        require(input.domain in setOf("com", "cn")) { "iCloud 区域必须为 com 或 cn" }
        val existing = if (input.accountId != null) sql.findById(XiaomiAccount::class, input.accountId)
            ?: throw BadRequestException("账号不存在") else sql.executeQuery(XiaomiAccount::class) {
            where(table.provider eq CloudProvider.ICLOUD)
            where(table.userId eq appleId)
            select(table)
        }.firstOrNull()
        require(existing == null || (existing.provider == CloudProvider.ICLOUD && existing.userId == appleId)) { "重新登录不能更换账号" }
        val previous = existing?.let { sql.findById(ICloudSession::class, it.id) }?.let {
            runCatching { mapper.readValue<ICloudCredentials>(secrets.decrypt(it.encryptedData)) }.getOrNull()
        }
        val credentials = if (previous?.domain == input.domain) previous.copy(password = input.password)
            else ICloudCredentials(appleId, input.password, input.domain)
        val client = ICloudClient(credentials)
        val ready = try { client.login() } catch (e: ICloudApiException) { throw BadRequestException(e.message.orEmpty()) }
        val account = sql.saveCommand(XiaomiAccount {
            if (existing != null) id = existing.id
            provider = CloudProvider.ICLOUD
            userId = appleId
            nickname = input.nickname.trim().ifBlank { appleId }
            passToken = ""
        }, if (existing == null) SaveMode.INSERT_ONLY else SaveMode.UPDATE_ONLY).execute().modifiedEntity
        clients[account.id] = client
        if (ready) alerted.remove(account.id)
        persist(account.id, client, if (ready) "READY" else "MFA_REQUIRED")
        return status(account.id)
    }

    fun status(accountId: Long): ICloudAccountStatus {
        val account = sql.findById(XiaomiAccount::class, accountId) ?: throw BadRequestException("账号不存在")
        require(account.provider == CloudProvider.ICLOUD) { "该账号不是 iCloud 账号" }
        val session = sql.findById(ICloudSession::class, accountId) ?: throw BadRequestException("iCloud 会话不存在")
        val credentials = mapper.readValue<ICloudCredentials>(secrets.decrypt(session.encryptedData))
        return ICloudAccountStatus(accountId, session.state, credentials.domain)
    }

    fun verify(accountId: Long, code: String): ICloudAccountStatus = withClient(accountId, verifying = true) { client ->
        client.verify(code)
        alerted.remove(accountId)
        persist(accountId, client, "READY")
        status(accountId)
    }

    internal fun <T> withClient(accountId: Long, verifying: Boolean = false, action: (ICloudClient) -> T): T {
        // 删除的账号即使曾经被缓存，也不能继续发起请求。
        val account = sql.findById(XiaomiAccount::class, accountId) ?: throw BadRequestException("账号不存在")
        require(account.provider == CloudProvider.ICLOUD) { "该账号不是 iCloud 账号" }
        val client = clients.computeIfAbsent(accountId) {
            val session = sql.findById(ICloudSession::class, accountId) ?: throw BadRequestException("请重新登录 iCloud")
            ICloudClient(mapper.readValue<ICloudCredentials>(secrets.decrypt(session.encryptedData)))
        }
        synchronized(client) {
            try {
                val result = action(client)
                if (!client.mfaRequired) alerted.remove(accountId)
                persist(accountId, client, if (client.mfaRequired) "MFA_REQUIRED" else "READY")
                return result
            } catch (e: ICloudApiException) {
                val state = when {
                    verifying || e.status == 409 -> "MFA_REQUIRED"
                    e.status == 401 -> "SESSION_EXPIRED"
                    else -> null
                }
                persist(accountId, client, state ?: status(accountId).state)
                if (state != null && !verifying && alerted.add(accountId)) {
                    runCatching { notifications.sendPassTokenExpired(account) }
                }
                throw BadRequestException(e.message.orEmpty())
            }
        }
    }

    @Synchronized fun forget(accountId: Long) {
        clients.remove(accountId)
        alerted.remove(accountId)
        sql.deleteById(ICloudSession::class, accountId)
    }

    private fun persist(id: Long, client: ICloudClient, state: String) {
        sql.saveCommand(ICloudSession {
            this.id = id
            encryptedData = secrets.encrypt(mapper.writeValueAsString(client.credentials))
            this.state = state
        }, SaveMode.UPSERT).execute()
    }
}

data class ICloudLoginInput(val appleId: String, val password: String, val domain: String = "com", val nickname: String = "", val accountId: Long? = null)
data class ICloudVerifyInput(val code: String)
data class ICloudAccountStatus(val accountId: Long, val state: String, val domain: String)
