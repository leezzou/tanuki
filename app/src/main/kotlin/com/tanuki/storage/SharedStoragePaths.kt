package com.tanuki.storage

import java.io.File
import java.security.MessageDigest

/** 手机卷的稳定工具路径；这里只生成路径，不授予权限或创建客体挂载。 */
object SharedStoragePaths {
    const val ROOT = "/phone"

    fun guestRoot(hostRoot: File, primaryRoot: File): String {
        require(hostRoot.isAbsolute && primaryRoot.isAbsolute) { "共享存储根必须是绝对路径" }
        val root = hostRoot.canonicalFile
        if (root == primaryRoot.canonicalFile) return "$ROOT/primary"
        val id = MessageDigest.getInstance("SHA-256").digest(root.path.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }.take(16)
        return "$ROOT/volume-$id"
    }

    fun guestPath(hostRoot: File, primaryRoot: File, relative: String = ""): String {
        require(relative.isEmpty() || relative.split('/').all { part ->
            part.isNotEmpty() && part != "." && part != ".." && part.none { it == '\\' || it == '\u0000' }
        }) { "共享存储相对路径无效" }
        return guestRoot(hostRoot, primaryRoot) + if (relative.isEmpty()) "" else "/$relative"
    }
}
