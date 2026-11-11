package com.tanuki.ui.design

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** 排版令牌的 Compose 形态；四档角色各有三号。 */
@Immutable
internal data class TanukiTextStyles(
    val titleLarge: TextStyle,
    val titleMedium: TextStyle,
    val titleSmall: TextStyle,
    val bodyLarge: TextStyle,
    val bodyMedium: TextStyle,
    val bodySmall: TextStyle,
    val captionLarge: TextStyle,
    val captionMedium: TextStyle,
    val captionSmall: TextStyle,
    val codeLarge: TextStyle,
    val codeMedium: TextStyle,
    val codeSmall: TextStyle,
)

/** 组件可读取的完整令牌集合。 */
@Immutable
internal data class TanukiDesignTokens(
    val isDark: Boolean,
    val reducedMotion: Boolean,
    val colors: TanukiColorTokens,
    val type: TanukiTextStyles,
)

/** 圆角令牌的 Compose 形态。 */
internal object TanukiShapeTokens {
    val control: CornerBasedShape = RoundedCornerShape(TanukiRadius.CONTROL_DP.dp)
    val card: CornerBasedShape = RoundedCornerShape(TanukiRadius.CARD_DP.dp)
    val sheet: CornerBasedShape = RoundedCornerShape(
        topStart = TanukiRadius.SHEET_DP.dp,
        topEnd = TanukiRadius.SHEET_DP.dp,
        bottomStart = 0.dp,
        bottomEnd = 0.dp,
    )
    val chrome: CornerBasedShape = RoundedCornerShape(TanukiRadius.CHROME_DP.dp)
    val pill: CornerBasedShape = RoundedCornerShape(percent = TanukiRadius.PILL_PERCENT)

    /**
     * 映射到 Material3 的通用圆角阶梯。
     *
     * 这里刻意不放 `sheet`：底部弹层的“只有上圆角”是专用形状，放进通用阶梯
     * 会让普通卡片出现方下角。通用 large/extraLarge 统一使用 chrome 的 28dp
     * 全圆角，弹层继续读 [TanukiSheetShape]。
     */
    val material: Shapes = Shapes(
        extraSmall = RoundedCornerShape(TanukiRadius.SMALL_DP.dp),
        small = control,
        medium = card,
        large = chrome,
        extraLarge = chrome,
    )
}

internal fun TanukiSurfaceLayer.shape(): Shape = when (this) {
    TanukiSurfaceLayer.Card -> TanukiShapeTokens.card
    TanukiSurfaceLayer.Pill -> TanukiShapeTokens.pill
    TanukiSurfaceLayer.Sheet -> TanukiShapeTokens.sheet
    TanukiSurfaceLayer.Chrome -> TanukiShapeTokens.chrome
}

/**
 * 主题令牌入口。
 *
 * 页面应在根组合外包一层 [TanukiDesignTheme]；组件通过 [TanukiDesign.tokens]
 * 读取同一份令牌，不在业务代码里散落色值和时长。
 */
internal object TanukiDesign {
    val tokens: TanukiDesignTokens
        @Composable @ReadOnlyComposable
        get() = LocalTanukiDesignTokens.current
    val motion: TanukiMotionSpecs
        @Composable @ReadOnlyComposable
        get() = TanukiMotionSpecs(tokens.reducedMotion)
}

private val LocalTanukiDesignTokens = staticCompositionLocalOf<TanukiDesignTokens> {
    error("TanukiDesignTheme 未包裹当前组合，无法读取设计令牌")
}

/**
 * 新增的原生界面主题入口。
 *
 * 不修改现有 `TanukiTheme` 与业务页面；页面迁移时可逐步用本主题包裹，再替换
 * 硬编码色值和尺寸。默认跟随系统深色和系统动画缩放。
 */
@Composable
internal fun TanukiDesignTheme(
    dark: Boolean = isSystemInDarkTheme(),
    reducedMotion: Boolean = rememberTanukiReducedMotion(),
    content: @Composable () -> Unit,
) {
    val tokens = remember(dark, reducedMotion) {
        TanukiDesignTokens(
            isDark = dark,
            reducedMotion = reducedMotion,
            colors = if (dark) TanukiColorTokens.Dark else TanukiColorTokens.Light,
            type = buildTextStyles(),
        )
    }
    CompositionLocalProvider(LocalTanukiDesignTokens provides tokens) {
        MaterialTheme(
            colorScheme = tokens.colors.toMaterialColorScheme(),
            typography = tokens.type.toMaterialTypography(),
            shapes = TanukiShapeTokens.material,
            content = content,
        )
    }
}

/** 系统关闭动画时返回 true；组件据此把动效时长压到 0 并停用循环动画。 */
@Composable
internal fun rememberTanukiReducedMotion(): Boolean {
    val context = LocalContext.current
    val source = remember(context.applicationContext) { TanukiMotionSettings.get(context) }
    val reduced by source.reducedMotion.collectAsState()
    return reduced
}

