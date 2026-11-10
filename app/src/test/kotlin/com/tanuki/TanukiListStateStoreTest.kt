package com.tanuki

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.saveable.SaverScope
import com.tanuki.ui.TanukiListPosition
import com.tanuki.ui.TanukiListStateStore
import com.tanuki.ui.TanukiListStateStoreSaver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 按会话显式保存/恢复滚动位置的回归。 */
class TanukiListStateStoreTest {
    private object AllowAll : SaverScope {
        override fun canBeSaved(value: Any): Boolean = true
    }

    @Test fun returningToSessionRestoresItsOwnPosition() {
        val store = TanukiListStateStore()
        store.save("dsh:A", 20, 4)
        store.save("dsh:B", 3, 1)
        assertEquals(TanukiListPosition(20, 4), store.position("dsh:A"))
        assertEquals(TanukiListPosition(3, 1), store.position("dsh:B"))
    }

    @Test fun reenteringPageUsesPersistedPosition() {
        val store = TanukiListStateStore()
        store.save("session-manager:list", 7, 12)
        // 页面移出再进入时从同一 store 读取，不回到顶部。
        assertEquals(TanukiListPosition(7, 12), store.position("session-manager:list"))
    }

    @Test fun unsetKeyStartsAtTop() {
        assertEquals(TanukiListPosition.Start, TanukiListStateStore().position("unknown"))
    }

    @Test fun negativeValuesAreClampedNotThrown() {
        val store = TanukiListStateStore()
        store.save("A", -2, -5)
        assertEquals(TanukiListPosition.Start, store.position("A"))
    }

    @Test fun saverRestoresEverySessionAfterActivityRecreation() {
        val store = TanukiListStateStore()
        store.save("dsh:A", 20, 4)
        store.save("dsh:B", 3, 1)
        val saved = with(TanukiListStateStoreSaver) { AllowAll.save(store) }
        val restored = TanukiListStateStoreSaver.restore(requireNotNull(saved))
        assertNotNull(restored)
        assertEquals(TanukiListPosition(20, 4), restored!!.position("dsh:A"))
        assertEquals(TanukiListPosition(3, 1), restored.position("dsh:B"))
    }

    @Test fun restoredStoreRecreatesLazyListStateAtSavedPosition() {
        val store = TanukiListStateStore()
        store.save("dsh:A", 20, 4)
        val saved = with(TanukiListStateStoreSaver) { AllowAll.save(store) }
        val restored = TanukiListStateStoreSaver.restore(requireNotNull(saved))
        val position = requireNotNull(restored).position("dsh:A")
        val state = LazyListState(position.index, position.offset)
        assertEquals(20, state.firstVisibleItemIndex)
        assertEquals(4, state.firstVisibleItemScrollOffset)
    }

    @Test fun clearCanRemoveOneKeyOrAll() {
        val store = TanukiListStateStore()
        store.save("A", 1, 2)
        store.save("B", 3, 4)
        store.clear("A")
        assertEquals(TanukiListPosition.Start, store.position("A"))
        assertEquals(TanukiListPosition(3, 4), store.position("B"))
        assertEquals(setOf("B"), store.keys())
        store.clear()
        assertTrue(store.keys().isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankKeyIsRejected() {
        TanukiListStateStore().position("  ")
    }
}
