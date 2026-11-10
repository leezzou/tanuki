package com.tanuki

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.tanuki.ui.TanukiContrast
import com.tanuki.ui.tanukiColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 主题关键前景/背景组合的对比度回归；不依赖设备。 */
class TanukiContrastTest {
    @Test fun blackOnWhiteIsTwentyOneToOne() {
        assertEquals(21.0, TanukiContrast.ratio(0xFF000000, 0xFFFFFFFF), 0.01)
        assertEquals(1.0, TanukiContrast.ratio(0xFF123456, 0xFF123456), 0.001)
    }

    @Test fun normalAndLargeTextThresholdsAreDistinct() {
        assertTrue(TanukiContrast.passesNormalText(0xFF000000, 0xFFFFFFFF))
        assertTrue(TanukiContrast.passesLargeText(0xFF777777, 0xFFFFFFFF))
        assertTrue(!TanukiContrast.passesNormalText(0xFF777777, 0xFFFFFFFF))
    }

    @Test fun lightThemeTextAndControlsAreReadable() = assertScheme(tanukiColorScheme(dark = false), "浅色")

    @Test fun darkThemeTextAndControlsAreReadable() = assertScheme(tanukiColorScheme(dark = true), "深色")

    private fun assertScheme(scheme: ColorScheme, label: String) {
        val pairs = listOf(
            "正文/背景" to (scheme.onBackground to scheme.background),
            "正文/卡片" to (scheme.onSurface to scheme.surface),
            "主按钮文字/主色" to (scheme.onPrimary to scheme.primary),
            "主容器文字/容器" to (scheme.onPrimaryContainer to scheme.primaryContainer),
            "次级容器文字/容器" to (scheme.onSecondaryContainer to scheme.secondaryContainer),
            "次要文字/表面" to (scheme.onSurfaceVariant to scheme.surfaceVariant),
            "错误容器文字/容器" to (scheme.onErrorContainer to scheme.errorContainer),
            "错误文字/背景" to (scheme.error to scheme.background),
        )
        for ((name, pair) in pairs) {
            val ratio = TanukiContrast.ratio(pair.first.argb(), pair.second.argb())
            assertTrue("$label 主题 $name 对比度不足：$ratio", ratio >= TanukiContrast.NORMAL_TEXT_MIN)
        }
    }

    private fun Color.argb(): Long = toArgb().toLong() and 0xFFFFFFFFL
}
