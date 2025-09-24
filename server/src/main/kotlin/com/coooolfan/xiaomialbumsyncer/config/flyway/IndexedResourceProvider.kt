package com.coooolfan.xiaomialbumsyncer.config.flyway

import org.flywaydb.core.api.ResourceProvider
import org.flywaydb.core.api.resource.LoadableResource
import org.flywaydb.core.internal.resource.classpath.ClassPathResource
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * 一个简化的、native-image 友好的 ResourceProvider 实现。
 *
 * 工作方式：
 * - 通过在构建期生成的资源清单（index 文件）列出所有可用迁移脚本；
 * - 运行期根据 prefix/suffix 进行匹配并返回可读的 LoadableResource。
 *
 * @author gpt-5
 */
class IndexedResourceProvider(
    private val classLoader: ClassLoader = Thread.currentThread().contextClassLoader,
    private val encoding: Charset = StandardCharsets.UTF_8,
    /**
     * 资源索引文件路径（必须在 classpath 上），每行一个资源路径，如：
     * db/migration/V1__init.sql
     * db/migration/V2__add_table.sql
     */
    private val indexPath: String = "META-INF/flyway-resources.idx",
    /**
     * 是否在找不到索引文件时抛出异常。
     * 若为 false，找不到索引时仅返回空结果。
     */
    private val failIfIndexMissing: Boolean = true
) : ResourceProvider {

    // 缓存索引内容
    @Volatile
    private var cachedIndex: List<String>? = null

    override fun getResource(name: String): LoadableResource? {
        // 直接按绝对路径加载（如 db/migration/V1__init.sql）
        val url = classLoader.getResource(name) ?: return null
        // 这里使用 Flyway internal 的 ClassPathResource 简化实现
        return ClassPathResource(null, name, classLoader, encoding)
    }

    override fun getResources(prefix: String, suffixes: Array<out String>): MutableCollection<LoadableResource> {
        val index = loadIndexOrEmpty()
        if (index.isEmpty()) {
            return mutableListOf()
        }

        val matched = index.asSequence()
            .filter { path -> startsWithAndEndsWith(path, prefix, suffixes) }
            .mapNotNull { path ->
                // 再次确认资源存在（native 下只要已被打包，一般能找到）
                val url = classLoader.getResource(path) ?: return@mapNotNull null
                ClassPathResource(null, path, classLoader, encoding) as LoadableResource
            }
            .toList()

        return matched.toMutableList()
    }

    private fun startsWithAndEndsWith(filename: String, prefix: String, suffixes: Array<out String>): Boolean {
        if (!filename.substringAfterLast('/').startsWith(prefix)) {
            return false
        }
        for (suf in suffixes) {
            if (filename.endsWith(suf)) {
                return true
            }
        }
        return false
    }

    private fun loadIndexOrEmpty(): List<String> {
        val cached = cachedIndex
        if (cached != null) {
            return cached
        }
        synchronized(this) {
            val again = cachedIndex
            if (again != null) return again

            val stream = classLoader.getResourceAsStream(indexPath)
            if (stream == null) {
                if (failIfIndexMissing) {
                    throw IllegalStateException(
                        "Resource index not found on classpath: $indexPath. " +
                                "Please generate it at build time and include it in the image."
                    )
                }
                cachedIndex = emptyList()
                return emptyList()
            }

            stream.use { ins ->
                BufferedReader(InputStreamReader(ins, encoding)).use { reader ->
                    val lines = reader.lineSequence()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() && !it.startsWith("#") }
                        .toList()
                    cachedIndex = lines
                    return lines
                }
            }
        }
    }
}