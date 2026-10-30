package com.coooolfan.xiaomialbumsyncer.xiaomicloud.e2ee

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import javax.crypto.AEADBadTagException

class E2eeCryptoTest {
    private val fixture = jacksonObjectMapper().readTree(javaClass.getResourceAsStream("/e2ee/webcrypto.json"))

    @Test fun unwrapsWebCryptoRecordKeyAndAdvancesCounterWithCarry() {
        val info = fixture.path("encryptInfo")
        val key = E2eeCrypto.recordKey(info, E2eeCrypto.decode(fixture.path("appKey").asText()))
        assertArrayEquals(E2eeCrypto.decode(fixture.path("recordKey").asText()), key)
        fixture.path("blocks").forEachIndexed { index, block ->
            val iv = E2eeCrypto.blockIv(E2eeCrypto.decode(info.path("recordIV").asText()), index)
            assertArrayEquals(E2eeCrypto.decode(block.path("iv").asText()), iv)
            assertArrayEquals(E2eeCrypto.decode(block.path("plain").asText()), E2eeCrypto.ctr(key, iv).doFinal(E2eeCrypto.decode(block.path("cipher").asText())))
        }
        assertArrayEquals(ByteArray(16).apply { this[13] = 4 }, E2eeCrypto.blockIv(ByteArray(16), 1))
        assertArrayEquals(ByteArray(16).apply { this[13] = 3; this[14] = -1; this[15] = -1 }, E2eeCrypto.blockIv(ByteArray(16) { -1 }, 1))
    }

    @Test fun rejectsTamperedRecordKeyAndCrc() {
        val info = fixture.path("encryptInfo").deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>()
        val app = E2eeCrypto.decode(fixture.path("appKey").asText())
        val wrapped = E2eeCrypto.decode(info.path("encryptedRecordKey").asText())
        wrapped[0] = (wrapped[0].toInt() xor 1).toByte()
        info.put("encryptedRecordKey", E2eeCrypto.encode(wrapped))
        assertThrows(AEADBadTagException::class.java) { E2eeCrypto.recordKey(info, app) }
        wrapped[0] = (wrapped[0].toInt() xor 1).toByte()
        wrapped[51] = (wrapped[51].toInt() xor 1).toByte()
        info.put("encryptedRecordKey", E2eeCrypto.encode(wrapped))
        assertThrows(IllegalStateException::class.java) { E2eeCrypto.recordKey(info, app) }
    }

}
