package com.coooolfan.xiaomialbumsyncer.model

/** iCloud 图库中的相册或智能相册查询。 */
data class ICloudAlbumRef(val zone: String, val recordName: String, val queryType: String, val smartAlbum: String? = null)

/** 一个相册成员的一个文件资源；不持久化有时效的下载 URL。 */
data class ICloudAssetRef(val zone: String, val recordName: String, val masterName: String, val resource: String, val checksum: String)
