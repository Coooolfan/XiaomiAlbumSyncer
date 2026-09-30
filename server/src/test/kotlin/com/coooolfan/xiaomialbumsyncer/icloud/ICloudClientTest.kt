package com.coooolfan.xiaomialbumsyncer.icloud

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.util.Base64

class ICloudClientTest {
    @Test fun verifiesChallengeAndRestoresSessionWithoutPasswordLogin() {
        val mapper = jacksonObjectMapper()
        val requests = mutableListOf<String>()
        var trusted = false
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            requests += "${exchange.requestMethod} $path"
            val body = exchange.requestBody.readAllBytes().toString(Charsets.UTF_8)
            var status = 200
            val response = when (path) {
                "/auth/signin/init" -> {
                    exchange.responseHeaders.add("scnt", "challenge-scnt")
                    exchange.responseHeaders.add("X-Apple-ID-Session-Id", "session-id")
                    mapOf("salt" to "AQIDBA==", "b" to "Ag==", "c" to "challenge", "iteration" to 1000, "protocol" to "s2k")
                }
                "/auth/signin/complete" -> {
                    assertEquals("challenge-scnt", exchange.requestHeaders.getFirst("scnt"))
                    val json = mapper.readTree(body)
                    assertFalse(json.has("password"))
                    assertEquals(32, Base64.getDecoder().decode(json.path("m1").asText()).size)
                    exchange.responseHeaders.add("X-Apple-Session-Token", "web-token")
                    exchange.responseHeaders.add("Set-Cookie", "auth=secret-cookie; Path=/; HttpOnly")
                    status = if (trusted) 200 else 409
                    mapOf("authType" to "hsa2")
                }
                "/auth/verify/trusteddevice/securitycode" -> {
                    if (exchange.requestMethod == "POST") {
                        assertEquals("123456", mapper.readTree(body).at("/securityCode/code").asText())
                        trusted = true
                    }
                    emptyMap<String, String>()
                }
                "/auth/2sv/trust" -> {
                    exchange.responseHeaders.add("X-Apple-TwoSV-Trust-Token", "trust-token")
                    emptyMap<String, String>()
                }
                "/setup/accountLogin", "/setup/validate" -> {
                    if (path == "/setup/validate") assertTrue(exchange.requestHeaders.getFirst("Cookie").contains("auth=secret-cookie"))
                    mapOf("dsInfo" to mapOf("hsaVersion" to 2, "dsid" to "123"), "hsaChallengeRequired" to !trusted,
                        "hsaTrustedBrowser" to trusted, "webservices" to mapOf("ckdatabasews" to mapOf("url" to "http://127.0.0.1:${server.address.port}")))
                }
                else -> mapOf("records" to emptyList<String>())
            }
            val bytes = mapper.writeValueAsBytes(response)
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val root = "http://127.0.0.1:${server.address.port}"
            val endpoints = ICloudClient.Endpoints("$root/auth", "$root/setup", root)
            val credentials = ICloudCredentials("test@example.com", "password", "com")
            val client = ICloudClient(credentials, endpoints)
            assertFalse(client.login())
            assertTrue(client.mfaRequired)
            client.verify("123456")
            assertFalse(client.mfaRequired)
            assertEquals("trust-token", credentials.headers["X-Apple-TwoSV-Trust-Token"])
            val restored = ICloudClient(mapper.readValue<ICloudCredentials>(mapper.writeValueAsString(credentials)), endpoints)
            restored.ensureAuthenticated()
            assertEquals(1, requests.count { it.endsWith("/signin/init") })
            assertTrue(requests.contains("POST /setup/validate"))
            assertTrue(restored.login())
            assertEquals(1, requests.count { it == "PUT /auth/verify/trusteddevice/securitycode" })
        } finally {
            server.stop(0)
        }
    }

    @Test fun supportsBothRegionsAndRejectsUnknownDomains() {
        assertEquals("https://www.icloud.com.cn", ICloudClient.Endpoints.forDomain("cn").home)
        assertEquals("https://www.icloud.com", ICloudClient.Endpoints.forDomain("com").home)
        assertThrows(IllegalArgumentException::class.java) { ICloudClient.Endpoints.forDomain("invalid") }
    }
}
