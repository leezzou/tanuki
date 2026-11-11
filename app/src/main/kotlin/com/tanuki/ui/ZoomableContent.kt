package com.tanuki.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.rememberCoroutineScope
import com.tanuki.ui.design.TanukiDesign
import com.tanuki.ui.design.TanukiMotion
import com.tanuki.ui.design.TanukiMotionEasing
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlin.math.min

/**
 * 图片/PDF 共用的缩放平移纯策略。单位为像素，内容始终以视口中心为原点；
 * 平移量表示内容中心相对视口中心的偏移。
 */
internal object ZoomPolicy {
    const val MIN_SCALE = 1f
    const val MAX_SCALE = 5f
    const val DOUBLE_TAP_SCALE = 2.5f
    const val RESET_SCALE = 1f
    private const val TOGGLE_EPSILON = 0.01f

    data class Pan(val x: Float, val y: Float) {
        companion object { val Zero = Pan(0f, 0f) }
    }

    fun clampScale(value: Float): Float =
        if (!value.isFinite()) MIN_SCALE else value.coerceIn(MIN_SCALE, MAX_SCALE)

    /** 双击在原始大小和 2.5 倍之间切换；其他中间缩放一律回到原始大小。 */
    fun nextDoubleTapScale(current: Float): Float =
        if (clampScale(current) > MIN_SCALE + TOGGLE_EPSILON) RESET_SCALE else DOUBLE_TAP_SCALE

    fun fittedSize(contentWidth: Float, contentHeight: Float, viewportWidth: Float, viewportHeight: Float): Pan {
        if (contentWidth <= 0f || contentHeight <= 0f || viewportWidth <= 0f || viewportHeight <= 0f) return Pan.Zero
        val scale = min(viewportWidth / contentWidth, viewportHeight / contentHeight)
        return Pan(contentWidth * scale, contentHeight * scale)
    }

    /** 围绕手指中心缩放，返回尚未受边界约束的平移。 */
    fun zoomAround(pan: Pan, centroidX: Float, centroidY: Float, centerX: Float, centerY: Float, factor: Float): Pan {
        if (!factor.isFinite() || factor <= 0f) return pan
        return Pan(
            x = (1f - factor) * (centroidX - centerX) + factor * pan.x,
            y = (1f - factor) * (centroidY - centerY) + factor * pan.y,
        )
    }

    /** 内容小于视口时禁止平移，否则限制在露出的内容边界内。 */
    fun clampPan(pan: Pan, scaledWidth: Float, scaledHeight: Float, viewportWidth: Float, viewportHeight: Float): Pan {
        val maxX = ((scaledWidth - viewportWidth) / 2f).coerceAtLeast(0f)
        val maxY = ((scaledHeight - viewportHeight) / 2f).coerceAtLeast(0f)
        if (!pan.x.isFinite() || !pan.y.isFinite()) return Pan.Zero
        return Pan(pan.x.coerceIn(-maxX, maxX), pan.y.coerceIn(-maxY, maxY))
    }
}

/**
 * 给内容加双指缩放、平移和双击缩放；resetKey 变化（切换文件/页面）或视口变化时恢复 1x。
 * 内容按 Fit 显示，缩放与平移只作用于显示层，不修改原图或 PDF。
 */
@Composable
internal fun Zoomable(
    contentWidth: Int,
    contentHeight: Int,
    resetKey: Any?,
    modifier: Modifier = Modifier,
    onScaleChange: (Float) -> Unit = {},
    // 查看器图片全屏只改变视口；其他调用方保留原有重置语义。
    preserveViewportState: Boolean = false,
    content: @Composable (Modifier) -> Unit,
) {
    var scale by remember(resetKey) { mutableFloatStateOf(ZoomPolicy.MIN_SCALE) }
    var pan by remember(resetKey) { mutableStateOf(ZoomPolicy.Pan.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val motion = TanukiDesign.motion
    val scope = rememberCoroutineScope()
    var zoomJob by remember(resetKey) { mutableStateOf<Job?>(null) }
    var zoomTarget by remember(resetKey) { mutableStateOf<Float?>(null) }
    LaunchedEffect(resetKey, viewport, motion.reducedMotion) {
        zoomJob?.cancel()
        if (motion.reducedMotion && zoomTarget != null) {
            scale = zoomTarget!!
            pan = ZoomPolicy.Pan.Zero
        }
        zoomTarget = null
        if (!preserveViewportState) {
            scale = ZoomPolicy.MIN_SCALE
            pan = ZoomPolicy.Pan.Zero
        } else {
            val fitted = ZoomPolicy.fittedSize(contentWidth.toFloat(), contentHeight.toFloat(),
                viewport.width.toFloat(), viewport.height.toFloat())
            pan = ZoomPolicy.clampPan(pan, fitted.x * scale, fitted.y * scale,
                viewport.width.toFloat(), viewport.height.toFloat())
        }
        onScaleChange(scale)
    }
    val fitted = ZoomPolicy.fittedSize(
        contentWidth.toFloat(), contentHeight.toFloat(), viewport.width.toFloat(), viewport.height.toFloat(),
    )
    fun apply(factor: Float, centroid: Offset, move: Offset) {
        // 手势立即接管当前显示值，不等待双击补间完成。
        zoomJob?.cancel()
        zoomTarget = null
        val next = ZoomPolicy.clampScale(scale * factor)
        val actual = if (scale > 0f) next / scale else 1f
        val zoomed = ZoomPolicy.zoomAround(pan, centroid.x, centroid.y,
            viewport.width / 2f, viewport.height / 2f, actual)
        pan = ZoomPolicy.clampPan(
            ZoomPolicy.Pan(zoomed.x + move.x, zoomed.y + move.y),
            fitted.x * next, fitted.y * next, viewport.width.toFloat(), viewport.height.toFloat(),
        )
        if (next != scale) {
            scale = next
            onScaleChange(next)
        }
    }
    Box(
        modifier
            .clipToBounds()
            .onSizeChanged { viewport = it }
            .pointerInput(resetKey, fitted, viewport) {
                detectTransformGestures { centroid, move, zoom, _ -> apply(zoom, centroid, move) }
            }
            .pointerInput(resetKey, viewport, motion.reducedMotion) {
                detectTapGestures(onDoubleTap = {
                    zoomJob?.cancel()
                    val target = ZoomPolicy.nextDoubleTapScale(scale)
                    if (!preserveViewportState || motion.reducedMotion) {
                        scale = target
                        pan = ZoomPolicy.Pan.Zero
                        onScaleChange(target)
                    } else {
                        val fromScale = scale
                        val fromPan = pan
                        zoomTarget = target
                        zoomJob = scope.launch {
                            Animatable(0f).animateTo(1f, tween(motion.duration(TanukiMotion.layout),
                                easing = TanukiMotionEasing.standard)) {
                                scale = fromScale + (target - fromScale) * value
                                pan = ZoomPolicy.Pan(fromPan.x * (1f - value), fromPan.y * (1f - value))
                                onScaleChange(scale)
                            }
                        }
                    }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = pan.x
                translationY = pan.y
            },
        ) {
            content(Modifier.fillMaxSize())
        }
    }
}
