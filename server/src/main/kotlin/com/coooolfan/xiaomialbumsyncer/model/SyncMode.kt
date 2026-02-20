package com.coooolfan.xiaomialbumsyncer.model

import org.babyfish.jimmer.sql.EnumType

// 同步模式枚举
@EnumType(EnumType.Strategy.NAME)
enum class SyncMode {
    ADD_ONLY, // 仅新增模式：只下载云端新增的文件到本地
    SYNC_ALL_CHANGES  // 同步所有变化模式：同步云端的新增、修改、删除到本地
}