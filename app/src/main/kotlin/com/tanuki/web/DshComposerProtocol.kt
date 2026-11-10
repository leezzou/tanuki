package com.tanuki.web

import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64
import java.util.Locale

/** 原生输入栏可提交的附件类型；图片随 prompt 发送，普通文件先换取 receipt。 */
enum class DshAttachmentKind { IMAGE, FILE }

/** 官方会话投影声明的图片接收上限；缺失时使用保守默认值。 */
data class DshImageLimits(
    val maxImageBytes: Int = 10 * 1024 * 1024,
    val maxImagesPerMessage: Int = 5,
    val maxMessageImageBytes: Int = 20 * 1024 * 1024,
    val maxImagePixels: Long = 40_000_000L,
    val maxImageDimension: Int = 8192,
    val mediaTypes: Set<String> = DshMediaTypes.SUPPORTED_IMAGES
) {
    /** 客户端内存上限低于服务端声明时取更小值，避免 base64 与解码同时占满堆。 */
    fun effectiveMaxImageBytes(): Int = minOf(maxImageBytes, DshComposerProtocol.CLIENT_MAX_IMAGE_BYTES)

    fun effectiveMaxMessageImageBytes(): Int =
        minOf(maxMessageImageBytes, DshComposerProtocol.CLIENT_MAX_MESSAGE_IMAGE_BYTES)
}

/** 从官方图片头解析出的固有尺寸。 */
data class DshImageSize(val width: Int, val height: Int)

/** 拖入接收判定只看开始阶段的 MIME 元数据；此时 Android 还不提供 clipData。 */
object DshComposerDragPolicy {
    fun accepts(mimeTypes: Set<String>): Boolean = mimeTypes.any { it.isNotBlank() }
}

/** 一次批量读取前分配的附件预算；读取阶段就拒绝超量，避免先把整批字节读进内存。 */
data class DshAttachmentReadBudget(
    val maxItems: Int,
    val remainingBytes: Long,
    val maxItemBytes: Int,
    val totalLabelBytes: Long
) {
    /** 返回当前条目可读取的最大字节；数量或总量已用尽时返回 null。 */
    fun limitFor(acceptedItems: Int, usedBytes: Long): Int? {
        if (acceptedItems >= maxItems || remainingBytes <= usedBytes) return null
        return minOf(maxItemBytes.toLong(), remainingBytes - usedBytes).toInt()
    }

    fun exhaustionMessage(acceptedItems: Int): String =
        if (acceptedItems >= maxItems) "还能添加 $maxItems 个附件"
        else "附件总大小不能超过 ${DshComposerPolicy.formatBytes(totalLabelBytes)}"
}

/** 官方 prompt 的内容块；顺序由调用方决定，原生输入栏按附件后文本发送。 */
sealed interface DshOutgoingPart {
    data class Text(val text: String) : DshOutgoingPart
    data class Image(val mediaType: String, val data: String, val name: String?) : DshOutgoingPart
    data class File(val receiptId: String) : DshOutgoingPart

    fun toJson(): JSONObject = when (this) {
        is Text -> JSONObject().put("type", "text").put("text", text)
        is Image -> JSONObject().put("type", "image").put("mediaType", mediaType).put("data", data)
            .apply { name?.takeIf { it.isNotBlank() }?.let { put("name", it) } }
        is File -> JSONObject().put("type", "file").put("receiptId", receiptId)
    }
}

/** 官方 commands/list 返回的斜杠命令描述；只保留原生补全需要的字段。 */
data class DshCommand(
    val name: String,
    val description: String,
    val inputHint: String?,
    val allowsAttachments: Boolean
)

/** 官方 fileReferences/list 返回的文件/目录候选。 */
data class DshFileReference(val path: String, val kind: String, val mention: String)

/** 官方 sessionReferenceResolver/candidates 返回的会话引用候选。 */
data class DshSessionReference(
    val mention: String,
    val sessionId: String,
    val label: String,
    val displayTitle: String,
    val sameWorkspace: Boolean,
    val createdAt: Long
)

/** 输入框中当前活动的 @ 查询；start/end 用于替换整段引用文本。 */
data class DshMentionQuery(
    val start: Int,
    val end: Int,
    val query: String,
    val quoted: Boolean
)

/**
 * 官方 DSH 输入/附件协议封装。
 *
 * 这里只构造官方已声明的 Remote 方法参数并解析其返回值，不新增端点、不复制
 * 官方 npm 包实现，也不把 token/Cookie 放进请求内容。所有 method 均通过
 * [DshApiProtocol.apiUrl] 固定到当前 loopback origin。
 */
