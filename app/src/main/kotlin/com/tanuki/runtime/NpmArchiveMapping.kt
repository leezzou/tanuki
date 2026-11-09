package com.tanuki.runtime

import java.net.URI

/**
 * npm 官方归档地址与国内镜像之间的唯一映射规则。
 *
 * 锁文件始终保存官方 registry 路径；下载时才把同一路径拼到白名单镜像。
 * 反向归一化用于把镜像元数据生成的锁恢复成官方锁，SRI 始终来自原始锁，不由镜像提供。
 */
internal object NpmArchiveMapping {
    const val OFFICIAL_REGISTRY = "https://registry.npmjs.org"

    val MIRROR_REGISTRIES = listOf(
        "https://registry.npmmirror.com",
        "https://repo.huaweicloud.com/repository/npm",
        "https://mirrors.huaweicloud.com/repository/npm",
        "https://mirrors.cloud.tencent.com/npm",
    )

    private val PATH_SEGMENT = Regex("""[A-Za-z0-9@._~!$&'()*+,;=:-]+""")

    fun isKnownRegistry(registry: String): Boolean {
        val normalized = registry.trimEnd('/')
        return normalized == OFFICIAL_REGISTRY || MIRROR_REGISTRIES.any { it.trimEnd('/') == normalized }
    }

    /** 只接受官方 registry 下的规范 HTTPS tgz 路径，拒绝任意 host、参数、编码穿越和目录跳转。 */
    fun officialPath(raw: String): String {
        val uri = parse(raw, "npm 归档地址无效")
        require(uri.scheme == "https" && uri.host?.lowercase() == "registry.npmjs.org" && uri.port == -1 &&
            uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
            uri.rawPath.startsWith("/") && uri.rawPath.endsWith(".tgz")) {
            "npm 归档地址必须来自官方锁文件"
        }
        return safePath(uri.rawPath)
    }

    fun urlFor(registry: String, officialUrl: String): String {
        require(isKnownRegistry(registry)) { "未知 npm 镜像" }
        return registry.trimEnd('/') + officialPath(officialUrl)
    }

    /** 把白名单镜像的锁地址还原为官方地址；未知 host 或非法路径直接拒绝。 */
    fun toOfficial(raw: String): String {
        val uri = parse(raw, "npm 锁归档地址无效")
        require(uri.scheme == "https" && uri.port == -1 && uri.rawUserInfo == null &&
            uri.rawQuery == null && uri.rawFragment == null) {
            "npm 锁包含非 HTTPS 或带参数的归档地址"
        }
        val (_, prefix) = registryFor(uri)
            ?: throw IllegalArgumentException("npm 锁归档地址不在镜像白名单：${uri.host ?: "未知"}")
        return OFFICIAL_REGISTRY + safePath(uri.rawPath.removePrefix(prefix))
    }

    private fun registryFor(uri: URI): Pair<String, String>? {
        val host = uri.host?.lowercase() ?: return null
        return (MIRROR_REGISTRIES + OFFICIAL_REGISTRY).firstNotNullOfOrNull { registry ->
            val base = runCatching { URI(registry) }.getOrNull() ?: return@firstNotNullOfOrNull null
            if (base.host?.lowercase() != host) return@firstNotNullOfOrNull null
            val prefix = base.rawPath.trimEnd('/')
            if (!uri.rawPath.startsWith("$prefix/")) return@firstNotNullOfOrNull null
            registry to prefix
        }
    }

    private fun parse(raw: String, invalid: String): URI =
        try { URI(raw) } catch (_: Exception) { throw IllegalArgumentException(invalid) }

    private fun safePath(path: String): String {
        require(path.startsWith("/") && path.endsWith(".tgz") && path.length <= 2048) { "npm 归档路径无效" }
        require(!path.contains('\\') && path.none { it < ' ' || it == '\u007f' || it == '%' }) {
            "npm 归档路径包含不安全字符"
        }
        val segments = path.removePrefix("/").removeSuffix(".tgz").split('/')
        require(segments.isNotEmpty() && segments.all {
            it.isNotEmpty() && it != "." && it != ".." && PATH_SEGMENT.matches(it)
        }) { "npm 归档路径包含非法段" }
        return path
    }
}
