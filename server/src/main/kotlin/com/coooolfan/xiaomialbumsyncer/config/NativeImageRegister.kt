package com.coooolfan.xiaomialbumsyncer.config

import com.coooolfan.xiaomialbumsyncer.config.flyway.DatabaseMigration.Companion.MIGRATION_SQL_PATTERN_IN_NATIVE
import org.noear.solon.annotation.Configuration
import org.noear.solon.aot.RuntimeNativeMetadata
import org.noear.solon.aot.RuntimeNativeRegistrar
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

        listOf(
            com.coooolfan.xiaomialbumsyncer.icloud.ICloudCredentials::class.java,
            com.coooolfan.xiaomialbumsyncer.model.dto.XiaomiAccountInput::class.java,
            com.coooolfan.xiaomialbumsyncer.icloud.ICloudAlbumKey::class.java,
            com.coooolfan.xiaomialbumsyncer.icloud.ICloudResourceKey::class.java,
        ).forEach { type ->
            metadata.registerReflection(type,
                org.noear.solon.aot.hint.MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                org.noear.solon.aot.hint.MemberCategory.INVOKE_PUBLIC_METHODS,
                org.noear.solon.aot.hint.MemberCategory.DECLARED_FIELDS)
        }
        metadata.registerResourceInclude(MIGRATION_SQL_PATTERN_IN_NATIVE)

    }
}