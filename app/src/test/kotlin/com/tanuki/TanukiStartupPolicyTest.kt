package com.tanuki

import com.tanuki.ui.TanukiStartupPolicy
import com.tanuki.ui.TanukiStartupStage
import com.tanuki.ui.TanukiStartupWork
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 冷启动到可交互的工作预算回归。 */
class TanukiStartupPolicyTest {
    @Test fun beforeFirstFrameOnlyLightRestorationIsAllowed() {
        assertTrue(TanukiStartupPolicy.allowed(TanukiStartupStage.BEFORE_FIRST_FRAME, TanukiStartupWork.RESTORE_VIEW_STATE))
        assertTrue(TanukiStartupPolicy.allowed(TanukiStartupStage.BEFORE_FIRST_FRAME, TanukiStartupWork.APPLY_THEME))
        assertTrue(TanukiStartupPolicy.allowed(TanukiStartupStage.BEFORE_FIRST_FRAME, TanukiStartupWork.READ_LOCAL_PREFERENCES))
        assertFalse(TanukiStartupPolicy.allowed(TanukiStartupStage.BEFORE_FIRST_FRAME, TanukiStartupWork.LOAD_SESSION_LIST))
        assertFalse(TanukiStartupPolicy.allowed(TanukiStartupStage.BEFORE_FIRST_FRAME, TanukiStartupWork.SCAN_WORKSPACE))
        assertFalse(TanukiStartupPolicy.allowed(TanukiStartupStage.BEFORE_FIRST_FRAME, TanukiStartupWork.PRELOAD_WEBVIEW))
    }

    @Test fun afterFirstFrameCanStartNetworkButNotHeavyDiskOrPreload() {
        assertTrue(TanukiStartupPolicy.allowed(TanukiStartupStage.AFTER_FIRST_FRAME, TanukiStartupWork.LOAD_SESSION_LIST))
        assertTrue(TanukiStartupPolicy.allowed(TanukiStartupStage.AFTER_FIRST_FRAME, TanukiStartupWork.CONNECT_LIVE_STREAM))
        assertFalse(TanukiStartupPolicy.allowed(TanukiStartupStage.AFTER_FIRST_FRAME, TanukiStartupWork.SCAN_WORKSPACE))
        assertFalse(TanukiStartupPolicy.allowed(TanukiStartupStage.AFTER_FIRST_FRAME, TanukiStartupWork.CHECK_UPDATES))
        assertFalse(TanukiStartupPolicy.allowed(TanukiStartupStage.AFTER_FIRST_FRAME, TanukiStartupWork.PRELOAD_WEBVIEW))
    }

    @Test fun interactiveStageAllowsEveryRegisteredWork() {
        for (work in TanukiStartupWork.entries) {
            assertTrue("$work 在可交互后不应继续被阻塞", TanukiStartupPolicy.allowed(TanukiStartupStage.INTERACTIVE, work))
        }
    }

    @Test fun workSetsGrowMonotonicallyWithStage() {
        val before = TanukiStartupPolicy.workFor(TanukiStartupStage.BEFORE_FIRST_FRAME)
        val after = TanukiStartupPolicy.workFor(TanukiStartupStage.AFTER_FIRST_FRAME)
        val interactive = TanukiStartupPolicy.workFor(TanukiStartupStage.INTERACTIVE)
        assertTrue(after.containsAll(before))
        assertTrue(interactive.containsAll(after))
        assertTrue(before.isNotEmpty())
        assertTrue(interactive.containsAll(TanukiStartupWork.entries.toSet()))
    }
}
