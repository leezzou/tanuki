package com.tanuki.storage

import com.tanuki.storage.WorkspaceFileManager.FileKind
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale

/**
 * 外部 content:// 文档的纯策略层：URI 来源校验、显示名清洗、内置预览大小上限和失败分类。
 *
 * 这里不接触 Android API，便于 JVM 回归验证；真正的权限申请和流读取由
 * [ExternalDocumentAccess] / [ContentDocument] 完成。策略只允许系统文件选择器或
 * 其他应用显式授予读权限的 content:// URI，不接受 file/http 等可绕过授权检查的来源。
 */
internal data class ExternalDocumentDescriptor(
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val bytes: Long,
    val persistedRead: Boolean,
    /** 每次成功打开/重新选择递增；查看器与子预览用它作为读取代次，同一 URI 也会重新读取。 */
    val openToken: Long = 0,
) {
    /** 保存到 Activity Bundle 的稳定字段；进程重建后按同一份描述恢复。 */
    fun savedFields(): Map<String, Any> = linkedMapOf(
        "uri" to uri,
        "name" to displayName,
        "mime" to mimeType,
        "bytes" to bytes,
        "persisted" to persistedRead,
        "openToken" to openToken,
    )

    companion object {
        fun restore(
            uri: String?,
            name: String?,
            mime: String?,
            bytes: Long,
            persisted: Boolean,
            openToken: Long = 0,
        ): ExternalDocumentDescriptor? {
            if (!ExternalDocumentPolicy.isAcceptedUri(uri)) return null
            return ExternalDocumentDescriptor(
                uri = uri!!,
                displayName = ExternalDocumentPolicy.sanitizeDisplayName(name),
                mimeType = mime?.takeIf { it.isNotBlank() } ?: "application/octet-stream",
                bytes = bytes,
                persistedRead = persisted,
                openToken = openToken,
            )
        }
    }
}

/** Activity 保存/恢复的完整状态；即使查看器已关闭，也保留已消费的启动请求。 */
internal data class ExternalDocumentSaveState(
    val descriptor: ExternalDocumentDescriptor? = null,
    val consumedRequest: String? = null,
) {
    fun entries(): Map<String, Any> = linkedMapOf<String, Any>().apply {
        descriptor?.let { descriptor ->
            put("externalUri", descriptor.uri)
            put("externalName", descriptor.displayName)
            put("externalMime", descriptor.mimeType)
            put("externalBytes", descriptor.bytes)
            put("externalPersisted", descriptor.persistedRead)
            put("externalOpenToken", descriptor.openToken)
        }
        consumedRequest?.let { put("externalConsumedRequest", it) }
    }

    companion object {
        fun restore(
            uri: String?,
            name: String?,
            mime: String?,
            bytes: Long,
            persisted: Boolean,
            openToken: Long,
            consumedRequest: String?,
        ): ExternalDocumentSaveState = ExternalDocumentSaveState(
            descriptor = ExternalDocumentDescriptor.restore(uri, name, mime, bytes, persisted, openToken),
            consumedRequest = consumedRequest,
        )
    }
}

internal object ExternalDocumentPolicy {
    const val MAX_URI_LENGTH = 4096
    const val MAX_DISPLAY_NAME = 255
    const val MAX_IMAGE_BYTES = 64L * 1024 * 1024
    const val MAX_OFFICE_BYTES = 64L * 1024 * 1024
    const val MAX_PDF_BYTES = 512L * 1024 * 1024
    const val UNKNOWN_SIZE = -1L

    const val NON_PERSISTED_WARNING =
        "此来源没有授予长期读取权限；应用进程重建后可能需要重新选择文件。"
    const val PERMISSION_LOST_MESSAGE =
        "无法访问此文件：读取权限已失效，或文件已被移动、删除。请重新选择文件，或复制到工作区后再打开。"
    const val UNSUPPORTED_PROVIDER_MESSAGE =
        "此文件提供方不支持直接读取，请复制到工作区或使用其他应用打开。"
    const val INVALID_URI_MESSAGE =
        "不支持此文件来源：只允许打开系统文件选择器提供的 content:// 文件。"
    const val TOO_LARGE_SUFFIX =
        "；请使用其他应用打开，或复制到工作区后处理。"

