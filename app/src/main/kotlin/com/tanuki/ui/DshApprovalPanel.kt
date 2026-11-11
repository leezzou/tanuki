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
import com.tanuki.ui.design.TanukiButton as Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.tanuki.ui.design.TanukiOutlinedButton as OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.tanuki.web.DshApprovalPresentation
import com.tanuki.web.DshRememberedChoice
import com.tanuki.web.DshRiskLevel
import com.tanuki.web.DshToolArguments

/**
 * 原生审批面板。
 *
 * 只展示当前选中会话的待审批请求；其他会话的请求给出数量提示，避免静默丢失。
 * 允许/拒绝都回传官方 eventId；“记住本次运行”只影响同一会话、同一工具、同一风险等级，
 * 不写 DSH 配置、不跨重启。批量请求逐条编号，高风险允许需要二次确认，降低误点风险。
 */
@Composable
fun DshApprovalPanel(
    state: DshApprovalState,
    sessionId: String?,
    onAnswer: (eventId: String, outcome: DshApprovalOutcome, remember: Boolean) -> Unit,
    onRetry: () -> Unit,
    onClearRemembered: () -> Unit,
    sessionTitle: String? = null,
    onForgetRemembered: ((DshRememberedChoice) -> Unit)? = null,
    onDismissNotice: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val pending = state.pendingFor(sessionId)
    val others = state.otherPendingCount(sessionId)
    if (!state.shouldShowPanel()) return
    val sessionLabel = sessionTitle?.takeIf { it.isNotBlank() }
        ?: sessionId?.let { "会话 ${it.take(8)}" }
        ?: "当前会话"
    Column(
        modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("审批请求", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (pending.size > 0) {
                Spacer(Modifier.width(8.dp))
                StatusPill("${pending.size} 条待处理", true)
            }
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
                        if (state.pending.isNotEmpty()) {
                            Text(
                                "待审批项仍保留；恢复连接后可继续回答。",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    TextButton(onClick = onRetry, enabled = !state.connecting) { Text("重试连接") }
                }
            }
        }
        state.notice?.let { notice ->
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        notice,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = onDismissNotice) { Text("知道了") }
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
            RememberedChoices(state.remembered, onForgetRemembered, onClearRemembered)
        }
        if (pending.size > 1) {
            Text(
                "待审批共 ${pending.size} 条；每张卡片独立回答，允许/拒绝只作用于本卡片。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        pending.forEachIndexed { index, item ->
            key(item.request.eventId) {
                ApprovalCard(
                    item = item,
                    ready = state.ready,
                    ordinal = index + 1,
                    total = pending.size,
                    sessionLabel = sessionLabel,
                    onAnswer = onAnswer
                )
            }
        }
        if (state.connecting) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(
                    "正在连接官方审批通道…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 已记住的选择列表：可逐条查看并撤销。
 *
 * 撤销只影响之后到达的相同会话/工具/风险请求；已经回传官方的结果不会伪造撤回。
 */
@Composable
private fun RememberedChoices(
    choices: List<DshRememberedChoice>,
    onForget: ((DshRememberedChoice) -> Unit)?,
    onClear: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "已记住 ${choices.size} 项选择",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "仅本次原生运行有效；撤销后相同审批不会自动回答。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onClear) { Text("全部清除") }
            }
            choices.forEach { choice ->
                key(choice.id) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "${choice.toolName} · ${choice.outcomeLabel}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "风险 ${choice.risk.label} · 会话 ${choice.sessionId.take(8)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (DshApprovalActions.canForget(onForget)) {
                            TextButton(onClick = { onForget?.invoke(choice) }) { Text("撤销") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ApprovalCard(
    item: DshPendingApproval,
    ready: Boolean,
    ordinal: Int,
    total: Int,
    sessionLabel: String,
    onAnswer: (String, DshApprovalOutcome, Boolean) -> Unit
) {
    var remember by rememberSaveable(item.request.eventId) { mutableStateOf(false) }
    var argumentsExpanded by rememberSaveable(item.request.eventId) { mutableStateOf(false) }
    var confirmHighRisk by rememberSaveable(item.request.eventId) { mutableStateOf(false) }
    val highRisk = item.risk.level == DshRiskLevel.HIGH
    val reason = item.request.displayReason ?: item.request.reason
    val source = item.request.source?.takeIf { it.isNotBlank() }
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
                Column(Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
                    Text(
                        buildString {
                            if (total > 1) {
                                append("第 ")
                                append(ordinal)
                                append("/")
                                append(total)
                                append(" 条 · ")
                            }
                            append(
                                when {
                                    item.answering -> "正在回传官方审批…"
                                    item.automatic -> "按记住的选择自动回答"
                                    else -> "等待审批"
                                }
                            )
                        },
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        "来源：${source ?: "官方工具调用"} · $sessionLabel",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Column(
                Modifier.padding(start = 14.dp, end = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        item.request.toolName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    DshRiskPill(item.risk.level, item.risk.summary)
                }
                Text(
                    "风险等级：${item.risk.level.label} · ${item.risk.summary}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (highRisk) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    reason ?: "工具 ${item.request.toolName} 请求权限；请确认是否允许。",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (item.request.unknownTool) {
                    Text(
                        "官方未提供可识别的工具类型；只能拒绝，不能按记忆自动允许。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (item.lookupPending) {
                    Text(
                        "正在按该审批所属会话读取工具参数与风险…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item.lookupError?.let { lookupError ->
                    Text(
                        lookupError,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DshApprovalPresentation.summarize(item.arguments)?.let { summary ->
                    Text(
                        "参数摘要（${summary.field}）：${summary.headline}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val arguments = DshToolArguments.pretty(item.arguments)
                if (arguments != null) {
                    val collapsed = !argumentsExpanded && arguments.length > MAX_COLLAPSED_ARGUMENTS
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "完整参数",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                    Text(
                        "官方请求未附带参数；可在对应工具卡片中查看调用详情。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = remember,
                        onCheckedChange = { remember = it },
                        enabled = ready && !item.answering && !item.lookupPending && !item.request.unknownTool
                    )
                    Text(
                        "本次运行内记住此工具与风险等级",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                if (remember && highRisk) {
                    Text(
                        "高风险工具被记住后，相同会话/工具/风险等级会自动允许；请确认这是有意选择。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (highRisk && confirmHighRisk) {
                    Text(
                        "这是高风险操作。再次点击“确认允许高风险”才会回传官方；点“拒绝”不会执行。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                item.error?.let { error ->
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (item.automatic) {
                    Text(
                        "已按记住的选择自动回答；若失败会保留此卡片并显示错误。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                    enabled = ready && !item.answering && !item.lookupPending && !item.request.unknownTool,
                    onClick = {
                        if (highRisk && !confirmHighRisk) {
                            confirmHighRisk = true
                        } else {
                            onAnswer(item.request.eventId, DshApprovalOutcome.ALLOWED_ONCE, remember)
                        }
                    }
                ) {
                    Text(
                        when {
                            highRisk && !confirmHighRisk -> "允许一次"
                            highRisk -> "确认允许高风险"
                            else -> "允许一次"
                        }
                    )
                }
            }
        }
    }
}

/** 审批组件的接线安全策略：未提供单条撤销回调时，界面不得显示可点击但无效的“撤销”。 */
object DshApprovalActions {
    fun canForget(onForget: ((DshRememberedChoice) -> Unit)?): Boolean = onForget != null
}

private const val MAX_COLLAPSED_ARGUMENTS = 1_200
