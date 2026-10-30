package com.coooolfan.xiaomialbumsyncer.xiaomicloud

import com.coooolfan.xiaomialbumsyncer.controller.ProviderAccountController.Companion.DEFAULT_PROVIDER_ACCOUNT
import com.coooolfan.xiaomialbumsyncer.exception.BadRequestException
import com.coooolfan.xiaomialbumsyncer.model.*
import com.coooolfan.xiaomialbumsyncer.model.dto.XiaomiAccountInput
import com.coooolfan.xiaomialbumsyncer.model.dto.xiaomiCredentialsMapper
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.noear.solon.annotation.Managed
import java.util.concurrent.ConcurrentHashMap

@Managed
class XiaomiAccountService(private val sql: KSqlClient, private val tokens: XiaomiTokenManager) {
    private val locks = Array(64) { Any() }
    private val sessions = ConcurrentHashMap<Long, XiaomiAccountSession>()

    internal fun <T> withAccount(id: Long, action: (ProviderAccount, XiaomiAccountSession) -> T): T =
        synchronized(locks[id.hashCode() and 63]) {
            val account = sql.findById(ProviderAccount::class, id) ?: throw BadRequestException("账号不存在")
            require(account.provider == CloudProvider.XIAOMI) { "该账号不是小米账号" }
            action(account, sessions.computeIfAbsent(id) { XiaomiAccountSession() })
        }

    fun create(input: XiaomiAccountInput, fetcher: Fetcher<ProviderAccount>): ProviderAccount =
        sql.saveCommand(input.toEntity(), SaveMode.INSERT_ONLY).execute(fetcher).modifiedEntity

    fun upsertCredentials(userId: String, passToken: String): ProviderAccount {
        val existing = sql.executeQuery(ProviderAccount::class) {
            where(table.userId eq userId)
            where(table.provider eq CloudProvider.XIAOMI)
            select(table)
        }.firstOrNull()
        return if (existing == null) {
            create(XiaomiAccountInput(userId, passToken, userId), DEFAULT_PROVIDER_ACCOUNT)
        } else {
            update(XiaomiAccountInput(existing.nickname, passToken, userId).toEntity(existing.id), DEFAULT_PROVIDER_ACCOUNT)
        }
    }

    fun update(account: ProviderAccount, fetcher: Fetcher<ProviderAccount>): ProviderAccount = withAccount(account.id) { existing, session ->
        val incoming = xiaomiCredentialsMapper.readTree(account.credentials) as ObjectNode
        if (existing.userId == account.userId) {
            xiaomiCredentialsMapper.readTree(existing.credentials).get("xiaomiE2ee")?.let {
                incoming.set<JsonNode>("xiaomiE2ee", it)
            }
        }
        val result = sql.saveCommand(ProviderAccount(account) {
            credentials = xiaomiCredentialsMapper.writeValueAsString(incoming)
        }, SaveMode.UPDATE_ONLY).execute(fetcher).modifiedEntity
        tokens.invalidateToken(account.id)
        if (existing.userId != account.userId) {
            session.e2eeVerificationAlerted = false
            session.lastE2eeCaptchaPush = 0
        }
        result
    }

    fun delete(id: Long) = withAccount(id) { _, _ ->
        sql.deleteById(ProviderAccount::class, id)
        tokens.invalidateToken(id)
        sessions.remove(id)
        Unit
    }
}

internal class XiaomiAccountSession {
    var e2eeVerificationAlerted = false
    var lastE2eeCaptchaPush = 0L
}
