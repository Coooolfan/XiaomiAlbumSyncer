package com.coooolfan.xiaomialbumsyncer.config

import org.noear.solon.annotation.Configuration
import org.noear.solon.aot.RuntimeNativeMetadata
import org.noear.solon.aot.RuntimeNativeRegistrar
import org.noear.solon.aot.hint.MemberCategory
import org.noear.solon.core.AppContext
import org.slf4j.LoggerFactory

@Configuration
class NativeImageRegister : RuntimeNativeRegistrar {

    private val log = LoggerFactory.getLogger(NativeImageRegister::class.java)

    override fun register(
        context: AppContext?,
        metadata: RuntimeNativeMetadata?
    ) {
        if (metadata == null) return
        log.info("注册 Native Image 配置")

        metadata.registerResourceInclude("db/migration/*")

    }
}