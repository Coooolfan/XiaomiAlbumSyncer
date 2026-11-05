package com.coooolfan.xiaomialbumsyncer.e2e

object CrontabConfigCases {

    private val Omit = Any()
    private val ExplicitNull = Any()

    private data class Dimension(val field: String, val states: List<Any?>)

    private val fixedValues: Map<String, Any?> = linkedMapOf(
        "expression" to "0 0 0 1 1 ? 2099",
        "timeZone" to "UTC",
        "targetPath" to "./downloads",
    )

    private val defaultValues: Map<String, Any?> = mapOf(
        "skipExistingFile" to true,
        "rewriteFileSystemTime" to false,
        "checkSha1" to false,
        "fetchFromDbSize" to 2,
        "downloaders" to 8,
        "verifiers" to 2,
        "exifProcessors" to 2,
        "fileTimeWorkers" to 2,
        "downloadAudios" to true,
        "notify" to true,
        "rewriteExifTimeZone" to null,
    )

    private val dimensions: List<Dimension> = listOf(
        Dimension("skipExistingFile", listOf(Omit, false, true)),
        Dimension("rewriteFileSystemTime", listOf(Omit, false, true)),
        Dimension("checkSha1", listOf(Omit, false, true)),
        Dimension("downloadAudios", listOf(Omit, false, true)),
        Dimension("notify", listOf(Omit, false, true)),
        Dimension("fetchFromDbSize", listOf(Omit, 3)),
        Dimension("downloaders", listOf(Omit, 4)),
        Dimension("verifiers", listOf(Omit, 5)),
        Dimension("exifProcessors", listOf(Omit, 6)),
        Dimension("fileTimeWorkers", listOf(Omit, 7)),
        Dimension("downloadImages", listOf(false, true)),
        Dimension("downloadVideos", listOf(false, true)),
        Dimension("rewriteExifTime", listOf(false, true)),
        Dimension("rewriteExifTimeZone", listOf(Omit, ExplicitNull, "UTC")),
    )

    val total: Int = dimensions.fold(1) { acc, dimension -> acc * dimension.states.size }

    val declaredFieldNames: Set<String> = fixedValues.keys + dimensions.mapTo(linkedSetOf()) { it.field }
    val optionalFieldNames: Set<String> = setOf(
        "skipExistingFile", "rewriteFileSystemTime", "checkSha1", "downloadAudios", "notify",
        "fetchFromDbSize", "downloaders", "verifiers", "exifProcessors", "fileTimeWorkers",
    )
    val nullableFieldNames: Set<String> = setOf("rewriteExifTimeZone")
    val stringFields: Set<String> = setOf("expression", "timeZone", "targetPath", "rewriteExifTimeZone")
    val booleanFields: Set<String> = setOf(
        "downloadImages", "downloadVideos", "rewriteExifTime",
        "skipExistingFile", "rewriteFileSystemTime", "checkSha1", "downloadAudios", "notify",
    )
    val intFields: Set<String> = setOf(
        "fetchFromDbSize", "downloaders", "verifiers", "exifProcessors", "fileTimeWorkers",
    )

    data class Case(
        val index: Int,
        val input: Map<String, Any?>,
        val expected: Map<String, Any?>,
    ) {
        val crontabId: Long get() = 9001L + index
        val crontabName: String get() = "Combination $index"
    }

    fun decode(index: Int): Case {
        require(index in 0 until total) { "index $index 超出组合范围" }
        var remainder = index
        val input = linkedMapOf<String, Any?>()
        input += fixedValues
        val expected = linkedMapOf<String, Any?>()
        expected += fixedValues
        dimensions.forEach { dimension ->
            val state = dimension.states[remainder % dimension.states.size]
            remainder /= dimension.states.size
            if (state !== Omit) {
                input[dimension.field] = if (state === ExplicitNull) null else state
            }
            expected[dimension.field] = when {
                state === Omit -> defaultValues.getValue(dimension.field)
                state === ExplicitNull -> null
                else -> state
            }
        }
        return Case(index, input, expected)
    }

    val cases: Sequence<Case>
        get() = (0 until total).asSequence().map(::decode)
}
