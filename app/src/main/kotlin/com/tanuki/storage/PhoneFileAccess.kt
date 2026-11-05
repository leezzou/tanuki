package com.tanuki.storage

import android.content.Context
import android.os.SystemClock
import android.system.Os
import android.util.Base64
import com.tanuki.runtime.Paths
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

/** 手机文件只经受控请求访问，不把真实共享卷暴露给客体文件系统；写权限只能由原生界面授予。 */
class PhoneFileAccess(private val context: Context, private val paths: Paths) : AutoCloseable {
    companion object { const val CHUNK_BYTES = 196_608 }
    private val policyLock = Any()
    private val operation = Mutex()
    // 仅在operation锁内绑定当前请求；每次检查文件权限时也核对其所属连接。
    private var requestIsCurrent: () -> Boolean = { true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val allowed = MutableStateFlow(false)
    val writeAllowed: StateFlow<Boolean> = allowed.asStateFlow()
    @Volatile private var epoch = 0L
    @Volatile private var closed = false
    private val uploads = linkedMapOf<String, Upload>()
    private val revisions = linkedMapOf<String, Pair<String, String>>()
    private var stagingPrepared = false
    private data class Upload(val id: String, val path: String, val file: Path, val size: Long,
                              val originalRevision: String?, val epoch: Long, var received: Long = 0,
                              var touched: Long = SystemClock.elapsedRealtime())
    private data class Resolved(val path: Path, val root: Path, val rootPath: String, val parts: List<String>)

    fun setWriteAllowed(enabled: Boolean) {
        synchronized(policyLock) {
            if (enabled && (closed || !PhoneStorageAccess.isWriteGranted(context))) fail("storage_write_permission_required")
            if (allowed.value != enabled || !enabled) { epoch++; allowed.value = enabled }
        }
        if (!enabled) scope.launch { operation.withLock { cleanUploads() } }
    }

    fun revoke() = setWriteAllowed(false)

    override fun close() {
        synchronized(policyLock) { closed = true; epoch++; allowed.value = false }
        scope.launch { operation.withLock { cleanUploads(); revisions.clear() }; scope.cancel() }
    }

    suspend fun execute(method: String, params: JSONObject, isCurrent: () -> Boolean = { true },
                        onProgress: (Long, Long) -> Unit = { _, _ -> }): JSONObject =
        withContext(Dispatchers.IO) {
            operation.withLock {
                if (closed) fail("connection_closed")
                if (!isCurrent()) fail("connection_expired")
                requestIsCurrent = isCurrent
                try {
                    expireUploads()
                    when (method) {
                        "status" -> status()
                        "list" -> list(params)
                        "stat" -> stat(params, onProgress)
                        "read" -> read(params, onProgress)
                        "write_begin" -> begin(params, onProgress)
                        "write_chunk" -> chunk(params)
                        "write_commit" -> commit(params, onProgress)
                        "write_cancel" -> cancelUpload(params)
                        "mkdir" -> mkdir(params)
                        "rename" -> rename(params, onProgress)
                        "delete" -> delete(params, onProgress)
                        else -> fail("unknown_method")
                    }
                } finally {
                    requestIsCurrent = { true }
                }
            }
        }

    private fun status(): JSONObject {
        val volumes = volumes()
        val labels = PhoneStorageAccess.roots(context).entries.associate { it.value.canonicalFile to it.key }
        return JSONObject().put("readAllowed", PhoneStorageAccess.isGranted(context))
            .put("writeAllowed", allowed.value && PhoneStorageAccess.isWriteGranted(context))
            .put("roots", JSONArray().apply { volumes.forEach { (path, file) ->
                put(JSONObject().put("id", path.substringAfterLast('/')).put("label", labels[file] ?: file.name).put("path", path))
            } })
    }

    private fun volumes(): Map<String, File> {
        if (!PhoneStorageAccess.isGranted(context)) {
            if (allowed.value) revoke()
            return emptyMap()
        }
        return PhoneStorageAccess.toolRoots(context)
    }

    private fun ensureRead() {
        if (!requestIsCurrent()) fail("connection_expired")
        if (closed) fail("connection_closed")
        if (!PhoneStorageAccess.isGranted(context)) { revoke(); fail("storage_permission_required") }
    }

    private fun ensureWrite(expectedEpoch: Long = epoch) {
        ensureRead()
        if (!allowed.value) fail("write_permission_required")
        if (epoch != expectedEpoch) fail("permission_revoked")
        if (!PhoneStorageAccess.isWriteGranted(context)) { revoke(); fail("storage_write_permission_required") }
    }

    private fun resolve(logical: String, missing: Boolean = false): Resolved {
        ensureRead()
        if (!logical.startsWith(SharedStoragePaths.ROOT + "/")) fail("invalid_path")
        val parts = logical.removePrefix(SharedStoragePaths.ROOT + "/").split('/')
        if (parts.any { it.isEmpty() || it == "." || it == ".." || it.any { c -> c == '\\' || c == '\u0000' } }) fail("invalid_path")
        val rootPath = SharedStoragePaths.ROOT + "/" + parts.first()
        val root = volumes()[rootPath]?.toPath() ?: fail("storage_volume_unavailable")
        val relative = parts.drop(1)
        // Android 的应用专属外部目录不属于此工具的共享文件范围，旧系统也明确拒绝。
        if (relative.size >= 2 && relative[0].equals("Android", ignoreCase = true) && relative[1].lowercase(java.util.Locale.ROOT) in setOf("data", "obb", "sandbox")) fail("private_directory_denied")
        var current = root
        if (!Files.isDirectory(root, NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) fail("storage_volume_unavailable")
        for ((index, part) in relative.withIndex()) {
            current = current.resolve(part)
            val last = index == relative.lastIndex
            if (!Files.exists(current, NOFOLLOW_LINKS)) {
                if (missing && last) continue
                fail("file_not_found")
            }
            val attrs = Files.readAttributes(current, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
            if (attrs.isSymbolicLink || (!last && !attrs.isDirectory)) fail("symlink_not_allowed")
            if (last && !attrs.isRegularFile && !attrs.isDirectory) fail("unsupported_file_type")
        }
        return Resolved(current, root, rootPath, relative)
    }

    private fun requiredPath(params: JSONObject, name: String = "path"): String =
        params.optString(name).takeIf { it.isNotEmpty() } ?: fail("invalid_path")

    private fun attrs(path: Path) = Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
    private fun fingerprint(path: Path): String {
        val stat = attrs(path)
        if (stat.isSymbolicLink) fail("symlink_not_allowed")
        return "${stat.fileKey()}:${stat.size()}:${stat.lastModifiedTime().to(TimeUnit.NANOSECONDS)}:${stat.isDirectory}"
    }

    private suspend fun revision(path: Path, force: Boolean = false, writeEpoch: Long? = null,
                                 progress: (Long, Long) -> Unit = { _, _ -> }): String {
        val before = fingerprint(path)
        if (!force) revisions[path.toString()]?.takeIf { it.first == before }?.let { return it.second }
        val hash = MessageDigest.getInstance("SHA-256")
        if (Files.isDirectory(path, NOFOLLOW_LINKS)) hash.update(before.toByteArray())
        else Files.newInputStream(path, READ, NOFOLLOW_LINKS).use { input ->
            val total = attrs(path).size()
            var count = 0L
            val buffer = ByteArray(64 * 1024)
            progress(count, total)
            while (true) {
                currentCoroutineContext().ensureActive()
                if (writeEpoch == null) ensureRead() else ensureWrite(writeEpoch)
                val n = input.read(buffer)
                if (n < 0) break
                hash.update(buffer, 0, n); count += n; progress(count, total)
                if (count > total) fail("file_changed")
            }
        }
        if (writeEpoch == null) ensureRead() else ensureWrite(writeEpoch)
        if (fingerprint(path) != before) fail("file_changed")
        val value = "sha256:" + hash.digest().joinToString("") { "%02x".format(it) }
        revisions[path.toString()] = before to value
        // 这是哈希缓存容量，不限制目录或可访问文件的数量。
        while (revisions.size > 128) revisions.remove(revisions.keys.first())
        return value
    }

    private suspend fun stat(params: JSONObject, progress: (Long, Long) -> Unit): JSONObject {
        val resolved = resolve(requiredPath(params))
        val stat = attrs(resolved.path)
        val version = revision(resolved.path, progress = progress)
        if (resolve(requiredPath(params)).path != resolved.path) fail("file_changed")
        return JSONObject().put("path", requiredPath(params)).put("directory", stat.isDirectory)
            .put("size", if (stat.isDirectory) 0 else stat.size()).put("modified", stat.lastModifiedTime().toMillis())
            .put("revision", version)
    }

    private suspend fun list(params: JSONObject): JSONObject {
        ensureRead()
        val logical = params.optString("path", SharedStoragePaths.ROOT)
        val offset = params.optLong("offset", 0)
        val limit = params.optInt("limit", 200)
        if (offset < 0 || offset > Int.MAX_VALUE || limit !in 1..200) fail("invalid_pagination")
        val entries = arrayListOf<JSONObject>()
        if (logical == SharedStoragePaths.ROOT) {
            volumes().forEach { (path, _) -> entries += JSONObject().put("name", path.substringAfterLast('/')).put("path", path)
                .put("directory", true).put("size", 0).put("modified", 0) }
        } else {
            val resolved = resolve(logical)
            if (!Files.isDirectory(resolved.path, NOFOLLOW_LINKS)) fail("not_a_directory")
            Files.newDirectoryStream(resolved.path).use { children -> for (file in children) {
                currentCoroutineContext().ensureActive(); ensureRead()
                val stat = try { attrs(file) } catch (_: java.nio.file.NoSuchFileException) { continue }
                if (stat.isSymbolicLink || (!stat.isDirectory && !stat.isRegularFile)) continue
                val path = logical + "/" + file.fileName
                if (resolved.parts.size == 1 && resolved.parts[0].equals("Android", ignoreCase = true) &&
                    file.fileName.toString().lowercase(java.util.Locale.ROOT) in setOf("data", "obb", "sandbox")) continue
                entries += JSONObject().put("name", file.fileName.toString()).put("path", path).put("directory", stat.isDirectory)
                    .put("size", if (stat.isDirectory) 0 else stat.size()).put("modified", stat.lastModifiedTime().toMillis())
            } }
        }
        entries.sortWith(compareByDescending<JSONObject> { it.getBoolean("directory") }.thenBy { it.getString("name") })
        ensureRead()
        if (logical != SharedStoragePaths.ROOT) resolve(logical)
        val begin = offset.toInt().coerceAtMost(entries.size)
        val end = (begin + limit).coerceAtMost(entries.size)
        return JSONObject().put("path", logical).put("entries", JSONArray(entries.subList(begin, end)))
            .put("totalEntries", entries.size).put("nextOffset", if (end < entries.size) end else JSONObject.NULL)
    }

    private suspend fun read(params: JSONObject, progress: (Long, Long) -> Unit): JSONObject {
        val resolved = resolve(requiredPath(params))
        if (!Files.isRegularFile(resolved.path, NOFOLLOW_LINKS)) fail("not_a_file")
        val offset = params.optLong("offset", 0)
        val length = params.optInt("length", CHUNK_BYTES)
        if (offset < 0 || length !in 1..CHUNK_BYTES) fail("invalid_pagination")
        val before = fingerprint(resolved.path)
        val revision = revision(resolved.path, progress = progress)
        val size = attrs(resolved.path).size()
        if (offset > size) fail("invalid_offset")
        val buffer = ByteBuffer.allocate(minOf(length.toLong(), size - offset).toInt())
        FileChannel.open(resolved.path, READ, NOFOLLOW_LINKS).use { channel ->
            channel.position(offset)
            while (buffer.hasRemaining()) {
                currentCoroutineContext().ensureActive(); ensureRead()
                if (channel.read(buffer) < 0) fail("file_changed")
            }
        }
        ensureRead()
        if (resolve(requiredPath(params)).path != resolved.path) fail("file_changed")
        if (before != fingerprint(resolved.path)) fail("file_changed")
        val next = offset + buffer.position()
        return JSONObject().put("dataBase64", Base64.encodeToString(buffer.array(), Base64.NO_WRAP)).put("offset", offset)
            .put("nextOffset", if (next < size) next else JSONObject.NULL).put("totalBytes", size).put("revision", revision)
    }

    private fun uploadDirectory(): Path {
        val root = File(context.cacheDir, "phone-file-uploads").toPath()
        if (Files.isSymbolicLink(root)) fail("unsafe_staging_path")
        Files.createDirectories(root)
        Os.chmod(root.toString(), 448)
        if (!stagingPrepared) {
            // 进程意外退出留下的上传不会在新连接中继续，也不能继承旧授权。
            Files.newDirectoryStream(root).use { files -> for (file in files) {
                if (file.fileName.toString().matches(Regex("[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}")) &&
                    Files.isRegularFile(file, NOFOLLOW_LINKS)) Files.deleteIfExists(file)
            } }
            stagingPrepared = true
        }
        return root
    }

    private suspend fun begin(params: JSONObject, progress: (Long, Long) -> Unit): JSONObject {
        val generation = epoch
        ensureWrite(generation)
        val logical = requiredPath(params)
        val target = resolve(logical, missing = true)
        if (target.parts.isEmpty()) fail("volume_root_protected")
        val size = params.optLong("size", -1)
        if (size < 0) fail("invalid_size")
        val free = context.cacheDir.usableSpace - WorkspaceCapacity.DISK_RESERVE
        if (size > free) fail("insufficient_space")
        val original = if (Files.exists(target.path, NOFOLLOW_LINKS)) {
            if (!Files.isRegularFile(target.path, NOFOLLOW_LINKS)) fail("not_a_file")
            val expected = params.optString("expectedRevision").takeIf { it.isNotEmpty() } ?: fail("expected_revision_required")
            val current = revision(target.path, true, generation, progress)
            if (current != expected) fail("file_changed")
            current
        } else {
            if (params.optString("expectedRevision").isNotEmpty()) fail("file_changed")
            null
        }
        ensureWrite(generation)
        val id = UUID.randomUUID().toString()
        val stage = uploadDirectory().resolve(id)
        Files.newOutputStream(stage, CREATE_NEW, WRITE, NOFOLLOW_LINKS).close()
        uploads[id] = Upload(id, logical, stage, size, original, generation)
        return JSONObject().put("id", id).put("received", 0).put("size", size)
    }

    private fun upload(params: JSONObject): Upload = uploads[params.optString("id")] ?: fail("upload_not_found")

    private suspend fun chunk(params: JSONObject): JSONObject {
        ensureWrite()
        val upload = upload(params)
        ensureWrite(upload.epoch)
        val encoded = params.optString("dataBase64")
        if (encoded.length > (CHUNK_BYTES + 2) / 3 * 4 || !encoded.matches(Regex("[A-Za-z0-9+/]*={0,2}"))) fail("invalid_chunk")
        val bytes = try { Base64.decode(encoded, Base64.DEFAULT) } catch (_: IllegalArgumentException) { fail("invalid_chunk") }
        if (bytes.size > CHUNK_BYTES || params.optLong("offset", -1) != upload.received || bytes.size.toLong() > upload.size - upload.received) fail("invalid_chunk")
        if (context.cacheDir.usableSpace - bytes.size < WorkspaceCapacity.DISK_RESERVE) fail("insufficient_space")
        FileChannel.open(upload.file, WRITE, NOFOLLOW_LINKS).use { channel ->
            channel.position(upload.received)
            val buffer = ByteBuffer.wrap(bytes)
            while (buffer.hasRemaining()) { currentCoroutineContext().ensureActive(); ensureWrite(upload.epoch); channel.write(buffer) }
        }
        ensureWrite(upload.epoch)
        upload.received += bytes.size; upload.touched = SystemClock.elapsedRealtime()
        return JSONObject().put("received", upload.received).put("size", upload.size)
    }

    private suspend fun originalStillMatches(upload: Upload, target: Resolved, progress: (Long, Long) -> Unit): String? {
        if (upload.originalRevision == null) {
            if (Files.exists(target.path, NOFOLLOW_LINKS)) fail("file_changed")
            return null
        }
        if (!Files.isRegularFile(target.path, NOFOLLOW_LINKS) || revision(target.path, true, upload.epoch, progress) != upload.originalRevision) fail("file_changed")
        return fingerprint(target.path)
    }

    private suspend fun commit(params: JSONObject, progress: (Long, Long) -> Unit): JSONObject {
        ensureWrite()
        val upload = upload(params)
        ensureWrite(upload.epoch)
        if (upload.received != upload.size) fail("upload_incomplete")
        val target = resolve(upload.path, missing = true)
        originalStillMatches(upload, target, progress)
        if (target.path.parent.toFile().usableSpace - upload.size < WorkspaceCapacity.DISK_RESERVE) fail("insufficient_space")
        val temporary = Files.createTempFile(target.path.parent, ".tanuki-phone-", ".tmp")
        try {
            Files.newInputStream(upload.file, READ, NOFOLLOW_LINKS).use { input ->
                FileChannel.open(temporary, WRITE, NOFOLLOW_LINKS).use { output ->
                    var copied = 0L
                    val buffer = ByteArray(64 * 1024)
                    progress(copied, upload.size)
                    while (true) {
                        currentCoroutineContext().ensureActive(); ensureWrite(upload.epoch)
                        val count = input.read(buffer)
                        if (count < 0) break
                        val data = ByteBuffer.wrap(buffer, 0, count)
                        while (data.hasRemaining()) { ensureWrite(upload.epoch); output.write(data) }
                        copied += count; progress(copied, upload.size)
                    }
                    if (copied != upload.size) fail("upload_incomplete")
                    output.force(true)
                }
            }
            val finalTarget = resolve(upload.path, missing = true)
            if (finalTarget.path != target.path) fail("file_changed")
            val originalStamp = originalStillMatches(upload, finalTarget, progress)
            synchronized(policyLock) {
                ensureWrite(upload.epoch)
                val checked = resolve(upload.path, missing = true)
                if (checked.path != target.path) fail("file_changed")
                if (originalStamp == null) {
                    if (Files.exists(target.path, NOFOLLOW_LINKS)) fail("file_changed")
                    Files.move(temporary, target.path)
                } else {
                    if (fingerprint(target.path) != originalStamp) fail("file_changed")
                    Files.move(temporary, target.path, ATOMIC_MOVE, REPLACE_EXISTING)
                }
            }
            uploads.remove(upload.id); Files.deleteIfExists(upload.file); revisions.remove(target.path.toString())
            return JSONObject().put("path", upload.path).put("size", upload.size).put("written", true)
        } finally { Files.deleteIfExists(temporary) }
    }

    private fun cancelUpload(params: JSONObject): JSONObject {
        val id = params.optString("id")
        uploads.remove(id)?.let { Files.deleteIfExists(it.file) }
        return JSONObject().put("cancelled", true)
    }

    private fun requireNotRoot(target: Resolved) { if (target.parts.isEmpty()) fail("volume_root_protected") }
    private fun requireEmptyDirectory(path: Path) {
        if (Files.isDirectory(path, NOFOLLOW_LINKS)) Files.newDirectoryStream(path).use { if (it.iterator().hasNext()) fail("directory_not_empty") }
    }

    private fun mkdir(params: JSONObject): JSONObject {
        val generation = epoch
        ensureWrite(generation)
        val logical = requiredPath(params)
        val target = resolve(logical, missing = true)
        requireNotRoot(target)
        synchronized(policyLock) {
            ensureWrite(generation)
            val checked = resolve(logical, missing = true)
            if (checked.path != target.path || Files.exists(target.path, NOFOLLOW_LINKS)) fail("file_exists")
            Files.createDirectory(target.path)
        }
        return JSONObject().put("path", logical).put("created", true)
    }

    private suspend fun mutateSource(params: JSONObject, generation: Long, progress: (Long, Long) -> Unit): Pair<Resolved, String> {
        ensureWrite(generation)
        val target = resolve(requiredPath(params))
        requireNotRoot(target); requireEmptyDirectory(target.path)
        val expected = params.optString("expectedRevision").takeIf { it.isNotEmpty() } ?: fail("expected_revision_required")
        if (revision(target.path, true, generation, progress) != expected) fail("file_changed")
        return target to fingerprint(target.path)
    }

    private suspend fun rename(params: JSONObject, progress: (Long, Long) -> Unit): JSONObject {
        val generation = epoch
        val (source, stamp) = mutateSource(params, generation, progress)
        val destination = resolve(requiredPath(params, "to"), missing = true)
        requireNotRoot(destination)
        if (source.root != destination.root) fail("cross_volume_move_not_supported")
        synchronized(policyLock) {
            ensureWrite(generation)
            val freshSource = resolve(requiredPath(params))
            val freshTarget = resolve(requiredPath(params, "to"), missing = true)
            if (freshSource.path != source.path || freshTarget.path != destination.path || fingerprint(source.path) != stamp) fail("file_changed")
            requireEmptyDirectory(source.path)
            if (Files.exists(destination.path, NOFOLLOW_LINKS)) fail("file_exists")
            Files.move(source.path, destination.path)
        }
        revisions.remove(source.path.toString())
        return JSONObject().put("path", requiredPath(params, "to")).put("renamed", true)
    }

    private suspend fun delete(params: JSONObject, progress: (Long, Long) -> Unit): JSONObject {
        val generation = epoch
        val (target, stamp) = mutateSource(params, generation, progress)
        synchronized(policyLock) {
            ensureWrite(generation)
            val checked = resolve(requiredPath(params))
            if (checked.path != target.path || fingerprint(target.path) != stamp) fail("file_changed")
            requireEmptyDirectory(target.path); Files.delete(target.path)
        }
        revisions.remove(target.path.toString())
        return JSONObject().put("deleted", true)
    }

    private fun cleanUploads() {
        val stale = uploads.values.filter { closed || it.epoch != epoch || !allowed.value }
        stale.forEach { upload -> runCatching { Files.deleteIfExists(upload.file) }; uploads.remove(upload.id) }
    }

    private fun expireUploads() {
        val cutoff = SystemClock.elapsedRealtime() - 60 * 60 * 1000L
        uploads.values.filter { it.touched < cutoff }.toList().forEach { upload ->
            Files.deleteIfExists(upload.file); uploads.remove(upload.id)
        }
    }

    private fun fail(code: String): Nothing = throw IllegalStateException(code)
}
