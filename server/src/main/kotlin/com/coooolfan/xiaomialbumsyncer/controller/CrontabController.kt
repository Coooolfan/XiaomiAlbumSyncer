package com.coooolfan.xiaomialbumsyncer.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import com.coooolfan.xiaomialbumsyncer.model.Crontab
import com.coooolfan.xiaomialbumsyncer.model.by
import com.coooolfan.xiaomialbumsyncer.model.dto.CrontabInput
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

@Api
@Managed
@Mapping("/api/crontab")
@Controller
@SaCheckLogin
class CrontabController(private val servce: CrontabService) {
    @Api
    @Mapping("", method = [MethodType.GET])
    fun listCrontabs(): List<@FetchBy("DEFAULT_CRONTAB") Crontab> {
        return servce.queryCrontab(DEFAULT_CRONTAB)
    }

    @Api
    @Mapping("", method = [MethodType.POST])
    fun createCrontab(@Body input: CrontabInput): @FetchBy("DEFAULT_CRONTAB") Crontab {
        return servce.createCrontab(input, DEFAULT_CRONTAB)
    }

    @Api
    @Mapping("/{crontabId}", method = [MethodType.PUT])
    fun updateCrontab(@Body input: CrontabInput, @Path crontabId: Long): @FetchBy("DEFAULT_CRONTAB") Crontab {
        return servce.updateCrontab(input.toEntity { id = crontabId }, DEFAULT_CRONTAB)
    }

    @Api
    @Mapping("/{crontabId}", method = [MethodType.DELETE])
    fun deleteCrontab(@Path crontabId: Long) {
        servce.deleteCrontab(crontabId)
    }

    companion object {
        private val DEFAULT_CRONTAB = newFetcher(Crontab::class).by {
            allScalarFields()
            albumIds()
        }
    }
}