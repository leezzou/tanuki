package com.tanuki.ui.design

import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import com.tanuki.ui.TanukiOverlayState
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** 覆盖弹层关闭、快速重开及单一动画所有者契约，不连接设备或业务运行时。 */
class TanukiOverlayMotionTest {
    @Test fun sheetAndScrimReachTargetsAtFrozenDurations() {
        val motion = TanukiMotionSpecs(false)
        for ((spec, duration) in listOf(
            motion.sheet<Float>(true) to 280,
            motion.sheet<Float>(false) to 240,
            motion.scrim<Float>() to 160,
        )) {
            val animation = TargetBasedAnimation(spec, Float.VectorConverter, 0f, 1f)
            assertEquals(duration * 1_000_000L, animation.durationNanos)
            assertFalse(animation.isFinishedFromNanos((duration - 1) * 1_000_000L))
            assertTrue(animation.isFinishedFromNanos(duration * 1_000_000L))
            assertEquals(1f, animation.getValueFromNanos(duration * 1_000_000L), 0f)
        }
        assertEquals(TanukiMotionEasing.enter, motion.sheet<Float>(true).easing)
        assertEquals(TanukiMotionEasing.exit, motion.sheet<Float>(false).easing)
        assertEquals(TanukiMotionEasing.standard, motion.scrim<Float>().easing)
    }

    @Test fun drawerReleaseFollowsDirectionVelocityAndHalfwayThreshold() {
        assertTrue(com.tanuki.ui.TanukiDrawerPolicy.releaseOpen(.2f, 500f))
        assertFalse(com.tanuki.ui.TanukiDrawerPolicy.releaseOpen(.8f, -500f))
        assertTrue(com.tanuki.ui.TanukiDrawerPolicy.releaseOpen(.6f, 0f))
        assertFalse(com.tanuki.ui.TanukiDrawerPolicy.releaseOpen(.4f, 0f))
    }

    @Test fun closingKeepsLastModelUntilExitCompletesAndReleasesIt() {
        var state = TanukiOverlayState<String>().update("首次内容").update("最后内容")
        state = state.update(null)
        assertFalse(state.open)
        assertEquals("最后内容", state.shown)
        assertEquals("最后内容", state.update(null).shown)
        state = state.hidden()
        assertNull(state.shown)
        assertFalse(state.open)
    }

    @Test fun oldExitCannotClearReopenedOrUpdatedContent() {
        val closing = TanukiOverlayState<String>().update("旧内容").update(null)
        val reopened = closing.update("新内容").hidden()
        assertTrue(reopened.open)
        assertEquals("新内容", reopened.shown)
        assertEquals("最新内容", reopened.update("最新内容").shown)
    }

    @Test fun reducedSheetAndScrimHaveNoIntermediateFrames() {
        val motion = TanukiMotionSpecs(true)
        for (spec in listOf(motion.sheet<Float>(true), motion.sheet<Float>(false), motion.scrim<Float>())) {
            val animation = TargetBasedAnimation(spec, Float.VectorConverter, 0f, 1f)
            assertEquals(0L, animation.durationNanos)
            assertEquals(1f, animation.getValueFromNanos(0L), 0f)
        }
    }

    @Test fun customSheetOwnsOnlyScrimAlphaAndSystemWindowHasNoOuterAnimation() {
        val root = listOf(File("src/main/kotlin/com/tanuki/ui"), File("app/src/main/kotlin/com/tanuki/ui"))
            .first { it.isDirectory }
        val sheet = File(root, "design/TanukiBottomSheet.kt").readText()
        // 每个方向只出现遮罩的一次淡化，面板只滑动，禁止高度补间。
        assertEquals(1, Regex("fadeIn\\(").findAll(sheet).count())
        assertEquals(1, Regex("fadeOut\\(").findAll(sheet).count())
        assertFalse(sheet.contains("animateContentSize"))
        assertFalse(sheet.contains("expandVertically"))
        val drawer = File(root, "TanukiDrawer.kt").readText()
        assertEquals(1, Regex("alpha =").findAll(drawer).count())
        assertFalse(drawer.contains("fadeIn("))
        assertTrue(drawer.contains("BackHandler(enabled = showing)"))
        assertTrue(drawer.contains("LocalTanukiDrawerOpen provides showing"))
        val system = File(root, "TanukiMotionFeedback.kt").readText()
            .substringAfter("internal fun <T : Any> TanukiModalSheet(").substringBefore("/** 固定键位")
        for (forbidden in listOf("fadeIn(", "fadeOut(", "scaleIn(", "scaleOut(", "AnimatedVisibility(")) {
            assertFalse("系统窗口不能叠加 $forbidden", system.contains(forbidden))
        }
    }
}