object DshComposerProtocol {
    const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
    const val MAX_REQUEST_ID_CHARS = 128
    const val MAX_SESSION_ID_CHARS = 128
    const val MAX_TEXT_CHARS = 64_000
    const val MAX_COMMAND_LINE_CHARS = 4_000
    const val MAX_FILE_NAME_CHARS = 120
    const val MAX_REFERENCE_QUERY_CHARS = 256

    /** 普通文件与图片的客户端硬上限；服务端更小限制仍会单独生效。 */
    const val MAX_FILE_BYTES = 20 * 1024 * 1024
    const val CLIENT_MAX_IMAGE_BYTES = 20 * 1024 * 1024
    const val CLIENT_MAX_MESSAGE_IMAGE_BYTES = 30 * 1024 * 1024
    const val MAX_FILES_PER_MESSAGE = 5
    const val MAX_ATTACHMENTS_PER_MESSAGE = 10
    const val MAX_TOTAL_ATTACHMENT_BYTES = 50 * 1024 * 1024

    private val SESSION_ID = Regex("[\\x21-\\x7e]{1,$MAX_SESSION_ID_CHARS}")
    private val COMMAND_NAME = Regex("[a-z0-9][a-z0-9_-]{0,63}")

    fun validSessionId(value: String?): Boolean = value != null && SESSION_ID.matches(value)

    fun validRequestId(value: String?): Boolean =
        value != null && value.length in 1..MAX_REQUEST_ID_CHARS && value.all { it.code in 0x21..0x7e }

    /** 只接受 queue/steer 两种官方投递模式，未知值不降级为其他语义。 */
    fun validMode(value: String?): Boolean = value == "queue" || value == "steer"

    /**
     * 从 session/projections 的 `value` 或 `values.imageLimits` 读取图片上限。
     * 缺失、类型错误或明显越界时使用保守默认值，不把异常值传进内存预算。
     */
    fun parseImageLimits(value: Any?): DshImageLimits {
        val root = value as? JSONObject ?: return DshImageLimits()
        val values = root.optJSONObject("values") ?: root
        val limits = values.optJSONObject("imageLimits")
            ?: values.takeIf { it.has("maxImageBytes") }
            ?: return DshImageLimits()
        val mediaTypes = limits.optJSONArray("mediaTypes")?.let { array ->
            buildSet {
                for (index in 0 until array.length()) {
                    val mediaType = array.optString(index).lowercase(Locale.ROOT)
                    if (DshMediaTypes.isSupportedImage(mediaType)) add(mediaType)
                }
            }
        }.orEmpty().ifEmpty { DshMediaTypes.SUPPORTED_IMAGES }
        val maxBytes = limits.optInt("maxImageBytes", 10 * 1024 * 1024)
            .coerceIn(64 * 1024, 32 * 1024 * 1024)
        val maxTotal = limits.optInt("maxMessageImageBytes", 20 * 1024 * 1024)
            .coerceIn(maxBytes, 64 * 1024 * 1024)
        return DshImageLimits(
            maxImageBytes = maxBytes,
            maxImagesPerMessage = limits.optInt("maxImagesPerMessage", 5).coerceIn(1, 20),
            maxMessageImageBytes = maxTotal,
            maxImagePixels = limits.optLong("maxImagePixels", 40_000_000L).coerceIn(1L, 200_000_000L),
            maxImageDimension = limits.optInt("maxImageDimension", 8192).coerceIn(1, 32768),
            mediaTypes = mediaTypes
        )
    }

    fun commandsArgs(sessionId: String): JSONObject {
        require(validSessionId(sessionId)) { "会话标识无效" }
        return JSONObject().put("agentId", sessionId)
    }

    fun fileReferencesArgs(sessionId: String, query: String): JSONObject {
        require(validSessionId(sessionId)) { "会话标识无效" }
        return JSONObject()
            .put("agentId", sessionId)
            .put("query", query.take(MAX_REFERENCE_QUERY_CHARS))
    }

    fun sessionReferencesArgs(sessionId: String, query: String): JSONObject {
        require(validSessionId(sessionId)) { "会话标识无效" }
        return JSONObject()
            .put("agentId", sessionId)
            .put("query", query.take(MAX_REFERENCE_QUERY_CHARS))
    }

