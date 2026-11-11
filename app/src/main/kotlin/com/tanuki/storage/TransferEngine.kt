package com.tanuki.storage

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.nio.channels.FileChannel
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.security.MessageDigest

/** 同名文件处理方式；未明确选择替换时，默认保留两者。 */
enum class TransferConflictPolicy { KEEP_BOTH, REPLACE, SKIP }

/** 一次复制或移动的结果；目录按其中文件数计数。 */
data class TransferReceipt(val bytes: Long, val files: Int, val sha256: String?)

/** 已发布的校验副本必须保留，不能因删源失败而回滚目标。 */
class SourceRemovalException(val target: Path, cause: Throwable) : IOException(
    "已复制并校验到「${target.fileName}」，但删除源文件未完成；目标已保留，请检查源文件。", cause)

/** 按 64 KiB 流式复制，在同目录暂存中完成校验后发布；不整文件读入内存。 */
object StreamingTransfer {
    private const val BUFFER_BYTES = 64 * 1024
    private const val MAX_DEPTH = 128
    private val noFollow = LinkOption.NOFOLLOW_LINKS

    private data class Stamp(
        val directory: Boolean,
        val bytes: Long,
        val modified: FileTime,
        val key: Any?,
    )
    private data class CopiedTree(
        val original: Map<String, Stamp>,
        val hashes: Map<String, String>,
        val receipt: TransferReceipt,
    )

