package com.tanuki.ui.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 锁定审计冻结的动效契约与即时降级，不依赖设备或动画时钟。 */
class TanukiMotionSpecsTest {
    private val normal = TanukiMotionSpecs(false)
    private val reduced = TanukiMotionSpecs(true)

    @Test fun durationsAndCompatibilityAliasesMatchFrozenContract() {
        assertEquals(listOf(0, 100, 140, 160, 240, 280, 300, 320, 340, 2400), listOf(
            TanukiMotion.instant, TanukiMotion.pressIn, TanukiMotion.pressOut, TanukiMotion.toggle,
            TanukiMotion.layout, TanukiMotion.sheetOpen, TanukiMotion.expand,
            TanukiMotion.page, TanukiMotion.pageMax, TanukiMotion.ambientPulse,
        ).map { it.durationMs })
        assertEquals(TanukiMotion.sheetDismiss.durationMs, TanukiMotion.layout.durationMs)
        assertEquals(10, TanukiMotion.tokens.size)
        assertEquals(1_500L, TanukiMotionSpecs.COPY_CONFIRMATION_MS)
    }

    @Test fun semanticFactoriesUseExplicitTimingAndCurves() {
        assertSpec(normal.toggle<Float>(), 160, TanukiMotionEasing.standard)
        assertSpec(normal.press<Float>(true), 100, TanukiMotionEasing.standard)
        assertSpec(normal.press<Float>(false), 140, TanukiMotionEasing.enter)
        assertSpec(normal.icon<Float>(true), 160, TanukiMotionEasing.enter)
        assertSpec(normal.icon<Float>(false), 100, TanukiMotionEasing.exit)
        assertSpec(normal.state<Float>(), 160, TanukiMotionEasing.standard)
        assertSpec(normal.expand<Float>(true), 240, TanukiMotionEasing.standard)
        assertSpec(normal.expand<Float>(true, longDetail = true), 300, TanukiMotionEasing.enter)
        assertSpec(normal.expand<Float>(false, longDetail = true), 240, TanukiMotionEasing.exit)
        assertSpec(normal.count<Float>(), 160, TanukiMotionEasing.enter)
        assertSpec(normal.copy<Float>(), 160, TanukiMotionEasing.standard)
        assertSpec(normal.progress<Float>(), 100, LinearEasing)
        val list = normal.list()
        assertEquals(240, (list.placement as TweenSpec<*>).durationMillis)
        assertEquals(160, (list.fadeIn as TweenSpec<*>).durationMillis)
        assertEquals(140, (list.fadeOut as TweenSpec<*>).durationMillis)
    }

    @Test fun curvesAndGestureSettleDoNotOvershoot() {
        for ((actual, expected) in listOf(
            TanukiMotionEasing.enter to CubicBezierEasing(.2f, 0f, 0f, 1f),
            TanukiMotionEasing.standard to CubicBezierEasing(.4f, 0f, .2f, 1f),
            TanukiMotionEasing.exit to CubicBezierEasing(.4f, 0f, 1f, 1f),
        )) for (step in 0..100) {
            val fraction = step / 100f
            assertEquals(expected.transform(fraction), actual.transform(fraction), .00001f)
            assertTrue(actual.transform(fraction) in 0f..1f)
        }
        val settle = normal.settle<Float>() as SpringSpec<*>
        assertEquals(1f, settle.dampingRatio, 0f)
        assertEquals(500f, settle.stiffness, 0f)
        assertEquals(.98f, normal.pressScale(true), 0f)
        assertEquals(1f, normal.pressScale(false), 0f)
        assertEquals(.92f, TanukiMotionSpecs.ICON_SCALE, 0f)
    }

    @Test fun reducedMotionMakesEveryFiniteFactoryImmediateAndDisablesListMotion() {
        for (spec in listOf(
            reduced.toggle<Float>(), reduced.press<Float>(true), reduced.press<Float>(false), reduced.icon<Float>(true),
            reduced.icon<Float>(false), reduced.state<Float>(), reduced.expand<Float>(true), reduced.expand<Float>(true, true),
            reduced.expand<Float>(false), reduced.count<Float>(), reduced.copy<Float>(), reduced.progress<Float>(),
        )) assertEquals(0, spec.durationMillis)
        assertTrue(reduced.settle<Float>() is SnapSpec<*>)
        assertNull(reduced.list().placement)
        assertNull(reduced.list().fadeIn)
        assertNull(reduced.list().fadeOut)
        assertEquals(1f, reduced.pressScale(true), 0f)
        for (token in TanukiMotion.tokens + listOf(TanukiMotion.instant, TanukiMotion.layout)) {
            assertEquals(0, reduced.duration(token))
        }
    }

    @Test fun sharedPreferencePublishesChangesAndForegroundRefreshWithoutDuplicates() = runBlocking {
        var setting = false
        val shared = TanukiReducedMotionState { setting }
        val compose = mutableListOf<Boolean>()
        val overlay = mutableListOf<Boolean>()
        val first = launch(Dispatchers.Unconfined) { shared.reducedMotion.collect { compose += it } }
        val second = launch(Dispatchers.Unconfined) { shared.reducedMotion.collect { overlay += it } }
        assertFalse(shared.reducedMotion.value)
        setting = true
        shared.refresh() // 模拟 ContentObserver 通知。
        shared.refresh() // 同值通知不会制造重复动画状态。
        assertTrue(shared.reducedMotion.value)
        setting = false
        shared.refresh() // 模拟回前台补读，修复后台期间遗漏的通知。
        assertEquals(listOf(false, true, false), compose)
        assertEquals(compose, overlay)
        first.cancel(); second.cancel()
    }

    private fun assertSpec(spec: TweenSpec<Float>, duration: Int, easing: androidx.compose.animation.core.Easing) {
        assertEquals(duration, spec.durationMillis)
        assertEquals(easing, spec.easing)
    }
}
