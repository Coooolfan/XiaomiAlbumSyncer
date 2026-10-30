package com.coooolfan.xiaomialbumsyncer.model

enum class CloudProvider {
    XIAOMI, ICLOUD;

    fun validateSyncMode(mode: CrontabSyncMode) {
        when (this) {
            XIAOMI -> Unit
            ICLOUD -> require(mode != CrontabSyncMode.TIMELINE) { "iCloud 不支持时间线同步，请选择全量或位点增量" }
        }
    }
}
