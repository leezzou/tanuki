package com.tanuki

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import com.tanuki.ui.GlassNavigationBar
import com.tanuki.ui.TanukiGlyph
import com.tanuki.ui.TanukiIcon
import com.tanuki.ui.design.TanukiDesignTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

/** 只使用合成导航；真实硬件折射存在时，选中层不能移动、放大或裁掉前景字形。 */
@SdkSuppress(minSdkVersion = 33)
class GlassNavigationReadabilityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun darkLargeFontLabelsRemainCompleteInLtr() = assertForeground(LayoutDirection.Ltr)

    @Test fun darkLargeFontLabelsRemainCompleteInRtl() = assertForeground(LayoutDirection.Rtl)

    private fun assertForeground(direction: LayoutDirection) {
        val labels = listOf("工作台", "DSH", "文件", "浏览器")
        val glyphs = listOf(TanukiGlyph.Home, TanukiGlyph.Terminal, TanukiGlyph.Folder, TanukiGlyph.Browser)
        val selected = mutableIntStateOf(1)
        val centerClicks = AtomicInteger()
        val labelColors = AtomicReference<Pair<Int, Int>>()
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(
                LocalDensity provides Density(density, 2f),
                LocalLayoutDirection provides direction,
            ) {
                // 强制深色tokens，不修改系统设置；消费者不能回退到系统明暗来源。
                TanukiDesignTheme(dark = true) {
                    val colors = MaterialTheme.colorScheme
                    SideEffect { labelColors.set(colors.onSurfaceVariant.toArgb() to colors.primary.toArgb()) }
                    Box(Modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.BottomCenter) {
                        GlassNavigationBar(
                            selectedIndex = selected.intValue,
                            labels = labels,
                            onSelect = { selected.intValue = it },
                            backdrop = null,
                            modifier = Modifier.width(320.dp),
                            centerAction = {
                                Box(Modifier.size(48.dp).background(colors.primary, CircleShape)
                                    .clickable(role = Role.Button) { centerClicks.incrementAndGet() }
                                    .semantics { contentDescription = "合成中央操作" })
                            },
                        ) { index, tint -> TanukiIcon(glyphs[index], tint = tint) }
                    }
                }
            }
        }
        compose.waitForIdle()
        for (label in listOf("工作台", "浏览器")) {
            compose.onNodeWithText("DSH").performClick()
            compose.waitForIdle()
            val text = compose.onNodeWithText(label, useUnmergedTree = true)
            val layouts = mutableListOf<TextLayoutResult>()
            text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                assertTrue(action(layouts))
            }
            assertFalse("大字号标签不能省略或溢出：$label", layouts.single().hasVisualOverflow)
            val reference = text.captureToImage()
            val colors = requireNotNull(labelColors.get())

            val tab = compose.onNodeWithText(label)
            tab.performClick().assertIsSelected()
            compose.waitForIdle()
            val normal = text.captureToImage()
            assertGlyphsStayPut(reference, normal, colors.first, colors.second)
            compose.onAllNodes(isSelected()).assertCountEquals(1)
            labels.forEach { compose.onAllNodesWithText(it).assertCountEquals(1) }
            tab.assertIsDisplayed()

            var pressed = false
            try {
                tab.performTouchInput { down(center) }
                pressed = true
                compose.waitForIdle()
                assertGlyphsStayPut(normal, text.captureToImage(), colors.second, colors.second)
            } finally {
                if (pressed) tab.performTouchInput { up() }
            }
            assertEquals("页签操作不能触发中央按钮", 0, centerClicks.get())
        }
    }

    private fun assertGlyphsStayPut(reference: ImageBitmap, actual: ImageBitmap, referenceColor: Int, actualColor: Int) {
        assertEquals(reference.width, actual.width)
        assertEquals(reference.height, actual.height)
        val before = reference.toPixelMap()
        val after = actual.toPixelMap()
        var expected = 0
        var preserved = 0
        for (y in 0 until reference.height) for (x in 0 until reference.width) {
            if (near(before[x, y], referenceColor)) {
                expected++
                if (near(after[x, y], actualColor)) preserved++
            }
        }
        assertTrue("参考图必须含实际绘制的字形，不能用空图通过", expected > 20)
        // 只比较接近不透明墨色的像素，给边缘抗锯齿留量，不把背板变化当作字形移动。
        assertTrue("选中/按压不能折射或裁切字形：$preserved/$expected", preserved >= expected * 0.97)
    }

    private fun near(color: Color, expected: Int): Boolean {
        val actual = color.toArgb()
        return listOf(16, 8, 0).all { shift ->
            abs(((actual ushr shift) and 255) - ((expected ushr shift) and 255)) <= 4
        }
    }
}
