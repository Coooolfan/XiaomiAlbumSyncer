package com.coooolfan.xiaomialbumsyncer

import org.noear.solon.annotation.Controller
import org.noear.solon.annotation.Mapping
import org.noear.solon.annotation.Param
import org.noear.solon.core.handle.ModelAndView

@Controller
@Mapping("/api")
class DemoController {
    @Mapping("/hello")
    fun hello(@Param(defaultValue = "world") name: String?): String {
        return String.format("Hello %s!", name)
    }

    @Mapping("/hello2")
    fun hello2(@Param(defaultValue = "world") name: String?): ModelAndView {
        return ModelAndView("hello2.ftl").put("name", name)
    }
}