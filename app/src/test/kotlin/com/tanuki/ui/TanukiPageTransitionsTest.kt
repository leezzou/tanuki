package com.tanuki.ui

import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.ui.unit.LayoutDirection
import com.tanuki.ui.design.TanukiMotionEasing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 使用真实 Compose 时间采样锁定方向、时长和即时终态；不冒充设备渲染。 */
class TanukiPageTransitionsTest {
    @Test fun everyPeerDestinationPairOnlyFadesFor160ms() {
        for (from in 0..3) for (to in 0..3) if (from != to) {
            val navigation = tanukiPageNavigation(TanukiPageRoute(from), TanukiPageRoute(to))
            assertEquals(TanukiPageNavigation.Peer, navigation)
            for (direction in LayoutDirection.entries) {
                val policy = tanukiPageMotion(navigation, false, direction)
                assertEquals(160, policy.durationMs)
                assertEquals(TanukiMotionEasing.standard, policy.spec.easing)
                val animation = animation(policy)
                assertEquals(160_000_000L, animation.durationNanos)
                for (millis in 0..160 step 8) {
                    val progress = animation.getValueFromNanos(millis * 1_000_000L)
                    assertEquals(0f, policy.offsetDp(progress), 0f)
                    assertTrue(policy.alpha(progress) in 0f..1f)
                }
                assertEquals(0f, policy.alpha(animation.getValueFromNanos(0)), 0f)
                assertEquals(1f, policy.alpha(animation.getValueFromNanos(animation.durationNanos)), 0f)
            }
        }
    }

    @Test fun hierarchyUsesEnterAndExitCurvesWithReciprocalDirections() {
        for (direction in LayoutDirection.entries) {
            val forward = tanukiPageMotion(TanukiPageNavigation.Forward, false, direction)
            val back = tanukiPageMotion(TanukiPageNavigation.Back, false, direction)
            assertEquals(320, forward.durationMs)
            assertEquals(240, back.durationMs)
            assertEquals(TanukiMotionEasing.enter, forward.spec.easing)
            assertEquals(TanukiMotionEasing.exit, back.spec.easing)
            assertEquals(if (direction == LayoutDirection.Ltr) 16f else -16f, forward.offsetDp(0f), 0f)
            assertEquals(-forward.offsetDp(0f), back.offsetDp(0f), 0f)
            for (policy in listOf(forward, back)) {
                val animation = animation(policy)
                var lastDistance = 16f
                for (millis in 0..policy.durationMs step 8) {
                    val distance = kotlin.math.abs(policy.offsetDp(animation.getValueFromNanos(millis * 1_000_000L)))
                    assertTrue("位移不能反弹或越过目标", distance <= lastDistance + .0001f)
                    lastDistance = distance
                }
                assertEquals(0f, policy.offsetDp(animation.getValueFromNanos(animation.durationNanos)), 0f)
            }
        }
    }

    @Test fun rtlMirrorsBothHierarchyDirectionsAndNeverChangesAlpha() {
        for (kind in listOf(TanukiPageNavigation.Forward, TanukiPageNavigation.Back)) {
            val ltr = tanukiPageMotion(kind, false, LayoutDirection.Ltr)
            val rtl = tanukiPageMotion(kind, false, LayoutDirection.Rtl)
            for (millis in 0..ltr.durationMs step 16) {
                val progress = animation(ltr).getValueFromNanos(millis * 1_000_000L)
                assertEquals(-ltr.offsetDp(progress), rtl.offsetDp(progress), 0f)
                assertEquals(ltr.alpha(progress), rtl.alpha(progress), 0f)
            }
        }
    }

    @Test fun reducedMotionHasItsFinalValueAtTimeZeroForEveryRoute() {
        for (kind in TanukiPageNavigation.entries) for (direction in LayoutDirection.entries) {
            val policy = tanukiPageMotion(kind, true, direction)
            val animation = animation(policy)
            assertEquals(0, policy.durationMs)
            assertEquals(0L, animation.durationNanos)
            assertEquals(1f, policy.alpha(animation.getValueFromNanos(0)), 0f)
            assertEquals(0f, policy.offsetDp(animation.getValueFromNanos(0)), 0f)
        }
    }