    /** 读取单会话投影；输入栏只用其中的图片上限，不改写任何会话内容。 */
    fun projectionArgs(sessionId: String): JSONObject {
        require(validSessionId(sessionId)) { "会话标识无效" }
        return JSONObject().put("request", JSONObject().put("sessionId", sessionId))
    }

    fun uploadArgs(sessionId: String, name: String?, data: String): JSONObject {
        require(validSessionId(sessionId)) { "会话标识无效" }
        require(data.isNotBlank()) { "文件内容为空" }
        val request = JSONObject().put("data", data)
        sanitizeFileName(name)?.let { request.put("name", it) }
        return JSONObject().put("agentId", sessionId).put("request", request)
    }

    fun promptArgs(
        requestId: String,
        sessionId: String,
        mode: String,
        parts: List<DshOutgoingPart>,
        clientTimeZone: String?
    ): JSONObject {
        require(validRequestId(requestId)) { "请求标识无效" }
        require(validSessionId(sessionId)) { "会话标识无效" }
        require(validMode(mode)) { "投递模式无效" }
        require(parts.isNotEmpty()) { "没有可发送的内容" }
        val content = JSONArray()
        var hasText = false
        var hasAttachment = false
        for (part in parts) {
            when (part) {
                is DshOutgoingPart.Text -> {
                    if (part.text.isBlank()) continue
                    hasText = true
                }
                is DshOutgoingPart.Image -> {
                    require(DshMediaTypes.isSupportedImage(part.mediaType)) { "图片类型不受支持" }
                    require(part.data.isNotBlank()) { "图片内容为空" }
                    hasAttachment = true
                }
                is DshOutgoingPart.File -> {
                    require(part.receiptId.isNotBlank()) { "文件凭证为空" }
                    hasAttachment = true
                }
            }
            content.put(part.toJson())
        }
        require(hasText || hasAttachment) { "没有可发送的内容" }
        val request = JSONObject()
            .put("requestId", requestId)
            .put("sessionId", sessionId)
            .put("mode", mode)
            .put("content", content)
        clientTimeZone?.takeIf { it.isNotBlank() && it.length <= 64 }?.let { request.put("clientTimeZone", it) }
        return JSONObject().put("request", request)
    }

    fun commandExecuteArgs(sessionId: String, line: String, attachments: List<DshOutgoingPart>): JSONObject {
        require(validSessionId(sessionId)) { "会话标识无效" }
        require(line.startsWith("/") && line.length <= MAX_COMMAND_LINE_CHARS) { "命令格式无效" }
        require(!line.contains('\n') && !line.contains('\r')) { "命令格式无效" }
        val submitted = JSONArray()
        for (part in attachments) {
            when (part) {
                is DshOutgoingPart.Image, is DshOutgoingPart.File -> submitted.put(part.toJson())
                is DshOutgoingPart.Text -> throw IllegalArgumentException("命令附件不能包含文本块")
            }
        }
        return JSONObject()
            .put("agentId", sessionId)
            .put("line", line)
            .put("submittedAttachments", submitted)
    }

    fun base64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    fun parseCommands(value: Any?): List<DshCommand> {
        val array = value as? JSONArray ?: return emptyList()
        val result = ArrayList<DshCommand>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val name = item.optString("name").trim().lowercase(Locale.ROOT)
            if (!COMMAND_NAME.matches(name)) continue
            val description = safeText(item.optString("description"), 240)
            val input = item.optJSONObject("input")
            result += DshCommand(
                name = name,
                description = description,
                inputHint = input?.optString("hint")?.let { safeText(it, 120) }?.takeIf { it.isNotBlank() },
                allowsAttachments = input?.optBoolean("attachments", false) == true
            )
        }
        return result.sortedBy { it.name }
    }

    fun parseFileReferences(value: Any?, query: String): List<DshFileReference> {
        val array = value as? JSONArray ?: return emptyList()
        val result = ArrayList<DshFileReference>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val path = item.optString("path")
            val kind = item.optString("kind")
            if (path.isBlank() || path.length > 1024 || path.any { it == '\u0000' || it == '\n' || it == '\r' }) continue
            if (kind != "file" && kind != "directory") continue
            val mention = DshComposerPolicy.formatFileMention(path, kind, preserveQuote = query.startsWith('"'))
                ?: continue
            result += DshFileReference(path, kind, mention)
        }
        return result
    }

    fun parseSessionReferences(value: Any?): List<DshSessionReference> {
        val array = value as? JSONArray ?: return emptyList()
        val result = ArrayList<DshSessionReference>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val mention = item.optString("mention")
            val sessionId = item.optString("sessionId")
            if (!validSessionId(sessionId)) continue
            if (mention.length !in 4..512 || !mention.startsWith("@[") || !mention.contains("](dsh-session:")) continue
            if (mention.any { it == '\u0000' || it == '\n' || it == '\r' }) continue
            val label = safeText(item.optString("label").ifBlank { sessionId.take(8) }, 160)
            val displayTitle = safeText(item.optString("displayTitle").ifBlank { label }, 160)
            result += DshSessionReference(
                mention = mention,
                sessionId = sessionId,
                label = label,
                displayTitle = displayTitle,
                sameWorkspace = item.optBoolean("sameWorkspace", false),
                createdAt = item.optLong("createdAt", 0L)
            )
        }
        return result
    }

    /** 服务端文件名只作为展示/上传字段；去掉路径与控制字符，避免伪造本地路径。 */
    fun sanitizeFileName(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val leaf = value.substringAfterLast('/').substringAfterLast('\\')
            .filterNot { it < ' ' || it == '\u007f' || it == '/' || it == '\\' }
            .trim()
            .trimStart('.')
        if (leaf.isBlank()) return null
        return leaf.take(MAX_FILE_NAME_CHARS)
    }

    private fun safeText(value: String, limit: Int): String =
        value.filterNot { it == '\u0000' }.trim().take(limit)
}

