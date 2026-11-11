package com.tanuki.ui.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 设计令牌的纯 JVM 回归。
 *
 * 不启动 Android、不读取资源、不依赖 Compose 运行时；颜色生成、对比度、
 * 数值范围和四层玻璃的模糊策略都在这里锁定。
 */
class TanukiDesignTokensTest {

    @Test
    fun `OKLCH 端点接近纯白与纯黑`() {
        val white = TanukiColorMath.oklchToRgb(1.0, 0.0, 0.0)
        val black = TanukiColorMath.oklchToRgb(0.0, 0.0, 0.0)
        assertTrue(white.red >= 254 && white.green >= 254 && white.blue >= 254)
        assertTrue(black.red <= 1 && black.green <= 1 && black.blue <= 1)
    }

    @Test
    fun `OKLCH 已知红色落在 sRGB 红色范围`() {
        val red = TanukiColorMath.oklchToRgb(0.6279, 0.2577, 29.23)
        assertTrue("红通道应接近 255：$red", red.red >= 250)
        assertTrue("绿通道应接近 0：$red", red.green <= 10)
        assertTrue("蓝通道应接近 0：$red", red.blue <= 10)
    }

    @Test
    fun `极端彩度会被映射到合法 sRGB`() {
        val hues = listOf(0.0, 27.0, 75.0, 110.0, 150.0, 245.0, 320.0)
        for (hue in hues) {
            for (lightness in listOf(0.1, 0.3, 0.5, 0.7, 0.9)) {
                val rgb = TanukiColorMath.oklchToRgb(lightness, 0.5, hue)
                assertTrue(rgb.red in 0..255)
                assertTrue(rgb.green in 0..255)
                assertTrue(rgb.blue in 0..255)
            }
        }
    }

    @Test
    fun `黑白对比度为 21 且同色对比度为 1`() {
        val black = TanukiRgb(0, 0, 0)
        val white = TanukiRgb(255, 255, 255)
        assertEquals(21.0, black.contrastWith(white), 0.01)
        assertEquals(1.0, black.contrastWith(black), 0.0001)
    }

    @Test
    fun `ARGB 往返保持一致`() {
        val rgb = TanukiRgb(1, 2, 3)
        assertEquals(0xFF010203L, rgb.toArgb())
        assertEquals(0x80010203L, rgb.toArgb(alpha = 0x80))
        assertEquals(rgb, TanukiColorMath.fromArgb(0x80010203L))
    }

