package com.tanuki.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.ScrollState
import com.tanuki.storage.OpenDocument
import com.tanuki.storage.XlsxCell
import com.tanuki.storage.XlsxHorizontalAlign
import com.tanuki.storage.XlsxVerticalAlign
import com.tanuki.storage.XlsxRow
import com.tanuki.storage.XlsxSheet
import com.tanuki.storage.XlsxWorkbook
import kotlinx.coroutines.CancellationException

/**
 * XLSX 只读原生表格预览：多工作表、行列表头、冻结窗格、列宽/行高、合并单元格、
 * 基础字体/填充/边框和格式化显示值。图表、宏、透视表、外部链接和复杂条件格式不显示。
 */
@Composable
internal fun XlsxTablePreview(document: OpenDocument, modifier: Modifier = Modifier) {
    var workbook by remember(document.key) { mutableStateOf<XlsxWorkbook?>(null) }
    var error by remember(document.key) { mutableStateOf<String?>(null) }
    var loading by remember(document.key) { mutableStateOf(true) }
    var sheetIndex by remember(document.key) { mutableIntStateOf(0) }
    var scale by remember(document.key) { mutableFloatStateOf(XlsxPreviewPolicy.DEFAULT_SCALE) }
    LaunchedEffect(document.key) {
        loading = true
        error = null
        try {
            workbook = document.readXlsx()
            sheetIndex = 0
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            error = failure.message?.takeIf { message -> message.any { it in '\u4e00'..'\u9fff' } }
                ?: "无法读取此 XLSX，文件可能已加密或损坏。"
        } finally {
            loading = false
        }
    }
    Column(modifier.fillMaxSize()) {
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { message ->
            Text(message, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
            Text(
                "内置预览只读取工作簿内容，不改动原文件；可用其他应用打开完整表格。",
                Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val book = workbook
        if (book != null) {
            if (book.sheets.isEmpty()) {
                Text("此工作簿没有可显示的工作表。", Modifier.padding(16.dp))
            } else {
                val safeIndex = sheetIndex.coerceIn(0, book.sheets.lastIndex)
                val sheet = book.sheets[safeIndex]
                if (book.sheets.size > 1) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        book.sheets.forEachIndexed { index, item ->
                            FilterChip(
                                selected = index == safeIndex,
                                onClick = { sheetIndex = index },
                                label = { Text("${index + 1}. ${item.name}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            )
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextButton(onClick = { scale = XlsxPreviewPolicy.zoomOut(scale) }, enabled = scale > XlsxPreviewPolicy.MIN_SCALE) {
                        Text("缩小")
                    }
                    Text(XlsxPreviewPolicy.percentLabel(scale), style = MaterialTheme.typography.labelMedium)
                    TextButton(onClick = { scale = XlsxPreviewPolicy.zoomIn(scale) }, enabled = scale < XlsxPreviewPolicy.MAX_SCALE) {
                        Text("放大")
                    }
                    TextButton(onClick = { scale = XlsxPreviewPolicy.DEFAULT_SCALE }) { Text("重置") }
                    Text(
                        "${sheet.rows.size} 行 · ${sheet.columnCount} 列",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        "只读原生预览：支持多工作表、冻结窗格、合并单元格、基础字体/填充/边框和公式缓存值；" +
                            "公式不重新计算，图表、宏、透视表、外部链接、图片和复杂条件格式不显示，长文本以省略号显示。",
                        Modifier.padding(10.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (book.truncated || sheet.truncated) {
                    Text(
                        "此工作表超过内置预览上限，仅显示前 ${sheet.rows.size} 行 / ${sheet.columnCount} 列；原文件未被修改。",
                        Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (book.textTruncated) {
                    Text(
                        "部分单元格文本超过预览上限，已截断显示。",
                        Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                key(safeIndex) {
                    XlsxSheetTable(sheet, scale, Modifier.weight(1f).fillMaxWidth())
                }
                Text(
                    "第 ${safeIndex + 1} / ${book.sheets.size} 张工作表 · 只读" +
                        if (book.date1904) " · 1904 日期系统" else "",
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun XlsxSheetTable(sheet: XlsxSheet, scale: Float, modifier: Modifier) {
    val horizontal = rememberScrollState()
    val widthValues = remember(sheet.name, sheet.columnWidths, scale) {
        List(sheet.columnCount) { (XlsxPreviewPolicy.columnWidthDp(sheet.columnWidths.getOrNull(it)) * scale) }
    }
    val widths = remember(widthValues) { widthValues.map { it.dp } }
    val headerHeight = (28f * scale).coerceIn(22f, 48f).dp
    val gutterWidth = (44f * scale).coerceIn(32f, 80f).dp
    val rowHeightValues = remember(sheet.name, scale) {
        sheet.rows.map { XlsxPreviewPolicy.rowHeightDp(it.heightPoints) * scale }
    }
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val headerColor = MaterialTheme.colorScheme.surfaceVariant
    BoxWithConstraints(modifier) {
        // 按真实视口和缩放后的累计尺寸收缩冻结区，保证表体始终保留可滚动、可点击的区域。
        val availableWidth = maxWidth.value.takeIf { it.isFinite() } ?: Float.MAX_VALUE
        val availableHeight = maxHeight.value.takeIf { it.isFinite() } ?: Float.MAX_VALUE
        val frozenColumns = XlsxPreviewPolicy.visibleFrozenColumns(
            sheet.frozenColumns, widthValues, gutterWidth.value, availableWidth,
        )
        val frozenRows = XlsxPreviewPolicy.visibleFrozenRows(
            sheet.frozenRows, rowHeightValues, headerHeight.value, availableHeight,
        )
        val body = remember(sheet.name, frozenRows) { sheet.rows.drop(frozenRows) }
        Column(Modifier.fillMaxSize()) {
            // 列表头固定，与表体共用水平滚动状态。
            Row(Modifier.fillMaxWidth().height(headerHeight)) {
                XlsxHeaderBox(gutterWidth, headerHeight, "#", headerColor, gridColor)
                if (frozenColumns > 0) {
                    Row {
                        for (column in 0 until frozenColumns) {
                            XlsxHeaderBox(widths[column], headerHeight, XlsxPreviewPolicy.columnName(column), headerColor, gridColor)
                        }
                    }
                    Box(Modifier.width(1.dp).height(headerHeight).background(gridColor))
                }
                Row(Modifier.horizontalScroll(horizontal)) {
                    for (column in frozenColumns until sheet.columnCount) {
                        XlsxHeaderBox(widths[column], headerHeight, XlsxPreviewPolicy.columnName(column), headerColor, gridColor)
                    }
                }
            }
            HorizontalDivider(thickness = 1.dp, color = gridColor)
            // 冻结行不随表体纵向滚动，但仍随表体水平滚动。
            if (frozenRows > 0) {
                Column {
                    for (index in 0 until frozenRows) {
                        val row = sheet.rows[index]
                        XlsxGridRow(
                            row, widths, rowHeightValues[index].dp,
                            frozenColumns, gutterWidth, gridColor, headerColor, scale, horizontal,
                        )
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = gridColor)
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                items(body, key = { it.index }) { row ->
                    XlsxGridRow(
                        row, widths, rowHeightValues[row.index].dp,
                        frozenColumns, gutterWidth, gridColor, headerColor, scale, horizontal,
                    )
                }
            }
            if (sheet.rows.isEmpty()) {
                Text("此工作表没有单元格数据。", Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun XlsxHeaderBox(width: Dp, height: Dp, text: String, background: Color, gridColor: Color) {
    Box(
        Modifier.width(width).height(height).background(background).border(BorderStroke(0.5.dp, gridColor)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun XlsxGridRow(
    row: XlsxRow,
    widths: List<Dp>,
    height: Dp,
    frozenColumns: Int,
    gutterWidth: Dp,
    gridColor: Color,
    headerColor: Color,
    scale: Float,
    horizontal: ScrollState,
) {
    Row(Modifier.fillMaxWidth().height(height)) {
        Box(
            Modifier.width(gutterWidth).fillMaxHeight().background(headerColor).border(BorderStroke(0.5.dp, gridColor)),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(
                "${row.index + 1}", Modifier.padding(end = 4.dp),
                style = MaterialTheme.typography.labelSmall, maxLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (frozenColumns > 0) {
            Row {
                for (placed in layoutCells(row, widths, 0, frozenColumns)) {
                    XlsxCellBox(placed, height, scale, gridColor)
                }
            }
            Box(Modifier.width(1.dp).height(height).background(gridColor))
        }
        Row(Modifier.horizontalScroll(horizontal)) {
            for (placed in layoutCells(row, widths, frozenColumns, widths.size)) {
                XlsxCellBox(placed, height, scale, gridColor)
            }
        }
    }
}

@Composable
private fun XlsxCellBox(placed: PlacedCell, height: Dp, scale: Float, gridColor: Color) {
    val cell = placed.cell
    val style = cell?.style
    val fill = style?.fillColor?.let { Color(it) }
    val borderColor = style?.border?.color?.let { Color(it) }
        ?: if (style?.border?.visible == true) MaterialTheme.colorScheme.outline else gridColor
    val horizontal = when (style?.horizontal) {
        XlsxHorizontalAlign.LEFT -> 0
        XlsxHorizontalAlign.CENTER -> 1
        XlsxHorizontalAlign.RIGHT -> 2
        else -> if (cell?.numeric == true) 2 else 0
    }
    val alignment = when (style?.vertical) {
        XlsxVerticalAlign.TOP -> when (horizontal) {
            0 -> Alignment.TopStart
            1 -> Alignment.TopCenter
            else -> Alignment.TopEnd
        }
        XlsxVerticalAlign.CENTER -> when (horizontal) {
            0 -> Alignment.CenterStart
            1 -> Alignment.Center
            else -> Alignment.CenterEnd
        }
        else -> when (horizontal) {
            0 -> Alignment.BottomStart
            1 -> Alignment.BottomCenter
            else -> Alignment.BottomEnd
        }
    }
    val textAlign = when (horizontal) {
        0 -> TextAlign.Start
        1 -> TextAlign.Center
        else -> TextAlign.End
    }
    val fontSize = XlsxPreviewPolicy.cellFontSizeSp(style?.fontSize, scale).sp
    Box(
        Modifier.width(placed.width).height(height)
            .then(if (fill != null) Modifier.background(fill) else Modifier)
            .border(BorderStroke(if (style?.border?.visible == true) 1.dp else 0.5.dp, borderColor)),
        contentAlignment = alignment,
    ) {
        if (cell != null && !cell.covered && cell.text.isNotEmpty()) {
            Text(
                cell.text,
                Modifier.padding(horizontal = 3.dp),
                fontSize = fontSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = textAlign,
                color = style?.fontColor?.let { Color(it) } ?: MaterialTheme.colorScheme.onSurface,
                fontWeight = if (style?.bold == true) FontWeight.SemiBold else FontWeight.Normal,
                fontStyle = if (style?.italic == true) FontStyle.Italic else FontStyle.Normal,
                textDecoration = if (style?.underline == true) TextDecoration.Underline else null,
            )
        }
    }
}

/** 一行内按列排布：合并起点占多列宽，被覆盖单元格保留占位但不显示文字与内容。 */
private data class PlacedCell(val cell: XlsxCell?, val column: Int, val span: Int, val width: Dp)

private fun layoutCells(row: XlsxRow, widths: List<Dp>, from: Int, to: Int): List<PlacedCell> {
    val start = from.coerceAtLeast(0)
    val end = to.coerceAtMost(widths.size)
    val output = ArrayList<PlacedCell>()
    var index = 0
    var column = start
    while (column < end) {
        while (index < row.cells.size && row.cells[index].column < column) index++
        val cell = row.cells.getOrNull(index)?.takeIf { it.column == column }
        if (cell == null) {
            output += PlacedCell(null, column, 1, widths[column])
            column++
            continue
        }
        val span = if (cell.covered) 1 else cell.columnSpan.coerceIn(1, end - column)
        var width = 0.dp
        for (offset in 0 until span) {
            if (column + offset < widths.size) width += widths[column + offset]
        }
        output += PlacedCell(cell, column, span, width)
        column += span
    }
    return output
}
