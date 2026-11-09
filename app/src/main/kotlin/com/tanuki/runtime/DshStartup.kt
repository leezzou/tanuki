package com.tanuki.runtime

import android.content.Context
import android.system.Os
import android.util.AtomicFile
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

/**
 * DSH 启动期只读路径缓存的宿主侧准备。
 *
 * shim 只从 APK 资源复制到应用私有目录，再以只读绑定挂入客体；不改官方 npm 包。
 * 任何准备失败都返回 null，调用方按原生方式启动，不阻塞 DSH。
 */
object DshStartup {
    const val GUEST_DIR = "/opt/tanuki-startup"
    const val SCRIPT_NAME = "path-cache.cjs"
    private const val ASSET = "dsh-startup/$SCRIPT_NAME"

    fun directory(paths: Paths) = File(paths.base, "dsh-startup-tools")

    @Synchronized
    fun prepare(context: Context, paths: Paths): File? = runCatching {
        val tools = directory(paths)
        check(!Files.isSymbolicLink(tools.toPath())) { "DSH 启动支持目录被重定向" }
        Files.createDirectories(tools.toPath())
        check(Files.isDirectory(tools.toPath(), LinkOption.NOFOLLOW_LINKS)) { "DSH 启动支持路径不是目录" }
        Os.chmod(tools.absolutePath, 448)
        val script = File(tools, SCRIPT_NAME)
        check(!Files.isSymbolicLink(script.toPath())) { "DSH 启动缓存脚本被重定向" }
        val text = context.assets.open(ASSET).bufferedReader().use { it.readText() }
        if (!script.isFile || script.readText() != text) atomicWrite(script, text)
        check(script.isFile && script.length() > 0) { "DSH 启动缓存脚本为空" }
        script
    }.onFailure {
        android.util.Log.w("TANUKI_RUNTIME", "DSH startup path cache unavailable: ${it.message}")
    }.getOrNull()

    private fun atomicWrite(file: File, text: String) {
        check(listOf(file, File(file.path + ".bak"), File(file.path + ".new")).none { Files.isSymbolicLink(it.toPath()) }) {
            "DSH 启动缓存暂存路径被重定向"
        }
        val atomic = AtomicFile(file)
        val output = atomic.startWrite()
        try {
            // 与浏览器工具一致：只允许应用自身读取，客体经 PRoot root 映射访问。
            Os.fchmod(output.fd, 384)
            output.write(text.toByteArray(Charsets.UTF_8))
            atomic.finishWrite(output)
        } catch (failure: Throwable) {
            atomic.failWrite(output)
            throw failure
        }
    }
}
