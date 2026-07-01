package com.coooolfan.xiaomialbumsyncer.xiaomicloud

import com.coooolfan.xiaomialbumsyncer.model.RecordingType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class XiaomiRecordingNameTest {

    @Test
    fun parsesPhoneCallRecordingType() {
        val recordingName = parseXiaomiRecordingName("phone.mp3_78_1_1658720546000_1666531492635")

        assertEquals("phone.mp3", recordingName.fileName)
        assertEquals(RecordingType.PHONE_CALL, recordingName.recordingType)
    }

    @Test
    fun parsesRecorderRecordingType() {
        val recordingName = parseXiaomiRecordingName("meeting.m4a_686_0_1641032438000_1666737108163")

        assertEquals("meeting.m4a", recordingName.fileName)
        assertEquals(RecordingType.RECORDER, recordingName.recordingType)
    }

    @Test
    fun parsesAppRecordingType() {
        val recordingName = parseXiaomiRecordingName("微信录音 Jim_20260511204018.aac_4054_3_17785072729_1778632353827")

        assertEquals("微信录音 Jim_20260511204018.aac", recordingName.fileName)
        assertEquals(RecordingType.APP, recordingName.recordingType)
    }

    @Test
    fun parsesUnknownRecordingType() {
        val recordingName = parseXiaomiRecordingName("unknown.aac_5_99_1658712843000_1666531491447")

        assertEquals("unknown.aac", recordingName.fileName)
        assertEquals(RecordingType.UNKNOWN, recordingName.recordingType)
    }

    @Test
    fun keepsLegacyFileNameFallbackForUnexpectedFormat() {
        val recordingName = parseXiaomiRecordingName("sample-audio.m4a_device_location_type_suffix")

        assertEquals("sample-audio.m4a", recordingName.fileName)
        assertEquals(RecordingType.UNKNOWN, recordingName.recordingType)
    }
}
