package com.coooolfan.xiaomialbumsyncer.icloud

import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

/** 图库中的相册或系统集合；查询条件由集合标识决定。 */
@JsonPropertyOrder("zone", "albumId")
data class ICloudAlbumKey(val zone: String, val albumId: String) {
    init {
        require(zone.isNotBlank() && albumId.isNotBlank()) { "iCloud 相册定位信息缺失" }
    }

    fun queryType(): String = when (albumId) {
        "__all__" -> "CPLAssetAndMasterByAssetDateWithoutHiddenOrDeleted"
        "__hidden__" -> "CPLAssetAndMasterHiddenByAssetDate"
        "__favorites__" -> "CPLAssetAndMasterInSmartAlbumByAssetDate"
        else -> "CPLContainerRelationLiveByAssetDate"
    }

    fun smartAlbum(): String? = if (albumId == "__favorites__") "FAVORITE" else null

    fun encode(): String = mapper.writeValueAsString(this)

    companion object {
        private val mapper = jacksonObjectMapper()
        fun decode(value: String): ICloudAlbumKey = mapper.readValue(value)
    }
}
