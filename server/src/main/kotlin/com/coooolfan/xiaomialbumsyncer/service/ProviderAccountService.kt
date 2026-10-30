package com.coooolfan.xiaomialbumsyncer.service

import com.coooolfan.xiaomialbumsyncer.exception.BadRequestException
import com.coooolfan.xiaomialbumsyncer.icloud.ICloudAccountService
import com.coooolfan.xiaomialbumsyncer.model.CloudProvider
import com.coooolfan.xiaomialbumsyncer.model.ProviderAccount
import com.coooolfan.xiaomialbumsyncer.xiaomicloud.XiaomiAccountService
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.noear.solon.annotation.Managed

@Managed
class ProviderAccountService(
    private val sql: KSqlClient,
    private val xiaomi: XiaomiAccountService,
    private val icloud: ICloudAccountService,
) {
    fun listAll(fetcher: Fetcher<ProviderAccount>): List<ProviderAccount> = sql.executeQuery(ProviderAccount::class) {
        select(table.fetch(fetcher))
    }

    fun delete(id: Long) {
        val account = sql.findById(ProviderAccount::class, id) ?: throw BadRequestException("账号不存在: $id")
        when (account.provider) {
            CloudProvider.XIAOMI -> xiaomi.delete(id)
            CloudProvider.ICLOUD -> icloud.delete(id)
        }
    }
}
