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

    @Test fun readerConfigurationRestoreKeepsNthPageAndPreviousBoundaries() {
        val state = com.tanuki.ui.ReaderViewState(
            scrollOffset = 321, showLineNumbers = false, pageIndex = 2,
            offsets = listOf(0L, 65_535L, 131_070L), lineStarts = listOf(1, 801, 1_601),
            resolvedEncoding = "GB18030",
        )
        val saver = com.tanuki.ui.ReaderViewStateSaver
        for (encoding in listOf("GB18030", "Shift_JIS", "UTF-32LE")) {
            val expected = state.copy(resolvedEncoding = encoding)
            val saved = with(saver) { AllowAll.save(expected) }
            val restored = requireNotNull(saver.restore(requireNotNull(saved)))
            assertEquals(expected, restored)
            assertEquals(131_070L, restored.offsets[restored.pageIndex])
            assertEquals(801, restored.lineStarts[restored.pageIndex - 1])
        }
    }

    @Test fun readerLegacyStateAndCorruptCursorRecoverToFirstPage() {
        val saver = com.tanuki.ui.ReaderViewStateSaver
        assertEquals(com.tanuki.ui.ReaderViewState(scrollOffset = 123),
            saver.restore(listOf("自动", 123, true)))
        val invalid = com.tanuki.ui.ReaderViewState(pageIndex = 2,
            offsets = listOf(0L, -1L), lineStarts = listOf(1), scrollOffset = 321).normalized()
        assertEquals(com.tanuki.ui.ReaderViewState(), invalid)
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
