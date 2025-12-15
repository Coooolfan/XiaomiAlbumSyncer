package com.coooolfan.xiaomialbumsyncer.utils

import com.coooolfan.xiaomialbumsyncer.model.*
import org.noear.solon.annotation.Managed
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.*
import kotlin.io.path.Path

@Managed
class SingleStagePatch {

    private val log = LoggerFactory.getLogger(SingleStagePatch::class.java)

    fun fillExifTime(
        assetPathMap: Map<Asset, Path>,
        systemConfig: SystemConfig,
        timeZone: TimeZone
    ) {

        log.info("开始填充下载文件的 EXIF 时间，共 ${assetPathMap.size} 个文件需要处理")

        var i = 0
        val step = maxOf(assetPathMap.size / 10, 1)
        for (it in assetPathMap) {
            if ((i + 1) % step == 0)
                log.info("正在尝试填充第 ${i + 1} 个文件的 Exif 数据，总进度：${(i + 1).percentOf(assetPathMap.size)}")

            try {
                rewriteExifTime(
                    it.key, it.value,
                    ExifRewriteConfig(Path(systemConfig.exifToolPath), timeZone)
                )
            } catch (e: Exception) {
                log.error("修改文件 EXIF 时间失败，跳过此文件，Asset ID: ${it.key.id}")
                e.printStackTrace()
            }
            i++
        }
    }

    fun rewriteFileSystemTime(assetPathMap: Map<Asset, Path>) {
        log.info("开始重写下载文件的文件系统时间，共 ${assetPathMap.size} 个文件需要处理")

        var i = 0
        val step = maxOf(assetPathMap.size / 10, 1)
        for (it in assetPathMap) {
            if ((i + 1) % step == 0)
                log.info("正在尝试重写第 ${i + 1} 个文件的文件系统时间，总进度：${(i + 1).percentOf(assetPathMap.size)}")

            try {
                rewriteFSTime(
                    it.value,
                    it.key.dateTaken
                )
            } catch (e: Exception) {
                log.error("修改文件系统时间未能正确返回，此操作的结果不可知，Asset ID: ${it.key.id}，Asset Path: ${it.value}")
                e.printStackTrace()
            }
            i++
        }
    }

}