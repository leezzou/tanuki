package com.tanuki.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 胶囊按钮的语气；填充层级从强到弱依次为 Filled、Tonal、Outline、Ghost。 */
internal enum class TanukiPillButtonTone {
    Filled,
    Tonal,
    Outline,
    Ghost,
    Danger,
}

/** 胶囊按钮的背景、内容色和可选边框；内容色同时用于文字与 leading 图标。 */
internal data class TanukiPillButtonColors(
    val background: Long,
    val content: Long,
    val border: Long?,
)

/**
 * 胶囊按钮。
 *
 * 视觉高度由内边距决定，外层始终至少 48dp；加载时按钮禁用点击但仍保留宽度，
 * 避免布局跳动。主操作最多一个 Filled，其余使用 Tonal 或 Ghost。
 */
@Composable
internal fun TanukiPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: TanukiPillButtonTone = TanukiPillButtonTone.Filled,
    enabled: Boolean = true,
    loading: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
) {
    val tokens = TanukiDesign.tokens
    val colors = tanukiPillButtonColors(tokens.colors, tone, enabled)
    val interaction = remember { MutableInteractionSource() }
    val active = enabled && !loading
    val pressed by interaction.collectIsPressedAsState()
    val motion = TanukiDesign.motion
    val scale by animateFloatAsState(
        targetValue = motion.pressScale(pressed && active),
        animationSpec = motion.press(pressed && active), label = "按钮按压",
    )
    // leading 槽位中的图标默认读取 LocalContentColor；必须与按钮 tone/enabled 同步，
    // 否则图标会沿用外层页面的颜色，在 Filled/Danger 背景上失去对比度。
    CompositionLocalProvider(LocalContentColor provides Color(colors.content)) {
        Row(
            modifier = modifier
                .heightIn(min = TanukiSpace.TOUCH_TARGET_MIN_DP.dp)
                .graphicsLayer {
                    scaleX = if (tokens.reducedMotion) 1f else scale
                    scaleY = if (tokens.reducedMotion) 1f else scale
                }
                .clip(TanukiShapeTokens.pill)
                .background(Color(colors.background))
                .then(
                    if (colors.border != null) {
                        Modifier.border(1.dp, Color(colors.border), TanukiShapeTokens.pill)
                    } else {
                        Modifier
                    },
                )
                .clickable(
                    enabled = active,
                    role = Role.Button,
                    interactionSource = interaction,
                    indication = LocalIndication.current,
                    onClick = onClick,
                )
                .semantics(mergeDescendants = true) {
                    if (loading) stateDescription = "处理中"
                }
                .padding(horizontal = TanukiSpace.XL_DP.dp, vertical = TanukiSpace.MD_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TanukiSpace.SM_DP.dp, Alignment.CenterHorizontally),
        ) {
            if (loading) {
                if (tokens.reducedMotion) {
                    Text(
                        text = "…",
                        color = Color(colors.content),
                        style = tokens.type.captionLarge,
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color(colors.content),
                        strokeWidth = 2.dp,
                    )
                }
            } else if (leading != null) {
                leading()
            }
            Text(
                text = text,
                color = Color(colors.content),
                style = tokens.type.captionLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 按钮颜色解析保持为纯函数，便于 JVM 锁定 light/dark、tone 与停用态。 */
internal fun tanukiPillButtonColors(
    colors: TanukiColorTokens,
    tone: TanukiPillButtonTone,
    enabled: Boolean,
): TanukiPillButtonColors {
    if (!enabled) {
        return TanukiPillButtonColors(
            background = colors.surfaceMuted,
            content = colors.textDisabled,
            border = null,
        )
    }
    return when (tone) {
        TanukiPillButtonTone.Filled -> TanukiPillButtonColors(colors.primary, colors.onPrimary, null)
        TanukiPillButtonTone.Tonal -> TanukiPillButtonColors(
            colors.primaryContainer,
            colors.onPrimaryContainer,
            null,
        )
        TanukiPillButtonTone.Outline -> TanukiPillButtonColors(
            background = colors.surface,
            content = colors.textPrimary,
            border = colors.outlineStrong,
        )
        TanukiPillButtonTone.Ghost -> TanukiPillButtonColors(
            background = 0x00000000L,
            content = colors.textPrimary,
            border = null,
        )
        TanukiPillButtonTone.Danger -> TanukiPillButtonColors(colors.danger, colors.onDanger, null)
    }
}

/**
 * 状态点；默认静态。运行中的呼吸只用 2.4s 环境令牌，系统移除动画时保持常亮。
 * 只有真正需要呼吸时才创建循环动画，静态状态点不产生持续重绘。
 */
@Composable
internal fun TanukiStatusDot(
    tone: TanukiStatusTone,
    modifier: Modifier = Modifier,
    pulse: Boolean = false,
    diameter: Dp = 8.dp,
    contentDescription: String? = null,
    decorative: Boolean = false,
) {
    val tokens = TanukiDesign.tokens
    val role = tokens.colors.statusColors(tone)
    val alpha = if (pulse && !tokens.reducedMotion) rememberPulseAlpha() else 1f
    val dotModifier = if (decorative) {
        modifier
            .size(diameter)
            .clearAndSetSemantics {}
    } else {
        modifier
            .size(diameter)
            .semantics {
                this.contentDescription = contentDescription ?: "状态：${tone.label()}"
            }
    }
    Canvas(dotModifier) {
        drawCircle(
            color = Color(role.dot).copy(alpha = alpha),
            radius = size.minDimension / 2f,
        )
    }
}

@Composable
private fun rememberPulseAlpha(): Float {
    val transition = rememberInfiniteTransition(label = "状态点")
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = TanukiMotion.AMBIENT_MS / 2,
                easing = TanukiMotionEasing.linear,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "状态点呼吸",
    )
    return alpha
}

/** 状态 chip：容器色 + 容器正文色，默认高度 28dp；可点击时扩展到 48dp。 */
@Composable
internal fun TanukiStatusChip(
    tone: TanukiStatusTone,
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    pulse: Boolean = false,
    contentDescription: String? = null,
) {
    val tokens = TanukiDesign.tokens
    val role = tokens.colors.statusColors(tone)
    val background by animateColorAsState(Color(role.container), TanukiDesign.motion.toggle(), label = "状态颜色")
    val interaction = remember { MutableInteractionSource() }
    val base = modifier
        .heightIn(min = if (onClick != null) TanukiSpace.TOUCH_TARGET_MIN_DP.dp else 28.dp)
        .clip(TanukiShapeTokens.pill)
        .background(if (tokens.reducedMotion) Color(role.container) else background)
        // 需要更完整说法（例如“权限：可写，点击管理”）时覆盖朗读内容；
        // 不传时保留下面的可见标签，不产生额外语义节点。
        .then(
            if (contentDescription != null) {
                Modifier.semantics { this.contentDescription = contentDescription }
            } else {
                Modifier
            },
        )
    val interactive = if (onClick != null) {
        base.clickable(
            enabled = enabled,
            role = Role.Button,
            interactionSource = interaction,
            indication = LocalIndication.current,
            onClick = onClick,
        )
    } else {
        base
    }
    Row(
        modifier = interactive
            .padding(horizontal = TanukiSpace.MD_DP.dp, vertical = TanukiSpace.XS_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TanukiSpace.SM_DP.dp),
    ) {
        TanukiStatusDot(tone = tone, pulse = pulse, decorative = true)
        Text(
            text = label,
            color = Color(role.content),
            style = tokens.type.captionMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 可按压容器。
 *
 * 点击时只做 0.98 缩放反馈，时长走反馈令牌；没有点击回调时退化为
 * 普通容器，不额外占用触控区。调用方负责内容背景和边框。
 */
@Composable
internal fun TanukiPressable(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    shape: Shape = TanukiShapeTokens.card,
    content: @Composable BoxScope.() -> Unit,
) {
    val tokens = TanukiDesign.tokens
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val motion = TanukiDesign.motion
    val activePress = pressed && enabled && onClick != null
    val target = motion.pressScale(activePress)
    val scale by animateFloatAsState(
        targetValue = target,
        animationSpec = motion.press(activePress),
        label = "按压反馈",
    )
    val shapeModifier = Modifier
        .graphicsLayer {
            scaleX = if (tokens.reducedMotion) 1f else scale
            scaleY = if (tokens.reducedMotion) 1f else scale
        }
        .clip(shape)
        .then(
            if (onClick != null) {
                Modifier
                    .heightIn(min = TanukiSpace.TOUCH_TARGET_MIN_DP.dp)
                    .clickable(
                        enabled = enabled,
                        role = Role.Button,
                        interactionSource = interaction,
                        indication = LocalIndication.current,
                        onClick = onClick,
                    )
            } else {
                Modifier
            },
        )
    Box(modifier.then(shapeModifier), content = content)
}

/**
 * 流式光标：2dp 竖条，完整明暗周期 2.4s；系统移除动画时保持常亮。
 *
 * 只应放在流式文本末尾，不要用于普通静态文本装饰。
 */
@Composable
internal fun TanukiStreamingCursor(
    modifier: Modifier = Modifier,
    visible: Boolean = true,
) {
    if (!visible) return
    val tokens = TanukiDesign.tokens
    val alpha = if (tokens.reducedMotion) 1f else rememberCursorBlink()
    Canvas(
        modifier
            .size(width = 2.dp, height = 18.dp)
            .semantics { contentDescription = "正在输出" },
    ) {
        drawRoundRect(
            color = Color(tokens.colors.textPrimary).copy(alpha = alpha),
            topLeft = Offset.Zero,
            size = Size(size.width, size.height),
            cornerRadius = CornerRadius(1.dp.toPx()),
        )
    }
}

@Composable
private fun rememberCursorBlink(): Float {
    val transition = rememberInfiniteTransition(label = "流式光标")
    val blink by transition.animateFloat(
        initialValue = 0.15f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = TanukiMotion.AMBIENT_MS / 2,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "流式光标闪烁",
    )
    return blink
}

/** 代码/命令文本；唯一允许使用等宽字体的入口之一。 */
@Composable
internal fun TanukiCodeText(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) {
    val tokens = TanukiDesign.tokens
    Text(
        text = text,
        modifier = modifier,
        color = Color(tokens.colors.textPrimary),
        style = tokens.type.codeMedium,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 文件路径文本；与代码一样使用等宽字体，默认单行省略。 */
@Composable
internal fun TanukiPathText(
    path: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
) {
    val tokens = TanukiDesign.tokens
    Text(
        text = path,
        modifier = modifier,
        color = Color(tokens.colors.textTertiary),
        style = tokens.type.codeSmall,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun TanukiStatusTone.label(): String = when (this) {
    TanukiStatusTone.Neutral -> "中性"
    TanukiStatusTone.Success -> "成功"
    TanukiStatusTone.Warning -> "警告"
    TanukiStatusTone.Danger -> "危险"
    TanukiStatusTone.Info -> "信息"
}
