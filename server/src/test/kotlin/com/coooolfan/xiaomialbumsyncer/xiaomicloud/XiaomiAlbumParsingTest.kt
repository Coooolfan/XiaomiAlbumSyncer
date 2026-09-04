package com.coooolfan.xiaomialbumsyncer.xiaomicloud

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class XiaomiAlbumParsingTest {
    private val objectMapper = ObjectMapper()

    @Test
    fun `parses private album as a regular named album`() {
        val album = parseGalleryAlbum(
            objectMapper.readTree(
                """
                {
                  "albumId": "1000",
                  "mediaCount": 707,
                  "lastUpdateTime": 1786036947393
                }
                """.trimIndent()
            ),
            accountId = 42L,
        )

        assertEquals(1000L, album.remoteId)
        assertEquals("隐私相册", album.name)
        assertEquals(707L, album.assetCount)
        assertEquals(42L, album.accountId)
    }

    @Test
    fun `keeps built in and custom album naming`() {
        val camera = parseGalleryAlbum(
            objectMapper.readTree("{\"albumId\": \"1\", \"mediaCount\": 1}"),
            accountId = 42L,
        )
        val screenshots = parseGalleryAlbum(
            objectMapper.readTree("{\"albumId\": \"2\", \"mediaCount\": 2}"),
            accountId = 42L,
        )
        val custom = parseGalleryAlbum(
            objectMapper.readTree("{\"albumId\": \"99\", \"name\": \"旅行\", \"mediaCount\": 3}"),
            accountId = 42L,
        )

        assertEquals("相机", camera.name)
        assertEquals("屏幕截图", screenshots.name)
        assertEquals("旅行", custom.name)
    }
}
