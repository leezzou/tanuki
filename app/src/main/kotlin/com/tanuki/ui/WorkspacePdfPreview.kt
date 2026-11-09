package com.tanuki.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.tanuki.storage.WorkspaceFileManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/** 打开失败的 PDF 使用固定中文提示，避免把底层英文异常直接展示给用户。 */
internal class PdfPreviewException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** PdfRenderer 不是线程安全的：所有打开、渲染和关闭都串行到同一个 IO 线程。 */
private val PdfRenderDispatcher = Dispatchers.IO.limitedParallelism(1)

/** 关闭可能等待正在渲染的页面，使用进程级作用域避免界面销毁时中断清理。 */
private val PdfCloseScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

private const val PDF_RENDER_OVERSAMPLE = 1.5
private val PDF_PAGE_SPACING = 8.dp
private val PDF_PAGE_HORIZONTAL_PADDING = 8.dp
private val PDF_DEFAULT_ASPECT = 595f / 842f
private val PDF_PAGE_BORDER = Color(0x1F000000)
private val PDF_PLACEHOLDER_TEXT = Color(0xFF6B6B6B)
private val PDF_PAGE_ERROR_TEXT = Color(0xFFB3261E)

/** 一页已渲染位图及其内存记账信息。 */
internal class PdfPageBitmap(val bitmap: Bitmap, val bytes: Long, val aspect: Float)

/**
 * 一次文档会话：同一 PdfRenderer 跨页复用，避免每次翻页重新解析整个 PDF。
 * 渲染结果通过回调交给调用方，保证协程取消时仍能拿到位图并回收。
 */
internal class PdfDocumentSession private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    val pageCount: Int,
) : AutoCloseable {
    private val mutex = Mutex()
    private val closed = AtomicBoolean(false)

    class RenderedPage(val bitmap: Bitmap, val aspect: Float)

    suspend fun renderPage(
        index: Int,
        viewportWidth: Int,
        maxPagePixels: Int,
        maxDimension: Int = PdfRenderPolicy.MAX_DIMENSION,
        onBitmapCreated: (Bitmap) -> Unit = {},
    ): RenderedPage = mutex.withLock {
        if (closed.get()) throw PdfPreviewException("此 PDF 已关闭，请重新打开。")
        withContext(PdfRenderDispatcher) {
            val page = renderer.openPage(index)
            try {
                val size = PdfRenderPolicy.viewportSize(
                    pageWidth = page.width,
                    pageHeight = page.height,
                    viewportWidth = viewportWidth,
                    maxDimension = maxDimension,
                    maxPixels = maxPagePixels,
                    oversample = PDF_RENDER_OVERSAMPLE,
                )
                val bitmap = try {
                    Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
                } catch (failure: OutOfMemoryError) {
                    throw PdfPreviewException(
                        "内存不足，无法渲染第 ${index + 1} 页；请关闭其他应用后重试。",
                        failure,
                    )
                }
                onBitmapCreated(bitmap)
                try {
                    // PDF 页面本身透明；铺白底避免深色主题下黑字看不清。
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                } catch (failure: Throwable) {
                    bitmap.recycle()
                    throw failure
                }
                RenderedPage(bitmap, aspect = page.width.toFloat() / page.height.toFloat())
            } finally {
                page.close()
            }
        }
    }

    /** 请求关闭；与渲染共用互斥锁，保证不会在渲染过程中关闭 PdfRenderer。 */
    fun requestClose() {
        PdfCloseScope.launch {
            mutex.withLock {
                if (closed.compareAndSet(false, true)) {
                    withContext(PdfRenderDispatcher) {
                        runCatching { renderer.close() }
                        runCatching { descriptor.close() }
                    }
                }
            }
        }
    }

    override fun close() = requestClose()

    companion object {
        suspend fun open(
            descriptor: ParcelFileDescriptor,
            onOpened: (PdfDocumentSession) -> Unit = {},
        ): PdfDocumentSession = withContext(PdfRenderDispatcher) {
            val renderer = try {
                PdfRenderer(descriptor)
            } catch (failure: OutOfMemoryError) {
                runCatching { descriptor.close() }
                throw PdfPreviewException("内存不足，无法打开此 PDF；请关闭其他应用后重试。", failure)
            } catch (failure: Exception) {
                runCatching { descriptor.close() }
                throw PdfPreviewException(openFailureMessage(failure), failure)
            }
            if (renderer.pageCount <= 0) {
                runCatching { renderer.close() }
                runCatching { descriptor.close() }
                throw PdfPreviewException("此 PDF 没有可显示的页面。")
            }
            // 页数在渲染线程读取一次，避免主线程直接访问非线程安全的 PdfRenderer。
            val session = PdfDocumentSession(descriptor, renderer, renderer.pageCount)
            // 先回调再返回：即使协程在返回途中被取消，调用方仍能拿到会话并关闭。
            onOpened(session)
            session
        }

        private fun openFailureMessage(failure: Exception): String {
            val detail = failure.message.orEmpty().lowercase(Locale.ROOT)
            return when {
                failure is SecurityException || "encrypt" in detail || "password" in detail ->
                    "此 PDF 已加密（需要密码），暂不支持内置预览；可使用其他应用打开。"
                "no page" in detail || "page count" in detail ->
                    "此 PDF 没有可显示的页面。"
                "memory" in detail || "oom" in detail ->
                    "内存不足，无法打开此 PDF；请关闭其他应用后重试。"
                else -> "此 PDF 无法解析，文件可能已损坏或格式不受支持；可使用其他应用打开。"
            }
        }
    }
}

