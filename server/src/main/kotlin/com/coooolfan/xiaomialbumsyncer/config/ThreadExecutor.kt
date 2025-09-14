package com.coooolfan.xiaomialbumsyncer.config

import org.noear.solon.annotation.Configuration
import org.noear.solon.annotation.Managed
import java.util.concurrent.Executor
import java.util.concurrent.Executors

@Configuration
class ThreadExecutor {
    @Managed
    fun taskExecutor(): Executor {
        return Executors.newVirtualThreadPerTaskExecutor()
    }
}