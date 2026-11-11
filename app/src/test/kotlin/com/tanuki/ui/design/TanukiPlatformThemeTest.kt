package com.tanuki.ui.design

import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** JVM 校验启动窗口、原生 View 与 Compose 共享配色，防止首帧退回旧主题。 */
class TanukiPlatformThemeTest {
    private val resources = File("src/main/res").takeIf { it.isDirectory }
        ?: File("app/src/main/res")

    private fun values(folder: String, file: String, tag: String): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(resources, "$folder/$file"))
        val nodes = document.getElementsByTagName(tag)
        return (0 until nodes.length).associate { index ->
            val node = nodes.item(index)
            node.attributes.getNamedItem("name").nodeValue to node.textContent
        }
    }

    @Test fun `启动窗口背景与当前令牌完全一致`() {
        for ((folder, tokens) in listOf("values" to TanukiColorTokens.Light, "values-night" to TanukiColorTokens.Dark)) {
            val colors = values(folder, "colors.xml", "color")
            assertEquals(TanukiColorMath.fromArgb(tokens.canvas).toHex(), colors["tanuki_canvas"])
            assertEquals(TanukiColorMath.fromArgb(tokens.primary).toHex(), colors["tanuki_link"])
        }
    }

    @Test fun `所有系统栏与原生背景引用同一资源并使用正确文字明暗`() {
        for (folder in listOf("values", "values-v27", "values-v29", "values-night", "values-night-v27", "values-night-v29")) {
            val items = values(folder, "themes.xml", "item")
            for (name in listOf("windowBackground", "colorBackground", "statusBarColor", "navigationBarColor")) {
                assertEquals("$folder/$name", "@color/tanuki_canvas", items["android:$name"])
            }
            val lightIcons = (!folder.contains("night")).toString()
            assertEquals(lightIcons, items["android:windowLightStatusBar"])
            // 导航栏深色图标从 API 27 起支持，基础资源不声明新 API 属性。
            if (folder.contains("-v")) assertEquals(lightIcons, items["android:windowLightNavigationBar"])
            else assertTrue("$folder 不应声明 API 27 属性", "android:windowLightNavigationBar" !in items)
        }
    }

    @Test fun `Material按钮选中控件与反色链接的实际配对可读`() {
        for (dark in listOf(false, true)) {
            val scheme = tanukiDesignColorScheme(dark)
            fun argb(color: androidx.compose.ui.graphics.Color): Long = color.toArgb().toLong() and 0xFFFFFFFFL
            for ((foreground, background) in listOf(
                scheme.onPrimary to scheme.primary,
                scheme.onPrimaryContainer to scheme.primaryContainer,
                scheme.onTertiary to scheme.tertiary,
                scheme.onTertiaryContainer to scheme.tertiaryContainer,
                scheme.inversePrimary to scheme.inverseSurface,
            )) {
                assertTrue("dark=$dark Material配对不足4.5:1",
                    TanukiColorMath.contrastRatio(argb(foreground), argb(background)) >= 4.5)
            }
            assertEquals(TanukiColorTokens.Light.primary,
                if (dark) argb(scheme.inversePrimary) else argb(scheme.primary))
        }
    }

    @Test fun `图标资源与强调及可读蓝角色一致`() {
        val colors = values("values", "colors.xml", "color")
        assertEquals(TanukiColorMath.fromArgb(TanukiColorTokens.Light.accent).toHex(), colors["tanuki_icon_accent"])
        assertEquals(TanukiColorMath.fromArgb(TanukiColorTokens.Light.primary).toHex(), colors["tanuki_icon_accent_text"])
    }

    @Test fun `深色表面避免纯白卡片和纯黑画布`() {
        val tokens = TanukiColorTokens.Dark
        for (color in listOf(tokens.canvas, tokens.surface, tokens.surfaceRaised, tokens.surfaceMuted, tokens.sheet)) {
            assertTrue(color != 0xFFFFFFFFL && color != 0xFF000000L)
        }
    }
}
