package com.tanuki.runtime

import org.json.JSONObject

/**
 * 镜像元数据只能作为候选，不能成为传递依赖 SRI 的信任来源。
 * 在接纳候选锁前，逐包对比官方元数据锁中的版本、官方归档路径与 SHA-512；
 * 任何差异、缺失或多余依赖都拒绝该候选。
 */
internal object NpmLockTrust {
    private val SRI = Regex("sha512-[A-Za-z0-9+/]{86}==")

    fun requireSameArchives(trusted: ByteArray, candidate: ByteArray) {
        val expected = archives(trusted)
        val actual = archives(candidate)
        require(expected.keys == actual.keys) { "镜像锁与官方锁的依赖集合不一致" }
        for ((name, archive) in expected) {
            require(archive == actual[name]) { "镜像锁中 $name 的版本、官方归档路径或 SRI 与官方不一致" }
        }
    }

    private fun archives(bytes: ByteArray): Map<String, Archive> {
        val packages = JSONObject(String(bytes, Charsets.UTF_8)).getJSONObject("packages")
        val result = sortedMapOf<String, Archive>()
        packages.keys().forEach { name ->
            val entry = packages.getJSONObject(name)
            if (!entry.has("resolved")) return@forEach
            val integrity = entry.getString("integrity")
            require(SRI.matches(integrity)) { "依赖 $name 的 SHA-512 格式无效" }
            result[name] = Archive(
                version = entry.getString("version"),
                resolved = NpmArchiveMapping.toOfficial(entry.getString("resolved")),
                integrity = integrity,
            )
        }
        return result
    }

    private data class Archive(val version: String, val resolved: String, val integrity: String)
}
