package com.tanuki

import com.tanuki.web.DshApprovalMemory
import com.tanuki.web.DshApprovalOutcome
import com.tanuki.web.DshApprovalProtocol
import com.tanuki.web.DshApiProtocol
import com.tanuki.web.DshApprovalRisk
import com.tanuki.web.DshEndpoint
import com.tanuki.web.DshEventFrame
import com.tanuki.web.DshRiskLevel
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DshApprovalProtocolTest {
    @Test fun parsesReadyAndKeepsClientId() {
        val frame = DshApprovalProtocol.parseStreamItem(item(JSONObject()
            .put("type", "ready")
            .put("clientId", "client-1")
            .put("host", JSONObject().put("home", "/home/tanuki"))))
        assertTrue(frame is DshEventFrame.Ready)
        assertEquals("client-1", (frame as DshEventFrame.Ready).clientId)
    }

    @Test fun parsesApprovalWaterfallAndOnlyWhitelistsDisplayFields() {
        val frame = DshApprovalProtocol.parseStreamItem(item(JSONObject()
            .put("type", "waterfall")
            .put("event", "approval/request")
            .put("eventId", "event-1")
            .put("agentId", "session-1")
            .put("request", JSONObject()
                .put("toolName", "bash")
                .put("callId", "call-1")
                .put("reason", "需要越权执行")
                .put("displayReason", JSONObject().put("zh", "工具请求越权").put("en", "Escalation"))
                .put("secret", "must-not-be-copied"))))
        assertTrue(frame is DshEventFrame.Approval)
        val request = (frame as DshEventFrame.Approval).request
        assertEquals("event-1", request.eventId)
        assertEquals("session-1", request.sessionId)
        assertEquals("bash", request.toolName)
        assertEquals("call-1", request.callId)
        assertEquals("需要越权执行", request.reason)
        assertEquals("工具请求越权", request.displayReason)
        assertFalse(request.toString().contains("must-not-be-copied"))
    }

    @Test fun unknownToolNameBecomesRejectOnlyApproval() {
        val frame = DshApprovalProtocol.parseStreamItem(item(JSONObject()
            .put("type", "waterfall")
            .put("event", "approval/request")
            .put("eventId", "event-1")
            .put("agentId", "session-1")
            .put("request", JSONObject().put("toolName", "   ").put("callId", "call-1"))))
        assertTrue(frame is DshEventFrame.Approval)
        val request = (frame as DshEventFrame.Approval).request
        assertTrue(request.unknownTool)
        assertEquals("未知审批", request.toolName)
    }

    @Test fun ignoresOtherWaterfallsAndMalformedIds() {
        assertTrue(DshApprovalProtocol.parseStreamItem(item(JSONObject()
            .put("type", "waterfall").put("event", "user-questions/request")
            .put("eventId", "e").put("agentId", "s"))) is DshEventFrame.Ignored)
        assertTrue(DshApprovalProtocol.parseStreamItem(item(JSONObject()
            .put("type", "waterfall").put("event", "approval/request")
            .put("eventId", "bad id").put("agentId", "s"))) is DshEventFrame.Ignored)
        assertTrue(DshApprovalProtocol.parseStreamItem(item(JSONObject()
            .put("type", "waterfall").put("event", "approval/request")
            .put("eventId", "e").put("agentId", "s"))) is DshEventFrame.Ignored)
        assertTrue(DshApprovalProtocol.parseStreamItem("not-json") is DshEventFrame.Ignored)
        assertTrue(DshApprovalProtocol.parseStreamItem(item(JSONObject()
            .put("type", "emit").put("event", "api-session/status"))) is DshEventFrame.Ignored)
    }

    @Test fun parsesOfficialStreamErrorWithShortCodeOnly() {
        val frame = DshApprovalProtocol.parseStreamItem(JSONObject()
            .put("type", "error")
            .put("streamId", "stream-1")
            .put("error", JSONObject().put("code", "stream_closed").put("message", "secret details"))
            .toString())
        assertEquals(DshEventFrame.StreamError("stream_closed"), frame)
    }

    @Test fun parsesCancelForSameEventId() {
        val frame = DshApprovalProtocol.parseStreamItem(item(JSONObject()
            .put("type", "cancel").put("eventId", "event-1")))
        assertEquals(DshEventFrame.Cancelled("event-1"), frame)
    }

    @Test fun answerBodyUsesOfficialEventsResultEnvelope() {
        val body = JSONObject(DshApprovalProtocol.answerBody("rpc-1", "client-1", "event-1", DshApprovalOutcome.ALLOWED_ONCE))
        assertEquals("client-request", body.getString("type"))
        assertEquals("rpc-1", body.getString("rpcId"))
        assertEquals("\$events/result", body.getString("method"))
        val args = body.getJSONObject("payload").getJSONObject("args")
        assertEquals("client-1", args.getString("clientId"))
        assertEquals("event-1", args.getString("eventId"))
        assertEquals("result", args.getJSONObject("outcome").getString("kind"))
        assertEquals("allowed-once", args.getJSONObject("outcome").getString("value"))
    }

    @Test fun approvalConnectUrlUsesAnswerInteractionsAndKeepsToken() {
        val url = DshApprovalProtocol.connectUrl(
            DshEndpoint(
                "http://127.0.0.1:1234",
                "http://127.0.0.1:1234/?token=secret-token&interactions=none"
            )
        )!!
        assertTrue(url.contains("token=secret-token"))
        assertTrue(url.contains("interactions=answer"))
        assertFalse(url.contains("interactions=none"))
        // 只读列表的既有契约仍是 none；审批不能复用它。
        val readOnly = DshApiProtocol.connectUrl(
            DshEndpoint("http://127.0.0.1:1234", "http://127.0.0.1:1234/?token=t")
        )!!
        assertTrue(readOnly.contains("interactions=none"))
    }

    @Test fun openStreamUsesEventsEndpointAndEmptyArgs() {
        val body = JSONObject(DshApprovalProtocol.openStreamBody("stream-1"))
        assertEquals("open", body.getString("type"))
        assertEquals("stream-1", body.getString("streamId"))
        assertEquals("\$events", body.getString("endpoint"))
        assertEquals(0, body.getJSONObject("payload").getJSONObject("args").length())
    }

    @Test fun riskDistinguishesEscalationShellWriteAndUnknown() {
        assertEquals(DshRiskLevel.HIGH, DshApprovalRisk.assess("bash", """{"command":"ls","sandbox_permissions":"danger-full-access"}""").level)
        assertEquals(DshRiskLevel.HIGH, DshApprovalRisk.assess("bash", """{"command":"ls"}""").level)
        assertEquals(DshRiskLevel.MEDIUM, DshApprovalRisk.assess("write", """{"file_path":"a.txt"}""").level)
        assertEquals(DshRiskLevel.LOW, DshApprovalRisk.assess("read", """{"path":"a.txt"}""").level)
        assertEquals(DshRiskLevel.UNKNOWN, DshApprovalRisk.assess("mcp__custom", null).level)
    }

    @Test fun memoryOnlyReusesSameSessionToolAndRisk() {
        val memory = DshApprovalMemory()
        assertTrue(memory.remember("s1", "bash", DshRiskLevel.HIGH, DshApprovalOutcome.ALLOWED_ONCE))
        assertEquals(DshApprovalOutcome.ALLOWED_ONCE, memory.lookup("s1", "bash", DshRiskLevel.HIGH))
        assertNull(memory.lookup("s2", "bash", DshRiskLevel.HIGH))
        assertNull(memory.lookup("s1", "write", DshRiskLevel.HIGH))
        assertNull(memory.lookup("s1", "bash", DshRiskLevel.LOW))
        memory.clear()
        assertNull(memory.lookup("s1", "bash", DshRiskLevel.HIGH))
    }

    private fun item(value: JSONObject): String = JSONObject()
        .put("type", "item")
        .put("streamId", "stream-1")
        .put("value", value)
        .toString()
}
