package com.tanuki

import com.tanuki.ui.DshAttachmentStatus
import com.tanuki.ui.DshComposerController
import com.tanuki.ui.DshComposerState
import com.tanuki.ui.DshPickedAttachment
import com.tanuki.ui.MemoryDshDraftStore
import com.tanuki.web.DshAttachmentKind
import com.tanuki.web.DshComposerClient
import com.tanuki.web.DshComposerRemote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

class DshComposerControllerTest {
    private data class Call(val method: String, val args: JSONObject)

    private class FakeRemote : DshComposerRemote {
        val calls = CopyOnWriteArrayList<Call>()
        var commands: JSONArray = JSONArray()
        var imageLimits: JSONObject = JSONObject()
            .put("maxImageBytes", 10 * 1024 * 1024)
            .put("maxImagesPerMessage", 5)
            .put("maxMessageImageBytes", 20 * 1024 * 1024)
            .put("maxImagePixels", 40_000_000L)
            .put("maxImageDimension", 8192)
        var uploadDelayMillis = 0L
        var commandsDelayMillis = 0L
        var promptFailure: Throwable? = null
        var promptAccepted = true

        override suspend fun call(method: String, args: JSONObject): Any? {
            calls += Call(method, args)
            return when (method) {
                "session/projections" -> JSONObject().put("kind", "sequenced")
                    .put("values", JSONObject().put("imageLimits", imageLimits))
                "commands/list" -> {
                    if (commandsDelayMillis > 0) delay(commandsDelayMillis)
                    commands
                }
                "fileReferences/list" -> JSONArray().put(
                    JSONObject().put("path", "src/Main.kt").put("kind", "file")
                )
                "sessionReferenceResolver/candidates" -> JSONArray().put(
                    JSONObject().put("mention", "@[历史会话](dsh-session:history-1)")
                        .put("sessionId", "history-1").put("label", "历史会话")
                        .put("sameWorkspace", true).put("createdAt", 1L)
                )
                "fileUploads/upload" -> {
                    if (uploadDelayMillis > 0) delay(uploadDelayMillis)
                    JSONObject().put("receiptId", "receipt-1").put(
                        "file",
                        JSONObject().put("attachmentId", "attachment-1")
                            .put("name", "a.txt").put("bytes", 5)
                    )
                }
                "session/prompt" -> {
                    promptFailure?.let { throw it }
                    JSONObject().put("accepted", promptAccepted)
                }
                "commands/execute" -> JSONObject().put("commandId", "c1")
                    .put("result", JSONObject().put("kind", "success"))
                else -> null
            }
        }
    }

    private fun controller(
        remote: FakeRemote,
        store: MemoryDshDraftStore = MemoryDshDraftStore(),
        onSent: (String) -> Unit = {}
    ): DshComposerController = DshComposerController(
        client = DshComposerClient(remote),
        drafts = store,
        scope = CoroutineScope(Dispatchers.Unconfined),
        onSent = onSent,
        timeZoneProvider = { "Asia/Shanghai" }
    )

    @Test fun draftIsKeptPerSessionAndFlushedOnSwitch() {
        val store = MemoryDshDraftStore()
        val controller = controller(FakeRemote(), store)
        controller.setSession("s1")
        controller.updateText("第一行\n第二行", 7)
        controller.setSession("s2")
        controller.updateText("另一个会话", 5)
        controller.setSession("s1")
        assertEquals("第一行\n第二行", controller.state.value.text)
        assertEquals("第一行\n第二行", store.read("s1"))
        assertEquals("另一个会话", store.read("s2"))
        controller.close()
        assertNull(controller.state.value.sessionId)
        assertEquals("第一行\n第二行", store.read("s1"))
    }

