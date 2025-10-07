package com.coooolfan.xiaomialbumsyncer.config.jimmer

import org.babyfish.jimmer.client.generator.ts.TypeScriptContext
import org.noear.solon.annotation.Controller
import org.noear.solon.annotation.Mapping
import org.noear.solon.core.handle.Context
import org.noear.solon.core.handle.MethodType
import java.io.File
import java.io.FileOutputStream


@Controller
class TypeScriptController(private val properties: JimmerProperties) {

    @Mapping("/api/openapi.zip", method = [MethodType.GET])
    fun download(ctx: Context) {

        val metadata = Metadata().create(true, null, null)

        val ts: JimmerProperties.Client.TypeScript = properties.ts
        val tsCtx = TypeScriptContext(
            metadata,
            ts.getIndent(),
            ts.isMutable(),
            ts.getApiName(),
            ts.getNullRenderMode(),
            ts.isEnumTsStyle()
        )
        // 创建临时文件
        val tempFile = File.createTempFile("typescript-api", ".zip")

        try {
            // 只创建文件输出流，让 renderAll 内部处理 ZipOutputStream
            FileOutputStream(tempFile).use { fileOutputStream ->
                // 直接传递 OutputStream，不要包装成 ZipOutputStream
                tsCtx.renderAll(fileOutputStream)
            }

            ctx.outputAsFile(tempFile)
            ctx.flush()

        } catch (e: Exception) {
            // 如果出错，删除临时文件
            tempFile.delete()
            throw RuntimeException("Cannot generate TypeScript files", e)
        } finally {
            tempFile.delete()
        }
    }
}