package com.tanuki

import com.tanuki.ui.DshAttachmentPolicy
import com.tanuki.ui.DshComposerController
import com.tanuki.ui.MemoryDshDraftStore
import com.tanuki.ui.DshChatRenderPolicy
import com.tanuki.ui.DshComposerDraftPolicy
import com.tanuki.ui.DshComposerSendRecorder
import com.tanuki.ui.DshComposerSendTracker
import com.tanuki.ui.DshFailedPrompt
import com.tanuki.ui.DshErrorKind
import com.tanuki.ui.DshErrorPresenter
import com.tanuki.ui.DshImageKind
import com.tanuki.ui.DshImagePolicy
import com.tanuki.ui.DshImageSampling
import com.tanuki.ui.DshLinkKind
import com.tanuki.ui.DshLinkPolicy
import com.tanuki.ui.DshStreamFrame
import com.tanuki.ui.DshStreamPolicy
import com.tanuki.web.DshComposerClient
import com.tanuki.web.DshComposerRemote
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DshChatRenderTest {
    @Test fun missingUsageNeverPretendsToBeZero() {
        assertEquals("—", DshChatRenderPolicy.contextPercent(null, 100))
        assertEquals("—", DshChatRenderPolicy.contextPercent(1, null))
        assertEquals("—", DshChatRenderPolicy.contextPercent(1, 0))
        assertEquals("—", DshChatRenderPolicy.contextPercent(-1, 100))
        assertEquals("<1%", DshChatRenderPolicy.contextPercent(1, 1000))
        assertEquals("0%", DshChatRenderPolicy.contextPercent(0, 1000))
        assertEquals("25%", DshChatRenderPolicy.contextPercent(250, 1000))
        assertEquals("100%", DshChatRenderPolicy.contextPercent(Long.MAX_VALUE, 1))
    }

    @Test fun activityNeverClaimsLiveThinkingAfterDisconnect() {
        assertEquals("连接未实时", DshChatRenderPolicy.activity(true, false))
        assertEquals("思考中", DshChatRenderPolicy.activity(true, true))
        assertEquals("等你回答", DshChatRenderPolicy.activity(true, true, true))
        assertEquals("就绪", DshChatRenderPolicy.activity(false, true))
        assertEquals("补充消息，发送后排队处理", DshChatRenderPolicy.composerPlaceholder(true))
    }

    @Test fun toolGroupsKeepMessageBoundariesAndRestorePrependedMember() {
        val one = renderTool(1)
        val two = renderTool(2)
        val message = com.tanuki.web.DshTimelineItem.Text(com.tanuki.web.DshMessage(
            3, com.tanuki.web.DshMessageRole.ASSISTANT, "步骤说明", 1000L))
        val three = renderTool(4)
        val rows = DshChatRenderPolicy.rows(listOf(one, two, message, three))
        assertEquals(3, rows.size)
        assertEquals(listOf("call-1", "call-2"), rows[0].tools.map { it.callId })
        assertTrue(rows[1].tools.isEmpty())
        assertEquals("tool:call-1", DshChatRenderPolicy.rowKey(rows, "tool:call-2"))
        assertEquals(null, DshChatRenderPolicy.rowKey(rows, "tool:missing"))
        // 先拆锚点再聚合，锚点后的工具保持独立；不会被历史组吞并。
        val before = DshChatRenderPolicy.rows(listOf(one))
        val after = DshChatRenderPolicy.rows(listOf(two))
        assertEquals("tool:call-1", before.single().key)
        assertEquals("tool:call-2", after.single().key)
    }

    @Test fun prependAdjacentToolPreservesExpandedGroupAndMemberViewportPosition() {
        val groups = com.tanuki.ui.DshChatGroupState()
        val b = renderTool(2)
        val c = renderTool(3)
        val original = groups.project(listOf(b, c)).history.single()
        val expandedByKey = mapOf(original.key to true)
        val earlierMessage = com.tanuki.web.DshTimelineItem.Text(com.tanuki.web.DshMessage(
            0, com.tanuki.web.DshMessageRole.USER, "更早消息", 500L))
        val after = groups.project(listOf(earlierMessage, renderTool(1), b, c)).history
        val retained = after.last()
        // 同一 SaveableState 槽及首 callId，成员完整不变，不会因前插而重建/收起。
        assertEquals(original, retained)
        assertEquals("call-2", retained.tools.first().callId)
        assertTrue(expandedByKey.getValue(retained.key))
        assertEquals(listOf("call-2", "call-3"), retained.tools.map { it.callId })
        assertEquals(listOf(1, 2), after.filter { it.tools.isNotEmpty() }.map { it.tools.size })

        // 使用页面相同的 renderKeys/restoreIndex 恢复：模拟正在阅读展开组内 C 的像素。
        val oldKeys = com.tanuki.ui.DshChatAnchorPolicy.renderKeys(listOf(original.key), emptyList(), "top", null)
        val newKeys = com.tanuki.ui.DshChatAnchorPolicy.renderKeys(after.map { it.key }, emptyList(), "top", null)
        val target = com.tanuki.ui.DshChatAnchorPolicy.restoreIndex(newKeys, original.key, leadingRows = 0)!!
        assertEquals(3, target)
        val offsetWithinExpandedGroup = 360
        val cOutputOffsetWithinGroup = 520
        val oldHeights = listOf(48, 800)
        val newHeights = listOf(48, 96, 52, 800)
        val oldIndex = oldKeys.indexOf(original.key)
        val oldViewportTop = oldHeights.take(oldIndex).sum() + offsetWithinExpandedGroup
        val newViewportTop = newHeights.take(target).sum() + offsetWithinExpandedGroup
        val oldOutputPixel = oldHeights.take(oldIndex).sum() + cOutputOffsetWithinGroup - oldViewportTop
        val newOutputPixel = newHeights.take(target).sum() + cOutputOffsetWithinGroup - newViewportTop
        assertEquals(oldOutputPixel, newOutputPixel)
    }

    @Test fun savedGroupBoundariesSurviveRestoreAndReleaseOldWindow() {
        val original = com.tanuki.ui.DshChatGroupState()
        original.project(listOf(renderTool(2), renderTool(3)))
        val restored = com.tanuki.ui.DshChatGroupState(original.savedStarts.toSet())
        val rows = restored.project(listOf(renderTool(1), renderTool(2), renderTool(3))).history
        assertEquals(listOf("tool:call-1", "tool:call-2"), rows.map { it.key })
        restored.project(listOf(renderTool(8)))
        assertEquals(listOf("tool:call-8"), restored.savedStarts)
        val otherSession = com.tanuki.ui.DshChatGroupState()
        assertEquals(1, otherSession.project(listOf(renderTool(1), renderTool(2))).history.size)
    }

    @Test fun anchorSplitBoundaryIsRetainedAfterReturningToLiveTail() {
        val groups = com.tanuki.ui.DshChatGroupState()
        val b = renderTool(2)
        val c = renderTool(3)
        val split = groups.project(listOf(b), listOf(c))
        assertEquals("tool:call-2", split.history.single().key)
        assertEquals("tool:call-3", split.live.single().key)
        assertEquals(2, groups.project(listOf(b, c)).history.size)
    }

    @Test fun unboundOrWrongSessionComposerKeepsPageStopFallback() {
        val native = com.tanuki.ui.DshNativeState(selectedId = "A", running = true)
        assertTrue(DshChatRenderPolicy.needsStopFallback(native, com.tanuki.ui.DshComposerState(sessionId = "A")))
        assertTrue(DshChatRenderPolicy.needsStopFallback(native,
            com.tanuki.ui.DshComposerState(sessionId = "B", generationRunning = true)))
        assertFalse(DshChatRenderPolicy.needsStopFallback(native,
            com.tanuki.ui.DshComposerState(sessionId = "A", generationRunning = true)))
        assertFalse(DshChatRenderPolicy.needsStopFallback(native.copy(running = false), com.tanuki.ui.DshComposerState()))
    }

    private fun renderTool(seq: Int) = com.tanuki.web.DshTimelineItem.Tool(com.tanuki.web.DshToolItem(
        seq = seq, time = 1000L, callId = "call-$seq", name = "bash", arguments = null,
        status = com.tanuki.web.DshToolStatus.RUNNING, output = null, error = null,
        command = "pwd", cwd = null, diffs = emptyList(), files = emptyList(), outputTruncated = false,
        risk = com.tanuki.web.DshRiskAssessment(com.tanuki.web.DshRiskLevel.LOW, "只读")
    ))

    @Test fun bottomDetectionNeedsLastItemAndOffset() {
        assertTrue(DshChatRenderPolicy.isAtBottom(9, 10, 1_000, 1_000))
        assertTrue(DshChatRenderPolicy.isAtBottom(9, 10, 1_010, 1_000))
        assertFalse(DshChatRenderPolicy.isAtBottom(8, 10, 1_000, 1_000))
        assertFalse(DshChatRenderPolicy.isAtBottom(9, 10, 1_200, 1_000))
        assertFalse(DshChatRenderPolicy.isAtBottom(0, 0, 0, 0))
    }

    @Test fun streamMergeHandlesCumulativeDeltaDuplicateAndReset() {
        assertEquals("abc", DshStreamPolicy.merge("a", "abc"))
        assertEquals("abcd", DshStreamPolicy.merge("abc", "d"))
        assertEquals("abc", DshStreamPolicy.merge("abc", "bc"))
        assertEquals("abc", DshStreamPolicy.merge("abc", "abc"))
        assertEquals("", DshStreamPolicy.merge("abc", ""))
        assertEquals("新", DshStreamPolicy.merge("", "新"))
        assertTrue(DshStreamPolicy.shouldPublish("新", "旧"))
        assertFalse(DshStreamPolicy.shouldPublish("同", "同"))
        assertFalse(DshStreamPolicy.shouldPublish(null, "旧"))
    }

    @Test fun streamMergeNeverExceedsMessageLimit() {
        val merged = DshStreamPolicy.merge("a".repeat(DshStreamPolicy.MAX_TEXT_CHARS), "增量")
        assertEquals(DshStreamPolicy.MAX_TEXT_CHARS, merged.length)
    }

    @Test fun streamResetFrameReplacesSnapshotPrefix() {
        assertEquals(
            "回答",
            DshStreamPolicy.mergeFrame("历史前缀", DshStreamFrame("回答", reset = true))
        )
        assertEquals(
            "",
            DshStreamPolicy.mergeFrame("历史前缀", DshStreamFrame("", reset = true))
        )
        assertEquals(
            "历史前缀回答",
            DshStreamPolicy.mergeFrame("历史前缀", DshStreamFrame("回答", reset = false))
        )
        assertEquals(
            "abc",
            DshStreamPolicy.mergeFrame("abc", DshStreamFrame("abc", reset = true))
        )
    }

    @Test fun imageSamplingUsesOriginalDimensionsAndNeverExceedsLimit() {
        assertEquals(1, DshImageSampling.sampleSize(2_048, 2_048))
        assertEquals(2, DshImageSampling.sampleSize(4_096, 2_048))
        assertEquals(4, DshImageSampling.sampleSize(8_192, 8_192))
        assertEquals(16, DshImageSampling.sampleSize(32_768, 32_768))
        assertEquals(8, DshImageSampling.sampleSize(10_000, 100))
        assertEquals(1, DshImageSampling.sampleSize(0, 100))
        listOf(8_192, 32_768).forEach { dimension ->
            val sample = DshImageSampling.sampleSize(dimension, dimension)
            assertTrue(
                "$dimension 在 sample=$sample 时仍超过解码上限",
                DshImageSampling.fits(dimension, dimension, sample)
            )
        }
    }

    @Test fun realComposerSendFailureFlowsIntoPersistedFailureMap() = runBlocking {
        val remote = object : DshComposerRemote {
            override suspend fun call(method: String, args: JSONObject): Any? = when (method) {
                "session/prompt" -> throw java.io.IOException("网络连接中断")
                "commands/list" -> JSONObject().put("commands", JSONArray())
                else -> null
            }
        }
        val recorder = DshComposerSendRecorder(remote)
        val controller = DshComposerController(
            client = DshComposerClient(recorder),
            drafts = MemoryDshDraftStore(),
            scope = CoroutineScope(Dispatchers.Unconfined)
        )
        controller.setSession("session-A")
        withTimeout(2_000) { controller.state.first { it.sessionId == "session-A" } }
        controller.updateText("真实输入栏原文")
        controller.send()
        val failed = withTimeout(2_000) {
            while (recorder.failures.value["session-A"] == null) delay(10)
            recorder.failures.value.getValue("session-A")
        }
        assertEquals("真实输入栏原文", failed.text)
        assertTrue(failed.reason.contains("网络"))
        controller.close()
    }

    @Test fun recorderRejectsBusinessFailureAndKeepsSessionsSeparate() = runBlocking {
        val remote = object : DshComposerRemote {
            override suspend fun call(method: String, args: JSONObject): Any? = when (method) {
                "session/prompt" -> {
                    val session = args.optJSONObject("request")?.optString("sessionId")
                    JSONObject().put("accepted", session == "session-ok")
                }
                "commands/execute" -> JSONObject().put(
                    "result",
                    JSONObject().put("kind", "error").put("text", "命令失败")
                )
                else -> null
            }
        }
        val recorder = DshComposerSendRecorder(remote)
        recorder.call("session/prompt", promptArgs("session-a", "被拒绝"))
        recorder.call("commands/execute", JSONObject().put("agentId", "session-b").put("line", "/bad"))
        assertEquals("官方接口未接受该消息", recorder.failures.value.getValue("session-a").reason)
        assertEquals("命令失败", recorder.failures.value.getValue("session-b").reason)
        recorder.call("session/prompt", promptArgs("session-ok", "成功"))
        assertFalse(recorder.failures.value.containsKey("session-ok"))
    }

    @Test fun cancellationDoesNotBecomeFailure() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val remote = object : DshComposerRemote {
            override suspend fun call(method: String, args: JSONObject): Any? {
                gate.await()
                return JSONObject().put("accepted", true)
            }
        }
        val recorder = DshComposerSendRecorder(remote)
        val job = launch(Dispatchers.Unconfined) {
            runCatching { recorder.call("session/prompt", promptArgs("session-cancel", "取消")) }
        }
        withTimeout(2_000) { while (!job.isActive) delay(1) }
        job.cancel()
        job.join()
        assertTrue(recorder.failures.value.isEmpty())
    }

    @Test fun sendRecorderCapturesFastFailureWithoutUiFrames() = runBlocking {
        val remote = object : DshComposerRemote {
            override suspend fun call(method: String, args: JSONObject): Any? =
                throw java.io.IOException("连接立即失败")
        }
        val recorder = DshComposerSendRecorder(remote)
        val args = JSONObject().put(
            "request",
            JSONObject()
                .put("sessionId", "session-A")
                .put(
                    "content",
                    JSONArray()
                        .put(JSONObject().put("type", "text").put("text", "失败原文"))
                        .put(JSONObject().put("type", "file").put("receiptId", "r-1"))
                )
        )
        runCatching { recorder.call("session/prompt", args) }
        val failed = recorder.failures.value.getValue("session-A")
        assertEquals("失败原文", failed.text)
        assertEquals(1, failed.attachmentCount)
        assertTrue(failed.reason.contains("连接"))
    }

    @Test fun retryPolicyRequiresUnchangedTextOnlyDraft() {
        val failure = DshFailedPrompt("失败原文", "网络连接中断", 1L)
        assertTrue(DshComposerDraftPolicy.matches(failure, "失败原文", 0))
        assertFalse(DshComposerDraftPolicy.matches(failure, "改成别的", 0))
        assertFalse(DshComposerDraftPolicy.matches(failure, "失败原文", 1))
        assertTrue(DshComposerDraftPolicy.retryHint(failure, "改成别的", 0)!!.contains("已修改"))
        assertTrue(DshComposerDraftPolicy.retryHint(failure, "失败原文", 1)!!.contains("附件"))
        val attachmentFailure = DshFailedPrompt("失败原文", "网络连接中断", 1L, attachmentCount = 1)
        assertFalse(DshComposerDraftPolicy.matches(attachmentFailure, "失败原文", 0))
        assertTrue(DshComposerDraftPolicy.retryHint(attachmentFailure, "失败原文", 0)!!.contains("附件"))
    }

    @Test fun composerSendTrackerConnectsRealInputBarFailures() {
        val tracker = DshComposerSendTracker()
        assertEquals(null, tracker.observe(sendingNow = false, text = "草稿", error = "校验错误"))
        assertEquals(null, tracker.observe(sendingNow = true, text = "提交原文", error = null))
        val failure = tracker.observe(sendingNow = false, text = "提交原文", error = "网络连接中断")!!
        assertEquals("提交原文", failure.text)
        assertTrue(failure.reason.contains("网络"))
        assertTrue(failure.time > 0)

        tracker.reset()
        assertEquals(null, tracker.observe(sendingNow = true, text = "第二次", error = null))
        assertEquals(null, tracker.observe(sendingNow = false, text = "", error = null))
        assertEquals(null, tracker.observe(sendingNow = false, text = "草稿", error = "校验错误"))
    }

    @Test fun linkPolicyClassifiesSupportedAndDangerousSchemes() {
        assertEquals(DshLinkKind.WEB, DshLinkPolicy.classify("https://example.invalid/a").kind)
        assertTrue(DshLinkPolicy.classify("http://127.0.0.1:8080").canOpen)
        assertEquals(DshLinkKind.MAIL, DshLinkPolicy.classify("mailto:a@example.invalid").kind)
        assertEquals(DshLinkKind.PHONE, DshLinkPolicy.classify("tel:10086").kind)
        assertEquals(DshLinkKind.ATTACHMENT, DshLinkPolicy.classify("attachment:file-1").kind)
        assertEquals(DshLinkKind.ATTACHMENT, DshLinkPolicy.classify("./docs/readme.md").kind)

        listOf("javascript:alert(1)", "data:text/html,hi", "intent://x", "file:///etc/passwd").forEach { url ->
            val decision = DshLinkPolicy.classify(url)
            assertEquals(DshLinkKind.BLOCKED, decision.kind)
            assertFalse(decision.canOpen)
            assertTrue(decision.problem!!.isNotBlank())
        }
    }

    @Test fun linkPolicyRejectsEmptyLongAndControlCharacters() {
        assertEquals(DshLinkKind.BLOCKED, DshLinkPolicy.classify("   ").kind)
        assertEquals(DshLinkKind.BLOCKED, DshLinkPolicy.classify("https://example.invalid/" + "a".repeat(2_100)).kind)
        val control = "https://example.invalid/\u0001"
        assertEquals(DshLinkKind.BLOCKED, DshLinkPolicy.classify(control).kind)
        assertEquals("https://example.invalid/a", DshLinkPolicy.classify(" <https://example.invalid/a> ").url)
    }

    @Test fun imagePolicyKeepsRemotePlaceholderAndExplainsFailures() {
        val remote = DshImagePolicy.describe("截图", "https://example.invalid/a.png")
        assertEquals(DshImageKind.REMOTE, remote.kind)
        assertFalse(remote.canPreview)
        assertTrue(remote.message.contains("未自动加载"))

        val local = DshImagePolicy.describe("本地", "content://media/1")
        assertEquals(DshImageKind.LOCAL, local.kind)
        assertTrue(local.canPreview)

        val embedded = DshImagePolicy.describe("", "data:image/png;base64,AAAA")
        assertEquals(DshImageKind.EMBEDDED, embedded.kind)
        assertTrue(embedded.canPreview)

        val invalid = DshImagePolicy.describe("坏图", "not a url")
        assertEquals(DshImageKind.INVALID, invalid.kind)
        assertTrue(invalid.message.contains("无法加载"))
    }

    @Test fun attachmentPolicyNeverOpensWorkspaceFilesSilently() {
        val local = DshAttachmentPolicy.describe("报告", "attachment:file-1")
        assertEquals("报告", local.name)
        assertFalse(local.canOpen)
        assertTrue(local.message.contains("授权"))

        val remote = DshAttachmentPolicy.describe("说明", "https://example.invalid/a.txt")
        assertTrue(remote.canOpen)

        val invalid = DshAttachmentPolicy.describe("", "")
        assertTrue(invalid.name.isNotBlank())
        assertTrue(invalid.message.isNotBlank())
    }

    @Test fun errorPresenterMapsCommonFailuresToChineseHint() {
        assertEquals(DshErrorKind.AUTH, DshErrorPresenter.describe("官方接口返回 401").kind)
        assertEquals(DshErrorKind.BUSY, DshErrorPresenter.describe("session/agent-busy").kind)
        assertEquals(DshErrorKind.TIMEOUT, DshErrorPresenter.describe("connect timed out").kind)
        assertEquals(DshErrorKind.NETWORK, DshErrorPresenter.describe("WebSocket 已断开").kind)
        assertEquals(DshErrorKind.PROTOCOL, DshErrorPresenter.describe("官方接口响应格式不正确").kind)
        assertEquals(DshErrorKind.CANCELLED, DshErrorPresenter.describe("操作已取消").kind)
        val unknown = DshErrorPresenter.describe(null)
        assertEquals(DshErrorKind.UNKNOWN, unknown.kind)
        assertEquals("未知错误", unknown.detail)
        assertTrue(unknown.hint.isNotBlank())
        assertTrue(DshErrorPresenter.describe("会话已结束").detail.contains("会话已结束"))
    }

    @Test fun routineErrorCopyUsesAssistantAndReplyTermsWithoutChangingClassification() {
        listOf("session/agent-busy", "connect timed out", "WebSocket 已断开", "官方接口响应格式不正确").forEach { raw ->
            val info = DshErrorPresenter.describe(raw)
            val copy = info.title + info.hint
            assertFalse(listOf("DSH", "session", "transport", "interop", "协议", "回合").any { it in copy })
            assertEquals(raw, info.detail)
        }
        assertTrue(DshErrorPresenter.describe("session/agent-busy").hint.contains("本次回复"))
    }

    private fun promptArgs(sessionId: String, text: String): JSONObject = JSONObject().put(
        "request",
        JSONObject()
            .put("sessionId", sessionId)
            .put(
                "content",
                JSONArray().put(JSONObject().put("type", "text").put("text", text))
            )
    )
}