    @Test fun sendPromptCarriesImageThenTextAndClearsDraft() {
        val remote = FakeRemote()
        val store = MemoryDshDraftStore()
        val controller = controller(remote, store)
        controller.setSession("s1")
        controller.addImages(listOf(image(png(100, 100), "a.png")))
        assertEquals(1, controller.state.value.attachments.size)
        controller.updateText("看看这张图", 5)
        controller.send()
        val state = awaitState(controller) { it.notice == "已发送" }
        assertTrue(state.text.isEmpty())
        assertTrue(state.attachments.isEmpty())
        assertEquals("", store.read("s1"))

        val call = remote.calls.first { it.method == "session/prompt" }
        val request = call.args.getJSONObject("request")
        assertEquals("s1", request.getString("sessionId"))
        assertEquals("queue", request.getString("mode"))
        assertEquals("Asia/Shanghai", request.getString("clientTimeZone"))
        assertTrue(request.getString("requestId").isNotBlank())
        val content = request.getJSONArray("content")
        assertEquals(2, content.length())
        assertEquals("image", content.getJSONObject(0).getString("type"))
        assertEquals("image/png", content.getJSONObject(0).getString("mediaType"))
        assertEquals("a.png", content.getJSONObject(0).getString("name"))
        assertEquals("看看这张图", content.getJSONObject(1).getString("text"))
    }

    @Test fun fileUploadsBeforePromptAndUsesReceipt() {
        val remote = FakeRemote().apply { uploadDelayMillis = 20 }
        val controller = controller(remote)
        controller.setSession("s1")
        controller.addFiles(
            listOf(
                DshPickedAttachment(
                    name = "a.txt",
                    mediaType = "text/plain",
                    bytes = "hello".toByteArray(),
                    kind = DshAttachmentKind.FILE
                )
            )
        )
        val uploaded = awaitState(controller) {
            it.attachments.singleOrNull()?.status == DshAttachmentStatus.UPLOADED
        }
        assertEquals("receipt-1", uploaded.attachments.single().receiptId)
        assertFalse(uploaded.uploadBusy)

        controller.updateText("处理附件", 4)
        controller.send()
        awaitState(controller) { it.notice == "已发送" }
        val content = remote.calls.first { it.method == "session/prompt" }
            .args.getJSONObject("request").getJSONArray("content")
        assertEquals("file", content.getJSONObject(0).getString("type"))
        assertEquals("receipt-1", content.getJSONObject(0).getString("receiptId"))
        assertEquals("处理附件", content.getJSONObject(1).getString("text"))
    }

    @Test fun slashCommandWaitsForCatalogBeforeTreatingAsPrompt() {
        val remote = FakeRemote().apply {
            commandsDelayMillis = 60
            commands = JSONArray().put(JSONObject().put("name", "compact").put("description", "压缩"))
        }
        val controller = controller(remote)
        controller.setSession("s1")
        controller.updateText("/compact", 8)
        controller.send()
        assertTrue(controller.state.value.error.orEmpty().contains("正在读取斜杠命令"))
        assertFalse(remote.calls.any { it.method == "session/prompt" || it.method == "commands/execute" })
        awaitState(controller) { it.commandsLoaded }
        controller.send()
        awaitState(controller) { it.notice == "已发送" }
        assertTrue(remote.calls.any { it.method == "commands/execute" })
    }

    @Test fun knownSlashCommandUsesExecuteAndHonorsAttachmentPolicy() {
        val remote = FakeRemote().apply {
            commands = JSONArray()
                .put(JSONObject().put("name", "compact").put("description", "压缩"))
                .put(
                    JSONObject().put("name", "goal").put("description", "目标")
                        .put("input", JSONObject().put("hint", "目标").put("attachments", true))
                )
        }
        val controller = controller(remote)
        controller.setSession("s1")
        awaitState(controller) { it.commands.size == 2 }

        controller.updateText("/compact", 8)
        controller.addImages(listOf(image(png(20, 20), "a.png")))
        controller.send()
        assertTrue(controller.state.value.error.orEmpty().contains("不接受附件"))
        assertFalse(remote.calls.any { it.method == "commands/execute" })

        controller.removeAttachment(controller.state.value.attachments.single().id)
        controller.send()
        awaitState(controller) { it.notice == "已发送" }
        val compact = remote.calls.first { it.method == "commands/execute" }
        assertEquals("/compact", compact.args.getString("line"))
        assertEquals(0, compact.args.getJSONArray("submittedAttachments").length())

        controller.updateText("/goal 完成目标", 8)
        controller.addImages(listOf(image(png(20, 20), "b.png")))
        controller.send()
        awaitState(controller) { it.notice == "已发送" }
        val goal = remote.calls.filter { it.method == "commands/execute" }.last()
        assertEquals(1, goal.args.getJSONArray("submittedAttachments").length())
        assertEquals("image", goal.args.getJSONArray("submittedAttachments").getJSONObject(0).getString("type"))
    }

