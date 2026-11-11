package com.tanuki

import com.tanuki.storage.FileLocation
import com.tanuki.ui.FileAccessPresentation
import com.tanuki.ui.TanukiContrast
import com.tanuki.ui.TanukiFileVisuals
import com.tanuki.ui.TanukiSessionBadgeKind
import com.tanuki.ui.TanukiSessionVisuals
import com.tanuki.ui.design.TanukiColorFamily
import com.tanuki.ui.design.TanukiColorTokens
import com.tanuki.ui.design.TanukiFontToken
import com.tanuki.ui.design.TanukiMotion
import com.tanuki.ui.design.TanukiMotionKind
import com.tanuki.ui.design.TanukiSpace
import com.tanuki.ui.design.TanukiStatusTone
import com.tanuki.ui.design.TanukiTypeRole
import com.tanuki.ui.design.TanukiTypeScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * 页面接入令牌后的纯 JVM 回归。
 *
 * 只锁定“状态 → 语义/文案/尺寸”和关键对比度，不启动 Android、不渲染 Compose；
 * 真正的截图自查仍需设备或独立视觉 QA。
 */
class TanukiPageVisualsTest {

    // ---- 令牌取值 ----

    @Test
    fun `列表行高与触控区不低于令牌下限`() {
        assertTrue("触控区下限 ${TanukiSpace.TOUCH_TARGET_MIN_DP}dp", TanukiSpace.TOUCH_TARGET_MIN_DP >= 48)
        assertTrue("列表行下限 ${TanukiSpace.LIST_ROW_MIN_DP}dp", TanukiSpace.LIST_ROW_MIN_DP >= 56)
        assertEquals(TanukiSpace.LIST_ROW_MIN_DP, TanukiFileVisuals.ROW_MIN_HEIGHT_DP)
        assertEquals(TanukiSpace.LIST_ROW_MIN_DP, TanukiSessionVisuals.ROW_MIN_HEIGHT_DP)
    }

    @Test
    fun `间距阶梯都是 4dp 的整数倍且严格递增`() {
        val values = TanukiSpace.values.values.toList()
        assertEquals("间距命名数量", 10, values.size)
        assertEquals(0, values.first())
        for ((index, value) in values.withIndex()) {
            assertEquals("第 $index 档间距必须是 4dp 的倍数", 0, value % TanukiSpace.BASE_DP)
            if (index > 0) assertTrue("间距必须递增：$values", value > values[index - 1])
        }
        assertEquals(TanukiSpace.GIANT_DP, values.last())
    }

    @Test
    fun `动效令牌满足反馈与结构区间且可被系统置零`() {
        for (token in TanukiMotion.tokens) {
            when (token.kind) {
                TanukiMotionKind.Feedback -> assertTrue(
                    "${token.name} 反馈时长 ${token.durationMs}ms 超过 160ms",
                    token.durationMs in 1..TanukiMotion.FEEDBACK_MAX_MS,
                )
                TanukiMotionKind.Structural -> assertTrue(
                    "${token.name} 结构时长 ${token.durationMs}ms 不在 240-340ms",
                    token.durationMs in TanukiMotion.STRUCTURAL_MIN_MS..TanukiMotion.STRUCTURAL_MAX_MS,
                )
                TanukiMotionKind.Ambient -> assertEquals(TanukiMotion.AMBIENT_MS, token.durationMs)
            }
            assertEquals("${token.name} 在移除动画时必须为 0", 0, TanukiMotion.durationMs(token, reducedMotion = true))
            assertEquals(token.durationMs, TanukiMotion.durationMs(token, reducedMotion = false))
        }
    }

    @Test
    fun `只有代码档使用等宽字体`() {
        for (token in TanukiTypeScale.tokens) {
            val expected = if (token.role == TanukiTypeRole.Code) TanukiFontToken.Monospace else TanukiFontToken.System
            assertEquals("${token.role}/${token.size} 字体族", expected, token.family)
            assertTrue("${token.role}/${token.size} 字号必须为正", token.fontSizeSp > 0)
            assertTrue("${token.role}/${token.size} 行高不能小于字号", token.lineHeightSp >= token.fontSizeSp)
        }
        // 每个角色三档，保证页面不会退回到散落字号。
        for (role in TanukiTypeRole.entries) {
            assertEquals("$role 档位数量", 3, TanukiTypeScale.byRole(role).size)
        }
        assertEquals(5, TanukiColorFamily.entries.size)
    }