/**
 * 内置 PDF 连续纵向阅读：单文档会话按需渲染，当前页前后各预加载一页，
 * 位图按字节预算 LRU 淘汰；支持适应宽度/整页、捏合 1–5x、双击缩放和快速跳页。
 */
@Composable
internal fun PdfPreview(
    files: WorkspaceFileManager,
    path: String,
    modifier: Modifier = Modifier,
    maxDimension: Int = PdfRenderPolicy.MAX_DIMENSION,
    maxPixels: Int = PdfRenderPolicy.MAX_PIXELS,
) {
    var session by remember(path) { mutableStateOf<PdfDocumentSession?>(null) }
    var opening by remember(path) { mutableStateOf(true) }
    var openError by remember(path) { mutableStateOf<String?>(null) }
    var pageCount by remember(path) { mutableIntStateOf(0) }
    var currentPage by remember(path) { mutableIntStateOf(0) }
    var fitMode by remember(path) { mutableStateOf(PdfFitMode.FIT_WIDTH) }
    var renderToken by remember(path) { mutableIntStateOf(0) }
    var jumpVisible by remember(path) { mutableStateOf(false) }
    var jumpInput by remember(path) { mutableStateOf("") }
    var jumpError by remember(path) { mutableStateOf<String?>(null) }

    val pages = remember(path) { mutableStateMapOf<Int, PdfPageBitmap>() }
    val aspects = remember(path) { mutableStateMapOf<Int, Float>() }
    val pageErrors = remember(path) { mutableStateMapOf<Int, String>() }
    var visiblePages by remember(path) { mutableStateOf(emptyList<Int>()) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val zoom = remember(path) { mutableFloatStateOf(ZoomPolicy.MIN_SCALE) }
    val panX = remember(path) { mutableFloatStateOf(0f) }
    // 留白扩大后新的滚动范围要等下一次测量才生效，补偿先累积、随后统一应用。
    val pendingScroll = remember(path) { floatArrayOf(0f) }

    // 缓存预算按应用堆计算；单页像素上限保证预算内至少容纳 3 页。
    val cacheBudget = remember { PdfMemoryPolicy.cacheBudgetBytes(Runtime.getRuntime().maxMemory()) }
    val memory = remember(path) {
        PdfPageMemory<Int, PdfPageBitmap>(
            budgetBytes = cacheBudget,
            sizeOf = { it.bytes },
            release = { page -> page.bitmap.takeUnless { it.isRecycled }?.recycle() },
        )
    }

    LaunchedEffect(path) {
        opening = true
        openError = null
        var pending: PdfDocumentSession? = null
        try {
            val info = files.describe(path)
            if (PdfOpenPolicy.fileTooLarge(info.bytes)) {
                openError = "此 PDF 为 ${pdfBytesLabel(info.bytes)}，超过内置预览上限 " +
                    "${pdfBytesLabel(PdfOpenPolicy.MAX_FILE_BYTES)}；请使用其他应用打开。"
                return@LaunchedEffect
            }
            val descriptor = files.openPreviewDescriptor(path)
            val opened = PdfDocumentSession.open(descriptor) { created -> pending = created }
            pending = opened
            currentCoroutineContext().ensureActive()
            session = opened
            pageCount = opened.pageCount
            pending = null
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            openError = pdfFailureMessage(failure)
        } finally {
            // 组合已销毁或打开失败时，确保描述符与渲染器被释放。
            pending?.requestClose()
            opening = false
        }
    }

    DisposableEffect(path) {
        onDispose {
            session?.close()
            session = null
            memory.close()
            pages.clear()
            aspects.clear()
            pageErrors.clear()
        }
    }

    LaunchedEffect(fitMode) {
        zoom.floatValue = ZoomPolicy.MIN_SCALE
        panX.floatValue = 0f
    }

    // 切换文件时复用同一个 LazyListState，必须回到第一页，避免沿用上一份文档的滚动位置。
    LaunchedEffect(path, pageCount) {
        if (pageCount > 0) listState.scrollToItem(0)
    }

    Column(modifier.fillMaxWidth()) {
        if (opening) LinearProgressIndicator(Modifier.fillMaxWidth())
        val message = openError
        if (message != null) {
            Text(
                message,
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            return@Column
        }
        if (pageCount <= 0) {
            if (!opening) {
                Text(
                    "此 PDF 没有可显示的页面。",
                    Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            val viewport = IntSize(constraints.maxWidth, constraints.maxHeight)
            val renderPlan = PdfPreloadPolicy.renderPlan(visiblePages, currentPage, pageCount)
            // 驻留页数 = 渲染计划 + 一页在途位图；单页上限随驻留集合缩小，受保护页总字节不超预算。
            val maxPagePixels = remember(cacheBudget, renderPlan.size, maxPixels) {
                minOf(PdfMemoryPolicy.maxPagePixels(
                    cacheBudgetBytes = cacheBudget,
                    residentPages = (renderPlan.size + 1).coerceAtLeast(PdfMemoryPolicy.CACHE_SLOTS),
                ), maxPixels)
            }
            val readModifier = Modifier
                .fillMaxSize()
                .pdfReaderGestures(
                    viewport = viewport,
                    zoom = zoom,
                    panX = panX,
                    onScrollCompensation = { delta -> pendingScroll[0] += delta },
                )

            // 缩放先改变留白，旧滚动范围会截断立即执行的补偿；等下一帧完成测量后再滚动。
            LaunchedEffect(zoom.floatValue) {
                if (pendingScroll[0] == 0f) return@LaunchedEffect
                withFrameNanos { }
                val delta = pendingScroll[0]
                pendingScroll[0] = 0f
                if (delta != 0f) listState.dispatchRawDelta(delta)
            }

            // 只记录可见页索引的变化，避免滚动过程中每一帧都触发重新组合。
            LaunchedEffect(listState, pageCount) {
                snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.index } }
                    .distinctUntilChanged()
                    .collect { indices -> visiblePages = indices }
            }

            LaunchedEffect(listState, pageCount, viewport) {
                snapshotFlow {
                    val info = listState.layoutInfo
                    PdfViewportPolicy.pageAtCenter(
                        visible = info.visibleItemsInfo.map {
                            PdfViewportPolicy.VisiblePage(it.index, it.offset, it.size)
                        },
                        viewportStart = info.viewportStartOffset,
                        viewportEnd = info.viewportEndOffset,
                        pageCount = pageCount,
                    )
                }
                    .collect { index -> if (index != null) currentPage = index }
            }

            // 先加载同屏可见页，再预加载可见范围外的相邻页；集合变化会取消旧任务并回收位图。
            LaunchedEffect(session, renderPlan, maxPagePixels, maxDimension, viewport.width, viewport.height, renderToken) {
                val activeSession = session ?: return@LaunchedEffect
                val protectedPages = renderPlan.toSet()
                val maxPageBytes = maxPagePixels.toLong() * PdfMemoryPolicy.BYTES_PER_PIXEL
                // 驻留页变多后单页上限会收紧：先放弃超限旧页并等组合释放，再按新上限渲染。
                val oversized = renderPlan.filter { index ->
                    pages[index]?.let { it.bytes > maxPageBytes } == true
                }
                if (oversized.isNotEmpty()) {
                    oversized.forEach { index ->
                        pages.remove(index)
                        memory.discard(index)
                    }
                    // 等旧位图离开组合并被回收；最多等 3 帧，避免异常布局下无限等待。
                    var attempts = 0
                    while (attempts < 3 && oversized.any { memory.contains(it) }) {
                        withFrameNanos { }
                        attempts++
                    }
                }
                val reservationProtected = visiblePages.toSet()
                var needsMemoryRetry = false
                for (index in renderPlan) {
                    val cached = pages[index]
                    if (cached != null && cached.bytes <= maxPageBytes) {
                        memory.get(index)
                        continue
                    }
                    pageErrors.remove(index)
                    var pending: Bitmap? = null
                    var reservation = 0L
                    try {
                        // 先按实际驻留字节腾出空间，再创建位图；避免先分配后淘汰造成峰值超预算。
                        var evictedByReservation = memory.tryReserve(maxPageBytes, reservationProtected)
                        var attempts = 0
                        while (evictedByReservation == null && attempts < 6) {
                            withFrameNanos { }
                            evictedByReservation = memory.tryReserve(maxPageBytes, reservationProtected)
                            attempts++
                        }
                        if (evictedByReservation == null) {
                            needsMemoryRetry = true
                            continue
                        }
                        evictedByReservation.forEach { key -> pages.remove(key) }
                        reservation = maxPageBytes
                        val rendered = activeSession.renderPage(
                            index = index,
                            viewportWidth = viewport.width,
                            maxPagePixels = maxPagePixels,
                            onBitmapCreated = { pending = it },
                        )
                        currentCoroutineContext().ensureActive()
                        val page = PdfPageBitmap(
                            bitmap = rendered.bitmap,
                            bytes = PdfMemoryPolicy.pageBytes(rendered.bitmap.width, rendered.bitmap.height),
                            aspect = rendered.aspect,
                        )
                        pending = null
                        aspects[index] = rendered.aspect
                        pages[index] = page
                        val evicted = memory.store(
                            key = index,
                            value = page,
                            protectedKeys = protectedPages,
                            reservedBytes = reservation,
                        )
                        reservation = 0L
                        // 已淘汰但仍显示在屏上的页保留到离开组合，避免绘制已回收位图。
                        evicted.forEach { key -> if (!memory.isDisplayed(key)) pages.remove(key) }
                    } catch (failure: CancellationException) {
                        throw failure
                    } catch (failure: OutOfMemoryError) {
                        pageErrors[index] = "内存不足，无法渲染第 ${index + 1} 页；请关闭其他应用后重试。"
                    } catch (failure: PdfPreviewException) {
                        pageErrors[index] = failure.message ?: "第 ${index + 1} 页无法渲染。"
                    } catch (failure: Exception) {
                        pageErrors[index] = "第 ${index + 1} 页无法渲染：文件可能已损坏。"
                    } finally {
                        if (reservation != 0L) memory.cancelReservation(reservation)
                        pending?.takeUnless { it.isRecycled }?.recycle()
                    }
                }
                if (needsMemoryRetry) {
                    // 仍在显示的旧页会在随后几帧离开组合；稍后重试，避免可见页长期停在占位。
                    delay(500)
                    renderToken++
                }
            }

            // 固定视口裁剪：缩放层自身 clip 会随 scale 一起放大，必须由未变换的外层容器裁剪。
            Box(readModifier.clipToBounds()) {
                val density = LocalDensity.current
                val horizontalPaddingPx = with(density) { (PDF_PAGE_HORIZONTAL_PADDING * 2).toPx() }
                val verticalPaddingPx = with(density) { (PDF_PAGE_SPACING * 2).toPx() }
                val pageWidthPx = (viewport.width - horizontalPaddingPx).coerceAtLeast(1f)
                val pageViewportHeightPx = (viewport.height - verticalPaddingPx).coerceAtLeast(1f)
                // 放大后补首尾滚动空间，否则第一页顶部与最后一页底部会移出可达范围。
                val zoomPadding = with(density) {
                    PdfViewportPolicy.zoomEdgePadding(viewport.height.toFloat(), zoom.floatValue).toDp()
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = zoom.floatValue
                            scaleY = zoom.floatValue
                            translationX = panX.floatValue
                            transformOrigin = TransformOrigin.Center
                            clip = true
                        },
                    contentPadding = PaddingValues(
                        top = PDF_PAGE_SPACING + zoomPadding,
                        bottom = PDF_PAGE_SPACING + zoomPadding,
                    ),
                    verticalArrangement = Arrangement.spacedBy(PDF_PAGE_SPACING),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    items(count = pageCount, key = { it }) { index ->
                        val aspect = aspects[index]?.takeIf { it.isFinite() && it > 0f } ?: PDF_DEFAULT_ASPECT
                        val display = PdfViewportPolicy.pageDisplaySize(
                            pageWidth = aspect,
                            pageHeight = 1f,
                            viewportWidth = pageWidthPx,
                            viewportHeight = pageViewportHeightPx,
                            mode = fitMode,
                        )
                        val pageModifier = Modifier.size(
                            width = with(density) { display.width.toDp() },
                            height = with(density) { display.height.toDp() },
                        )
                        PdfPageItem(
                            index = index,
                            page = pages[index],
                            error = pageErrors[index],
                            modifier = pageModifier,
                            onDisplay = { memory.display(index) },
                            onHide = {
                                memory.hide(index)
                                if (!memory.contains(index)) pages.remove(index)
                            },
                            onRetry = { renderToken++ },
                        )
                    }
                }
            }
        }

        PdfControlBar(
            pageCount = pageCount,
            currentPage = currentPage,
            fitMode = fitMode,
            zoom = zoom.floatValue,
            onFitMode = { mode ->
                if (mode != fitMode) {
                    fitMode = mode
                    scope.launch { listState.scrollToItem(currentPage) }
                }
            },
            onJump = {
                jumpInput = (currentPage + 1).toString()
                jumpError = null
                jumpVisible = true
            },
            onResetZoom = {
                zoom.floatValue = ZoomPolicy.MIN_SCALE
                panX.floatValue = 0f
            },
        )
    }

    if (jumpVisible) {
        AlertDialog(
            onDismissRequest = { jumpVisible = false },
            title = { Text("跳到页面") },
            text = {
                OutlinedTextField(
                    value = jumpInput,
                    onValueChange = {
                        jumpInput = it
                        jumpError = null
                    },
                    singleLine = true,
                    label = { Text("页码（1–$pageCount）") },
                    isError = jumpError != null,
                    supportingText = { jumpError?.let { message -> Text(message) } },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = PdfJumpPolicy.parse(jumpInput, pageCount)
                    if (target == null) {
                        jumpError = PdfJumpPolicy.hint(pageCount)
                    } else {
                        jumpVisible = false
                        zoom.floatValue = ZoomPolicy.MIN_SCALE
                        panX.floatValue = 0f
                        scope.launch { listState.scrollToItem(target) }
                    }
                }) { Text("跳转") }
            },
            dismissButton = {
                TextButton(onClick = { jumpVisible = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun PdfPageItem(
    index: Int,
    page: PdfPageBitmap?,
    error: String?,
    modifier: Modifier,
    onDisplay: () -> Unit,
    onHide: () -> Unit,
    onRetry: () -> Unit,
) {
    DisposableEffect(index, page) {
        if (page != null) onDisplay()
        onDispose { if (page != null) onHide() }
    }
    val shape = RoundedCornerShape(2.dp)
    Box(
        modifier
            .clip(shape)
            .background(Color.White)
            .border(0.5.dp, PDF_PAGE_BORDER, shape),
        contentAlignment = Alignment.Center,
    ) {
        when {
            page != null -> {
                val image = remember(page) { page.bitmap.asImageBitmap() }
                Image(
                    bitmap = image,
                    contentDescription = "PDF 第 ${index + 1} 页",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
            error != null -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "第 ${index + 1} 页无法渲染",
                    color = PDF_PAGE_ERROR_TEXT,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = PDF_PLACEHOLDER_TEXT,
                    style = MaterialTheme.typography.labelSmall,
                )
                TextButton(onClick = onRetry) { Text("重试") }
            }
            else -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                Text(
                    "正在渲染第 ${index + 1} 页",
                    color = PDF_PLACEHOLDER_TEXT,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun PdfControlBar(
    pageCount: Int,
    currentPage: Int,
    fitMode: PdfFitMode,
    zoom: Float,
    onFitMode: (PdfFitMode) -> Unit,
    onJump: () -> Unit,
    onResetZoom: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FilterChip(
            selected = fitMode == PdfFitMode.FIT_WIDTH,
            onClick = { onFitMode(PdfFitMode.FIT_WIDTH) },
            label = { Text("适应宽度") },
        )
        FilterChip(
            selected = fitMode == PdfFitMode.FIT_PAGE,
            onClick = { onFitMode(PdfFitMode.FIT_PAGE) },
            label = { Text("适应页面") },
        )
        TextButton(onClick = onJump) { Text(PdfJumpPolicy.label(currentPage, pageCount)) }
        if (PdfViewportPolicy.isZoomed(zoom)) {
            TextButton(onClick = onResetZoom) { Text("还原 1×") }
        }
        Text(
            PdfJumpPolicy.zoomLabel(zoom),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
    }
}

/**
 * 阅读手势：双指捏合缩放并消费事件，避免同时触发列表滚动；单指横向拖动只在放大后平移；
 * 双击在 1x 与 2.5x 之间切换。所有手势都不拦截纵向翻页滚动。
 */
private fun Modifier.pdfReaderGestures(
    viewport: IntSize,
    zoom: MutableFloatState,
    panX: MutableFloatState,
    onScrollCompensation: (Float) -> Unit,
): Modifier = pointerInput(viewport) {
    var lastTapTime = 0L
    var lastTapPosition = Offset.Zero
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var activePointer = down.id
        var upPosition = down.position
        var moved = false
        var pinchActive = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) {
                upPosition = event.changes.firstOrNull { it.id == activePointer }?.position ?: upPosition
                break
            }
            if (pressed.size >= 2) {
                // 双指手势：即使这一帧没有缩放变化也消费，防止列表跟着手指滚动。
                pinchActive = true
                moved = true
                val factor = event.calculateZoom()
                val centroid = event.calculateCentroid(useCurrent = true)
                if (factor.isFinite() && factor > 0f && centroid.x.isFinite() && centroid.y.isFinite()) {
                    val oldScale = zoom.floatValue
                    val nextScale = ZoomPolicy.clampScale(oldScale * factor)
                    if (nextScale != oldScale) {
                        panX.floatValue = PdfViewportPolicy.zoomPanX(
                            panX = panX.floatValue,
                            centroidX = centroid.x,
                            viewportWidth = viewport.width.toFloat(),
                            oldScale = oldScale,
                            newScale = nextScale,
                        )
                        zoom.floatValue = nextScale
                        val viewportHeight = viewport.height.toFloat()
                        onScrollCompensation(
                            PdfViewportPolicy.zoomScrollCompensation(
                                centroidY = centroid.y,
                                viewportHeight = viewportHeight,
                                oldScale = oldScale,
                                newScale = nextScale,
                            ) + PdfViewportPolicy.zoomEdgePadding(viewportHeight, nextScale) -
                                PdfViewportPolicy.zoomEdgePadding(viewportHeight, oldScale),
                        )
                    }
                }
                event.changes.forEach { if (it.pressed) it.consume() }
            } else if (!pinchActive) {
                val change = pressed.first()
                val delta = change.positionChange()
                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                if (PdfViewportPolicy.isZoomed(zoom.floatValue)) {
                    panX.floatValue = PdfViewportPolicy.clampPanX(
                        panX = panX.floatValue + delta.x,
                        viewportWidth = viewport.width.toFloat(),
                        scale = zoom.floatValue,
                    )
                }
                // 不消费：纵向拖动仍交给 LazyColumn，实现连续翻页。
                activePointer = change.id
                upPosition = change.position
            }
        }
        if (!moved && !pinchActive) {
            val now = SystemClock.uptimeMillis()
            val isDoubleTap = now - lastTapTime in viewConfiguration.doubleTapMinTimeMillis..viewConfiguration.doubleTapTimeoutMillis &&
                (upPosition - lastTapPosition).getDistance() <= viewConfiguration.touchSlop * 2f
            if (isDoubleTap) {
                val oldScale = zoom.floatValue
                val nextScale = ZoomPolicy.nextDoubleTapScale(oldScale)
                panX.floatValue = if (nextScale <= ZoomPolicy.MIN_SCALE) {
                    0f
                } else {
                    PdfViewportPolicy.zoomPanX(
                        panX = 0f,
                        centroidX = upPosition.x,
                        viewportWidth = viewport.width.toFloat(),
                        oldScale = ZoomPolicy.MIN_SCALE,
                        newScale = nextScale,
                    )
                }
                zoom.floatValue = nextScale
                val viewportHeight = viewport.height.toFloat()
                onScrollCompensation(
                    PdfViewportPolicy.zoomScrollCompensation(
                        centroidY = upPosition.y,
                        viewportHeight = viewportHeight,
                        oldScale = oldScale,
                        newScale = nextScale,
                    ) + PdfViewportPolicy.zoomEdgePadding(viewportHeight, nextScale) -
                        PdfViewportPolicy.zoomEdgePadding(viewportHeight, oldScale),
                )
                lastTapTime = 0L
            } else {
                lastTapTime = now
                lastTapPosition = upPosition
            }
        }
    }
}

private fun pdfFailureMessage(failure: Exception): String = when (failure) {
    is PdfPreviewException -> failure.message ?: "无法打开此 PDF。"
    is SecurityException -> "此 PDF 已加密（需要密码），暂不支持内置预览；可使用其他应用打开。"
    // 文件已被移除或存储卷不可用时，保留文件管理器的中文原因。
    else -> failure.message?.takeIf { message -> message.any { it in '\u4e00'..'\u9fff' } }
        ?: "此 PDF 无法解析，文件可能已损坏；可使用其他应用打开。"
}

private fun pdfBytesLabel(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> String.format(Locale.ROOT, "%.1f GiB", bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> String.format(Locale.ROOT, "%.1f MiB", bytes / (1024.0 * 1024))
    bytes >= 1024L -> String.format(Locale.ROOT, "%.1f KiB", bytes / 1024.0)
    else -> "$bytes B"
}
