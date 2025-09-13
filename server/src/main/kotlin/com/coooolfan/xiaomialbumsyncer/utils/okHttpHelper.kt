package com.coooolfan.xiaomialbumsyncer.utils

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import java.nio.file.Path

const val UA =
    """Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36 Edg/139.0.0.0"""

fun Request.Builder.ua() = this.header("User-Agent", UA)

fun Request.Builder.authHeader(authPair: Pair<String, String>) =
    this.header("Cookie", withCookie("userId" to authPair.first, "serviceToken" to authPair.second))

fun withCookie(vararg pairs: Pair<String, String?>): String {
    return pairs.joinToString(" ") { (k, v) -> "$k=${v ?: ""};" }
}

fun client(): OkHttpClient = OkHttpClient().newBuilder()
    .followRedirects(false)           // 禁用HTTP重定向
    .followSslRedirects(false)        // 禁用HTTPS重定向
    .build()

fun throwIfNotSuccess(respCode: Int) {
    val i = respCode / 100
    if (i != 2 && i != 3) throw IllegalStateException("Request failed with HTTP code $respCode")
}

// 保存ResponseBody到指定文件，自动创建父目录，流式写
fun ResponseBody.saveToFile(targetPath: Path) {
    val targetFile = targetPath.toFile()
    targetFile.parentFile?.mkdirs()

    byteStream().use { inputStream ->
        targetFile.outputStream().use { outputStream ->
            inputStream.copyTo(outputStream, bufferSize = 8192)
        }
    }
}