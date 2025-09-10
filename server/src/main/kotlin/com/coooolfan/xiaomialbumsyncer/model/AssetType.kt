package com.coooolfan.xiaomialbumsyncer.model

import org.babyfish.jimmer.sql.EnumType


@EnumType(EnumType.Strategy.NAME)
enum class AssetType {
    IMAGE,
    VIDEO
}