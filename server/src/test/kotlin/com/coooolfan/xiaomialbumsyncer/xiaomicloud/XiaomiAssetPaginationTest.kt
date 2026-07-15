package com.coooolfan.xiaomialbumsyncer.xiaomicloud

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class XiaomiAssetPaginationTest {
    @Test
    fun `recording pagination stops on a short final page`() {
        assertTrue(shouldFetchNextAssetPage(audioAlbum = true, assetCount = 500, pageSize = 500, isLastPage = false))
        assertFalse(shouldFetchNextAssetPage(audioAlbum = true, assetCount = 163, pageSize = 500, isLastPage = false))
        assertTrue(shouldFetchNextAssetPage(audioAlbum = false, assetCount = 200, pageSize = 200, isLastPage = false))
        assertFalse(shouldFetchNextAssetPage(audioAlbum = false, assetCount = 200, pageSize = 200, isLastPage = true))
        assertFalse(shouldFetchNextAssetPage(audioAlbum = false, assetCount = 0, pageSize = 200, isLastPage = false))
    }
}
