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
import org.noear.solon.core.handle.Result

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
     * @return Result<Int> 返回执行结果，状态码201表示执行成功
     * 
     * @api POST /api/crontab/{crontabId}/executions
     * @permission 需要登录认证
     * @description 调用CrontabService.executeCrontab()方法立即执行指定定时任务
     */
    @Api
    @Mapping("/{crontabId}/executions", method = [MethodType.POST])
    fun executeCrontab(@Path crontabId: Long){
        service.executeCrontab(crontabId)
    }

    companion object {
        private val DEFAULT_CRONTAB = newFetcher(Crontab::class).by {
            allScalarFields()
            albumIds()
            histories {
                allScalarFields()
                isCompleted()
            }
        }
    }
}