    @Test fun oversizeImageIsRejectedWithChineseMessage() {
        val remote = FakeRemote().apply {
            imageLimits = JSONObject()
                .put("maxImageBytes", 1024)
                .put("maxImagesPerMessage", 2)
                .put("maxMessageImageBytes", 2048)
                .put("maxImagePixels", 1_000_000L)
                .put("maxImageDimension", 1000)
        }
        val controller = controller(remote)
        controller.setSession("s1")
        awaitState(controller) { it.limits.maxImageBytes == 64 * 1024 }
        controller.addImages(listOf(image(png(100, 100) + ByteArray(70 * 1024), "big.png")))
        assertTrue(controller.state.value.attachments.isEmpty())
        assertTrue(controller.state.value.error.orEmpty().contains("单张图片不能超过"))
    }

    @Test fun mentionCandidatesLoadFromSessionsAndFiles() {
        val controller = controller(FakeRemote())
        controller.setSession("s1")
        controller.updateText("看看 @a", 6)
        val state = awaitState(controller) { it.mentionCandidates.size == 2 }
        assertEquals("a", state.mentionQuery?.query)
        assertTrue(state.mentionCandidates[0] is com.tanuki.ui.DshMentionCandidate.Session)
        assertTrue(state.mentionCandidates[1] is com.tanuki.ui.DshMentionCandidate.File)
        assertEquals("@[历史会话](dsh-session:history-1)", state.mentionCandidates[0].insertion)
        assertEquals("@src/Main.kt", state.mentionCandidates[1].insertion)
    }

    @Test fun failedSendKeepsDraftAndAttachments() {
        val remote = FakeRemote().apply { promptFailure = IOException("网络请求失败") }
        val controller = controller(remote)
        controller.setSession("s1")
        controller.updateText("不要丢", 3)
        controller.addImages(listOf(image(png(20, 20), "a.png")))
        controller.send()
        val state = awaitState(controller) { it.error != null }
        assertTrue(state.error!!.contains("网络请求失败"))
        assertEquals("不要丢", state.text)
        assertEquals(1, state.attachments.size)
        assertFalse(state.sending)
    }

    @Test fun officialAttachmentErrorMapsToChineseMessage() {
        val remote = FakeRemote().apply {
            promptFailure = com.tanuki.web.DshApiException("attachment/too-large")
        }
        val controller = controller(remote)
        controller.setSession("s1")
        controller.updateText("发送", 2)
        controller.send()
        val state = awaitState(controller) { it.error != null }
        val error = state.error.orEmpty()
        assertTrue(error.contains("附件超过官方大小限制"))
        assertFalse(error.contains("attachment/too-large"))
    }

    @Test fun rejectedPromptKeepsDraft() {
        val remote = FakeRemote().apply { promptAccepted = false }
        val controller = controller(remote)
        controller.setSession("s1")
        controller.updateText("保留", 2)
        controller.send()
        val state = awaitState(controller) { it.error != null }
        assertTrue(state.error!!.contains("未接受"))
        assertEquals("保留", state.text)
    }

    @Test fun onSentReceivesSessionId() {
        var sent: String? = null
        val controller = controller(FakeRemote(), onSent = { sent = it })
        controller.setSession("s1")
        controller.updateText("发送", 2)
        controller.send()
        awaitState(controller) { it.notice == "已发送" }
        assertEquals("s1", sent)
    }

    private fun awaitState(
        controller: DshComposerController,
        predicate: (DshComposerState) -> Boolean
    ): DshComposerState = runBlocking {
        withTimeout(3_000) { controller.state.first(predicate) }
    }

    private fun image(bytes: ByteArray, name: String) = DshPickedAttachment(
        name = name,
        mediaType = "image/png",
        bytes = bytes,
        kind = DshAttachmentKind.IMAGE,
        width = 100,
        height = 100
    )

    private fun png(width: Int, height: Int): ByteArray {
        val header = byteArrayOf(
            0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
            0, 0, 0, 13, 'I'.code.toByte(), 'H'.code.toByte(), 'D'.code.toByte(), 'R'.code.toByte()
        )
        return header + be32(width) + be32(height)
    }

    private fun be32(value: Int) = byteArrayOf(
        (value shr 24).toByte(), (value shr 16).toByte(), (value shr 8).toByte(), value.toByte()
    )
}
