package com.coooolfan.xiaomialbumsyncer.xiaomicloud.e2ee

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path

class GalleryBlockDownloaderTest {
    @TempDir lateinit var directory: Path
    private val mapper = jacksonObjectMapper()
    private val fixture = mapper.readTree(javaClass.getResourceAsStream("/e2ee/webcrypto.json"))

    private val rc4 = mapper.readTree(javaClass.getResourceAsStream("/e2ee/rc4.json"))

    @Test fun decryptsIndependentWebCryptoBlocksAndRetriesWithoutDuplicatingOutput() = withServer { server, data ->
        var calls = 0
        server.removeContext("/0")
        server.createContext("/0") { exchange ->
            assertNull(exchange.requestHeaders.getFirst("Cookie"))
            val bytes = E2eeCrypto.decode(fixture.at("/blocks/0/cipher").asText()).also { if (calls++ == 0) it[0] = (it[0].toInt() xor 1).toByte() }
            exchange.sendResponseHeaders(200, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
        }
        val target = directory.resolve("photo.tmp")
        GalleryBlockDownloader().download(data, target, 81, fixture.path("sha1").asText()) { version ->
            assertEquals(5645147155988960L, version)
            E2eeCrypto.decode(fixture.path("appKey").asText())
        }
        assertEquals(2, calls)
        assertArrayEquals(E2eeCrypto.decode(fixture.path("plaintext").asText()), Files.readAllBytes(target))
    }

    @Test fun wrongWholeFileHashDeletesTemporaryFile() = withServer { _, data ->
        val target = directory.resolve("corrupt.tmp")
        assertThrows(IllegalStateException::class.java) {
            GalleryBlockDownloader().download(data, target, 81, "0".repeat(40)) { E2eeCrypto.decode(fixture.path("appKey").asText()) }
        }
        assertFalse(Files.exists(target))
    }

    @Test fun supportsPlaintextBlocksWithoutRequestingKeys() = withServer { server, data ->
        data.remove("encryptInfo")
        fixture.path("blocks").forEachIndexed { i, block ->
            val bytes = E2eeCrypto.decode(block.path("plain").asText())
            server.removeContext("/$i")
            server.createContext("/$i") { exchange ->
                exchange.sendResponseHeaders(200, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
            }
            (data.at("/kss/blocks/$i") as ObjectNode).remove("encryptedSha1")
        }
        val target = directory.resolve("plain.tmp")
        GalleryBlockDownloader().download(data, target, 81, fixture.path("sha1").asText()) { error("明文不应取 key") }
        assertArrayEquals(E2eeCrypto.decode(fixture.path("plaintext").asText()), Files.readAllBytes(target))
    }

    @Test fun missingKeyDoesNotCreateOutput() = withServer { _, data ->
        val target = directory.resolve("missing.tmp")
        assertThrows(IllegalStateException::class.java) {
            GalleryBlockDownloader().download(data, target, 81, fixture.path("sha1").asText()) { error("需要验证") }
        }
        assertFalse(Files.exists(target))
    }

    @Test fun decryptsRc4BlocksWithoutE2eeOrRequestingAppKeys() = withServer { server, data ->
        serveRc4(server, data, e2ee = false)
        val target = directory.resolve("rc4.tmp")
        GalleryBlockDownloader().download(data, target, 81, fixture.path("sha1").asText()) { error("RC4 不应请求 appkey") }
        assertArrayEquals(E2eeCrypto.decode(fixture.path("plaintext").asText()), Files.readAllBytes(target))
    }

    @Test fun decryptsRc4BeforeE2eeAndResetsCiphersOnBlockRetry() = withServer { server, data ->
        serveRc4(server, data, e2ee = true)
        var calls = 0
        server.removeContext("/0")
        server.createContext("/0") { exchange ->
            val bytes = java.util.Base64.getDecoder().decode(rc4.at("/blocks/0/e2ee").asText())
            if (calls++ == 0) bytes[0] = (bytes[0].toInt() xor 1).toByte()
            exchange.sendResponseHeaders(200, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
        }
        val target = directory.resolve("rc4-e2ee.tmp")
        GalleryBlockDownloader().download(data, target, 81, fixture.path("sha1").asText()) { E2eeCrypto.decode(fixture.path("appKey").asText()) }
        assertEquals(2, calls)
        assertArrayEquals(E2eeCrypto.decode(fixture.path("plaintext").asText()), Files.readAllBytes(target))
    }

    @Test fun wrongRc4KeyFailsIntegrityChecksAndDeletesOutput() = withServer { server, data ->
        serveRc4(server, data, e2ee = false)
        (data.path("kss") as ObjectNode).put("secure_key", "ff".repeat(16))
        val target = directory.resolve("wrong-rc4.tmp")
        assertThrows(IllegalStateException::class.java) {
            GalleryBlockDownloader().download(data, target, 81, fixture.path("sha1").asText()) { error("不应取 appkey") }
        }
        assertFalse(Files.exists(target))
    }

    @Test fun rejectsMalformedRc4KeyBeforeCreatingOutput() = withServer { _, data ->
        val target = directory.resolve("invalid-rc4.tmp")
        for (invalid in listOf("xyz", "001")) {
            (data.path("kss") as ObjectNode).put("secure_key", invalid)
            assertThrows(IllegalStateException::class.java) {
                GalleryBlockDownloader().download(data, target, 81, fixture.path("sha1").asText()) { error("不应取 appkey") }
            }
            assertFalse(Files.exists(target))
        }
    }

    @Test fun keepsRc4StateAcrossReadBuffers() = withServer { server, data ->
        val plain = ByteArray(150003) { ((it * 37 + 11) % 256).toByte() }
        val key = java.util.HexFormat.of().parseHex(rc4.path("secureKey").asText())
        val cipher = javax.crypto.Cipher.getInstance("ARCFOUR").apply {
            init(javax.crypto.Cipher.ENCRYPT_MODE, javax.crypto.spec.SecretKeySpec(key, "ARCFOUR"))
        }.doFinal(plain)
        val hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-1").digest(plain))
        data.remove("encryptInfo")
        val kss = data.path("kss") as ObjectNode
        kss.put("secure_key", rc4.path("secureKey").asText())
        kss.putArray("blocks").addObject().apply {
            put("size", plain.size); put("sha1", hash)
            putArray("urls").add("http://127.0.0.1:${server.address.port}/large")
        }
        server.createContext("/large") { exchange ->
            exchange.sendResponseHeaders(200, cipher.size.toLong()); exchange.responseBody.use { it.write(cipher) }
        }
        val target = directory.resolve("large-rc4.tmp")
        GalleryBlockDownloader().download(data, target, plain.size.toLong(), hash) { error("不应取 appkey") }
        assertArrayEquals(plain, Files.readAllBytes(target))
    }

    private fun serveRc4(server: HttpServer, data: ObjectNode, e2ee: Boolean) {
        (data.path("kss") as ObjectNode).put("secure_key", rc4.path("secureKey").asText())
        if (!e2ee) data.remove("encryptInfo")
        rc4.path("blocks").forEachIndexed { i, block ->
            if (!e2ee) (data.at("/kss/blocks/$i") as ObjectNode).remove("encryptedSha1")
            val bytes = java.util.Base64.getDecoder().decode(block.path(if (e2ee) "e2ee" else "plain").asText())
            server.removeContext("/$i")
            server.createContext("/$i") { exchange ->
                assertNull(exchange.requestHeaders.getFirst("Cookie"))
                exchange.sendResponseHeaders(200, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
            }
        }
    }

    private fun withServer(action: (HttpServer, ObjectNode) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val data = mapper.createObjectNode()
        data.set<ObjectNode>("encryptInfo", fixture.path("encryptInfo").deepCopy())
        val blocks = data.putObject("kss").put("stat", "OK").putArray("blocks")
        fixture.path("blocks").forEachIndexed { i, block ->
            val bytes = E2eeCrypto.decode(block.path("cipher").asText())
            blocks.addObject().apply {
                put("size", bytes.size); put("sha1", block.path("sha1").asText()); put("encryptedSha1", block.path("encryptedSha1").asText())
                putArray("urls").add("http://127.0.0.1:${server.address.port}/$i")
            }
            server.createContext("/$i") { exchange ->
                exchange.sendResponseHeaders(200, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
            }
        }
        server.start()
        try { action(server, data) } finally { server.stop(0) }
    }
}
