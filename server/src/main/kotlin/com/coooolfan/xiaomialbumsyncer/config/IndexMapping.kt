package com.coooolfan.xiaomialbumsyncer.config

import org.noear.solon.annotation.Controller
import org.noear.solon.annotation.Mapping
import org.noear.solon.core.handle.Context
import org.noear.solon.core.handle.MethodType


@Controller
class IndexMapping {

    @Mapping("/", method = [MethodType.GET])
    fun home(ctx: Context) {
        //内部跳转到 /index.htm
        ctx.forward("/index.html")
    }
}