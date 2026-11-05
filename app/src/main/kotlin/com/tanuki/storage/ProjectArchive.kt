package com.tanuki.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ProjectArchive {
    /** 可在运行中导出；逐文件检查变化，不承诺整个工作区是同一时刻的快照。 */
    suspend fun write(workspaces: File, allowedRoot: File, output: OutputStream,
                      onProgress: (Long) -> Unit = {}) = withContext(Dispatchers.IO) {
        val noFollow = LinkOption.NOFOLLOW_LINKS
        val logicalRoot = workspaces.toPath().toAbsolutePath().normalize()
        check(!Files.isSymbolicLink(logicalRoot)) { "工作区被重定向" }
        val root = logicalRoot.toRealPath()
        val allowed = allowedRoot.toPath().toRealPath()
        check(root.startsWith(allowed) && Files.isDirectory(root, noFollow)) { "工作区不存在或超出允许范围" }
        val excluded = setOf(".env", ".npmrc", ".netrc", "credentials", "id_rsa", "id_ed25519")
        val coroutine = currentCoroutineContext()
        var received = 0L
        fun changed(): Nothing = throw IOException("导出期间项目文件发生变化，请刷新后重试；未生成完整导出")
        fun parents(path: Path) {
            check(path.normalize().startsWith(allowed)) { "导出文件超出允许范围" }
            var current = allowed
            for (part in allowed.relativize(path.parent)) {
                current = current.resolve(part)
                if (!Files.isDirectory(current, noFollow) || Files.isSymbolicLink(current)) changed()
            }
        }
        fun same(before: BasicFileAttributes, after: BasicFileAttributes) =
            before.fileKey() == after.fileKey() && before.size() == after.size() &&
                before.lastModifiedTime() == after.lastModifiedTime() &&
                before.isSymbolicLink == after.isSymbolicLink && before.isDirectory == after.isDirectory
        fun attributes(path: Path) = Files.readAttributes(path, BasicFileAttributes::class.java, noFollow)
        val directories = hashMapOf<Path, BasicFileAttributes>()
        onProgress(0)
        ZipOutputStream(output.buffered()).use { zip ->
            Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
                fun relative(path: Path) = root.relativize(path).joinToString("/")
                fun skip(path: Path) = root.relativize(path).any { part ->
                    part.toString().let { it in excluded || it.startsWith(".env.") || it.startsWith(".import-") }
                }
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    coroutine.ensureActive()
                    if (skip(dir)) return FileVisitResult.SKIP_SUBTREE
                    parents(dir)
                    if (Files.isSymbolicLink(dir)) changed()
                    directories[dir] = attrs
                    if (dir != root) { zip.putNextEntry(ZipEntry(relative(dir) + "/")); zip.closeEntry() }
                    return FileVisitResult.CONTINUE
                }
                override fun visitFile(path: Path, attrs: BasicFileAttributes): FileVisitResult {
                    coroutine.ensureActive()
                    if (skip(path)) return FileVisitResult.CONTINUE
                    parents(path)
                    val source = L2sStore.payload(path, allowed) ?: path.takeIf { Files.isRegularFile(it, noFollow) }
                        ?: throw IOException("项目包含无法导出的符号链接或特殊文件：${relative(path)}；未生成完整导出")
                    parents(source)
                    val before = attributes(source)
                    if (!before.isRegularFile || !same(attrs, attributes(path))) changed()
                    zip.putNextEntry(ZipEntry(relative(path)))
                    Files.newInputStream(source, noFollow).use { input ->
                        var remaining = before.size()
                        val buffer = ByteArray(64 * 1024)
                        while (remaining > 0) {
                            coroutine.ensureActive()
                            val count = input.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
                            if (count < 0) changed()
                            if (count == 0) continue
                            zip.write(buffer, 0, count)
                            remaining -= count; received += count
                            onProgress(received)
                        }
                        if (input.read() != -1) changed()
                    }
                    parents(path); parents(source)
                    if (!same(before, attributes(source)) || !same(attrs, attributes(path))) changed()
                    zip.closeEntry()
                    return FileVisitResult.CONTINUE
                }
                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                    coroutine.ensureActive()
                    if (exc != null) throw exc
                    parents(dir)
                    if (!same(directories.remove(dir)!!, attributes(dir))) changed()
                    return FileVisitResult.CONTINUE
                }
                override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = throw exc
            })
            coroutine.ensureActive()
        }
    }
}
