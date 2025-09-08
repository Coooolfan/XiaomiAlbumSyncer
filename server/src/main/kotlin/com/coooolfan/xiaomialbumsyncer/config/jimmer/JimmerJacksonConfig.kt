package com.coooolfan.xiaomialbumsyncer.config.jimmer

import org.babyfish.jimmer.jackson.ImmutableModule
import org.noear.solon.annotation.Bean
import org.noear.solon.annotation.Configuration
import org.noear.solon.annotation.Inject
import org.noear.solon.serialization.jackson.JacksonActionExecutor
import org.noear.solon.serialization.jackson.JacksonRenderFactory
import org.slf4j.LoggerFactory

@Configuration
class JimmerJacksonConfig {

    private val log = LoggerFactory.getLogger(JimmerJacksonConfig::class.java)

    @Bean
    fun registerJimmerJacksonModule(@Inject factory: JacksonRenderFactory, @Inject executor: JacksonActionExecutor) {
        log.info("注册适用于 Jimmer 实体的 Jackson module...")
        factory.config().registerModule(ImmutableModule())
        executor.config().registerModule(ImmutableModule())
    }
}