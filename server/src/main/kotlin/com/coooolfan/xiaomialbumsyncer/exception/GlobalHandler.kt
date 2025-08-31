package com.coooolfan.xiaomialbumsyncer.exception

import cn.dev33.satoken.exception.NotLoginException
import org.noear.solon.annotation.Component
import org.noear.solon.core.handle.Context
import org.noear.solon.core.handle.Filter
import org.noear.solon.core.handle.FilterChain
import org.noear.solon.core.handle.Result

@Component(index = 0)
class GlobalHandler : Filter {
    @Throws(Throwable::class)
    override fun doFilter(ctx: Context, chain: FilterChain) {
        try {
            chain.doFilter(ctx)
        } catch (e: NotLoginException) {
            ctx.status(401)
            ctx.returnValue(
                Result.failure<Object>(401, e.message)
            )
        } catch (e: Exception) {
            e.printStackTrace()
            ctx.status(500)
            ctx.returnValue(
                Result.failure<Object>(500, e.message)
            )
        }
    }
}