package com.coooolfan.xiaomialbumsyncer.model.dto

import com.coooolfan.xiaomialbumsyncer.model.CloudProvider
import com.coooolfan.xiaomialbumsyncer.model.ProviderAccount
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

/** 小米登录输入；凭据只写入，不通过账号 API 返回。 */
data class XiaomiAccountInput(val nickname: String, val passToken: String, val userId: String) {
    fun toEntity(id: Long? = null): ProviderAccount = ProviderAccount {
        if (id != null) this.id = id
        provider = CloudProvider.XIAOMI
        nickname = this@XiaomiAccountInput.nickname
        userId = this@XiaomiAccountInput.userId
        credentials = jacksonObjectMapper().writeValueAsString(mapOf("passToken" to passToken))
    }
}
