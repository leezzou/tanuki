package com.tanuki.storage

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.FileNotFoundException
import java.util.Locale

/**
 * 外部 content:// 文件的 Android 接入层：申请持久读权限、读取显示元数据。
 *
 * 只接受 content:// 且带非空 authority 的 URI；不把 file:// 路径或网页 URL 当作
 * 可打开文件。元数据查询失败、权限失效由上层转换为中文错误，不静默退回复制。
 */
internal object ExternalDocumentAccess {
    data class Metadata(val name: String?, val mimeType: String?, val bytes: Long)

    /**
     * 申请长期读取权限。并非所有提供方都支持持久授权：返回 false 时本次会话仍可读，
     * 但界面必须提示进程重建后可能需要重新选择，不能假装已经持久化。
     */
    fun persistReadPermission(context: Context, uri: Uri): Boolean {
        if (!ExternalDocumentPolicy.isAcceptedUri(uri.toString())) return false
        return runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            true
        }.getOrDefault(false)
    }

    fun descriptor(context: Context, uri: Uri, persistedRead: Boolean): ExternalDocumentDescriptor {
        require(ExternalDocumentPolicy.isAcceptedUri(uri.toString())) { ExternalDocumentPolicy.INVALID_URI_MESSAGE }
        val metadata = metadata(context, uri)
        val name = ExternalDocumentPolicy.sanitizeDisplayName(
            metadata.name ?: uri.lastPathSegment,
            fallback = "外部文件",
        )
        val mime = metadata.mimeType?.takeIf { it.isNotBlank() }
            ?: mimeFromName(name)
            ?: "application/octet-stream"
        return ExternalDocumentDescriptor(
            uri = uri.toString(),
            displayName = name,
            mimeType = mime,
            bytes = metadata.bytes,
            persistedRead = persistedRead,
        )
    }

    fun metadata(context: Context, uri: Uri): Metadata {
        var name: String? = null
        var bytes = ExternalDocumentPolicy.UNKNOWN_SIZE
        // 部分提供方不支持 query，但仍可打开流；元数据缺失不阻止直接预览。
        val cursor: Cursor? = try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )
        } catch (failure: SecurityException) {
            throw failure
        } catch (_: Exception) {
            null
        }
        cursor?.use { value ->
            if (value.moveToFirst()) {
                val nameIndex = value.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = value.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0 && !value.isNull(nameIndex)) name = value.getString(nameIndex)
                if (sizeIndex >= 0 && !value.isNull(sizeIndex)) bytes = value.getLong(sizeIndex)
            }
        }
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        return Metadata(name, mime, bytes)
    }

    private fun mimeFromName(name: String): String? {
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (extension.isEmpty()) return null
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    }

    /** 把提供方异常转换成用户能执行的说明；不输出 URI 或提供方内部信息。 */
    fun failure(failure: Throwable): Exception = when (failure) {
        is SecurityException, is FileNotFoundException ->
            java.io.IOException(ExternalDocumentPolicy.PERMISSION_LOST_MESSAGE, failure)
        is UnsupportedOperationException ->
            java.io.IOException(ExternalDocumentPolicy.UNSUPPORTED_PROVIDER_MESSAGE, failure)
        is java.io.IOException -> failure
        else -> java.io.IOException("无法读取此文件，请重试或复制到工作区后再打开。", failure)
    }
}
