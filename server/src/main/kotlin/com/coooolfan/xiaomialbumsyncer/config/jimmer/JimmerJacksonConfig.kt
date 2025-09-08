package com.coooolfan.xiaomialbumsyncer.config.jimmer

import org.babyfish.jimmer.jackson.ImmutableModule
import org.noear.solon.annotation.Bean
import org.noear.solon.annotation.Configuration
import org.noear.solon.annotation.Inject
import org.noear.solon.serialization.jackson.JacksonActionExecutor
import org.noear.solon.serialization.jackson.JacksonRenderFactory
import org.slf4j.LoggerFactory
import java.lang.String


/**
 * Jimmer 的 Solon 插件实现，自动为 Jackson 配置 ImmutableModule。
 */
@Configuration
class JimmerJacksonConfig {

    private val log = LoggerFactory.getLogger(JimmerJacksonConfig::class.java)

    @Bean
    fun registerJimmerJacksonModule(@Inject factory: JacksonRenderFactory, @Inject executor: JacksonActionExecutor) {
        log.info("注册适用于 Jimmer 实体的 Jackson module...")
        factory.addConvertor(String::class.java, { b -> "registerJimmerJacksonModule" })
        factory.config().registerModule(ImmutableModule())
        executor.config().registerModule(ImmutableModule())
        log.info("注册结束")
    }

//    @Bean
//    fun registerJimmerSnack3Module(@Inject factory: SnackRenderFactory, @Inject executor: SnackActionExecutor) {
//        log.info("注册适用于 Jimmer 实体的 Jackson module...")
////        factory.config().registerModule(ImmutableModule())
////        executor.config().registerModule(ImmutableModule())
//        factory.addConvertor(String::class.java, { b -> "registerJimmerSnack3Module" })
//        log.info("注册结束")
//    }
}