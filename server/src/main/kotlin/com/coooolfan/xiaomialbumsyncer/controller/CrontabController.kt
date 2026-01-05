package com.coooolfan.xiaomialbumsyncer.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import com.coooolfan.xiaomialbumsyncer.model.Crontab
import com.coooolfan.xiaomialbumsyncer.model.by
import com.coooolfan.xiaomialbumsyncer.model.dto.CrontabCreateInput
import com.coooolfan.xiaomialbumsyncer.model.dto.CrontabUpdateInput
import com.coooolfan.xiaomialbumsyncer.service.CrontabService
import org.babyfish.jimmer.client.FetchBy
import org.babyfish.jimmer.client.meta.Api
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.noear.solon.annotation.Body
import org.noear.solon.annotation.Controller
import org.noear.solon.annotation.Managed
import org.noear.solon.annotation.Mapping
import org.noear.solon.annotation.Path
import org.noear.solon.core.handle.MethodType
import java.time.Instant

/**
 * 定时任务管理控制器
 *
 * 提供定时任务相关的API接口，包括定时任务的创建、更新、删除、执行等功能
 * 所有接口均需要用户登录认证（通过类级别注解 @SaCheckLogin 控制）
 *
 * @property service 定时任务服务，用于处理定时任务相关的业务逻辑
 */
@Api
@Managed
@Mapping("/api/crontab")
@Controller
@SaCheckLogin
class CrontabController(private val service: CrontabService) {
    /**
     * 获取所有定时任务列表
     *
     * 此接口用于获取系统中配置的所有定时任务信息
     * 需要用户登录认证才能访问（类级别注解）
     *
     * @return List<Crontab> 返回所有定时任务的列表，包含任务的基本信息和执行历史
     *
     * @api GET /api/crontab
     * @permission 需要登录认证
     * @description 调用CrontabService.queryCrontab()方法获取所有定时任务数据
     */
    @Api
    @Mapping("", method = [MethodType.GET])
    fun listCrontabs(): List<@FetchBy("DEFAULT_CRONTAB") Crontab> {
        return service.queryCrontab(DEFAULT_CRONTAB)
    }

    /**
     * 创建新的定时任务
     *
     * 此接口用于在系统中创建新的定时任务配置
     * 需要用户登录认证才能访问（类级别注解）
     *
     * @param input 定时任务输入参数，包含任务的配置信息
     * @return Crontab 返回创建成功的定时任务对象
     *
     * @api POST /api/crontab
     * @permission 需要登录认证
     * @description 调用CrontabService.createCrontab()方法创建新的定时任务
     */
    @Api
    @Mapping("", method = [MethodType.POST])
    fun createCrontab(@Body input: CrontabCreateInput): @FetchBy("DEFAULT_CRONTAB") Crontab {
        return service.createCrontab(input, DEFAULT_CRONTAB)
    }

    /**
     * 更新指定定时任务
     *
     * 此接口用于更新系统中已存在的定时任务配置
     * 需要用户登录认证才能访问（类级别注解）
     *
     * @param input 定时任务输入参数，包含要更新的配置信息
     * @param crontabId 定时任务ID，用于指定要更新的任务
     * @return Crontab 返回更新后的定时任务对象
     *
     * @api PUT /api/crontab/{crontabId}
     * @permission 需要登录认证
     * @description 调用CrontabService.updateCrontab()方法更新指定定时任务
     */
    @Api
    @Mapping("/{crontabId}", method = [MethodType.PUT])
    fun updateCrontab(@Body input: CrontabUpdateInput, @Path crontabId: Long): @FetchBy("DEFAULT_CRONTAB") Crontab {
        return service.updateCrontab(input.toEntity { id = crontabId }, DEFAULT_CRONTAB)
    }

    /**
     * 删除指定定时任务
     *
     * 此接口用于从系统中删除指定的定时任务配置
     * 需要用户登录认证才能访问（类级别注解）
     *
     * @param crontabId 定时任务ID，用于指定要删除的任务
     *
     * @api DELETE /api/crontab/{crontabId}
     * @permission 需要登录认证
     * @description 调用CrontabService.deleteCrontab()方法删除指定定时任务
     */
    @Api
    @Mapping("/{crontabId}", method = [MethodType.DELETE])
    fun deleteCrontab(@Path crontabId: Long) {
        service.deleteCrontab(crontabId)
    }

