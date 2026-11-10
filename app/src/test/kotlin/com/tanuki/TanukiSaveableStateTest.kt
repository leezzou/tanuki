package com.tanuki

import androidx.compose.runtime.saveable.SaverScope
import com.tanuki.ui.TanukiDshPaneState
import com.tanuki.ui.TanukiDshSwitchState
import com.tanuki.ui.TanukiDshSwitchStateSaver
import com.tanuki.ui.TanukiSecondaryPageState
import com.tanuki.ui.TanukiSecondaryPageStateSaver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** 原生/官方切换与会话二级页状态的 Bundle 往返回归。 */
class TanukiSaveableStateTest {
    private object AllowAll : SaverScope {
        override fun canBeSaved(value: Any): Boolean = true
    }

    @Test fun dshSwitchStateRoundTripsBothPanes() {
        val state = TanukiDshSwitchState(
            native = true,
            nativePane = TanukiDshPaneState("native", listScrollIndex = 7, listScrollOffset = 21, draft = "原生草稿"),
            officialPane = TanukiDshPaneState("official", listScrollIndex = 2, listScrollOffset = 5, draft = "官方草稿"),
        )
        val saved = with(TanukiDshSwitchStateSaver) { AllowAll.save(state) }
        val restored = TanukiDshSwitchStateSaver.restore(requireNotNull(saved))
        assertNotNull(restored)
        assertEquals(state, restored)
    }

    @Test fun secondaryPageStateRoundTripsPanelsListAndScroll() {
        val state = TanukiSecondaryPageState(
            panels = listOf("会话详情", "模型选择"),
            listItems = listOf("A", "B", "C"),
            listScrollIndex = 3,
            listScrollOffset = 42,
        )
        val saved = with(TanukiSecondaryPageStateSaver) { AllowAll.save(state) }
        val restored = TanukiSecondaryPageStateSaver.restore(requireNotNull(saved))
        assertNotNull(restored)
        assertEquals(state, restored)
    }
}
