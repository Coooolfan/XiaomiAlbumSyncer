package com.coooolfan.xiaomialbumsyncer.config

import cn.dev33.satoken.`fun`.strategy.SaCorsHandleFunction
import cn.dev33.satoken.router.SaHttpMethod
import cn.dev33.satoken.router.SaRouter
import cn.dev33.satoken.solon.integration.SaTokenInterceptor
import org.noear.solon.annotation.Bean
import org.noear.solon.annotation.Configuration

@Configuration
class SaToken {
    @Bean
    fun saTokenInterceptor(): SaTokenInterceptor {
        return SaTokenInterceptor()
            .addInclude("/**")
    }

    @Bean
    fun corsHandle(): SaCorsHandleFunction {
        return SaCorsHandleFunction { req, res, sto ->
            res.apply {
                // 允许指定域访问跨域资源
                setHeader("Access-Control-Allow-Origin", "*")
                // 允许所有请求方式
                setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS, DELETE")
                // 有效时间
                setHeader("Access-Control-Max-Age", "3600")
                // 允许的header参数
                setHeader("Access-Control-Allow-Headers", "*")
            }

            // 如果是预检请求，则立即返回到前端
            SaRouter.match(SaHttpMethod.OPTIONS)
                // Kotlin 的 lambda 表达式，println 对应 System.out.println
                .back()
        }
    }
}