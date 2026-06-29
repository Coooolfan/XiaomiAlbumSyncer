package com.coooolfan.xiaomialbumsyncer.model

import org.babyfish.jimmer.sql.EnumType

@EnumType(EnumType.Strategy.NAME)
enum class RecordingType(
    val code: Int,
    val label: String,
) {
    RECORDER(0, "录音机录音"),
    PHONE_CALL(1, "通话录音"),
    FM(2, "FM录音"),
    APP(3, "应用录音"),
    UNKNOWN(-1, "未知录音");

    companion object {
        fun fromCode(code: Int): RecordingType {
            return entries.firstOrNull { it.code == code } ?: UNKNOWN
        }
    }
}
