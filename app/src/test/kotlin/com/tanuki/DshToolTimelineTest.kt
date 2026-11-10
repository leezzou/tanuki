package com.tanuki

import com.tanuki.web.DshDiffLineKind
import com.tanuki.web.DshDiffPresentation
import com.tanuki.web.DshFileDiff
import com.tanuki.web.DshRiskLevel
import com.tanuki.web.DshTimelineItem
import com.tanuki.web.DshToolArguments
import com.tanuki.web.DshToolStatus
import com.tanuki.web.DshToolTimeline
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DshToolTimelineTest {
    @Test fun pairsToolCallWithResultAndKeepsCommandDiffAndFiles() {
        val records = JSONArray()
            .put(call(1, "c1", "bash", """{"command":"ls","cwd":"/workspaces"}"""))
            .put(result(
                2, "c1", "a\nb", isError = false,
                meta = JSONObject().put("diffs", JSONArray().put(JSONObject()
                    .put("path", "/workspaces/a.txt").put("oldText", "a").put("newText", "b")))
            ))
            .put(call(3, "c2", "write", """{"file_path":"/workspaces/b.txt"}"""))
            .put(userMessage(4, "请继续"))
        val timeline = DshToolTimeline.parse(records)
        assertEquals(3, timeline.size)

        val bash = (timeline[0] as DshTimelineItem.Tool).tool
        assertEquals("c1", bash.callId)
        assertEquals(DshToolStatus.SUCCESS, bash.status)
        assertEquals("ls", bash.command)
        assertEquals("/workspaces", bash.cwd)
        assertEquals("a\nb", bash.output)
        assertEquals(1, bash.diffs.size)
        assertTrue(bash.files.contains("/workspaces/a.txt"))
        assertEquals(DshRiskLevel.HIGH, bash.risk.level)

        val write = (timeline[1] as DshTimelineItem.Tool).tool
        assertEquals(DshToolStatus.RUNNING, write.status)
        assertEquals(DshRiskLevel.MEDIUM, write.risk.level)
        assertTrue(write.files.contains("/workspaces/b.txt"))

        assertEquals("请继续", (timeline[2] as DshTimelineItem.Text).message.text)
    }

    @Test fun parsePageKeepsMessagesAndToolTimelineTogether() {
        val records = JSONArray()
            .put(userMessage(1, "运行命令"))
            .put(call(2, "c1", "bash", """{"command":"ls"}"""))
            .put(result(3, "c1", "a.txt", isError = false))
        val body = JSONObject()
            .put("type", "server-response")
            .put("rpcId", "rpc-page")
            .put("result", JSONObject().put("ok", true).put("value", JSONObject()
                .put("records", records).put("hasMore", false)))
            .toString()
        val page = com.tanuki.web.DshApiProtocol.parsePage(body)
        assertEquals(1, page.messages.size)
        assertEquals(2, page.timeline.size)
        assertTrue(page.timeline[1] is DshTimelineItem.Tool)
        assertEquals("c1", (page.timeline[1] as DshTimelineItem.Tool).tool.callId)
    }

    @Test fun failedToolResultShowsOfficialReason() {
        val records = JSONArray()
            .put(call(1, "c1", "write", """{"file_path":"/workspaces/a.txt"}"""))
            .put(result(
                2, "c1", "denied", isError = true,
                error = JSONObject().put("code", "E_DENIED").put("reason", "没有工作区写权限")
            ))
        val tool = (DshToolTimeline.parse(records).single() as DshTimelineItem.Tool).tool
        assertEquals(DshToolStatus.FAILED, tool.status)
        assertEquals("没有工作区写权限", tool.error)
    }

    @Test fun replaceSurfaceUpdatesSameCardInsteadOfDuplicating() {
        val records = JSONArray()
            .put(call(1, "c1", "read", """{"path":"a.txt"}"""))
            .put(result(2, "c1", "old", isError = false))
            .put(result(
                3, "c1", "new", isError = false,
                surfaceOp = JSONObject().put("op", "replace").put("startSeq", 2).put("endSeq", 2)
            ))
        val timeline = DshToolTimeline.parse(records)
        assertEquals(1, timeline.size)
        val tool = (timeline.single() as DshTimelineItem.Tool).tool
        assertEquals("new", tool.output)
        assertEquals(DshToolStatus.SUCCESS, tool.status)
    }

    @Test fun orphanResultIsVisibleWithExplicitUnknownTool() {
        val records = JSONArray().put(result(5, "c9", "result-only", isError = false))
        val tool = (DshToolTimeline.parse(records).single() as DshTimelineItem.Tool).tool
        assertEquals("未知工具", tool.name)
        assertEquals(DshToolStatus.SUCCESS, tool.status)
        assertEquals(DshRiskLevel.UNKNOWN, tool.risk.level)
    }

    @Test fun longOutputIsTruncatedAndMarked() {
        val text = "x".repeat(DshToolTimeline.MAX_OUTPUT_CHARS + 500)
        val records = JSONArray()
            .put(call(1, "c1", "read", """{"path":"a.txt"}"""))
            .put(result(2, "c1", text, isError = false))
        val tool = (DshToolTimeline.parse(records).single() as DshTimelineItem.Tool).tool
        assertTrue(tool.outputTruncated)
        assertTrue(tool.output!!.length <= DshToolTimeline.MAX_OUTPUT_CHARS + 1)
    }

    @Test fun diffPresentationShowsContextRemovedAndAddedLines() {
        val lines = DshDiffPresentation.lines(DshFileDiff("/w/a.txt", "a\nb\nc", "a\nB\nc"))
        assertEquals(
            listOf(DshDiffLineKind.CONTEXT, DshDiffLineKind.REMOVED, DshDiffLineKind.ADDED, DshDiffLineKind.CONTEXT),
            lines.map { it.kind }
        )
        assertEquals(1 to 1, DshDiffPresentation.counts(DshFileDiff("/w/a.txt", "a\nb\nc", "a\nB\nc")))
    }

    @Test fun newFileDiffIsAllAddedAndArgumentsAreExtractedSafely() {
        val diff = DshFileDiff("/w/new.txt", null, "one\ntwo")
        assertEquals(listOf(DshDiffLineKind.ADDED, DshDiffLineKind.ADDED),
            DshDiffPresentation.lines(diff).map { it.kind })
        assertEquals("ls", DshToolArguments.command("""{"command":"ls"}"""))
        assertEquals("/w", DshToolArguments.cwd("""{"workdir":"/w"}"""))
        assertTrue(DshToolArguments.paths("""{"file_path":"/w/a","paths":["/w/b"]}""").containsAll(listOf("/w/a", "/w/b")))
        assertNull(DshToolArguments.command("not-json"))
        assertFalse(DshToolArguments.pretty("not-json")!!.contains("\n"))
    }

    private fun call(seq: Int, callId: String, name: String, arguments: String): JSONObject = event(
        "tool/call", seq,
        JSONObject().put("callId", callId).put("name", name).put("arguments", arguments)
    )

    private fun result(
        seq: Int,
        callId: String,
        text: String,
        isError: Boolean,
        error: JSONObject? = null,
        meta: JSONObject? = null,
        surfaceOp: JSONObject? = null
    ): JSONObject {
        val data = JSONObject()
            .put("message", JSONObject()
                .put("toolCallId", callId)
                .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", text)))
                .put("isError", isError))
        error?.let { data.put("error", it) }
        meta?.let { data.put("meta", it) }
        return event("tool/result", seq, data, surfaceOp)
    }

    private fun userMessage(seq: Int, text: String): JSONObject = event(
        "user/message", seq,
        JSONObject()
            .put("source", JSONObject().put("kind", "user"))
            .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", text))),
        "append"
    )

    private fun event(type: String, seq: Int, data: JSONObject, surfaceOp: Any? = null): JSONObject {
        val event = JSONObject().put("type", type).put("seq", seq).put("time", 1_000L + seq).put("data", data)
        if (surfaceOp != null) event.put("surfaceOp", surfaceOp)
        return JSONObject().put("event", event)
    }
}