    // ---- 对比度 ----

    @Test
    fun `状态 chip 在浅色和深色下都满足正文对比度`() {
        for (tokens in listOf(TanukiColorTokens.Light, TanukiColorTokens.Dark)) {
            val theme = if (tokens.isDark) "深色" else "浅色"
            for (tone in TanukiStatusTone.entries) {
                val role = tokens.statusColors(tone)
                assertTrue(
                    "$theme $tone 容器正文对比度 ${rate(role.content, role.container)}",
                    TanukiContrast.passesNormalText(role.content, role.container),
                )
                // 状态点作为图形元素，按大号文字阈值检查。
                assertTrue(
                    "$theme $tone 状态点对比度 ${rate(role.dot, tokens.canvas)}",
                    TanukiContrast.passesLargeText(role.dot, tokens.canvas),
                )
                assertNotEquals("$theme $tone 容器不能与画布同色", tokens.canvas, role.container)
            }
        }
    }

    @Test
    fun `卡片说明与路径文字在抬升表面上可读`() {
        for (tokens in listOf(TanukiColorTokens.Light, TanukiColorTokens.Dark)) {
            val theme = if (tokens.isDark) "深色" else "浅色"
            assertTrue("$theme 说明文字/卡片", TanukiContrast.passesNormalText(tokens.textSecondary, tokens.surfaceRaised))
            assertTrue("$theme 路径文字/卡片", TanukiContrast.passesNormalText(tokens.textTertiary, tokens.surfaceRaised))
            assertTrue("$theme 正文/分组底", TanukiContrast.passesNormalText(tokens.textPrimary, tokens.surfaceMuted))
            // 停用行文字属于大号/非关键说明，按 3.0 检查。
            assertTrue("$theme 停用文字/分组底", TanukiContrast.passesLargeText(tokens.textDisabled, tokens.surfaceMuted))
        }
    }

    // ---- 格式化与映射 ----

    @Test
    fun `文件大小文案区分未知大小与各单位`() {
        assertEquals("无法读取大小", TanukiFileVisuals.sizeText(-1))
        assertEquals("0 B", TanukiFileVisuals.sizeText(0))
        assertEquals("1023 B", TanukiFileVisuals.sizeText(1023))
        assertEquals("1.0 KiB", TanukiFileVisuals.sizeText(1024))
        assertEquals("1.5 MiB", TanukiFileVisuals.sizeText((1.5 * 1024 * 1024).toLong()))
        assertEquals("2.0 GiB", TanukiFileVisuals.sizeText(2L * 1024 * 1024 * 1024))
        assertEquals("1,5 KiB", TanukiFileVisuals.sizeText(1536, Locale.GERMANY))
        assertEquals("1.5 KiB", TanukiFileVisuals.sizeText(1536, Locale.ROOT))
    }

    @Test
    fun `传输标签不伪造未知百分比`() {
        assertEquals("25%", TanukiFileVisuals.transferLabel(25))
        assertEquals("0%", TanukiFileVisuals.transferLabel(0))
        assertEquals("传输中", TanukiFileVisuals.transferLabel(null))
    }

    @Test
    fun `文件空态文案覆盖搜索只有隐藏文件与只读位置`() {
        val searching = TanukiFileVisuals.emptyCopy(query = "hello", hasEntries = true, writable = true)
        assertEquals("暂无匹配文件", searching.title)
        assertEquals("清除搜索", searching.actionLabel)

        val hiddenOnly = TanukiFileVisuals.emptyCopy(query = "", hasEntries = true, writable = true)
        assertEquals("暂无可见文件", hiddenOnly.title)
        assertEquals("显示隐藏文件", hiddenOnly.actionLabel)

        val writableEmpty = TanukiFileVisuals.emptyCopy(query = "", hasEntries = false, writable = true)
        assertEquals("暂无文件", writableEmpty.title)
        assertTrue(writableEmpty.detail.contains("新建文件夹"))
        assertEquals("刷新", writableEmpty.actionLabel)

        val readOnlyEmpty = TanukiFileVisuals.emptyCopy(query = "", hasEntries = false, writable = false)
        assertEquals("暂无文件", readOnlyEmpty.title)
        assertEquals("此位置暂无内容。", readOnlyEmpty.detail)
        for (copy in listOf(searching, hiddenOnly, writableEmpty, readOnlyEmpty)) {
            assertTrue("空态标题不能为空", copy.title.isNotBlank())
            assertTrue("空态说明不能为空", copy.detail.isNotBlank())
        }
    }

