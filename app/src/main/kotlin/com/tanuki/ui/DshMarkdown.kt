package com.tanuki.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/** 原生消息渲染的 Markdown 块；只覆盖聊天正文常见的确定性语法。 */
sealed interface DshMarkdownBlock {
    data class Paragraph(val text: String) : DshMarkdownBlock
    data class Heading(val level: Int, val text: String) : DshMarkdownBlock
    data class Code(val language: String?, val code: String) : DshMarkdownBlock
    data class Quote(val text: String) : DshMarkdownBlock
    data class ListBlock(val ordered: Boolean, val items: List<String>) : DshMarkdownBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>) : DshMarkdownBlock
    data class Image(val alt: String, val url: String) : DshMarkdownBlock
    data object Rule : DshMarkdownBlock
}

/**
 * 一小组确定性的 Markdown 解析：
 * 标题、段落、围栏代码块、引用、列表、表格、独立图片与分隔线。
 * 不执行 HTML、不加载远程图片；流式未闭合代码块按“到末尾仍是代码”处理。
 */
object DshMarkdownParser {
    private val heading = Regex("^\\s{0,3}(#{1,6})\\s+(.*?)\\s*#*\\s*$")
    private val fence = Regex("^\\s*(`{3,}|~{3,})\\s*([^`]*)$")
    private val quote = Regex("^\\s*>\\s?(.*)$")
    private val bullet = Regex("^(\\s*)([-*+]|\\d+[.)])\\s+(.*)$")
    private val tableSeparator = Regex("^\\s*\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)+\\|?\\s*$")
    private val imageOnly = Regex("^\\s*!\\[([^\\]]*)]\\(([^)\\s]+)(?:\\s+\"[^\"]*\")?\\)\\s*$")
    private val rule = Regex("^\\s{0,3}((\\*\\s*){3,}|(-\\s*){3,}|(_\\s*){3,})$")

    fun parse(source: String): List<DshMarkdownBlock> {
        val lines = source.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val blocks = ArrayList<DshMarkdownBlock>()
        val paragraph = StringBuilder()
        fun flushParagraph() {
            val value = paragraph.toString().trim()
            if (value.isNotEmpty()) blocks += DshMarkdownBlock.Paragraph(value)
            paragraph.setLength(0)
        }
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            if (line.isBlank()) {
                flushParagraph()
                index++
                continue
            }
            val fenceMatch = fence.find(line)
            if (fenceMatch != null) {
                flushParagraph()
                val marker = fenceMatch.groupValues[1]
                val language = fenceMatch.groupValues[2].trim().take(40).takeIf { it.isNotEmpty() }
                val code = ArrayList<String>()
                index++
                while (index < lines.size && !lines[index].trimStart().startsWith(marker)) {
                    code += lines[index]
                    index++
                }
                if (index < lines.size) index++
                blocks += DshMarkdownBlock.Code(language, code.joinToString("\n"))
                continue
            }
            val headingMatch = heading.find(line)
            if (headingMatch != null) {
                flushParagraph()
                blocks += DshMarkdownBlock.Heading(
                    headingMatch.groupValues[1].length,
                    headingMatch.groupValues[2].trim()
                )
                index++
                continue
            }
            if (rule.matches(line)) {
                flushParagraph()
                blocks += DshMarkdownBlock.Rule
                index++
                continue
            }
            val imageMatch = imageOnly.find(line)
            if (imageMatch != null) {
                flushParagraph()
                blocks += DshMarkdownBlock.Image(imageMatch.groupValues[1], imageMatch.groupValues[2])
                index++
                continue
            }
            if (quote.matches(line)) {
                flushParagraph()
                val quoted = ArrayList<String>()
                while (index < lines.size && quote.matches(lines[index])) {
                    quoted += quote.find(lines[index])!!.groupValues[1]
                    index++
                }
                blocks += DshMarkdownBlock.Quote(quoted.joinToString("\n"))
                continue
            }
            if (line.contains('|') && index + 1 < lines.size && tableSeparator.matches(lines[index + 1])) {
                flushParagraph()
                val headers = cells(line)
                val rows = ArrayList<List<String>>()
                index += 2
                while (index < lines.size && lines[index].contains('|') && !lines[index].isBlank()) {
                    val row = cells(lines[index])
                    rows += List(headers.size) { position -> row.getOrElse(position) { "" } }
                    index++
                }
                blocks += DshMarkdownBlock.Table(headers, rows)
                continue
            }
            val listMatch = bullet.find(line)
            if (listMatch != null) {
                flushParagraph()
                val ordered = listMatch.groupValues[2].first().isDigit()
                val items = ArrayList<String>()
                while (index < lines.size) {
                    val item = bullet.find(lines[index]) ?: break
                    if (item.groupValues[2].first().isDigit() != ordered) break
                    items += item.groupValues[3]
                    index++
                }
                blocks += DshMarkdownBlock.ListBlock(ordered, items)
                continue
            }
            if (paragraph.isNotEmpty()) paragraph.append('\n')
            paragraph.append(line)
            index++
        }
        flushParagraph()
        return blocks
    }

    private fun cells(line: String): List<String> =
        line.trim().removePrefix("|").removeSuffix("|").split('|').map { it.trim() }
}

