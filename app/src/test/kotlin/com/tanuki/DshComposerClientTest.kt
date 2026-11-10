package com.tanuki

import com.tanuki.web.DshCommandException
import com.tanuki.web.DshComposerClient
import com.tanuki.web.DshComposerProtocol
import com.tanuki.web.DshComposerRemote
import com.tanuki.web.DshOutgoingPart
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class DshComposerClientTest {
    private data class Call(val method: String, val args: JSONObject)

    private class FakeRemote(var handler: suspend (String, JSONObject) -> Any?) : DshComposerRemote {
        val calls = CopyOnWriteArrayList<Call>()
        var resets = 0
        override suspend fun call(method: String, args: JSONObject): Any? {
            calls += Call(method, args)
            return handler(method, args)
        }

        override fun reset() {
            resets++
        }
    }

    @Test fun imageLimitsUsesProjectionEndpoint() {
        val remote = FakeRemote { method, _ ->
            assertEquals("session/projections", method)
            JSONObject().put(
                "kind", "sequenced"
            ).put(
                "values",
                JSONObject().put("imageLimits", JSONObject().put("maxImageBytes", 1024).put("maxImagesPerMessage", 2))
            )
        }
        val client = DshComposerClient(remote)
        val limits = kotlinx.coroutines.runBlocking { client.imageLimits("s1") }.getOrThrow()
        assertEquals(64 * 1024, limits.maxImageBytes)
        assertEquals(2, limits.maxImagesPerMessage)
        val request = remote.calls.single().args.getJSONObject("request")
        assertEquals("s1", request.getString("sessionId"))
    }

    @Test fun commandsAndReferencesParseOfficialValues() {
        val remote = FakeRemote { method, _ ->
            when (method) {
                "commands/list" -> JSONArray().put(
                    JSONObject().put("name", "compact").put("description", "压缩")
                )
                "fileReferences/list" -> JSONArray().put(
                    JSONObject().put("path", "src/Main.kt").put("kind", "file")
                )
                "sessionReferenceResolver/candidates" -> JSONArray().put(
                    JSONObject().put("mention", "@[会话](dsh-session:abc)")
                        .put("sessionId", "abc").put("label", "会话")
                        .put("sameWorkspace", false).put("createdAt", 1L)
                )
                else -> null
            }
        }
        val client = DshComposerClient(remote)
        val commands = kotlinx.coroutines.runBlocking { client.commands("s1") }.getOrThrow()
        val files = kotlinx.coroutines.runBlocking { client.fileReferences("s1", "src") }.getOrThrow()
        val sessions = kotlinx.coroutines.runBlocking { client.sessionReferences("s1", "会话") }.getOrThrow()
        assertEquals("compact", commands.single().name)
        assertEquals("@src/Main.kt", files.single().mention)
        assertEquals("abc", sessions.single().sessionId)
        assertEquals("s1", remote.calls.first { it.method == "commands/list" }.args.getString("agentId"))
        assertEquals("src", remote.calls.first { it.method == "fileReferences/list" }.args.getString("query"))
    }

    @Test fun uploadReturnsReceiptAndSendsCanonicalBase64() {
        val remote = FakeRemote { method, args ->
            assertEquals("fileUploads/upload", method)
            assertEquals("s1", args.getString("agentId"))
            JSONObject().put("receiptId", "receipt-1").put(
                "file",
                JSONObject().put("attachmentId", "attachment-1")
                    .put("name", "a.txt").put("bytes", 3)
            )
        }
        val client = DshComposerClient(remote)
        val uploaded = kotlinx.coroutines.runBlocking {
            client.uploadFile("s1", "a.txt", "abc".toByteArray())
        }.getOrThrow()
        assertEquals("receipt-1", uploaded.receiptId)
        assertEquals("attachment-1", uploaded.attachmentId)
        assertEquals(3L, uploaded.bytes)
        val request = remote.calls.single().args.getJSONObject("request")
        assertEquals("YWJj", request.getString("data"))
        assertEquals("a.txt", request.getString("name"))
    }

    @Test fun promptRequiresAcceptedTrueAndSendsParts() {
        val remote = FakeRemote { _, _ -> JSONObject().put("accepted", true) }
        val client = DshComposerClient(remote)
        val result = kotlinx.coroutines.runBlocking {
            client.sendPrompt(
                "s1", "r1", "queue",
                listOf(DshOutgoingPart.Text("你好")), "Asia/Shanghai"
            )
        }
        assertTrue(result.isSuccess)
        val request = remote.calls.single().args.getJSONObject("request")
        assertEquals("r1", request.getString("requestId"))
        assertEquals("你好", request.getJSONArray("content").getJSONObject(0).getString("text"))

        val rejected = DshComposerClient(FakeRemote { _, _ -> JSONObject().put("accepted", false) })
        assertTrue(
            kotlinx.coroutines.runBlocking {
                rejected.sendPrompt("s1", "r2", "queue", listOf(DshOutgoingPart.Text("x")), null)
            }.isFailure
        )
    }

    @Test fun commandExecuteMapsBusinessAndUnresolvedResults() {
        val success = DshComposerClient(
            FakeRemote { _, _ -> JSONObject().put("commandId", "c1").put("result", JSONObject().put("kind", "success")) }
        )
        assertTrue(
            kotlinx.coroutines.runBlocking {
                success.executeCommand("s1", "/compact", emptyList())
            }.isSuccess
        )

        val failed = DshComposerClient(
            FakeRemote { _, _ ->
                JSONObject().put("commandId", "c2").put(
                    "result",
                    JSONObject().put("kind", "error").put("text", "命令失败")
                )
            }
        )
        val error = kotlinx.coroutines.runBlocking {
            failed.executeCommand("s1", "/compact", emptyList())
        }.exceptionOrNull()
        assertTrue(error is DshCommandException)
        assertTrue(error!!.message.orEmpty().contains("命令失败"))

        val unresolved = DshComposerClient(FakeRemote { _, _ -> null })
        assertTrue(
            kotlinx.coroutines.runBlocking {
                unresolved.executeCommand("s1", "/unknown", emptyList())
            }.isFailure
        )
    }

    @Test fun resetDelegatesToTransport() {
        val remote = FakeRemote { _, _ -> null }
        DshComposerClient(remote).reset()
        assertEquals(1, remote.resets)
    }

    @Test fun rejectsEmptyUploadAndInvalidPromptIdentity() {
        val client = DshComposerClient(FakeRemote { _, _ -> null })
        assertTrue(kotlinx.coroutines.runBlocking { client.uploadFile("s1", "a", byteArrayOf()) }.isFailure)
        assertTrue(
            kotlinx.coroutines.runBlocking {
                client.sendPrompt("bad id", "r1", "queue", listOf(DshOutgoingPart.Text("x")), null)
            }.isFailure
        )
    }

    @Test fun remoteLoopbackOriginIsAcceptedForProtocol() {
        val endpoint = com.tanuki.web.DshEndpoint(
            "http://127.0.0.1:4567",
            "http://127.0.0.1:4567/?token=secret"
        )
        val url = com.tanuki.web.DshApiProtocol.connectUrl(endpoint)
        assertTrue(url!!.startsWith("http://127.0.0.1:4567/connect?"))
        assertTrue(url.contains("interactions=answer"))
        // 这是实际发送给 /connect 的地址，token 必须保留；不得进入日志或错误文本。
        assertTrue(url.contains("token=secret"))
        assertTrue(DshComposerProtocol.validSessionId("session-1"))
        assertFalse(DshComposerProtocol.validSessionId("session 1"))
    }
}
