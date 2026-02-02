package com.coooolfan.xiaomialbumsyncer.config

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.ObjectMapper
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
    fun objectMapper(serializer: JacksonStringSerializer): ObjectMapper {
        log.info("配置 Jackson 并注册全局 ObjectMapper Bean...")

        val immutableModule = ImmutableModule()
        val kotlinModule = KotlinModule.Builder().build()

        // 配置序列化 mapper
        serializer.serializeConfig.mapper.registerModule(immutableModule)
        serializer.serializeConfig.mapper.registerModule(kotlinModule)
        serializer.serializeConfig.mapper.configure(
            com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
            false
        )

        // 配置反序列化 mapper
        serializer.deserializeConfig.mapper.registerModule(immutableModule)
        serializer.deserializeConfig.mapper.registerModule(kotlinModule)
        serializer.deserializeConfig.mapper.configure(
            com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
            false
        )

        serializer.addEncoder(Instant::class.java) { it.toString() }
        serializer.addDecoder(Instant::class.java) { Instant.parse(it) }

        // 返回反序列化 mapper，因为 Jimmer 主要用于反序列化数据库中的 JSON
        return serializer.deserializeConfig.mapper
    }
}

fun <T> JacksonStringSerializer.addDecoder(clz: Class<T>, converter: Converter<String, T>) {
    deserializeConfig.customModule.addDeserializer(
        clz,
        object : JsonDeserializer<T>() {
            override fun deserialize(p: JsonParser, ctxt: DeserializationContext): T =
                converter.convert(p.valueAsString)
        }
    )
}