/**
 * 行内 Markdown 转 Compose AnnotatedString。
 * 链接保留可读的 `label (url)` 文本，不自动打开外部页面；图片不解析为网络加载。
 */
internal fun dshInlineMarkdown(text: String, codeBackground: Color): AnnotatedString =
    buildAnnotatedString { appendInline(this, text, codeBackground) }

private fun appendInline(builder: AnnotatedString.Builder, text: String, codeBackground: Color) {
    var index = 0
    while (index < text.length) {
        when {
            text.startsWith("`", index) -> {
                val end = text.indexOf('`', index + 1)
                if (end > index + 1) {
                    builder.withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)) {
                        append(text.substring(index + 1, end))
                    }
                    index = end + 1
                } else {
                    builder.append('`'); index++
                }
            }
            text.startsWith("**", index) || text.startsWith("__", index) -> {
                val marker = text.substring(index, index + 2)
                val end = text.indexOf(marker, index + 2)
                if (end > index + 2) {
                    val inner = text.substring(index + 2, end)
                    builder.withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(dshInlineMarkdown(inner, codeBackground))
                    }
                    index = end + 2
                } else {
                    builder.append(marker); index += 2
                }
            }
            text.startsWith("~~", index) -> {
                val end = text.indexOf("~~", index + 2)
                if (end > index + 2) {
                    val inner = text.substring(index + 2, end)
                    builder.withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        append(dshInlineMarkdown(inner, codeBackground))
                    }
                    index = end + 2
                } else {
                    builder.append("~~"); index += 2
                }
            }
            text[index] == '[' -> {
                val close = text.indexOf(']', index + 1)
                val openParen = if (close > 0 && close + 1 < text.length && text[close + 1] == '(') close + 1 else -1
                val end = if (openParen > 0) text.indexOf(')', openParen + 1) else -1
                if (close > index + 1 && end > openParen + 1) {
                    val label = text.substring(index + 1, close)
                    val url = text.substring(openParen + 1, end)
                    builder.withStyle(
                        SpanStyle(
                            color = Color(0xFF4C8DFF),
                            textDecoration = TextDecoration.Underline
                        )
                    ) {
                        append(label)
                        if (url != label) append(" ($url)")
                    }
                    index = end + 1
                } else {
                    builder.append(text[index]); index++
                }
            }
            text[index] == '*' || text[index] == '_' -> {
                val marker = text[index]
                val end = text.indexOf(marker, index + 1)
                if (end > index + 1) {
                    val inner = text.substring(index + 1, end)
                    builder.withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                        append(dshInlineMarkdown(inner, codeBackground))
                    }
                    index = end + 1
                } else {
                    builder.append(marker); index++
                }
            }
            else -> {
                builder.append(text[index])
                index++
            }
        }
    }
}

/**
 * 原生消息正文渲染。代码块可复制、表格横向滚动；远程图片只显示说明，不自动联网，
 * 避免绕过 DSH 的会话授权与认证。
 */
@Composable
fun DshMarkdown(text: String, modifier: Modifier = Modifier) {
    val blocks = androidx.compose.runtime.remember(text) { DshMarkdownParser.parse(text) }
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is DshMarkdownBlock.Paragraph -> SelectionContainer {
                    Text(dshInlineMarkdown(block.text, codeBackground), style = MaterialTheme.typography.bodyMedium)
                }
                is DshMarkdownBlock.Heading -> {
                    val style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    }
                    Text(dshInlineMarkdown(block.text, codeBackground), style = style, fontWeight = FontWeight.SemiBold)
                }
                is DshMarkdownBlock.Code -> DshCodeBlock(block.language, block.code)
                is DshMarkdownBlock.Quote -> Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SelectionContainer {
                        Text(
                            dshInlineMarkdown(block.text, codeBackground),
                            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                is DshMarkdownBlock.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    block.items.forEachIndexed { position, item ->
                        Row {
                            Text(
                                if (block.ordered) "${position + 1}." else "•",
                                Modifier.width(26.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            SelectionContainer {
                                Text(dshInlineMarkdown(item, codeBackground), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                is DshMarkdownBlock.Table -> DshTable(block)
                is DshMarkdownBlock.Image -> Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            "图片：${block.alt.ifBlank { "未命名" }}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "原生消息暂不加载远程图片；可在官方界面查看。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                DshMarkdownBlock.Rule -> HorizontalDivider()
            }
        }
    }
}

@Composable
private fun DshCodeBlock(language: String?, code: String) {
    val clipboard = LocalClipboardManager.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    language?.takeIf { it.isNotBlank() } ?: "代码",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { clipboard.setText(AnnotatedString(code)) }) { Text("复制") }
            }
            Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 4.dp)) {
                SelectionContainer {
                    Text(code, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun DshTable(table: DshMarkdownBlock.Table) {
    Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Row(Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            table.headers.forEach { header ->
                Text(
                    dshInlineMarkdown(header, MaterialTheme.colorScheme.surfaceContainerHighest),
                    Modifier.widthIn(min = 108.dp).padding(8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        table.rows.forEach { row ->
            Row {
                row.forEach { cell ->
                    Text(
                        dshInlineMarkdown(cell, MaterialTheme.colorScheme.surfaceContainerHighest),
                        Modifier.widthIn(min = 108.dp).padding(8.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            HorizontalDivider()
        }
    }
}
