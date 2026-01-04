package com.coooolfan.xiaomialbumsyncer.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import com.coooolfan.xiaomialbumsyncer.model.Asset
import com.coooolfan.xiaomialbumsyncer.model.XiaomiAccount
import com.coooolfan.xiaomialbumsyncer.model.by
import com.coooolfan.xiaomialbumsyncer.model.dto.XiaomiAccountCreate
import com.coooolfan.xiaomialbumsyncer.model.dto.XiaomiAccountUpdate
import com.coooolfan.xiaomialbumsyncer.model.dto.XiaomiAccountView
import com.coooolfan.xiaomialbumsyncer.service.XiaomiAccountService
import org.babyfish.jimmer.client.FetchBy
import org.babyfish.jimmer.client.meta.Api
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.noear.solon.annotation.*
import org.noear.solon.core.handle.MethodType

/**
 * 小米账号管理控制器
 *
 * 提供小米账号的增删改查功能
 * 所有接口均需要用户登录认证
 */
@Api
@Managed
@Mapping("/api/account")
@Controller
class XiaomiAccountController(private val service: XiaomiAccountService) {

    /**
     * 获取所有账号列表
     */
    @Api
    @Mapping(method = [MethodType.GET])
    @SaCheckLogin
    fun listAll(): List<@FetchBy("DEFAULT_XIAOMI_ACCOUNT") XiaomiAccount> {
        return service.listAll(DEFAULT_XIAOMI_ACCOUNT)
    }

    /**
     * 创建新账号
     */
    @Api
    @Mapping(method = [MethodType.POST])
    @SaCheckLogin
    fun create(@Body create: XiaomiAccountCreate): @FetchBy("DEFAULT_XIAOMI_ACCOUNT") XiaomiAccount {
        return service.create(create)
    }

    /**
     * 更新账号信息
     */
    @Api
    @Mapping("/{id}", method = [MethodType.PUT])
    @SaCheckLogin
    fun update(@Path id: Long, @Body update: XiaomiAccountUpdate): @FetchBy("DEFAULT_XIAOMI_ACCOUNT") XiaomiAccount {
        return service.update(update.toEntity { this.id = id }, DEFAULT_XIAOMI_ACCOUNT)
    }

    /**
     * 删除账号
     */
    @Api
    @Mapping("/{id}", method = [MethodType.DELETE])
    @SaCheckLogin
    fun delete(@Path id: Long) {
        service.delete(id)
    }


    companion object {
        private val DEFAULT_XIAOMI_ACCOUNT = newFetcher(XiaomiAccount::class).by {
            nickname()
            userId()
        }
    }
}
