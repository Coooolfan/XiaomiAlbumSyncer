package com.coooolfan.xiaomialbumsyncer.model

import org.babyfish.jimmer.sql.Serialized
import java.time.LocalDate

@Serialized
data class AlbumTimeline(
    val indexHash: String,
    val dayCount: Map<LocalDate, Long>
)

val EMPTY_ALBUM_TIMELINE = AlbumTimeline("-1", emptyMap())

operator fun AlbumTimeline.minus(other: AlbumTimeline): Map<LocalDate, Long> {
    if (this.indexHash == other.indexHash) return emptyMap()
    val allKeys = this.dayCount.keys + other.dayCount.keys
    return allKeys.associateWith { k ->
        (this.dayCount[k] ?: 0L) - (other.dayCount[k] ?: 0L)
    }.filterValues { it != 0L }
}