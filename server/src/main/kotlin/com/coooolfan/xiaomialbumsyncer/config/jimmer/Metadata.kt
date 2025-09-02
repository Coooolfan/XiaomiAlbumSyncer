package com.coooolfan.xiaomialbumsyncer.config.jimmer

import org.babyfish.jimmer.client.meta.TypeName
import org.babyfish.jimmer.client.runtime.Metadata
import org.babyfish.jimmer.client.runtime.Operation
import org.babyfish.jimmer.client.runtime.VirtualType
import org.noear.solon.annotation.*
import org.noear.solon.core.handle.UploadedFile
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Method
import java.lang.reflect.Parameter
import java.util.regex.Pattern

class Metadata {
    fun create(
        isGenericSupported: Boolean,
        groups: String?,
        uriPrefix: String?
    ): Metadata {
        val COMMA_PATTERN = Pattern.compile("\\s*,\\s*")

        var realGroups: List<String>? = null
        if (groups != null && !groups.isEmpty()) {
            realGroups = COMMA_PATTERN.split(groups).toList()
        }

        val virtualTypeMap = HashMap<TypeName, VirtualType>()
        virtualTypeMap[TypeName.of(UploadedFile::class.java)] = VirtualType.FILE
        return Metadata
            .newBuilder()
            .setOperationParser(OperationParserImpl())
            .setParameterParser(ParameterParserImpl())
            .setVirtualTypeMap(virtualTypeMap)
            .setGenericSupported(isGenericSupported)
            .setGroups(realGroups)
            .setUriPrefix(uriPrefix)
            .build()
    }

    class OperationParserImpl() : Metadata.OperationParser {
        override fun uri(element: AnnotatedElement?): String? {
            if (element == null) return null
            for (annotation in element.annotations) {
                if (annotation is Mapping)
                    return annotation.value
            }
            return null
        }

        override fun http(method: Method?): Array<out Operation.HttpMethod?>? {
            // MethodType 和 Operation.HttpMethod 的枚举值名称相同
            return method?.annotations
                ?.filterIsInstance<Mapping>()
                ?.firstOrNull()
                ?.method
                ?.map { Operation.HttpMethod.valueOf(it.name) }
                ?.toTypedArray()
        }

        override fun isStream(method: Method?): Boolean {
            return false
//            if (method == null) return false
//            return method.returnType in StreamingResponseBody::class
        }

    }

    class ParameterParserImpl() : Metadata.ParameterParser {
        override fun requestHeader(javaParameter: Parameter?): String? {
            javaParameter?.annotations?.forEach { annotation ->
                if (annotation is Header) return annotation.value
            }
            return null
        }

        override fun requestParam(javaParameter: Parameter?): String? {
            javaParameter?.annotations?.forEach { annotation ->
                if (annotation is Param) return annotation.value
            }
            return null
        }

        override fun pathVariable(javaParameter: Parameter?): String? {
            javaParameter?.annotations?.forEach { annotation ->
                if (annotation is Path) return annotation.value
            }
            return null
        }

        override fun requestPart(javaParameter: Parameter?): String? {
            javaParameter?.annotations?.forEach { annotation ->
                if (annotation is Param && javaParameter.type == UploadedFile::class.java) return annotation.value
            }
            return null
        }

        override fun defaultValue(javaParameter: Parameter?): String? {
            javaParameter?.annotations?.forEach { annotation ->
                if (annotation is Param) return annotation.defaultValue
            }
            return null
        }

        override fun isOptional(javaParameter: Parameter?): Boolean? {
            javaParameter?.annotations?.forEach { annotation ->
                if (annotation is Header) return !annotation.required
                if (annotation is Param) return !annotation.required
                if (annotation is Multipart) return null
            }
            return null
        }

        override fun isRequestBody(javaParameter: Parameter?): Boolean {
            if (javaParameter == null) return false
            return javaParameter.annotations.any { a -> a is Body }
        }

        override fun isRequestPartRequired(javaParameter: Parameter?): Boolean {
            if (javaParameter == null) return false
            return javaParameter.type == UploadedFile::class.java
        }
    }
}