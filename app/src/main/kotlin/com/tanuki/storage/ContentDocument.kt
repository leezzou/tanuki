package com.tanuki.storage

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Xml
import com.tanuki.storage.docx.DocxDocument
import com.tanuki.storage.docx.DocxDocumentReader
import com.tanuki.storage.docx.DocxLimits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import java.util.Locale
import java.util.UUID

/**
 * content:// 文档的内置预览实现。
 *
 * - 文本、图片、PDF 直接使用 ContentResolver 流或描述符，不复制整文件到工作区；
 * - DOCX/XLSX 依赖 ZIP 随机访问，只在应用私有缓存暂存有大小上限的副本，解析后立即删除；
 * - 权限失效、文件移动或提供方不支持随机读时返回中文错误，由界面提供重新选择或显式复制。
 */
internal class ContentDocument(
    private val context: Context,
    val uri: Uri,
    private val descriptor: ExternalDocumentDescriptor,
) : OpenDocument {
    override val key: String = ExternalDocumentPolicy.contentKey(uri.toString(), descriptor.openToken)
    override val displayName: String get() = descriptor.displayName
    override val accessWarning: String? =
        if (descriptor.persistedRead) null else ExternalDocumentPolicy.NON_PERSISTED_WARNING
    override val copyablePath: String get() = uri.toString()

    private val resolver = context.contentResolver
    private var cachedInfo: WorkspaceFileManager.FileInfo? = null

    override suspend fun describe(): WorkspaceFileManager.FileInfo = withContext(Dispatchers.IO) {
        cachedInfo?.let { return@withContext it }
        val metadata = try {
            ExternalDocumentAccess.metadata(context, uri)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            throw ExternalDocumentAccess.failure(failure)
        }
        val name = ExternalDocumentPolicy.sanitizeDisplayName(
            metadata.name ?: descriptor.displayName,
            fallback = descriptor.displayName,
        )
        val mime = metadata.mimeType?.takeIf { it.isNotBlank() } ?: descriptor.mimeType
        val bytes = metadata.bytes.takeIf { it >= 0 } ?: descriptor.bytes
        val kind = WorkspaceFileManager.classifyFileKind(name, mime)
        // 元数据读取与“使用其他应用打开/导出”不受内置预览大小限制；
        // 超限只在内置解码/解析路径（PDF 描述符、图片、DOCX/XLSX）拒绝。
        WorkspaceFileManager.FileInfo(name, mime, bytes, kind).also { cachedInfo = it }
    }

    override suspend fun previewImage(): WorkspaceFileManager.Preview = withContext(Dispatchers.IO) {
        val info = describe()
        check(info.kind == WorkspaceFileManager.FileKind.IMAGE) { "此文件不是内置预览支持的图片" }
        val limit = ExternalDocumentPolicy.MAX_IMAGE_BYTES
        if (info.bytes > limit) throw IOException(ExternalDocumentPolicy.tooLargeMessage(info.kind, info.bytes))
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openStream { input -> BitmapFactory.decodeStream(input, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("此图片格式无法内置预览，请使用其他应用打开。")
        }
        var sample = 1
        while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val image = openStream { input -> BitmapFactory.decodeStream(input, null, options) }
            ?: throw IOException("图片无法解码，请使用其他应用打开。")
        WorkspaceFileManager.Preview(image = image)
    }

    override suspend fun readTextPage(offset: Long, encoding: String): WorkspaceFileManager.TextPage =
        withContext(Dispatchers.IO) {
            val info = describe()
            try {
                val page = readTextSource(info, offset, encoding)
                WorkspaceFileManager.TextPage(
                    text = page.text,
                    offset = page.offset,
                    nextOffset = page.nextOffset,
                    totalBytes = page.totalBytes,
                    encoding = page.encoding,
                    hasMore = page.hasMore,
                )
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                throw ExternalDocumentAccess.failure(failure)
            }
        }

    /**
     * 优先使用可随机读描述符；部分云盘/第三方提供方只返回顺序流。
     * 两种方式每次分页都只读取一页，跳页时顺序流会重新打开并向前跳过。
     */
    private fun readTextSource(
        info: WorkspaceFileManager.FileInfo,
        offset: Long,
        encoding: String,
    ): TextFileReader.Page {
        val pfd = try {
            resolver.openFileDescriptor(uri, "r")
        } catch (failure: SecurityException) {
            throw failure
        } catch (failure: FileNotFoundException) {
            throw failure
        } catch (_: UnsupportedOperationException) {
            null
        }
        // statSize < 0 通常表示管道/非普通文件，不能按偏移随机读，退回顺序流。
        if (pfd != null && pfd.statSize >= 0) {
            val stream = try {
                FileInputStream(pfd.fileDescriptor)
            } catch (failure: Throwable) {
                runCatching { pfd.close() }
                throw failure
            }
            try {
                return TextFileReader.read(ChannelSeekableInput(stream.channel), offset, encoding)
            } finally {
                runCatching { stream.close() }
                runCatching { pfd.close() }
            }
        }
        pfd?.let { runCatching { it.close() } }
        val input = resolver.openInputStream(uri) ?: throw IOException("无法打开此文件")
        return input.use { stream ->
            TextFileReader.read(
                StreamSeekableInput(stream, info.bytes.takeIf { it > 0 }),
                offset,
                encoding,
            )
        }
    }

    // 描述符由 PdfDocumentSession 持有并关闭；此方法只负责把所有权交给调用方。
    @SuppressLint("Recycle")
    override suspend fun openPreviewDescriptor(): ParcelFileDescriptor = withContext(Dispatchers.IO) {
        try {
            val descriptor = resolver.openFileDescriptor(uri, "r")
                ?: throw IOException("无法打开此 PDF 的只读描述符")
            // 元数据可能不返回大小；描述符的实际大小是最后一道 PDF 上限检查。
            if (descriptor.statSize > ExternalDocumentPolicy.MAX_PDF_BYTES) {
                val size = descriptor.statSize
                runCatching { descriptor.close() }
                throw IOException(ExternalDocumentPolicy.tooLargeMessage(WorkspaceFileManager.FileKind.PDF, size))
            }
            descriptor
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            throw ExternalDocumentAccess.failure(failure)
        }
    }

    override suspend fun readDocx(): DocxDocument = withContext(Dispatchers.IO) {
        val limits = DocxLimits()
        val info = describe()
        if (info.bytes > limits.maxArchiveBytes) {
            throw IOException(ExternalDocumentPolicy.tooLargeMessage(info.kind, info.bytes))
        }
        val staged = stageArchive(limits.maxArchiveBytes, "DOCX")
        try {
            DocxDocumentReader.read(staged) { Xml.newPullParser() }
        } finally {
            staged.delete()
        }
    }

    override suspend fun readXlsx(): XlsxWorkbook = withContext(Dispatchers.IO) {
        val limits = XlsxWorkbookParser.Limits()
        val info = describe()
        if (info.bytes > limits.maxArchiveBytes) {
            throw IOException(ExternalDocumentPolicy.tooLargeMessage(info.kind, info.bytes))
        }
        val staged = stageArchive(limits.maxArchiveBytes, "XLSX")
        try {
            XlsxWorkbookParser.parse(staged) { input ->
                Xml.newPullParser().apply {
                    setFeature(org.xmlpull.v1.XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                    setInput(input, null)
                }
            }
        } finally {
            staged.delete()
        }
    }

    /**
     * ZIP 容器需要可随机访问的本地文件；只在私有 cacheDir 暂存，绝不写入工作区。
     * 流复制有 64 KiB 缓冲和硬上限，解析完成或失败都会删除。
     */
    private suspend fun stageArchive(maxBytes: Long, label: String): File {
        val job = currentCoroutineContext()[Job]
        val root = File(context.cacheDir, "external-archive")
        if (!root.exists() && !root.mkdirs()) throw IOException("无法准备 $label 临时目录")
        if (!root.isDirectory) throw IOException("无法准备 $label 临时目录")
        cleanStaleStaging(root)
        val expected = cachedInfo?.bytes ?: descriptor.bytes
        if (expected > 0 && context.cacheDir.usableSpace - expected < WorkspaceCapacity.DISK_RESERVE) {
            throw IOException("设备空间不足，无法准备此 $label 文件")
        }
        val target = File(root, UUID.randomUUID().toString())
        try {
            val input = resolver.openInputStream(uri) ?: throw IOException("无法打开此文件")
            input.use { source ->
                target.outputStream().use { output ->
                    BoundedCopy.copy(
                        source,
                        output,
                        maxBytes,
                        limitMessage = "此 $label 文件超过 ${ExternalDocumentPolicy.humanBytes(maxBytes)} 的内置预览上限" +
                            ExternalDocumentPolicy.TOO_LARGE_SUFFIX,
                    ) { copied ->
                        job?.ensureActive()
                        if (copied > 0 && copied % (4 * 1024 * 1024) < 64 * 1024 &&
                            context.cacheDir.usableSpace < WorkspaceCapacity.DISK_RESERVE) {
                            throw IOException("设备空间不足，复制已取消")
                        }
                    }
                }
            }
            return target
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            target.delete()
            throw cancelled
        } catch (failure: Throwable) {
            target.delete()
            throw ExternalDocumentAccess.failure(failure)
        }
    }

    private fun cleanStaleStaging(root: File) {
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        runCatching {
            root.listFiles()?.forEach { entry ->
                if (entry.isFile && entry.lastModified() < cutoff) entry.delete()
            }
        }
    }

    /** 直接使用原 content:// URI，不产生工作区副本，也不把写权限转交目标应用。 */
    override suspend fun prepareOpen(context: Context, onProgress: (Long, Long) -> Unit): Intent =
        withContext(Dispatchers.IO) {
            check(ExternalDocumentPolicy.canTransferOriginal(uri.toString())) {
                ExternalDocumentPolicy.INVALID_URI_MESSAGE
            }
            val info = describe()
            onProgress(0, info.bytes.coerceAtLeast(0))
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, info.mimeType).apply {
                clipData = ClipData.newRawUri(displayName, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

    override suspend fun export(output: OutputStream, onProgress: (Long, Long) -> Unit) =
        withContext(Dispatchers.IO) {
            check(ExternalDocumentPolicy.canTransferOriginal(uri.toString())) {
                ExternalDocumentPolicy.INVALID_URI_MESSAGE
            }
            val info = describe()
            try {
                val input = resolver.openInputStream(uri) ?: throw IOException("无法打开此文件")
                input.use { source ->
                    var copied = 0L
                    val buffer = ByteArray(64 * 1024)
                    onProgress(0, info.bytes.coerceAtLeast(0))
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = source.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        copied += count
                        onProgress(copied, info.bytes.coerceAtLeast(0))
                    }
                    if (info.bytes >= 0 && copied != info.bytes) {
                        throw IOException("文件在读取期间发生变化，请重试")
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                throw ExternalDocumentAccess.failure(failure)
            }
        }

    private fun <T> openStream(block: (java.io.InputStream) -> T): T {
        val input = try {
            resolver.openInputStream(uri)
        } catch (failure: Throwable) {
            throw ExternalDocumentAccess.failure(failure)
        } ?: throw IOException("无法打开此文件")
        return input.use(block)
    }

    companion object {
        /** 从落盘/恢复的描述中构建；URI 不合法时返回 null，不把任意字符串当作文件源。 */
        fun create(context: Context, descriptor: ExternalDocumentDescriptor): ContentDocument? {
            if (!ExternalDocumentPolicy.isAcceptedUri(descriptor.uri)) return null
            val uri = runCatching { Uri.parse(descriptor.uri) }.getOrNull() ?: return null
            if (uri.scheme?.lowercase(Locale.ROOT) != "content") return null
            return ContentDocument(context, uri, descriptor)
        }
    }
}
