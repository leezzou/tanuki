package com.tanuki.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tanuki.ui.design.TanukiPillButton
import com.tanuki.ui.design.TanukiPillButtonTone
import com.tanuki.ui.design.TanukiStatusDot
import com.tanuki.update.ApkUpdateCheckState
import com.tanuki.update.AppUpdatePhase
import com.tanuki.update.AppUpdateState
import com.tanuki.update.UpdateCheckStatus
import kotlinx.coroutines.delay
import java.util.Locale

/** 主体展示版本结论和更新动作；安装约束留在可展开详情，小屏和大字体可换行。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ApplicationUpdateCard(state: AppUpdateState, currentVersion: String, installAllowed: Boolean,
    onCheck: () -> Unit, onDownload: () -> Unit, onInstall: () -> Unit, onCancel: () -> Unit,
    background: ApkUpdateCheckState = ApkUpdateCheckState()) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var details by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.busy, state.startedAt) {
        while (state.busy) { now = SystemClock.elapsedRealtime(); delay(1_000) }
    }
    val versionStatus = ApplicationUpdatePresentation.status(state, background)
    val lastChecked = remember(background.lastCheckedAt) {
        if (background.lastCheckedAt <= 0L) "尚未检查"
        else runCatching { java.text.SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(java.util.Date(background.lastCheckedAt)) }
            .getOrDefault("未知")
    }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Tanuki 应用更新", style = MaterialTheme.typography.titleLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("当前版本 $currentVersion", style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TanukiStatusDot(versionStatus.tone, decorative = true)
                    Text(versionStatus.label, style = MaterialTheme.typography.labelLarge)
                }
            }
            Text(ApplicationUpdatePresentation.summary, style = MaterialTheme.typography.bodyMedium)
            Text(ApplicationUpdatePresentation.message(state), style = MaterialTheme.typography.bodyMedium,
                color = if (state.phase == AppUpdatePhase.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            TanukiPillButton(text = "检查更新", onClick = onCheck, enabled = !state.busy,
                tone = TanukiPillButtonTone.Filled, modifier = Modifier.fillMaxWidth())
            Text("后台每 30 分钟检查一次（需联网） · 上次 $lastChecked", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (background.status == UpdateCheckStatus.FAILED) {
                Text("上次自动检查未完成，稍后会重试；也可以手动检查。", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.candidate?.let { candidate ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("可用版本 ${candidate.versionName}", style = MaterialTheme.typography.titleMedium)
                Text("下载大小 ${megabytes(candidate.bytes)} MiB", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.phase in setOf(AppUpdatePhase.AVAILABLE, AppUpdatePhase.CANCELLED, AppUpdatePhase.FAILED)) {
                    Button(onClick = onDownload, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("下载更新") }
                }
                if (state.phase == AppUpdatePhase.READY) {
                    Button(onClick = onInstall, enabled = installAllowed, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("安装更新") }
                    if (!installAllowed) Text("请先停止 DSH，并等待环境和文件任务结束；下载文件会保留。", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (state.busy) {
                val fraction = if (state.phase == AppUpdatePhase.DOWNLOADING && state.total > 0)
                    (state.received.toDouble() / state.total).toFloat().coerceIn(0f, 1f) else null
                OperationProgressBar(fraction, Modifier.fillMaxWidth().semantics {
                    contentDescription = if (fraction != null) "应用更新下载进度" else "应用更新正在处理"
                })
                if (fraction != null) Text("${megabytes(state.received)} / ${megabytes(state.total)} MiB", style = MaterialTheme.typography.labelMedium)
                Text("已用 ${((now - state.startedAt).coerceAtLeast(0) / 1_000)} 秒", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) { Text("取消更新任务") }
            }
            TextButton(onClick = { details = !details }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (details) "收起详情" else "了解详情")
            }
            if (details) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(ApplicationUpdatePresentation.details, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(ApplicationUpdatePresentation.dataNotice, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.candidate?.let { Text("应用包名：${it.packageName}", style = MaterialTheme.typography.bodySmall) }
                    if (state.message.isNotBlank()) Text(state.message, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun megabytes(bytes: Long): String = String.format(Locale.ROOT, "%.1f", bytes / (1024.0 * 1024))