/** 图片类型嗅探与固有尺寸解析；只读取文件头，不做完整解码。 */
object DshMediaTypes {
    val SUPPORTED_IMAGES = setOf("image/png", "image/jpeg", "image/webp", "image/gif")

    fun isSupportedImage(value: String?): Boolean = value != null && value.lowercase(Locale.ROOT) in SUPPORTED_IMAGES

    fun isImage(value: String?): Boolean = value != null && value.lowercase(Locale.ROOT).startsWith("image/")

    /** 按实际字节判断图片类型，避免把扩展名/MIME 声明当成可信事实。 */
    fun sniff(bytes: ByteArray): String? {
        if (bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4e.toByte() && bytes[3] == 0x47.toByte() &&
            bytes[4] == 0x0d.toByte() && bytes[5] == 0x0a.toByte() &&
            bytes[6] == 0x1a.toByte() && bytes[7] == 0x0a.toByte()
        ) return "image/png"
        if (bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte()) {
            return "image/jpeg"
        }
        if (bytes.size >= 6) {
            val header = bytes.copyOfRange(0, 6).toString(Charsets.US_ASCII)
            if (header == "GIF87a" || header == "GIF89a") return "image/gif"
        }
        if (bytes.size >= 12 && bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF" &&
            bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII) == "WEBP"
        ) return "image/webp"
        return null
    }
}

object DshImageMetadata {
    /** 解析 PNG/JPEG/GIF/WebP 的固有宽高；损坏或超短返回 null。 */
    fun read(bytes: ByteArray): DshImageSize? = when (DshMediaTypes.sniff(bytes)) {
        "image/png" -> png(bytes)
        "image/jpeg" -> jpeg(bytes)
        "image/gif" -> gif(bytes)
        "image/webp" -> webp(bytes)
        else -> null
    }

    private fun png(bytes: ByteArray): DshImageSize? {
        if (bytes.size < 24) return null
        val width = be32(bytes, 16)
        val height = be32(bytes, 20)
        return size(width, height)
    }

    private fun gif(bytes: ByteArray): DshImageSize? {
        if (bytes.size < 10) return null
        return size(le16(bytes, 6), le16(bytes, 8))
    }

    private fun jpeg(bytes: ByteArray): DshImageSize? {
        var index = 2
        while (index + 3 < bytes.size) {
            if (bytes[index] != 0xff.toByte()) return null
            var marker = bytes[index + 1].toInt() and 0xff
            while (marker == 0xff && index + 2 < bytes.size) {
                index++
                marker = bytes[index + 1].toInt() and 0xff
            }
            index += 2
            if (marker == 0xd8 || marker == 0xd9 || marker in 0xd0..0xd7 || marker == 0x01) continue
            if (index + 1 >= bytes.size) return null
            val length = be16(bytes, index)
            if (length < 2 || index + length > bytes.size) return null
            val isStartOfFrame = marker in 0xc0..0xc3 || marker in 0xc5..0xc7 ||
                marker in 0xc9..0xcb || marker in 0xcd..0xcf
            if (isStartOfFrame) {
                if (length < 7) return null
                return size(be16(bytes, index + 5), be16(bytes, index + 3))
            }
            index += length
        }
        return null
    }

