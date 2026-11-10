package com.tanuki.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tanuki.web.DshApprovalOutcome
import com.tanuki.web.DshRememberedChoice
import com.tanuki.web.DshRiskLevel

/**
 * 审批入口摘要；只在当前会话有待审批、记忆、错误或提示时显示窄条。
 *
 * 这里不展示审批卡片本身，避免把待审批项散落在消息流里；实际处理在底部抽屉。
 */
data class DshApprovalEntry(
    val visible: Boolean,
    val pendingCount: Int,
    val highRiskCount: Int,
    val rememberedCount: Int,
    val hasError: Boolean,
    val hasNotice: Boolean
) {
    val label: String
        get() = when {
            pendingCount > 0 -> "⚑ 待审批 $pendingCount 项"
            hasError -> "审批通道异常"
            rememberedCount > 0 -> "审批记忆 $rememberedCount 项"
            hasNotice -> "审批提示"
            else -> ""
        }

    companion object {
        fun of(state: DshApprovalState, sessionId: String?): DshApprovalEntry {
            val pending = state.pendingFor(sessionId)
            return DshApprovalEntry(
                visible = pending.isNotEmpty() || state.remembered.isNotEmpty() ||
                    state.error != null || state.notice != null,
                pendingCount = pending.size,
                highRiskCount = pending.count { it.risk.level == DshRiskLevel.HIGH },
                rememberedCount = state.remembered.size,
                hasError = state.error != null,
                hasNotice = state.notice != null
            )
        }
    }
}

/**
 * 输入框上方的审批窄条。
 *
 * 点击后由页面打开 [DshApprovalSheet]；批量请求不提供“一键全允许”，必须在抽屉内逐条处理。
 */
@Composable
fun DshApprovalEntryBar(
    state: DshApprovalState,
    sessionId: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val entry = DshApprovalEntry.of(state, sessionId)
    if (!entry.visible) return
    Surface(
        color = if (entry.hasError) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        contentColor = if (entry.hasError) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        },
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { contentDescription = "打开审批：${entry.label}" }
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(entry.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            if (entry.highRiskCount > 0) {
                Text(
                    "高风险 ${entry.highRiskCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.weight(1f))
            Text("逐条处理", style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.width(4.dp))
            Text("打开", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * 审批底部抽屉：复用 [DshApprovalPanel] 的逐条卡片、记忆和错误重试。
 *
 * 页面只负责维护 `visible`；关闭 / 没有可展示内容时回调 [onDismiss]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DshApprovalSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    state: DshApprovalState,
    sessionId: String?,
    onAnswer: (String, DshApprovalOutcome, Boolean) -> Unit,
    onRetry: () -> Unit,
    onClearRemembered: () -> Unit,
    sessionTitle: String? = null,
    onForgetRemembered: ((DshRememberedChoice) -> Unit)? = null,
    onDismissNotice: () -> Unit = {}
) {
    if (!visible) return
    val hasContent = state.shouldShowPanel()
    LaunchedEffect(hasContent) {
        if (!hasContent) onDismiss()
    }
    if (!hasContent) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            DshApprovalPanel(
                state = state,
                sessionId = sessionId,
                onAnswer = onAnswer,
                onRetry = onRetry,
                onClearRemembered = onClearRemembered,
                sessionTitle = sessionTitle,
                onForgetRemembered = onForgetRemembered,
                onDismissNotice = onDismissNotice,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            )
        }
    }
}
