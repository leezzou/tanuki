package com.tanuki.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import com.tanuki.ui.design.TanukiDesign
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope

internal object TanukiDrawerPolicy {
    /** 速度以朝向打开的 dp/s 表示，RTL 在调用处归一化。 */
    fun releaseOpen(progress: Float, openingVelocity: Float): Boolean = when {
        openingVelocity > 400f -> true
        openingVelocity < -400f -> false
        else -> progress >= .5f
    }
}

/** 自绘抽屉只有一份偏移和一份遮罩 alpha；同时启停，不叠高度或窗口动画。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TanukiDrawer(
    drawerWidth: Dp,
    navigation: @Composable (closeNavigation: () -> Unit) -> Unit,
    content: @Composable (modifier: Modifier, openNavigation: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    // 恢复到打开状态直接呈现，不重播上次导航。
    val progress = remember { Animatable(if (open) 1f else 0f) }
    val scrimProgress = remember { Animatable(if (open) 1f else 0f) }
    val motion = TanukiDesign.motion
    val density = LocalDensity.current
    val widthPx = with(density) { drawerWidth.toPx() }
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val showing = open || progress.value > 0f || scrimProgress.value > 0f || dragging
    LaunchedEffect(open, dragging, motion.reducedMotion) {
        if (!dragging) coroutineScope {
            val target = if (open) 1f else 0f
            launch {
                if (motion.reducedMotion) progress.snapTo(target)
                else progress.animateTo(target, motion.sheet(open))
            }
            launch {
                if (motion.reducedMotion) scrimProgress.snapTo(target)
                else scrimProgress.animateTo(target, motion.scrim())
            }
        }
    }
    LaunchedEffect(open) { if (open) focus.requestFocus() }
    Box(
        modifier.draggable(
            state = rememberDraggableState { delta ->
                scope.launch {
                    val target = (progress.value + delta * direction / widthPx).coerceIn(0f, 1f)
                    progress.snapTo(target)
                    scrimProgress.snapTo(target)
                }
            },
            orientation = Orientation.Horizontal,
            onDragStarted = { dragging = true; progress.stop(); scrimProgress.stop() },
            onDragStopped = { velocity ->
                open = TanukiDrawerPolicy.releaseOpen(progress.value, velocity * direction / density.density)
                dragging = false
            },
        ),
    ) {
        CompositionLocalProvider(LocalTanukiDrawerOpen provides showing) {
            content(Modifier.fillMaxSize().tanukiOverlayInput(!showing), { open = true })
        }
        if (showing) {
            val scrim = DrawerDefaults.scrimColor
            Box(
                Modifier.fillMaxSize()
                    .background(scrim.copy(alpha = scrim.alpha * scrimProgress.value))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { open = false },
            )
            ModalDrawerSheet(
                modifier = Modifier.align(Alignment.CenterStart).fillMaxHeight().width(drawerWidth)
                    .tanukiOverlayInput(open || dragging)
                    .graphicsLayer { translationX = (progress.value - 1f) * widthPx * direction }
                    .focusRequester(focus).focusable()
                    .semantics {
                        paneTitle = "导航抽屉"
                        dismiss { open = false; true }
                    },
            ) {
                navigation { open = false }
            }
        }
        // 晚于正文注册，打开途中与拖动时也先关抽屉，不触发下层详情返回。
        BackHandler(enabled = showing) { dragging = false; open = false }
    }
}
