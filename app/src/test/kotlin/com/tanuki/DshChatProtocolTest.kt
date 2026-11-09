package com.tanuki

import com.tanuki.web.ChatSignalKind
import com.tanuki.web.DshChatProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DshChatProtocolTest {
    @Test fun parsesOfficialStatusAndErrorEvents() {
        val status = DshChatProtocol.parse(
            """{"kind":"status","sessionId":"session-1","running":true,"seq":3,"pageId":"page-1","visibleSession":"session-1"}"""
        )
        assertNotNull(status)
        assertEquals(ChatSignalKind.STATUS, status!!.kind)
        assertEquals("session-1", status.sessionId)
        assertEquals(true, status.running)
        assertEquals(3, status.sequence)
        assertEquals("session-1", status.visibleSessionId)

        val seed = DshChatProtocol.parse(
            """{"kind":"status","sessionId":"session-3","running":true,"pageId":"page-1","visibleSession":""}"""
        )
        assertNotNull(seed)
        assertEquals(true, seed!!.running)
        assertNull(seed.sequence)

        val error = DshChatProtocol.parse(
            """{"kind":"error","sessionId":"session-2","seq":4,"pageId":"page-1","visibleSession":""}"""
        )
        assertNotNull(error)
        assertEquals(ChatSignalKind.ERROR, error!!.kind)
        assertNull(error.visibleSessionId)
    }

    @Test fun parsesApprovalAndQuestionWaterfallsWithEventIdentity() {
        val approval = DshChatProtocol.parse(
            """{"kind":"approval","sessionId":"session-1","eventId":"event-9","pageId":"page-1","visibleSession":"session-1"}"""
        )
        assertNotNull(approval)
        assertEquals(ChatSignalKind.APPROVAL, approval!!.kind)
        assertEquals("event-9", approval.eventId)

        val question = DshChatProtocol.parse(
            """{"kind":"question","sessionId":"session-1","eventId":"event-10","pageId":"page-1"}"""
        )
        assertNotNull(question)
        assertEquals(ChatSignalKind.QUESTION, question!!.kind)
    }

    @Test fun rejectsUnknownKindsAndMalformedPayloads() {
        assertNull(DshChatProtocol.parse(null))
        assertNull(DshChatProtocol.parse(""))
        assertNull(DshChatProtocol.parse("not json"))
        assertNull(DshChatProtocol.parse("""{"kind":"unknown","sessionId":"s","pageId":"p","seq":1}"""))
        assertNull(DshChatProtocol.parse("""{"kind":"status","sessionId":"s","pageId":"p","seq":1}"""))
        assertNull(DshChatProtocol.parse("""{"kind":"status","sessionId":"s","running":false,"pageId":"p","seq":0}"""))
        assertNull(DshChatProtocol.parse("""{"kind":"error","sessionId":"s","pageId":"p"}"""))
        assertNull(DshChatProtocol.parse("""{"kind":"approval","sessionId":"s","eventId":"","pageId":"p"}"""))
        assertNull(DshChatProtocol.parse("""{"kind":"question","sessionId":"","eventId":"e","pageId":"p"}"""))
        assertNull(DshChatProtocol.parse("""{"kind":"approval","sessionId":"has space","eventId":"e","pageId":"p"}"""))
        assertNull(DshChatProtocol.parse("""{"kind":"approval","sessionId":"s","eventId":"e","pageId":""}"""))
        assertNull(DshChatProtocol.parse("x".repeat(5000)))
    }

    @Test fun pageScriptOnlyTapsOfficialEventsAndExposesSessionOpener() {
        val script = DshChatProtocol.SCRIPT
        assertTrue(script.contains("/api/remote.mux"))
        assertTrue(script.contains("api-session/status"))
        assertTrue(script.contains("api-session/error"))
        assertTrue(script.contains("approval/request"))
        assertTrue(script.contains("user-questions/request"))
        assertTrue(script.contains("data-conversation-session"))
        assertTrue(script.contains("data-row-key"))
        assertTrue(script.contains("data-state"))
        assertTrue(script.contains("clearSearchFilter"))
        assertTrue(script.contains("清除搜索"))
        assertTrue(script.contains("searchCleared"))
        assertTrue(script.contains(DshChatProtocol.OPEN_SESSION_FUNCTION))
        assertTrue(script.contains(DshChatProtocol.JS_OBJECT))
        // 只读旁路不得扫描页面正文、本地存储或凭据。
        assertFalse(script.contains("innerText"))
        assertFalse(script.contains("textContent"))
        assertFalse(script.contains("localStorage"))
        assertFalse(script.contains("indexedDB"))
    }
}
