package com.coooolfan.xiaomialbumsyncer.service

import cn.dev33.satoken.stp.StpUtil
import com.coooolfan.xiaomialbumsyncer.controller.LoginRequest
import com.coooolfan.xiaomialbumsyncer.controller.SystemConfigFtqqKeyIsInitResponse
import com.coooolfan.xiaomialbumsyncer.model.*
import com.coooolfan.xiaomialbumsyncer.model.dto.SystemConfigFtqqKeyUpdate
import com.coooolfan.xiaomialbumsyncer.model.dto.SystemConfigInit
import com.coooolfan.xiaomialbumsyncer.model.dto.SystemConfigPasswordUpdate
import com.coooolfan.xiaomialbumsyncer.utils.DataImporter
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.noear.solon.annotation.Managed
import java.security.MessageDigest

@Managed
class SystemConfigService(private val sql: KSqlClient, private val dataImporter: DataImporter) {

    fun isInit(): Boolean {
        return sql.executeQuery(SystemConfig::class) {
            selectCount()
        }[0] > 0
    }

    fun initConfig(create: SystemConfigInit) {
        if (isInit()) throw IllegalStateException("System is already initialized")

        sql.saveCommand(SystemConfig {
            id = CONFIG_ID
            password = hashPwd(create.password)
            exifToolPath = "exiftool"
            assetsDateMapTimeZone = "Asia/Shanghai"
        }, SaveMode.INSERT_ONLY).execute()
    }

    fun login(login: LoginRequest) {
        if (!isInit()) throw IllegalStateException("System is not initialized")

        val lng = sql.executeQuery(SystemConfig::class) {
            where(table.id eq CONFIG_ID)
            where(table.password eq hashPwd(login.password))
            selectCount()
        }[0]

        if (lng != 1.toLong()) throw IllegalStateException("Auth failed")

        StpUtil.login(0)

    }

    fun updateConfig(update: SystemConfig) {
        sql.saveCommand(SystemConfig(update) {
            id = CONFIG_ID
        }, SaveMode.UPDATE_ONLY).execute()
    }

    fun updatePassword(update: SystemConfigPasswordUpdate) {
        if (!isInit()) throw IllegalStateException("System is not initialized")

        val rows = sql.executeUpdate(SystemConfig::class) {
            set(table.password, hashPwd(update.password))
            where(table.id eq CONFIG_ID)
            where(table.password eq hashPwd(update.oldPassword))
        }

        if (rows != 1) throw IllegalStateException("Auth failed")
    }


    private fun hashPwd(password: String): String {
        val unHashed = "djshfpiuwEGfiugeiugfpiugpiuiuf$password"
        val digest = MessageDigest.getInstance("SHA3-384")
        val hashBytes = digest.digest(unHashed.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") {
            "%02x".format(it.toInt() and 0xFF)
        }
    }

    fun importFromV2Db() {

        // 避免新旧数据冲突与合并，只允许空库导入

        val existsRows = sql.executeQuery(Album::class) { selectCount() }[0] + //
                sql.executeQuery(Crontab::class) { selectCount() }[0] + //
                sql.executeQuery(Asset::class) { selectCount() }[0]

        if (existsRows > 0) {
            throw IllegalStateException("当前数据库非空，导入已中止")
        }

        dataImporter.exec()
    }

    fun getConfig(fetcher: Fetcher<SystemConfig>): SystemConfig {
        return sql.findById(fetcher, CONFIG_ID) ?: throw IllegalStateException("System is not initialized")
    }

    fun updateFtqqKey(update: SystemConfigFtqqKeyUpdate) {
        sql.saveCommand(SystemConfig {
            id = CONFIG_ID
            ftqqKey = update.ftqqKey
        }, SaveMode.UPDATE_ONLY).execute()
    }

    fun ftqqKeyIsInit(): SystemConfigFtqqKeyIsInitResponse {
        val config = sql.findOneById(SystemConfig::class, CONFIG_ID)
        return SystemConfigFtqqKeyIsInitResponse(config.ftqqKey.length > 1)
    }

    companion object {
        const val CONFIG_ID = 0L
    }
}

