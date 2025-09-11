package com.coooolfan.xiaomialbumsyncer.service

import com.coooolfan.xiaomialbumsyncer.model.Crontab
import com.coooolfan.xiaomialbumsyncer.model.dto.CrontabInput
import com.coooolfan.xiaomialbumsyncer.model.id
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.noear.solon.annotation.Managed

@Managed
class CrontabService(private val sql: KSqlClient) {
    fun createCrontab(input: CrontabInput, fetcher: Fetcher<Crontab>): Crontab {
        val execute = sql.saveCommand(input, SaveMode.INSERT_ONLY).execute(fetcher)
        return execute.modifiedEntity
    }

    fun queryCrontab(fetcher: Fetcher<Crontab>): List<Crontab> {
        return sql.executeQuery(Crontab::class) {
            select(table.fetch(fetcher))
        }
    }

    fun deleteCrontab(id: Long) {
        sql.executeDelete(Crontab::class) {
            where(table.id eq id)
        }
    }
    fun updateCrontab(crontab: Crontab, fetcher: Fetcher<Crontab>): Crontab {
        val execute = sql.saveCommand(crontab, SaveMode.UPDATE_ONLY).execute(fetcher)
        return execute.modifiedEntity
    }
}