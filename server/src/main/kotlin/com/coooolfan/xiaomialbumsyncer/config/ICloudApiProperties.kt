package com.coooolfan.xiaomialbumsyncer.config

import org.noear.solon.annotation.Inject
import org.noear.solon.annotation.Managed

/**
 * iCloud Web API 地址。
 *
 * 未配置时按账号区域访问 Apple。API E2E 通过 ICLOUD_API_BASE_URL 把登录、CloudKit 和下载指到本地 mocks。
 */
@Managed
class ICloudApiProperties {

    @Inject(value = $$"${icloud.api.base-url:}", required = false)
    private var configuredBaseUrl: String = ""

    fun baseUrl(): String {
        val fromEnv = System.getenv("ICLOUD_API_BASE_URL")?.trim().orEmpty().trimEnd('/')
        if (fromEnv.isNotEmpty()) return fromEnv
        return configuredBaseUrl.trim().trimEnd('/')
    }
}
