package com.coooolfan.xiaomialbumsyncer.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import com.coooolfan.xiaomialbumsyncer.model.ArchiveDetail
import com.coooolfan.xiaomialbumsyncer.model.ArchiveRecord
import com.coooolfan.xiaomialbumsyncer.model.by
import com.coooolfan.xiaomialbumsyncer.service.ArchiveService
import kotlinx.coroutines.runBlocking
import org.babyfish.jimmer.client.FetchBy
import org.babyfish.jimmer.client.meta.Api
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.noear.solon.annotation.*
import org.noear.solon.core.handle.MethodType

/**
 * 归档控制器
 * 提供智能归档功能 API
 */
@Api
@Managed
@Mapping("/api/archive")
@Controller
@SaCheckLogin
class ArchiveController(private val archiveService: ArchiveService) {

    /**
     * 预览归档计划
     *
     * @param crontabId 定时任务 ID
     * @return 归档计划
     *
     * @api POST /api/archive/preview/{crontabId}
     * @permission 需要登录认证
     */
    @Api
    @Mapping("/preview/{crontabId}", method = [MethodType.POST])
    fun previewArchive(@Path crontabId: Long): ArchiveService.ArchivePlan {
        return archiveService.previewArchive(crontabId)
    }

    /**
     * 执行归档任务
     *
     * @param crontabId 定时任务 ID
     * @param request 归档执行请求
     * @return 归档记录 ID
     *
     * @api POST /api/archive/execute/{crontabId}
     * @permission 需要登录认证
     */
    @Api
    @Mapping("/execute/{crontabId}", method = [MethodType.POST])
    fun executeArchive(
        @Path crontabId: Long,
        @Body request: ExecuteArchiveRequest
    ): ExecuteArchiveResponse = runBlocking {
        val archiveRecordId = archiveService.executeArchive(crontabId, request.confirmed)
        ExecuteArchiveResponse(archiveRecordId)
    }

    /**
     * 获取归档记录列表
     *
     * @param crontabId 定时任务 ID
     * @return 归档记录列表
     *
     * @api GET /api/archive/records/{crontabId}
     * @permission 需要登录认证
     */
    @Api
    @Mapping("/records/{crontabId}", method = [MethodType.GET])
    fun getArchiveRecords(@Path crontabId: Long): List<@FetchBy("ARCHIVE_RECORD_LIST") ArchiveRecord> {
        // TODO: 实现分页查询
        return emptyList()
    }

    /**
     * 获取归档详情
     *
     * @param recordId 归档记录 ID
     * @return 归档详情列表
     *
     * @api GET /api/archive/details/{recordId}
     * @permission 需要登录认证
     */
    @Api
    @Mapping("/details/{recordId}", method = [MethodType.GET])
    fun getArchiveDetails(@Path recordId: Long): List<@FetchBy("ARCHIVE_DETAIL_LIST") ArchiveDetail> {
        // TODO: 实现查询逻辑
        return emptyList()
    }

    companion object {
        private val ARCHIVE_RECORD_LIST = newFetcher(ArchiveRecord::class).by {
            allScalarFields()
            crontabId()
        }

        private val ARCHIVE_DETAIL_LIST = newFetcher(ArchiveDetail::class).by {
            allScalarFields()
            archiveRecordId()
            assetId()
        }
    }
}

/**
 * 执行归档请求
 */
data class ExecuteArchiveRequest(
    val confirmed: Boolean
)

/**
 * 执行归档响应
 */
data class ExecuteArchiveResponse(
    val archiveRecordId: Long
)
