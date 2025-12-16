package com.coooolfan.xiaomialbumsyncer.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import com.coooolfan.xiaomialbumsyncer.model.SystemConfig
import com.coooolfan.xiaomialbumsyncer.model.by
import com.coooolfan.xiaomialbumsyncer.model.dto.SystemConfigInit
import com.coooolfan.xiaomialbumsyncer.model.dto.SystemConfigPassTokenUpdate
import com.coooolfan.xiaomialbumsyncer.model.dto.SystemConfigPasswordUpdate
import com.coooolfan.xiaomialbumsyncer.model.dto.SystemConfigUpdate
import com.coooolfan.xiaomialbumsyncer.service.DebugService
import com.coooolfan.xiaomialbumsyncer.service.SystemConfigService
import org.babyfish.jimmer.client.FetchBy
import org.babyfish.jimmer.client.meta.Api
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.noear.solon.annotation.Body
import org.noear.solon.annotation.Controller
import org.noear.solon.annotation.Mapping
import org.noear.solon.core.handle.MethodType
import org.noear.solon.core.runtime.NativeDetector.inNativeImage
import org.noear.solon.core.runtime.NativeDetector.isAotRuntime


@Api
@Controller
@Mapping("/api/system-config")
class SystemConfigController(private val service: SystemConfigService, private val debugService: DebugService) {

    /**
     * 检查系统是否已完成初始化
     *
     * 此接口用于检查系统配置是否已经完成初始化设置
     * 无需登录认证即可访问（公开接口）
     *
     * @return IsInitResponse 返回初始化状态，包含布尔值表示是否已初始化
     *
     * @api GET /api/system-config
     * @permission 公开接口，无需认证
     * @description 调用SystemConfigService.isInit()方法检查系统初始化状态
     */
    @Api
    @Mapping(method = [MethodType.GET])
    fun isInit(): IsInitResponse {
        return IsInitResponse(service.isInit())
    }

    /**
     * 初始化系统配置
     *
     * 此接口用于首次设置系统的基本配置信息
     * 无需登录认证即可访问（公开接口）
     *
     * @param create 系统配置初始化参数，包含初始配置信息
     *
     * @api POST /api/system-config
     * @permission 公开接口，无需认证
     * @description 调用SystemConfigService.initConfig()方法初始化系统配置
     */
    @Api
    @Mapping(method = [MethodType.POST])
    fun initConfig(@Body create: SystemConfigInit) {
        return service.initConfig(create)
    }

    /**
     * 更新密码令牌配置
     *
     * 此接口用于更新系统的密码令牌相关配置
     * 需要用户登录认证才能访问
     *
     * @param update 密码令牌更新参数，包含新的密码令牌配置信息
     *
     * @api POST /api/system-config/pass-token
     * @permission 需要登录认证
     * @description 调用SystemConfigService.updateConfig()方法更新密码令牌配置
     */
    @Api
    @Mapping("/pass-token", method = [MethodType.POST])
    @SaCheckLogin
    fun updatePassToken(@Body update: SystemConfigPassTokenUpdate) {
        return service.updateConfig(update.toEntity())
    }

    /**
     * 更新普通系统配置
     *
     * 此接口用于更新系统的普通配置信息（如exif工具路径等）
     * 需要用户登录认证才能访问
     *
     * @param update 系统配置更新参数，包含新的配置信息
     *
     * @api POST /api/system-config/normal
     * @permission 需要登录认证
     * @description 调用SystemConfigService.updateConfig()方法更新普通系统配置
     */
    @Api
    @Mapping("/normal", method = [MethodType.POST])
    @SaCheckLogin
    fun updateSystemConfig(@Body update: SystemConfigUpdate) {
        return service.updateConfig(update.toEntity())
    }

    /**
     * 获取普通系统配置
     *
     * 此接口用于获取系统的普通配置信息（如exif工具路径等）
     * 需要用户登录认证才能访问
     *
     * @return SystemConfig 返回系统的普通配置信息
     *
     * @api GET /api/system-config/normal
     * @permission 需要登录认证
     * @description 调用SystemConfigService.getConfig()方法获取普通系统配置
     */
    @Api
    @Mapping("/normal", method = [MethodType.GET])
    @SaCheckLogin
    fun getSystemConfig(): @FetchBy("NORMAL_SYSTEM_CONFIG") SystemConfig {
        return service.getConfig(NORMAL_SYSTEM_CONFIG)
    }


    /**
     * 更新用户密码
     *
     * 此接口用于更新系统的用户登录密码
     * 需要用户登录认证才能访问
     *
     * @param update 密码更新参数，包含新的密码信息
     *
     * @api POST /api/system-config/password
     * @permission 需要登录认证
     * @description 调用SystemConfigService.updatePassword()方法更新用户密码
     */
    @Api
    @Mapping("/password", method = [MethodType.POST])
    @SaCheckLogin
    fun updatePassword(
        @Body update: SystemConfigPasswordUpdate
    ) {
        return service.updatePassword(update)
    }


    @Api
    @Mapping("/info", method = [MethodType.GET])
    @SaCheckLogin
    fun getSystemInfo(): SystemInfoResponse {
        val jvmVersion = System.getProperty("java.version")
        val isAotRuntime = isAotRuntime()
        val isNativeImage = inNativeImage()
        return SystemInfoResponse(
            aotRuntime = isAotRuntime,
            nativeImage = isNativeImage,
            jvmVersion = jvmVersion
        )
    }

    @Api
    @Mapping("/info/debug", method = [MethodType.GET])
    @SaCheckLogin
    fun getSystemDebugInfo(): String {
        return debugService.getDebugInfo()
    }

    /**
     * 从旧版本数据库导入数据
     *
     * 此接口用于从旧版本的数据库中导入数据到当前系统
     * 需要用户登录认证才能访问
     *
     * @api POST /api/system-config/import-from-v2
     * @permission 需要登录认证
     * @description 调用SystemConfigService.importFromV2Db()方法执行数据导入操作
     */
    @Api
    @Mapping("/import-from-v2", method = [MethodType.POST])
    @SaCheckLogin
    fun importFromV2Db() {
        return service.importFromV2Db()
    }

    companion object {
        val NORMAL_SYSTEM_CONFIG = newFetcher(SystemConfig::class).by {
            exifToolPath()
        }
    }
}

data class IsInitResponse(
    val init: Boolean
)

data class SystemInfoResponse(
    val aotRuntime: Boolean,
    val nativeImage: Boolean,
    val jvmVersion: String?,
    val appVersion: String = loadAppVersion()
)

private fun loadAppVersion(): String {
    return try {
        val props = java.util.Properties()
        SystemInfoResponse::class.java.classLoader
            .getResourceAsStream("version.properties")
            ?.use { props.load(it) }
        props.getProperty("app.version", "dev")
    } catch (_: Exception) {
        "dev"
    }
}