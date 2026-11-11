package com.tanuki.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
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
 *
 * 渲染必须只遍历一次 [visible]（见 [DshDockRenderPlan]），不能再用 [folded]
 * 单独渲染一遍，否则展开时同一停靠条会出现两个实例。
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

/**
 * 停靠条实际渲染计划；组件只遍历 [bars] 一次，每个种类至多出现一次。
 *
 * 展开“+N”时 [DshDockLayout.visible] 已经包含全部可显示条，因此不能再遍历
 * [DshDockLayout.folded] 渲染第二遍。本计划就是组件的唯一渲染来源，
 * JVM 回归可直接断言同一停靠条不会出现两份。
 */
data class DshDockRenderPlan(
    val bars: List<DshDockKind>,
    val overflowCount: Int,
    val overflowExpanded: Boolean
) {
    /** “+N/收起”按钮应插在最后一条可见条之后。 */
    val overflowAfter: DshDockKind? get() = bars.lastOrNull()

    companion object {
        fun of(layout: DshDockLayout): DshDockRenderPlan = DshDockRenderPlan(
            bars = layout.visible.distinct(),
            overflowCount = layout.overflowCount,
            overflowExpanded = layout.overflowExpanded
        )
    }
}

/**
 * 页面底部纵向布局契约。
 *
 * 页面必须保持“消息区 `weight(1f)` → [DshDockColumn] → 输入栏”的顺序，并且不要把
 * 停靠容器再包进 `animateContentSize`/`AnimatedVisibility`。这样停靠条高度变化只会
 * 压缩消息区，输入栏顶部位置始终由窗口高度与自身高度决定，不会被停靠条推动或跳变。
 */
object DshDockPlacement {
    /** 输入栏顶部位置（px）：与停靠条高度无关。 */
    fun composerTopPx(totalPx: Int, composerPx: Int): Int =
        (totalPx - composerPx).coerceAtLeast(0)

    /** 消息区高度（px）：总高扣除固定头部、停靠条和输入栏；停靠条变高只缩小这里。 */
    fun messageViewportPx(totalPx: Int, fixedTopPx: Int, dockPx: Int, composerPx: Int): Int =
        (totalPx - fixedTopPx - dockPx - composerPx).coerceAtLeast(0)
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

    val plan = DshDockRenderPlan.of(layout)

    Column(
        modifier
            .fillMaxWidth()
            // 兜底动画：任一条整条出现/消失时，容器高度仍按同一时长过渡。
            // 先裁剪再动画，收起过程中内容不会画到消息区或输入栏上。
            .clipToBounds()
            .animateContentSize(animationSpec = tween(durationMs))
    ) {
        // 每个种类只在这里遍历一次；展开态已把折叠项并入 plan.bars。
        // 隐藏的槽位保留在组合中，以便“+N”展开时仍能播放高度动画。
        DshDockKind.entries.forEach { kind ->
            val barIndex = plan.bars.indexOf(kind)
            key(kind) {
                DshDockAnimatedSlot(
                    visible = barIndex >= 0,
                    durationMs = durationMs,
                    // 间距放在可见条内部；隐藏槽位保持 0 高度，不额外占用版面。
                    hasTopGap = barIndex > 0
                ) {
                    DshDockBarContent(kind, approvalBar, goal, todo, goalCapabilities, onGoalAction, onTodoExpandedChange)
                }
            }
            if (kind == plan.overflowAfter && plan.overflowCount > 0) {
                Box(Modifier.fillMaxWidth().padding(top = if (plan.bars.isNotEmpty()) 4.dp else 0.dp)) {
                    DshDockOverflowButton(
                        count = plan.overflowCount,
                        expanded = plan.overflowExpanded,
                        onClick = { overflowExpanded = !overflowExpanded }
                    )
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
    hasTopGap: Boolean = false,
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
        Box(Modifier.fillMaxWidth().padding(top = if (hasTopGap) 4.dp else 0.dp)) { content() }
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
