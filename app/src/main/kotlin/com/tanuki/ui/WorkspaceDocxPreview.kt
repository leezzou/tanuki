package com.tanuki.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanuki.storage.WorkspaceFileManager
import com.tanuki.storage.docx.DocxAlignment
import com.tanuki.storage.docx.DocxBlock
import com.tanuki.storage.docx.DocxDocument
import com.tanuki.storage.docx.DocxInline
import com.tanuki.storage.docx.DocxLineSpacing
import com.tanuki.storage.docx.DocxRunStyle
import com.tanuki.storage.docx.DocxTableCell
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/** DOCX 应用内只读原生预览：解析在 IO 线程，渲染使用 Compose，不经外部应用或 LibreOffice。 */
@Composable
internal fun DocxPreview(files: WorkspaceFileManager, path: String, modifier: Modifier = Modifier) {
    var document by remember(path) { mutableStateOf<DocxDocument?>(null) }
    var error by remember(path) { mutableStateOf<String?>(null) }
    var loading by remember(path) { mutableStateOf(true) }
    LaunchedEffect(path) {
        loading = true
        error = null
        try {
            document = files.readDocx(path)
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            error = failure.message?.takeIf { message -> message.any { it in '\u4e00'..'\u9fff' } }
                ?: "无法读取此 DOCX，文件可能已损坏或加密。请用其他应用打开或重新导出。"
        } finally {
            loading = false
        }
    }
    Column(modifier.fillMaxSize()) {
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
        document?.let { loaded -> DocxDocumentView(loaded, Modifier.weight(1f).fillMaxWidth()) }
    }
}

@Composable
private fun DocxDocumentView(document: DocxDocument, modifier: Modifier = Modifier) {
    val baseDensity = LocalDensity.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val bitmaps = rememberDocxBitmaps(document)
    var zoom by remember(document) { mutableFloatStateOf(1f) }
    var copied by remember(document) { mutableStateOf(false) }
    var showInfo by remember(document) { mutableStateOf(false) }
    val vertical = rememberScrollState()
    val horizontal = rememberScrollState()
    fun changeZoom(target: Float) {
        zoom = target.coerceIn(MIN_ZOOM, MAX_ZOOM)
    }
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("只读预览", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(8.dp))
            Text(
                "${document.paragraphCount} 段 · ${document.tableCount} 表 · ${document.imageCount} 图",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { changeZoom(zoom - 0.25f) }, enabled = zoom > MIN_ZOOM) { Text("−") }
            Text("${(zoom * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
            TextButton(onClick = { changeZoom(zoom + 0.25f) }, enabled = zoom < MAX_ZOOM) { Text("+") }
            TextButton(onClick = { changeZoom(1f) }) { Text("适应宽度") }
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(document.plainText()))
                copied = true
                scope.launch { kotlinx.coroutines.delay(1500); copied = false }
            }) { Text(if (copied) "已复制" else "复制文本") }
            TextButton(onClick = { showInfo = true }) { Text("支持范围") }
        }
        HorizontalDivider()
        if (document.truncated) {
            Text(
                "文档超过内置预览上限，仅显示前面部分；完整内容请用其他应用打开。",
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val availableWidth = maxWidth
            val geometry = remember(document.page, availableWidth) {
                DocxPageGeometryPolicy.compute(document.page, availableWidth.value)
            }
            val measurer = rememberTextMeasurer()
            val atoms = remember(document, geometry, bitmaps, baseDensity.density) {
                DocxMeasurer(document, geometry, measurer, bitmaps, baseDensity.density).measure()
            }
            val renderByKey = remember(atoms) { atoms.associateBy { it.atom.key } }
            val pages = remember(atoms, geometry.contentHeightDp) {
                DocxPaginator.paginate(atoms.map { it.atom }, geometry.contentHeightDp)
            }
            val contentPadding = ((availableWidth - geometry.pageWidthDp.dp).value / 2f).coerceAtLeast(0f)
            Box(
                Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                if (event.changes.count { it.pressed } >= 2) {
                                    val factor = event.calculateZoom()
                                    if (factor.isFinite() && factor != 1f) {
                                        val next = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
                                        if (next != zoom) zoom = next
                                        event.changes.forEach { if (it.pressed) it.consume() }
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                    .pointerInput(document) {
                        detectTapGestures(onDoubleTap = {
                            zoom = if (zoom > 1.05f) 1f else 2f
                        })
                    }
                    .verticalScroll(vertical),
            ) {
                Box(Modifier.horizontalScroll(horizontal)) {
                    CompositionLocalProvider(
                        LocalDensity provides Density(baseDensity.density * zoom, baseDensity.fontScale),
                    ) {
                        Column(
                            Modifier.width(geometry.pageWidthDp.dp)
                                .padding(start = contentPadding.dp, top = 8.dp, bottom = 20.dp),
                        ) {
                            pages.forEachIndexed { index, page ->
                                DocxPageCard(
                                    geometry = geometry,
                                    page = page,
                                    pageIndex = index,
                                    pageCount = pages.size,
                                    atoms = renderByKey,
                                )
                                Spacer(Modifier.height(14.dp))
                            }
                        }
                    }
                }
            }
        }
    }
    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text("内置 DOCX 预览范围") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("支持：段落、标题、列表编号、粗体/斜体/下划线/字号/颜色、常用表格、内嵌图片、纸张尺寸与页边距、近似分页。", style = MaterialTheme.typography.bodyMedium)
                    Text("不支持：复杂域、宏与脚本、嵌入对象、文本框/艺术字、页眉页脚、脚注尾注、批注与修订痕迹、多栏排版、精确分页与跨页表头重复。", style = MaterialTheme.typography.bodyMedium)
                    Text("为便于手机阅读，正文字号相对纸张比例放大，因此页数多于 Word 的精确排版；原文件不会被修改。", style = MaterialTheme.typography.bodyMedium)
                    if (document.warnings.isNotEmpty()) {
                        HorizontalDivider()
                        Text("本文档提示", style = MaterialTheme.typography.labelLarge)
                        document.warnings.forEach { warning -> Text("· $warning", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text("知道了") } },
        )
    }
}

