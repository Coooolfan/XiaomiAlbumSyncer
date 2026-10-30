package com.coooolfan.xiaomialbumsyncer.service

import com.coooolfan.xiaomialbumsyncer.icloud.ICloudAccountService
import com.coooolfan.xiaomialbumsyncer.model.dto.xiaomiCredentialsMapper
import com.coooolfan.xiaomialbumsyncer.controller.ProviderAccountController.Companion.DEFAULT_PROVIDER_ACCOUNT
import com.coooolfan.xiaomialbumsyncer.model.ProviderAccount
import com.coooolfan.xiaomialbumsyncer.model.userId
import com.coooolfan.xiaomialbumsyncer.model.provider
import com.coooolfan.xiaomialbumsyncer.model.CloudProvider
import com.coooolfan.xiaomialbumsyncer.model.dto.XiaomiAccountInput
import com.coooolfan.xiaomialbumsyncer.xiaomicloud.XiaomiTokenManager
import com.coooolfan.xiaomialbumsyncer.xiaomicloud.e2ee.XiaomiE2eeService
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.noear.solon.annotation.Managed

@Managed
class ProviderAccountService(
    private val sql: KSqlClient,
    private val tokenManager: XiaomiTokenManager,
    private val e2ee: XiaomiE2eeService,
    private val icloud: ICloudAccountService
) {
    fun listAll(fetcher: Fetcher<ProviderAccount>): List<ProviderAccount> {
        return sql.executeQuery(ProviderAccount::class) {
            select(table.fetch(fetcher))
        }
    }

    /**
     * 创建小米账号
     */
    fun create(create: XiaomiAccountInput): ProviderAccount {
        return sql.saveCommand(create.toEntity(), SaveMode.INSERT_ONLY).execute(DEFAULT_PROVIDER_ACCOUNT).modifiedEntity
    }

    /**
     * 按小米 userId 写入凭据；已有账号更新 passToken 并失效缓存，新账号使用 userId 作为昵称
     */
    fun upsertCredentials(userId: String, passToken: String): ProviderAccount {
        val existing = sql.executeQuery(ProviderAccount::class) {
            where(table.userId eq userId)
            where(table.provider eq CloudProvider.XIAOMI)
            select(table)
        }.firstOrNull()

        if (existing != null) {
            return update(ProviderAccount(existing) { credentials = xiaomiCredentialsMapper.writeValueAsString(mapOf("passToken" to passToken)) }, DEFAULT_PROVIDER_ACCOUNT)
        }

        return create(XiaomiAccountInput(nickname = userId, passToken = passToken, userId = userId))
    }

    /**
     * 更新小米账号信息及凭据
     */
    fun update(account: ProviderAccount, fetcher: Fetcher<ProviderAccount>): ProviderAccount = e2ee.withAccountLock(account.id) {
        val existing = sql.findById(ProviderAccount::class, account.id)
            ?: throw IllegalArgumentException("账号不存在，ID: ${account.id}")
        require(existing.provider == CloudProvider.XIAOMI) { "请使用 iCloud 登录接口更新该账号" }
        val incoming = xiaomiCredentialsMapper.readTree(account.credentials) as ObjectNode
        if (existing.userId == account.userId) {
            xiaomiCredentialsMapper.readTree(existing.credentials).get("xiaomiE2ee")?.let {
                incoming.set<JsonNode>("xiaomiE2ee", it)
            }
        }
        val result = sql.saveCommand(ProviderAccount(account) {
            credentials = xiaomiCredentialsMapper.writeValueAsString(incoming)
        }, SaveMode.UPDATE_ONLY).execute(fetcher)
        tokenManager.invalidateToken(account.id)
        if (existing.userId != account.userId) e2ee.forget(account.id)
        result.modifiedEntity
    }

    /**
     * 删除云服务账号；数据库外键级联删除关联相册和定时任务
     */
    fun delete(id: Long) = e2ee.withAccountLock(id) {
        e2ee.forget(id)
        tokenManager.invalidateToken(id)

        icloud.forget(id)
        val rows = sql.deleteById(ProviderAccount::class, id).affectedRowCount(ProviderAccount::class)
        if (rows == 0) {
            throw IllegalArgumentException("账号不存在: $id")
        }
    }

}
