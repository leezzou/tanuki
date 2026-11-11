package com.tanuki

import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import com.tanuki.ui.ViewerViewport
import com.tanuki.ui.design.TanukiMotionSpecs
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** 宿主回归验证视口几何与插值；Surface 连续性仍须独立设备 QA。 */
class FileViewerMotionTest {
    @Test fun fullscreenCommitsOnlyFinalViewportThroughoutChromeFade() {
        val normal = ViewerViewport.contentHeight(800, 56, 49, false)
        assertEquals(695, normal)
        for (retainedHeader in listOf(56, 40, 12, 0)) {
            for (retainedFooter in listOf(49, 20, 0)) {
                assertEquals("保留或卸载退出 chrome 均不能再次提交正文高度", 800,
                    ViewerViewport.contentHeight(800, retainedHeader, retainedFooter, true))
            }
        }
        assertEquals(normal, ViewerViewport.contentHeight(800, 56, 49, false))
        assertEquals(0, ViewerViewport.contentHeight(20, 56, 49, false))
    }

    private fun value(specs: TanukiMotionSpecs, from: Float, to: Float, ms: Long): Float =
        TargetBasedAnimation(specs.progress<Float>(), Float.VectorConverter, from, to)
            .getValueFromNanos(ms * 1_000_000)

    @Test fun progressRedirectsFromCurrentValueToLatestObservedBytes() {
        val motion = TanukiMotionSpecs(false)
        val halfway = value(motion, 0.2f, 0.6f, 50)
        assertEquals(0.4f, halfway, 0.0001f)
        assertEquals(halfway, value(motion, halfway, 0.9f, 0), 0.0001f)
        assertEquals(0.65f, value(motion, halfway, 0.9f, 50), 0.0001f)
        assertEquals(0.9f, value(motion, halfway, 0.9f, 100), 0.0001f)
        assertEquals(0.1f, value(motion, halfway, 0.1f, 100), 0.0001f)
    }

    @Test fun reducedProgressAndContentReachTargetWithoutIntermediateFrames() {
        val motion = TanukiMotionSpecs(true)
        assertEquals(0.9f, value(motion, 0.1f, 0.9f, 0), 0f)
        assertEquals(0, motion.state<Float>().durationMillis)
        assertEquals(0, motion.duration(com.tanuki.ui.design.TanukiMotion.layout))
    }

    private fun source(name: String): String {
        val root = sequenceOf(File("."), File("..")).first { File(it, "app/src/main").exists() }
        return File(root, "app/src/main/kotlin/com/tanuki/ui/$name.kt").readText()
    }

    @Test fun toolbarHasOneHeightOwnerAndPaginationNeverSlides() {
        val viewer = source("WorkspaceFileViewer")
        val toolbar = viewer.substringAfter("internal fun ViewerToolbarVisibility").substringBefore("/**")
        assertTrue(toolbar.contains("expandVertically"))
        assertTrue(toolbar.contains("shrinkVertically"))
        assertFalse(viewer.contains("animateContentSize"))
        assertFalse(viewer.contains("slideIn"))
        assertFalse(viewer.contains("slideOut"))
        assertTrue(viewer.contains("viewerReveal(page"))
        assertTrue(viewer.contains("compact = compact,"))
    }

    @Test fun fullscreenKeepsOneBodyAndNeverFadesVideoSurface() {
        val viewer = source("WorkspaceFileViewer")
        assertEquals(1, Regex("WorkspaceVideoPlayer\\(it, fullscreen").findAll(viewer).count())
        assertTrue(viewer.contains("ViewerChromeLayout("))
        assertFalse(viewer.contains("Crossfade("))
        val video = viewer.substringAfterLast("WorkspaceFileManager.FileKind.VIDEO ->").substringBefore("WorkspaceFileManager.FileKind.OTHER")
        assertFalse(video.contains("graphicsLayer"))
        assertFalse(video.contains("viewerReveal"))
    }
}
