package com.tanuki.ui

import android.view.Window
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import com.tanuki.ui.design.TanukiDesign

/** 各Activity共用有效主题的系统栏策略；不改变全屏显隐和刘海布局。 */
@Suppress("DEPRECATION")
@Composable
internal fun TanukiSystemBars(window: Window) {
    val dark = TanukiDesign.tokens.isDark
    val background = MaterialTheme.colorScheme.background.toArgb()
    LaunchedEffect(window, dark, background) {
        window.statusBarColor = background
        window.navigationBarColor = background
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}