    enum class Status { READY, PERMISSION_LOST, UNSUPPORTED_PROVIDER, TOO_LARGE, INVALID_URI }

    data class Decision(
        val status: Status,
        val message: String? = null,
        val copyFallback: Boolean = false,
    ) {
        val ready: Boolean get() = status == Status.READY
    }

    /** 元数据/导出/转交原 URI 只看来源是否合法，不受内置预览大小上限影响。 */
    fun canTransferOriginal(uri: String?): Boolean = isAcceptedUri(uri)

    /** 查看器/子预览的稳定键；同一 URI 重新选择时 token 变化会触发新的读取。 */
    fun contentKey(uri: String, openToken: Long): String = "content:$uri#$openToken"

    /** 从完整描述派生查看器键，确保 openToken 真正进入 ContentDocument.key。 */
    fun viewerKey(descriptor: ExternalDocumentDescriptor): String =
        contentKey(descriptor.uri, descriptor.openToken)

    /** Activity 恢复时把计数器推进到至少已恢复的 token，下一次重选必然得到更大的 token。 */
    fun restoredTokenSequence(currentSequence: Long, restoredToken: Long): Long =
        maxOf(currentSequence, restoredToken)

    /**
     * 计算 ACTION_VIEW/ACTION_SEND 的外部打开请求身份，用于避免 Activity 重建时重放已消费的启动 Intent。
     * 仅接受可直开的 content://；其他 action/来源返回 null。
     */
    fun requestId(action: String?, data: String?, stream: String?): String? {
        val uri = when (action) {
            "android.intent.action.VIEW" -> data
            "android.intent.action.SEND" -> stream
            else -> null
        }
        if (!isAcceptedUri(uri)) return null
        return "$action|$uri"
    }

    /** 新请求总是消费；重建时只有与已消费身份不同才消费，关闭后重建也不会重开旧文件。 */
    fun shouldConsume(restored: Boolean, consumedRequest: String?, requestId: String?): Boolean =
        requestId != null && (!restored || requestId != consumedRequest)

    /** content://<authority>/...，拒绝空 authority、userInfo、控制字符和超长 URI。 */
    fun isAcceptedUri(raw: String?): Boolean {
        if (raw == null || raw.length !in 1..MAX_URI_LENGTH) return false
        if (raw.any { it == '\u0000' || it == '\n' || it == '\r' || it == '\t' }) return false
        val prefix = "content://"
        if (!raw.startsWith(prefix, ignoreCase = true)) return false
        val rest = raw.substring(prefix.length)
        val authority = rest.substringBefore('/').substringBefore('?').substringBefore('#')
        if (authority.isBlank() || authority.contains('@')) return false
        if (authority.any { it.isWhitespace() }) return false
        return true
    }

    /** 显示名只用于界面和导出文件名，去掉路径分隔符与控制字符，长度有上限。 */
    fun sanitizeDisplayName(raw: String?, fallback: String = "外部文件"): String {
        val candidate = raw
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.filter { it.code >= 0x20 && it != '\u007f' }
            ?.trim()
            .orEmpty()
        if (candidate.isEmpty() || candidate == "." || candidate == "..") return fallback
        return candidate.take(MAX_DISPLAY_NAME).ifBlank { fallback }
    }

    /** 按类型给出内置预览的编码后文件上限；文本分页读取，不设整文件上限。 */
    fun sizeLimit(kind: FileKind): Long? = when (kind) {
        FileKind.PDF -> MAX_PDF_BYTES
        FileKind.DOCX, FileKind.XLSX -> MAX_OFFICE_BYTES
        FileKind.IMAGE -> MAX_IMAGE_BYTES
        else -> null
    }

    fun tooLarge(kind: FileKind, bytes: Long): Boolean {
        val limit = sizeLimit(kind) ?: return false
        return bytes > limit
    }

