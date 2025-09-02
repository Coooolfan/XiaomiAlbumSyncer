package com.coooolfan.xiaomialbumsyncer.config.jimmer

import org.babyfish.jimmer.client.generator.openapi.OpenApiProperties
import org.babyfish.jimmer.client.generator.ts.NullRenderMode
import org.noear.solon.annotation.Managed

@Managed
class JimmerProperties {

    val client = Client(null, null, null)

    val ts = Client.TypeScript("/ts-api.ts", "Api", 4, false, null, false)

    val errTanslater = ErrorTranslator(null, null, null, null)

    class ErrorTranslator(
        disabled: Boolean?,
        httpStatus: Int?,
        debugInfoSupported: Boolean?,
        debugInfoMaxStackTraceCount: Int?
    ) {
        private val disabled: Boolean = disabled ?: false

        private val httpStatus: Int = httpStatus ?: 500

        private val debugInfoSupported: Boolean = debugInfoSupported ?: false

        private val debugInfoMaxStackTraceCount: Int = debugInfoMaxStackTraceCount ?: Int.MAX_VALUE

        fun isDisabled(): Boolean = disabled

        fun getHttpStatus(): Int = httpStatus

        fun isDebugInfoSupported(): Boolean = debugInfoSupported

        fun getDebugInfoMaxStackTraceCount(): Int = debugInfoMaxStackTraceCount

        override fun toString(): String {
            return "ErrorTranslator{httpStatus=$httpStatus, debugInfoSupported=$debugInfoSupported}"
        }
    }

    class Client(
        private val uriPrefix: String?,
        ts: TypeScript?,
        openapi: Openapi?
    ) {
        private val ts: TypeScript = ts ?: TypeScript(null, "Api", 4, false, null, false)
        private val openapi: Openapi = openapi ?: Openapi("/openapi.yml", "/openapi.html", "/api/openapi.yml", null)

        fun getUriPrefix(): String? = uriPrefix

        fun getTs(): TypeScript = ts

        fun getOpenapi(): Openapi = openapi

        override fun toString(): String {
            return "Client{ts=$ts, openapi=$openapi}"
        }

        class TypeScript(
            path: String?,
            apiName: String?,
            indent: Int,
            private val mutable: Boolean,
            nullRenderMode: NullRenderMode?,
            private val isEnumTsStyle: Boolean
        ) {
            private val path: String? = when {
                path.isNullOrEmpty() -> null
                !path.startsWith("/") -> throw IllegalArgumentException("`jimmer.client.ts.path` must start with \"/\"")
                else -> path
            }

            private val apiName: String = if (apiName.isNullOrEmpty()) "Api" else apiName

            private val indent: Int = if (indent != 0) maxOf(indent, 2) else 4

            private val nullRenderMode: NullRenderMode = nullRenderMode ?: NullRenderMode.UNDEFINED

            fun getPath(): String? = path

            fun getApiName(): String = apiName

            fun getIndent(): Int = indent

            fun isMutable(): Boolean = mutable

            fun getNullRenderMode(): NullRenderMode = nullRenderMode

            fun isEnumTsStyle(): Boolean = isEnumTsStyle

            override fun toString(): String {
                return "TypeScript{path='$path'}"
            }
        }

        class Openapi(
            path: String?,
            uiPath: String?,
            refPath: String?,
            properties: OpenApiProperties?
        ) {
            private val path: String? = when {
                path.isNullOrEmpty() -> null
                !path.startsWith("/") -> "/$path"
                else -> path
            }

            private val uiPath: String? = when {
                uiPath.isNullOrEmpty() -> null
                !uiPath.startsWith("/") -> throw IllegalArgumentException("`jimmer.client.openapi.ui-path` must start with \"/\"")
                else -> uiPath
            }

            private val refPath: String? = when {
                refPath.isNullOrEmpty() -> if (path.isNullOrEmpty()) null else path
                else -> refPath
            }

            private val properties: OpenApiProperties

            init {
                val info = properties?.info
                this.properties = OpenApiProperties.newBuilder(properties)
                    .setInfo(
                        OpenApiProperties.newInfoBuilder(properties?.info)
                            .setTitle(
                                info?.title ?: "<`jimmer.client.openapi.properties.info.title` is unspecified>"
                            )
                            .setDescription(
                                info?.description
                                    ?: "<`jimmer.client.openapi.properties.info.description` is unspecified>"
                            )
                            .setVersion(
                                info?.version ?: "<`jimmer.client.openapi.properties.info.version` is unspecified>"
                            )
                            .build()
                    )
                    .build()
            }

            fun getPath(): String? = path

            fun getUiPath(): String? = uiPath

            fun getRefPath(): String? = refPath

            fun getProperties(): OpenApiProperties = properties

            override fun toString(): String {
                return "Openapi{apiPath='$path', uiPath='$uiPath', properties=$properties}"
            }
        }

        class JavaFeign(
            path: String?,
            apiName: String?,
            indent: Int,
            basePackage: String?
        ) {
            private val path: String? = when {
                path.isNullOrEmpty() -> null
                !path.startsWith("/") -> throw IllegalArgumentException("`jimmer.client.ts.path` must start with \"/\"")
                else -> path
            }

            private val apiName: String = if (apiName.isNullOrEmpty()) "Api" else apiName

            private val indent: Int = if (indent != 0) maxOf(indent, 2) else 4

            private val basePackage: String = basePackage ?: ""

            fun getPath(): String? = path

            fun getApiName(): String = apiName

            fun getIndent(): Int = indent

            fun getBasePackage(): String = basePackage

            override fun toString(): String {
                return "JavaFeign{path='$path', clientName='$apiName', indent=$indent, basePackage=$basePackage}"
            }
        }
    }
}