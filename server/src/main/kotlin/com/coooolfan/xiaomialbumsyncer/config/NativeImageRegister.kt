package com.coooolfan.xiaomialbumsyncer.config

import org.noear.solon.annotation.Configuration
import org.noear.solon.aot.RuntimeNativeMetadata
import org.noear.solon.aot.RuntimeNativeRegistrar
import org.noear.solon.aot.hint.MemberCategory
import org.noear.solon.core.AppContext

@Configuration
class NativeImageRegister : RuntimeNativeRegistrar {
    override fun register(
        context: AppContext?,
        metadata: RuntimeNativeMetadata?
    ) {
        if (metadata == null) return
        metadata.registerReflection(
            com.coooolfan.xiaomialbumsyncer.model.dto.SystemConfigPasswordUpdate::class.java,
            MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS, MemberCategory.INVOKE_PUBLIC_METHODS
        )
    }
}