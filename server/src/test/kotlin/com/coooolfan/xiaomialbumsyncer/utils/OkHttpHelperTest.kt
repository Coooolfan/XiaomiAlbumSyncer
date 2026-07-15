package com.coooolfan.xiaomialbumsyncer.utils

import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OkHttpHelperTest {
    @Test
    fun `readTree parses JSON directly from response body`() {
        val body =
            """{"data":{"name":"Xiaomi","count":2}}"""
                .toResponseBody("application/json; charset=utf-8".toMediaType())

        val tree = ObjectMapper().readTree(body)

        assertEquals("Xiaomi", tree.at("/data/name").asText())
        assertEquals(2, tree.at("/data/count").asInt())
    }

    @Test
    fun `readJsonpTree skips callback wrapper`() {
        val body =
            "callback({\"url\":\"https://example.com/file\",\"meta\":\"value\"})"
                .toResponseBody("application/javascript; charset=utf-8".toMediaType())

        val tree = ObjectMapper().readJsonpTree(body)

        assertEquals("https://example.com/file", tree.get("url").asText())
        assertEquals("value", tree.get("meta").asText())
    }
}
