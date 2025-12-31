package com.coooolfan.xiaomialbumsyncer.utils

import com.coooolfan.xiaomialbumsyncer.model.Album
import java.text.DecimalFormat
import java.time.ZoneId
import java.util.TimeZone

fun Int.percentOf(total: Int): String {
    if (total == 0) return "0%"
    val formatter = DecimalFormat("#.##")  // 最多保留2位小数
    return "${formatter.format(this * 100.0 / total)}%"
}

fun String.toTimeZone(): TimeZone {
    return TimeZone.getTimeZone(ZoneId.of(this))
}

fun Album.isAudioAlbum(): Boolean {
    return this.remoteId == -1L
}