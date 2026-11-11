package com.tanuki.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tanuki.ui.design.rememberTanukiReducedMotion

/** 停靠条种类；声明顺序即叠放优先级（审批 > 目标 > 待办）。 */
enum class DshDockKind(val priority: Int, val label: String) {
    APPROVAL(0, "审批"),
    GOAL(1, "目标"),
    TODO(2, "待办")
}

/**
 * 停靠条折叠结果；纯数据，不读取协议、不持有 Compose 状态。
 *
 * 默认最多显示两条：按 [DshDockKind] 优先级取前两条，其余进入 [folded]。
 * 用户主动点开“+N”时才把折叠项临时铺开；此时 [visible] 包含全部可显示项，
 * 但 [folded] 仍保留折叠集合，便于按钮显示“+N / 收起”。
 */
data class DshDockLayout(
    val eligible: List<DshDockKind>,
    val visible: List<DshDockKind>,
    val folded: List<DshDockKind>,
    val overflowExpanded: Boolean = false
) {
    /** 被折叠的条数；即按钮上的 N。 */
    val overflowCount: Int get() = folded.size

    companion object {
        /** 同屏最多显示的停靠条数量。 */
        const val MAX_VISIBLE: Int = 2

        fun resolve(
            approvalVisible: Boolean,
            goalVisible: Boolean,
            todoVisible: Boolean,
            overflowExpanded: Boolean = false
        ): DshDockLayout {
            val eligible = buildList {
                if (approvalVisible) add(DshDockKind.APPROVAL)
                if (goalVisible) add(DshDockKind.GOAL)
                if (todoVisible) add(DshDockKind.TODO)
            }.sortedBy { it.priority }
            val folded = eligible.drop(MAX_VISIBLE)
            val expanded = overflowExpanded && folded.isNotEmpty()
            val visible = if (expanded) eligible else eligible.take(MAX_VISIBLE)
            return DshDockLayout(
                eligible = eligible,
                visible = visible,
                folded = folded,
                overflowExpanded = expanded
            )
        }
    }
}

/** 停靠条高度动画时长；固定落在 200–300ms，系统移除动画时为 0。 */
object DshDockMotion {
    const val MIN_HEIGHT_MS: Int = 200
    const val MAX_HEIGHT_MS: Int = 300

    /** 入场/退场共用同一时长，避免展开与收起速度不一致。 */
    const val HEIGHT_MS: Int = 240

    fun heightDurationMs(reducedMotion: Boolean): Int = if (reducedMotion) 0 else HEIGHT_MS
}

/**
 * 三条停靠条的叠放容器。
 *
 * 挂载位置由集成方决定：建议放在消息列表与输入栏之间。外层页面保持
 * “消息列表 weight(1f) + 本容器 + 输入栏”的纵向顺序时，消息列表顶部、
 * 标题和会话条保持固定；高度动画只改变停靠区与消息列表底边，不会把整页
 * 内容上下推走。
 *
 * 审批条不在这里重写：调用方把已有 `DshApprovalEntryBar` 作为 [approvalBar]
 * 插槽传入，本容器只负责顺序、折叠和高度动画。审批是否可显示必须由调用方
 * 通过 [approvalVisible] 明确告知（可用 `DshApprovalEntry.of(...).visible`）。
 */
@Composable
fun DshDockColumn(
    approvalVisible: Boolean,
    goal: GoalDockState?,
    todo: TodoDockState?,
    approvalBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    goalCapabilities: DshGoalCapabilities = DshGoalCapabilities(),
    onGoalAction: (DshGoalAction) -> Unit = {},
    onTodoExpandedChange: (Boolean) -> Unit = {}
) {
    var overflowExpanded by rememberSaveable { mutableStateOf(false) }
    val layout = DshDockLayout.resolve(
        approvalVisible = approvalVisible,
        goalVisible = goal?.isVisible == true,
        todoVisible = todo?.isVisible == true,
        overflowExpanded = overflowExpanded
    )
    val reducedMotion = rememberTanukiReducedMotion()
    val durationMs = DshDockMotion.heightDurationMs(reducedMotion)

    // 折叠项全部消失后自动复位，避免下次出现时仍停留在展开态。
    LaunchedEffect(layout.overflowCount) {
        if (layout.overflowCount == 0) overflowExpanded = false
    }

    Column(
        modifier
            .fillMaxWidth()
            // 兜底动画：任一条整条出现/消失时，容器高度仍按同一时长过渡。
            // 先裁剪再动画，收起过程中内容不会画到消息区或输入栏上。
            .clipToBounds()
            .animateContentSize(animationSpec = tween(durationMs)),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        layout.visible.forEach { kind ->
            key(kind) {
                DshDockAnimatedSlot(visible = true, durationMs = durationMs) {
                    DshDockBarContent(kind, approvalBar, goal, todo, goalCapabilities, onGoalAction, onTodoExpandedChange)
                }
            }
        }
        if (layout.overflowCount > 0) {
            DshDockOverflowButton(
                count = layout.overflowCount,
                expanded = layout.overflowExpanded,
                onClick = { overflowExpanded = !overflowExpanded }
            )
        }
        // 折叠项只在用户点开“+N”后出现；默认不参与同屏两条的版面。
        layout.folded.forEach { kind ->
            key(kind) {
                DshDockAnimatedSlot(visible = layout.overflowExpanded, durationMs = durationMs) {
                    DshDockBarContent(kind, approvalBar, goal, todo, goalCapabilities, onGoalAction, onTodoExpandedChange)
                }
            }
        }
    }
}

@Composable
private fun DshDockBarContent(
    kind: DshDockKind,
    approvalBar: @Composable () -> Unit,
    goal: GoalDockState?,
    todo: TodoDockState?,
    goalCapabilities: DshGoalCapabilities,
    onGoalAction: (DshGoalAction) -> Unit,
    onTodoExpandedChange: (Boolean) -> Unit
) {
    when (kind) {
        DshDockKind.APPROVAL -> approvalBar()
        DshDockKind.GOAL -> goal?.let {
            DshGoalEntryBar(state = it, capabilities = goalCapabilities, onAction = onGoalAction)
        }
        DshDockKind.TODO -> todo?.let {
            DshTodoEntryBar(state = it, onExpandedChange = onTodoExpandedChange)
        }
    }
}

/** 单条停靠条的高度入场/退场；内容始终裁剪在容器内，不盖住消息或输入栏。 */
@Composable
private fun DshDockAnimatedSlot(
    visible: Boolean,
    durationMs: Int,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(
            animationSpec = tween(durationMs),
            expandFrom = Alignment.Top
        ) + fadeIn(tween(durationMs)),
        exit = shrinkVertically(
            animationSpec = tween(durationMs),
            shrinkTowards = Alignment.Top
        ) + fadeOut(tween(durationMs))
    ) {
        Box(Modifier.fillMaxWidth()) { content() }
    }
}

/**
 * “+N”折叠入口；整个 48dp 行都可点击，视觉上只显示小胶囊。
 * 展开后按钮改为“收起”，但仍保留 N 的文字说明。
 */
@Composable
private fun DshDockOverflowButton(
    count: Int,
    expanded: Boolean,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = if (expanded) {
                    "收起其余 $count 条停靠条"
                } else {
                    "展开其余 $count 条停靠条"
                }
            },
        contentAlignment = Alignment.CenterEnd
    ) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = RoundedCornerShape(50)
        ) {
            Text(
                text = if (expanded) "收起" else "+$count",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
    }
}
