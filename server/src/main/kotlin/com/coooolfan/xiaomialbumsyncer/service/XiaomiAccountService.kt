package com.coooolfan.xiaomialbumsyncer.service

import com.coooolfan.xiaomialbumsyncer.model.XiaomiAccount
import com.coooolfan.xiaomialbumsyncer.model.dto.XiaomiAccountCreate
import com.coooolfan.xiaomialbumsyncer.xiaomicloud.TokenManager
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.noear.solon.annotation.Managed
import org.slf4j.LoggerFactory

@Managed
class XiaomiAccountService(
    private val sql: KSqlClient,
    private val tokenManager: TokenManager
) {
    private val log = LoggerFactory.getLogger(XiaomiAccountService::class.java)

    /**
     * 获取所有账号列表
     */
    fun listAll(fetcher: Fetcher<XiaomiAccount>): List<XiaomiAccount> {
        return sql.executeQuery(XiaomiAccount::class) {
            select(table.fetch(fetcher))
        }
    }

    /**
     * 根据 ID 获取账号
     */
    fun getById(id: Long): XiaomiAccount? {
        return sql.findById(XiaomiAccount::class, id)
    }

    /**
     * 添加新账号
     */
    fun create(create: XiaomiAccountCreate): XiaomiAccount {
        return sql.saveCommand(create, SaveMode.INSERT_ONLY).execute().modifiedEntity
    }

    /**
     * 更新账号信息
     */
    fun update(account: XiaomiAccount, fetcher: Fetcher<XiaomiAccount>): XiaomiAccount {
        // 验证账号存在
        if (!exists(account.id))
            throw IllegalArgumentException("账号不存在: $account.id")

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

        val rows = sql.deleteById(XiaomiAccount::class, id).affectedRowCount(XiaomiAccount::class)
        if (rows == 0) {
            throw IllegalArgumentException("账号不存在: $id")
        }
    }

    /**
     * 检查账号是否存在
     */
    fun exists(id: Long): Boolean {
        return sql.findById(XiaomiAccount::class, id) != null
    }

}
