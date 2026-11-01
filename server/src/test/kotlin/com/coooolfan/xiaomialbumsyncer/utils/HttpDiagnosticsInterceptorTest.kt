package com.coooolfan.xiaomialbumsyncer.utils

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.io.IOException

class HttpDiagnosticsInterceptorTest {
    @Test
    fun logsResponseMetadataWithoutReadingBodyOrExposingCredentials() = capture { events ->
        val http = OkHttpClient.Builder().addInterceptor(HttpDiagnosticsInterceptor()).addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(503).message("Unavailable")
                .header("Set-Cookie", "secret-cookie")
                .body("secret-response-body".toResponseBody()).build()
        }.build()
        val request = Request.Builder().url("https://example.test/secret-path?token=secret-query")
            .post("secret-request-body".toRequestBody())
            .header("Authorization", "Bearer secret-header").header("Cookie", "secret-request-cookie").build()
        http.newCall(request).execute().use {
            assertEquals(503, it.code)
            assertEquals("secret-response-body", it.body.string())
        }
        val lines = events.list.map { it.formattedMessage }
        assertEquals(2, lines.size)
        assertTrue(lines.first().contains("host=example.test"))
        assertTrue(lines.last().contains("status=503"))
        assertEquals(lines.first().substringAfter("requestId=").substringBefore(' '),
            lines.last().substringAfter("requestId=").substringBefore(' '))
        assertFalse(lines.joinToString().contains("secret-"))
        assertTrue(events.list.all { it.throwableProxy == null })
    }

    @Test
    fun preservesTransportFailureWithoutLoggingItsSensitiveMessage() = capture { events ->
        val failure = IOException("https://example.test/secret-download?signature=secret-signature")
        val http = OkHttpClient.Builder().addInterceptor(HttpDiagnosticsInterceptor())
            .addInterceptor(Interceptor { throw failure }).build()
        val thrown = assertThrows(IOException::class.java) {
            http.newCall(Request.Builder().url("https://example.test/").build()).execute()
        }
        assertSame(failure, thrown)
        val last = events.list.last()
        assertEquals(Level.WARN, last.level)
        assertTrue(last.formattedMessage.contains("errorType=IOException"))
        assertFalse(events.list.joinToString { it.formattedMessage }.contains("secret-"))
        assertNull(last.throwableProxy)
    }

    private fun capture(test: (ListAppender<ILoggingEvent>) -> Unit) {
        val logger = LoggerFactory.getLogger(HttpDiagnosticsInterceptor::class.java) as Logger
        val previous = logger.level
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.level = Level.INFO
        logger.addAppender(appender)
        try { test(appender) } finally {
            logger.detachAppender(appender)
            logger.level = previous
            appender.stop()
        }
    }
}
