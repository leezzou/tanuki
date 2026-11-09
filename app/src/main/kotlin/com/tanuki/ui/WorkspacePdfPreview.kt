package com.tanuki.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.tanuki.storage.WorkspaceFileManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale

/** 打开失败的 PDF 使用固定中文提示，避免把底层英文异常直接展示给用户。 */
internal class PdfPreviewException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** 一次打开/渲染/关闭的 PdfRenderer 会话；调用方须保证在同一线程内创建和使用。 */
internal class PdfDocumentSession private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) : AutoCloseable {
    val pageCount: Int get() = renderer.pageCount

    fun renderPage(
        index: Int,
        maxDimension: Int = PdfRenderPolicy.MAX_DIMENSION,
        maxPixels: Int = PdfRenderPolicy.MAX_PIXELS,
    ): Bitmap {
        require(index in 0 until pageCount) { "PDF 页码超出范围" }
        val page = renderer.openPage(index)
        try {
            val size = PdfRenderPolicy.targetSize(page.width, page.height, maxDimension, maxPixels)
            val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
            try {
                // PDF 页面本身是透明背景；铺白底避免深色主题下黑字看不清。
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            } catch (failure: Throwable) {
                bitmap.recycle()
                throw failure
            }
            return bitmap
        } finally {
            page.close()
        }
    }

    override fun close() {
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
    }

    companion object {
        fun open(descriptor: ParcelFileDescriptor): PdfDocumentSession {
            val renderer = try {
                PdfRenderer(descriptor)
            } catch (failure: Exception) {
                runCatching { descriptor.close() }
                throw PdfPreviewException(openFailureMessage(failure), failure)
            }
            if (renderer.pageCount <= 0) {
                runCatching { renderer.close() }
                runCatching { descriptor.close() }
                throw PdfPreviewException("此 PDF 没有可显示的页面。")
            }
            return PdfDocumentSession(descriptor, renderer)
        }

        private fun openFailureMessage(failure: Exception): String {
            val detail = failure.message.orEmpty().lowercase(Locale.ROOT)
            return when {
                "encrypt" in detail || "password" in detail || failure is SecurityException ->
                    "此 PDF 已加密，暂不支持内置预览；可使用其他应用打开。"
                "no page" in detail || "page count" in detail ->
                    "此 PDF 没有可显示的页面。"
                else -> "此 PDF 无法打开：文件可能已加密、损坏或格式不受支持。"
            }
        }
    }
}

private data class PdfRenderResult(val pageCount: Int, val index: Int, val bitmap: Bitmap)

/** 内置 PDF 逐页渲染；上一页/下一页、页码和缩放平移都在原生界面完成。 */
@Composable
internal fun PdfPreview(
    files: WorkspaceFileManager,
    path: String,
    modifier: Modifier = Modifier,
    maxDimension: Int = PdfRenderPolicy.MAX_DIMENSION,
    maxPixels: Int = PdfRenderPolicy.MAX_PIXELS,
) {
    var pageCount by remember(path) { mutableIntStateOf(0) }
    var pageIndex by remember(path) { mutableIntStateOf(0) }
    var bitmap by remember(path) { mutableStateOf<Bitmap?>(null) }
    var loading by remember(path) { mutableStateOf(true) }
    var error by remember(path) { mutableStateOf<String?>(null) }
    var zoom by remember(path) { mutableFloatStateOf(1f) }
    DisposableEffect(bitmap) {
        val current = bitmap
        onDispose { current?.takeUnless { it.isRecycled }?.recycle() }
    }
    LaunchedEffect(path, pageIndex, maxDimension, maxPixels) {
        loading = true
        error = null
        bitmap = null
        var pending: Bitmap? = null
        try {
            val result = withContext(Dispatchers.IO) {
                val descriptor = files.openPreviewDescriptor(path)
                var session: PdfDocumentSession? = null
                try {
                    session = PdfDocumentSession.open(descriptor)
                    val index = pageIndex.coerceIn(0, session.pageCount - 1)
                    PdfRenderResult(session.pageCount, index, session.renderPage(index, maxDimension, maxPixels).also { pending = it })
                } finally {
                    if (session != null) session.close() else runCatching { descriptor.close() }
                }
            }
            currentCoroutineContext().ensureActive()
            pageCount = result.pageCount
            pageIndex = result.index
            bitmap = result.bitmap
            pending = null
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            error = pdfFailureMessage(failure)
        } finally {
            pending?.takeUnless { it.isRecycled }?.recycle()
            loading = false
        }
    }
    Column(modifier.fillMaxWidth()) {
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
        Box(
            Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow),
            contentAlignment = Alignment.Center,
        ) {
            val current = bitmap
            if (current != null) {
                Zoomable(
                    contentWidth = current.width,
                    contentHeight = current.height,
                    resetKey = "$path#$pageIndex",
                    modifier = Modifier.fillMaxSize(),
                    onScaleChange = { zoom = it },
                ) { contentModifier ->
                    Image(current.asImageBitmap(), "PDF 第 ${pageIndex + 1} 页", contentModifier, contentScale = ContentScale.Fit)
                }
            } else if (!loading && error == null) {
                Text("此 PDF 没有可显示的页面。", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = { pageIndex-- }, enabled = pageIndex > 0 && !loading) { Text("上一页") }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (pageCount > 0) "第 ${pageIndex + 1} / $pageCount 页" else "正在读取页数…",
                    style = MaterialTheme.typography.labelLarge,
                )
                if (zoom > 1.001f) Text(
                    String.format(Locale.ROOT, "%.1f× · 双击还原", zoom),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { pageIndex++ }, enabled = pageIndex + 1 < pageCount && !loading) { Text("下一页") }
        }
    }
}

private fun pdfFailureMessage(failure: Exception): String = when (failure) {
    is PdfPreviewException -> failure.message ?: "无法打开此 PDF。"
    is IllegalArgumentException -> "此 PDF 的页面无法渲染，文件可能已损坏。"
    else -> "此 PDF 无法解析，文件可能已损坏；可使用其他应用打开。"
}
