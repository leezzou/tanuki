package com.tanuki.ui.design

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * 底部弹层骨架，支持下拉关闭。
 *
 * - 遮罩点击、返回键、向下拖动超过约 96dp 都会请求关闭。
 * - 拖动只改变弹层自身位移，不改变页面滚动或内容状态。
 * - 结构动画使用 240–340ms；系统移除动画时时长为 0。
 * - 调用方应把本组件放在页面根部，确保遮罩覆盖整个页面。
 * - 内容区不内置滚动；长内容由调用方使用 LazyColumn 或 verticalScroll，
 *   并自行处理与下拉手势的边界。
 *
 * 这是骨架而非业务弹层：标题、说明和内容由调用方提供，组件不读取、不缓存
 * 任何业务状态。
 */
@Composable
internal fun TanukiBottomSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    /**
     * 固定占屏比例；为空时按内容包裹。
     *
     * 密集详情页可以传 0.9 让弹层占满大部分屏幕并在内部滚动；短确认框保持
     * 包裹，避免小弹层被拉成全屏。
     */
    heightFraction: Float? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = TanukiDesign.tokens
    val density = LocalDensity.current
    val dismissThreshold = with(density) { 96.dp.toPx() }
    val sheetShape = TanukiSheetShape
    val spec = TanukiElevation.of(TanukiSurfaceLayer.Sheet)
    val enterMs = if (tokens.reducedMotion) 0 else TanukiMotion.sheetOpen.durationMs
    val exitMs = if (tokens.reducedMotion) 0 else TanukiMotion.sheetDismiss.durationMs
    val scope = rememberCoroutineScope()
    val dismiss by rememberUpdatedState(onDismissRequest)
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val settle = remember { Animatable(0f) }

    LaunchedEffect(visible) {
        if (visible) {
            dragOffset = 0f
            dragging = false
            settle.snapTo(0f)
        }
    }
    BackHandler(enabled = visible) { dismiss() }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(enterMs)),
            exit = fadeOut(animationSpec = tween(exitMs)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(tokens.colors.scrim))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { dismiss() },
                    ),
            )
        }

        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(
                animationSpec = tween(enterMs, easing = FastOutSlowInEasing),
            ) { fullHeight -> fullHeight } + fadeIn(animationSpec = tween(enterMs)),
            exit = slideOutVertically(
                animationSpec = tween(exitMs, easing = FastOutSlowInEasing),
            ) { fullHeight -> fullHeight } + fadeOut(animationSpec = tween(exitMs)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (heightFraction != null) Modifier.fillMaxHeight(heightFraction) else Modifier)
                    .graphicsLayer {
                        translationY = if (dragging) dragOffset else settle.value
                    }
                    .shadow(
                        elevation = spec.shadowDp.dp,
                        shape = sheetShape,
                        clip = false,
                    )
                    .clip(sheetShape)
                    .background(Color(tokens.colors.sheet))
                    .border(0.75.dp, Color(tokens.colors.hairline), sheetShape)
                    .pointerInput(visible, dismissThreshold) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                dragging = true
                                dragOffset = 0f
                            },
                            onVerticalDrag = { change, amount ->
                                change.consume()
                                dragOffset = (dragOffset + amount).coerceAtLeast(0f)
                            },
                            onDragEnd = {
                                val shouldDismiss = dragOffset > dismissThreshold
                                if (shouldDismiss) {
                                    // 保持当前拖动位移进入退出动画，避免先跳回 0 再滑出。
                                    scope.launch {
                                        settle.snapTo(dragOffset)
                                        dragging = false
                                        dismiss()
                                    }
                                } else {
                                    scope.launch {
                                        settle.snapTo(dragOffset)
                                        dragging = false
                                        settle.animateTo(
                                            targetValue = 0f,
                                            animationSpec = tween(
                                                durationMillis = if (tokens.reducedMotion) 0 else TanukiMotion.sheetDismiss.durationMs,
                                                easing = FastOutSlowInEasing,
                                            ),
                                        )
                                    }
                                }
                            },
                            onDragCancel = {
                                scope.launch {
                                    settle.snapTo(dragOffset)
                                    dragging = false
                                    settle.animateTo(
                                        targetValue = 0f,
                                        animationSpec = tween(
                                            durationMillis = if (tokens.reducedMotion) 0 else TanukiMotion.sheetDismiss.durationMs,
                                            easing = FastOutSlowInEasing,
                                        ),
                                    )
                                }
                            },
                        )
                    }
                    .navigationBarsPadding()
                    .semantics { paneTitle = title },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = TanukiSpace.SM_DP.dp, bottom = TanukiSpace.XS_DP.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(width = 36.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(Color(tokens.colors.outline)),
                    )
                }
                // 弹层自己绘制 sheet 背景，必须同时提供匹配的正文色；否则正文会
                // 继承页面默认黑色，在深色 sheet 上不可读（PR #70 评审 P2）。
                CompositionLocalProvider(LocalContentColor provides Color(tokens.colors.textPrimary)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = TanukiSpace.XL_DP.dp)
                            .padding(bottom = TanukiSpace.XL_DP.dp),
                    ) {
                        Text(
                            text = title,
                            color = Color(tokens.colors.textPrimary),
                            style = tokens.type.titleMedium,
                        )
                        if (subtitle != null) {
                            Spacer(Modifier.height(TanukiSpace.XS_DP.dp))
                            Text(
                                text = subtitle,
                                color = Color(tokens.colors.textSecondary),
                                style = tokens.type.bodySmall,
                            )
                        }
                        Spacer(Modifier.height(TanukiSpace.MD_DP.dp))
                        content()
                    }
                }
            }
        }
    }
}
