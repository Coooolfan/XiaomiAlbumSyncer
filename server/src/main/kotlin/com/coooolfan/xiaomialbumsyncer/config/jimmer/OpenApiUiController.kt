package com.coooolfan.xiaomialbumsyncer.config.jimmer

import org.noear.solon.annotation.Controller
import org.noear.solon.annotation.Mapping
import org.noear.solon.annotation.Param
import org.noear.solon.core.handle.Context
import java.io.UnsupportedEncodingException
import java.net.URLEncoder


@Controller
class OpenApiUiController(private val properties: JimmerProperties) {
    @Mapping("/api/openapi.html")
    fun download(
        @Param(name = "groups", required = false) groups: String?, ctx: Context
    ) {
        ctx.contentType("text/html;charset=utf-8")
        ctx.render(html(groups))
        ctx.flush()
    }

    private fun html(groups: String?): String {
        var refPath = properties.client.getOpenapi().getRefPath()
        val resource = if (refPath != null && !refPath.isEmpty()) indexHtmlTemplate else noApiHtml
        if (groups != null && !groups.isEmpty()) {
            try {
                refPath += "?groups=" + URLEncoder.encode(groups, "utf-8")
            } catch (ex: UnsupportedEncodingException) {
                throw AssertionError("Internal bug: utf-8 is not supported")
            }
        }
        return resource.replace("\r\n", "\n") // Normalize line endings to LF
            .replace(
                $$"${openapi.css}", "https://unpkg.com/swagger-ui-dist@5.10.5/swagger-ui.css"
            ).replace(
                $$"${openapi.js}", "https://unpkg.com/swagger-ui-dist@5.10.5/swagger-ui-bundle.js"
            ).replace(
                $$"${openapi.refPath}", refPath!!
            )
    }


    private val indexHtmlTemplate = $$"""
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <link rel="icon" href="./favicon.ico" type="image/x-icon">
          <meta charset="utf-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1" />
          <meta
            name="description"
            content="SwaggerUI"
          />
          <title>Jimmer-SwaggerUI</title>
          <link rel="stylesheet" href="${openapi.css}" />
        </head>
        <body>
        <div id="swagger-ui"></div>
        <script src="${openapi.js}" crossorigin></script>
        <script>
          window.onload = () => {
            window.ui = SwaggerUIBundle({
              url: '${openapi.refPath}',
              dom_id: '#swagger-ui',
            });
          };
        </script>
        </body>
        </html>
    """.trimIndent()

    private val noApiHtml = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
        </head>
        <body>
            <div style="font-size:2rem;text-align:center">
                Cannot view API because `jimmer.client.openapi.path` is unspecified
            </div>
        </body>
        <html>
    """.trimIndent()
}