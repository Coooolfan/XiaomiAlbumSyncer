package com.coooolfan.xiaomialbumsyncer.config

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.noear.solon.annotation.Inject
import org.noear.solon.annotation.Managed

/**
 * 小米云 API 地址配置。
 *
 * 生产环境默认访问 i.mi.com，API E2E 测试通过环境变量将请求切换到本地 mock 服务。
 */
@Managed
class XiaomiApiProperties {

    @Inject(value = $$"${xiaomi.api.base-url}")
    private lateinit var configuredBaseUrl: String

    val baseUrl: HttpUrl by lazy {
        configuredBaseUrl.trimEnd('/').plus('/').toHttpUrl()
    }

    fun url(pathAndQuery: String): String {
        return baseUrl.resolve(pathAndQuery.trimStart('/'))?.toString()
            ?: throw IllegalArgumentException("无法基于 $baseUrl 解析小米 API 地址: $pathAndQuery")
    }
}
