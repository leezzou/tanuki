package com.tanuki.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import com.tanuki.ui.design.TanukiOutlinedButton as OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tanuki.ui.design.rememberTanukiReducedMotion

/** 目标状态；文案与官方语义一一对应，不在组件里解析事件。 */
enum class GoalDockStatus(val label: String) {
    RUNNING("进行中"),
    PAUSED("已暂停"),
    BLOCKED("受阻")
}

/**
 * 目标条状态；调用方从自己的目标数据源填充，组件不直接依赖协议解析。
 *
 * [summary] 是单行摘要的原始文本，UI 负责省略号；[detail] 可放官方返回的
 * 短状态说明。两个字段都不应包含凭据、完整对话或原始 RPC 报文。
 */
data class GoalDockState(
    val summary: String = "",
    val status: GoalDockStatus = GoalDockStatus.RUNNING,
    val detail: String? = null
) {
    /** 空摘要不占位；目标条整条隐藏。 */
    val isVisible: Boolean get() = summary.isNotBlank()

    /** 压成一行，避免摘要里的换行把停靠条撑高。 */
    val oneLineSummary: String
        get() = summary.trim().replace(WHITESPACE, " ")

    val statusLabel: String get() = status.label

    /** 受阻时使用警示色。 */
    val blocked: Boolean get() = status == GoalDockStatus.BLOCKED

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}

/** 目标操作；只表达用户意图，具体 RPC 由集成方接线。 */
enum class DshGoalAction(val label: String) {
    PAUSE("暂停"),
    RESUME("恢复"),
    EDIT("编辑"),
    CLEAR("清除")
}

/**
 * 目标操作能力。
 *
 * 官方协议存在目标读取/暂停/恢复/编辑/清除通道，但原生页是否已接线由
 * 集成方决定。默认全部关闭并给出统一原因：组件不猜测能力，也不伪造成功。
 */
data class DshGoalCapabilities(
    val canPause: Boolean = false,
    val canResume: Boolean = false,
    val canEdit: Boolean = false,
    val canClear: Boolean = false,
    val unavailableReason: String = "本会话尚未接入官方目标操作通道"
) {
    val anyAvailable: Boolean get() = canPause || canResume || canEdit || canClear
}

/** 单个操作的可用性与禁用原因；[reason] 只在禁用时非空。 */
data class DshGoalActionAvailability(
    val action: DshGoalAction,
    val enabled: Boolean,
    val reason: String? = null
)

/** 目标操作可用性的纯策略：同时看接线能力和当前状态。 */
object DshGoalActions {
    fun availability(
        state: GoalDockState,
        capabilities: DshGoalCapabilities
    ): List<DshGoalActionAvailability> = listOf(
        pause(state, capabilities),
        resume(state, capabilities),
        edit(capabilities),
        clear(capabilities)
    )

    private fun pause(
        state: GoalDockState,
        capabilities: DshGoalCapabilities
    ): DshGoalActionAvailability = when {
        !capabilities.canPause -> disabled(DshGoalAction.PAUSE, capabilities.unavailableReason)
        state.status == GoalDockStatus.PAUSED -> disabled(DshGoalAction.PAUSE, "目标已暂停，无需再次暂停")
        state.status == GoalDockStatus.BLOCKED -> disabled(DshGoalAction.PAUSE, "目标已受阻，请先恢复或编辑目标")
        else -> DshGoalActionAvailability(DshGoalAction.PAUSE, enabled = true)
    }

    private fun resume(
        state: GoalDockState,
        capabilities: DshGoalCapabilities
    ): DshGoalActionAvailability = when {
        !capabilities.canResume -> disabled(DshGoalAction.RESUME, capabilities.unavailableReason)
        state.status == GoalDockStatus.RUNNING -> disabled(DshGoalAction.RESUME, "目标正在进行中，无需恢复")
        state.status == GoalDockStatus.BLOCKED -> disabled(DshGoalAction.RESUME, "目标受阻，恢复前请先编辑目标")
        else -> DshGoalActionAvailability(DshGoalAction.RESUME, enabled = true)
    }

    private fun edit(capabilities: DshGoalCapabilities): DshGoalActionAvailability =
        if (capabilities.canEdit) {
            DshGoalActionAvailability(DshGoalAction.EDIT, enabled = true)
        } else {
            disabled(DshGoalAction.EDIT, capabilities.unavailableReason)
        }

    private fun clear(capabilities: DshGoalCapabilities): DshGoalActionAvailability =
        if (capabilities.canClear) {
            DshGoalActionAvailability(DshGoalAction.CLEAR, enabled = true)
        } else {
            disabled(DshGoalAction.CLEAR, capabilities.unavailableReason)
        }

