package com.coooolfan.xiaomialbumsyncer.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import com.coooolfan.xiaomialbumsyncer.model.XiaomiAccount
import com.coooolfan.xiaomialbumsyncer.model.dto.XiaomiAccountCreate
import com.coooolfan.xiaomialbumsyncer.model.dto.XiaomiAccountUpdate
import com.coooolfan.xiaomialbumsyncer.model.dto.XiaomiAccountView
import com.coooolfan.xiaomialbumsyncer.service.XiaomiAccountService
import org.babyfish.jimmer.client.meta.Api
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
    fun listAll(): List<XiaomiAccountView> {
        return service.listAll().map { XiaomiAccountView(it) }
    }

    /**
     * 根据 ID 获取账号
     */
    @Api
    @Mapping("/{id}", method = [MethodType.GET])
    @SaCheckLogin
    fun getById(@Path id: Long): XiaomiAccountView {
        val account = service.getById(id)
            ?: throw IllegalArgumentException("账号不存在: $id")
        return XiaomiAccountView(account)
    }

    /**
     * 创建新账号
     */
    @Api
    @Mapping(method = [MethodType.POST])
    @SaCheckLogin
    fun create(@Body create: XiaomiAccountCreate): XiaomiAccountView {
        val account = XiaomiAccount {
            nickname = create.nickname
            passToken = create.passToken
            userId = create.userId
        }
        val saved = service.create(account)
        return XiaomiAccountView(saved)
    }

    /**
     * 更新账号信息
     */
    @Api
    @Mapping("/{id}", method = [MethodType.PUT])
    @SaCheckLogin
    fun update(@Path id: Long, @Body update: XiaomiAccountUpdate): XiaomiAccountView {
        // 验证账号存在
        if (!service.exists(id)) {
            throw IllegalArgumentException("账号不存在: $id")
        }

        val account = XiaomiAccount {
            this.id = id
            nickname = update.nickname
            passToken = update.passToken
            userId = update.userId
        }
        val saved = service.update(account)
        return XiaomiAccountView(saved)
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

    /**
     * 获取账号数量
     */
    @Api
    @Mapping("/count", method = [MethodType.GET])
    @SaCheckLogin
    fun count(): Long {
        return service.count()
    }
}
