package com.coooolfan.xiaomialbumsyncer.config

import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.babyfish.jimmer.jackson.ImmutableModule
import org.noear.solon.annotation.Bean
import org.noear.solon.annotation.Configuration
import org.noear.solon.annotation.Inject
import org.noear.solon.serialization.jackson.JacksonActionExecutor
import org.noear.solon.serialization.jackson.JacksonRenderFactory
import org.slf4j.LoggerFactory
import java.time.Instant

@Configuration
class Jackson {

    private val log = LoggerFactory.getLogger(Jackson::class.java)

    @Bean
    fun registerJimmerJacksonModule(@Inject factory: JacksonRenderFactory, @Inject executor: JacksonActionExecutor) {
        log.info("注册适用于 Jimmer 实体的 Jackson module...")
        factory.config().registerModule(ImmutableModule())
        factory.config().registerModule(KotlinModule.Builder().build())
        executor.config().registerModule(ImmutableModule())
        executor.config().registerModule(KotlinModule.Builder().build())
        // 注册 Instant 序列化
        factory.addConvertor(Instant::class.java, { it.toString() })
    }
}