    private fun disabled(action: DshGoalAction, reason: String) =
        DshGoalActionAvailability(action, enabled = false, reason = reason)
}

/**
 * 输入框上方的目标条。
 *
 * 收起时只显示“⚑ 目标 <一行摘要> + 状态”；点击在原地展开操作面板。
 * 面板不悬浮、不遮住消息，展开高度受父级 Column 约束。操作按钮在未接线
 * 或状态不匹配时禁用，并在面板内逐组说明原因，不提供“看起来能点但无效”的按钮。
 */
@Composable
fun DshGoalEntryBar(
    state: GoalDockState,
    modifier: Modifier = Modifier,
    capabilities: DshGoalCapabilities = DshGoalCapabilities(),
    onAction: (DshGoalAction) -> Unit = {},
    initiallyExpanded: Boolean = false
) {
    if (!state.isVisible) return
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val reducedMotion = rememberTanukiReducedMotion()
    val durationMs = DshDockMotion.heightDurationMs(reducedMotion)
    val availabilities = DshGoalActions.availability(state, capabilities)

    val containerColor = if (state.blocked) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (state.blocked) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .semantics {
                contentDescription = if (expanded) {
                    "目标：${state.oneLineSummary}；状态：${state.statusLabel}；点击收起操作面板"
                } else {
                    "目标：${state.oneLineSummary}；状态：${state.statusLabel}；点击展开操作面板"
                }
            }
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "⚑ 目标",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    state.oneLineSummary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                GoalStatusChip(state)
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = tween(durationMs),
                    expandFrom = Alignment.Top
                ) + fadeIn(tween(durationMs)),
                exit = shrinkVertically(
                    animationSpec = tween(durationMs),
                    shrinkTowards = Alignment.Top
                ) + fadeOut(tween(durationMs))
            ) {
                Column(
                    Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        state.detail?.takeIf { it.isNotBlank() } ?: state.statusHint(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GoalActionButton(availabilities.first { it.action == DshGoalAction.PAUSE }, onAction)
                        GoalActionButton(availabilities.first { it.action == DshGoalAction.RESUME }, onAction)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GoalActionButton(availabilities.first { it.action == DshGoalAction.EDIT }, onAction)
                        GoalActionButton(availabilities.first { it.action == DshGoalAction.CLEAR }, onAction)
                    }
                    // 按原因归并说明，避免四个按钮重复同一句话。
                    availabilities.filterNot { it.enabled }
                        .groupBy { it.reason.orEmpty() }
                        .forEach { (reason, actions) ->
                            Text(
                                "${actions.joinToString("/") { it.action.label }}不可用：$reason",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                }
            }
        }
    }
}

private fun GoalDockState.statusHint(): String = when (status) {
    GoalDockStatus.RUNNING -> "目标正在进行中。"
    GoalDockStatus.PAUSED -> "目标已暂停，恢复后继续执行。"
    GoalDockStatus.BLOCKED -> "目标受阻，请先处理后继续。"
}

@Composable
private fun GoalStatusChip(state: GoalDockState) {
    val color = when (state.status) {
        GoalDockStatus.RUNNING -> MaterialTheme.colorScheme.primaryContainer
        GoalDockStatus.PAUSED -> MaterialTheme.colorScheme.surfaceContainerHighest
        GoalDockStatus.BLOCKED -> MaterialTheme.colorScheme.error.copy(alpha = 0.16f)
    }
    val content = when (state.status) {
        GoalDockStatus.RUNNING -> MaterialTheme.colorScheme.onPrimaryContainer
        GoalDockStatus.PAUSED -> MaterialTheme.colorScheme.onSurfaceVariant
        GoalDockStatus.BLOCKED -> MaterialTheme.colorScheme.error
    }
    Surface(color = color, contentColor = content, shape = RoundedCornerShape(50)) {
        Text(
            state.statusLabel,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (state.blocked) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun GoalActionButton(
    availability: DshGoalActionAvailability,
    onAction: (DshGoalAction) -> Unit
) {
    val buttonModifier = if (availability.enabled) {
        Modifier
    } else {
        Modifier.semantics {
            contentDescription = "${availability.action.label}不可用：${availability.reason.orEmpty()}"
        }
    }
    OutlinedButton(
        onClick = { onAction(availability.action) },
        enabled = availability.enabled,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        modifier = buttonModifier
    ) {
        Text(availability.action.label, style = MaterialTheme.typography.labelMedium)
    }
}
