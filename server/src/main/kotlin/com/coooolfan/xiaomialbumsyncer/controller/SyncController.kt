package com.coooolfan.xiaomialbumsyncer.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import com.coooolfan.xiaomialbumsyncer.model.SyncRecord
import com.coooolfan.xiaomialbumsyncer.model.by
import com.coooolfan.xiaomialbumsyncer.service.SyncService
import org.babyfish.jimmer.client.FetchBy
import org.babyfish.jimmer.client.meta.Api
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.noear.solon.annotation.*
import org.noear.solon.core.handle.MethodType

/**
 * 同步控制器
 * 提供云端到本地的同步功能 API
 */
@Api
@Managed
@Mapping("/api/sync")
@Controller
@SaCheckLogin
class SyncController(private val syncService: SyncService) {

    /**
     * 获取同步记录列表
     *
     * @param crontabId 定时任务 ID
     * @return 同步记录列表
     *
     * @api GET /api/sync/records/{crontabId}
     * @permission 需要登录认证
     */
    @Api
    @Mapping("/records/{crontabId}", method = [MethodType.GET])
    fun getSyncRecords(@Path crontabId: Long): List<@FetchBy("SYNC_RECORD_LIST") SyncRecord> {
        // TODO: 实现分页查询
        return emptyList()
    }

    /**
     * 获取同步状态
     *
     * @param crontabId 定时任务 ID
     * @return 同步状态信息
     *
     * @api GET /api/sync/status/{crontabId}
     * @permission 需要登录认证
     */
    @Api
    @Mapping("/status/{crontabId}", method = [MethodType.GET])
    fun getSyncStatus(@Path crontabId: Long): SyncService.SyncStatusInfo {
        return syncService.getSyncStatus(crontabId)
    }

    /**
     * 检测云端变化
     *
     * @param crontabId 定时任务 ID
     * @return 变化摘要
     *
     * @api GET /api/sync/detect-changes/{crontabId}
     * @permission 需要登录认证
     */
    @Api
    @Mapping("/detect-changes/{crontabId}", method = [MethodType.GET])
    fun detectChanges(@Path crontabId: Long): SyncService.ChangeSummary {
        return syncService.detectChanges(crontabId)
    }

    companion object {
        private val SYNC_RECORD_LIST = newFetcher(SyncRecord::class).by {
            allScalarFields()
            crontabId()
        }
    }
}
