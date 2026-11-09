package com.tanuki.storage

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.ParcelFileDescriptor
import android.util.Xml
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.tanuki.runtime.Paths
import com.tanuki.storage.docx.DocxDocument
import com.tanuki.storage.docx.DocxDocumentReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.File
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.Locale
import java.util.UUID

/** 文件操作在 IO 线程串行执行；读取允许服务运行，修改已有内容仍要求服务停止。
 * 路径检查不构成恶意同 UID 代码的隔离；新建/复制/移动不覆盖同名文件，编辑检查原内容版本。
 * 底层 IO 失败可能造成部分删除，调用方必须保留错误并刷新目录。
 */
enum class FileLocation(val title: String, val guestPath: String, val writable: Boolean) {
    WORKSPACE("工作区", "/workspaces", true), HOME("用户目录", "/home/tanuki", false),
    UBUNTU("Ubuntu 文件", "/", false), PHONE("手机存储", "/phone", false)
}

class WorkspaceFileManager(
    private val paths: Paths,
    private val mutation: Mutex = Mutex(),
    private val requireIdle: () -> Unit = {},
    private val storage: WorkspaceStorage = WorkspaceStorage(paths),
    val location: FileLocation = FileLocation.WORKSPACE,
    private val phoneRoots: () -> Map<String, File> = { emptyMap() }
) {
    data class Entry(val path: String, val name: String, val directory: Boolean, val bytes: Long, val linked: Boolean, val modified: Long = 0)
    data class Preview(val text: String? = null, val image: Bitmap? = null, val revision: String? = null)
    enum class FileKind { TEXT, IMAGE, AUDIO, VIDEO, PDF, DOCX, DOC, XLSX, OTHER }
    data class FileInfo(val name: String, val mimeType: String, val bytes: Long, val kind: FileKind)
    data class TextPage(val text: String, val offset: Long, val nextOffset: Long, val totalBytes: Long,
                        val encoding: String, val hasMore: Boolean, val revision: String? = null)
    fun inLocation(value: FileLocation) = WorkspaceFileManager(paths, mutation, requireIdle, storage, value, phoneRoots)
    fun guestPath(relative: String): String {
        if (location == FileLocation.PHONE && relative.isNotEmpty()) {
            val key = relative.substringBefore('/')
            val root = phoneRoots()[key]
            if (root != null) return PhoneStorageAccess.guestPath(root, relative.removePrefix(key).removePrefix("/"))
        }
        return (location.guestPath.trimEnd('/') + if (relative.isEmpty()) "" else "/$relative").ifEmpty { "/" }
    }
    companion object {
        const val TEXT_LIMIT = 256 * 1024
        private val textExtensions = setOf("txt", "text", "md", "markdown", "log", "csv", "tsv", "json", "jsonl", "xml", "yaml", "yml",
            "toml", "ini", "cfg", "conf", "properties", "kt", "kts", "java", "py", "js", "jsx", "mjs", "cjs", "ts", "tsx",
            "html", "htm", "css", "scss", "less", "sh", "bash", "zsh", "c", "h", "cpp", "hpp", "rs", "go", "swift", "sql", "srt", "vtt", "env")
        private val textNames = setOf("Dockerfile", "Makefile", "LICENSE", "README", ".gitignore", ".gitattributes", ".editorconfig")

        /** 纯扩展名/MIME 分类；.doc 明确保留为旧二进制格式，不能当作 DOCX 或文本。 */
        internal fun classifyFileKind(name: String, mime: String?): FileKind {
            val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
            val type = mime?.lowercase(Locale.ROOT)
            return when {
                extension == "pdf" -> FileKind.PDF
                extension == "docx" -> FileKind.DOCX
                extension == "doc" -> FileKind.DOC
                extension == "xlsx" -> FileKind.XLSX
                type?.startsWith("image/") == true -> FileKind.IMAGE
                type?.startsWith("audio/") == true -> FileKind.AUDIO
                type?.startsWith("video/") == true -> FileKind.VIDEO
                extension in textExtensions || name in textNames || type?.startsWith("text/") == true -> FileKind.TEXT
                else -> FileKind.OTHER
            }
        }
    }
    private val lock = Mutex()
    private val noFollow = LinkOption.NOFOLLOW_LINKS
    private val internalName = Regex("^\\.(?:proot\\.)?l2s\\..*")
    private fun reserved(name: String) = internalName.matches(name) || name.startsWith(".workspace-tmp-")
    private fun segment(name: String) {
        require(name.isNotBlank() && name != "." && name != ".." &&
            name.none { it == '/' || it == '\\' || it == '\u0000' } && !reserved(name)) { "名称无效或属于内部存储条目" }
    }
    // 按启动器挂载显示客体路径；不暴露 /dev、/proc、/sys 或含连接凭据的 browser-tools 挂载。
    private val mounts get() = when (location) {
        FileLocation.WORKSPACE -> mapOf("" to paths.workspaces)
        FileLocation.HOME -> mapOf("" to paths.home)
        FileLocation.UBUNTU -> mapOf("" to paths.root, "workspaces" to paths.workspaces,
            "home/tanuki" to paths.home, "dsh-data" to paths.dshData, "opt/node" to paths.node,
            "opt/pnpm" to paths.pnpm, "opt/engines" to paths.engines, "tmp" to paths.guestTmp)
        FileLocation.PHONE -> phoneRoots()
    }
    private fun root(): Path = paths.workspaces.toPath().toRealPath()
    private fun resolve(relative: String, entry: Boolean = false): Path {
        require(relative.isNotEmpty() || !entry) { "不能修改位置根目录" }
        if (relative.isNotEmpty()) relative.split('/').forEach(::segment)
        if (location == FileLocation.PHONE) {
            val key = relative.substringBefore('/')
            val volume = phoneRoots()[key] ?: throw IOException("存储卷不可用，请检查存储权限或重新选择")
            var result = volume.toPath().toRealPath()
            check(Files.isDirectory(result, noFollow)) { "存储卷不可用" }
            val tail = relative.removePrefix(key).removePrefix("/")
            if (tail.isNotEmpty()) tail.split('/').forEachIndexed { index, part ->
                result = result.resolve(part)
                if (index < tail.split('/').lastIndex) check(Files.isDirectory(result, noFollow) && !Files.isSymbolicLink(result)) { "不能通过链接访问目录" }
            }
            return result
        }
        val binding = mounts.keys.filter { it.isEmpty() || relative == it || relative.startsWith("$it/") }.maxBy { it.length }
        val bound = mounts.getValue(binding).toPath()
        val logicalBase = paths.base.toPath().toAbsolutePath().normalize()
        val base = logicalBase.toRealPath()
        check(bound.normalize().startsWith(logicalBase)) { "目录超出允许范围" }
        var checked = base
        for (part in logicalBase.relativize(bound)) {
            checked = checked.resolve(part)
            check(Files.isDirectory(checked, noFollow) && !Files.isSymbolicLink(checked)) { "目录尚未安装、已移除或被重定向" }
        }
        val tail = if (binding.isEmpty()) relative else relative.removePrefix(binding).removePrefix("/")
        var result = checked
        val parts = tail.split('/')
        if (tail.isNotEmpty()) parts.forEachIndexed { index, part ->
            result = result.resolve(part)
            if (index < parts.lastIndex) check(Files.isDirectory(result, noFollow) && !Files.isSymbolicLink(result)) { "不能通过链接访问目录" }
        }
        return result
    }
    /** 相对路径使用 /，空字符串表示当前位置根目录。 */
    private fun directory(relative: String): Path = resolve(relative).also {
        check(Files.isDirectory(it, noFollow) && !Files.isSymbolicLink(it)) { "目录不存在或是符号链接" }
    }
    private fun destination(parent: String, name: String): Path {
        segment(name)
        return directory(parent).resolve(name).also { check(!Files.exists(it, noFollow)) { "同名条目已存在，未覆盖" } }
    }
    private fun payload(path: Path): Path {
        if (Files.isRegularFile(path, noFollow)) return path
        if (Files.isSymbolicLink(path)) {
            check(location != FileLocation.PHONE) { "不通过手机存储中的符号链接读取文件" }
            val target = L2sStore.payload(path, paths.base.toPath()) ?: throw IOException("不支持预览或复制普通符号链接")
            val store = paths.linkStore.toPath()
            val approvedStore = !Files.isSymbolicLink(store) && Files.isDirectory(store, noFollow) &&
                target.parent == store.toRealPath()
            check(target.startsWith(root()) || approvedStore ||
                (location != FileLocation.WORKSPACE && mounts.values.any { root ->
                    !Files.isSymbolicLink(root.toPath()) && root.isDirectory && target.startsWith(root.toPath().toRealPath())
                })) {
                "链接内容超出当前位置和允许的硬链接存储"
            }
            return target
        }
        throw IOException("不支持此文件类型，或文件已不存在")
    }
    private suspend fun <T> io(growing: Boolean = false, writing: Boolean = true, block: suspend () -> T): T = withContext(Dispatchers.IO) {
        mutation.withLock {
            if (writing) {
                check(location.writable) { "此位置只读；请在工作区编辑项目文件" }
                requireIdle()
            }
            lock.withLock {
                if (growing) storage.requireCanStart()
                block()
            }
        }
    }

    suspend fun list(directory: String = ""): List<Entry> = io(writing = false) {
        if (location == FileLocation.PHONE && directory.isEmpty()) {
            return@io phoneRoots().map { (key, _) ->
                segment(key)
                Entry(key, if (key == "internal") "内部共享存储" else key, true, 0, false)
            }.sortedBy { it.path }
        }
        val folder = directory(directory)
        val entries = linkedMapOf<String, Entry>()
        fun child(name: String) = if (directory.isEmpty()) name else "$directory/$name"
        Files.newDirectoryStream(folder).use { children ->
            for (path in children) {
                currentCoroutineContext().ensureActive()
                if (reserved(path.fileName.toString())) continue
                // Agent 可在列举过程中增删文件；已消失的条目不使整个目录加载失败。
                val stat = try { Files.readAttributes(path, BasicFileAttributes::class.java, noFollow) }
                    catch (_: NoSuchFileException) { continue }
                val name = path.fileName.toString()
                entries[name] = Entry(child(name), name, stat.isDirectory,
                    if (stat.isSymbolicLink) runCatching { Files.size(payload(path)) }.getOrDefault(-1L) else stat.size(),
                    stat.isSymbolicLink, stat.lastModifiedTime().toMillis())
            }
        }
        // 实际挂载内容优先于镜像中的空挂载目录。
        for ((mount, physical) in mounts) {
            val prefix = if (directory.isEmpty()) "" else "$directory/"
            if (mount.isNotEmpty() && mount.startsWith(prefix) && physical.isDirectory) {
                val name = mount.removePrefix(prefix).substringBefore('/')
                if (name.isNotEmpty()) entries[name] = Entry(child(name), name, true, 0, false)
            }
        }
        entries.values.sortedWith(compareByDescending<Entry> { it.directory }.thenBy { it.name.lowercase() }.thenBy { it.name })
    }
    suspend fun createFolder(parent: String, name: String) = io(growing = true) {
        val target = Files.createDirectory(destination(parent, name))
        try { storage.requireCanCommitImport() }
        catch (failure: Throwable) { Files.deleteIfExists(target); throw failure }
    }
    suspend fun createText(parent: String, name: String, text: String) = io(growing = true) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        require(bytes.size <= TEXT_LIMIT) { "文本不能超过 256 KiB" }
        val target = destination(parent, name)
        val stage = Files.createTempFile(target.parent, ".workspace-tmp-", ".part")
        try {
            writeStage(stage, bytes)
            currentCoroutineContext().ensureActive()
            storage.requireCanCommitImport()
            publish(stage, target)
        } finally { Files.deleteIfExists(stage) }
    }
    private fun writeStage(stage: Path, bytes: ByteArray) {
        java.nio.channels.FileChannel.open(stage, StandardOpenOption.WRITE, noFollow).use { channel ->
            val content = ByteBuffer.wrap(bytes)
            while (content.hasRemaining()) channel.write(content)
            channel.force(true)
        }
    }
    private fun publish(stage: Path, target: Path) {
        check(!Files.exists(target, noFollow)) { "同名条目已存在，未覆盖" }
        // 同目录发布明确不覆盖；ATOMIC_MOVE 可能允许替换，故此处不使用。
        Files.move(stage, target)
    }
    suspend fun preview(relative: String): Preview = io(writing = false) {
        val file = payload(resolve(relative, true))
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        Files.newInputStream(file, noFollow).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth > 0 && bounds.outHeight > 0) {
            var sample = 1
            while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val image = Files.newInputStream(file, noFollow).use { BitmapFactory.decodeStream(it, null, options) }
                ?: throw IOException("图片无法解码")
            Preview(image = image)
        } else {
            check(Files.size(file) <= TEXT_LIMIT) { "文本超过 256 KiB，无法预览" }
            val bytes = Files.newInputStream(file, noFollow).use { input ->
                val buffer = ByteArray(TEXT_LIMIT + 1)
                var count = 0
                while (count < buffer.size) {
                    currentCoroutineContext().ensureActive()
                    val n = input.read(buffer, count, buffer.size - count)
                    if (n < 0) break
                    count += n
                }
                check(count <= TEXT_LIMIT) { "文本超过 256 KiB，无法预览" }
                buffer.copyOf(count)
            }
            check(bytes.none { it == 0.toByte() }) { "此文件不是可预览的文本或图片" }
            val text = try { Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString() }
            catch (_: java.nio.charset.CharacterCodingException) { throw IOException("仅支持 UTF-8 文本预览") }
            Preview(text = text, revision = revision(bytes))
        }
    }

    suspend fun describe(relative: String): FileInfo = io(writing = false) {
        val file = payload(resolve(relative, true))
        val name = relative.substringAfterLast('/')
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val explicitMime = when (extension) {
            "mkv" -> "video/x-matroska"; "mov" -> "video/quicktime"; "webm" -> "video/webm"
            "mp4", "m4v" -> "video/mp4"; "avi" -> "video/x-msvideo"; "m4a" -> "audio/mp4"
            "mp3" -> "audio/mpeg"; "flac" -> "audio/flac"; "wav" -> "audio/wav"; "ogg", "opus" -> "audio/ogg"
            "pdf" -> "application/pdf"; "md", "markdown" -> "text/markdown"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "doc" -> "application/msword"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        }
        var kind = classifyFileKind(name, explicitMime)
        // 无已知扩展名时保留原有内容探测：可解码为文本的文件按文本预览。
        if (kind == FileKind.OTHER && explicitMime == null && runCatching { TextFileReader.read(file); true }.getOrDefault(false)) {
            kind = FileKind.TEXT
        }
        FileInfo(name, explicitMime ?: if (kind == FileKind.TEXT) "text/plain" else "application/octet-stream", Files.size(file), kind)
    }

    /** 为 PDF 等需要可随机读取的内置预览提供只读描述符；调用方负责关闭。 */
    suspend fun openPreviewDescriptor(relative: String): ParcelFileDescriptor = io(writing = false) {
        val file = payload(resolve(relative, true))
        check(Files.isRegularFile(file, noFollow)) { "此条目不是普通文件，无法预览" }
        ParcelFileDescriptor.open(file.toFile(), ParcelFileDescriptor.MODE_READ_ONLY)
    }

    /** DOCX 原生预览在 IO 锁内完成有界解压与解析，只向界面返回受控模型。 */
    suspend fun readDocx(relative: String): DocxDocument = io(writing = false) {
        val file = payload(resolve(relative, true))
        DocxDocumentReader.read(file.toFile()) { Xml.newPullParser() }
    }

    /** XLSX 只读解析在 IO 锁内完成有界解压；不向界面暴露文件路径。 */
    internal suspend fun readXlsx(relative: String): XlsxWorkbook = io(writing = false) {
        val file = payload(resolve(relative, true))
        XlsxWorkbookParser.parse(file.toFile()) { input ->
            Xml.newPullParser().apply {
                setFeature(org.xmlpull.v1.XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                setInput(input, null)
            }
        }
    }

    suspend fun readTextPage(relative: String, offset: Long = 0, encoding: String = "自动"): TextPage = io(writing = false) {
        val file = payload(resolve(relative, true))
        val before = Files.readAttributes(file, BasicFileAttributes::class.java, noFollow)
        val page = TextFileReader.read(file, offset, encoding)
        val revision = if (offset == 0L && !page.hasMore && page.totalBytes <= TEXT_LIMIT && page.encoding == "UTF-8") {
            val bytes = ByteArray(TEXT_LIMIT + 1)
            var count = 0
            Files.newInputStream(file, noFollow).use { input ->
                while (count < bytes.size) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(bytes, count, bytes.size - count)
                    if (read < 0) break
                    count += read
                }
            }
            check(count.toLong() == page.totalBytes && count <= TEXT_LIMIT) { "文件已变化，请重新打开" }
            val original = bytes.copyOf(count)
            val decoded = original.toString(Charsets.UTF_8).removePrefix("\ufeff")
            check(decoded == page.text) { "文件已变化，请重新打开" }
            revision(original)
        } else null
        val after = Files.readAttributes(file, BasicFileAttributes::class.java, noFollow)
        check(before.fileKey() == after.fileKey() && before.size() == after.size() && before.lastModifiedTime() == after.lastModifiedTime()) { "文件已变化，请重新打开" }
        TextPage(page.text, page.offset, page.nextOffset, page.totalBytes, page.encoding, page.hasMore, revision)
    }

    /** 只把用户选择的单文件复制到独立缓存，目标应用获得只读 URI，不获得私有目录访问权。 */
    suspend fun prepareOpen(context: Context, relative: String, onProgress: (Long, Long) -> Unit = { _, _ -> }): Intent = io(writing = false) {
        val file = payload(resolve(relative, true))
        val cache = File(context.cacheDir, "workspace-open").toPath()
        if (!Files.exists(cache, noFollow)) Files.createDirectory(cache)
        check(Files.isDirectory(cache, noFollow) && !Files.isSymbolicLink(cache)) { "文件打开缓存不可用" }
        val now = System.currentTimeMillis()
        Files.newDirectoryStream(cache).use { children -> for (entry in children) {
            currentCoroutineContext().ensureActive()
            if (Files.isDirectory(entry, noFollow) && now - Files.getLastModifiedTime(entry, noFollow).toMillis() > 24 * 60 * 60 * 1000L) {
                removeTree(entry)
            }
        } }
        val directory = Files.createDirectory(cache.resolve(UUID.randomUUID().toString()))
        val target = directory.resolve(relative.substringAfterLast('/'))
        try {
            Files.newOutputStream(target, StandardOpenOption.CREATE_NEW).use { output ->
                copyChecked(file, output) { copied, total ->
                    check(context.cacheDir.usableSpace >= WorkspaceCapacity.DISK_RESERVE) { "设备空间不足，无法准备此文件" }
                    onProgress(copied, total)
                }
            }
            val uri = FileProvider.getUriForFile(context, context.packageName + ".workspaceopen", target.toFile())
            val mime = fileMime(relative)
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).apply {
                clipData = ClipData.newRawUri("用户选择的文件", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (failure: Throwable) {
            removeTree(directory)
            throw failure
        }
    }

    /** 导出用户指定的单文件；输出流归调用方管理，变化或取消时由调用方撤销未完成目标。 */
    suspend fun exportFile(relative: String, output: OutputStream, onProgress: (Long, Long) -> Unit = { _, _ -> }) = io(writing = false) {
        copyChecked(payload(resolve(relative, true)), output, onProgress)
    }

    private suspend fun copyChecked(file: Path, output: OutputStream, onProgress: (Long, Long) -> Unit) {
        val before = Files.readAttributes(file, BasicFileAttributes::class.java, noFollow)
        check(before.isRegularFile) { "不支持导出此文件类型" }
        var copied = 0L
        onProgress(copied, before.size())
        Files.newInputStream(file, noFollow).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (copied < before.size()) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer, 0, minOf(buffer.size.toLong(), before.size() - copied).toInt())
                check(count >= 0) { "文件在读取期间发生变化，请稍后重试" }
                output.write(buffer, 0, count)
                copied += count
                onProgress(copied, before.size())
            }
            check(input.read() < 0) { "文件在读取期间发生变化，请稍后重试" }
        }
        currentCoroutineContext().ensureActive()
        val after = Files.readAttributes(file, BasicFileAttributes::class.java, noFollow)
        check(before.size() == after.size() && before.lastModifiedTime() == after.lastModifiedTime() && before.fileKey() == after.fileKey()) {
            "文件在读取期间发生变化，请稍后重试"
        }
    }

    private fun fileMime(relative: String): String {
        val extension = relative.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when (extension) {
            "mkv" -> "video/x-matroska"; "mov" -> "video/quicktime"; "webm" -> "video/webm"
            "m4a" -> "audio/mp4"; "flac" -> "audio/flac"; "opus" -> "audio/ogg"
            "pdf" -> "application/pdf"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "doc" -> "application/msword"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
        }
    }
    private fun revision(bytes: ByteArray) = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }

    /** 乐观保存：原内容版本变化时拒绝覆盖；链接只允许读取。 */
    suspend fun saveText(relative: String, text: String, expectedRevision: String) = io(growing = true) {
        val target = resolve(relative, true)
        check(Files.isRegularFile(target, noFollow) && !Files.isSymbolicLink(target)) { "链接或特殊文件仅可预览，请先复制为独立文件" }
        check(Files.size(target) <= TEXT_LIMIT) { "文件已变大，请重新打开" }
        val original = Files.newInputStream(target, noFollow).use { it.readBytes() }
        check(revision(original) == expectedRevision) { "文件已被修改，未覆盖；请复制当前草稿并重新打开文件" }
        val bytes = text.toByteArray(Charsets.UTF_8)
        check(bytes.size <= TEXT_LIMIT) { "文本不能超过 256 KiB" }
        val stage = Files.createTempFile(target.parent, ".workspace-tmp-", ".part")
        try {
            writeStage(stage, bytes)
            Files.setPosixFilePermissions(stage, Files.getPosixFilePermissions(target, noFollow))
            storage.requireCanCommitImport()
            currentCoroutineContext().ensureActive()
            // 编辑仅替换已核对的原版本，与不覆盖的新建/复制操作区分。
            Files.move(stage, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(stage) }
    }
    suspend fun rename(relative: String, name: String) = move(relative, relative.substringBeforeLast('/', ""), name)
    suspend fun move(relative: String, parent: String, name: String = relative.substringAfterLast('/')) = io {
        val source = resolve(relative, true)
        val target = destination(parent, name)
        check(!target.startsWith(source)) { "不能移动到自身或子目录" }
        if (source.parent != target.parent) {
            check(!Files.isSymbolicLink(source)) { "链接仅支持原目录重命名；跨目录请复制后删除原条目" }
            inspectTree(source, rejectLinks = true)
        } else inspectTree(source, rejectLinks = Files.isDirectory(source, noFollow))
        publish(source, target)
    }
    suspend fun copy(relative: String, parent: String, name: String = relative.substringAfterLast('/')) = io(growing = true) {
        val source = resolve(relative, true)
        val target = destination(parent, name)
        check(!target.startsWith(source)) { "不能复制到自身或子目录" }
        inspectTree(source)
        val stage = Files.createTempDirectory(target.parent, ".workspace-tmp-")
        try {
            copyTree(source, stage.resolve("content"))
            currentCoroutineContext().ensureActive()
            storage.requireCanCommitImport()
            publish(stage.resolve("content"), target)
        } finally { removeTree(stage) }
    }
    suspend fun delete(relative: String) = io {
        val source = resolve(relative, true)
        inspectTree(source)
        currentCoroutineContext().ensureActive()
        try { removeTree(source) } // 不跟随链接删除共享内容，只删除发布条目本身。
        catch (error: IOException) { throw IOException("删除未完成，部分条目可能已删除；请刷新检查", error) }
    }
    private suspend fun inspectTree(source: Path, rejectLinks: Boolean = false) {
        val coroutine = currentCoroutineContext()
        Files.walkFileTree(source, object : SimpleFileVisitor<Path>() {
            private fun inspect(path: Path, attrs: BasicFileAttributes): FileVisitResult {
                coroutine.ensureActive()
                check(!reserved(path.fileName.toString())) { "目录包含内部硬链接存储或临时条目，不能操作" }
                check(!rejectLinks || !attrs.isSymbolicLink) { "目录包含链接，无法安全移动或重命名" }
                return FileVisitResult.CONTINUE
            }
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes) = inspect(dir, attrs)
            override fun visitFile(file: Path, attrs: BasicFileAttributes) = inspect(file, attrs)
        })
    }
    private suspend fun copyTree(source: Path, target: Path, depth: Int = 0) {
        currentCoroutineContext().ensureActive()
        check(depth <= 128) { "目录层级过深" }
        if (Files.isDirectory(source, noFollow)) {
            Files.createDirectory(target)
            Files.newDirectoryStream(source).use { children -> for (child in children) copyTree(child, target.resolve(child.fileName), depth + 1) }
        } else {
            Files.newInputStream(payload(source), noFollow).use { input ->
                Files.newOutputStream(target, StandardOpenOption.CREATE_NEW).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        check(paths.base.usableSpace - n >= WorkspaceCapacity.DISK_RESERVE) { "设备剩余空间不足，复制已取消" }
                        output.write(buffer, 0, n)
                    }
                }
            }
        }
    }
    private fun removeTree(source: Path) {
        Files.walkFileTree(source, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult { Files.delete(file); return FileVisitResult.CONTINUE }
            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                if (exc != null) throw exc
                Files.delete(dir)
                return FileVisitResult.CONTINUE
            }
        })
    }
}
