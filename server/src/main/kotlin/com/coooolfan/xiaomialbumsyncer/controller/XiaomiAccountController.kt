package com.coooolfan.xiaomialbumsyncer.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import com.coooolfan.xiaomialbumsyncer.model.XiaomiAccount
import com.coooolfan.xiaomialbumsyncer.model.by
import com.coooolfan.xiaomialbumsyncer.model.dto.XiaomiAccountCreate
import com.coooolfan.xiaomialbumsyncer.model.dto.XiaomiAccountUpdate
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
 *
 * @property service 小米账号服务，用于处理账号相关的业务逻辑
 */
@Api
@Managed
@Mapping("/api/account")
@Controller
class XiaomiAccountController(private val service: XiaomiAccountService) {

    /**
     * 获取所有小米账号列表
     *
     * 此接口用于获取系统中配置的所有小米账号信息
     * 需要用户登录认证才能访问
     *
     * @return List<XiaomiAccount> 返回所有小米账号的列表
     *
     * @api GET /api/account
     * @permission 需要登录认证
     * @description 调用XiaomiAccountService.listAll()方法获取所有账号数据
     */
    @Api
    @Mapping(method = [MethodType.GET])
    @SaCheckLogin
    fun listAll(): List<@FetchBy("DEFAULT_XIAOMI_ACCOUNT") XiaomiAccount> {
        return service.listAll(DEFAULT_XIAOMI_ACCOUNT)
    }

    /**
     * 创建新的小米账号
     *
     * 此接口用于在系统中添加新的小米账号配置
     * 需要用户登录认证才能访问
     *
     * @param create 账号创建参数，包含昵称、passToken、userId等信息
     * @return XiaomiAccount 返回创建成功的账号对象
     *
     * @api POST /api/account
     * @permission 需要登录认证
     * @description 调用XiaomiAccountService.create()方法创建新账号
     */
    @Api
    @Mapping(method = [MethodType.POST])
    @SaCheckLogin
    fun create(@Body create: XiaomiAccountCreate): @FetchBy("DEFAULT_XIAOMI_ACCOUNT") XiaomiAccount {
        return service.create(create)
    }

    /**
     * 更新小米账号信息
     *
     * 此接口用于更新已存在的小米账号配置信息
     * 需要用户登录认证才能访问
     *
     * @param id 账号ID，用于指定要更新的账号
     * @param update 账号更新参数，包含要更新的账号信息
     * @return XiaomiAccount 返回更新后的账号对象
     *
     * @api PUT /api/account/{id}
     * @permission 需要登录认证
     * @description 调用XiaomiAccountService.update()方法更新账号信息
     */
    @Api
    @Mapping("/{id}", method = [MethodType.PUT])
    @SaCheckLogin
    fun update(@Path id: Long, @Body update: XiaomiAccountUpdate): @FetchBy("DEFAULT_XIAOMI_ACCOUNT") XiaomiAccount {
        return service.update(update.toEntity { this.id = id }, DEFAULT_XIAOMI_ACCOUNT)
    }

    /**
     * 删除小米账号
     *
     * 此接口用于从系统中删除指定的小米账号
     * 删除账号会同时删除关联的相册和定时任务（由数据库外键约束处理）
     * 需要用户登录认证才能访问
     *
     * @param id 账号ID，用于指定要删除的账号
     *
     * @api DELETE /api/account/{id}
     * @permission 需要登录认证
     * @description 调用XiaomiAccountService.delete()方法删除账号
     */
    @Api
    @Mapping("/{id}", method = [MethodType.DELETE])
    @SaCheckLogin
    fun delete(@Path id: Long) {
        service.delete(id)
    }


    companion object {
        val DEFAULT_XIAOMI_ACCOUNT = newFetcher(XiaomiAccount::class).by {
            nickname()
            userId()
        }
    }
}
