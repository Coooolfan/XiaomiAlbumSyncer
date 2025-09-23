package com.coooolfan.xiaomialbumsyncer.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import com.coooolfan.xiaomialbumsyncer.model.SystemConfig
import com.coooolfan.xiaomialbumsyncer.model.by
import com.coooolfan.xiaomialbumsyncer.model.dto.SystemConfigInit
import com.coooolfan.xiaomialbumsyncer.model.dto.SystemConfigPassTokenUpdate
import com.coooolfan.xiaomialbumsyncer.model.dto.SystemConfigUpdate
import com.coooolfan.xiaomialbumsyncer.service.SystemConfigService
import org.babyfish.jimmer.client.FetchBy
import org.babyfish.jimmer.client.meta.Api
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.noear.solon.annotation.Body
import org.noear.solon.annotation.Controller
import org.noear.solon.annotation.Mapping
import org.noear.solon.core.handle.MethodType

@Api
@Controller
@Mapping("/api/system-config")
class SystemConfigController(private val service: SystemConfigService) {
    @Api
    @Mapping(method = [MethodType.GET])
    fun isInit(): IsInitResponse {
        return IsInitResponse(service.isInit())
    }

    @Api
    @Mapping("/hello", method = [MethodType.GET])
    fun hello(): String {
        return "Hello World!\n"
    }

    @Api
    @Mapping(method = [MethodType.POST])
    fun initConfig(@Body create: SystemConfigInit) {
        return service.initConfig(create)
    }

    @Api
    @Mapping("/pass-token", method = [MethodType.POST])
    @SaCheckLogin
    fun updatePassToken(@Body update: SystemConfigPassTokenUpdate) {
        return service.updateConfig(update.toEntity())
    }

    @Api
    @Mapping("/normal", method = [MethodType.POST])
    @SaCheckLogin
    fun updateSystemConfig(@Body update: SystemConfigUpdate) {
        return service.updateConfig(update.toEntity())
    }

    @Api
    @Mapping("/normal", method = [MethodType.GET])
    @SaCheckLogin
    fun getSystemConfig(): @FetchBy("NORMAL_SYSTEM_CONFIG") SystemConfig {
        return service.getConfig(NORMAL_SYSTEM_CONFIG)
    }

    companion object {
        private val NORMAL_SYSTEM_CONFIG = newFetcher(SystemConfig::class).by {
            exifToolPath()
        }
    }
}

data class IsInitResponse(
    val init: Boolean
)