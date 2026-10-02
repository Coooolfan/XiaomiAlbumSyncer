package com.coooolfan.xiaomialbumsyncer.icloud

import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

/** 一个逻辑资产中的一个原始文件；图库区域由所属相册提供。 */
@JsonPropertyOrder("assetId", "resource")
data class ICloudResourceKey(val assetId: String, val resource: String) {
    init {
        require(assetId.isNotBlank()) { "iCloud 资产标识缺失" }
        require(resource in setOf("resOriginal", "resOriginalAlt", "resOriginalVidCompl")) { "不支持的 iCloud 原件资源" }
    }

    fun encode(): String = mapper.writeValueAsString(this)

    companion object {
        private val mapper = jacksonObjectMapper()
        fun decode(value: String): ICloudResourceKey = mapper.readValue(value)
    }
}
