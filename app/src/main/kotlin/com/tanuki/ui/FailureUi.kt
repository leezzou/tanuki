package com.tanuki.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tanuki.runtime.AppFailure
import com.tanuki.runtime.FailureActionKind

/**
 * 组件卡内嵌错误块：失败后就地显示原因与主行动，不依赖 toast 或顶部进度条。
 */
@Composable
internal fun InlineFailureBlock(
    failure: AppFailure,
    onAction: (FailureActionKind) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.large,
        color = colors.errorContainer,
        contentColor = colors.onErrorContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(failure.title, style = MaterialTheme.typography.titleSmall)
            Text(failure.reason, style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = { onAction(failure.primaryAction.kind) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) { Text(failure.primaryAction.label) }
            failure.secondaryAction?.let { secondary ->
                OutlinedButton(
                    onClick = { onAction(secondary.kind) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) { Text(secondary.label) }
            }
            // 技术原文默认折叠；用户需要反馈问题时再展开复制。
            TanukiDetails("详情") {
                SelectableCopy(failure.detail, error = true)
                if (failure.attempt > 0) {
                    Text("已尝试 ${failure.attempt} 次", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/**
 * 全局错误条：任何 Tab 失败时停在顶部，点整条或「处理」打开详情；
 * 关闭只隐藏本次提示，组件卡和工作台里的错误仍保留。
 */
@Composable
internal fun GlobalFailureBar(
    failure: AppFailure,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onOpen,
        shape = MaterialTheme.shapes.large,
        color = colors.errorContainer,
        contentColor = colors.onErrorContainer,
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "${failure.title}：${failure.reason}",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = onOpen) { Text("处理") }
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    }
}

/**
 * 错误详情：展开原因、技术原文与主/次行动；复制内容带时间，便于反馈。
 * 行动执行前先关闭对话框，避免和确认弹窗叠在一起。
 */
@Composable
internal fun FailureDetailDialog(
    failure: AppFailure,
    versionName: String,
    onAction: (FailureActionKind) -> Unit,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(failure.stableKey) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(failure.title) },
        text = {
            Column(
                Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(failure.reason, style = MaterialTheme.typography.bodyMedium)
                failure.secondaryAction?.let { secondary ->
                    OutlinedButton(
                        onClick = { onAction(secondary.kind); onDismiss() },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) { Text(secondary.label) }
                }
                TanukiDetails("技术详情") {
                    SelectableCopy(failure.detail, error = true)
                }
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(failure.copyText(versionName)))
                        copied = true
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text(if (copied) "已复制" else "复制错误信息") }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAction(failure.primaryAction.kind); onDismiss() }) {
                Text(failure.primaryAction.label)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}
