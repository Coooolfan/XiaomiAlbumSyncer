package com.coooolfan.xiaomialbumsyncer.utils

import okhttp3.Interceptor
import okhttp3.Response
import org.slf4j.LoggerFactory
import java.util.UUID

/** HTTP 传输摘要；URL 路径、查询串、认证头和正文可能包含下载签名或凭据，不写入日志。 */
internal class HttpDiagnosticsInterceptor : Interceptor {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val requestId = UUID.randomUUID().toString()
        val started = System.nanoTime()
        log.info("HTTP 开始 requestId={} method={} host={}", requestId, request.method, request.url.host)
        try {
            val response = chain.proceed(request)
            log.info("HTTP 响应 requestId={} status={} contentLength={} headersElapsedMs={}",
                requestId, response.code, response.body.contentLength(), (System.nanoTime() - started) / 1_000_000)
            return response
        } catch (e: Exception) {
            log.warn("HTTP 失败 requestId={} errorType={} elapsedMs={}",
                requestId, e.javaClass.simpleName, (System.nanoTime() - started) / 1_000_000)
            throw e
        }
    }
}
