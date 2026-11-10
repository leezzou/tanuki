package com.tanuki.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.compose.foundation.Image
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 原生消息渲染的 Markdown 块；只覆盖聊天正文常见的确定性语法。 */
sealed interface DshMarkdownBlock {
    data class Paragraph(val text: String) : DshMarkdownBlock
    data class Heading(val level: Int, val text: String) : DshMarkdownBlock
    data class Code(val language: String?, val code: String) : DshMarkdownBlock
    data class Quote(val text: String) : DshMarkdownBlock
    data class ListBlock(val ordered: Boolean, val items: List<DshListItem>) : DshMarkdownBlock
    data class Table(
        val headers: List<String>,
        val rows: List<List<String>>,
        val alignments: List<DshTableAlign> = emptyList()
    ) : DshMarkdownBlock
    data class Image(val alt: String, val url: String) : DshMarkdownBlock
    data class Attachment(val name: String, val url: String) : DshMarkdownBlock
    data class Truncated(val message: String) : DshMarkdownBlock
    data object Rule : DshMarkdownBlock
}

enum class DshTableAlign { LEFT, CENTER, RIGHT }

/** 列表项；depth 用于嵌套缩进，checked 用于任务列表。 */
data class DshListItem(
    val text: String,
    val depth: Int = 0,
    val ordered: Boolean = false,
    val number: Int = 1,
    val checked: Boolean? = null
)

/**
 * 一小组确定性的 Markdown 解析：
 * 标题、段落、围栏代码块、引用、嵌套列表、任务列表、带对齐表格、独立图片、
 * 附件占位与分隔线。不执行 HTML、不加载远程图片；流式未闭合代码块按“到末尾仍是代码”处理。
 */
