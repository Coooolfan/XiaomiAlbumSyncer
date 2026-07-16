package com.coooolfan.xiaomialbumsyncer.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.nio.file.Path

class ExifHelperTest {

    @Test
    fun `image condition only matches a missing or zero date`() {
        assertEquals(
            "not defined \$DateTimeOriginal or \$DateTimeOriginal =~ /^0000:00:00 00:00:00/",
            IMAGE_TIME_MISSING_CONDITION,
        )
    }

    @Test
    fun `failed condition can be treated as a successful no-op`() {
        runExifTool(
            Path.of("/bin/sh"),
            listOf("-c", "printf '1 files failed condition\\n'; exit 2"),
            failedConditionIsSuccess = true,
        )
    }

    @Test
    fun `other exiftool failures are not hidden`() {
        assertThrows(RuntimeException::class.java) {
            runExifTool(
                Path.of("/bin/sh"),
                listOf("-c", "printf 'write failed\\n'; exit 2"),
                failedConditionIsSuccess = true,
            )
        }
    }
}
