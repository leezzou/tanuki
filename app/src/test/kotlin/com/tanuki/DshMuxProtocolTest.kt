package com.tanuki

import com.tanuki.web.DshApiProtocol
import com.tanuki.web.DshApprovalKind
import com.tanuki.web.DshEventFrame
import com.tanuki.web.DshFollowFrame
import com.tanuki.web.DshFollowParser
import com.tanuki.web.DshMessageRole
import com.tanuki.web.DshMuxDownlink
import com.tanuki.web.DshMuxProtocol
import com.tanuki.web.DshWsCodec
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class DshMuxProtocolTest {
    @Test fun openItemEndAndCancelUseOfficialMuxShape() {
        val open = JSONObject(
            DshMuxProtocol.openFrame(
                "stream-1",
                DshMuxProtocol.FOLLOW_ENDPOINT,
                DshMuxProtocol.followArgs("session-1", 50)
            )
        )
        assertEquals("open", open.getString("type"))
        assertEquals("stream-1", open.getString("streamId"))
        assertEquals("session/follow", open.getString("endpoint"))
        val request = open.getJSONObject("payload").getJSONObject("args").getJSONObject("request")
        assertEquals("session", request.getJSONObject("address").getString("kind"))
        assertEquals("session-1", request.getJSONObject("address").getString("sessionId"))
        assertTrue(request.getBoolean("assistantStream"))
        assertEquals(50, request.getInt("maxMessages"))

        val item = JSONObject(DshMuxProtocol.itemFrame("stream-1", JSONObject().put("type", "ready")))
        assertEquals("item", item.getString("type"))
        assertEquals("stream-1", item.getString("streamId"))

        val end = JSONObject(DshMuxProtocol.endFrame("stream-1"))
        assertEquals("end", end.getString("type"))
        val cancel = JSONObject(DshMuxProtocol.cancelFrame("stream-1"))
        assertEquals("cancel", cancel.getString("type"))
    }

    @Test fun rejectsForeignEndpointsAndInvalidStreamIds() {
        assertTrue(DshMuxProtocol.validEndpoint("session/follow"))
        assertTrue(DshMuxProtocol.validEndpoint("\$events"))
        assertTrue(DshMuxProtocol.validEndpoint("\$events/result"))
        assertFalse(DshMuxProtocol.validEndpoint("http://evil.example/x"))
        assertFalse(DshMuxProtocol.validEndpoint("session/../follow"))
        assertFalse(DshMuxProtocol.validEndpoint("session"))
        assertFalse(DshMuxProtocol.validStreamId(""))
        assertFalse(DshMuxProtocol.validStreamId("has space"))
        assertThrows(IllegalArgumentException::class.java) {
            DshMuxProtocol.openFrame("bad id", "session/follow", JSONObject())
        }
    }

    @Test fun parseDownlinkOnlyAcceptsOfficialFrames() {
        val item = DshMuxProtocol.parseDownlink(
            """{"type":"item","streamId":"s1","value":{"type":"ready","clientId":"c1"}}"""
        )
        assertTrue(item is DshMuxDownlink.Item)
        assertEquals("c1", (item as DshMuxDownlink.Item).value.getString("clientId"))

        val error = DshMuxProtocol.parseDownlink(
            """{"type":"error","streamId":"s1","error":{"code":"session/not-found","message":"secret"}}"""
        )
        assertEquals("session/not-found", (error as DshMuxDownlink.Failure).code)
        assertFalse(error.toString().contains("secret"))

        assertEquals("s1", (DshMuxProtocol.parseDownlink("""{"type":"end","streamId":"s1"}""") as DshMuxDownlink.Ended).streamId)
        assertNull(DshMuxProtocol.parseDownlink("""{"type":"weird","streamId":"s1"}"""))
        assertNull(DshMuxProtocol.parseDownlink("""{"type":"item","streamId":"has space","value":{}}"""))
        assertNull(DshMuxProtocol.parseDownlink("""{"type":"item","streamId":"s1","value":"text"}"""))
        assertNull(DshMuxProtocol.parseDownlink("x".repeat(DshMuxProtocol.MAX_TEXT_BYTES + 1)))
    }

    @Test fun eventStreamParsesReadyWaterfallCancelAndStatus() {
        val ready = DshMuxProtocol.parseEventFrame(JSONObject().put("type", "ready").put("clientId", "client-1"))
        assertEquals("client-1", (ready as DshEventFrame.Ready).clientId)

        val waterfall = DshMuxProtocol.parseEventFrame(
            JSONObject()
                .put("type", "waterfall")
                .put("event", "approval/request")
                .put("eventId", "event-1")
                .put("agentId", "session-1")
                .put(
                    "request",
                    JSONObject().put("toolName", "Bash").put("reason", "需要运行命令")
                        .put("arguments", JSONObject().put("command", "secret"))
                )
        )
        val approval = DshMuxProtocol.approvalOf(waterfall as DshEventFrame.Waterfall)
        assertNotNull(approval)
        assertEquals(DshApprovalKind.APPROVAL, approval!!.kind)
        assertEquals("Bash", approval.title)
        assertEquals("需要运行命令", approval.detail)
        assertFalse(approval.toString().contains("secret"))

        val cancelled = DshMuxProtocol.parseEventFrame(
            JSONObject().put("type", "cancel").put("eventId", "event-1")
        )
        assertEquals("event-1", (cancelled as DshEventFrame.Cancelled).eventId)

        val status = DshMuxProtocol.parseEventFrame(
            JSONObject()
                .put("type", "emit")
                .put("event", "api-session/status")
                .put("args", JSONArray().put("session-1").put(true))
        )
        assertTrue((status as DshEventFrame.Emit).args.getBoolean(1))

        assertNull(
            DshMuxProtocol.parseEventFrame(
                JSONObject().put("type", "waterfall").put("event", "other/event")
                    .put("eventId", "e").put("agentId", "a").put("request", JSONObject())
            )
        )
    }

    @Test fun questionWaterfallOnlyKeepsQuestionTextAndOptions() {
        val frame = DshMuxProtocol.parseEventFrame(
            JSONObject()
                .put("type", "waterfall")
                .put("event", "user-questions/request")
                .put("eventId", "event-2")
                .put("agentId", "session-1")
                .put(
                    "request",
                    JSONObject().put(
                        "questions",
                        JSONArray().put(
                            JSONObject().put("id", "q1").put("question", "选择部署环境")
                                .put(
                                    "options",
                                    JSONArray().put(JSONObject().put("label", "测试"))
                                        .put(JSONObject().put("label", "生产"))
                                )
                        )
                    )
                )
        ) as DshEventFrame.Waterfall
        val approval = DshMuxProtocol.approvalOf(frame)
        assertEquals(DshApprovalKind.QUESTION, approval!!.kind)
        assertEquals("选择部署环境", approval.title)
        assertEquals("测试 / 生产", approval.detail)
    }

    @Test fun followSnapshotFiltersReplaceInjectedAndSortsBySeq() {
        val parser = DshFollowParser()
        val snapshot = JSONObject()
            .put("type", "snapshot")
            .put("hasMore", true)
            .put(
                "records",
                JSONArray()
                    .put(record("assistant/message", 3, assistant("后回答")))
                    .put(record("user/message", 1, user("先提问", "user")))
                    .put(record("user/message", 2, user("注入", "agent")))
                    .put(record("assistant/message", 4, assistant("替换"), surfaceOp = JSONObject().put("op", "replace").put("startSeq", 3).put("endSeq", 3)))
            )
        val frames = parser.accept(snapshot)
        val state = frames.single() as DshFollowFrame.Snapshot
        assertEquals(listOf("先提问", "后回答"), state.messages.map { it.text })
        assertTrue(state.hasMore)
        assertTrue(state.liveText.isEmpty())
    }

    @Test fun followStreamingOnlyAccumulatesTextDeltaAndBaselineText() {
        val parser = DshFollowParser()
        val snapshot = JSONObject()
            .put("type", "snapshot")
            .put("records", JSONArray())
            .put("hasMore", false)
            .put(
                "assistantStream",
                JSONObject().put(
                    "revision", 1
                ).put(
                    "activeAttempt",
                    JSONObject().put("attemptId", "a1").put("startedAfterSeq", 1).put("turn", 1).put("step", 1)
                        .put("nextIndex", 2)
                        .put(
                            "stream",
                            JSONArray()
                                .put(
                                    JSONObject().put("type", "text-chunks").put("time0", 1)
                                        .put("index", 0).put("dt", JSONArray()).put("texts", JSONArray().put("历史"))
                                )
                                .put(
                                    JSONObject().put("type", "chunk").put("time", 2)
                                        .put("chunk", JSONObject().put("type", "text-delta").put("index", 0).put("text", "前缀"))
                                )
                        )
                )
            )
        val state = parser.accept(snapshot).single() as DshFollowFrame.Snapshot
        assertEquals("历史前缀", state.liveText)

        val start = parser.accept(
            JSONObject().put("type", "assistant-stream")
                .put("frame", JSONObject().put("type", "start").put("attemptId", "a2"))
        )
        assertEquals("", (start.single() as DshFollowFrame.Streaming).text)

        parser.accept(
            JSONObject().put("type", "assistant-stream")
                .put(
                    "frame",
                    JSONObject().put("type", "chunk").put("attemptId", "a2")
                        .put("chunk", JSONObject().put("type", "reasoning-delta").put("index", 0).put("text", "思考"))
                )
        )
        val text = parser.accept(
            JSONObject().put("type", "assistant-stream")
                .put(
                    "frame",
                    JSONObject().put("type", "chunk").put("attemptId", "a2")
                        .put("chunk", JSONObject().put("type", "text-delta").put("index", 0).put("text", "回答"))
                )
        )
        assertEquals("回答", (text.single() as DshFollowFrame.Streaming).text)

        val finished = parser.accept(
            JSONObject().put("type", "assistant-stream")
                .put(
                    "frame",
                    JSONObject().put("type", "end").put("attemptId", "a2")
                        .put("outcome", JSONObject().put("kind", "committed").put("eventType", "assistant/message").put("seq", 9))
                )
        ).single() as DshFollowFrame.Finished
        assertTrue(finished.committed)
        assertEquals(9, finished.seq)
    }

    @Test fun committedAssistantMessageReplacesStreamingBubble() {
        val parser = DshFollowParser()
        parser.accept(
            JSONObject().put("type", "assistant-stream")
                .put(
                    "frame",
                    JSONObject().put("type", "chunk").put("attemptId", "a1")
                        .put("chunk", JSONObject().put("type", "text-delta").put("index", 0).put("text", "临时回复"))
                )
        )
        val frames = parser.accept(
            JSONObject().put("type", "event").put("event", event("assistant/message", 9, assistant("最终回复")))
        )
        val message = (frames.single() as DshFollowFrame.Message).message
        assertEquals(DshMessageRole.ASSISTANT, message.role)
        assertEquals("最终回复", message.text)
        assertTrue(parser.liveText().isEmpty())
    }

    @Test fun parseMessageKeepsOnlyAppendOriginUserText() {
        val event = event("user/message", 2, user("真实消息", "user"))
        assertEquals("真实消息", DshApiProtocol.parseMessage(event)!!.text)
        assertNull(DshApiProtocol.parseMessage(event("user/message", 3, user("注入", "agent"))))
        assertNull(DshApiProtocol.parseMessage(event("user/message", 4, user("替换", "user"), surfaceOp = JSONObject().put("op", "replace"))))
    }

    @Test fun websocketAcceptMatchesRfcExampleAndClientFramesAreMasked() {
        assertEquals(
            "s3pPLMBiTxaQ9kYGzzhZRbK+xOo=",
            DshWsCodec.expectedAccept("dGhlIHNhbXBsZSBub25jZQ==")
        )
        val mask = byteArrayOf(1, 2, 3, 4)
        val frame = DshWsCodec.encodeClientFrame(DshWsCodec.OP_TEXT, "hi".toByteArray(), mask)
        assertEquals(0x81, frame[0].toInt() and 0xff)
        assertEquals(0x82, frame[1].toInt() and 0xff)
        assertEquals("hi", String(byteArrayOf(
            (frame[6].toInt() xor 1).toByte(),
            (frame[7].toInt() xor 2).toByte()
        )))
        // 长帧必须使用 126 扩展长度并保留客户端掩码位。
        val long = DshWsCodec.encodeClientFrame(DshWsCodec.OP_TEXT, ByteArray(200), mask)
        assertEquals(0xFE, long[1].toInt() and 0xff)
        assertEquals(0, long[2].toInt())
        assertEquals(200, long[3].toInt() and 0xff)
        assertTrue(Base64.getEncoder().encodeToString(mask).isNotEmpty())
    }

    private fun record(type: String, seq: Int, data: JSONObject, surfaceOp: Any? = "append"): JSONObject =
        JSONObject().put("type", "event").put("event", event(type, seq, data, surfaceOp))

    private fun event(type: String, seq: Int, data: JSONObject, surfaceOp: Any? = "append"): JSONObject =
        JSONObject().put("type", type).put("seq", seq).put("time", seq.toLong()).put("data", data)
            .also { if (surfaceOp != null) it.put("surfaceOp", surfaceOp) }

    private fun user(text: String, kind: String): JSONObject = JSONObject()
        .put("source", JSONObject().put("kind", kind))
        .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", text)))

    private fun assistant(text: String): JSONObject = JSONObject().put(
        "message",
        JSONObject().put("content", JSONArray().put(JSONObject().put("type", "text").put("text", text)))
    )
}