object DshMarkdownParser {
    private val heading = Regex("^\\s{0,3}(#{1,6})\\s+(.*?)\\s*#*\\s*$")
    private val fenceOpen = Regex("^ {0,3}(`{3,}|~{3,})\\s*(.*)$")
    private val quote = Regex("^\\s{0,3}>\\s?(.*)$")
    private val listItem = Regex("^(\\s*)([-*+]|(\\d+)[.)])\\s+(.*)$")
    private val taskMarker = Regex("^\\[([ xX])]\\s+(.*)$")
    private val imageOnly = Regex("^\\s*!\\[([^\\]]*)]\\((\\S+?)(?:\\s+[\"'][^\"']*[\"'])?\\)\\s*$")
    private val attachmentOnly = Regex(
        "^\\s*\\[([^\\]]+)]\\(((?:attachment|sandbox|workspace|file|content):[^)\\s]+)\\)\\s*$"
    )
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
            val fenceMatch = fenceOpen.find(line)
            if (fenceMatch != null) {
                flushParagraph()
                val marker = fenceMatch.groupValues[1]
                val fenceChar = marker.first()
                val minimum = marker.length
                val language = fenceMatch.groupValues[2].trim().take(40).takeIf { it.isNotEmpty() }
                val code = ArrayList<String>()
                index++
                while (index < lines.size && !isFenceClose(lines[index], fenceChar, minimum)) {
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
            val attachmentMatch = attachmentOnly.find(line)
            if (attachmentMatch != null) {
                flushParagraph()
                blocks += DshMarkdownBlock.Attachment(
                    attachmentMatch.groupValues[1],
                    attachmentMatch.groupValues[2]
                )
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
            if (looksLikeTable(line, lines.getOrNull(index + 1))) {
                flushParagraph()
                val headers = cells(line)
                val alignments = parseAlignments(lines[index + 1], headers.size)
                val rows = ArrayList<List<String>>()
                index += 2
                while (index < lines.size) {
                    val rowLine = lines[index]
                    if (rowLine.isBlank() || !rowLine.contains('|') || isTableSeparator(rowLine)) break
                    val row = cells(rowLine)
                    rows += List(headers.size) { position -> row.getOrElse(position) { "" } }
                    index++
                }
                blocks += DshMarkdownBlock.Table(headers, rows, alignments)
                continue
            }
            val listMatch = listItem.find(line)
            if (listMatch != null) {
                flushParagraph()
                val baseIndent = listMatch.groupValues[1].length
                val items = ArrayList<DshListItem>()
                while (index < lines.size) {
                    val current = lines[index]
                    if (current.isBlank()) break
                    val match = listItem.find(current)
                    if (match != null) {
                        val indent = match.groupValues[1].length
                        val depth = ((indent - baseIndent).coerceAtLeast(0) / 2).coerceAtMost(4)
                        val marker = match.groupValues[2]
                        val ordered = marker.first().isDigit()
                        val number = if (ordered) marker.dropLast(1).toIntOrNull() ?: 1 else 1
                        var itemText = match.groupValues[4]
                        var checked: Boolean? = null
                        val task = taskMarker.find(itemText)
                        if (task != null) {
                            checked = task.groupValues[1].equals("x", ignoreCase = true)
                            itemText = task.groupValues[2]
                        }
                        items += DshListItem(itemText, depth, ordered, number, checked)
                        index++
                    } else if (current.firstOrNull()?.isWhitespace() == true && items.isNotEmpty()) {
                        // 缩进续行并入上一项；没有列表项时按普通段落处理，避免误吞正文。
                        val last = items.removeAt(items.lastIndex)
                        items += last.copy(text = last.text + "\n" + current.trim())
                        index++
                    } else {
                        break
                    }
                }
                blocks += DshMarkdownBlock.ListBlock(items.firstOrNull()?.ordered == true, items)
                continue
            }
            if (paragraph.isNotEmpty()) paragraph.append('\n')
            paragraph.append(line)
            index++
        }
        flushParagraph()
        if (blocks.size <= DshChatRenderPolicy.MAX_MARKDOWN_BLOCKS) return blocks
        val capped = ArrayList(blocks.take(DshChatRenderPolicy.MAX_MARKDOWN_BLOCKS))
        capped += DshMarkdownBlock.Truncated(
            "内容过长，仅渲染前 ${DshChatRenderPolicy.MAX_MARKDOWN_BLOCKS} 段；可复制整条消息查看全文。"
        )
        return capped
    }

    private fun isFenceClose(line: String, fenceChar: Char, minimum: Int): Boolean {
        val trimmed = line.trim()
        return trimmed.length >= minimum && trimmed.all { it == fenceChar }
    }

    private fun looksLikeTable(line: String, next: String?): Boolean =
        line.contains('|') && next != null && isTableSeparator(next)

    private fun isTableSeparator(line: String): Boolean {
        val parts = cells(line)
        return parts.isNotEmpty() && parts.all { cell -> cell.matches(Regex("^:?-{2,}:?$")) }
    }

    private fun parseAlignments(separator: String, count: Int): List<DshTableAlign> {
        val parts = cells(separator)
        return List(count) { position ->
            val cell = parts.getOrElse(position) { "" }
            when {
                cell.startsWith(":") && cell.endsWith(":") -> DshTableAlign.CENTER
                cell.endsWith(":") -> DshTableAlign.RIGHT
                else -> DshTableAlign.LEFT
            }
        }
    }

    /** 拆分表格单元：支持转义竖线与行内代码中的竖线，避免把代码内容截断。 */
    private fun cells(line: String): List<String> {
        val raw = line.trim()
        // 只去掉首尾未转义的边界竖线，避免把单元格末尾的 `\|` 误删。
        val start = if (raw.startsWith("|")) 1 else 0
        val end = if (raw.length > start && raw.endsWith("|") && !raw.endsWith("\\|")) {
            raw.length - 1
        } else {
            raw.length
        }
        val trimmed = raw.substring(start, end)
        val cells = ArrayList<String>()
        val current = StringBuilder()
        var escaped = false
        var inCode = false
        for (character in trimmed) {
            when {
                escaped -> {
                    if (character != '|') current.append('\\')
                    current.append(character)
                    escaped = false
                }
                character == '\\' -> escaped = true
                character == '`' -> {
                    inCode = !inCode
                    current.append(character)
                }
                character == '|' && !inCode -> {
                    cells += current.toString().trim()
                    current.setLength(0)
                }
                else -> current.append(character)
            }
        }
        cells += current.toString().trim()
        return cells
    }
}

private const val LINK_BLUE = 0xFF4C8DFF

/**
 * 行内 Markdown 转 Compose AnnotatedString。
 * 链接生成可点击 [LinkAnnotation]，但只有用户确认后才会调起系统应用；
 * 图片不在这里加载，避免在文本层绕过会话授权。
 */
internal fun dshInlineMarkdown(
    text: String,
    codeBackground: Color,
    onLinkClick: ((String) -> Unit)? = null
): AnnotatedString = buildAnnotatedString {
    val linkStyle = SpanStyle(color = Color(LINK_BLUE), textDecoration = TextDecoration.Underline)
    val linkStyles = TextLinkStyles(
        style = linkStyle,
        pressedStyle = SpanStyle(color = Color(LINK_BLUE), background = codeBackground)
    )
    appendInline(this, text, codeBackground, linkStyles, onLinkClick)
}

private fun appendInline(
    builder: AnnotatedString.Builder,
    text: String,
    codeBackground: Color,
    linkStyles: TextLinkStyles,
    onLinkClick: ((String) -> Unit)?,
    allowLinks: Boolean = true
) {
    var index = 0
    while (index < text.length) {
        when {
            // 反斜杠转义只保留被转义字符本身，避免 \* 被误当成强调标记。
            text[index] == '\\' && index + 1 < text.length && text[index + 1] in "\\`*_{}[]()#+-.!>~|" -> {
                builder.append(text[index + 1])
                index += 2
            }
            text.startsWith("`", index) -> {
                val end = text.indexOf('`', index + 1)
                if (end > index + 1) {
                    builder.withStyle(
                        SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)
                    ) {
                        append(text.substring(index + 1, end))
                    }
                    index = end + 1
                } else {
                    builder.append('`')
                    index++
                }
            }
            text.startsWith("***", index) || text.startsWith("___", index) -> {
                val marker = text.substring(index, index + 3)
                val end = text.indexOf(marker, index + 3)
                if (end > index + 3) {
                    builder.withStyle(
                        SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
                    ) {
                        appendInline(
                            this, text.substring(index + 3, end), codeBackground, linkStyles,
                            onLinkClick, allowLinks
                        )
                    }
                    index = end + 3
                } else {
                    builder.append(marker)
                    index += 3
                }
            }
            text.startsWith("**", index) || text.startsWith("__", index) -> {
                val marker = text.substring(index, index + 2)
                val end = closingMarker(text, marker, index + 2)
                if (end > index + 2) {
                    builder.withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        appendInline(
                            this, text.substring(index + 2, end), codeBackground, linkStyles,
                            onLinkClick, allowLinks
                        )
                    }
                    index = end + 2
                } else {
                    builder.append(marker)
                    index += 2
                }
            }
            text.startsWith("~~", index) -> {
                val end = text.indexOf("~~", index + 2)
                if (end > index + 2) {
                    builder.withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        appendInline(
                            this, text.substring(index + 2, end), codeBackground, linkStyles,
                            onLinkClick, allowLinks
                        )
                    }
                    index = end + 2
                } else {
                    builder.append("~~")
                    index += 2
                }
            }
            allowLinks && text.startsWith("<", index) -> {
                val end = text.indexOf('>', index + 1)
                val inner = if (end > index + 1) text.substring(index + 1, end) else ""
                val decision = if (inner.isNotBlank()) DshLinkPolicy.classify(inner) else null
                if (decision != null && decision.canOpen) {
                    builder.appendLink(inner, linkStyles, onLinkClick)
                    index = end + 1
                } else {
                    builder.append(text[index])
                    index++
                }
            }
            allowLinks && (text.startsWith("http://", index) || text.startsWith("https://", index)) &&
                (index == 0 || text[index - 1].isWhitespace() || text[index - 1] in "([<") -> {
                var end = index
                while (end < text.length && !text[end].isWhitespace() &&
                    text[end] !in "<>\"'，。；：！？、）】》"
                ) end++
                var url = text.substring(index, end)
                while (url.isNotEmpty() && url.last() in ".,;:!?)]}") url = url.dropLast(1)
                if (url.length > 8) {
                    builder.appendLink(url, linkStyles, onLinkClick)
                    index += url.length
                } else {
                    builder.append(text[index])
                    index++
                }
            }
            allowLinks && text[index] == '[' -> {
                val close = text.indexOf(']', index + 1)
                val openParen = if (close > 0 && close + 1 < text.length && text[close + 1] == '(') close + 1 else -1
                val end = if (openParen > 0) text.indexOf(')', openParen + 1) else -1
                if (close > index + 1 && end > openParen + 1) {
                    val label = text.substring(index + 1, close)
                    val url = text.substring(openParen + 1, end).trim()
                    if (label.isNotBlank() && url.isNotBlank()) {
                        builder.withLink(
                            LinkAnnotation.Clickable(
                                tag = url,
                                styles = linkStyles,
                                linkInteractionListener = { onLinkClick?.invoke(url) }
                            )
                        ) {
                            withStyle(linkStyles.style ?: SpanStyle()) {
                                appendInline(
                                    this, label, codeBackground, linkStyles, onLinkClick,
                                    allowLinks = false
                                )
                            }
                        }
                    } else {
                        builder.append(text[index])
                        index++
                    }
                    index = end + 1
                } else {
                    builder.append(text[index])
                    index++
                }
            }
            text[index] == '*' || text[index] == '_' -> {
                val marker = text[index]
                if (marker == '_' && index > 0 && text[index - 1].isLetterOrDigit()) {
                    builder.append(marker)
                    index++
                } else {
                    val end = text.indexOf(marker, index + 1)
                    if (end > index + 1) {
                        builder.withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            appendInline(
                                this, text.substring(index + 1, end), codeBackground, linkStyles,
                                onLinkClick, allowLinks
                            )
                        }
                        index = end + 1
                    } else {
                        builder.append(marker)
                        index++
                    }
                }
            }
            else -> {
                builder.append(text[index])
                index++
            }
        }
    }
}

