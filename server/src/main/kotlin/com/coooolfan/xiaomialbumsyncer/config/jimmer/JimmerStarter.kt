package com.coooolfan.xiaomialbumsyncer.config.jimmer

import org.babyfish.jimmer.client.meta.TypeName
import org.babyfish.jimmer.client.runtime.Metadata
import org.babyfish.jimmer.client.runtime.Operation
import org.babyfish.jimmer.client.runtime.VirtualType
import org.babyfish.jimmer.sql.dialect.SQLiteDialect
import org.babyfish.jimmer.sql.exception.DatabaseValidationException
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.newKSqlClient
import org.babyfish.jimmer.sql.runtime.*
import org.flywaydb.core.Flyway
import org.noear.solon.annotation.*
import org.noear.solon.core.handle.MethodType
import org.noear.solon.core.handle.UploadedFile
import org.slf4j.LoggerFactory
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Method
import java.lang.reflect.Parameter
import java.util.regex.Pattern
import javax.sql.DataSource


@Configuration
class JimmerStarter {

    private val log = LoggerFactory.getLogger(JimmerStarter::class.java)

    @Managed
    fun sql(dataSource: DataSource, flyway: Flyway): KSqlClient {
        // flyway 的注入仅用于确保在初始化 KSqlClient 之前执行 Flyway 迁移
        val kSqlClient = newKSqlClient {
            log.info("初始化 JimmerStarter kSqlClient 并校验表结构")
            setDialect(SQLiteDialect())
            setConnectionManager(ConnectionManager.simpleConnectionManager(dataSource))
            setDatabaseNamingStrategy(DefaultDatabaseNamingStrategy.LOWER_CASE)
            setExecutor(Executor.log())
            setSqlFormatter(SqlFormatter.PRETTY)
            setDatabaseValidationMode(
                DatabaseValidationMode.ERROR
            )
        }
        try {
            kSqlClient.validateDatabase()
        } catch (e: DatabaseValidationException) {
            log.info("数据库校验失败: " + e.message)
            log.info("1. 如果您正处于开发环境，请确保已执行最新的迁移脚本，并根据报错信息手动调整数据库或Flyway迁移文件。")
            log.info("2. 如果您正处于生产环境，请前往Github仓库提交issue寻求帮助。此报错不应该出现在生产环境。")
            throw RuntimeException("数据库校验失败", e)
        }
        return kSqlClient
    }


    @Managed
    fun genMetadata(): Metadata? {
        val COMMA_PATTERN = Pattern.compile("\\s*,\\s*")
        val groups = "myGroup"
        val uriPrefix = "api"

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
            .setGenericSupported(true)
            .setGroups(realGroups)
            .setUriPrefix(uriPrefix)
            .build()
    }

    class OperationParserImpl() : Metadata.OperationParser {
        override fun uri(element: AnnotatedElement?): String? {
            if (element == null) return null
            for (annotation in element.annotations) {
                if (annotation is Mapping)
                    return annotation.path
            }
            return null
        }

        override fun http(method: Method?): Array<out Operation.HttpMethod?>? {
            if (method == null) return null

            for (annotation in method.annotations)
                if (annotation is Mapping) {
                    val methods = emptyArray<Operation.HttpMethod?>()
                    for (type in annotation.method) {
                        if (type == MethodType.GET) methods.plus(Operation.HttpMethod.GET)
                        if (type == MethodType.POST) methods.plus(Operation.HttpMethod.POST)
                        if (type == MethodType.PUT) methods.plus(Operation.HttpMethod.PUT)
                        if (type == MethodType.DELETE) methods.plus(Operation.HttpMethod.DELETE)
                        if (type == MethodType.PATCH) methods.plus(Operation.HttpMethod.PATCH)
                        if (type == MethodType.OPTIONS) methods.plus(Operation.HttpMethod.OPTIONS)
                        if (type == MethodType.TRACE) methods.plus(Operation.HttpMethod.TRACE)
                        if (type == MethodType.HEAD) methods.plus(Operation.HttpMethod.HEAD)
                    }
                    return methods
                }

            return null
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

