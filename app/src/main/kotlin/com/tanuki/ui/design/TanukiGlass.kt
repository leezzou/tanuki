package com.tanuki.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow

/**
 * 玻璃/海拔表面。
 *
 * [backdrop] 为 null 时只画半透明底色，不做模糊；传入已录制的背板后才启用
 * chrome/pill 的模糊。sheet 和 card 的 `blurEnabled` 为 false，即使传入背板
 * 也不会模糊，优先保证密集内容可读。
 */
@Composable
internal fun TanukiGlassSurface(
    layer: TanukiSurfaceLayer,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    shape: Shape = layer.shape(),
    content: @Composable BoxScope.() -> Unit,
) {
    val tokens = TanukiDesign.tokens
    val spec = TanukiElevation.of(layer)
    val borderWidth = with(androidx.compose.ui.platform.LocalDensity.current) { 1.toDp() }
    // 没有背板时只画半透明底色，不调用模糊；这条路径不会假装已经折射或模糊。
    if (backdrop == null) {
        Box(
            modifier = modifier
                .shadow(elevation = spec.shadowDp.dp, shape = shape, clip = false)
                .clip(shape)
                .background(Color(tokens.colors.surface).copy(alpha = spec.fillAlpha.toFloat()))
                .border(width = borderWidth, color = Color(tokens.colors.outline), shape = shape),
            content = content,
        )
        return
    }
    Box(
        modifier = modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    if (spec.blurEnabled) blur(spec.blurRadiusDp.dp.toPx())
                },
                highlight = {
                    Highlight.Default.copy(alpha = if (tokens.isDark) 0.28f else 0.55f)
                },
                shadow = {
                    Shadow(radius = spec.shadowDp.dp, alpha = spec.shadowAlpha.toFloat())
                },
                onDrawSurface = {
                    drawRect(
                        Color(tokens.colors.surface).copy(alpha = spec.fillAlpha.toFloat()),
                    )
                },
            )
            .clip(shape)
            .border(width = borderWidth, color = Color(tokens.colors.outline), shape = shape),
        content = content,
    )
}

/**
 * 玻璃胶囊。
 *
 * 用于悬浮返回、会话切换、单个高频操作等短内容；高度至少 48dp。没有背板时
 * 仍是半透明胶囊，但不会声称已经模糊。
 */
@Composable
internal fun TanukiGlassPill(
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = TanukiSpace.LG_DP.dp,
        vertical = TanukiSpace.SM_DP.dp,
    ),
    content: @Composable RowScope.() -> Unit,
) {
    TanukiGlassSurface(
        layer = TanukiSurfaceLayer.Pill,
        modifier = modifier.heightIn(min = TanukiSpace.TOUCH_TARGET_MIN_DP.dp),
        backdrop = backdrop,
        shape = TanukiShapeTokens.pill,
    ) {
        Row(
            modifier = Modifier.padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TanukiSpace.SM_DP.dp),
            content = content,
        )
    }
}

/** 顶栏/底栏 chrome；默认允许模糊，传 null 背板时退化为半透明。 */
@Composable
internal fun TanukiGlassChrome(
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = TanukiSpace.LG_DP.dp,
        vertical = TanukiSpace.SM_DP.dp,
    ),
    content: @Composable RowScope.() -> Unit,
) {
    TanukiGlassSurface(
        layer = TanukiSurfaceLayer.Chrome,
        modifier = modifier.heightIn(min = TanukiSpace.LIST_ROW_MIN_DP.dp),
        backdrop = backdrop,
        shape = TanukiShapeTokens.chrome,
    ) {
        Row(
            modifier = Modifier.padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TanukiSpace.SM_DP.dp),
            content = content,
        )
    }
}

/**
 * 内容卡：不模糊，使用抬升表面、细边框和按压反馈。
 *
 * 传入 `onClick` 时由 [TanukiPressable] 提供 48dp 触控区和缩放反馈；不传时
 * 只是普通容器，不拦截触摸。
 */
@Composable
internal fun TanukiCardSurface(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = TanukiDesign.tokens
    val shape = TanukiShapeTokens.card
    TanukiPressable(
        modifier = modifier,
        onClick = onClick,
        shape = shape,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(tokens.colors.surfaceRaised))
                .border(with(androidx.compose.ui.platform.LocalDensity.current) { 1.toDp() }, Color(tokens.colors.hairline), shape)
                .padding(TanukiSpace.LG_DP.dp),
            content = content,
        )
    }
}

/** 底部弹层顶部圆角的辅助形状；弹层内容需要圆角裁剪时使用。 */
internal val TanukiSheetShape: Shape
    get() = RoundedCornerShape(
        topStart = TanukiRadius.SHEET_DP.dp,
        topEnd = TanukiRadius.SHEET_DP.dp,
        bottomStart = 0.dp,
        bottomEnd = 0.dp,
    )