    @Test
    fun `文件权限语气随授权状态变化`() {
        val writableWorkspace = FileAccessPresentation.state(
            FileLocation.WORKSPACE,
            runtimeRunning = false,
            phoneStorageGranted = false,
            phoneWriteAllowed = false,
            phoneWritingAvailable = false,
        )
        assertEquals(TanukiStatusTone.Success, TanukiFileVisuals.permissionTone(writableWorkspace))

        val runningWorkspace = FileAccessPresentation.state(
            FileLocation.WORKSPACE,
            runtimeRunning = true,
            phoneStorageGranted = false,
            phoneWriteAllowed = false,
            phoneWritingAvailable = false,
        )
        assertEquals(TanukiStatusTone.Neutral, TanukiFileVisuals.permissionTone(runningWorkspace))

        val notGrantedPhone = FileAccessPresentation.state(
            FileLocation.PHONE,
            runtimeRunning = true,
            phoneStorageGranted = false,
            phoneWriteAllowed = false,
            phoneWritingAvailable = false,
        )
        assertEquals(TanukiStatusTone.Warning, TanukiFileVisuals.permissionTone(notGrantedPhone))

        val grantablePhone = FileAccessPresentation.state(
            FileLocation.PHONE,
            runtimeRunning = true,
            phoneStorageGranted = true,
            phoneWriteAllowed = false,
            phoneWritingAvailable = true,
        )
        assertEquals(TanukiStatusTone.Info, TanukiFileVisuals.permissionTone(grantablePhone))

        val phoneWritable = FileAccessPresentation.state(
            FileLocation.PHONE,
            runtimeRunning = true,
            phoneStorageGranted = true,
            phoneWriteAllowed = true,
            phoneWritingAvailable = true,
        )
        assertEquals(TanukiStatusTone.Success, TanukiFileVisuals.permissionTone(phoneWritable))

        assertEquals("权限：可写，点击管理", TanukiFileVisuals.permissionDescription("可写"))
    }

    @Test
    fun `会话状态标签语气与呼吸只由语义决定`() {
        assertEquals(TanukiStatusTone.Success, TanukiSessionVisuals.badgeTone(TanukiSessionBadgeKind.RUNNING))
        assertEquals(TanukiStatusTone.Info, TanukiSessionVisuals.badgeTone(TanukiSessionBadgeKind.UNREAD))
        assertEquals(TanukiStatusTone.Danger, TanukiSessionVisuals.badgeTone(TanukiSessionBadgeKind.MIGRATION_REQUIRED))
        for (kind in TanukiSessionBadgeKind.entries) {
            assertEquals(
                "只有运行中允许呼吸：$kind",
                kind == TanukiSessionBadgeKind.RUNNING,
                TanukiSessionVisuals.badgePulse(kind),
            )
        }
        assertFalse(TanukiSessionVisuals.badgePulse(TanukiSessionBadgeKind.CURRENT))
    }

    @Test
    fun `会话权限语气覆盖官方预设与未知值`() {
        assertEquals(TanukiStatusTone.Neutral, TanukiSessionVisuals.permissionTone("read-only"))
        assertEquals(TanukiStatusTone.Warning, TanukiSessionVisuals.permissionTone("workspace-write"))
        assertEquals(TanukiStatusTone.Danger, TanukiSessionVisuals.permissionTone("danger-full-access"))
        assertEquals(TanukiStatusTone.Info, TanukiSessionVisuals.permissionTone("auto"))
        assertEquals(TanukiStatusTone.Info, TanukiSessionVisuals.permissionTone("custom"))
        assertEquals(TanukiStatusTone.Info, TanukiSessionVisuals.permissionTone("future-preset"))
        assertEquals(TanukiStatusTone.Neutral, TanukiSessionVisuals.permissionTone(null))
    }

    private fun rate(foreground: Long, background: Long): String =
        String.format(Locale.ROOT, "%.2f", TanukiContrast.ratio(foreground, background))
}
