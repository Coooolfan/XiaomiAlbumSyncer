package com.coooolfan.xiaomialbumsyncer.utils

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.FileTime
import java.time.Instant

fun rewriteFSTime(
    path: Path,
    newTimeInstant: Instant
) {

    // 这种情况比较罕见
    if (!Files.exists(path)) {
        return
    }

    val newFileTime = FileTime.from(newTimeInstant)
    val attributeView = Files.getFileAttributeView(path, BasicFileAttributeView::class.java)

    // 如果某个参数为 null，则对应的时间不会被修改
    attributeView.setTimes(newFileTime, null, newFileTime)
}