    private fun webp(bytes: ByteArray): DshImageSize? {
        if (bytes.size < 30) return null
        var index = 12
        while (index + 8 <= bytes.size) {
            val chunk = bytes.copyOfRange(index, index + 4).toString(Charsets.US_ASCII)
            val length = le32(bytes, index + 4)
            if (length < 0 || index + 8 + length > bytes.size) return null
            val data = index + 8
            when (chunk) {
                "VP8X" -> {
                    if (length < 10) return null
                    return size(le24(bytes, data + 4) + 1, le24(bytes, data + 7) + 1)
                }
                "VP8 " -> {
                    if (length < 10 || data + 9 >= bytes.size) return null
                    if (bytes[data + 3] != 0x9d.toByte() || bytes[data + 4] != 0x01.toByte() ||
                        bytes[data + 5] != 0x2a.toByte()
                    ) return null
                    return size(le16(bytes, data + 6) and 0x3fff, le16(bytes, data + 8) and 0x3fff)
                }
                "VP8L" -> {
                    if (length < 5 || bytes[data] != 0x2f.toByte()) return null
                    val bits = le32(bytes, data + 1)
                    return size((bits and 0x3fff) + 1, ((bits ushr 14) and 0x3fff) + 1)
                }
            }
            index = data + length + (length and 1)
        }
        return null
    }

    private fun size(width: Int, height: Int): DshImageSize? =
        if (width in 1..100_000 && height in 1..100_000) DshImageSize(width, height) else null

    private fun be16(bytes: ByteArray, index: Int): Int =
        ((bytes[index].toInt() and 0xff) shl 8) or (bytes[index + 1].toInt() and 0xff)

    private fun be32(bytes: ByteArray, index: Int): Int =
        ((bytes[index].toInt() and 0xff) shl 24) or ((bytes[index + 1].toInt() and 0xff) shl 16) or
            ((bytes[index + 2].toInt() and 0xff) shl 8) or (bytes[index + 3].toInt() and 0xff)

    private fun le16(bytes: ByteArray, index: Int): Int =
        (bytes[index].toInt() and 0xff) or ((bytes[index + 1].toInt() and 0xff) shl 8)

    private fun le24(bytes: ByteArray, index: Int): Int =
        (bytes[index].toInt() and 0xff) or ((bytes[index + 1].toInt() and 0xff) shl 8) or
            ((bytes[index + 2].toInt() and 0xff) shl 16)

    private fun le32(bytes: ByteArray, index: Int): Int =
        (bytes[index].toInt() and 0xff) or ((bytes[index + 1].toInt() and 0xff) shl 8) or
            ((bytes[index + 2].toInt() and 0xff) shl 16) or ((bytes[index + 3].toInt() and 0xff) shl 24)
}

/** 附件与引用输入的纯策略；返回中文错误，空字符串表示通过。 */
object DshComposerPolicy {
    fun validateImage(
        mediaType: String,
        bytes: Int,
        width: Int?,
        height: Int?,
        limits: DshImageLimits,
        existingImages: Int,
        existingImageBytes: Long,
        existingTotalBytes: Long
    ): String? {
        val normalized = mediaType.lowercase(Locale.ROOT)
        if (!DshMediaTypes.isSupportedImage(normalized) || normalized !in limits.mediaTypes) {
            return "不支持该图片格式，请选择 PNG、JPEG、WebP 或 GIF"
        }
        if (existingImages >= limits.maxImagesPerMessage) {
            return "每条消息最多添加 ${limits.maxImagesPerMessage} 张图片"
        }
        val maxBytes = limits.effectiveMaxImageBytes()
        if (bytes > maxBytes) return "单张图片不能超过 ${formatBytes(maxBytes)}"
        if (existingImageBytes + bytes > limits.effectiveMaxMessageImageBytes()) {
            return "图片总大小不能超过 ${formatBytes(limits.effectiveMaxMessageImageBytes())}，请移除部分图片"
        }
        if (existingTotalBytes + bytes > DshComposerProtocol.MAX_TOTAL_ATTACHMENT_BYTES) {
            return "附件总大小不能超过 ${formatBytes(DshComposerProtocol.MAX_TOTAL_ATTACHMENT_BYTES)}"
        }
        if (width == null || height == null || width <= 0 || height <= 0) return "无法读取图片尺寸，请更换图片"
        if (width > limits.maxImageDimension || height > limits.maxImageDimension) {
            return "图片宽高不能超过 ${limits.maxImageDimension} 像素，请缩小后重试"
        }
        val pixels = width.toLong() * height.toLong()
        if (pixels > limits.maxImagePixels) {
            return "图片像素总量不能超过 ${limits.maxImagePixels}，请缩小后重试"
        }
        return null
    }

