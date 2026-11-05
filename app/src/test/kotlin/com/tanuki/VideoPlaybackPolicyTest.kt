package com.tanuki

import com.tanuki.ui.VideoPlaybackPolicy
import org.junit.Assert.*
import org.junit.Test

class VideoPlaybackPolicyTest {
    @Test fun skipClampsToActualDurationWithoutOverflow() {
        assertEquals(0, VideoPlaybackPolicy.skip(5000, -10_000, 20_000))
        assertEquals(15_000, VideoPlaybackPolicy.skip(5000, 10_000, 20_000))
        assertEquals(20_000, VideoPlaybackPolicy.skip(19_000, 10_000, 20_000))
        assertEquals(Int.MAX_VALUE, VideoPlaybackPolicy.skip(Int.MAX_VALUE - 1, 10_000, Int.MAX_VALUE))
        assertEquals(0, VideoPlaybackPolicy.skip(5000, 10_000, -1))
    }
    @Test fun playbackSpeedChoicesIncludeSlowAndFastRates() {
        assertEquals(listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f), VideoPlaybackPolicy.speeds)
        assertTrue(VideoPlaybackPolicy.acceptsSpeed(0.5f))
        assertFalse(VideoPlaybackPolicy.acceptsSpeed(0f))
        assertFalse(VideoPlaybackPolicy.acceptsSpeed(Float.NaN))
        assertFalse(VideoPlaybackPolicy.acceptsSpeed(3f))
    }
}
