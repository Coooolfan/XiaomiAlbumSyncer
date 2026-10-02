package com.coooolfan.xiaomialbumsyncer.model

/** iCloud 图库中的相册或智能相册查询。 */
data class ICloudAlbumRef(val zone: String, val recordName: String, val queryType: String, val smartAlbum: String? = null)
