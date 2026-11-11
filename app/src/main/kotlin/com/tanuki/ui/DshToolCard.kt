package com.tanuki.ui

import androidx.compose.foundation.BorderStroke
import com.tanuki.ui.design.TanukiDesign
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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

/** 相邻调用的展示组；缺失结果保持未完成，不把历史截断算成成功。 */
@Composable
internal fun DshToolGroupCard(
    tools: List<DshToolItem>,
    pendingApprovalCalls: Set<String> = emptySet(),
    onOpenApproval: () -> Unit = {}
) {
    if (tools.isEmpty()) return
    val colors = TanukiDesign.tokens.colors
    var expanded by rememberSaveable(tools.first().callId) { mutableStateOf(false) }
    val completed = tools.all { it.status == DshToolStatus.SUCCESS }
    val failed = tools.any { it.status == DshToolStatus.FAILED }
    Surface(color = Color(colors.surface), contentColor = Color(colors.textPrimary),
        shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(colors.outline)),
        modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClickLabel = if (expanded) "收起工具调用" else "展开工具调用") {
                expanded = !expanded
            }.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (completed) "✓" else if (failed) "!" else "◌", color = Color(colors.primary))
                Text("${if (completed) "已完成" else if (failed) "调用失败" else "未完成"} · ${tools.size} 次工具调用 · ${tools.count { it.finishedAt != null }} 条结果消息",
                    modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                TanukiIconSwap(expanded, Modifier.size(24.dp)) { Text(if (it) "⌃" else "⌄") }
            }
            // 不补间高度，避免与停靠条或输入换行同时播放高度动画。
            if (expanded) tools.forEach { item ->
                DshToolCard(item, embedded = true, awaitingApproval = item.callId in pendingApprovalCalls,
                    onOpenApproval = onOpenApproval)
            }
        }
    }
}

/**
 * 工具调用卡片：状态、耗时、命令、工作目录、文件变更、diff、参数与可折叠输出。
 *
 * 数据全部来自官方 `tool/call` + `tool/result`；没有结果时明确显示“进行中”，
 * 不把历史截断伪装成成功。输出默认折叠，失败时默认展开错误与输出；输出和错误都可复制。
 */
@Composable
fun DshToolCard(
    item: DshToolItem,
    modifier: Modifier = Modifier,
    embedded: Boolean = false,
    awaitingApproval: Boolean = false,
    onOpenApproval: () -> Unit = {}
) {
    val clipboard = LocalClipboardManager.current
    var expanded by rememberSaveable(item.callId) { mutableStateOf(item.status == DshToolStatus.FAILED) }
    var diffExpanded by rememberSaveable(item.callId) { mutableStateOf(false) }
    val errorColor = Color(TanukiDesign.tokens.colors.danger)
    val duration = DshToolDuration.label(item.durationMs)
    Surface(
        color = Color(TanukiDesign.tokens.colors.surface),
        contentColor = Color(TanukiDesign.tokens.colors.textPrimary),
        shape = RoundedCornerShape(if (embedded) 0.dp else 16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        color = Color(TanukiDesign.tokens.colors.textSecondary)
                    )
                }
                DshRiskPill(item.risk.level, item.risk.summary)
                if (item.hasDetails) {
                    Text(
                        if (expanded) "收起" else "展开",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(TanukiDesign.tokens.colors.primary)
                    )
                }
            }
            if (item.status == DshToolStatus.RUNNING) {
                Text(
                    "未读取到官方结果：可能仍在运行，或历史页被截断。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(TanukiDesign.tokens.colors.textSecondary)
                )
            }
            if (awaitingApproval) {
                TextButton(onClick = onOpenApproval) {
                    Text("🔒 运行 · 需要授权后才能执行", style = MaterialTheme.typography.labelSmall)
                }
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
                        color = Color(TanukiDesign.tokens.colors.dangerContainer),
                        contentColor = Color(TanukiDesign.tokens.colors.onDangerContainer),
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
                                TanukiCopyButton("复制错误", identity = item.callId, onCopy = {
                                    clipboard.setText(AnnotatedString(error))
                                })
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
                            TanukiCopyButton("复制输出", identity = item.callId, onCopy = {
                                clipboard.setText(AnnotatedString(output))
                            })
                        }
                        Surface(
                            color = Color(TanukiDesign.tokens.colors.canvas),
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
                                color = Color(TanukiDesign.tokens.colors.textSecondary)
                            )
                        }
                    }
                }
            } else if (!item.output.isNullOrBlank()) {
                Text(
                    "输出已折叠 · 点击卡片展开",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(TanukiDesign.tokens.colors.textSecondary)
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
        DshToolStatus.SUCCESS -> StatusDot(Color(TanukiDesign.tokens.colors.primary))
        DshToolStatus.FAILED -> StatusDot(Color(TanukiDesign.tokens.colors.danger))
    }
}

@Composable
private fun StatusDot(color: Color) {
    Box(Modifier.size(10.dp).background(color, RoundedCornerShape(50)))
}

@Composable
internal fun DshRiskPill(level: DshRiskLevel, summary: String) {
    val color = when (level) {
        DshRiskLevel.HIGH -> Color(TanukiDesign.tokens.colors.dangerContainer)
        DshRiskLevel.MEDIUM -> Color(TanukiDesign.tokens.colors.primaryContainer)
        DshRiskLevel.LOW -> Color(TanukiDesign.tokens.colors.primaryContainer)
        DshRiskLevel.UNKNOWN -> Color(TanukiDesign.tokens.colors.canvas)
    }
    val content = when (level) {
        DshRiskLevel.HIGH -> Color(TanukiDesign.tokens.colors.onDangerContainer)
        DshRiskLevel.MEDIUM -> Color(TanukiDesign.tokens.colors.textPrimary)
        DshRiskLevel.LOW -> Color(TanukiDesign.tokens.colors.textPrimary)
        DshRiskLevel.UNKNOWN -> Color(TanukiDesign.tokens.colors.textSecondary)
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
        color = Color(TanukiDesign.tokens.colors.canvas),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "命令",
                style = MaterialTheme.typography.labelSmall,
                color = Color(TanukiDesign.tokens.colors.textSecondary)
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
                    color = Color(TanukiDesign.tokens.colors.textSecondary)
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
                        color = Color(TanukiDesign.tokens.colors.primary)
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        "-$removed",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(TanukiDesign.tokens.colors.danger)
                    )
                }
                val lines = DshDiffPresentation.lines(diff)
                val shown = if (expanded) lines else lines.take(24)
                Surface(
                    color = Color(TanukiDesign.tokens.colors.canvas),
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
            Color(TanukiDesign.tokens.colors.primary).copy(alpha = 0.12f),
            Color(TanukiDesign.tokens.colors.primary)
        )
        DshDiffLineKind.REMOVED -> Triple(
            "-",
            Color(TanukiDesign.tokens.colors.danger).copy(alpha = 0.14f),
            Color(TanukiDesign.tokens.colors.danger)
        )
        DshDiffLineKind.CONTEXT -> Triple(
            " ",
            Color.Transparent,
            Color(TanukiDesign.tokens.colors.textSecondary)
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
            color = Color(TanukiDesign.tokens.colors.textSecondary)
        )
        content()
    }
}
