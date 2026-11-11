package com.tanuki.ui

import com.tanuki.ui.design.TanukiOutlinedButton as OutlinedButton

import com.tanuki.ui.design.TanukiButton as Button

import android.os.SystemClock
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tanuki.runtime.FileTransferProgress
import com.tanuki.runtime.Snapshot
import com.tanuki.runtime.operationActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

/**
 * 全局进度入口。
 *
 * 只在操作进行中或确实失败时出现一条细进度条，不再向文件页、远程页等页面注入
 * 常驻卡片；点击整条入口才展开抽屉，抽屉里再放阶段、耗时、取消和重试。
 */
@Composable
internal fun OperationProgressEntry(
    snapshot: Snapshot,
    transfers: StateFlow<List<FileTransferProgress>>,
    onCancel: () -> Unit,
    onCancelTransfer: (Long) -> Unit = {},
    onRetry: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    showFileTransfers: Boolean = true,
) {
    // 只有入口自己订阅传输流；进度帧不会重组整页内容。
    val transferItems by transfers.collectAsState()
    val visibleTransfers = if (showFileTransfers) transferItems else emptyList()
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val ticking = snapshot.operationActive || visibleTransfers.isNotEmpty()
    LaunchedEffect(ticking) {
        while (ticking) {
            delay(1000)
            now = SystemClock.elapsedRealtime()
        }
    }
    // 运行时没有 startedAt 时用入口首次出现的时间兜底，不把页面停留时长算成安装耗时。
    val appearedAt = remember(snapshot.operationActive) { SystemClock.elapsedRealtime() }
    val runtime = remember(snapshot, now, appearedAt) { operationProgress(snapshot, now, appearedAt) }
    val summary = FileTransferPresentation.summary(visibleTransfers)
    if (runtime == null && summary == null) return
    val fraction = if (runtime != null) runtime.fraction else summary?.percent?.let { it / 100f }
    val failed = runtime?.mode == OperationProgressMode.FAILED
    val status = when {
        failed -> "查看详情"
        runtime != null -> runtime.percent?.let { "$it%" } ?: runtime.statusText
        else -> summary?.let { it.percent?.let { p -> "传输中 $p%" } ?: "传输中" }.orEmpty()
    }
    val title = runtime?.failureTitle ?: runtime?.stage?.label ?: "文件"
    var expanded by remember { mutableStateOf(false) }
    Surface(modifier = modifier, shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 2.dp) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable { expanded = true }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "操作进度"
                stateDescription = when {
                    runtime == null -> status
                    failed -> "$title，上次操作没有完成"
                    fraction != null -> "$title，$status"
                    else -> "$title，正在准备"
                }
            }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelMedium, maxLines = 1,
            color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        OperationProgressBar(
            fraction = if (failed) null else fraction,
            failed = failed,
            modifier = Modifier.weight(1f),
        )
        Text(status, style = MaterialTheme.typography.labelMedium, maxLines = 1,
            color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    }
    if (expanded) OperationProgressDrawer(
        runtime = runtime,
        transfers = visibleTransfers,
        now = now,
        onDismiss = { expanded = false },
        onCancel = onCancel,
        onCancelTransfer = onCancelTransfer,
        onRetry = onRetry,
    )
}

/**
 * 连续进度条：确定进度只画当前阶段比例，未知总量画不定进度。
 *
 * 不使用带间隙/终点的默认分段样式，避免看起来像“一段快满、其余空白”的坏进度。
 */
@Composable
internal fun OperationProgressBar(
    fraction: Float?,
    modifier: Modifier = Modifier,
    failed: Boolean = false,
    height: Dp = 4.dp,
) {
    val trackColor = MaterialTheme.colorScheme.primaryContainer
    val barColor = if (failed) MaterialTheme.colorScheme.error else androidx.compose.ui.graphics.Color(com.tanuki.ui.design.TanukiDesign.tokens.colors.accent)
    BoxWithConstraints(
        modifier
            .then(if (failed) Modifier else if (fraction != null) Modifier.progressSemantics(fraction.coerceIn(0f, 1f))
                else Modifier.progressSemantics())
            .height(height)
            .clip(CircleShape)
            .background(trackColor),
    ) {
        when {
            failed -> Box(Modifier.fillMaxSize().background(barColor))
            fraction != null -> Box(
                Modifier.fillMaxHeight()
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .background(barColor),
            )
            fraction == null && com.tanuki.ui.design.TanukiDesign.tokens.reducedMotion -> {
                // 移除动画时保留不定进度的静态提示，文案仍说明正在准备，不假装是百分比。
                Box(Modifier.fillMaxHeight().fillMaxWidth(0.35f).background(barColor))
            }
            else -> {
                val transition = rememberInfiniteTransition(label = "不定进度")
                val progress by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 1400, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart,
                    ),
                    label = "不定进度位置",
                )
                val barWidth = maxWidth * 0.35f
                Box(
                    Modifier.fillMaxHeight()
                        .width(barWidth)
                        .offset(x = (maxWidth + barWidth) * progress - barWidth)
                        .background(barColor),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OperationProgressDrawer(
    runtime: OperationProgressText?,
    transfers: List<FileTransferProgress>,
    now: Long,
    onDismiss: () -> Unit,
    onCancel: () -> Unit,
    onCancelTransfer: (Long) -> Unit,
    onRetry: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 680.dp).verticalScroll(rememberScrollState())
                .navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (runtime?.mode == OperationProgressMode.FAILED) "操作失败" else "操作进度",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "关闭进度详情")
                }
            }
            runtime?.let { state ->
                Text(
                    state.statusText,
                    style = MaterialTheme.typography.headlineSmall,
                    color = when (state.mode) {
                        OperationProgressMode.FAILED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    },
                )
                OperationProgressBar(
                    fraction = state.fraction,
                    failed = state.mode == OperationProgressMode.FAILED,
                    modifier = Modifier.fillMaxWidth().semantics {
                        contentDescription = "当前阶段进度"
                        stateDescription = when {
                            state.mode == OperationProgressMode.FAILED -> "上次操作没有完成"
                            state.fraction != null -> "当前阶段 ${state.percent}%"
                            else -> "进行中，未提供可信总量"
                        }
                    },
                )
                Text(
                    state.elapsedText + (state.remainingText?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("当前阶段：${state.stage.label}", style = MaterialTheme.typography.titleSmall)
                Text(state.stageText, style = MaterialTheme.typography.bodyMedium)
                if (state.detail.isNotBlank() && state.detail != state.stageText) Text(
                    state.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.mode == OperationProgressMode.FAILED) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.quietWarning?.let { warning ->
                    Text(
                        warning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (state.active) {
                    Text(
                        "百分比只代表当前阶段；下载完成后仍需解压、校验和安装。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.canCancel) OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("取消当前操作") }
                if (state.canRetry && state.retryCommand != null) Button(
                    onClick = {
                        onRetry(state.retryCommand)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(if (state.retryCommand == "OPEN_COMPONENTS") "返回组件入口重试" else "重试") }
            }
            if (transfers.isNotEmpty()) {
                if (runtime != null) HorizontalDivider()
                Text("文件传输 · ${transfers.size} 项", style = MaterialTheme.typography.titleMedium)
                transfers.forEach { item ->
                    key(item.id) {
                        TransferRow(item = item, now = now, onCancel = {
                            onCancelTransfer(item.id)
                        })
                    }
                }
                Text(
                    "文件进度按实际复制或下载的字节计算；总大小未知时使用不定进度。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
