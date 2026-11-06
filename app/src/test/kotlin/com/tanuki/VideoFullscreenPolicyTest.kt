package com.tanuki

import com.tanuki.ui.VideoFullscreenPolicy
import org.junit.Assert.*
import org.junit.Test

class VideoFullscreenPolicyTest {
    // ActivityInfo.SCREEN_ORIENTATION_PORTRAIT；测试保持纯 JVM，不加载 Android 框架。
    private val portrait = 1

    @Test fun fullSensorValueMatchesActivityInfoAndAllowsRotation() {
        // ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR = 10：全屏期间允许竖屏/横屏随设备旋转，不强制横屏。
        assertEquals(10, VideoFullscreenPolicy.FULL_SENSOR)
    }

    @Test fun enterRequestsFullSensorAndExitRestoresUnspecified() {
        val session = VideoFullscreenPolicy.Session()
        assertEquals(VideoFullscreenPolicy.FULL_SENSOR, session.enter(VideoFullscreenPolicy.UNSPECIFIED))
        assertTrue(session.active)
        assertEquals(VideoFullscreenPolicy.UNSPECIFIED, session.exit())
        assertFalse(session.active)
    }

    @Test fun exitRestoresOrientationRequestedBeforeFullscreen() {
        val session = VideoFullscreenPolicy.Session()
        session.enter(portrait)
        assertEquals(portrait, session.exit())
    }

    @Test fun repeatedEnterKeepsFirstRestoreOrientation() {
        val session = VideoFullscreenPolicy.Session()
        assertEquals(VideoFullscreenPolicy.FULL_SENSOR, session.enter(portrait))
        // 重复进入（例如重组再次触发）仍请求可旋转，但不能覆盖最初记录的原方向。
        assertEquals(VideoFullscreenPolicy.FULL_SENSOR, session.enter(VideoFullscreenPolicy.UNSPECIFIED))
        assertEquals(portrait, session.exit())
        // 退出后再次退出不应产生第二次方向改动。
        assertNull(session.exit())
    }

    @Test fun exitWithoutEnterDoesNotRequestOrientationChange() {
        val session = VideoFullscreenPolicy.Session()
        assertNull(session.exit())
        assertFalse(session.active)
    }

    @Test fun sessionCanBeReusedAfterExit() {
        val session = VideoFullscreenPolicy.Session()
        session.enter(VideoFullscreenPolicy.UNSPECIFIED)
        assertEquals(VideoFullscreenPolicy.UNSPECIFIED, session.exit())
        session.enter(portrait)
        assertEquals(VideoFullscreenPolicy.FULL_SENSOR, session.enter(8))
        assertEquals(portrait, session.exit())
    }
}
