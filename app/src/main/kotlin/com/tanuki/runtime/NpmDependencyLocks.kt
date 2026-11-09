package com.tanuki.runtime

import kotlinx.coroutines.CancellationException
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest
import java.util.Base64

/**
 * 缓存只绑定发布版本、SRI 与目标平台；Node/npm 升级不再让已保存的锁失效。
 * v3 只接纳经官方元数据核验的锁；旧缓存没有可信来源标记，不能继续快速复用。
 * 锁是否仍适用于当前运行时由每次安装的依赖图检查决定，而不是缓存键猜测。
 */
internal data class NpmLockIdentity(val version: String, val integrity: String) {
    init {
        require(VERSION.matches(version)) { "依赖锁版本身份无效" }
        require(SRI.matches(integrity) && Base64.getDecoder().decode(integrity.removePrefix("sha512-")).size == 64 &&
            "sha512-" + Base64.getEncoder().encodeToString(Base64.getDecoder().decode(integrity.removePrefix("sha512-"))) == integrity) {
            "依赖锁必须绑定规范的 SHA-512 校验"
        }
    }
    val key: String = npmLockSha256(listOf("tanuki-npm-lock-v3", NpmArchiveMapping.OFFICIAL_REGISTRY, "linux-arm64-glibc",
        version, integrity).joinToString("\n").toByteArray(Charsets.UTF_8))

    companion object {
        private val VERSION = Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[0-9A-Za-z.-]+)?")
        private val SRI = Regex("sha512-[A-Za-z0-9+/]{86}==")
    }
}

internal fun npmLockSha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
    .joinToString("") { "%02x".format(it.toInt() and 255) }

/** 只保存经官方解析及依赖图检查的公开锁；单文件提交避免混用两次写入的内容。 */
internal class NpmLockCache(private val base: File) {
    private val directory = File(base, "downloads/npm-locks")
    private fun file(identity: NpmLockIdentity) = File(directory, "${identity.key}.lock")

    private fun owned(file: File) {
        val root = base.absoluteFile.toPath().normalize()
        var cursor = file.absoluteFile.toPath().normalize()
        check(cursor.startsWith(root)) { "依赖锁缓存路径越界" }
        while (true) {
            check(!Files.isSymbolicLink(cursor)) { "依赖锁缓存路径被重定向，已保留原文件" }
            if (cursor == root) break
            cursor = cursor.parent ?: error("依赖锁缓存路径越界")
        }
    }

    fun load(identity: NpmLockIdentity): ByteArray? {
        val target = file(identity)
        return try {
            owned(target)
            if (!Files.isRegularFile(target.toPath(), NOFOLLOW_LINKS) || target.length() !in 1..MAX_LOCK_BYTES + 1024L) return null
            DataInputStream(FileInputStream(target)).use { input ->
                require(input.readUTF() == MAGIC && input.readUTF() == identity.key)
                val length = input.readInt()
                require(length in 1..MAX_LOCK_BYTES)
                val bytes = ByteArray(length).also(input::readFully)
                require(input.readUTF() == npmLockSha256(bytes) && input.read() == -1)
                bytes
            }
        } catch (_: IOException) { null }
        catch (_: IllegalArgumentException) { null }
        // 缓存路径被符号链接重定向时只放弃该缓存，绝不回退到跟随链接读取。
        catch (_: IllegalStateException) { null }
    }

    fun store(identity: NpmLockIdentity, bytes: ByteArray) {
        require(bytes.size in 1..MAX_LOCK_BYTES) { "依赖锁缓存大小无效" }
        val target = file(identity)
        owned(target)
        Files.createDirectories(directory.toPath())
        owned(target)
        val temporary = Files.createTempFile(directory.toPath(), "lock-", ".pending")
        try {
            FileOutputStream(temporary.toFile()).use { output ->
                val record = DataOutputStream(output)
                record.writeUTF(MAGIC)
                record.writeUTF(identity.key)
                record.writeInt(bytes.size)
                record.write(bytes)
                record.writeUTF(npmLockSha256(bytes))
                record.flush()
                output.fd.sync()
            }
            owned(target)
            try { Files.move(temporary, target.toPath(), ATOMIC_MOVE, REPLACE_EXISTING) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(temporary, target.toPath(), REPLACE_EXISTING) }
        } finally { Files.deleteIfExists(temporary) }
    }