    fun validateFile(
        bytes: Int,
        existingFiles: Int,
        existingTotalBytes: Long
    ): String? {
        if (bytes <= 0) return "文件内容为空"
        if (bytes > DshComposerProtocol.MAX_FILE_BYTES) {
            return "单个文件不能超过 ${formatBytes(DshComposerProtocol.MAX_FILE_BYTES)}"
        }
        if (existingFiles >= DshComposerProtocol.MAX_FILES_PER_MESSAGE) {
            return "每条消息最多添加 ${DshComposerProtocol.MAX_FILES_PER_MESSAGE} 个文件"
        }
        if (existingTotalBytes + bytes > DshComposerProtocol.MAX_TOTAL_ATTACHMENT_BYTES) {
            return "附件总大小不能超过 ${formatBytes(DshComposerProtocol.MAX_TOTAL_ATTACHMENT_BYTES)}"
        }
        return null
    }

    /** 与官方文件提及语法一致：含空白使用引号，目录保留尾斜杠并保持未闭合引号。 */
    fun formatFileMention(path: String, kind: String, preserveQuote: Boolean = false): String? {
        if (path.isBlank() || path.length > 1024) return null
        if (path.any { it < ' ' || it == '\u007f' || it == '"' }) return null
        val value = if (kind == "directory") "$path/" else path
        return when {
            preserveQuote && kind == "directory" -> "@\"$value"
            preserveQuote -> "@\"$value\""
            value.any { it.isWhitespace() } && kind == "directory" -> "@\"$value"
            value.any { it.isWhitespace() } -> "@\"$value\""
            else -> "@$value"
        }
    }

    /** 光标前同一行内、由空白或行首引出的 @ 查询；引号内的空格不截断。 */
    fun mentionQuery(text: String, cursor: Int): DshMentionQuery? {
        if (cursor !in 0..text.length) return null
        val lineStart = text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        var at = -1
        for (index in cursor - 1 downTo lineStart) {
            if (text[index] != '@') continue
            if (index > 0 && !text[index - 1].isWhitespace() && text[index - 1] != '\n') continue
            at = index
            break
        }
        if (at < 0) return null
        val quoted = at + 1 < cursor && text[at + 1] == '"'
        val queryStart = at + if (quoted) 2 else 1
        if (queryStart > cursor) return null
        val query = text.substring(queryStart, cursor)
        if (query.length > DshComposerProtocol.MAX_REFERENCE_QUERY_CHARS) return null
        // 引号查询遇到结束引号即视为已完成，不再弹出补全。
        if (quoted && query.contains('"')) return null
        if (!quoted && query.any { it.isWhitespace() }) return null
        if (query.any { it == '\u0000' || it == '\n' || it == '\r' }) return null
        return DshMentionQuery(at, cursor, query, quoted)
    }

    /** 仅在首行输入 `/` 且尚未输入空白时提供命令补全。 */
    fun slashQuery(text: String, cursor: Int): String? {
        if (cursor !in 1..text.length || !text.startsWith('/')) return null
        val prefix = text.substring(0, cursor)
        if (prefix.contains('\n') || prefix.any { it.isWhitespace() }) return null
        val token = prefix.drop(1)
        if (token.length > 64) return null
        return token
    }

    /** 已识别的首行斜杠命令；未知命令仍作为普通提示词发送。 */
    fun commandFor(text: String, commands: List<DshCommand>): DshCommand? {
        val line = text.trimStart()
        if (!line.startsWith('/')) return null
        val token = line.drop(1).substringBefore(' ').substringBefore('\n').substringBefore('\t')
        if (token.isBlank()) return null
        return commands.firstOrNull { it.name == token }
    }

    fun commandLine(text: String): String = text.trim()

    fun formatBytes(bytes: Int): String = formatBytes(bytes.toLong())

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return if (kb == kb.toLong().toDouble()) "${kb.toLong()} KB" else String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        return if (mb == mb.toLong().toDouble()) "${mb.toLong()} MB" else String.format(Locale.US, "%.1f MB", mb)
    }
}
