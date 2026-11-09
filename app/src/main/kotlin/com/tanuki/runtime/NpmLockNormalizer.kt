package com.tanuki.runtime

import org.json.JSONObject

/**
 * npm 用国内镜像解析元数据时会把 `resolved` 写成镜像地址。
 * 锁进入安装流程前必须恢复为官方 registry 路径，下载阶段再按白名单映射；
 * 只改 URL，SRI 与依赖图内容保持原样。
 */
internal object NpmLockNormalizer {
    fun normalize(bytes: ByteArray): ByteArray {
        val root = JSONObject(String(bytes, Charsets.UTF_8))
        val packages = root.getJSONObject("packages")
        var changed = false
        packages.keys().forEach { name ->
            val entry = packages.getJSONObject(name)
            if (!entry.has("resolved")) return@forEach
            val resolved = entry.getString("resolved")
            val official = NpmArchiveMapping.toOfficial(resolved)
            if (official != resolved) {
                entry.put("resolved", official)
                changed = true
            }
        }
        return if (changed) root.toString().toByteArray(Charsets.UTF_8) else bytes
    }
}
