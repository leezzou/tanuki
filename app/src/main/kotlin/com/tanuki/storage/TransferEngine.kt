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
import java.security.MessageDigest

/** 同名目标的处理方式；批量时由 [ConflictBatch] 统一展开。 */
enum class TransferConflictPolicy { KEEP_BOTH, REPLACE, SKIP }

/** 一次复制或移动的结果；目录按其中文件数计数。 */
data class TransferReceipt(val bytes: Long, val files: Int, val sha256: String?)

/** 目标文件已经完整写入并校验，但删除源文件失败；此时必须保留目标，不能回滚成丢数据。 */
class SourceRemovalException(val target: Path, cause: Throwable) : IOException(
    "已复制并校验到「${target.fileName}」，但删除源文件失败；目标已保留，请检查后手动清理源文件。", cause)

/**
 * 流式复制与校验。
 *
 * 大文件按 64 KiB 分块读写，不把整个文件放进内存；每个文件写完后回读计算 SHA-256，
 * 大小和摘要都一致才视为成功。暂存目录名与列表内部保留前缀一致，取消或失败时由
 * [transferPath] 的 finally 清理，不会留下半成品。
 */
object StreamingTransfer {
    private const val BUFFER_BYTES = 64 * 1024
    private val noFollow = LinkOption.NOFOLLOW_LINKS