    @Test fun settingsDepthDoesNotInventANavigationStack() {
        val home = TanukiPageRoute(0)
        val settings = TanukiPageRoute(4)
        val remote = TanukiPageRoute(4, "远程主机")
        val storage = TanukiPageRoute(4, "存储与空间")
        assertEquals(TanukiPageNavigation.Forward, tanukiPageNavigation(home, settings))
        assertEquals(TanukiPageNavigation.Forward, tanukiPageNavigation(settings, remote))
        assertEquals(TanukiPageNavigation.Back, tanukiPageNavigation(remote, settings))
        assertEquals(TanukiPageNavigation.Back, tanukiPageNavigation(settings, home))
        assertEquals(TanukiPageNavigation.Back, tanukiPageNavigation(storage, TanukiPageRoute(2)))
        assertEquals(TanukiPageNavigation.Peer, tanukiPageNavigation(remote, storage))
        assertEquals(TanukiPageNavigation.Instant, tanukiPageNavigation(remote, remote))
    }

    @Test fun webViewNeverReceivesHierarchyDisplacement() {
        for (kind in TanukiPageNavigation.entries) for (direction in LayoutDirection.entries) {
            val policy = tanukiPageMotion(kind, false, direction, webView = true)
            for (step in 0..10) assertEquals(0f, policy.offsetDp(step / 10f), 0f)
        }
    }

    @Test fun fileDetailsUseStableIdentityAndReturnWithoutChangingDirectorySemantics() {
        val document = "workspace:WORKSPACE:project/readme.md"
        assertEquals(TanukiPageNavigation.Forward, tanukiDetailNavigation(null, document))
        assertEquals(TanukiPageNavigation.Back, tanukiDetailNavigation(document, null))
        assertEquals(TanukiPageNavigation.Instant, tanukiDetailNavigation(document, document))
        assertEquals(TanukiPageNavigation.Instant, tanukiDetailNavigation(null, null))
        assertEquals(TanukiPageNavigation.Instant, tanukiDetailNavigation(null, document, restoring = true))
        assertEquals(TanukiPageNavigation.Forward, tanukiDetailNavigation(null, document, restoring = false))
        assertEquals(TanukiPageNavigation.Peer, tanukiDetailNavigation(document, "content://example.test/file"))
    }

    @Test fun browserDirectionFollowsExistingBackTargetsInsteadOfInventingParents() {
        val nativePages = listOf("页面总览", "浏览器设置", "历史记录", "收藏夹", "下载与截图")
        for (page in nativePages) {
            assertEquals(TanukiPageNavigation.Forward, tanukiBrowserPageNavigation(null, page))
            assertEquals(TanukiPageNavigation.Back, tanukiBrowserPageNavigation(page, null))
            assertEquals(TanukiPageNavigation.Instant, tanukiBrowserPageNavigation(page, page))
        }
        for (page in listOf("通用", "权限与后台", "数据管理", "关于浏览器")) {
            assertEquals(TanukiPageNavigation.Forward, tanukiBrowserPageNavigation("浏览器设置", page))
            assertEquals(TanukiPageNavigation.Back, tanukiBrowserPageNavigation(page, "浏览器设置"))
        }
        for (page in listOf("历史记录", "收藏夹", "下载与截图")) {
            assertEquals(TanukiPageNavigation.Forward, tanukiBrowserPageNavigation("数据管理", page))
            // 原 back() 直接返回网页；动效不能擅自返回“数据管理”。
            assertEquals(TanukiPageNavigation.Back, tanukiBrowserPageNavigation(page, null))
        }
    }

    private fun animation(policy: TanukiPageMotion) = TargetBasedAnimation(
        animationSpec = policy.spec,
        typeConverter = Float.VectorConverter,
        initialValue = 0f,
        targetValue = 1f,
    )
}
