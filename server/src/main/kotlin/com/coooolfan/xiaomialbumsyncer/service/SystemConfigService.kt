package com.coooolfan.xiaomialbumsyncer.service

import cn.dev33.satoken.stp.StpUtil
import com.coooolfan.xiaomialbumsyncer.controller.LoginRequest
import com.coooolfan.xiaomialbumsyncer.model.SystemConfig
import com.coooolfan.xiaomialbumsyncer.model.dto.SystemConfigInit
import com.coooolfan.xiaomialbumsyncer.model.dto.SystemConfigUpdate
import com.coooolfan.xiaomialbumsyncer.model.id
import com.coooolfan.xiaomialbumsyncer.model.password
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.noear.solon.annotation.Managed
import java.security.MessageDigest

@Managed
class SystemConfigService(private val sql: KSqlClient) {
    fun isInit(): Boolean {
        return sql.executeQuery(SystemConfig::class) {
            selectCount()
        }[0] > 0
    }

    fun initConfig(create: SystemConfigInit) {
        if (isInit()) throw IllegalStateException("System is already initialized")

        sql.saveCommand(SystemConfig {
            id = 0
            password = hashPwd(create.password)
            passToken = "-"
            userId = "-"
            exifToolPath = "exiftool"
        }, SaveMode.INSERT_ONLY).execute()
    }

    fun login(login: LoginRequest) {
        if (!isInit()) throw IllegalStateException("System is not initialized")

        val lng = sql.executeQuery(SystemConfig::class) {
            where(table.id eq 0)
            where(table.password eq hashPwd(login.password))
            selectCount()
        }[0]

        if (lng != 1.toLong()) throw IllegalStateException("Auth failed")

        StpUtil.login(0)

    }

    private fun hashPwd(password: String): String {
        val unHashed = "djshfpiuwEGfiugeiugfpiugpiuiuf$password"
        val digest = MessageDigest.getInstance("SHA3-384")
        val hashBytes = digest.digest(unHashed.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") {
            "%02x".format(it.toInt() and 0xFF)
        }
    }

    fun updateConfig(update: SystemConfig) {
        sql.saveCommand(SystemConfig(update) {
            id = 0
        }, SaveMode.UPDATE_ONLY).execute()
    }

    fun getConfig(fetcher: Fetcher<SystemConfig>): SystemConfig {
        return sql.findById(fetcher, 0) ?: throw IllegalStateException("System is not initialized")
    }
}