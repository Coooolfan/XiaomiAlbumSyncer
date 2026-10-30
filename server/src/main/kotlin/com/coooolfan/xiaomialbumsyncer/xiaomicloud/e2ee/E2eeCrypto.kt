package com.coooolfan.xiaomialbumsyncer.xiaomicloud.e2ee

import com.fasterxml.jackson.databind.JsonNode
import java.math.BigInteger
import java.nio.charset.StandardCharsets.UTF_8
import java.security.*
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.zip.CRC32
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

internal object E2eeCrypto {
    private val random = SecureRandom()
    fun randomBytes(size: Int) = ByteArray(size).also(random::nextBytes)
    fun encode(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    fun decode(value: String): ByteArray = Base64.getDecoder().decode(value.replace('-', '+').replace('_', '/'))
    fun sha256(value: String): String = encode(MessageDigest.getInstance("SHA-256").digest(value.toByteArray(UTF_8)))

    fun rsaKeyPair(): KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048, random) }.generateKeyPair()

    fun publicPem(key: PublicKey): String {
        val spki = key.encoded
        require(spki.size == 294) { "临时 RSA 公钥格式不支持" }
        val body = Base64.getEncoder().encodeToString(spki.copyOfRange(24, spki.size)).chunked(64).joinToString("\n")
        return "-----BEGIN RSA PUBLIC KEY-----\n$body\n-----END RSA PUBLIC KEY-----\n"
    }

    fun importHsmKey(pkcs1: String): PublicKey = KeyFactory.getInstance("RSA")
        .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A$pkcs1")))

    fun rsa(mode: Int, key: Key, bytes: ByteArray): ByteArray = Cipher.getInstance("RSA/ECB/OAEPPadding").run {
        init(mode, key, OAEPParameterSpec("SHA-1", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT))
        doFinal(bytes)
    }

    fun gcm(mode: Int, key: ByteArray, iv: ByteArray, data: ByteArray, aad: ByteArray? = null): ByteArray =
        Cipher.getInstance("AES/GCM/NoPadding").run {
            require(key.size == 32) { "AES 密钥长度错误" }
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            if (aad != null) updateAAD(aad)
            doFinal(data)
        }

    fun ticket(zone: Int, publicKey: PublicKey, json: ByteArray): String {
        val key = randomBytes(32)
        val iv = randomBytes(12)
        val aad = randomBytes(16)
        return try {
            listOf(zone.toString(), encode(rsa(Cipher.ENCRYPT_MODE, publicKey, key)), encode(iv),
                encode(gcm(Cipher.ENCRYPT_MODE, key, iv, json, aad)), encode(aad)).joinToString("~")
        } finally { key.fill(0) }
    }

    fun unwrapResponse(data: JsonNode, field: String, privateKey: PrivateKey): ByteArray {
        val key = rsa(Cipher.DECRYPT_MODE, privateKey, decode(data.requiredText("tephemeralKey")))
        return try {
            gcm(Cipher.DECRYPT_MODE, key, decode(data.requiredText("ephemeralKeyIv")),
                decode(data.requiredText(field)), decode(data.requiredText("ephemeralKeyAad")))
        } finally { key.fill(0) }
    }

    fun recordKey(info: JsonNode, appKey: ByteArray): ByteArray {
        val iv = decode(info.requiredText("recordIV"))
        require(iv.size == 16) { "recordIV 长度错误" }
        val wrapped = decode(info.requiredText("encryptedRecordKey"))
        require(wrapped.size == 52) { "encryptedRecordKey 长度错误" }
        val key = gcm(Cipher.DECRYPT_MODE, appKey, iv, wrapped.copyOfRange(0, 48))
        require(key.size == 32) { "recordKey 长度错误" }
        val expected = BigInteger(1, wrapped.copyOfRange(48, 52)).toLong()
        check(CRC32().apply { update(key) }.value == expected) { "recordKey CRC32 校验失败" }
        return key
    }

    fun blockIv(iv: ByteArray, index: Int): ByteArray {
        require(iv.size == 16 && index >= 0)
        val value = BigInteger(1, iv).add(BigInteger.valueOf(index.toLong()).shiftLeft(18))
            .and(BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE)).toByteArray()
        return ByteArray(16).also { value.copyInto(it, maxOf(0, 16 - value.size), maxOf(0, value.size - 16)) }
    }

    fun ctr(key: ByteArray, iv: ByteArray): Cipher = Cipher.getInstance("AES/CTR/NoPadding").apply {
        init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
    }
}

internal fun JsonNode.requiredText(field: String): String = path(field).takeIf { it.isTextual && it.asText().isNotBlank() }
    ?.asText() ?: throw IllegalStateException("小米响应缺少 $field")
