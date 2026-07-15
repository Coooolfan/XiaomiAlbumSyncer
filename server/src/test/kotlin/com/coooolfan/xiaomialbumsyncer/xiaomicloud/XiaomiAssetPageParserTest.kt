package com.coooolfan.xiaomialbumsyncer.xiaomicloud

import com.fasterxml.jackson.databind.ObjectMapper
import com.coooolfan.xiaomialbumsyncer.model.AssetType
import com.coooolfan.xiaomialbumsyncer.model.RecordingType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class XiaomiAssetPageParserTest {
    private val objectMapper = ObjectMapper()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    @Test
    fun `streams gallery assets without building a page tree`() {
        val body =
            """
            {
              "data": {
                "isLastPage": true,
                "galleries": [
                  {
                    "id": 11,
                    "fileName": "photo.jpg",
                    "type": "image",
                    "dateTaken": 1710000000000,
                    "sha1": "sha-photo",
                    "mimeType": "image/jpeg",
                    "title": "Photo",
                    "size": 1234,
                    "ignored": {"nested": true}
                  },
                  {
                    "id": 12,
                    "fileName": "clip.mp4",
                    "type": "video",
                    "dateTaken": 1720000000000,
                    "sha1": "sha-video",
                    "mimeType": "video/mp4"
                  }
                ]
              }
            }
            """.trimIndent().toResponseBody(jsonMediaType)

        val page = parseXiaomiAssetPage(objectMapper, body, albumId = 7, audioAlbum = false)

        assertTrue(page.isLastPage)
        assertEquals(2, page.assets.size)
        assertEquals(7, page.assets[0].album.id)
        assertEquals(AssetType.IMAGE, page.assets[0].type)
        assertEquals("Photo", page.assets[0].title)
        assertEquals(1234, page.assets[0].size)
        assertEquals(AssetType.VIDEO, page.assets[1].type)
        assertEquals("clip", page.assets[1].title)
        assertEquals(0, page.assets[1].size)
    }

    @Test
    fun `recording pagination stops on a short final page`() {
        assertTrue(shouldFetchNextAssetPage(audioAlbum = true, assetCount = 500, pageSize = 500, isLastPage = false))
        assertFalse(shouldFetchNextAssetPage(audioAlbum = true, assetCount = 163, pageSize = 500, isLastPage = false))
        assertTrue(shouldFetchNextAssetPage(audioAlbum = false, assetCount = 200, pageSize = 200, isLastPage = false))
        assertFalse(shouldFetchNextAssetPage(audioAlbum = false, assetCount = 200, pageSize = 200, isLastPage = true))
    }

    @Test
    fun `streams recording assets and leaves absent isLastPage false`() {
        val body =
            """
            {
              "data": {
                "list": [
                  {
                    "id": 21,
                    "name": "meeting.m4a_686_0_1641032438000_1666737108163",
                    "create_time": 1641032438000,
                    "sha1": "sha-recording",
                    "size": 4321
                  }
                ]
              }
            }
            """.trimIndent().toResponseBody(jsonMediaType)

        val page = parseXiaomiAssetPage(objectMapper, body, albumId = 9, audioAlbum = true)

        assertFalse(page.isLastPage)
        assertEquals(1, page.assets.size)
        assertEquals("meeting.m4a", page.assets.single().fileName)
        assertEquals(AssetType.AUDIO, page.assets.single().type)
        assertEquals(RecordingType.RECORDER, page.assets.single().recordingType)
        assertEquals(4321, page.assets.single().size)
    }
}
