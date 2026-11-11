package com.tanuki.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tanuki.ui.design.rememberTanukiReducedMotion
import kotlin.math.roundToInt

/** 单条待办的状态；只保留展示所需的三态。 */
enum class TodoDockItemStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED
}

/** 待办条目；[id] 用于列表键，重复时组件回退到下标。 */
data class TodoDockItem(
    val id: String,
    val text: String,
    val status: TodoDockItemStatus = TodoDockItemStatus.PENDING
) {
    /** 压成一行，避免清单行被内部换行撑高。 */
    val oneLineText: String
        get() = text.trim().replace(WHITESPACE, " ")

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}

/**
 * 待办条状态；纯数据，不解析官方待办事件。
 *
 * 进度只由“已完成条数 / 总条数”计算，不把“进行中”折算成半个完成项，
 * 也不伪造未知总量百分比。清单为空时整条隐藏。
 */
data class TodoDockState(
    val items: List<TodoDockItem> = emptyList()
) {
    val isVisible: Boolean get() = items.isNotEmpty()
    val total: Int get() = items.size
    val completed: Int get() = items.count { it.status == TodoDockItemStatus.COMPLETED }

    /** 当前项：优先进行中，其次第一条未完成；全部完成时为 null。 */
    val current: TodoDockItem?
        get() = activeIndex.takeIf { it >= 0 }?.let(items::get)

    /** 进行中或第一条未完成的下标；全部完成时为 -1。 */
    val activeIndex: Int
        get() {
            val running = items.indexOfFirst { it.status == TodoDockItemStatus.IN_PROGRESS }
            if (running >= 0) return running
            return items.indexOfFirst { it.status == TodoDockItemStatus.PENDING }
        }

    /** 展开时滚动的目标下标：当前项；全部完成时定位到最后一条。 */
    val scrollIndex: Int
        get() = activeIndex.takeIf { it >= 0 } ?: items.lastIndex

    /** 0..1；空清单为 0，不产生除零。 */
    val progress: Float
        get() = if (total == 0) 0f else completed.toFloat() / total.toFloat()

    /** 展示用整数百分比，向下取整；空清单为 0。 */
    val progressPercent: Int
        get() = if (total == 0) 0 else completed * 100 / total

    /** 形如“☑ 3/7 · 正在：写回归”；空清单为空串。 */
    val summary: String
        get() {
            if (items.isEmpty()) return ""
            val prefix = "☑ $completed/$total"
            val current = current ?: return "$prefix · 已全部完成"
            val label = if (current.status == TodoDockItemStatus.IN_PROGRESS) "正在" else "下一步"
            return "$prefix · $label：${current.oneLineText}"
        }
}

/** 待办清单展开高度与滚动定位的纯策略。 */
object TodoDockLayout {
    /** 展开清单最多占窗口高度的 30%。 */
    const val MAX_SCREEN_PERCENT: Int = 30

    fun maxExpandedHeightDp(screenHeightDp: Int): Int {
        if (screenHeightDp <= 0) return 0
        return (screenHeightDp.toLong() * MAX_SCREEN_PERCENT / 100L).toInt().coerceAtLeast(1)
    }
}

/** 展开时的一次滚动请求；[index] 已限制在清单范围内。 */
data class TodoDockScrollRequest(val index: Int)

object TodoDockScrollPolicy {
    /**
     * 只在展开且清单非空时返回请求。
     *
     * 收起、空清单返回 null；全部完成时定位到最后一条，避免滚到不存在的“当前项”。
     */
    fun request(state: TodoDockState, expanded: Boolean): TodoDockScrollRequest? {
        if (!expanded || state.items.isEmpty()) return null
        return TodoDockScrollRequest(state.scrollIndex.coerceIn(0, state.items.lastIndex))
    }
}

/**
 * 输入框上方的待办条。
 *
 * 收起时显示“☑ 3/7 · 正在：当前项”和细进度条；点击原地展开清单，
 * 清单被限制在窗口高度的 30% 内并在卡片内滚动，展开时自动定位当前项。
 * 完成项淡化并加删除线，进行中项加粗。空清单不渲染任何占位高度。
 */
@Composable
fun DshTodoEntryBar(
    state: TodoDockState,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    onExpandedChange: (Boolean) -> Unit = {}
) {
    if (!state.isVisible) return
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val reducedMotion = rememberTanukiReducedMotion()
    val durationMs = DshDockMotion.heightDurationMs(reducedMotion)
    val listState = rememberLazyListState()
    val scrollRequest = TodoDockScrollPolicy.request(state, expanded)

    // 展开时定位当前项；收起或空清单不滚动。
    LaunchedEffect(expanded, scrollRequest?.index) {
        val request = scrollRequest ?: return@LaunchedEffect
        listState.animateScrollToItem(request.index)
    }

    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val screenHeightDp = with(density) { windowInfo.containerSize.height.toDp().value }.roundToInt()
    val maxExpandedHeight = TodoDockLayout.maxExpandedHeightDp(screenHeightDp)
        .takeIf { it > 0 }?.dp ?: 240.dp

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        expanded = !expanded
                        onExpandedChange(expanded)
                    }
                    .semantics {
                        contentDescription = if (expanded) {
                            "待办 ${state.completed}/${state.total}，点击收起清单"
                        } else {
                            "待办 ${state.completed}/${state.total}，点击展开清单"
                        }
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "☑ ${state.completed}/${state.total}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(" · ", style = MaterialTheme.typography.labelLarge)
                Text(
                    state.currentHeadline(),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (expanded) "收起" else "展开",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(6.dp))
            TodoProgressBar(state)
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
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxExpandedHeight),
                    contentPadding = PaddingValues(top = 6.dp)
                ) {
                    itemsIndexed(state.items) { index, item ->
                        TodoDockRow(
                            item = item,
                            current = index == state.activeIndex
                        )
                    }
                }
            }
        }
    }
}

/** 细进度条；只使用真实完成条数，不把进行中折算成小数进度。 */
@Composable
private fun TodoProgressBar(state: TodoDockState) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics { contentDescription = "待办进度 ${state.completed}/${state.total}" }
    ) {
        Box(
            Modifier
                .fillMaxWidth(state.progress)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
private fun TodoDockRow(item: TodoDockItem, current: Boolean) {
    val completed = item.status == TodoDockItemStatus.COMPLETED
    val running = item.status == TodoDockItemStatus.IN_PROGRESS
    val rowColor = if (current) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0f)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(rowColor)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            when {
                completed -> "☑"
                running -> "◐"
                else -> "☐"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(6.dp))
        Text(
            item.oneLineText.ifBlank { "（未命名待办）" },
            style = MaterialTheme.typography.bodySmall,
            color = if (completed) {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            fontWeight = if (running) FontWeight.SemiBold else FontWeight.Normal,
            textDecoration = if (completed) TextDecoration.LineThrough else null
        )
    }
}

private fun TodoDockState.currentHeadline(): String {
    val current = current ?: return "已全部完成"
    val label = if (current.status == TodoDockItemStatus.IN_PROGRESS) "正在" else "下一步"
    return "$label：${current.oneLineText.ifBlank { "（未命名待办）" }}"
}