    fun tooLargeMessage(kind: FileKind, bytes: Long): String {
        val limit = sizeLimit(kind) ?: return ""
        val label = when (kind) {
            FileKind.PDF -> "PDF"
            FileKind.DOCX -> "DOCX"
            FileKind.XLSX -> "XLSX"
            FileKind.IMAGE -> "图片"
            else -> "文件"
        }
        return "此$label 为 ${humanBytes(bytes)}，超过内置预览上限 ${humanBytes(limit)}$TOO_LARGE_SUFFIX"
    }

    /** 内置查看器是否直接支持；其他类型保留显式复制和其他应用打开。 */
    fun builtInPreview(kind: FileKind): Boolean = when (kind) {
        FileKind.TEXT, FileKind.IMAGE, FileKind.AUDIO, FileKind.VIDEO, FileKind.PDF, FileKind.DOCX, FileKind.XLSX -> true
        FileKind.DOC, FileKind.OTHER -> false
    }

    /** 无内置预览的类型必须由用户显式选择复制，不能自动整文件写入工作区。 */
    fun copyIsExplicitFallback(kind: FileKind): Boolean = !builtInPreview(kind)

    /**
     * 根据来源、权限持久化结果、大小和读取失败原因决定界面状态。
     * 权限失效与提供方不支持都保留“复制到工作区”这一显式回退入口。
     */
    fun decide(
        uri: String?,
        kind: FileKind,
        bytes: Long,
        persistedRead: Boolean,
        failure: Throwable? = null,
    ): Decision {
        if (!isAcceptedUri(uri)) return Decision(Status.INVALID_URI, INVALID_URI_MESSAGE)
        when (failure) {
            is SecurityException, is FileNotFoundException ->
                return Decision(Status.PERMISSION_LOST, PERMISSION_LOST_MESSAGE, copyFallback = true)
            is UnsupportedOperationException ->
                return Decision(Status.UNSUPPORTED_PROVIDER, UNSUPPORTED_PROVIDER_MESSAGE, copyFallback = true)
            is IOException -> {
                val message = failure.message.orEmpty()
                if (message.contains("不支持直接读取") || message.contains("无法打开描述符")) {
                    return Decision(Status.UNSUPPORTED_PROVIDER, UNSUPPORTED_PROVIDER_MESSAGE, copyFallback = true)
                }
            }
        }
        if (tooLarge(kind, bytes)) {
            return Decision(Status.TOO_LARGE, tooLargeMessage(kind, bytes), copyFallback = true)
        }
        return Decision(Status.READY, if (persistedRead) null else NON_PERSISTED_WARNING)
    }

    fun humanBytes(bytes: Long): String {
        if (bytes < 0) return "大小未知"
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(Locale.ROOT, "%.1f KiB", bytes / 1024.0)
            bytes < 1024L * 1024 * 1024 -> String.format(Locale.ROOT, "%.1f MiB", bytes / (1024.0 * 1024))
            else -> String.format(Locale.ROOT, "%.1f GiB", bytes / (1024.0 * 1024 * 1024))
        }
    }
}

/**
 * 有界流复制：ZIP 容器暂存、导入回退和导出共用。
 * 超过 [maxBytes] 立即失败，不会把整个外部文件读入内存；调用方负责删除未完成目标。
 */
internal object BoundedCopy {
    private const val BUFFER = 64 * 1024

    fun copy(
        input: InputStream,
        output: OutputStream,
        maxBytes: Long,
        limitMessage: String = "文件超过允许的大小上限",
        onProgress: (Long) -> Unit = {},
    ): Long {
        require(maxBytes >= 0) { "复制上限无效" }
        var total = 0L
        var idleReads = 0
        onProgress(0)
        val buffer = ByteArray(BUFFER)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) {
                if (++idleReads > 8) throw IOException("文件来源暂时无法读取")
                continue
            }
            idleReads = 0
            total += count
            if (total > maxBytes) throw IOException(limitMessage)
            output.write(buffer, 0, count)
            onProgress(total)
        }
        return total
    }
}