/**
 * 原生 View 读取同一套调色板的入口。
 *
 * 浏览器悬浮层等非 Compose 界面用它在浅色/深色之间切换，保证和根主题
 * 使用同一份色值，而不是各自维护一套十六进制颜色。
 */
internal fun tanukiDesignColorScheme(dark: Boolean): ColorScheme =
    (if (dark) TanukiColorTokens.Dark else TanukiColorTokens.Light).toMaterialColorScheme()

private fun buildTextStyles(): TanukiTextStyles {
    fun style(token: TanukiTypeToken): TextStyle = TextStyle(
        fontSize = token.fontSizeSp.sp,
        lineHeight = token.lineHeightSp.sp,
        fontWeight = token.weight.toFontWeight(),
        letterSpacing = token.trackingEm.em,
        fontFamily = when (token.family) {
            TanukiFontToken.System -> FontFamily.SansSerif
            TanukiFontToken.Monospace -> FontFamily.Monospace
        },
    )

    fun style(role: TanukiTypeRole, size: TanukiTypeSize): TextStyle =
        style(TanukiTypeScale.token(role, size))

    return TanukiTextStyles(
        titleLarge = style(TanukiTypeRole.Title, TanukiTypeSize.Large),
        titleMedium = style(TanukiTypeRole.Title, TanukiTypeSize.Medium),
        titleSmall = style(TanukiTypeRole.Title, TanukiTypeSize.Small),
        bodyLarge = style(TanukiTypeRole.Body, TanukiTypeSize.Large),
        bodyMedium = style(TanukiTypeRole.Body, TanukiTypeSize.Medium),
        bodySmall = style(TanukiTypeRole.Body, TanukiTypeSize.Small),
        captionLarge = style(TanukiTypeRole.Caption, TanukiTypeSize.Large),
        captionMedium = style(TanukiTypeRole.Caption, TanukiTypeSize.Medium),
        captionSmall = style(TanukiTypeRole.Caption, TanukiTypeSize.Small),
        codeLarge = style(TanukiTypeRole.Code, TanukiTypeSize.Large),
        codeMedium = style(TanukiTypeRole.Code, TanukiTypeSize.Medium),
        codeSmall = style(TanukiTypeRole.Code, TanukiTypeSize.Small),
    )
}

private fun Int.toFontWeight(): FontWeight = when (this) {
    400 -> FontWeight.Normal
    500 -> FontWeight.Medium
    600 -> FontWeight.SemiBold
    700 -> FontWeight.Bold
    else -> FontWeight.Normal
}

private fun TanukiTextStyles.toMaterialTypography(): Typography = Typography(
    displayLarge = titleLarge,
    displayMedium = titleMedium,
    displaySmall = titleSmall,
    headlineLarge = titleLarge,
    headlineMedium = titleMedium,
    headlineSmall = titleSmall,
    titleLarge = titleMedium,
    titleMedium = titleSmall,
    titleSmall = captionLarge,
    bodyLarge = bodyLarge,
    bodyMedium = bodyMedium,
    bodySmall = bodySmall,
    labelLarge = captionLarge,
    labelMedium = captionMedium,
    labelSmall = captionSmall,
)

private fun TanukiColorTokens.toMaterialColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = Color(primary),
        onPrimary = Color(if (isDark) canvas else onPrimary),
        primaryContainer = Color(primaryContainer),
        onPrimaryContainer = Color(onPrimaryContainer),
        secondary = Color(textSecondary),
        onSecondary = Color(surface),
        secondaryContainer = Color(surfaceMuted),
        onSecondaryContainer = Color(textPrimary),
        tertiary = Color(info),
        onTertiary = Color(onInfo),
        tertiaryContainer = Color(infoContainer),
        onTertiaryContainer = Color(onInfoContainer),
        error = Color(if (isDark) danger else onDangerContainer),
        onError = Color(onDanger),
        errorContainer = Color(dangerContainer),
        onErrorContainer = Color(onDangerContainer),
        background = Color(canvas),
        onBackground = Color(textPrimary),
        surface = Color(surface),
        onSurface = Color(textPrimary),
        surfaceVariant = Color(surfaceMuted),
        onSurfaceVariant = Color(textSecondary),
        surfaceTint = Color.Transparent,
        inverseSurface = Color(textPrimary),
        inverseOnSurface = Color(surface),
        inversePrimary = Color(primaryContainer),
        outline = Color(outlineStrong),
        outlineVariant = Color(outline),
        scrim = Color(scrim),
        surfaceDim = Color(surfaceSunken),
        surfaceBright = Color(surfaceRaised),
        surfaceContainerLowest = Color(surfaceSunken),
        surfaceContainerLow = Color(surface),
        surfaceContainer = Color(surfaceRaised),
        surfaceContainerHigh = Color(surfaceMuted),
        surfaceContainerHighest = Color(surfaceMuted),
    )
}