    companion object {
        const val MAX_LOCK_BYTES = 16 * 1024 * 1024
        private const val MAGIC = "Tanuki official npm dependency lock v2"
    }
}

internal enum class NpmLockOrigin { SAVED, INSTALLED, BUNDLED, OFFICIAL }
internal data class NpmLockCandidate(val bytes: ByteArray, val origin: NpmLockOrigin)

/** 下载开始前提交已验证的锁，后续失败或重建候选目录都不会丢失解析成果。 */
internal class NpmDependencyLocks(private val cache: NpmLockCache) {
    /**
     * 快速路径：saved/installed/bundled 任一锁通过依赖图校验就直接安装，不请求官方元数据。
     * 候选锁损坏或与当前平台不兼容时继续尝试下一份；全部不可用才解析元数据。
     */
    suspend fun prepare(identity: NpmLockIdentity, reuse: suspend () -> List<NpmLockCandidate>,
                        resolveOfficial: suspend () -> ByteArray, verify: suspend (ByteArray) -> Unit,
                        onOrigin: (NpmLockOrigin) -> Unit = {}, onCacheError: (IOException) -> Unit = {},
                        onRejected: (NpmLockOrigin, Exception) -> Unit = { _, _ -> }): NpmLockOrigin {
        val candidates = ArrayList<NpmLockCandidate>(4)
        cache.load(identity)?.let { candidates += NpmLockCandidate(it, NpmLockOrigin.SAVED) }
        candidates += reuse()
        val seen = HashSet<String>()
        for (candidate in candidates) {
            if (!seen.add(npmLockSha256(candidate.bytes))) continue
            try {
                require(candidate.bytes.size in 1..NpmLockCache.MAX_LOCK_BYTES) { "依赖锁大小无效" }
                // 命中任何锁都重新检查根依赖、平台、引擎和完整依赖图，不能绕过安装校验。
                verify(candidate.bytes)
                onOrigin(candidate.origin)
                if (candidate.origin != NpmLockOrigin.SAVED) {
                    try { cache.store(identity, candidate.bytes) }
                    catch (error: Exception) {
                        if (error is CancellationException) throw error
                        onCacheError(IOException("依赖锁缓存写入失败", error))
                    }
                }
                return candidate.origin
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { onRejected(candidate.origin, error) }
        }
        onOrigin(NpmLockOrigin.OFFICIAL)
        val bytes = resolveOfficial()
        require(bytes.size in 1..NpmLockCache.MAX_LOCK_BYTES) { "依赖锁大小无效" }
        verify(bytes)
        try { cache.store(identity, bytes) }
        catch (error: Exception) {
            if (error is CancellationException) throw error
            onCacheError(IOException("依赖锁缓存写入失败", error))
        }
        return NpmLockOrigin.OFFICIAL
    }
}

/** 预置锁来自官方 HTTPS registry，完整内容由 APK 中的摘要绑定，不向镜像索取校验值。 */
internal object BundledDshLock {
    const val VERSION = "0.2.1-rc.5"
    const val INTEGRITY = "sha512-WkyJZv4zl9sCAxSzRctGm58m0rilGqwR6pniBiLOz5CPDfHfDuAUicg0AAazXI7oj/Cph6/cjNTncA/rpLTXpw=="
    const val SHA256 = "f1167bea5a45cbd179edb630ef58f2b7aa8c9512b1aaab8268fbd180bc7c6bcb"
    const val ASSET = "dsh-locks/$VERSION.json"

    fun load(version: String, integrity: String, read: () -> ByteArray): ByteArray? {
        if (version != VERSION || integrity != INTEGRITY) return null
        val bytes = read()
        check(bytes.size in 1..NpmLockCache.MAX_LOCK_BYTES && npmLockSha256(bytes) == SHA256) { "APK 预置 DSH 依赖锁校验失败" }
        return bytes
    }
}
