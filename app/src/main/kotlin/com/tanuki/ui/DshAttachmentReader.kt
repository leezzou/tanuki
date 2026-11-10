package com.tanuki.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tanuki.web.DshAttachmentKind
import com.tanuki.web.DshAttachmentReadBudget
import com.tanuki.web.DshComposerPolicy
import com.tanuki.web.DshComposerProtocol
import com.tanuki.web.DshImageMetadata
import com.tanuki.web.DshMediaTypes
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Base64

data class DshAttachmentReadResult(
    val items: List<DshPickedAttachment> = emptyList(),
    val errors: List<String> = emptyList()
) {
    fun merge(other: DshAttachmentReadResult) = DshAttachmentReadResult(
        items = items + other.items,
        errors = errors + other.errors
    )
}

/**
 * 只读取用户明确选择的文档 URI 或剪贴板/拖入内容。
 *
 * 读取前先查大小，未知大小时按剩余预算 + 1 字节读取；图片按实际字节嗅探类型，
 * 不把文件名或 MIME 声明当成可信事实。批量读取在分配字节前执行数量和总量预算。
 */
object DshAttachmentReader {
    private val DATA_IMAGE = Regex(
        "data:(image/(?:png|jpeg|webp|gif));base64,([A-Za-z0-9+/=\\s]+)",
        RegexOption.IGNORE_CASE
    )

    fun readUris(
        context: Context,
        uris: List<Uri>,
        kind: DshAttachmentKind,
        budget: DshAttachmentReadBudget = defaultBudget(kind)
    ): DshAttachmentReadResult {
        val items = ArrayList<DshPickedAttachment>()
        val errors = ArrayList<String>()
        var usedBytes = 0L
        for (uri in uris.distinct()) {
            val limit = budget.limitFor(items.size, usedBytes)
            if (limit == null) {
                errors += budget.exhaustionMessage(items.size)
                break
            }
            val limitMessage = if (limit < budget.maxItemBytes) {
                "附件总大小不能超过 ${DshComposerPolicy.formatBytes(budget.totalLabelBytes)}"
            } else {
                "单个附件不能超过 ${DshComposerPolicy.formatBytes(limit)}"
            }
            readUri(context, uri, kind, limit, limitMessage)
                .onSuccess {
                    items += it
                    usedBytes += it.bytes.size
                }
                .onFailure { errors += it.message ?: "附件读取失败" }
        }
        return DshAttachmentReadResult(items, errors)
    }

    /** 剪贴板只接受图片；普通文本不会被当成文件上传。 */
    fun readClipboard(context: Context, budget: DshAttachmentReadBudget = defaultBudget(DshAttachmentKind.IMAGE)): DshAttachmentReadResult {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return DshAttachmentReadResult(errors = listOf("当前系统不支持读取剪贴板"))
        val clip = runCatching { manager.primaryClip }.getOrElse {
            return DshAttachmentReadResult(errors = listOf("无法读取剪贴板，请改用图片选择器"))
        } ?: return DshAttachmentReadResult(errors = listOf("剪贴板没有可用的图片"))
        val uris = ArrayList<Uri>()
        val dataUris = ArrayList<DshPickedAttachment>()
        var dataBytes = 0L
        for (index in 0 until clip.itemCount) {
            val item = clip.getItemAt(index)
            val uri = item.uri
            if (uri != null) {
                val type = runCatching { context.contentResolver.getType(uri) }.getOrNull()
                // 部分选择器把图片标成 application/octet-stream 或 null；交给字节嗅探判断。
                if (type == null || type.startsWith("image/")) {
                    uris += uri
                    continue
                }
            }
            val text = item.htmlText ?: item.text
            // 剪贴板 HTML 可能包含整页内容；只扫描有限长度，避免在主线程外仍做无界正则。
            if (!text.isNullOrBlank() && text.length <= 8 * 1024 * 1024) {
                for (match in DATA_IMAGE.findAll(text)) {
                    val mediaType = match.groupValues[1].lowercase()
                    val encoded = match.groupValues[2].filterNot { it.isWhitespace() }
                    if (encoded.isEmpty()) continue
                    val limit = budget.limitFor(dataUris.size, dataBytes)
                        ?: return DshAttachmentReadResult(
                            dataUris,
                            listOf(budget.exhaustionMessage(dataUris.size))
                        )
                    val bytes = runCatching { Base64.getDecoder().decode(encoded) }.getOrNull() ?: continue
                    if (bytes.isEmpty()) continue
                    if (bytes.size > limit) {
                        return DshAttachmentReadResult(
                            dataUris,
                            listOf(
                                if (limit < budget.maxItemBytes) {
                                    "附件总大小不能超过 ${DshComposerPolicy.formatBytes(budget.totalLabelBytes)}"
                                } else {
                                    "单张图片不能超过 ${DshComposerPolicy.formatBytes(limit)}"
                                }
                            )
                        )
                    }
                    val sniffed = DshMediaTypes.sniff(bytes) ?: continue
                    val size = DshImageMetadata.read(bytes) ?: continue
                    dataUris += DshPickedAttachment(
                        name = "粘贴的图片",
                        mediaType = sniffed,
                        bytes = bytes,
                        kind = DshAttachmentKind.IMAGE,
                        width = size.width,
                        height = size.height
                    )
                    dataBytes += bytes.size
                }
            }
        }
        val remainingBudget = budget.copy(
            maxItems = (budget.maxItems - dataUris.size).coerceAtLeast(0),
            remainingBytes = (budget.remainingBytes - dataBytes).coerceAtLeast(0)
        )
        val fromUris = if (uris.isEmpty() || remainingBudget.maxItems <= 0 || remainingBudget.remainingBytes <= 0) {
            DshAttachmentReadResult()
        } else {
            readUris(context, uris, DshAttachmentKind.IMAGE, remainingBudget)
        }
        val merged = fromUris.copy(items = dataUris + fromUris.items)
        return if (merged.items.isEmpty()) {
            DshAttachmentReadResult(
                errors = merged.errors.ifEmpty { listOf("剪贴板没有可用的图片") }
            )
        } else merged
    }

