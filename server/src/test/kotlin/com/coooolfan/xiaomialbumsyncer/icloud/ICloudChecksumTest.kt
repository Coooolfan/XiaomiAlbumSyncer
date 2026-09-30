package com.coooolfan.xiaomialbumsyncer.icloud

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Base64

class ICloudChecksumTest {
    @Test fun checksContentAndRejectsCorruption(@TempDir dir: Path) {
        val path = dir.resolve("photo")
        val content = "original photo".toByteArray()
        Files.write(path, content)
        val hash = MessageDigest.getInstance("SHA-1").digest(content)
        val raw = Base64.getEncoder().encodeToString(hash)
        val tagged = Base64.getEncoder().encodeToString(byteArrayOf(1) + hash)
        verifyICloudChecksum(path, raw)
        verifyICloudChecksum(path, tagged)
        Files.writeString(path, "corrupted")
        assertThrows(IllegalArgumentException::class.java) { verifyICloudChecksum(path, tagged) }
    }
}
