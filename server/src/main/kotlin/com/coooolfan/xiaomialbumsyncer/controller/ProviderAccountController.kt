package com.coooolfan.xiaomialbumsyncer.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import com.coooolfan.xiaomialbumsyncer.model.ProviderAccount
import com.coooolfan.xiaomialbumsyncer.model.by
import com.coooolfan.xiaomialbumsyncer.model.dto.XiaomiAccountInput
import com.coooolfan.xiaomialbumsyncer.service.ProviderAccountService
import org.babyfish.jimmer.client.FetchBy
import org.babyfish.jimmer.client.meta.Api
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.noear.solon.annotation.*
import org.noear.solon.core.handle.MethodType

/** 云服务账号查询与删除，以及小米账号凭据写入。 */
@Api
@Managed
@Mapping("/api/account")
@Controller
class ProviderAccountController(private val service: ProviderAccountService) {

    /** 查询云服务账号的公开信息，不返回凭据。 */
    @Api
    @Mapping(method = [MethodType.GET])
    @SaCheckLogin
    fun listAll(): List<@FetchBy("DEFAULT_PROVIDER_ACCOUNT") ProviderAccount> {
        return service.listAll(DEFAULT_PROVIDER_ACCOUNT)
    }

    /** 创建小米账号；iCloud 账号通过 ICloudController 登录接口创建。 */
    @Api
    @Mapping(method = [MethodType.POST])
    @SaCheckLogin
    fun create(@Body create: XiaomiAccountInput): @FetchBy("DEFAULT_PROVIDER_ACCOUNT") ProviderAccount {
        return service.create(create)
    }

    /** 更新小米账号信息及凭据。 */
    @Api
    @Mapping("/{id}", method = [MethodType.PUT])
    @SaCheckLogin
    fun update(@Path id: Long, @Body update: XiaomiAccountInput): @FetchBy("DEFAULT_PROVIDER_ACCOUNT") ProviderAccount {
        return service.update(update.toEntity(id), DEFAULT_PROVIDER_ACCOUNT)
    }

    /** 删除云服务账号及关联相册、定时任务。 */
    @Api
    @Mapping("/{id}", method = [MethodType.DELETE])
    @SaCheckLogin
    fun delete(@Path id: Long) {
        service.delete(id)
    }


    companion object {
        val DEFAULT_PROVIDER_ACCOUNT = newFetcher(ProviderAccount::class).by {
            provider()
            nickname()
            userId()
        }
    }
}