    /**
     * 立即执行指定定时任务
     *
     * 此接口用于立即执行指定的定时任务，不等待预定的执行时间
     * 需要用户登录认证才能访问（类级别注解）
     *
     * @param crontabId 定时任务ID，用于指定要立即执行的任务
     *
     * @api POST /api/crontab/{crontabId}/executions
     * @permission 需要登录认证
     * @description 调用CrontabService.executeCrontab()方法立即执行指定定时任务
     */
    @Api
    @Mapping("/{crontabId}/executions", method = [MethodType.POST])
    fun executeCrontab(@Path crontabId: Long) {
        service.executeCrontab(crontabId)
    }

    /**
     * 获取指定定时任务的当前统计信息
     *
     * 此接口用于获取指定定时任务的当前执行统计信息，包括资源数量、下载完成数等
     * 需要用户登录认证才能访问（类级别注解）
     *
     * @param crontabId 定时任务ID，用于指定要获取统计信息的任务
     * @return CrontabCurrentStats 返回定时任务的当前统计信息
     *
     * @api POST /api/crontab/{crontabId}/current
     * @permission 需要登录认证
     * @description 调用CrontabService.getCrontabCurrentStats()方法获取定时任务当前统计信息
     */
    @Api
    @Mapping("/{crontabId}/current", method = [MethodType.POST])
    fun getCrontabCurrentStats(@Path crontabId: Long): CrontabCurrentStats {
        return service.getCrontabCurrentStats(crontabId)
    }


    /**
     * 立即执行指定定时任务的EXIF填充操作
     *
     * 此接口用于立即执行指定定时任务中的EXIF填充操作
     * 需要用户登录认证才能访问（类级别注解）
     *
     * @param crontabId 定时任务ID，用于指定要执行EXIF填充操作的任务
     *
     * @api POST /api/crontab/{crontabId}/fill-exif/executions
     * @permission 需要登录认证
     * @description 调用CrontabService.executeCrontabFillExif()方法立即执行EXIF填充操作
     */
    @Api
    @Mapping("/{crontabId}/fill-exif/executions", method = [MethodType.POST])
    fun executeCrontabExifTime(@Path crontabId: Long) {
        service.executeCrontabExifTime(crontabId)
    }

    /**
     * 立即执行指定定时任务的文件系统时间重写操作
     *
     * 此接口用于立即执行指定定时任务中的文件系统时间重写操作
     * 需要用户登录认证才能访问（类级别注解）
     *
     * @param crontabId 定时任务ID，用于指定要执行文件系统时间重写操作的任务
     *
     * @api POST /api/crontab/{crontabId}/rewrite-fs-time/executions
     * @permission 需要登录认证
     * @description 调用CrontabService.executeCrontabRewriteFileSystemTime()方法立即执行文件系统时间重写操作
     */
    @Api
    @Mapping("/{crontabId}/rewrite-fs-time/executions", method = [MethodType.POST])
    fun executeCrontabRewriteFileSystemTime(@Path crontabId: Long) {
        service.executeCrontabRewriteFileSystemTime(crontabId)
    }

    companion object {
        private val DEFAULT_CRONTAB = newFetcher(Crontab::class).by {
            allScalarFields()
            accountId()
            account { nickname() }
            albumIds()
            running()
            histories {
                allScalarFields()
                isCompleted()
                timelineSnapshot(false)
            }
        }

        val CRONTAB_WITH_ALBUMS_FETCHER = newFetcher(Crontab::class).by {
            allScalarFields()
            accountId()
            albumIds()
            albums { allScalarFields() }
        }
    }
}

/**
 * 定时任务当前统计信息数据类
 *
 * @property ts 统计时间戳
 * @property assetCount 资源总数
 * @property downloadCompletedCount 下载完成数
 * @property sha1VerifiedCount SHA1验证完成数
 * @property exifFilledCount EXIF填充完成数
 * @property fsTimeUpdatedCount 文件系统时间更新完成数
 */
data class CrontabCurrentStats(
    val ts: Instant? = null,
    val assetCount: Long? = null,
    val downloadCompletedCount: Long? = null,
    val sha1VerifiedCount: Long? = null,
    val exifFilledCount: Long? = null,
    val fsTimeUpdatedCount: Long? = null
)