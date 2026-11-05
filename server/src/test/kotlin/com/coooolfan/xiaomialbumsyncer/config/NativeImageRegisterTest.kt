package com.coooolfan.xiaomialbumsyncer.config

import com.coooolfan.xiaomialbumsyncer.model.CrontabConfig
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.noear.solon.aot.RuntimeNativeMetadata
import org.noear.solon.aot.hint.MemberCategory

class NativeImageRegisterTest {

    @Test
    fun registerAddsCrontabConfigReflectionHints() {
        val metadata = RuntimeNativeMetadata()

        NativeImageRegister().register(null, metadata)

        val hints = metadata.reflection[CrontabConfig::class.java.name]
        assertNotNull(hints)
        val categories = hints!!.memberCategories
        assertTrue(categories.contains(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS))
        assertTrue(categories.contains(MemberCategory.INVOKE_PUBLIC_METHODS))
        assertTrue(categories.contains(MemberCategory.DECLARED_FIELDS))
    }
}
