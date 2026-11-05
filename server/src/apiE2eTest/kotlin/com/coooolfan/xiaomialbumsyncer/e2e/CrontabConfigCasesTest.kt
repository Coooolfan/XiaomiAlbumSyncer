package com.coooolfan.xiaomialbumsyncer.e2e

import com.coooolfan.xiaomialbumsyncer.model.CrontabConfig
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.reflect.full.primaryConstructor

class CrontabConfigCasesTest {

    private val mapper = ObjectMapper()

    @Test
    fun generatorMatchesCrontabConfigShape() {
        val constructor = CrontabConfig::class.primaryConstructor ?: error("CrontabConfig 缺少主构造器")
        val parameters = constructor.parameters

        assertEquals(
            CrontabConfigCases.declaredFieldNames,
            parameters.mapTo(HashSet()) { it.name },
            "字段集合漂移"
        )
        assertEquals(
            CrontabConfigCases.optionalFieldNames,
            parameters.filter { it.isOptional }.mapTo(HashSet()) { it.name },
            "可选字段集合漂移"
        )
        assertEquals(
            CrontabConfigCases.nullableFieldNames,
            parameters.filter { it.type.isMarkedNullable }.mapTo(HashSet()) { it.name },
            "可空字段集合漂移"
        )
        parameters.forEach { parameter ->
            val expectedClassifier = when (parameter.name) {
                in CrontabConfigCases.stringFields -> String::class
                in CrontabConfigCases.booleanFields -> Boolean::class
                in CrontabConfigCases.intFields -> Int::class
                else -> error("未声明类型的字段: ${parameter.name}")
            }
            assertEquals(expectedClassifier, parameter.type.classifier, "字段 ${parameter.name} 类型漂移")
        }
    }

    @Test
    fun generatorCoversFullCartesianProduct() {
        assertEquals(186624, CrontabConfigCases.total)

        val seen = HashSet<String>()
        var count = 0
        var lastIndex = -1
        CrontabConfigCases.cases.forEach { case ->
            assertEquals(lastIndex + 1, case.index)
            lastIndex = case.index
            assertTrue(seen.add(stateKey(case.input)), "重复组合 index=${case.index}")
            assertEquals(CrontabConfigCases.declaredFieldNames, case.expected.keys)
            count += 1
        }
        assertEquals(CrontabConfigCases.total, count)
        assertEquals(186624, seen.size)
    }

    @Test
    fun generatorProducesExpectedBoundaryCases() {
        val first = CrontabConfigCases.decode(0)
        assertEquals(
            setOf("expression", "timeZone", "targetPath", "downloadImages", "downloadVideos", "rewriteExifTime"),
            first.input.keys
        )
        assertFalse(first.input.getValue("downloadImages") as Boolean)
        assertFalse(first.input.getValue("downloadVideos") as Boolean)
        assertFalse(first.input.getValue("rewriteExifTime") as Boolean)
        assertTrue(first.expected.getValue("skipExistingFile") as Boolean)
        assertEquals(8, first.expected.getValue("downloaders"))
        assertNull(first.expected["rewriteExifTimeZone"])

        val last = CrontabConfigCases.decode(CrontabConfigCases.total - 1)
        assertTrue(last.input.getValue("skipExistingFile") as Boolean)
        assertTrue(last.input.getValue("notify") as Boolean)
        assertEquals(3, last.input.getValue("fetchFromDbSize"))
        assertEquals(7, last.input.getValue("fileTimeWorkers"))
        assertTrue(last.input.getValue("rewriteExifTime") as Boolean)
        assertEquals("UTC", last.input.getValue("rewriteExifTimeZone"))
        assertEquals(7, last.expected.getValue("fileTimeWorkers"))
    }

    @Test
    fun generatorDistinguishesOmitNullAndExplicitValues() {
        val explicitFalse = CrontabConfigCases.cases.first { it.input["skipExistingFile"] == false }
        assertTrue(explicitFalse.input.containsKey("skipExistingFile"))
        assertFalse(explicitFalse.expected.getValue("skipExistingFile") as Boolean)
        assertTrue(mapper.writeValueAsString(explicitFalse.input).contains("\"skipExistingFile\":false"))

        val omitted = CrontabConfigCases.cases.first { !it.input.containsKey("skipExistingFile") }
        assertTrue(omitted.expected.getValue("skipExistingFile") as Boolean)
        assertFalse(mapper.writeValueAsString(omitted.input).contains("skipExistingFile"))

        val explicitNull = CrontabConfigCases.cases.first {
            it.input.containsKey("rewriteExifTimeZone") && it.input["rewriteExifTimeZone"] == null
        }
        assertNull(explicitNull.expected["rewriteExifTimeZone"])
        assertTrue(mapper.writeValueAsString(explicitNull.input).contains("\"rewriteExifTimeZone\":null"))

        val omittedTimeZone = CrontabConfigCases.decode(0)
        assertFalse(omittedTimeZone.input.containsKey("rewriteExifTimeZone"))
        assertFalse(mapper.writeValueAsString(omittedTimeZone.input).contains("rewriteExifTimeZone"))
    }

    private fun stateKey(input: Map<String, Any?>): String =
        input.entries.sortedBy { it.key }.joinToString("|") { "${it.key}=${it.value}" }
}
