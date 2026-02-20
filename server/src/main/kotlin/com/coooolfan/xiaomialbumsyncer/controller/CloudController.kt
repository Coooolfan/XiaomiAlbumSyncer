package com.coooolfan.xiaomialbumsyncer.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import com.coooolfan.xiaomialbumsyncer.xiaomicloud.CloudSpaceInfo
import com.coooolfan.xiaomialbumsyncer.xiaomicloud.XiaoMiApi
import org.babyfish.jimmer.client.meta.Api
import org.noear.solon.annotation.Controller
import org.noear.solon.annotation.Managed
import org.noear.solon.annotation.Mapping
import org.noear.solon.annotation.Path
import org.noear.solon.core.handle.MethodType

/**
 * 云端控制器
 * 提供云端相关功能 API
 */
@Api
@Managed
@Mapping("/api/cloud")
@Controller
@SaCheckLogin
class CloudController(private val xiaoMiApi: XiaoMiApi) {

    /**
     * 获取云端空间使用情况
     *
     * @param accountId 账号 ID
     * @return 云端空间信息
     *
     * @api GET /api/cloud/space/{accountId}
     * @permission 需要登录认证
     */
    @Api
    @Mapping("/space/{accountId}", method = [MethodType.GET])
    fun getCloudSpace(@Path accountId: Long): CloudSpaceInfo {
        return xiaoMiApi.getCloudSpace(accountId)
    }
}
