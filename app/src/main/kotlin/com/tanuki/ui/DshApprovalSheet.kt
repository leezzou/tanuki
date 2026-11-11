package com.tanuki.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import com.tanuki.ui.design.TanukiDesign
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tanuki.web.DshApprovalOutcome
import com.tanuki.web.DshRememberedChoice
import com.tanuki.web.DshRiskLevel
import kotlinx.coroutines.delay

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
    val motion = TanukiDesign.motion
    val containerColor by animateColorAsState(
        if (entry.hasError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        animationSpec = motion.toggle(), label = "审批条目背景",
    )
    val contentColor by animateColorAsState(
        if (entry.hasError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        animationSpec = motion.toggle(), label = "审批条目文字",
    )
    Surface(
        color = containerColor,
        contentColor = contentColor,
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
            TanukiAnimatedCount(entry.label, minWidth = 112.dp,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
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
    val hasContent = state.shouldShowPanel()
    LaunchedEffect(visible, hasContent) {
        if (visible && !hasContent) onDismiss()
    }
    val scroll = rememberScrollState()
    val pendingNow = state.pendingFor(sessionId).size
    var arrival by remember { mutableStateOf(DshApprovalArrivalState(pendingNow)) }
    // 只负责“检测新增 + 更新基线”；不要在这里 delay，否则数量变化会取消隐藏计时。
    LaunchedEffect(visible, sessionId, pendingNow) {
        arrival = if (visible) DshApprovalArrivalPolicy.onCount(arrival, pendingNow)
            else DshApprovalArrivalState(pendingNow)
    }
    // 隐藏计时绑定 token：新增会重置 3 秒；数量减少不会取消已显示的提示。
    LaunchedEffect(arrival.token) {
        if (arrival.visible) {
            delay(3_000)
            arrival = DshApprovalArrivalPolicy.hidden(arrival)
        }
    }
    TanukiModalSheet(
        value = if (visible && hasContent) Triple(state, sessionId, sessionTitle) else null,
        onDismissRequest = onDismiss,
    ) { shown ->
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            // 固定预留槽：提示切换不参与滚动内容排版，审批卡和按钮位置不会上下跳。
            Box(Modifier.fillMaxWidth().height(40.dp)) {
                androidx.compose.animation.AnimatedVisibility(visible = arrival.visible,
                    enter = fadeIn(TanukiDesign.motion.toggle()),
                    exit = fadeOut(TanukiDesign.motion.toggle())) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            "有新的审批请求，计数已更新；当前处理项不会跳转。",
                            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
            Column(
                Modifier.fillMaxWidth().heightIn(max = 600.dp).verticalScroll(scroll)
            ) {
                DshApprovalPanel(
                    state = shown.first,
                    sessionId = shown.second,
                    onAnswer = { id, outcome, remember -> if (visible && hasContent) onAnswer(id, outcome, remember) },
                    onRetry = { if (visible && hasContent) onRetry() },
                    onClearRemembered = { if (visible && hasContent) onClearRemembered() },
                    sessionTitle = shown.third,
                    onForgetRemembered = onForgetRemembered?.let { action -> { if (visible && hasContent) action(it) } },
                    onDismissNotice = { if (visible && hasContent) onDismissNotice() },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                )
            }
        }
    }
}

/** 抽屉到达提示状态；token 变化表示需要重新计时。 */
data class DshApprovalArrivalState(
    val knownCount: Int,
    val visible: Boolean = false,
    val token: Int = 0
)

/** 到达提示的纯策略：数量增加才显示；数量减少不取消已显示的 3 秒计时。 */
object DshApprovalArrivalPolicy {
    fun onCount(state: DshApprovalArrivalState, currentCount: Int): DshApprovalArrivalState =
        if (currentCount > state.knownCount) {
            state.copy(knownCount = currentCount, visible = true, token = state.token + 1)
        } else {
            state.copy(knownCount = currentCount)
        }

    fun hidden(state: DshApprovalArrivalState): DshApprovalArrivalState = state.copy(visible = false)
}