    /** 要求目标不存在；读写、长度或摘要校验失败时仅删除自己创建的目标。 */
    suspend fun copyFileVerified(
        source: InputStream,
        expectedBytes: Long?,
        target: Path,
        beforeWrite: (Int) -> Unit = {},
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): TransferReceipt {
        var copied = 0L
        var owned = false
        var failure: Throwable? = null
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            onProgress(0, expectedBytes ?: -1L)
            Files.newOutputStream(target, StandardOpenOption.CREATE_NEW).use { output ->
                owned = true
                val buffer = ByteArray(BUFFER_BYTES)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = source.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    if (expectedBytes != null && count.toLong() > expectedBytes - copied) {
                        throw IOException("源文件在读取期间变大，已取消复制")
                    }
                    beforeWrite(count)
                    output.write(buffer, 0, count)
                    digest.update(buffer, 0, count)
                    copied = Math.addExact(copied, count.toLong())
                    onProgress(copied, expectedBytes ?: -1L)
                }
            }
            force(target)
            check(expectedBytes == null || copied == expectedBytes) { "源文件在读取期间变小，已取消复制" }
            val verified = digestOf(target)
            check(verified.second == copied && verified.first.contentEquals(digest.digest())) {
                "复制校验失败，目标文件与源文件不一致"
            }
            return TransferReceipt(copied, 1, verified.first.toHex())
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            if (owned && failure != null) {
                try { Files.deleteIfExists(target) }
                catch (cleanup: Exception) { failure.addSuppressed(cleanup) }
            }
        }
    }

    suspend fun copyPathVerified(
        source: Path,
        target: Path,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): TransferReceipt = copyTree(source, target, onProgress).receipt

    private suspend fun copyTree(
        source: Path,
        target: Path,
        onProgress: (Long, Long) -> Unit,
    ): CopiedTree {
        val original = snapshot(source)
        val total = totalBytes(original)
        var copied = 0L
        val hashes = linkedMapOf<String, String>()
        onProgress(0, total)
        for ((relative, before) in original) {
            currentCoroutineContext().ensureActive()
            val from = child(source, relative)
            val to = child(target, relative)
            requireSame(before, stamp(from))
            if (before.directory) {
                Files.createDirectory(to)
            } else {
                val receipt = Files.newInputStream(from, noFollow).use { input ->
                    copyFileVerified(input, before.bytes, to) { done, _ -> onProgress(copied + done, total) }
                }
                requireSame(before, stamp(from))
                hashes[relative] = checkNotNull(receipt.sha256)
                copied += receipt.bytes
            }
        }
        check(snapshot(source) == original) { "源目录在复制期间发生变化，已取消；源文件已保留" }
        return CopiedTree(original, hashes, TransferReceipt(copied, hashes.size, hashes[""]))
    }

    /**
     * 同目录暂存→流式复制并回读校验→容量/权限复核→发布→删除已校验源。
     * 发布前的取消、源变化或容量拒绝都清理暂存，保留原源与旧目标。
     */
    suspend fun transferPath(
        source: Path,
        target: Path,
        deleteSource: Boolean,
        replace: Boolean = false,
        beforePublish: suspend (Path) -> Unit = {},
        beforeDelete: () -> Unit = {},
        onPhase: (String) -> Unit = {},
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): TransferReceipt {
        val parent = target.parent ?: throw IOException("目标目录无效")
        if (!Files.isDirectory(parent, noFollow) || Files.isSymbolicLink(parent)) throw IOException("目标目录不存在或是链接")
        if (target.toAbsolutePath().normalize().startsWith(source.toAbsolutePath().normalize())) {
            throw IOException("不能复制到自身或子目录")
        }
        val previous = if (Files.exists(target, noFollow)) stamp(target) else null
        if (previous != null) {
            check(replace) { "同名条目已存在，未覆盖" }
            check(!previous.directory && Files.isRegularFile(source, noFollow)) {
                "只能替换同名普通文件；文件夹请选择保留两者或跳过"
            }
        }
        val previousHash = if (previous != null) digestOf(target).first.toHex() else null
        val stage = Files.createTempDirectory(parent, ".workspace-tmp-")
        val staged = stage.resolve("content")
        var failure: Throwable? = null
        try {
            onPhase("正在复制并校验")
            val copied = copyTree(source, staged, onProgress)
            onPhase("正在核对源文件与容量")
            beforePublish(staged)
            check(snapshot(source) == copied.original) { "源目录或文件已变化，源文件未删除" }
            // 移动前重新读取源摘要，能识别同大小、同时间戳的并发改写。
            if (deleteSource) for ((relative, hash) in copied.hashes) {
                check(digestOf(child(source, relative)).first.toHex() == hash) { "源文件内容已变化，源文件未删除" }
            }
            check(snapshot(source) == copied.original) { "源文件已变化，源文件未删除" }
            if (deleteSource) beforeDelete()
            if (previous == null) {
                check(!Files.exists(target, noFollow)) { "目标出现新的同名条目，未覆盖；请重试" }
            } else {
                requireSame(previous, stamp(target))
                check(digestOf(target).first.toHex() == previousHash) { "目标文件已被修改，未覆盖" }
            }
            onPhase(if (deleteSource) "正在提交并清理源文件" else "正在提交")
            currentCoroutineContext().ensureActive()
            // 发布是提交点：此后仅收尾已校验条目，不能把已完成项报告成未复制。
            withContext(NonCancellable) {
                if (previous != null) Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                else Files.move(staged, target)
                if (deleteSource) try {
                    removeVerifiedSource(source, copied.original)
                } catch (error: Exception) {
                    throw SourceRemovalException(target, error)
                }
            }
            return copied.receipt
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            try { deleteTree(stage) }
            catch (cleanup: Exception) {
                if (failure != null) failure.addSuppressed(cleanup)
                else throw IOException("传输完成，但临时目录清理失败，请检查目标目录", cleanup)
            }
        }
    }

    /** 只删本次清单中的文件，再逐层删除空目录；后来新增的文件不会被递归扫掉。 */
    private fun removeVerifiedSource(source: Path, original: Map<String, Stamp>) {
        for ((relative, before) in original.entries.filter { !it.value.directory }) {
            val path = child(source, relative)
            requireSame(before, stamp(path))
            Files.delete(path)
        }
        for ((relative, before) in original.entries.filter { it.value.directory }.asReversed()) {
            val path = child(source, relative)
            val current = stamp(path)
            check(current.directory && current.key == before.key) { "源目录已被替换，停止清理；目标已保留" }
            Files.delete(path)
        }
    }

    /** 只保存路径、大小、时间与文件标识；清单不包含任何文件正文。 */
    private suspend fun snapshot(source: Path): Map<String, Stamp> {
        val result = linkedMapOf<String, Stamp>()
        suspend fun walk(path: Path, depth: Int) {
            currentCoroutineContext().ensureActive()
            check(depth <= MAX_DEPTH) { "目录层级超过 128 层，无法安全复制" }
            val item = stamp(path)
            result[source.relativize(path).toString()] = item
            if (item.directory) Files.newDirectoryStream(path).use { children ->
                for (entry in children.sortedBy { it.fileName.toString() }) walk(entry, depth + 1)
            }
        }
        walk(source, 0)
        return result
    }

    private fun stamp(path: Path): Stamp {
        val attrs = Files.readAttributes(path, BasicFileAttributes::class.java, noFollow)
        check(!attrs.isSymbolicLink && (attrs.isRegularFile || attrs.isDirectory)) { "不支持复制链接或特殊文件" }
        return Stamp(attrs.isDirectory, attrs.size(), attrs.lastModifiedTime(), attrs.fileKey())
    }

    private fun requireSame(before: Stamp, after: Stamp) {
        check(before == after) { "文件在复制期间被修改或替换，已停止操作" }
    }

    private fun child(root: Path, relative: String): Path = if (relative.isEmpty()) root else root.resolve(relative)
    private fun totalBytes(items: Map<String, Stamp>): Long = items.values.filter { !it.directory }
        .fold(0L) { total, item -> Math.addExact(total, item.bytes) }

    suspend fun measurePath(source: Path): Long = totalBytes(snapshot(source))

    /** 只清理调用方已确认归本次事务所有的暂存目录，不跟随链接。 */
    fun deleteTree(path: Path) {
        if (!Files.exists(path, noFollow)) return
        Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.delete(file)
                return FileVisitResult.CONTINUE
            }
            override fun postVisitDirectory(dir: Path, error: IOException?): FileVisitResult {
                if (error != null) throw error
                Files.delete(dir)
                return FileVisitResult.CONTINUE
            }
        })
    }

    private suspend fun digestOf(path: Path): Pair<ByteArray, Long> {
        val digest = MessageDigest.getInstance("SHA-256")
        var bytes = 0L
        Files.newInputStream(path, noFollow).use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                digest.update(buffer, 0, count)
                bytes += count
            }
        }
        return digest.digest() to bytes
    }

    private fun force(path: Path) = FileChannel.open(path, StandardOpenOption.WRITE).use { it.force(true) }
    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}

/** “全部应用”仅决定尚未处理的冲突，不回改用户已单独处理的项目。 */
object ConflictBatch {
    fun apply(
        decisions: Map<String, TransferConflictPolicy>,
        conflictNames: List<String>,
        current: String,
        policy: TransferConflictPolicy,
        applyToAll: Boolean,
        replaceable: Set<String> = conflictNames.toSet(),
    ): Map<String, TransferConflictPolicy> {
        require(current in conflictNames) { "冲突项不在当前批次" }
        val next = decisions.toMutableMap()
        val affected = if (applyToAll) conflictNames.filter { it !in decisions } else listOf(current)
        affected.forEach { name ->
            next[name] = if (policy == TransferConflictPolicy.REPLACE && name !in replaceable) {
                TransferConflictPolicy.KEEP_BOTH
            } else policy
        }
        return next
    }
    fun next(conflictNames: List<String>, decisions: Map<String, TransferConflictPolicy>): String? =
        conflictNames.firstOrNull { it !in decisions }
}