private fun closingMarker(text: String, marker: String, start: Int): Int {
    var position = text.indexOf(marker, start)
    while (position >= 0) {
        // `**` 不吞掉 `***` 的第三个星号，交给加粗+斜体分支处理。
        if (marker == "__" && position > 0 && text[position - 1].isLetterOrDigit()) {
            position = text.indexOf(marker, position + marker.length)
            continue
        }
        return position
    }
    return -1
}

private fun AnnotatedString.Builder.appendLink(
    url: String,
    linkStyles: TextLinkStyles,
    onLinkClick: ((String) -> Unit)?
) {
    withLink(
        LinkAnnotation.Clickable(
            tag = url,
            styles = linkStyles,
            linkInteractionListener = { onLinkClick?.invoke(url) }
        )
    ) {
        withStyle(linkStyles.style ?: SpanStyle()) { append(url) }
    }
}

/**
 * 原生消息正文渲染。代码块等宽、可横向滚动并可选行号；表格可横向滚动；
 * 远程图片只显示占位与中文原因，本地/内嵌图片点击后在对话框查看，读取失败显示中文提示。
 * 链接点击先弹确认框，确认后才交给系统浏览器/邮件/拨号。
 */
@Composable
fun DshMarkdown(text: String, modifier: Modifier = Modifier) {
    val blocks = remember(text) { DshMarkdownParser.parse(text) }
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    var pendingLink by remember { mutableStateOf<DshLinkDecision?>(null) }
    var linkError by remember { mutableStateOf<String?>(null) }
    var viewingImage by remember { mutableStateOf<DshMarkdownBlock.Image?>(null) }
    var viewingAttachment by remember { mutableStateOf<DshMarkdownBlock.Attachment?>(null) }

    val onLink: (String) -> Unit = { raw ->
        linkError = null
        pendingLink = DshLinkPolicy.classify(raw)
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEachIndexed { index, block ->
            when (block) {
                is DshMarkdownBlock.Paragraph -> SelectionContainer {
                    Text(
                        dshInlineMarkdown(block.text, codeBackground, onLink),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                is DshMarkdownBlock.Heading -> {
                    val style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        3 -> MaterialTheme.typography.titleSmall
                        else -> MaterialTheme.typography.bodyLarge
                    }
                    Text(
                        dshInlineMarkdown(block.text, codeBackground, onLink),
                        style = style,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                is DshMarkdownBlock.Code -> DshCodeBlock(index, block.language, block.code)
                is DshMarkdownBlock.Quote -> DshQuote(block.text, codeBackground, onLink)
                is DshMarkdownBlock.ListBlock -> DshList(block, codeBackground, onLink)
                is DshMarkdownBlock.Table -> DshTable(block, codeBackground, onLink)
                is DshMarkdownBlock.Image -> DshImageCard(block) { viewingImage = block }
                is DshMarkdownBlock.Attachment -> DshAttachmentCard(block) { viewingAttachment = block }
                is DshMarkdownBlock.Truncated -> Text(
                    block.message,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                DshMarkdownBlock.Rule -> HorizontalDivider(Modifier.padding(vertical = 2.dp))
            }
        }
    }

    pendingLink?.let { decision ->
        val canOpen = decision.canOpen && decision.url.isNotBlank()
        AlertDialog(
            onDismissRequest = { pendingLink = null },
            title = { Text(if (canOpen) "打开链接？" else "无法打开链接") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(decision.confirmText, style = MaterialTheme.typography.bodySmall)
                    if (decision.url.isNotBlank()) {
                        SelectionContainer {
                            Text(
                                decision.url,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 6,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    linkError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (canOpen) {
                            runCatching { uriHandler.openUri(decision.url) }
                                .onSuccess { pendingLink = null }
                                .onFailure { linkError = "打开失败：${it.message?.take(80) ?: "系统没有可处理该链接的应用"}" }
                        } else {
                            if (decision.url.isNotBlank()) {
                                clipboard.setText(AnnotatedString(decision.url))
                            }
                            pendingLink = null
                        }
                    }
                ) { Text(if (canOpen) "打开" else "复制地址") }
            },
            dismissButton = {
                TextButton(onClick = { pendingLink = null }) { Text(if (canOpen) "取消" else "关闭") }
            }
        )
    }

    viewingImage?.let { image ->
        DshImageViewer(image) { viewingImage = null }
    }
    viewingAttachment?.let { attachment ->
        DshAttachmentViewer(attachment) { viewingAttachment = null }
    }
}

@Composable
private fun DshQuote(text: String, codeBackground: Color, onLink: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
        )
        SelectionContainer {
            Text(
                dshInlineMarkdown(text, codeBackground, onLink),
                Modifier.padding(start = 10.dp, top = 2.dp, bottom = 2.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DshList(block: DshMarkdownBlock.ListBlock, codeBackground: Color, onLink: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        block.items.forEach { item ->
            val marker = when {
                item.checked == true -> "☑"
                item.checked == false -> "☐"
                item.ordered -> "${item.number}."
                else -> "•"
            }
            Row(Modifier.padding(start = (item.depth * 16).dp)) {
                Text(
                    marker,
                    Modifier.width(32.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    fontWeight = FontWeight.SemiBold
                )
                SelectionContainer {
                    Text(
                        dshInlineMarkdown(item.text, codeBackground, onLink),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun DshTable(
    table: DshMarkdownBlock.Table,
    codeBackground: Color,
    onLink: (String) -> Unit
) {
    val view = remember(table) { DshTablePolicy.view(table) }
    val cellModifier = Modifier.widthIn(min = 108.dp, max = 240.dp).padding(8.dp)
    Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Row(Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            view.headers.forEachIndexed { index, header ->
                Text(
                    dshInlineMarkdown(header, codeBackground, onLink),
                    cellModifier,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = view.alignments.getOrElse(index) { DshTableAlign.LEFT }.toTextAlign()
                )
            }
        }
        view.rows.forEachIndexed { rowIndex, row ->
            Row(
                Modifier.background(
                    if (rowIndex % 2 == 1) MaterialTheme.colorScheme.surfaceContainerLow else Color.Transparent
                )
            ) {
                row.forEachIndexed { index, cell ->
                    Text(
                        dshInlineMarkdown(cell, codeBackground, onLink),
                        cellModifier,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = view.alignments.getOrElse(index) { DshTableAlign.LEFT }.toTextAlign()
                    )
                }
            }
            HorizontalDivider()
        }
        if (view.truncatedRows || view.truncatedColumns) {
            Text(
                buildString {
                    if (view.truncatedRows) append("仅显示前 ${view.rows.size} 行（共 ${view.totalRows} 行）")
                    if (view.truncatedRows && view.truncatedColumns) append("；")
                    if (view.truncatedColumns) append("仅显示前 ${view.headers.size} 列")
                    append("；完整表格请在官方界面查看。")
                },
                Modifier.padding(8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun DshTableAlign.toTextAlign(): TextAlign = when (this) {
    DshTableAlign.LEFT -> TextAlign.Start
    DshTableAlign.CENTER -> TextAlign.Center
    DshTableAlign.RIGHT -> TextAlign.End
}

@Composable
private fun DshCodeBlock(index: Int, language: String?, code: String) {
    val clipboard = LocalClipboardManager.current
    val view = remember(code) { DshCodeBlockPolicy.view(code) }
    var showLineNumbers by rememberSaveable("code-lines-$index") { mutableStateOf(false) }
    val numberWidth = remember(view.totalLines) { DshCodeBlockPolicy.numberWidth(view.totalLines) }
    val lineNumberColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
    val rendered = remember(view, showLineNumbers, numberWidth, lineNumberColor) {
        buildAnnotatedString {
            view.lines.forEachIndexed { lineIndex, line ->
                if (lineIndex > 0) append('\n')
                if (showLineNumbers) {
                    withStyle(
                        SpanStyle(
                            color = lineNumberColor,
                            fontFamily = FontFamily.Monospace
                        )
                    ) {
                        append(DshCodeBlockPolicy.lineNumberPrefix(lineIndex + 1, numberWidth))
                    }
                }
                append(line)
            }
        }
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(start = 10.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    language?.takeIf { it.isNotBlank() } ?: "代码",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { showLineNumbers = !showLineNumbers }) {
                    Text(if (showLineNumbers) "隐藏行号" else "行号")
                }
                TextButton(onClick = { clipboard.setText(AnnotatedString(code)) }) { Text("复制") }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                SelectionContainer {
                    Text(
                        rendered,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        softWrap = false
                    )
                }
            }
            if (view.truncated) {
                Text(
                    "仅显示前 ${view.lines.size} 行（共 ${view.totalLines} 行）；复制可获取完整代码。",
                    Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DshImageCard(image: DshMarkdownBlock.Image, onOpen: () -> Unit) {
    val info = remember(image.alt, image.url) { DshImagePolicy.describe(image.alt, image.url) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("图", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
            Column(Modifier.padding(start = 10.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "图片：${image.alt.ifBlank { "未命名" }}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    info.message,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = onOpen) { Text("查看") }
        }
    }
}

@Composable
private fun DshAttachmentCard(attachment: DshMarkdownBlock.Attachment, onOpen: () -> Unit) {
    val info = remember(attachment.name, attachment.url) {
        DshAttachmentPolicy.describe(attachment.name, attachment.url)
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("附", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
            Column(Modifier.padding(start = 10.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "附件：${info.name}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    info.message,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = onOpen) { Text("查看") }
        }
    }
}

@Composable
private fun DshImageViewer(image: DshMarkdownBlock.Image, onClose: () -> Unit) {
    val info = remember(image.alt, image.url) { DshImagePolicy.describe(image.alt, image.url) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var bitmap by remember(info.url) { mutableStateOf<ImageBitmap?>(null) }
    var failure by remember(info.url) { mutableStateOf<String?>(null) }
    var loading by remember(info.url) { mutableStateOf(info.canPreview) }

    LaunchedEffect(info.url, info.kind) {
        if (!info.canPreview) {
            loading = false
            return@LaunchedEffect
        }
        val decoded = withContext(Dispatchers.IO) {
            runCatching { DshLocalImageLoader.load(context, info) }.getOrNull()
        }
        bitmap = decoded?.asImageBitmap()
        failure = if (decoded == null) "图片加载失败：文件不可读、已损坏或格式不支持" else null
        loading = false
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("图片：${image.alt.ifBlank { "未命名" }}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    loading -> Box(Modifier.fillMaxWidth().heightIn(min = 120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    bitmap != null -> Image(
                        bitmap = bitmap!!,
                        contentDescription = image.alt.ifBlank { "消息图片" },
                        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                        contentScale = ContentScale.Fit
                    )
                    else -> Text(
                        failure ?: info.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (failure != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                SelectionContainer {
                    Text(
                        image.url,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (image.url.isNotBlank()) clipboard.setText(AnnotatedString(image.url))
                onClose()
            }) { Text("复制地址") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("关闭") } }
    )
}

@Composable
private fun DshAttachmentViewer(attachment: DshMarkdownBlock.Attachment, onClose: () -> Unit) {
    val info = remember(attachment.name, attachment.url) {
        DshAttachmentPolicy.describe(attachment.name, attachment.url)
    }
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("附件：${info.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(info.message, style = MaterialTheme.typography.bodySmall)
                SelectionContainer {
                    Text(
                        attachment.url,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(attachment.url))
                onClose()
            }) { Text("复制地址") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("关闭") } }
    )
}

/**
 * 只读取本地/内嵌图片，且按尺寸采样，避免消息里的超大图在解码时占满内存。
 * 远程图片保持不自动联网，由占位卡片提示用户查看地址。
 */
private object DshLocalImageLoader {
    private const val MAX_DIMENSION = 2_048

    fun load(context: Context, info: DshImageInfo): Bitmap? = when (info.kind) {
        DshImageKind.EMBEDDED -> decodeEmbedded(info.url)
        DshImageKind.LOCAL -> decodeLocal(context, info.url)
        else -> null
    }

    private fun decodeEmbedded(url: String): Bitmap? {
        val comma = url.indexOf(',')
        if (comma <= 0 || comma >= url.length - 1) return null
        val payload = url.substring(comma + 1)
        if (payload.length > DshImagePolicy.MAX_EMBEDDED_CHARS) return null
        val bytes = runCatching { Base64.decode(payload, Base64.DEFAULT) }.getOrNull() ?: return null
        return decodeBytes(bytes)
    }

    private fun decodeLocal(context: Context, url: String): Bitmap? {
        val uri = runCatching { url.toUri() }.getOrNull() ?: return null
        val stream = runCatching {
            if (uri.scheme == "file") {
                uri.path?.let { java.io.FileInputStream(it) }
            } else {
                context.contentResolver.openInputStream(uri)
            }
        }.getOrNull() ?: return null
        return stream.use { decodeStream(it) }
    }

    private fun decodeBytes(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds) }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun decodeStream(stream: java.io.InputStream): Bitmap? {
        // 有界读取，避免异常大的 content:// 流在解码前就占满内存。
        val limit = 12 * 1024 * 1024
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) return null
            output.write(buffer, 0, read)
        }
        return decodeBytes(output.toByteArray())
    }

    private fun sampleSize(bounds: BitmapFactory.Options): Int {
        var sample = 1
        var width = bounds.outWidth
        var height = bounds.outHeight
        while (width / sample > MAX_DIMENSION || height / sample > MAX_DIMENSION) {
            sample *= 2
            width /= 2
            height /= 2
        }
        return sample
    }
}
