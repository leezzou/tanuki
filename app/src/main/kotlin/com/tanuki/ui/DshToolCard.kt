package com.tanuki.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tanuki.web.DshDiffLine
import com.tanuki.web.DshDiffLineKind
import com.tanuki.web.DshDiffPresentation
import com.tanuki.web.DshFileDiff
import com.tanuki.web.DshRiskLevel
import com.tanuki.web.DshToolArguments
import com.tanuki.web.DshToolDuration
import com.tanuki.web.DshToolItem
import com.tanuki.web.DshToolStatus

/**
 * 工具调用卡片：状态、耗时、命令、工作目录、文件变更、diff、参数与可折叠输出。
 *
 * 数据全部来自官方 `tool/call` + `tool/result`；没有结果时明确显示“进行中”，
 * 不把历史截断伪装成成功。输出默认折叠，失败时默认展开错误与输出；输出和错误都可复制。
 */
@Composable
fun DshToolCard(item: DshToolItem, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    var expanded by rememberSaveable(item.callId) { mutableStateOf(item.status == DshToolStatus.FAILED) }
    var diffExpanded by rememberSaveable(item.callId) { mutableStateOf(false) }
    var copiedOutput by rememberSaveable(item.callId) { mutableStateOf(false) }
    var copiedError by rememberSaveable(item.callId) { mutableStateOf(false) }
    val errorColor = MaterialTheme.colorScheme.error
    val duration = DshToolDuration.label(item.durationMs)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable(enabled = item.hasDetails) { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ToolStatusMark(item.status)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        item.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        buildString {
                            append(item.status.label)
                            if (duration != null) {
                                append(" · ")
                                append(duration)
                            } else if (item.status != DshToolStatus.RUNNING) {
                                append(" · 耗时未知")
                            }
                            if (item.command == null && item.files.isNotEmpty()) {
                                append(" · ")
                                append(item.files.size)
                                append(" 个文件")
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DshRiskPill(item.risk.level, item.risk.summary)
                if (item.hasDetails) {
                    Text(
                        if (expanded) "收起" else "展开",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (item.status == DshToolStatus.RUNNING) {
                Text(
                    "未读取到官方结果：可能仍在运行，或历史页被截断。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item.command?.let { CommandBlock(it, item.cwd) }
            if (item.files.isNotEmpty()) {
                FileChanges(item.files)
            }
            if (item.diffs.isNotEmpty()) {
                DiffsBlock(item.diffs, diffExpanded) { diffExpanded = !diffExpanded }
            }
            if (expanded) {
                DshToolArguments.pretty(item.arguments)?.let { pretty ->
                    LabeledBlock("参数") {
                        SelectionContainer {
                            Text(
                                pretty,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
                item.error?.let { error ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "错误",
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                TextButton(onClick = {
                                    clipboard.setText(AnnotatedString(error))
                                    copiedError = true
                                }) { Text(if (copiedError) "已复制" else "复制错误") }
                            }
                            SelectionContainer {
                                Text(error, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                item.output?.let { output ->
                    LabeledBlock("输出") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = {
                                clipboard.setText(AnnotatedString(output))
                                copiedOutput = true
                            }) { Text(if (copiedOutput) "已复制" else "复制输出") }
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SelectionContainer {
                                Text(
                                    output,
                                    Modifier.padding(10.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        if (item.outputTruncated) {
                            Text(
                                "输出已截断，仅显示前 12,000 字符。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else if (!item.output.isNullOrBlank()) {
                Text(
                    "输出已折叠 · 点击卡片展开",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (item.error != null && !expanded) {
                Text(
                    item.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = errorColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ToolStatusMark(status: DshToolStatus) {
    when (status) {
        DshToolStatus.RUNNING -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        DshToolStatus.SUCCESS -> StatusDot(MaterialTheme.colorScheme.primary)
        DshToolStatus.FAILED -> StatusDot(MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun StatusDot(color: Color) {
    Box(Modifier.size(10.dp).background(color, RoundedCornerShape(50)))
}

@Composable
internal fun DshRiskPill(level: DshRiskLevel, summary: String) {
    val color = when (level) {
        DshRiskLevel.HIGH -> MaterialTheme.colorScheme.errorContainer
        DshRiskLevel.MEDIUM -> MaterialTheme.colorScheme.secondaryContainer
        DshRiskLevel.LOW -> MaterialTheme.colorScheme.primaryContainer
        DshRiskLevel.UNKNOWN -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val content = when (level) {
        DshRiskLevel.HIGH -> MaterialTheme.colorScheme.onErrorContainer
        DshRiskLevel.MEDIUM -> MaterialTheme.colorScheme.onSecondaryContainer
        DshRiskLevel.LOW -> MaterialTheme.colorScheme.onPrimaryContainer
        DshRiskLevel.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = color,
        contentColor = content,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.semantics { contentDescription = "风险 ${level.label}：$summary" }
    ) {
        Text(
            "风险 ${level.label}",
            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun CommandBlock(command: String, cwd: String?) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "命令",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SelectionContainer {
                Text(
                    "$ $command",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
            if (!cwd.isNullOrBlank()) {
                Text(
                    "工作目录：$cwd",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DiffsBlock(diffs: List<DshFileDiff>, expanded: Boolean, onToggle: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        diffs.forEach { diff ->
            val (added, removed) = DshDiffPresentation.counts(diff)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        diff.path,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "+$added",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        "-$removed",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                val lines = DshDiffPresentation.lines(diff)
                val shown = if (expanded) lines else lines.take(24)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        shown.forEach { line -> DiffLineRow(line) }
                        if (!expanded && lines.size > shown.size) {
                            TextButton(
                                onClick = onToggle,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                Text("展开完整 diff（${lines.size} 行）")
                            }
                        }
                        if (expanded && lines.size > 24) {
                            TextButton(
                                onClick = onToggle,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                Text("收起 diff")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiffLineRow(line: DshDiffLine) {
    val (prefix, background, foreground) = when (line.kind) {
        DshDiffLineKind.ADDED -> Triple(
            "+",
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            MaterialTheme.colorScheme.primary
        )
        DshDiffLineKind.REMOVED -> Triple(
            "-",
            MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
            MaterialTheme.colorScheme.error
        )
        DshDiffLineKind.CONTEXT -> Triple(
            " ",
            Color.Transparent,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Text(
        "$prefix${line.text}",
        Modifier.fillMaxWidth().background(background).padding(horizontal = 10.dp, vertical = 1.dp),
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = foreground
    )
}

@Composable
private fun FileChanges(files: List<String>) {
    LabeledBlock("文件变更（${files.size}）") {
        files.forEach { path ->
            Text(
                path,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LabeledBlock(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        content()
    }
}