    @Test
    fun `浅色和深色主题的对比度审计全部通过`() {
        for (tokens in listOf(TanukiColorTokens.Light, TanukiColorTokens.Dark)) {
            val failures = TanukiContrastAudit.failures(tokens)
            assertTrue(
                "${if (tokens.isDark) "深色" else "浅色"}主题存在对比度不足：${
                    failures.joinToString { "${it.name}=${"%.2f".format(it.actualRatio)}<${it.minimum}" }
                }",
                failures.isEmpty(),
            )
            for (check in TanukiContrastAudit.checks(tokens)) {
                assertTrue("${check.name} 对比度非有限值", check.actualRatio.isFinite())
                assertTrue(
                    "${check.name} 最低要求 ${check.minimum}，实际 ${"%.2f".format(check.actualRatio)}",
                    check.passes,
                )
            }
        }
    }

    @Test
    fun `正文不低于 4_5 比 1 大字不低于 3 比 1`() {
        for (tokens in listOf(TanukiColorTokens.Light, TanukiColorTokens.Dark)) {
            assertTrue(
                "正文/画布",
                TanukiColorMath.contrastRatio(tokens.textPrimary, tokens.canvas) >= 4.5,
            )
            assertTrue(
                "次级正文/画布",
                TanukiColorMath.contrastRatio(tokens.textSecondary, tokens.canvas) >= 4.5,
            )
            assertTrue(
                "三级正文/画布",
                TanukiColorMath.contrastRatio(tokens.textTertiary, tokens.canvas) >= 4.5,
            )
            // 禁用与装饰不承担正文信息，不纳入可读文字阈值。
            assertNotEquals(tokens.textTertiary, tokens.textDisabled)
        }
    }

    @Test
    fun `语义实心色与前景色满足正文对比度`() {
        for (tokens in listOf(TanukiColorTokens.Light, TanukiColorTokens.Dark)) {
            val pairs = listOf(
                "成功" to (tokens.onSuccess to tokens.success),
                "警告" to (tokens.onWarning to tokens.warning),
                "危险" to (tokens.onDanger to tokens.danger),
                "信息" to (tokens.onInfo to tokens.info),
                "成功容器" to (tokens.onSuccessContainer to tokens.successContainer),
                "警告容器" to (tokens.onWarningContainer to tokens.warningContainer),
                "危险容器" to (tokens.onDangerContainer to tokens.dangerContainer),
                "信息容器" to (tokens.onInfoContainer to tokens.infoContainer),
            )
            for ((label, pair) in pairs) {
                val ratio = TanukiColorMath.contrastRatio(pair.first, pair.second)
                assertTrue("$label 对比度 $ratio 低于 4.5", ratio >= 4.5)
            }
        }
    }

    @Test
    fun `中性色与四类语义色使用同一明度阶梯`() {
        val palette = TanukiTonePalette.standard()
        assertEquals(TanukiColorFamily.entries.size, palette.scales.size)
        for (family in TanukiColorFamily.entries) {
            val scale = palette[family]
            assertEquals(family, scale.family)
            assertEquals(TanukiToneLadder.values.size, scale.tones.size)
            assertEquals(TanukiToneLadder.values.size, TanukiToneLadder.indices.count())
        }
    }

    @Test
    fun `浅色与深色主题是两套角色令牌`() {
        assertNotEquals(TanukiColorTokens.Light.canvas, TanukiColorTokens.Dark.canvas)
        assertNotEquals(TanukiColorTokens.Light.surface, TanukiColorTokens.Dark.surface)
        assertNotEquals(TanukiColorTokens.Light.textPrimary, TanukiColorTokens.Dark.textPrimary)
        assertNotEquals(TanukiColorTokens.Light.primary, TanukiColorTokens.Dark.primary)
        assertFalse(TanukiColorTokens.Light.isDark)
        assertTrue(TanukiColorTokens.Dark.isDark)
    }

    @Test
    fun `纯白灰与天蓝浅深方案取值固定`() {
        val light = TanukiColorTokens.Light
        val dark = TanukiColorTokens.Dark
        assertEquals(0xFFFFFFFFL, light.canvas)
        assertEquals(0xFFF5F6F7L, light.surfaceMuted)
        assertEquals(0xFFFFFFFFL, light.surface)
        assertEquals(0xFFECEDEFL, light.outline)
        assertEquals(0xFF111315L, light.textPrimary)
        assertEquals(0xFF5A6068L, light.textSecondary)
        assertEquals(0xFF6B7280L, light.textTertiary)
        assertEquals(0xFF8C929AL, light.textDisabled)
        assertEquals(0xFF0EA5E9L, light.accent)
        assertEquals(0xFFE6F4FEL, light.primaryContainer)
        assertEquals(0xFF0E1012L, dark.canvas)
        assertEquals(0xFF16181BL, dark.surfaceMuted)
        assertEquals(0xFF1A1D21L, dark.surface)
        assertEquals(0xFF24282DL, dark.outline)
        assertEquals(0xFFF2F4F6L, dark.textPrimary)
        assertEquals(0xFFA8AFB8L, dark.textSecondary)
        assertEquals(0xFF9AA1AAL, dark.textTertiary)
        assertEquals(0xFF6F767FL, dark.textDisabled)
        assertEquals(0xFF38BDF8L, dark.accent)
        assertEquals(0xFF0B2B3AL, dark.primaryContainer)
        assertEquals(0xFF7DD3FCL, dark.primary)
        assertNotEquals(light.success, dark.success)
        assertNotEquals(light.warning, dark.warning)
        assertNotEquals(light.danger, dark.danger)
    }

    @Test
    fun `装饰天蓝不能当白字按钮或浅色正文`() {
        for (tokens in listOf(TanukiColorTokens.Light, TanukiColorTokens.Dark)) {
            assertTrue(TanukiColorMath.contrastRatio(tokens.onPrimary, tokens.accent) < 3.0)
            assertNotEquals(tokens.primaryFill, tokens.accent)
            assertNotEquals(tokens.primary, tokens.accent)
            assertEquals(0xFF0369A1L, tokens.primaryFill)
            assertEquals(0xFFFFFFFFL, tokens.onPrimary)
            assertNotEquals(tokens.textTertiary, tokens.textDisabled)
            assertTrue(TanukiContrastAudit.checks(tokens).none { it.foreground == tokens.textDisabled })
        }
    }

    @Test
    fun `每种启用按钮语气在两种主题下都可读`() {
        for (tokens in listOf(TanukiColorTokens.Light, TanukiColorTokens.Dark)) {
            for (tone in TanukiPillButtonTone.entries) {
                val colors = tanukiPillButtonColors(tokens, tone, enabled = true)
                val background = if (tone == TanukiPillButtonTone.Ghost) tokens.surface else colors.background
                assertTrue("$tone 按钮正文", TanukiColorMath.contrastRatio(colors.content, background) >= 4.5)
                if (tone == TanukiPillButtonTone.Outline || tone == TanukiPillButtonTone.Tonal) {
                    assertEquals(tokens.surface, colors.background)
                    assertEquals(tokens.textPrimary, colors.content)
                    assertEquals(tokens.outline, colors.border)
                }
            }
            assertEquals(0, TanukiElevation.card.shadowDp)
            assertEquals(0.0, TanukiElevation.card.shadowAlpha, 0.0)
        }
    }

    @Test
    fun `白底说明与分组说明按实际背景选择合格颜色`() {
        val light = TanukiColorTokens.Light
        assertEquals(light.textTertiary, light.descriptionOn(light.surface))
        assertTrue(TanukiColorMath.contrastRatio(light.textTertiary, light.surfaceMuted) < 4.5)
        assertEquals(light.textSecondary, light.descriptionOn(light.surfaceMuted))
        assertEquals(light.textSecondary, light.descriptionOn(light.primaryContainer))
        for (tokens in listOf(light, TanukiColorTokens.Dark)) {
            for (bg in listOf(tokens.canvas, tokens.surface, tokens.surfaceMuted, tokens.primaryContainer, tokens.sheet)) {
                assertTrue(TanukiColorMath.contrastRatio(tokens.descriptionOn(bg), bg) >= 4.5)
                assertTrue(TanukiColorMath.contrastRatio(tokens.accentIcon, bg) >= 3.0)
            }
        }
    }

    @Test
    fun `间距以 4dp 为基准且触控区至少 48dp`() {
        assertEquals(4, TanukiSpace.BASE_DP)
        assertEquals(48, TanukiSpace.TOUCH_TARGET_MIN_DP)
        assertTrue(TanukiSpace.LIST_ROW_MIN_DP >= TanukiSpace.TOUCH_TARGET_MIN_DP)
        for ((name, value) in TanukiSpace.values) {
            assertEquals("间距 $name 不是 4dp 的倍数", 0, value % TanukiSpace.BASE_DP)
        }
        assertEquals(
            TanukiSpace.values.size,
            TanukiSpace.values.keys.distinct().size,
        )
    }

    @Test
    fun `圆角阶梯为胶囊大于弹层大于卡片`() {
        assertTrue(TanukiRadius.CARD_DP < TanukiRadius.SHEET_DP)
        assertTrue(TanukiRadius.SHEET_DP <= TanukiRadius.CHROME_DP)
        assertEquals(50, TanukiRadius.PILL_PERCENT)
        assertEquals(5, TanukiRadius.values.size)
    }

    @Test
    fun `四档字体完整且等宽只用于代码`() {
        assertEquals(4, TanukiTypeRole.entries.size)
        for (role in TanukiTypeRole.entries) {
            val tokens = TanukiTypeScale.byRole(role)
            assertEquals("$role 应有三档", 3, tokens.size)
            assertTrue(tokens.all { it.fontSizeSp > 0 && it.lineHeightSp >= it.fontSizeSp })
        }
        val neutral = TanukiTonePalette.standard()[TanukiColorFamily.Neutral][1]
        assertEquals(neutral.red, neutral.green)
        assertEquals(neutral.green, neutral.blue)
        val monospace = TanukiTypeScale.tokens.filter { it.family == TanukiFontToken.Monospace }
        assertTrue(monospace.isNotEmpty())
        assertTrue("等宽字体只能用于代码", monospace.all { it.role == TanukiTypeRole.Code })
        assertTrue(
            "代码档必须全部使用等宽字体",
            TanukiTypeScale.byRole(TanukiTypeRole.Code).all { it.family == TanukiFontToken.Monospace },
        )
    }

    @Test
    fun `动效时长满足反馈结构与环境三档约束`() {
        assertEquals(160, TanukiMotion.FEEDBACK_MAX_MS)
        assertEquals(240, TanukiMotion.STRUCTURAL_MIN_MS)
        assertEquals(340, TanukiMotion.STRUCTURAL_MAX_MS)
        assertEquals(2400, TanukiMotion.AMBIENT_MS)
        for (token in TanukiMotion.tokens) {
            when (token.kind) {
                TanukiMotionKind.Feedback -> assertTrue(
                    "${token.name} 反馈时长 ${token.durationMs} 超过 160ms",
                    token.durationMs in 1..TanukiMotion.FEEDBACK_MAX_MS,
                )
                TanukiMotionKind.Structural -> assertTrue(
                    "${token.name} 结构时长 ${token.durationMs} 不在 240–340ms",
                    token.durationMs in TanukiMotion.STRUCTURAL_MIN_MS..TanukiMotion.STRUCTURAL_MAX_MS,
                )
                TanukiMotionKind.Ambient -> assertTrue(
                    "${token.name} 环境时长 ${token.durationMs} 不接近 2.4s",
                    token.durationMs in 2280..2520,
                )
            }
            assertEquals(
                "reduced-motion 必须为 0：${token.name}",
                0,
                TanukiMotion.durationMs(token, reducedMotion = true),
            )
            assertEquals(token.durationMs, TanukiMotion.durationMs(token, reducedMotion = false))
        }
    }

    @Test
    fun `海拔四层顺序固定且只有 chrome 与 pill 模糊`() {
        assertEquals(4, TanukiElevation.all.size)
        assertEquals(
            listOf(
                TanukiSurfaceLayer.Card,
                TanukiSurfaceLayer.Pill,
                TanukiSurfaceLayer.Sheet,
                TanukiSurfaceLayer.Chrome,
            ),
            TanukiElevation.all.map { it.layer },
        )
        val zIndices = TanukiElevation.all.map { it.zIndex }
        assertEquals(zIndices.size, zIndices.distinct().size)
        assertEquals(zIndices.sorted(), zIndices)
        assertTrue(TanukiElevation.chrome.blurEnabled)
        assertTrue(TanukiElevation.pill.blurEnabled)
        assertFalse(TanukiElevation.sheet.blurEnabled)
        assertFalse(TanukiElevation.card.blurEnabled)
        assertTrue(TanukiElevation.chrome.zIndex > TanukiElevation.sheet.zIndex)
        assertTrue(TanukiElevation.sheet.zIndex > TanukiElevation.pill.zIndex)
        assertTrue(TanukiElevation.pill.zIndex > TanukiElevation.card.zIndex)
        for (spec in TanukiElevation.all) {
            assertTrue("${spec.layer} fillAlpha 越界", spec.fillAlpha in 0.0..1.0)
            assertTrue("${spec.layer} shadowAlpha 越界", spec.shadowAlpha in 0.0..1.0)
            assertTrue("${spec.layer} 阴影不能为负", spec.shadowDp >= 0)
            if (spec.blurEnabled) {
                assertTrue("${spec.layer} 开启模糊但半径非正", spec.blurRadiusDp > 0)
            } else {
                assertEquals("${spec.layer} 未开启模糊但半径非零", 0, spec.blurRadiusDp)
            }
        }
    }

    @Test
    fun `状态色调覆盖五种语义`() {
        for (tokens in listOf(TanukiColorTokens.Light, TanukiColorTokens.Dark)) {
            for (tone in TanukiStatusTone.entries) {
                val colors = tokens.statusColors(tone)
                assertTrue(colors.dot != 0L)
                assertTrue(colors.container != 0L)
                assertTrue(colors.content != 0L)
                val ratio = TanukiColorMath.contrastRatio(colors.content, colors.container)
                assertTrue("$tone 状态 chip 对比度 $ratio 低于 4.5", ratio >= 4.5)
            }
        }
    }

    @Test
    fun `对比度审计报告可读且覆盖两种主题`() {
        for (tokens in listOf(TanukiColorTokens.Light, TanukiColorTokens.Dark)) {
            val checks = TanukiContrastAudit.checks(tokens)
            assertEquals("每个主题应有 45 项用途审计", 45, checks.size)
            println("===== ${if (tokens.isDark) "深色" else "浅色"}主题对比度 =====")
            for (check in checks) {
                println(
                    "%-24s %6.2f : 1  (最低 %.1f)".format(
                        check.name,
                        check.actualRatio,
                        check.minimum,
                    ),
                )
            }
        }
    }

    @Test
    fun `令牌色值报告可读`() {
        for (tokens in listOf(TanukiColorTokens.Light, TanukiColorTokens.Dark)) {
            println("===== ${if (tokens.isDark) "深色" else "浅色"}主题令牌 =====")
            TanukiColorTokens::class.java.declaredFields
                .filter { it.type == Long::class.javaPrimitiveType }
                .sortedBy { it.name }
                .forEach { field ->
                    field.isAccessible = true
                    println("%-28s #%08X".format(field.name, field.getLong(tokens)))
                }
        }
    }

    @Test
    fun `胶囊按钮 leading 图标继承正确内容色`() {
        val states = listOf(
            Triple(TanukiPillButtonTone.Filled, true, 4.5),
            Triple(TanukiPillButtonTone.Danger, true, 4.5),
            Triple(TanukiPillButtonTone.Filled, false, 1.0),
            Triple(TanukiPillButtonTone.Danger, false, 1.0),
        )
        for (tokens in listOf(TanukiColorTokens.Light, TanukiColorTokens.Dark)) {
            for ((tone, enabled, minimum) in states) {
                val colors = tanukiPillButtonColors(tokens, tone, enabled)
                val label = "${if (tokens.isDark) "深色" else "浅色"}/$tone/enabled=$enabled"
                val expected = when {
                    !enabled -> tokens.textDisabled
                    tone == TanukiPillButtonTone.Danger -> tokens.onDanger
                    else -> tokens.onPrimary
                }
                assertEquals("$label 图标内容色", expected, colors.content)
                assertNotEquals("$label 不能沿用外层正文色", tokens.textPrimary, colors.content)
                val ratio = TanukiColorMath.contrastRatio(colors.content, colors.background)
                assertTrue("$label 图标对比度 $ratio 低于 $minimum", ratio >= minimum)
            }
        }
    }
}
