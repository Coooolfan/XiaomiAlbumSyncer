package com.coooolfan.xiaomialbumsyncer.icloud

import org.noear.solon.annotation.Inject
import org.noear.solon.annotation.Managed
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** AES-GCM 密钥与数据库分开保存，备份时必须同时保留密钥文件。 */
@Managed
class ICloudSecrets {
    @Inject(value = $$"${solon.app.db}")
    private lateinit var databasePath: String
    private val random = SecureRandom()
    private val key by lazy {
        val file = Path.of(databasePath).toAbsolutePath().resolveSibling("icloud.key")
        Files.createDirectories(file.parent)
        val bytes = ByteArray(32).also(random::nextBytes)
        try {
            val attributes = if (file.fileSystem.supportedFileAttributeViews().contains("posix"))
                arrayOf(PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))) else emptyArray()
            Files.newByteChannel(file, setOf(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE), *attributes).use { channel ->
                channel.write(java.nio.ByteBuffer.wrap(bytes))
            }
        } catch (_: java.nio.file.FileAlreadyExistsException) { }
        val stored = Files.readAllBytes(file)
        require(stored.size == 32) { "iCloud 凭据密钥文件无效" }
        SecretKeySpec(stored, "AES")
    }
    @Synchronized fun encrypt(value: String): String {
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        return Base64.getEncoder().encodeToString(iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)))
    }
    @Synchronized fun decrypt(value: String): String {
        val bytes = Base64.getDecoder().decode(value)
        require(bytes.size >= 28) { "iCloud 凭据数据无效" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }
}
