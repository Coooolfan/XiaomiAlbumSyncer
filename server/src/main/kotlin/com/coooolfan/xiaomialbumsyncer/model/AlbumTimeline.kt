package com.coooolfan.xiaomialbumsyncer.model

import org.babyfish.jimmer.sql.Serialized
import java.time.LocalDate

@Serialized
data class AlbumTimeline(
    val indexHash: String,
    val dayCount: Map<LocalDate, Long>
)

val EMPTY_ALBUM_TIMELINE = AlbumTimeline("-1", emptyMap())

/**
 * 计算两个相册时间线的差异
 * 如果两个时间线的indexHash相同，返回空Map（表示没有差异）
 * 否则，计算每个日期对应的媒体数量差值
 *
 * @param other 另一个相册时间线
 * @return 日期到数量差值的映射，只包含非零差值
 */
operator fun AlbumTimeline.minus(other: AlbumTimeline): Map<LocalDate, Long> {
    if (this.indexHash == other.indexHash) return emptyMap()
    val allKeys = this.dayCount.keys + other.dayCount.keys
    return allKeys.associateWith { k ->
        (this.dayCount[k] ?: 0L) - (other.dayCount[k] ?: 0L)
    }.filterValues { it != 0L }
}