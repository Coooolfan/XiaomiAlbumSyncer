package com.coooolfan.xiaomialbumsyncer.utils

import com.coooolfan.xiaomialbumsyncer.model.Album
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.noear.solon.core.AppContext
import java.text.DecimalFormat
import java.time.ZoneId
import java.util.*

fun Int.percentOf(total: Int): String {
    if (total == 0) return "0%"
    val formatter = DecimalFormat("#.##")  // 最多保留2位小数
    return "${formatter.format(this * 100.0 / total)}%"
}

fun String.toTimeZone(): TimeZone {
    return TimeZone.getTimeZone(ZoneId.of(this))
}

fun Album.isAudioAlbum(): Boolean {
    return this.remoteKey == "-1"
}

val AppContext.objectMapper: ObjectMapper
    get() = this.getBean(ObjectMapper::class.java)

fun JsonNode.requiredText(field: String): String =
    at("/$field").asText("").ifBlank { error("JSON 响应缺少字段 $field") }

fun JsonNode.textOrNull(field: String): String? =
    at("/$field").asText(null)?.takeIf { it.isNotBlank() }

// 小米接口约定：code != 0 即业务错误
fun JsonNode.throwIfBizError() {
    val code = at("/code").asInt()
    if (code == 0) return
    val reason = at("/description").asText().ifBlank { at("/reason").asText() }
    throw IllegalStateException("小米返回错误码 $code ($reason)")
}