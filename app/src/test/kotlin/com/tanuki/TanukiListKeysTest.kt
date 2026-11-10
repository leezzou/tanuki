package com.tanuki

import com.tanuki.ui.TanukiListKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** 稳定列表 key 的 JVM 回归。 */
class TanukiListKeysTest {
    @Test fun sameBusinessIdProducesSameKey() {
        assertEquals(TanukiListKeys.session("A"), TanukiListKeys.session(" A "))
        assertEquals(TanukiListKeys.message(42), TanukiListKeys.message(42))
        assertEquals(TanukiListKeys.tool("call-1"), TanukiListKeys.tool("call-1"))
    }

    @Test fun differentBusinessIdsDoNotCollide() {
        assertNotEquals(TanukiListKeys.session("A"), TanukiListKeys.session("B"))
        assertNotEquals(TanukiListKeys.tool("call-1"), TanukiListKeys.tool("call-2"))
        assertNotEquals(TanukiListKeys.message(1), TanukiListKeys.message(2))
    }

    @Test fun committedAndPendingMessageWithSameSeqStayDistinct() {
        assertNotEquals(TanukiListKeys.message(7), TanukiListKeys.pendingMessage(7))
    }

    @Test fun invalidIdsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { TanukiListKeys.session("  ") }
        assertThrows(IllegalArgumentException::class.java) { TanukiListKeys.tool("x".repeat(513)) }
        assertThrows(IllegalArgumentException::class.java) { TanukiListKeys.message(-1) }
    }

    @Test fun duplicateKeysAreRejectedBeforeTheyReachCompose() {
        assertEquals(listOf("a", "b"), TanukiListKeys.requireUnique(listOf("a", "b")))
        assertThrows(IllegalArgumentException::class.java) {
            TanukiListKeys.requireUnique(listOf("a", "a"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            TanukiListKeys.requireUnique(listOf("a", " "))
        }
    }
}