@Composable
private fun DocxPageCard(
    geometry: DocxPageGeometry,
    page: DocxPageSlice,
    pageIndex: Int,
    pageCount: Int,
    atoms: Map<Int, DocxRenderAtom>,
) {
    val paddingEnd = (geometry.pageWidthDp - geometry.marginLeftDp - geometry.contentWidthDp).coerceAtLeast(0f)
    val paddingBottom = (geometry.pageHeightDp - geometry.marginTopDp - geometry.contentHeightDp).coerceAtLeast(0f)
    Surface(
        modifier = Modifier.width(geometry.pageWidthDp.dp).height(geometry.pageHeightDp.dp),
        color = Color(0xFFFDFDFD),
        shadowElevation = 2.dp,
    ) {
        Box(Modifier.fillMaxSize().clipToBounds()) {
            Column(
                Modifier.fillMaxSize().padding(
                    start = geometry.marginLeftDp.dp,
                    top = geometry.marginTopDp.dp,
                    end = paddingEnd.dp,
                    bottom = paddingBottom.dp,
                ),
            ) {
                page.atoms.forEach { atom ->
                    if (atom.gapBefore > 0f) Spacer(Modifier.height(atom.gapBefore.dp))
                    atoms[atom.key]?.let { render -> DocxRenderAtomView(render, geometry) }
                }
            }
            Text(
                "${pageIndex + 1} / $pageCount",
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp),
                color = Color(0xFF808080),
                fontSize = 7.sp,
            )
            if (page.overflow) {
                Text(
                    "本页内容超出纸张，已截断",
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
                    color = Color(0xFFB3261E),
                    fontSize = 7.sp,
                )
            }
        }
    }
}

