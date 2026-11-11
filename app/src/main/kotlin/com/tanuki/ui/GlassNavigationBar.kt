package com.tanuki.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import kotlin.math.roundToInt

/** 液态镜片只采样原生底图和导航自身；拖动预览与页面提交分离，避免途经页签时重建网页。 */
@Composable
internal fun GlassNavigationBar(
    selectedIndex: Int,
    labels: List<String>,
    onSelect: (Int) -> Unit,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    blurRadius: Dp = 16.dp,
    translucentBackground: Boolean = false,
    centerAction: (@Composable () -> Unit)? = null,
    icon: @Composable (Int, Color) -> Unit,
) {
    if (labels.isEmpty()) return
    val tokens = com.tanuki.ui.design.TanukiDesign.tokens.colors
    val colors = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    val direction = LocalLayoutDirection.current
    val density = LocalDensity.current
    val select by rememberUpdatedState(onSelect)
    val hasCenterAction = centerAction != null
    fun slotForIndex(index: Int) = if (hasCenterAction && index >= 2) index + 1 else index
    val sources = remember(labels.size) { List(labels.size) { MutableInteractionSource() } }
    val pressed = sources.map { it.collectIsPressedAsState().value }.getOrNull(selectedIndex) == true
    var dragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }
    var lastSelectedSlot by remember { mutableIntStateOf(slotForIndex(selectedIndex.coerceIn(labels.indices))) }
    val selected = selectedIndex in labels.indices
    val position by animateFloatAsState(
        if (dragging) dragPosition else if (selected) slotForIndex(selectedIndex).toFloat() else lastSelectedSlot.toFloat(),
        if (dragging) snap() else spring(dampingRatio = 0.8f, stiffness = 480f),
        label = "液态导航位置",
    )
    val pressure by animateFloatAsState(
        if (dragging || pressed) 1f else 0f,
        spring(dampingRatio = 0.8f, stiffness = 500f), label = "液态导航形变",
    )
    val visibility by animateFloatAsState(
        if (selected || dragging) 1f else 0f, label = "液态导航可见度",
    )
    LaunchedEffect(selectedIndex, labels.size, direction, hasCenterAction) {
        dragging = false
        if (selected) lastSelectedSlot = slotForIndex(selectedIndex)
    }
    val tabsBackdrop = rememberLayerBackdrop()
    // 网页靠原生叠放透显，不进入录制层；背板保留足够底色以保证明暗网页上的文字对比度。
    val fallback = rememberCanvasBackdrop { drawRect(colors.surface.copy(alpha = if (translucentBackground) 0.72f else 1f)) }
    val panelShape = RoundedCornerShape(36.dp)
    val lensShape = RoundedCornerShape(50)

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val slotCount = labels.size + if (hasCenterAction) 1 else 0
        val lastSlot = slotCount - 1
        val itemWidth = (maxWidth - 8.dp) / slotCount
        val itemPixels = with(density) { itemWidth.toPx() }
        val edgePixels = with(density) { 4.dp.toPx() }
        fun visualSlotAt(x: Float) = (x - edgePixels) / itemPixels - 0.5f
        fun positionAt(x: Float): Float {
            // 按住时镜片连续跟手，中央也能停留；仅释放时排除中央页签。
            return (if (direction == LayoutDirection.Rtl) lastSlot - visualSlotAt(x) else visualSlotAt(x))
                .coerceIn(0f, lastSlot.toFloat())
        }
        fun finishDrag() {
            if (!dragging) return
            val rounded = dragPosition.roundToInt().coerceIn(0, lastSlot)
            val targetSlot = if (hasCenterAction && rounded == 2) if (dragPosition < 2f) 1 else 3 else rounded
            val target = if (hasCenterAction && targetSlot >= 3) targetSlot - 1 else targetSlot
            dragging = false
            if (target != selectedIndex) select(target)
        }
        LaunchedEffect(itemPixels) { dragging = false }
        val gesture = Modifier.pointerInput(labels.size, selectedIndex, direction, itemPixels, hasCenterAction) {
            if (itemPixels <= 0f) return@pointerInput
            if (!hasCenterAction) {
                detectHorizontalDragGestures(
                    onDragStart = { point -> dragPosition = positionAt(point.x); dragging = true },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        if (dragging) dragPosition = positionAt(change.position.x)
                    },
                    onDragEnd = ::finishDrag,
                    onDragCancel = { dragging = false },
                )
            } else {
                detectDragGestures(
                    orientationLock = Orientation.Horizontal,
                    onDragStart = { down, _, _ ->
                        // 必须用真实 DOWN 判定起点，过 slop 后才落入中央的手势仍属于导航拖动。
                        dragging = visualSlotAt(down.position.x) !in 1.5f..<2.5f
                        if (dragging) dragPosition = positionAt(down.position.x)
                    },
                    onDrag = { change, _ ->
                        if (dragging) dragPosition = positionAt(change.position.x)
                    },
                    onDragEnd = { finishDrag() },
                    onDragCancel = { dragging = false },
                )
            }
        }
        Box(gesture) {
            // 录制层仅含背板与真实标签，镜片在其同级绘制，避免递归捕获和重复无障碍节点。
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min)
                    .layerBackdrop(tabsBackdrop)
                    .drawBackdrop(
                        backdrop = backdrop ?: fallback,
                        shape = { panelShape },
                        effects = { if (backdrop != null) blur(blurRadius.toPx()) },
                        highlight = { Highlight.Default.copy(alpha = if (dark) 0.35f else 0.6f) },
                        shadow = { Shadow(radius = 12.dp, alpha = 0.6f) },
                        onDrawSurface = {
                            if (backdrop != null) drawRect(colors.surface.copy(alpha = if (dark) 0.38f else 0.32f))
                        },
                    )
                    .border(with(density) { 1.toDp() }, colors.outlineVariant, panelShape)
                    .padding(4.dp).selectableGroup(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                labels.forEachIndexed { index, label ->
                    if (hasCenterAction && index == 2) {
                        // 只留等宽空列，实际按钮在录制层外绘制，避免被镜片采样后重复显示。
                        Spacer(Modifier.weight(1f).fillMaxHeight())
                    }
                    val focused by sources[index].collectIsFocusedAsState()
                    Column(
                        Modifier.weight(1f).fillMaxHeight().heightIn(min = 64.dp)
                            .selectable(
                                selected = selectedIndex == index,
                                interactionSource = sources[index], indication = null,
                                role = Role.Tab, onClick = { select(index) },
                            )
                            .then(if (focused) Modifier.border(1.5.dp, colors.onSurface, lensShape) else Modifier)
                            .padding(horizontal = 2.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                    ) {
                        Box(Modifier.clearAndSetSemantics {}) { icon(index, if (selectedIndex == index) Color(tokens.accentIcon) else colors.onSurfaceVariant) }
                        Text(label, color = if (selectedIndex == index) colors.primary else colors.onSurfaceVariant, style = MaterialTheme.typography.labelMedium,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Box(Modifier.matchParentSize().padding(4.dp).clearAndSetSemantics {}) {
                Box(
                    Modifier.align(AbsoluteAlignment.TopLeft).width(itemWidth).fillMaxHeight()
                        .drawBackdrop(
                            backdrop = tabsBackdrop,
                            shape = { lensShape },
                            effects = {
                                // API33+ 使用库提供的真实折射；旧系统保留同样的手势和高可读选中层。
                                lens((8f + 8f * pressure).dp.toPx(), (12f + 10f * pressure).dp.toPx())
                            },
                            highlight = { Highlight.Default.copy(width = 0.8.dp, alpha = 0.65f + pressure * 0.35f) },
                            shadow = { Shadow(radius = 6.dp, alpha = 0.65f) },
                            layerBlock = {
                                translationX = (if (direction == LayoutDirection.Rtl) lastSlot - position else position) * itemPixels
                                alpha = visibility
                                scaleX = 1f + 0.055f * pressure
                                scaleY = 1f + 0.08f * pressure
                            },
                            onDrawSurface = {
                                drawRect(Color(tokens.accent).copy(alpha = 0.09f - 0.035f * pressure))
                            },
                        ),
                )
            }
            if (centerAction != null) {
                Box(Modifier.matchParentSize().padding(4.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(itemWidth).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        centerAction()
                    }
                }
            }
        }
    }
}
