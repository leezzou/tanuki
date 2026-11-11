package com.tanuki.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** 实际取消关闭协程，验证系统动画被中断时的释放与代次隔离。 */
class TanukiOverlayExitTest {
    @Test fun canceledHideReleasesTheStillClosedWindow() = runBlocking {
        var state = TanukiOverlayState<String>().update("旧内容").update(null)
        val started = CompletableDeferred<Unit>()
        val close = launch {
            finishTanukiOverlayExit(state.generation, {
                started.complete(Unit)
                awaitCancellation()
            }, { state }, { state = it })
        }
        started.await()
        assertEquals("旧内容", state.shown)
        close.cancelAndJoin()
        assertTrue(close.isCancelled)
        assertFalse(state.open)
        assertNull(state.shown)
    }

    @Test fun canceledOldHideKeepsReopenedContent() = runBlocking {
        var state = TanukiOverlayState<String>().update("旧内容").update(null)
        val started = CompletableDeferred<Unit>()
        val close = launch {
            finishTanukiOverlayExit(state.generation, {
                started.complete(Unit)
                awaitCancellation()
            }, { state }, { state = it })
        }
        started.await()
        state = state.update("重新打开")
        close.cancelAndJoin()
        assertTrue(state.open)
        assertEquals("重新打开", state.shown)
    }

    @Test fun canceledOldHideCannotReleaseANewerClosingGeneration() = runBlocking {
        var state = TanukiOverlayState<String>().update("旧内容").update(null)
        val started = CompletableDeferred<Unit>()
        val close = launch {
            finishTanukiOverlayExit(state.generation, {
                started.complete(Unit)
                awaitCancellation()
            }, { state }, { state = it })
        }
        started.await()
        state = state.update("新内容").update(null)
        close.cancelAndJoin()
        assertFalse(state.open)
        assertEquals("新内容", state.shown)
        finishTanukiOverlayExit(state.generation, {}, { state }, { state = it })
        assertNull(state.shown)
    }

    @Test fun contentUpdatesDoNotInvalidateTheCurrentCloseGeneration() = runBlocking {
        val opened = TanukiOverlayState<String>().update("初始内容")
        val updated = opened.update("进度更新")
        assertEquals(opened.generation, updated.generation)
        var closing = updated.update(null)
        val generation = closing.generation
        closing = closing.update(null)
        assertEquals(generation, closing.generation)
        finishTanukiOverlayExit(generation, {}, { closing }, { closing = it })
        assertNull(closing.shown)
        assertEquals(generation, closing.generation)
    }
}