    /** 拖入内容可能是图片也可能是普通文件；按每个 URI 的实际 MIME 分流并共享剩余预算。 */
    fun readDrop(
        context: Context,
        clip: ClipData,
        imageBudget: DshAttachmentReadBudget,
        fileBudget: DshAttachmentReadBudget
    ): DshAttachmentReadResult {
        val images = ArrayList<Uri>()
        val files = ArrayList<Uri>()
        for (index in 0 until clip.itemCount) {
            val uri = clip.getItemAt(index).uri ?: continue
            val type = runCatching { context.contentResolver.getType(uri) }.getOrNull()
            if (type != null && type.startsWith("image/")) images += uri else files += uri
        }
        val imageResult = readUris(context, images, DshAttachmentKind.IMAGE, imageBudget)
        val usedByImages = imageResult.items.sumOf { it.bytes.size.toLong() }
        val remainingFileBudget = fileBudget.copy(
            maxItems = (fileBudget.maxItems - imageResult.items.size).coerceAtLeast(0),
            remainingBytes = (fileBudget.remainingBytes - usedByImages).coerceAtLeast(0)
        )
        val fileResult = if (files.isEmpty() || remainingFileBudget.maxItems <= 0 || remainingFileBudget.remainingBytes <= 0) {
            DshAttachmentReadResult()
        } else {
            readUris(context, files, DshAttachmentKind.FILE, remainingFileBudget)
        }
        return imageResult.merge(fileResult)
    }

    private fun readUri(
        context: Context,
        uri: Uri,
        kind: DshAttachmentKind,
        maxBytes: Int,
        limitMessage: String
    ): Result<DshPickedAttachment> = runCatching {
        require(uri.scheme == "content" && !uri.authority.isNullOrBlank() && uri.userInfo == null) {
            "只能读取文档选择器提供的本地内容"
        }
        val resolver = context.contentResolver
        val declaredType = runCatching { resolver.getType(uri) }.getOrNull()
        val name = queryName(context, uri) ?: if (kind == DshAttachmentKind.IMAGE) "图片" else "附件"
        val knownSize = querySize(context, uri)
        if (knownSize != null && knownSize > maxBytes) throw IllegalArgumentException(limitMessage)
        val bytes = readBounded(resolver.openInputStream(uri), maxBytes, limitMessage)
        if (bytes.isEmpty()) throw IllegalArgumentException("附件内容为空")
        if (kind == DshAttachmentKind.IMAGE) {
            val mediaType = DshMediaTypes.sniff(bytes)
                ?: throw IllegalArgumentException("不支持该图片格式，请选择 PNG、JPEG、WebP 或 GIF")
            // 以实际字节为准；部分文档提供方会把图片声明成 octet-stream。
            val size = DshImageMetadata.read(bytes)
                ?: throw IllegalArgumentException("无法读取图片尺寸，请更换图片")
            DshPickedAttachment(
                name = name,
                mediaType = mediaType,
                bytes = bytes,
                kind = DshAttachmentKind.IMAGE,
                width = size.width,
                height = size.height,
                previewUri = uri.toString()
            )
        } else {
            DshPickedAttachment(
                name = name,
                mediaType = declaredType ?: "application/octet-stream",
                bytes = bytes,
                kind = DshAttachmentKind.FILE
            )
        }
    }

    private fun readBounded(stream: InputStream?, maxBytes: Int, limitMessage: String): ByteArray {
        if (stream == null) throw IllegalArgumentException("无法打开附件")
        stream.use {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val read = it.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxBytes) throw IllegalArgumentException(limitMessage)
                output.write(buffer, 0, read)
            }
            return output.toByteArray()
        }
    }

    private fun defaultBudget(kind: DshAttachmentKind): DshAttachmentReadBudget =
        DshAttachmentReadBudget(
            maxItems = DshComposerProtocol.MAX_ATTACHMENTS_PER_MESSAGE,
            remainingBytes = DshComposerProtocol.MAX_TOTAL_ATTACHMENT_BYTES.toLong(),
            maxItemBytes = if (kind == DshAttachmentKind.IMAGE) {
                DshComposerProtocol.CLIENT_MAX_IMAGE_BYTES
            } else DshComposerProtocol.MAX_FILE_BYTES,
            totalLabelBytes = DshComposerProtocol.MAX_TOTAL_ATTACHMENT_BYTES.toLong()
        )

    private fun queryName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0)?.takeIf { it.isNotBlank() } else null
        }
    }.getOrNull()

    private fun querySize(context: Context, uri: Uri): Long? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0).takeIf { it >= 0 } else null
        }
    }.getOrNull()
}
