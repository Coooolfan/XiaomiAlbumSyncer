package com.coooolfan.xiaomialbumsyncer.config

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.babyfish.jimmer.jackson.ImmutableModule
import org.noear.solon.annotation.Bean
import org.noear.solon.annotation.Configuration
import org.noear.solon.core.convert.Converter
import org.noear.solon.serialization.jackson.JacksonStringSerializer
import org.slf4j.LoggerFactory
import java.time.Instant

@Configuration
class Jackson {

    private val log = LoggerFactory.getLogger(Jackson::class.java)

    @Bean
    fun registerJimmerJacksonModule(serializer: JacksonStringSerializer) {
        log.info("注册适用于 Jimmer 实体的 Jackson module...")
        serializer.serializeConfig.mapper.registerModule(ImmutableModule())
        serializer.serializeConfig.mapper.registerModule(KotlinModule.Builder().build())
        serializer.deserializeConfig.mapper.registerModule(ImmutableModule())
        serializer.deserializeConfig.mapper.registerModule(KotlinModule.Builder().build())

        // 注册 Instant 序列化
        serializer.addEncoder(Instant::class.java) { it.toString() }
        serializer.addDecoder(Instant::class.java) { Instant.parse(it) }
    }
}

// string -> T
fun <T> JacksonStringSerializer.addDecoder(clz: Class<T>, converter: Converter<String, T>) {
    deserializeConfig.customModule.addDeserializer(
        clz,
        object : JsonDeserializer<T>() {
            override fun deserialize(p: JsonParser, ctxt: DeserializationContext): T =
                converter.convert(p.valueAsString)
        }
    )
}