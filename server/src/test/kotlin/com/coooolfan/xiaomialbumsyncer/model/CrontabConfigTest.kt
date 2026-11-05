package com.coooolfan.xiaomialbumsyncer.model

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CrontabConfigTest {

    private val mapper = jacksonObjectMapper()

    @Test
    fun deserializesLegacyJsonWithDefaults() {
        val config = mapper.readValue<CrontabConfig>(
            """{"expression":"0 0 * * * ?","timeZone":"Asia/Shanghai","targetPath":"/data/downloads","downloadImages":true,"downloadVideos":false,"rewriteExifTime":true,"rewriteExifTimeZone":null}"""
        )

        assertEquals("0 0 * * * ?", config.expression)
        assertEquals("Asia/Shanghai", config.timeZone)
        assertEquals("/data/downloads", config.targetPath)
        assertTrue(config.downloadImages)
        assertFalse(config.downloadVideos)
        assertTrue(config.rewriteExifTime)
        assertNull(config.rewriteExifTimeZone)
        assertTrue(config.skipExistingFile)
        assertFalse(config.rewriteFileSystemTime)
        assertFalse(config.checkSha1)
        assertEquals(2, config.fetchFromDbSize)
        assertEquals(8, config.downloaders)
        assertEquals(2, config.verifiers)
        assertEquals(2, config.exifProcessors)
        assertEquals(2, config.fileTimeWorkers)
        assertTrue(config.downloadAudios)
        assertTrue(config.notify)
    }

    @Test
    fun deserializesLegacyJsonIgnoringRemovedProperties() {
        val config = mapper.readValue<CrontabConfig>(
            """{"expression":"0 30 2 * * ?","timeZone":"UTC","targetPath":"/data","downloadImages":true,"downloadVideos":true,"rewriteExifTime":false,"rewriteExifTimeZone":"UTC","diffByTimeline":true,"expressionTargetPath":"/ignored"}"""
        )

        assertEquals("0 30 2 * * ?", config.expression)
        assertEquals("UTC", config.rewriteExifTimeZone)
        assertTrue(config.downloadAudios)
        assertTrue(config.notify)
    }

    @Test
    fun serializationRoundTripPreservesExplicitValues() {
        val config = CrontabConfig(
            expression = "0 0 3 * * ?",
            timeZone = "America/New_York",
            targetPath = "/mnt/photos",
            downloadImages = true,
            downloadVideos = true,
            rewriteExifTime = true,
            rewriteExifTimeZone = "Asia/Tokyo",
            skipExistingFile = false,
            rewriteFileSystemTime = true,
            checkSha1 = true,
            fetchFromDbSize = 5,
            downloaders = 16,
            verifiers = 4,
            exifProcessors = 3,
            fileTimeWorkers = 6,
            downloadAudios = false,
            notify = false,
        )

        val restored = mapper.readValue<CrontabConfig>(mapper.writeValueAsString(config))

        assertEquals(config, restored)
        assertFalse(restored.downloadAudios)
        assertFalse(restored.notify)
    }

    @Test
    fun reflectConfigRegistersCrontabConfigForInvocation() {
        val entry = loadNativeImageEntry("META-INF/native-image/reflect-config.json", "name")

        assertNotNull(entry)
        assertTrue(entry!!["allDeclaredFields"]?.asBoolean() == true)
        assertTrue(entry["allDeclaredConstructors"]?.asBoolean() == true)
        assertTrue(entry["allPublicMethods"]?.asBoolean() == true)
    }

    @Test
    fun reachabilityMetadataRegistersCrontabConfigForInvocation() {
        val entry = loadNativeImageEntry("META-INF/native-image/reachability-metadata.json", "type")

        assertNotNull(entry)
        assertTrue(entry!!["allDeclaredFields"]?.asBoolean() == true)
        assertTrue(entry["allDeclaredConstructors"]?.asBoolean() == true)
        assertTrue(entry["allPublicMethods"]?.asBoolean() == true)
    }

    private fun loadNativeImageEntry(path: String, key: String): JsonNode? {
        val stream = javaClass.classLoader.getResourceAsStream(path)
        assertNotNull(stream)
        val root = mapper.readTree(stream)
        val reflection = if (root.isArray) root else root["reflection"]
        return reflection.firstOrNull {
            it[key]?.asText() == CrontabConfig::class.java.name
        }
    }
}
