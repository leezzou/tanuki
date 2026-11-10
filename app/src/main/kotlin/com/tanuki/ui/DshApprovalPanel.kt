package com.tanuki.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tanuki.web.DshApprovalOutcome
import com.tanuki.web.DshToolArguments

/**
 * 原生审批面板。
 *
 * 只展示当前选中会话的待审批请求；其他会话的请求给出数量提示，避免静默丢失。
 * 允许/拒绝都回传官方 eventId；“记住本次运行”只影响同一会话、同一工具、同一风险等级，
 * 不写 DSH 配置、不跨重启。
 */
@Composable
fun DshApprovalPanel(
    state: DshApprovalState,
    sessionId: String?,
    onAnswer: (eventId: String, outcome: DshApprovalOutcome, remember: Boolean) -> Unit,
    onRetry: () -> Unit,
    onClearRemembered: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pending = state.pendingFor(sessionId)
    val others = state.otherPendingCount(sessionId)
    if (pending.isEmpty() && others == 0 && state.error == null && !state.connecting) return
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("审批请求", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            StatusPill(
                when {
                    state.ready -> "审批通道已连接"
                    state.connecting -> "审批通道连接中"
                    else -> "审批通道未连接"
                },
                state.ready
            )
            Spacer(Modifier.weight(1f))
            if (state.remembered.isNotEmpty()) {
                TextButton(onClick = onClearRemembered) { Text("清除记住的选择") }
            }
        }
        state.error?.let { message ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("审批通道异常", fontWeight = FontWeight.SemiBold)
                        SelectionContainer { Text(message, style = MaterialTheme.typography.bodySmall) }
                    }
                    TextButton(onClick = onRetry) { Text("重试") }
                }
            }
        }
        if (others > 0) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "其他会话还有 $others 个待审批；切换会话后可处理。",
                    Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        if (state.remembered.isNotEmpty()) {
            Text(
                "本次运行已记住 ${state.remembered.size} 项选择；关闭原生页或停止服务后清空。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        pending.forEach { item -> ApprovalCard(item, state.ready, onAnswer) }
        if (state.connecting) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text("正在连接官方审批通道…", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ApprovalCard(
    item: DshPendingApproval,
    ready: Boolean,
    onAnswer: (String, DshApprovalOutcome, Boolean) -> Unit
) {
    var remember by rememberSaveable(item.request.eventId) { mutableStateOf(false) }
    var argumentsExpanded by rememberSaveable(item.request.eventId) { mutableStateOf(false) }
    val reason = item.request.displayReason ?: item.request.reason
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    when {
                        item.answering -> "正在回传官方审批…"
                        item.automatic -> "按记住的选择自动回答"
                        else -> "等待审批"
                    },
                    Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Column(Modifier.padding(start = 14.dp, end = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(item.request.toolName, style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    DshRiskPill(item.risk.level, item.risk.summary)
                }
                Text(
                    reason ?: "工具 ${item.request.toolName} 请求权限；请确认是否允许。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(item.risk.summary, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                val arguments = DshToolArguments.pretty(item.arguments)
                if (arguments != null) {
                    val collapsed = !argumentsExpanded && arguments.length > MAX_COLLAPSED_ARGUMENTS
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("参数", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            SelectionContainer {
                                Text(
                                    if (collapsed) arguments.take(MAX_COLLAPSED_ARGUMENTS) + "…" else arguments,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            if (arguments.length > MAX_COLLAPSED_ARGUMENTS) {
                                TextButton(onClick = { argumentsExpanded = !argumentsExpanded }) {
                                    Text(if (argumentsExpanded) "收起参数" else "展开完整参数")
                                }
                            }
                        }
                    }
                } else {
                    Text("官方请求未附带参数；可在对应工具卡片中查看调用详情。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = remember, onCheckedChange = { remember = it },
                        enabled = ready && !item.answering)
                    Text("本次运行内记住此工具与风险等级", style = MaterialTheme.typography.labelSmall)
                }
                item.error?.let { error ->
                    Text(error, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
                if (item.automatic) {
                    Text("已按记住的选择自动回答；若失败会保留此卡片并显示错误。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    enabled = ready && !item.answering,
                    onClick = { onAnswer(item.request.eventId, DshApprovalOutcome.REJECTED, remember) }
                ) { Text("拒绝") }
                Button(
                    enabled = ready && !item.answering,
                    onClick = { onAnswer(item.request.eventId, DshApprovalOutcome.ALLOWED_ONCE, remember) }
                ) { Text("允许一次") }
            }
        }
    }
}

private const val MAX_COLLAPSED_ARGUMENTS = 1_200
