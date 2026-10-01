package com.coooolfan.xiaomialbumsyncer.service

import com.coooolfan.xiaomialbumsyncer.controller.ProviderAccountController.Companion.DEFAULT_PROVIDER_ACCOUNT
import com.coooolfan.xiaomialbumsyncer.model.ProviderAccount
import com.coooolfan.xiaomialbumsyncer.model.userId
import com.coooolfan.xiaomialbumsyncer.model.provider
import com.coooolfan.xiaomialbumsyncer.model.CloudProvider
import com.coooolfan.xiaomialbumsyncer.model.dto.XiaomiAccountInput
import com.coooolfan.xiaomialbumsyncer.xiaomicloud.TokenManager
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.noear.solon.annotation.Managed

@Managed
class ProviderAccountService(
    private val sql: KSqlClient,
    private val tokenManager: TokenManager,
    private val icloud: com.coooolfan.xiaomialbumsyncer.icloud.ICloudAccountService
) {
    /**
     * 获取所有账号列表
     */
    fun listAll(fetcher: Fetcher<ProviderAccount>): List<ProviderAccount> {
        return sql.executeQuery(ProviderAccount::class) {
            select(table.fetch(fetcher))
        }
    }

    /**
     * 添加新账号
     */
    fun create(create: XiaomiAccountInput): ProviderAccount {
        return sql.saveCommand(create.toEntity(), SaveMode.INSERT_ONLY).execute(DEFAULT_PROVIDER_ACCOUNT).modifiedEntity
    }

    /**
     * 按 userId 写入登录凭证：账号已存在则更新 passToken 并刷新 token 缓存，否则以 userId 为默认昵称创建
     */
    fun upsertCredentials(userId: String, passToken: String): ProviderAccount {
        val existing = sql.executeQuery(ProviderAccount::class) {
            where(table.userId eq userId)
            where(table.provider eq CloudProvider.XIAOMI)
            select(table)
        }.firstOrNull()

        if (existing != null) {
            return update(ProviderAccount(existing) { credentials = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().writeValueAsString(mapOf("passToken" to passToken)) }, DEFAULT_PROVIDER_ACCOUNT)
        }

        return create(XiaomiAccountInput(nickname = userId, passToken = passToken, userId = userId))
    }

    /**
     * 更新账号信息
     */
    fun update(account: ProviderAccount, fetcher: Fetcher<ProviderAccount>): ProviderAccount {
        val existing = sql.findById(ProviderAccount::class, account.id)
            ?: throw IllegalArgumentException("账号不存在，ID: ${account.id}")
        require(existing.provider == CloudProvider.XIAOMI) { "请使用 iCloud 登录接口更新该账号" }
        val result = sql.saveCommand(account, SaveMode.UPDATE_ONLY).execute(fetcher)
        // 更新后清除该账号的 token 缓存
        tokenManager.invalidateToken(account.id)
        return result.modifiedEntity
    }

    /**
     * 删除账号
     * 注意：删除账号会同时删除关联的相册和定时任务（由数据库外键约束处理）
     */
    fun delete(id: Long) {
        // 先清除 token 缓存
        tokenManager.invalidateToken(id)

        icloud.forget(id)
        val rows = sql.deleteById(ProviderAccount::class, id).affectedRowCount(ProviderAccount::class)
        if (rows == 0) {
            throw IllegalArgumentException("账号不存在: $id")
        }
    }

}
