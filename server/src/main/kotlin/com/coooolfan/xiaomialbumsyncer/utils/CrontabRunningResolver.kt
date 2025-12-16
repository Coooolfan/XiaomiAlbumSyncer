package com.coooolfan.xiaomialbumsyncer.utils

import com.coooolfan.xiaomialbumsyncer.config.TaskScheduler
import org.babyfish.jimmer.sql.kt.KTransientResolver
import org.noear.solon.annotation.Managed

@Managed
class CrontabRunningResolver(private val taskScheduler: TaskScheduler) : KTransientResolver<Long, Boolean> {
    
    override fun resolve(ids: Collection<Long>): Map<Long, Boolean> {
        return ids.associateWith { taskScheduler.checkIsRunning(it) }
    }
}