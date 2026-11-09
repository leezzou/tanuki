package com.tanuki

import com.tanuki.ui.DshNativeController
import com.tanuki.web.ChatNotificationDecision
import com.tanuki.web.ChatNotificationRoute
import com.tanuki.web.ChatNotificationTargetPolicy
import com.tanuki.web.DshMessage
import com.tanuki.web.DshMessageRole
import com.tanuki.web.DshSession
import com.tanuki.web.DshSessionPage
import com.tanuki.web.DshSessionSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class DshNativeControllerTest {
    @Test fun coldStartNotificationTargetSelectsTargetSession() {
        val source = FakeSource(listOf(session("A"), session("B")))
        val controller = controller(source)
        controller.openSession("B")
        val state = awaitSelection(controller, "B")
        assertEquals("B", state.selectedId)
        assertEquals(listOf("message-B"), state.messages.map { it.text })
    }

    @Test fun loadedNativePageNotificationTargetReplacesCurrentSelection() {
        val source = FakeSource(listOf(session("A"), session("B")))
        val controller = controller(source)
        controller.refresh()
        awaitSelection(controller, "A")
        controller.openSession("B")
        val state = awaitSelection(controller, "B")
        assertEquals("B", state.selectedId)
        assertEquals(listOf("message-B"), state.messages.map { it.text })
    }

    @Test fun nativeListFailureLeavesControllerUnavailableForOfficialFallback() {
        val source = FakeSource(listOf(session("A")), failSessions = true)
        val controller = controller(source)
        controller.openSession("B")
        val state = runBlocking {
            withTimeout(2_000) { controller.state.first { it.unavailableReason != null } }
        }
        assertNull(state.selectedId)
        assertTrue(state.unavailableReason!!.contains("DSH 接口"))
    }

    @Test fun notificationDecisionForSelectedSessionForcesReloadBeforeConsuming() {
        val source = FakeSource(listOf(session("A")), messages = mapOf("A" to "message-A-old"))
        val controller = controller(source)
        controller.refresh()
        awaitSelection(controller, "A")
        source.setMessage("A", "message-A-new")

        val before = controller.state.value
        var requestIssued = false
        // 通知到达前 A 已选中且空闲，但本次通知尚未发起刷新：必须请求原生强制重载。
        assertEquals(
            ChatNotificationDecision.REQUEST_NATIVE,
            ChatNotificationTargetPolicy.decide(
                "A", ChatNotificationRoute.NATIVE, before.selectedId, before.loading,
                nativeUnavailable = false, nativeRequestIssued = requestIssued
            )
        )
        requestIssued = true
        controller.openSession("A")
        val after = runBlocking {
            withTimeout(2_000) {
                controller.state.first { it.selectedId == "A" && !it.loading && it.messages.any { m -> m.text == "message-A-new" } }
            }
        }
        assertEquals(listOf("message-A-new"), after.messages.map { it.text })
        assertEquals(
            ChatNotificationDecision.CONSUMED,
            ChatNotificationTargetPolicy.decide(
                "A", ChatNotificationRoute.NATIVE, after.selectedId, after.loading,
                nativeUnavailable = false, nativeRequestIssued = requestIssued
            )
        )
    }

    @Test fun notificationForCurrentSessionReloadsNewHistory() {
        val source = FakeSource(listOf(session("A")), messages = mapOf("A" to "message-A-old"))
        val controller = controller(source)
        controller.refresh()
        awaitSelection(controller, "A")
        source.setMessage("A", "message-A-new")
        controller.openSession("A")
        val state = runBlocking {
            withTimeout(2_000) {
                controller.state.first { it.selectedId == "A" && !it.loading && it.messages.any { m -> m.text == "message-A-new" } }
            }
        }
        assertEquals(listOf("message-A-new"), state.messages.map { it.text })
        assertFalse(state.messages.any { it.text == "message-A-old" })
    }

    @Test fun notificationTargetMissingFromListIsStillSelectedAndShown() {
        val source = FakeSource(listOf(session("A")))
        val controller = controller(source)
        controller.openSession("C")
        val state = awaitSelection(controller, "C")
        assertEquals("C", state.selectedId)
        assertTrue(state.sessions.any { it.id == "C" })
        assertEquals(listOf("message-C"), state.messages.map { it.text })
    }

    private fun controller(source: DshSessionSource): DshNativeController =
        DshNativeController(source, CoroutineScope(Dispatchers.Unconfined))

    private fun awaitSelection(controller: DshNativeController, id: String) = runBlocking {
        withTimeout(2_000) {
            controller.state.first { it.selectedId == id && !it.loading }
        }
    }

    private fun session(id: String): DshSession = DshSession(
        id = id,
        title = "会话 $id",
        updatedAt = 0L,
        running = false,
        blank = false,
        agentAvailable = false,
        asOfSeq = 12
    )

    private class FakeSource(
        private val items: List<DshSession>,
        private val failSessions: Boolean = false,
        messages: Map<String, String> = emptyMap()
    ) : DshSessionSource {
        private val current = messages.toMutableMap()

        fun setMessage(sessionId: String, text: String) {
            current[sessionId] = text
        }

        override suspend fun sessions(): Result<List<DshSession>> =
            if (failSessions) Result.failure(IOException("DSH 接口认证失败")) else Result.success(items)

        override suspend fun messages(sessionId: String): Result<DshSessionPage> = Result.success(
            DshSessionPage(
                messages = listOf(
                    DshMessage(
                        seq = 1,
                        role = DshMessageRole.USER,
                        text = current[sessionId] ?: "message-$sessionId",
                        time = 1L
                    )
                ),
                hasMore = false
            )
        )
    }
}
