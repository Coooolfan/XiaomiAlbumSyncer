package com.coooolfan.xiaomialbumsyncer.xiaomicloud.e2ee

import com.coooolfan.xiaomialbumsyncer.utils.client
import com.coooolfan.xiaomialbumsyncer.utils.ua
import com.fasterxml.jackson.databind.JsonNode
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/** 临时目标通过明文大小及 hash 校验后才交还下载流水线。 */
internal class GalleryBlockDownloader(private val http: OkHttpClient = client()) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun download(data: JsonNode, target: Path, expectedSize: Long, expectedSha1: String, downloadId: String = java.util.UUID.randomUUID().toString(), appKey: (Long) -> ByteArray) {
        val started = System.nanoTime()
        val kss = data.path("kss")
        check(kss.path("stat").asText() == "OK") { "相册分块存储状态异常" }
        val secureKey = kss.path("secure_key").takeUnless { it.isMissingNode || it.isNull }?.let {
            check(it.isTextual) { "相册 secure_key 格式错误" }
            it.asText().takeIf(String::isNotBlank)?.also { value ->
                check(value.length % 2 == 0 && value.matches(Regex("[0-9a-fA-F]+"))) { "相册 secure_key 必须为十六进制" }
            }
        }
        val blocks = kss.path("blocks")
        check(blocks.isArray && blocks.size() > 0) { "相册下载缺少文件块" }
        requireSha1(expectedSha1)
        check(expectedSize >= 0 && blocks.all { it.path("size").isIntegralNumber && it.path("size").asLong() in 0..4194304 }) { "相册文件块大小无效" }
        check(blocks.sumOf { it.path("size").asLong() } == expectedSize) { "相册分块总大小与资产大小不一致" }
        val info = data.path("encryptInfo").takeUnless { it.isMissingNode || it.isNull }
        log.info("相册分块开始 downloadId={} blocks={} expectedBytes={} rc4={} encrypted={} keyVersion={}",
            downloadId, blocks.size(), expectedSize, secureKey != null, info != null, info?.path("appKeyVersion")?.asText())
        val key = info?.let {
            check(it.isObject) { "encryptInfo 格式错误" }
            val version = it.path("appKeyVersion")
            check(version.isIntegralNumber && version.canConvertToLong() && version.asLong() > 0) { "appKeyVersion 无效" }
            val wrappingKey = appKey(version.asLong())
            try { E2eeCrypto.recordKey(it, wrappingKey) } finally { wrappingKey.fill(0) }
        }
        try {
            target.parent?.let { Files.createDirectories(it) }
            RandomAccessFile(target.toFile(), "rw").use { output ->
                output.setLength(0)
                blocks.forEachIndexed { index, block ->
                    log.debug("相册块开始 downloadId={} block={} bytes={}", downloadId, index, block.path("size").asLong())
                    val start = output.filePointer
                    val urls = block.path("urls").map { it.asText() }.filter { it.isNotBlank() }
                    check(urls.isNotEmpty()) { "文件块缺少下载地址" }
                    val sha1 = block.requiredText("sha1").also(::requireSha1)
                    val encryptedSha1 = block.path("encryptedSha1").takeIf { it.isTextual && it.asText().isNotBlank() }?.asText()?.also(::requireSha1)
                    val iv = info?.let { E2eeCrypto.blockIv(E2eeCrypto.decode(it.requiredText("recordIV")), index) }
                    var lastError: Exception? = null
                    for (attempt in 0..2) {
                        try {
                            output.seek(start)
                            output.setLength(start)
                            readBlock(urls[attempt % urls.size], block.path("size").asLong(), sha1, encryptedSha1, secureKey, key, iv, output)
                            log.debug("相册块完成 downloadId={} block={} attempt={}", downloadId, index, attempt + 1)
                            lastError = null
                            break
                        } catch (e: Exception) {
                            if (Thread.currentThread().isInterrupted) throw e
                            log.warn("相册块失败 downloadId={} block={} attempt={} urls={} errorType={}",
                                downloadId, index, attempt + 1, urls.size, e.javaClass.simpleName)
                            lastError = e
                        }
                    }
                    if (lastError != null) throw IllegalStateException("相册文件块 $index 下载或校验失败，请重试任务", lastError)
                }
                check(output.length() == expectedSize) { "相册文件大小校验失败" }
            }
            val sha1 = MessageDigest.getInstance("SHA-1")
            Files.newInputStream(target).use { input ->
                val buffer = ByteArray(65536)
                while (true) { val count = input.read(buffer); if (count < 0) break; sha1.update(buffer, 0, count) }
            }
            check(hex(sha1.digest()).equals(expectedSha1, ignoreCase = true)) { "相册文件明文 SHA-1 校验失败" }
            log.info("相册分块校验完成 downloadId={} bytes={} elapsedMs={}", downloadId, expectedSize, (System.nanoTime() - started) / 1_000_000)
        } catch (e: Exception) {
            log.warn("相册分块失败 downloadId={} errorType={} elapsedMs={}", downloadId, e.javaClass.simpleName, (System.nanoTime() - started) / 1_000_000)
            try { Files.deleteIfExists(target) } catch (cleanup: Exception) { e.addSuppressed(cleanup) }
            throw e
        } finally { key?.fill(0) }
    }

    private fun readBlock(url: String, size: Long, sha1: String, encryptedSha1: String?, secureKey: String?, key: ByteArray?, iv: ByteArray?, output: RandomAccessFile) {
        val request = Request.Builder().url(url).ua().get().build()
        http.newCall(request).execute().use { response ->
            check(response.code == 200) { "相册文件块返回 HTTP ${response.code}" }
            val plainHash = MessageDigest.getInstance("SHA-1")
            val cipherHash = MessageDigest.getInstance("SHA-1")
            val cipher = key?.let { E2eeCrypto.ctr(it, requireNotNull(iv)) }
            // 每个块和每次重试均从 RC4 初始状态开始；RC4 外层先于 E2EE AES-CTR 解密。
            val storageCipher = secureKey?.let {
                val bytes = java.util.HexFormat.of().parseHex(it)
                try {
                    Cipher.getInstance("ARCFOUR").apply { init(Cipher.DECRYPT_MODE, SecretKeySpec(bytes, "ARCFOUR")) }
                } finally { bytes.fill(0) }
            }
            var count = 0L
            fun write(bytes: ByteArray) { plainHash.update(bytes); output.write(bytes) }
            fun decrypt(bytes: ByteArray, length: Int = bytes.size) {
                // encryptedSha1 校验的是去除 RC4 外层后的 E2EE 密文。
                cipherHash.update(bytes, 0, length)
                if (cipher == null) { plainHash.update(bytes, 0, length); output.write(bytes, 0, length) }
                else cipher.update(bytes, 0, length)?.let(::write)
            }
            response.body.byteStream().use { input ->
                val buffer = ByteArray(65536)
                while (true) {
                    if (Thread.currentThread().isInterrupted) throw InterruptedException("下载已取消")
                    val length = input.read(buffer)
                    if (length < 0) break
                    count += length
                    check(count <= size) { "相册文件块超出声明大小" }
                    if (storageCipher == null) decrypt(buffer, length)
                    else storageCipher.update(buffer, 0, length)?.let { decrypt(it) }
                }
                storageCipher?.doFinal()?.let { decrypt(it) }
                cipher?.doFinal()?.let(::write)
            }
            check(count == size) { "相册文件块不完整" }
            check(hex(plainHash.digest()).equals(sha1, ignoreCase = true)) { "相册文件块明文 SHA-1 校验失败" }
            if (encryptedSha1 != null) check(hex(cipherHash.digest()).equals(encryptedSha1, ignoreCase = true)) { "相册文件块密文 SHA-1 校验失败" }
        }
    }

    private fun requireSha1(value: String) { require(Regex("[0-9a-fA-F]{40}").matches(value)) { "相册 SHA-1 格式错误" } }
    private fun hex(bytes: ByteArray) = java.util.HexFormat.of().formatHex(bytes)
}
