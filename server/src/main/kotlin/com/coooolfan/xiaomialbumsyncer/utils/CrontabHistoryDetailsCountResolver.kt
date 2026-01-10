package com.coooolfan.xiaomialbumsyncer.utils

import com.coooolfan.xiaomialbumsyncer.model.CrontabHistoryDetail
import com.coooolfan.xiaomialbumsyncer.model.crontabHistoryId
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.KTransientResolver
import org.babyfish.jimmer.sql.kt.ast.expression.count
import org.babyfish.jimmer.sql.kt.ast.expression.valueIn
import org.noear.solon.annotation.Managed

@Managed
class CrontabHistoryDetailsCountResolver(private val sql: KSqlClient) : KTransientResolver<Long, Long> {

    override fun resolve(ids: Collection<Long>): Map<Long, Long> {
        val idsSet = ids.toSet()
        val map = sql.createQuery(CrontabHistoryDetail::class) {
            where(table.crontabHistoryId valueIn idsSet)
            groupBy(table.crontabHistoryId)

            select(
                table.crontabHistoryId,
                count(table)
            )
        }.execute().associateBy({ it._1 }) {
            it._2
        }
        return idsSet.associateWith { map[it] ?: 0 }
    }
}