@Composable
private fun DocxRenderAtomView(atom: DocxRenderAtom, geometry: DocxPageGeometry) {
    when (atom) {
        is DocxRenderAtom.TextLine -> Box(
            Modifier.fillMaxWidth().height(atom.atom.height.dp).clipToBounds(),
        ) {
            Text(
                atom.text,
                modifier = Modifier
                    .offset(x = atom.leftDp.dp, y = (-atom.lineTopDp).dp)
                    .width(atom.widthDp.dp)
                    // 分页切片只显示整段布局的一部分，必须允许超出切片高度排版后再裁剪。
                    .wrapContentHeight(unbounded = true),
                style = atom.style,
                softWrap = true,
            )
        }
        is DocxRenderAtom.ImageAtom -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            val bitmap = atom.bitmap
            if (bitmap != null && !bitmap.isRecycled) {
                Image(
                    bitmap.asImageBitmap(),
                    atom.altText ?: "文档图片",
                    Modifier.width(atom.widthDp.dp).height(atom.heightDp.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Box(
                    Modifier.width(atom.widthDp.dp).height(atom.heightDp.dp)
                        .border(0.5.dp, Color(0xFFBDBDBD))
                        .background(Color(0xFFF5F5F5)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("图片无法显示", color = Color(0xFF707070), fontSize = 8.sp)
                }
            }
        }
        is DocxRenderAtom.TableRow -> Row(Modifier.fillMaxWidth().height(atom.heightDp.dp).clipToBounds()) {
            atom.cells.forEach { cell ->
                val cellWidth = atom.columnWidths.drop(cell.columnStart).take(cell.colSpan).sum()
                Column(
                    Modifier.width(cellWidth.dp).height(atom.heightDp.dp).clipToBounds()
                        .background(cell.shade?.let { Color(it) } ?: if (atom.header) Color(0xFFF2F2F2) else Color.Transparent)
                        .border(0.5.dp, Color(0xFFBDBDBD))
                        .padding(3.dp),
                ) {
                    cell.blocks.forEach { block ->
                        when (block) {
                            is DocxCellBlock.Text -> {
                                if (block.gapBefore > 0f) Spacer(Modifier.height(block.gapBefore.dp))
                                Text(block.text, Modifier.fillMaxWidth(), style = block.style)
                            }
                            is DocxCellBlock.Image -> {
                                if (block.gapBefore > 0f) Spacer(Modifier.height(block.gapBefore.dp))
                                val bitmap = block.bitmap
                                if (bitmap != null && !bitmap.isRecycled) {
                                    Image(
                                        bitmap.asImageBitmap(),
                                        block.altText ?: "表格图片",
                                        Modifier.width(block.widthDp.dp).height(block.heightDp.dp),
                                        contentScale = ContentScale.Fit,
                                    )
                                }
                            }
                            is DocxCellBlock.Placeholder -> {
                                if (block.gapBefore > 0f) Spacer(Modifier.height(block.gapBefore.dp))
                                Text(block.text, color = Color(0xFF707070), fontSize = 8.sp)
                            }
                        }
                    }
                }
            }
        }
        is DocxRenderAtom.Placeholder -> Text(
            atom.text,
            Modifier.padding(vertical = 2.dp),
            color = Color(0xFF707070),
            fontSize = 8.sp,
        )
    }
}

private sealed interface DocxRenderAtom {
    val atom: DocxPageAtom

    data class TextLine(
        override val atom: DocxPageAtom,
        val text: AnnotatedString,
        val style: TextStyle,
        val lineTopDp: Float,
        val leftDp: Float,
        val widthDp: Float,
    ) : DocxRenderAtom

    data class ImageAtom(
        override val atom: DocxPageAtom,
        val bitmap: Bitmap?,
        val altText: String?,
        val widthDp: Float,
        val heightDp: Float,
    ) : DocxRenderAtom

    data class TableRow(
        override val atom: DocxPageAtom,
        val header: Boolean,
        val cells: List<DocxCellRender>,
        val columnWidths: List<Float>,
        val heightDp: Float,
    ) : DocxRenderAtom

    data class Placeholder(override val atom: DocxPageAtom, val text: String) : DocxRenderAtom
}

private data class DocxCellRender(
    val columnStart: Int,
    val colSpan: Int,
    val shade: Int?,
    val blocks: List<DocxCellBlock>,
)

private sealed interface DocxCellBlock {
    data class Text(
        val text: AnnotatedString,
        val style: TextStyle,
        val gapBefore: Float,
        val heightDp: Float,
    ) : DocxCellBlock

    data class Image(
        val bitmap: Bitmap?,
        val altText: String?,
        val widthDp: Float,
        val heightDp: Float,
        val gapBefore: Float,
    ) : DocxCellBlock

    data class Placeholder(val text: String, val gapBefore: Float) : DocxCellBlock
}

private class DocxMeasurer(
    private val document: DocxDocument,
    private val geometry: DocxPageGeometry,
    private val measurer: TextMeasurer,
    private val bitmaps: Map<String, Bitmap?>,
    private val density: Float,
) {
    private val output = mutableListOf<DocxRenderAtom>()
    private var nextKey = 0
    private var renderedBlocks = 0

    fun measure(): List<DocxRenderAtom> {
        var pending = 0f
        for (block in document.blocks) {
            if (renderedBlocks++ >= MAX_RENDERED_BLOCKS || output.size >= MAX_RENDERED_ATOMS) break
            pending = when (block) {
                is DocxBlock.PageBreak -> {
                    output += DocxRenderAtom.Placeholder(
                        DocxPageAtom(nextKey++, 0f, pageBreakBefore = true),
                        "",
                    )
                    0f
                }
                is DocxBlock.Paragraph -> measureParagraph(block, pending)
                is DocxBlock.Table -> measureTable(block, pending)
            }
        }
        return output
    }

    private fun measureParagraph(paragraph: DocxBlock.Paragraph, pendingGap: Float): Float {
        val props = paragraph.style
        val spaceBefore = geometry.textLengthDp(props.spaceBeforeTwips).coerceIn(0f, geometry.contentHeightDp / 2f)
        val spaceAfter = geometry.textLengthDp(props.spaceAfterTwips).coerceIn(0f, geometry.contentHeightDp / 2f)
        val hasText = paragraph.inlines.any { it is DocxInline.Text && it.text.isNotEmpty() }
        val images = paragraph.inlines.filterIsInstance<DocxInline.Image>()
        var first = true
        var pendingAfter = spaceAfter
        if (hasText || images.isEmpty()) {
            val metrics = paragraphMetrics(paragraph, geometry.contentWidthDp)
            val lineCount = metrics.layout.lineCount.coerceAtLeast(1)
            for (line in 0 until lineCount) {
                val top = metrics.layout.getLineTop(line) / density
                val bottom = metrics.layout.getLineBottom(line) / density
                val gap = if (first) pendingGap + spaceBefore else 0f
                output += DocxRenderAtom.TextLine(
                    atom = DocxPageAtom(nextKey++, (bottom - top).coerceAtLeast(1f), gap),
                    text = metrics.annotated,
                    style = metrics.style,
                    lineTopDp = top,
                    leftDp = metrics.leftDp,
                    widthDp = metrics.widthDp,
                )
                first = false
            }
        }
        for (image in images) {
            val bitmap = image.image.entryName?.let { bitmaps[it] }
            val size = if (bitmap != null) {
                DocxImagePolicy.displaySize(
                    bitmap.width, bitmap.height, image.image.widthEmu, image.image.heightEmu,
                    geometry.contentWidthDp, geometry.contentHeightDp, document.page.contentWidthTwips, density,
                )
            } else {
                DocxImageSize(geometry.contentWidthDp.coerceAtMost(160f), 48f)
            }
            val gap = if (first) pendingGap + spaceBefore else 4f
            output += DocxRenderAtom.ImageAtom(
                atom = DocxPageAtom(nextKey++, size.heightDp, gap),
                bitmap = bitmap,
                altText = image.image.altText,
                widthDp = size.widthDp,
                heightDp = size.heightDp,
            )
            first = false
            pendingAfter = spaceAfter
        }
        return pendingAfter
    }

    private fun measureTable(table: DocxBlock.Table, pendingGap: Float): Float {
        val columnCount = table.rows.maxOfOrNull { row -> row.cells.sumOf { it.colSpan.coerceAtLeast(1) } }?.coerceAtLeast(1) ?: return pendingGap
        val weights = if (table.columnWidthsTwips.size == columnCount) {
            table.columnWidthsTwips.map { it.coerceAtLeast(1) }
        } else {
            List(columnCount) { 1 }
        }
        val totalWeight = weights.sum().coerceAtLeast(1)
        val widths = weights.map { geometry.contentWidthDp * it / totalWeight }
        var pending = pendingGap
        table.rows.forEach { row ->
            var columnStart = 0
            val cells = mutableListOf<DocxCellRender>()
            for (cell in row.cells) {
                val span = cell.colSpan.coerceIn(1, (columnCount - columnStart).coerceAtLeast(1))
                val cellWidth = widths.drop(columnStart).take(span).sum().coerceAtLeast(24f) - CELL_HORIZONTAL_PADDING * 2f
                val measured = measureCell(cell, cellWidth.coerceAtLeast(16f))
                cells += DocxCellRender(columnStart, span, cell.shadeArgb, measured)
                columnStart += span
                if (columnStart >= columnCount) break
            }
            val contentHeight = cells.maxOfOrNull { cell ->
                cell.blocks.fold(0f) { total, block ->
                    total + when (block) {
                        is DocxCellBlock.Text -> block.heightDp + block.gapBefore
                        is DocxCellBlock.Image -> block.heightDp + block.gapBefore
                        is DocxCellBlock.Placeholder -> 16f + block.gapBefore
                    }
                }
            } ?: 20f
            val rowHeight = (contentHeight + CELL_HORIZONTAL_PADDING * 2f).coerceAtLeast(20f)
            output += DocxRenderAtom.TableRow(
                atom = DocxPageAtom(nextKey++, rowHeight, pending),
                header = row.header,
                cells = cells,
                columnWidths = widths,
                heightDp = rowHeight,
            )
            pending = 0f
        }
        return 0f
    }

    private fun measureCell(cell: DocxTableCell, widthDp: Float): List<DocxCellBlock> {
        val output = mutableListOf<DocxCellBlock>()
        var first = true
        for (block in cell.blocks) {
            when (block) {
                is DocxBlock.Paragraph -> {
                    val spaceBefore = if (first) 0f else geometry.textLengthDp(block.style.spaceBeforeTwips).coerceIn(0f, 12f)
                    val metrics = paragraphMetrics(block, widthDp)
                    if (metrics.layout.lineCount > 0) {
                        output += DocxCellBlock.Text(metrics.annotated, metrics.style, spaceBefore, metrics.layout.size.height / density)
                    }
                    for (image in block.inlines.filterIsInstance<DocxInline.Image>()) {
                        val bitmap = image.image.entryName?.let { bitmaps[it] }
                        val imageSize = if (bitmap != null) {
                            DocxImagePolicy.displaySize(
                                bitmap.width, bitmap.height, image.image.widthEmu, image.image.heightEmu,
                                widthDp, geometry.contentHeightDp / 2f, document.page.contentWidthTwips, density,
                            )
                        } else {
                            DocxImageSize(widthDp.coerceAtMost(120f), 40f)
                        }
                        output += DocxCellBlock.Image(
                            bitmap, image.image.altText, imageSize.widthDp, imageSize.heightDp,
                            if (first) 0f else 2f,
                        )
                        first = false
                    }
                    first = false
                }
                is DocxBlock.Table -> {
                    output += DocxCellBlock.Placeholder("嵌套表格未内置显示", if (first) 0f else 2f)
                    first = false
                }
                DocxBlock.PageBreak -> Unit
            }
        }
        return output
    }

    private fun paragraphMetrics(paragraph: DocxBlock.Paragraph, maxWidthDp: Float): ParagraphMetrics {
        val props = paragraph.style
        val numberingIndent = paragraph.numbering?.indentLeftTwips ?: 0
        val leftTwips = max(props.indentLeftTwips, numberingIndent)
        val leftDp = geometry.textLengthDp(leftTwips).coerceIn(0f, maxWidthDp * 0.4f)
        val widthDp = (maxWidthDp - leftDp).coerceAtLeast(24f)
        val annotated = paragraphText(paragraph)
        val style = paragraphStyle(paragraph, widthDp)
        val layout = measurer.measure(
            annotated,
            style,
            constraints = Constraints(maxWidth = (widthDp * density).roundToInt().coerceAtLeast(1)),
        )
        return ParagraphMetrics(annotated, style, layout, leftDp, widthDp)
    }

    private fun paragraphText(paragraph: DocxBlock.Paragraph): AnnotatedString = buildAnnotatedString {
        val marker = paragraph.numbering
        if (marker != null) {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                append(marker.text)
                append('\u00A0')
            }
        }
        for (inline in paragraph.inlines) {
            if (inline !is DocxInline.Text) continue
            withStyle(spanStyle(inline.style)) { append(inline.text) }
        }
        if (length == 0) append(' ')
    }

    private fun spanStyle(style: DocxRunStyle): SpanStyle = SpanStyle(
        color = Color(style.colorArgb),
        fontSize = geometry.fontDp(style.fontSizeHalfPoints).sp,
        fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (style.italic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = decoration(style),
        background = style.highlightArgb?.let { Color(it) } ?: Color.Unspecified,
    )

    private fun paragraphStyle(paragraph: DocxBlock.Paragraph, widthDp: Float): TextStyle {
        val props = paragraph.style
        val default = props.defaultRun
        val maxFontDp = (paragraph.inlines.filterIsInstance<DocxInline.Text>().maxOfOrNull {
            geometry.fontDp(it.style.fontSizeHalfPoints)
        } ?: geometry.fontDp(default.fontSizeHalfPoints)).coerceAtLeast(1f)
        val lineHeight = when (val spacing = props.lineSpacing) {
            is DocxLineSpacing.Auto -> maxFontDp * (spacing.percent.coerceIn(120, 600) / 240f)
            is DocxLineSpacing.Exact -> geometry.textLengthDp(spacing.twips)
            is DocxLineSpacing.AtLeast -> max(maxFontDp, geometry.textLengthDp(spacing.twips))
        }.coerceAtLeast(maxFontDp * 1.05f)
        val firstLine = geometry.textLengthDp(props.indentFirstLineTwips - props.hangingTwips)
            .coerceIn(-widthDp * 0.4f, widthDp * 0.4f)
        return TextStyle(
            color = Color(default.colorArgb),
            fontSize = geometry.fontDp(default.fontSizeHalfPoints).sp,
            fontWeight = if (default.bold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (default.italic) FontStyle.Italic else FontStyle.Normal,
            textDecoration = decoration(default),
            textAlign = alignment(props.alignment),
            lineHeight = lineHeight.sp,
            textIndent = TextIndent(firstLine = firstLine.sp, restLine = 0.sp),
        )
    }

    private fun alignment(value: DocxAlignment): TextAlign = when (value) {
        DocxAlignment.CENTER -> TextAlign.Center
        DocxAlignment.END -> TextAlign.End
        DocxAlignment.JUSTIFY -> TextAlign.Justify
        DocxAlignment.START -> TextAlign.Start
    }

    private fun decoration(style: DocxRunStyle): TextDecoration {
        val list = buildList {
            if (style.underline) add(TextDecoration.Underline)
            if (style.strike) add(TextDecoration.LineThrough)
        }
        return when (list.size) {
            0 -> TextDecoration.None
            1 -> list.first()
            else -> TextDecoration.combine(list)
        }
    }

    private companion object {
        const val CELL_HORIZONTAL_PADDING = 3f
        const val MAX_RENDERED_BLOCKS = 1200
        const val MAX_RENDERED_ATOMS = 12_000
    }
}

private data class ParagraphMetrics(
    val annotated: AnnotatedString,
    val style: TextStyle,
    val layout: TextLayoutResult,
    val leftDp: Float,
    val widthDp: Float,
)

@Composable
private fun rememberDocxBitmaps(document: DocxDocument): Map<String, Bitmap?> {
    val media = document.media
    var decoded by remember(media) { mutableStateOf<Map<String, Bitmap?>>(emptyMap()) }
    LaunchedEffect(media) {
        decoded = withContext(Dispatchers.IO) { decodeDocxBitmaps(media) }
    }
    DisposableEffect(decoded) {
        onDispose {
            decoded.values.forEach { bitmap -> if (bitmap != null && !bitmap.isRecycled) bitmap.recycle() }
        }
    }
    return decoded
}

private fun decodeDocxBitmaps(media: Map<String, ByteArray>): Map<String, Bitmap?> {
    val output = linkedMapOf<String, Bitmap?>()
    var usedPixels = 0L
    for ((name, bytes) in media) {
        if (output.size >= DocxImagePolicy.MAX_IMAGES_PER_PAGE) break
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            output[name] = null
            continue
        }
        val sample = DocxImagePolicy.sampleSize(bounds.outWidth, bounds.outHeight)
        val sampledWidth = bounds.outWidth / sample
        val sampledHeight = bounds.outHeight / sample
        if (!DocxImagePolicy.withinBudget(usedPixels, sampledWidth, sampledHeight)) {
            output[name] = null
            continue
        }
        val bitmap = runCatching {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()
        if (bitmap == null) {
            output[name] = null
            continue
        }
        usedPixels += bitmap.width.toLong() * bitmap.height
        output[name] = bitmap
    }
    return output
}

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 4f