    /** 单文件：写入 [target]（要求不存在）后回读校验。 */
    suspend fun copyFileVerified(
        source: InputStream,
        expectedBytes: Long?,
        target: Path,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): TransferReceipt {
        var copied = 0L
        val digest = MessageDigest.getInstance("SHA-256")
        onProgress(0, expectedBytes ?: -1L)
        Files.newOutputStream(target, StandardOpenOption.CREATE_NEW).use { output ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = source.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                output.write(buffer, 0, count)
                digest.update(buffer, 0, count)
                copied += count
                onProgress(copied, expectedBytes ?: -1L)
            }
        }
        force(target)
        if (expectedBytes != null && copied != expectedBytes) {
            throw IOException("文件大小与声明不一致，已取消")
        }
        val verified = digestOf(target)
        if (verified.second != copied || !verified.first.contentEquals(digest.digest())) {
            throw IOException("复制校验失败，目标文件与源文件不一致")
        }
        return TransferReceipt(copied, 1, verified.first.toHex())
    }

    /** 文件或目录：目录递归复制，进度总量为所有普通文件字节数之和。 */
    suspend fun copyPathVerified(
        source: Path,
        target: Path,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): TransferReceipt {
        if (!Files.isDirectory(source, noFollow)) {
            check(Files.isRegularFile(source, noFollow)) { "不支持复制链接或特殊文件" }
            return Files.newInputStream(source, noFollow).use { input ->
                copyFileVerified(input, Files.size(source), target, onProgress)
            }
        }
        val total = measurePath(source)
        var copied = 0L
        var files = 0
        suspend fun walk(from: Path, to: Path) {
            currentCoroutineContext().ensureActive()
            check(!Files.isSymbolicLink(from)) { "目录包含链接，无法安全复制" }
            if (Files.isDirectory(from, noFollow)) {
                Files.createDirectory(to)
                Files.newDirectoryStream(from).use { children ->
                    for (child in children) walk(child, to.resolve(child.fileName.toString()))
                }
            } else {
                check(Files.isRegularFile(from, noFollow)) { "目录包含链接或特殊文件，无法安全复制" }
                val expected = Files.size(from)
                Files.newInputStream(from, noFollow).use { input ->
                    val receipt = copyFileVerified(input, expected, to) { done, _ ->
                        onProgress(copied + done, total)
                    }
                    copied += receipt.bytes
                    files += 1
                }
            }
        }
        walk(source, target)
        onProgress(copied, total)
        return TransferReceipt(copied, files, null)
    }

    /**
     * 复制到同目录暂存、校验后发布；[deleteSource] 为真时只在发布成功后删除源。
     * 取消发生在发布前时只清理暂存，源文件保持原样。
     */
    suspend fun transferPath(
        source: Path,
        target: Path,
        deleteSource: Boolean,
        replace: Boolean = false,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): TransferReceipt {
        val parent = target.parent ?: throw IOException("目标目录无效")
        if (!Files.isDirectory(parent, noFollow)) throw IOException("目标目录不存在")
        if (target.toAbsolutePath().normalize().startsWith(source.toAbsolutePath().normalize())) {
            throw IOException("不能复制到自身或子目录")
        }
        val stage = Files.createTempDirectory(parent, ".workspace-tmp-")
        val staged = stage.resolve("content")
        try {
            val receipt = copyPathVerified(source, staged, onProgress)
            currentCoroutineContext().ensureActive()
            if (Files.exists(target, noFollow)) {
                check(replace) { "同名条目已存在，未覆盖" }
                check(!Files.isDirectory(target, noFollow)) { "同名文件夹不能整体替换，请选择保留两者或跳过" }
                check(!Files.isSymbolicLink(target)) { "同名链接不能替换" }
            }
            // 暂存与目标在同一目录；校验通过后才发布。REPLACE 仅用于普通文件。
            if (replace) Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING)
            else Files.move(staged, target)
            if (deleteSource) {
                // 发布已经完成；即使此刻被取消也把删源收尾，避免留下“复制成功但界面说取消”的重复件。
                try {
                    withContext(NonCancellable) { deleteTree(source) }
                } catch (error: Exception) {
                    throw SourceRemovalException(target, error)
                }
            }
            return receipt
        } finally {
            // 发布成功后 staged 已移走，stage 只是空目录；失败/取消时这里清掉半成品。
            // 清理失败不能掩盖原始异常；残留目录使用内部前缀，不会出现在文件列表里。
            runCatching { deleteTree(stage) }
        }
    }

    /** 统计普通文件总字节数；遇到链接或特殊文件直接拒绝，避免复制到意外内容。 */
    suspend fun measurePath(source: Path): Long {
        if (!Files.isDirectory(source, noFollow)) {
            check(Files.isRegularFile(source, noFollow)) { "不支持复制链接或特殊文件" }
            return Files.size(source)
        }
        var total = 0L
        suspend fun walk(path: Path) {
            currentCoroutineContext().ensureActive()
            check(!Files.isSymbolicLink(path)) { "目录包含链接，无法安全复制" }
            if (Files.isDirectory(path, noFollow)) {
                Files.newDirectoryStream(path).use { children ->
                    for (child in children) walk(child)
                }
            } else {
                check(Files.isRegularFile(path, noFollow)) { "目录包含链接或特殊文件，无法安全复制" }
                total = try { Math.addExact(total, Files.size(path)) } catch (_: ArithmeticException) {
                    throw IOException("目录总大小超出可处理范围")
                }
            }
        }
        walk(source)
        return total
    }

    /** 只删除条目本身和目录内容，不跟随符号链接。 */
    fun deleteTree(path: Path) {
        if (!Files.exists(path, noFollow)) return
        if (Files.isSymbolicLink(path) || !Files.isDirectory(path, noFollow)) {
            Files.deleteIfExists(path)
            return
        }
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

    private fun force(path: Path) {
        FileChannel.open(path, StandardOpenOption.WRITE).use { it.force(true) }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}

/** 批量冲突选择：逐项询问，也可以一次“全部应用”。 */
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
        if (applyToAll) {
            conflictNames.forEach { name ->
                // 文件夹不能整体替换；“全部替换”只作用于普通文件，目录回退为保留两者。
                next[name] = if (policy == TransferConflictPolicy.REPLACE && name !in replaceable) {
                    TransferConflictPolicy.KEEP_BOTH
                } else policy
            }
        } else next[current] = policy
        return next
    }

    fun next(conflictNames: List<String>, decisions: Map<String, TransferConflictPolicy>): String? =
        conflictNames.firstOrNull { it !in decisions }
}
