package com.coooolfan.xiaomialbumsyncer.service

import cn.dev33.satoken.stp.StpUtil
import com.coooolfan.xiaomialbumsyncer.controller.LoginRequest
import com.coooolfan.xiaomialbumsyncer.model.Album
import com.coooolfan.xiaomialbumsyncer.model.Asset
import com.coooolfan.xiaomialbumsyncer.model.Crontab
import com.coooolfan.xiaomialbumsyncer.model.SystemConfig
import com.coooolfan.xiaomialbumsyncer.model.dto.SystemConfigInit
import com.coooolfan.xiaomialbumsyncer.model.dto.SystemConfigPasswordUpdate
import com.coooolfan.xiaomialbumsyncer.model.dto.SystemConfigUpdate
import com.coooolfan.xiaomialbumsyncer.model.id
import com.coooolfan.xiaomialbumsyncer.model.password
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
            id = 0
            password = hashPwd(create.password)
            passToken = "-"
            userId = "-"
            exifToolPath = "exiftool"
            assetsDateMapTimeZone = "Asia/Shanghai"
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

    fun updateConfig(update: SystemConfig) {
        sql.saveCommand(SystemConfig(update) {
            id = 0
        }, SaveMode.UPDATE_ONLY).execute()
    }

    fun getConfig(fetcher: Fetcher<SystemConfig>): SystemConfig {
        return sql.findById(fetcher, 0) ?: throw IllegalStateException("System is not initialized")
    }

    fun updatePassword(update: SystemConfigPasswordUpdate) {
        if (!isInit()) throw IllegalStateException("System is not initialized")

        val rows = sql.executeUpdate(SystemConfig::class) {
            set(table.password, hashPwd(update.password))
            where(table.id eq 0)
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
            throw IllegalStateException("Current database is not empty, import aborted")
        }

        dataImporter.exec()
    }
}