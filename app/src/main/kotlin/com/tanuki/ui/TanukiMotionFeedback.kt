package com.tanuki.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tanuki.ui.design.TanukiDesign
import com.tanuki.ui.design.TanukiMotionSpecs
import kotlinx.coroutines.delay

/**
 * 系统窗口只有 Material 一个动画所有者；宿主常驻，关闭时先 hide 再释放展示快照。
 * 调用方必须传当前模型（关闭时为 null），不能在外层用 if 移除本宿主。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun <T : Any> TanukiModalSheet(
    value: T?,
    onDismissRequest: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.(T) -> Unit,
) {
    var presentation by remember { mutableStateOf(TanukiOverlayState<T>()) }
    presentation = presentation.update(value)
    val shown = presentation.shown ?: return
    val reduced = TanukiDesign.motion.reducedMotion
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(presentation.open, reduced) {
        if (!presentation.open) {
            closing = true
            // 移除动画直接卸载；正常关闭仍由系统窗口完成位移及遮罩。
            if (!reduced) sheetState.hide()
            presentation = presentation.hidden()
        } else if (closing) {
            closing = false
            // 快速重开重定向正在退出的系统动画，不排队等待旧 hide。
            sheetState.show()
        }
    }
    if (reduced && !presentation.open) return
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
    ) {
        Column(Modifier.tanukiOverlayInput(presentation.open)) {
            content(shown)
        }
    }
}

/** 离场快照不接受旧操作；入场与可见期间始终允许交互。 */
internal fun Modifier.tanukiOverlayInput(enabled: Boolean): Modifier = if (enabled) this else
    clearAndSetSemantics {}.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        }
    }

/** 固定键位内仅淡化/缩放图标；不移动键位、不补间内容尺寸。 */
@Composable
internal fun <T> TanukiIconSwap(target: T, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    val motion = TanukiDesign.motion
    Box(modifier, contentAlignment = Alignment.Center) {
        if (motion.reducedMotion) content(target) else AnimatedContent(
            targetState = target,
            transitionSpec = {
                ((fadeIn(motion.icon(true)) + scaleIn(motion.icon(true), initialScale = TanukiMotionSpecs.ICON_SCALE))
                    togetherWith (fadeOut(motion.icon(false)) + scaleOut(motion.icon(false), targetScale = TanukiMotionSpecs.ICON_SCALE)))
                    .using(null)
            },
            contentAlignment = Alignment.Center,
            label = "图标状态",
        ) { content(it) }
    }
}

/** 调用方传入固定可用区域；这里只淡化，不承担高度或页面导航。 */
@Composable
internal fun <T> TanukiStateCrossfade(
    target: T,
    modifier: Modifier = Modifier.fillMaxSize(),
    content: @Composable (T) -> Unit,
) {
    val motion = TanukiDesign.motion
    // 开关变化只替换 spec，保持内容组合身份，避免列表滚动状态被重新创建。
    Crossfade(
        targetState = target, modifier = modifier,
        animationSpec = motion.state(), label = "区域状态", content = content,
    )
}

/** 数值立即更新，只给新值淡入；最小宽度防止相邻操作被个位数挤动。 */
@Composable
internal fun TanukiAnimatedCount(
    value: String,
    modifier: Modifier = Modifier,
    minWidth: Dp = 48.dp,
    style: TextStyle = MaterialTheme.typography.labelMedium,
    color: Color = Color.Unspecified,
) {
    val motion = TanukiDesign.motion
    val alpha = remember { Animatable(1f) }
    var previous by remember { mutableStateOf(value) }
    LaunchedEffect(value, motion.reducedMotion) {
        val changed = previous != value
        previous = value
        if (motion.reducedMotion || !changed) alpha.snapTo(1f) else {
            alpha.snapTo(0f)
            alpha.animateTo(1f, motion.count())
        }
    }
    Text(value, modifier.widthIn(min = minWidth).graphicsLayer {
        this.alpha = if (motion.reducedMotion) 1f else alpha.value
    }, style = style, color = color, maxLines = 1)
}

/** 复制成功的固定文字槽；重按立即复制并重置确认计时，移除动画仍保留反馈。 */
@Composable
internal fun TanukiCopyButton(
    label: String,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
    identity: Any? = Unit,
    enabled: Boolean = true,
) {
    var generation by remember(identity) { mutableIntStateOf(0) }
    var copied by remember(identity) { mutableStateOf(false) }
    val motion = TanukiDesign.motion
    val accessibility = LocalAccessibilityManager.current
    val textStyle = MaterialTheme.typography.labelLarge
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val width = with(density) {
        maxOf(measurer.measure(label, textStyle).size.width,
            measurer.measure("已复制", textStyle).size.width).toDp()
    }
    LaunchedEffect(identity, generation) {
        if (generation == 0) return@LaunchedEffect
        delay(accessibility?.calculateRecommendedTimeoutMillis(
            TanukiMotionSpecs.COPY_CONFIRMATION_MS, containsIcons = false,
            containsText = true, containsControls = false,
        ) ?: TanukiMotionSpecs.COPY_CONFIRMATION_MS)
        copied = false
    }
    TextButton(onClick = { onCopy(); copied = true; generation++ }, modifier = modifier, enabled = enabled) {
        Box(Modifier.width(width).clearAndSetSemantics {
            text = AnnotatedString(if (copied) "已复制" else label)
        }, contentAlignment = Alignment.Center) {
            if (motion.reducedMotion) Text(if (copied) "已复制" else label) else Crossfade(
                targetState = copied, animationSpec = motion.copy(), label = "复制确认",
            ) { Text(if (it) "已复制" else label) }
        }
    }
}
