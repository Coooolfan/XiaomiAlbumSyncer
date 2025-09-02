package com.coooolfan.xiaomialbumsyncer.config.jimmer

import org.babyfish.jimmer.client.generator.openapi.OpenApiGenerator
import org.noear.solon.annotation.Controller
import org.noear.solon.annotation.Mapping
import org.noear.solon.annotation.Param
import org.noear.solon.core.handle.Context
import java.io.File
import java.io.FileWriter


@Controller
class OpenApiController(private val properties: JimmerProperties) {
    @Mapping("/api/openapi.yml")
    fun download(@Param(name = "groups", required = false) groups: String?, ctx: Context) {
        val metadata = Metadata().create(
            false,
            groups,
            properties.client.getUriPrefix()
        )

        val generator: OpenApiGenerator =
            object : OpenApiGenerator(metadata, properties.client.getOpenapi().getProperties()) {
                override fun errorHttpStatus(): Int {
                    return properties.errTanslater.getHttpStatus()
                }
            }
        ctx.contentType("application/yml")
        // 创建临时文件
        val tempFile = File.createTempFile("openapi", ".yml")

        try {
            FileWriter(tempFile).use { fileWriter ->
                // generator.generate接受一个java.io.Writer
                generator.generate(fileWriter)
            }
            ctx.outputAsFile(tempFile)
            ctx.flush()

        } catch (e: Exception) {
            // 如果出错，删除临时文件
            tempFile.delete()
            throw RuntimeException("Cannot generate TypeScript files", e)
        }

    }
}