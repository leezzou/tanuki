package com.tanuki.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tanuki.ui.design.TanukiDesign
import com.tanuki.ui.design.TanukiDesignTheme
import com.tanuki.ui.design.TanukiSpace
import com.tanuki.ui.design.tanukiDesignColorScheme

/**
 * 间距兼容入口。
 *
 * 数值直接取自 [TanukiSpace]，避免旧调用方与新页面各自维护一套阶梯。
 * 新代码优先使用 `TanukiDesign.tokens` 和 `TanukiSpace`，不要再新增常量。
 */
object TanukiSpacing {
    val xs = TanukiSpace.XS_DP.dp
    val sm = TanukiSpace.SM_DP.dp
    val md = TanukiSpace.MD_DP.dp
    val lg = TanukiSpace.LG_DP.dp
    val xl = TanukiSpace.XXL_DP.dp
    val xxl = TanukiSpace.XXXL_DP.dp
}

/**
 * 非 Compose 界面使用的调色板。
 *
 * 浏览器悬浮层等原生 View 通过它取得与根主题相同的颜色，浅色/深色跟随系统。
 */
internal fun tanukiColorScheme(dark: Boolean): ColorScheme = tanukiDesignColorScheme(dark)

/**
 * 停用内容的文字/图标颜色。
 *
 * 直接用令牌里的 textDisabled，而不是把 onSurface 调透明度：后者在深色和
 * 浅色下会得到不同的实际对比度，也不在对比度审计范围内。
 */
@Composable
internal fun tanukiDisabledContentColor(): Color = Color(TanukiDesign.tokens.colors.textDisabled)

/**
 * 应用根主题。
 *
 * 只保留一套颜色来源：直接使用 design 包的调色板、字体阶梯、圆角与动效令牌，
 * 同时安装 `LocalTanukiDesignTokens`，让 `com.tanuki.ui.design` 的基础组件
 * 能在页面里读取同一份令牌。深色/浅色默认跟随系统。
 */
@Composable
fun TanukiTheme(content: @Composable () -> Unit) {
    TanukiDesignTheme(dark = isSystemInDarkTheme(), content = content)
}
