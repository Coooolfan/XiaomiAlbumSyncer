package com.coooolfan.xiaomialbumsyncer.xiaomicloud

import com.coooolfan.xiaomialbumsyncer.model.Album

internal fun Album.isXiaomiRecordingAlbum(): Boolean = remoteKey == "-1"
