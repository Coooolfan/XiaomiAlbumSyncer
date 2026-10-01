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
class ICloudAccountService(private val sql: KSqlClient, private val notifications: com.coooolfan.xiaomialbumsyncer.service.NotifyService) {
    private val mapper = jacksonObjectMapper()
    private val alerted = ConcurrentHashMap.newKeySet<Long>()
    private val clients = ConcurrentHashMap<Long, ICloudClient>()

    @Synchronized fun login(input: ICloudLoginInput): ICloudAccountStatus {
        val appleId = input.appleId.trim()
        require(appleId.isNotBlank() && input.password.isNotBlank()) { "Apple 账号和密码不能为空" }
        require(input.domain in setOf("com", "cn")) { "iCloud 区域必须为 com 或 cn" }
        val existing = if (input.accountId != null) sql.findById(ProviderAccount::class, input.accountId)
            ?: throw BadRequestException("账号不存在") else sql.executeQuery(ProviderAccount::class) {
            where(table.provider eq CloudProvider.ICLOUD)
            where(table.userId eq appleId)
            select(table)
        }.firstOrNull()
        require(existing == null || (existing.provider == CloudProvider.ICLOUD && existing.userId == appleId)) { "重新登录不能更换账号" }
        val previous = existing?.let {
            runCatching { mapper.readValue<ICloudCredentials>(it.credentials) }.getOrNull()
        }
        val credentials = if (previous?.domain == input.domain) previous.copy(password = input.password)
            else ICloudCredentials(appleId, input.password, input.domain)
        val client = ICloudClient(credentials)
        val ready = try { client.login() } catch (e: ICloudApiException) { throw BadRequestException(e.message.orEmpty()) }
        val account = sql.saveCommand(ProviderAccount {
            if (existing != null) id = existing.id
            provider = CloudProvider.ICLOUD
            userId = appleId
            nickname = input.nickname.trim().ifBlank { appleId }
            this.credentials = mapper.writeValueAsString(client.credentials.copy(state = if (ready) "READY" else "MFA_REQUIRED"))
        }, if (existing == null) SaveMode.INSERT_ONLY else SaveMode.UPDATE_ONLY).execute().modifiedEntity
        clients[account.id] = client
        if (ready) alerted.remove(account.id)
        return ICloudAccountStatus(account.id, if (ready) "READY" else "MFA_REQUIRED", credentials.domain)
    }

    fun status(accountId: Long): ICloudAccountStatus {
        val account = sql.findById(ProviderAccount::class, accountId) ?: throw BadRequestException("账号不存在")
        require(account.provider == CloudProvider.ICLOUD) { "该账号不是 iCloud 账号" }
        val credentials = mapper.readValue<ICloudCredentials>(account.credentials)
        return ICloudAccountStatus(accountId, credentials.state, credentials.domain)
    }

    fun verify(accountId: Long, code: String): ICloudAccountStatus = withClient(accountId, verifying = true) { client ->
        client.verify(code)
        ICloudAccountStatus(accountId, "READY", client.credentials.domain)
    }

    internal fun <T> withClient(accountId: Long, verifying: Boolean = false, action: (ICloudClient) -> T): T {
        // 删除的账号即使曾经被缓存，也不能继续发起请求。
        val account = sql.findById(ProviderAccount::class, accountId) ?: throw BadRequestException("账号不存在")
        require(account.provider == CloudProvider.ICLOUD) { "该账号不是 iCloud 账号" }
        val client = clients.computeIfAbsent(accountId) {
            ICloudClient(mapper.readValue<ICloudCredentials>(account.credentials))
        }
        synchronized(client) {
            try {
                val result = action(client)
                if (!client.mfaRequired) alerted.remove(accountId)
                persist(account, client, if (client.mfaRequired) "MFA_REQUIRED" else "READY")
                return result
            } catch (e: ICloudApiException) {
                val state = when {
                    verifying || e.status == 409 -> "MFA_REQUIRED"
                    e.status == 401 -> "SESSION_EXPIRED"
                    else -> null
                }
                persist(account, client, state ?: mapper.readValue<ICloudCredentials>(account.credentials).state)
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
    }

    private fun persist(account: ProviderAccount, client: ICloudClient, state: String) {
        val credentials = mapper.writeValueAsString(client.credentials.copy(state = state))
        if (mapper.readTree(account.credentials) == mapper.readTree(credentials)) return
        sql.saveCommand(ProviderAccount {
            id = account.id
            this.credentials = credentials
        }, SaveMode.UPDATE_ONLY).execute()
    }
}

data class ICloudLoginInput(val appleId: String, val password: String, val domain: String = "com", val nickname: String = "", val accountId: Long? = null)
data class ICloudVerifyInput(val code: String)
data class ICloudAccountStatus(val accountId: Long, val state: String, val domain: String)
