package com.coooolfan.xiaomialbumsyncer.xiaomicloud.e2ee

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Cipher

class XiaomiKeybagClientTest {
    @Test fun exchangesCaptchaAndTokenTicketsAndCompletesGalleryPagination(@TempDir directory: Path) {
        val mapper = jacksonObjectMapper()
        val hsm = E2eeCrypto.rsaKeyPair()
        val hsmPkcs1 = Base64.getEncoder().encodeToString(hsm.public.encoded.copyOfRange(24, 294))
        val key = E2eeCrypto.encode(ByteArray(32) { it.toByte() })
        val failures = AtomicReference<Throwable>()
        val calls = mutableListOf<String>()
        var nonce = 0
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/common/check") { exchange ->
            calls += "check"
            exchange.responseHeaders.add("Set-Cookie", "i.mi.com_istrudev=test-trust-flag; Path=/")
            val bytes = mapper.writeValueAsBytes(mapOf("code" to 0))
            exchange.sendResponseHeaders(200, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
        }
        server.createContext("/mic/keybag/v1/") { exchange ->
            try {
                val name = exchange.requestURI.path.substringAfterLast('/')
                calls += name
                assertTrue(exchange.requestHeaders.getFirst("Cookie").contains("serviceToken=test-service-token"))
                assertTrue(exchange.requestHeaders.getFirst("Cookie").contains("i.mi.com_istrudev=test-trust-flag"))
                val data: Any = when (name) {
                    "getEncInfo" -> mapOf("zone" to 301, "serverSignZone" to 401, "nonce" to "nonce-${++nonce}", "e2eeStatus" to "open")
                    "keybagCaptchaPush" -> {
                        assertFalse(exchange.requestHeaders.getFirst("Cookie").contains("trust_device_auth_token"))
                        emptyMap<String, Any>()
                    }
                    else -> {
                        val form = exchange.requestBody.readAllBytes().toString(Charsets.UTF_8).split('&').associate {
                            val parts = it.split('=', limit = 2); URLDecoder.decode(parts[0], Charsets.UTF_8) to URLDecoder.decode(parts[1], Charsets.UTF_8)
                        }
                        assertEquals("test-service-token", form["serviceToken"])
                        val outer = mapper.readTree(form["data"])
                        assertEquals("gallery", outer.path("nameSpace").asText())
                        assertEquals("nonce-$nonce", outer.path("nonce").asText())
                        val captcha = name == "getAppkeyTokenByCaptcha"
                        val ticket = outer.path(if (captcha) "clientTicket" else "clientTokenTicket").asText().split('~')
                        assertEquals("301", ticket[0])
                        val aes = E2eeCrypto.rsa(Cipher.DECRYPT_MODE, hsm.private, E2eeCrypto.decode(ticket[1]))
                        val payload = mapper.readTree(E2eeCrypto.gcm(Cipher.DECRYPT_MODE, aes, E2eeCrypto.decode(ticket[2]), E2eeCrypto.decode(ticket[3]), E2eeCrypto.decode(ticket[4])))
                        assertEquals("123456", payload.path("userId").asText())
                        assertEquals(outer.path("tpubString"), payload.path("tpubString"))
                        assertEquals(outer.path("nonce"), payload.path("nonce"))
                        val signed = payload.path("clientSignField").joinToString("~") { payload.path(it.asText()).asText() }
                        assertEquals(E2eeCrypto.encode(MessageDigest.getInstance("SHA-256").digest(signed.toByteArray())), payload.path("clientSign").asText())
                        if (captcha) {
                            assertEquals("001234", payload.path("captchaCode").asText())
                            exchange.responseHeaders.add("Set-Cookie", "trust_device_auth_token=test-trust; Path=/")
                        } else {
                            assertEquals("test-encrypt-token", payload.path("encryptToken").asText())
                            assertEquals(9L, outer.path("lastAppKeyVersion").asLong())
                            assertTrue(exchange.requestHeaders.getFirst("Cookie").contains("trust_device_auth_token=test-trust"))
                        }
                        val publicKey = E2eeCrypto.importHsmKey(outer.path("tpubString").asText().lineSequence().filter { !it.startsWith("---") }.joinToString(""))
                        val responseKey = ByteArray(32) { 7 }; val iv = ByteArray(12) { 3 }; val aad = byteArrayOf(1, 2, 3)
                        val responsePayload = if (captcha) mapOf("encryptToken" to "test-encrypt-token", "appKey" to listOf(10L, 9L).map { mapOf("appKeyVersion" to it, "appKey" to key) })
                            else mapOf("appKey" to listOf(mapOf("appKeyVersion" to 1L, "appKey" to key)))
                        mapOf("tephemeralKey" to E2eeCrypto.encode(E2eeCrypto.rsa(Cipher.ENCRYPT_MODE, publicKey, responseKey)),
                            "ephemeralKeyIv" to E2eeCrypto.encode(iv), "ephemeralKeyAad" to E2eeCrypto.encode(aad), "totalAppKeyCnt" to 3,
                            (if (captcha) "tokenAppkeyTicket" else "appkeyTicket") to E2eeCrypto.encode(E2eeCrypto.gcm(Cipher.ENCRYPT_MODE, responseKey, iv, mapper.writeValueAsBytes(responsePayload), aad)))
                    }
                }
                val bytes = mapper.writeValueAsBytes(mapOf("code" to 0, "data" to data))
                exchange.sendResponseHeaders(200, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
            } catch (e: Throwable) {
                failures.set(e); exchange.sendResponseHeaders(500, -1); exchange.close()
            }
        }
        server.start()
        try {
            E2eeTestDatabase(directory.resolve("keybag.db"), "http://127.0.0.1:${server.address.port}").use { db ->
                db.cacheToken(1, "123456")
                val client = XiaomiKeybagClient(1, "123456", db.tokens, db.properties, hsmKeys = mapOf(301 to hsmPkcs1))
                client.push()
                val result = try { client.galleryKeys("001234") } catch (e: Exception) { failures.get()?.let { throw it }; throw e }
                assertEquals(setOf(10L, 9L, 1L), result.keys)
                assertEquals(setOf(key), result.values.toSet())
                assertTrue(client.trustCookie!!.contains("test-trust"))
                assertEquals(listOf("check", "keybagCaptchaPush", "getEncInfo", "getAppkeyTokenByCaptcha", "getEncInfo", "getAppkeyByToken"), calls)
                assertNull(failures.get())
            }
        } finally { server.stop(0) }
    }
}
