package com.coooolfan.xiaomialbumsyncer.config.flyway

import com.coooolfan.xiaomialbumsyncer.config.flyway.DatabaseMigration.Companion.MIGRATION_SQL_PATTERN_IN_NATIVE
import org.flywaydb.core.api.ResourceProvider
import org.flywaydb.core.api.resource.LoadableResource
import org.flywaydb.core.internal.resource.classpath.ClassPathResource
import org.noear.solon.core.util.ResourceUtil
import org.slf4j.LoggerFactory
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
) : ResourceProvider {

    private val log = LoggerFactory.getLogger(IndexedResourceProvider::class.java)

    // 缓存索引内容
    @Volatile
    private var cachedIndex: List<String>? = null

    override fun getResource(name: String): LoadableResource? {
        // 直接按绝对路径加载（如 db/migration/V1__init.sql）
        classLoader.getResource(name) ?: return null
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
                classLoader.getResource(path) ?: return@mapNotNull null
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

            // 由 Solon AOT 在构建期生成的资源索引文件
            return ResourceUtil.scanResources(MIGRATION_SQL_PATTERN_IN_NATIVE).toList()
        }
    }
}