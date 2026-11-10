package com.tanuki

import com.tanuki.web.DshConnectionState
import com.tanuki.web.DshEndpoint
import kotlinx.coroutines.CompletableDeferred
import com.tanuki.web.DshLiveEvent
import com.tanuki.web.DshMuxProtocol
import com.tanuki.web.DshMuxSocket
import com.tanuki.web.DshNativeClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

class DshNativeLiveTest {
    @Test fun sendAndCancelUseOfficialUnaryEndpoints() {
        val requests = CopyOnWriteArrayList<TestRequest>()
        TestServer { request ->
            requests += request
            when {
                request.target.startsWith("/connect?") -> connectResponse()
                request.target == "/api/session/prompt" -> TestResponse(200, emptyList(), accepted(request))
                request.target == "/api/session/cancel" -> TestResponse(200, emptyList(), accepted(request))
                else -> TestResponse(404, emptyList(), "not found")
            }
        }.use { server ->
            val client = DshNativeClient(
                socketFactory = { error("unary calls must not open a mux socket") },
                endpointProvider = { DshEndpoint(server.origin, "${server.origin}/?token=top-secret") }
            )
            val sent = runBlocking { client.send("s1", "  请检查登录  ") }
            assertTrue(sent.exceptionOrNull()?.toString(), sent.isSuccess)
            val cancelled = runBlocking { client.cancel("s1") }
            assertTrue(cancelled.exceptionOrNull()?.toString(), cancelled.isSuccess)
        }

        val connect = requests.first { it.target.startsWith("/connect?") }
        assertTrue(connect.target.contains("interactions=answer"))
        val prompt = requests.first { it.target == "/api/session/prompt" }
        assertFalse(prompt.body.contains("top-secret"))
        val args = JSONObject(prompt.body).getJSONObject("payload").getJSONObject("args").getJSONObject("request")
        assertEquals("s1", args.getString("sessionId"))
        assertEquals("queue", args.getString("mode"))
        assertTrue(args.getString("requestId").isNotBlank())
        assertEquals("请检查登录", args.getJSONArray("content").getJSONObject(0).getString("text"))

        val cancelArgs = JSONObject(requests.first { it.target == "/api/session/cancel" }.body)
            .getJSONObject("payload").getJSONObject("args").getJSONObject("request")
        assertEquals("s1", cancelArgs.getString("sessionId"))
    }

    @Test fun rejectedSendResultIsFailureNotFalseSuccess() {
        TestServer { request ->
            when {
                request.target.startsWith("/connect?") -> connectResponse()
                request.target == "/api/session/prompt" -> TestResponse(
                    200,
                    emptyList(),
                    JSONObject()
                        .put("type", "server-response")
                        .put("rpcId", JSONObject(request.body).getString("rpcId"))
                        .put("result", JSONObject().put("ok", true).put("value", JSONObject().put("accepted", false)))
                        .toString()
                )
                else -> TestResponse(404, emptyList(), "not found")
            }
        }.use { server ->
            val client = DshNativeClient(
                socketFactory = { error("unary calls must not open a mux socket") },
                endpointProvider = { DshEndpoint(server.origin, "${server.origin}/?token=top-secret") }
            )
            val result = runBlocking { client.send("s1", "未接受") }
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()!!.message.orEmpty().contains("未接受"))
        }
    }

    @Test fun liveStreamsOfficialSnapshotAssistantTextAndForwardsApproval() = runBlocking {
        TestServer { request ->
            when {
                request.target.startsWith("/connect?") -> connectResponse()
                request.target == "/api/\$events/result" -> nextResponse(request)
                else -> TestResponse(404, emptyList(), "not found")
            }
        }.use { server ->
            lateinit var socket: ScriptedMuxSocket
            socket = ScriptedMuxSocket { current, endpoint, streamId ->
                when (endpoint) {
                    DshMuxProtocol.EVENTS_ENDPOINT -> {
                        current.emit(item(streamId, JSONObject().put("type", "ready").put("clientId", "client-1")))
                        current.emit(
                            item(
                                streamId,
                                JSONObject()
                                    .put("type", "waterfall")
                                    .put("event", "approval/request")
                                    .put("eventId", "event-1")
                                    .put("agentId", "s1")
                                    .put(
                                        "request",
                                        JSONObject().put("toolName", "Bash").put("reason", "需要运行命令")
                                    )
                            )
                        )
                        current.emit(
                            item(
                                streamId,
                                JSONObject()
                                    .put("type", "emit")
                                    .put("event", "api-session/status")
                                    .put("args", JSONArray().put("s1").put(true))
                            )
                        )
                    }
                    DshMuxProtocol.FOLLOW_ENDPOINT -> {
                        current.emit(
                            item(
                                streamId,
                                JSONObject()
                                    .put("type", "snapshot")
                                    .put("hasMore", false)
                                    .put("records", JSONArray().put(record("user/message", 1, user("请检查登录"))))
                            )
                        )
                        current.emit(
                            item(
                                streamId,
                                JSONObject()
                                    .put("type", "assistant-stream")
                                    .put(
                                        "frame",
                                        JSONObject().put("type", "chunk").put("attemptId", "a1")
                                            .put(
                                                "chunk",
                                                JSONObject().put("type", "text-delta").put("index", 0).put("text", "正在回复")
                                            )
                                    )
                            )
                        )
                        current.emit(
                            item(
                                streamId,
                                JSONObject()
                                    .put("type", "event")
                                    .put("event", event("assistant/message", 2, assistant("最终回复")))
                            )
                        )
                        current.finish()
                    }
                }
            }
            val client = DshNativeClient(
                socketFactory = { socket },
                endpointProvider = { DshEndpoint(server.origin, "${server.origin}/?token=top-secret") }
            )
            val events = client.live("s1").toList()
            assertTrue(events.toString(), events.first() is DshLiveEvent.Connected)
            val snapshot = events.filterIsInstance<DshLiveEvent.Snapshot>().single()
            assertEquals("请检查登录", snapshot.messages.single().text)
            assertTrue(events.filterIsInstance<DshLiveEvent.Streaming>().any { it.text == "正在回复" })
            assertEquals("最终回复", events.filterIsInstance<DshLiveEvent.Message>().single().message.text)
            assertTrue(events.filterIsInstance<DshLiveEvent.Running>().single().running)

            val opens = socket.sent.map(::JSONObject).filter { it.optString("type") == "open" }
            assertEquals(2, opens.size)
            assertTrue(opens.any { it.getString("endpoint") == DshMuxProtocol.EVENTS_ENDPOINT })
            assertTrue(opens.any { it.getString("endpoint") == DshMuxProtocol.FOLLOW_ENDPOINT })
            assertTrue(socket.closed)
            assertEquals("dsh-session=abc", socket.cookie)
        }
    }

    @Test fun chatPeerForwardsWaterfallNextInsteadOfShowingApproval() = runBlocking {
        val requests = CopyOnWriteArrayList<TestRequest>()
        TestServer { request ->
            requests += request
            when {
                request.target.startsWith("/connect?") -> connectResponse()
                request.target == "/api/\$events/result" -> TestResponse(
                    200,
                    emptyList(),
                    JSONObject()
                        .put("type", "server-response")
                        .put("rpcId", JSONObject(request.body).getString("rpcId"))
                        .put("result", JSONObject().put("ok", true))
                        .toString()
                )
                else -> TestResponse(404, emptyList(), "not found")
            }
        }.use { server ->
            val socket = ScriptedMuxSocket { current, endpoint, streamId ->
                if (endpoint == DshMuxProtocol.EVENTS_ENDPOINT) {
                    current.emit(item(streamId, JSONObject().put("type", "ready").put("clientId", "client-1")))
                    current.emit(
                        item(
                            streamId,
                            JSONObject()
                                .put("type", "waterfall")
                                .put("event", "approval/request")
                                .put("eventId", "event-1")
                                .put("agentId", "s1")
                                .put("request", JSONObject().put("toolName", "Bash"))
                        )
                    )
                }
            }
            val client = DshNativeClient(
                socketFactory = { socket },
                endpointProvider = { DshEndpoint(server.origin, "${server.origin}/?token=top-secret") }
            )
            val collector = CoroutineScope(Dispatchers.Default).launch { client.live("s1").collect { } }
            withTimeout(3_000) {
                while (requests.none { it.target == "/api/\$events/result" }) delay(20)
            }
            collector.cancelAndJoin()

            val forwarded = requests.first { it.target == "/api/\$events/result" }
            val args = JSONObject(forwarded.body).getJSONObject("payload").getJSONObject("args")
            assertEquals("client-1", args.getString("clientId"))
            assertEquals("event-1", args.getString("eventId"))
            assertEquals("next", args.getJSONObject("outcome").getString("kind"))
            assertFalse(args.getJSONObject("outcome").has("value"))
            assertFalse(forwarded.body.contains("top-secret"))
        }
    }

    @Test fun chatPeerForwardsNextAfterReconnect() = runBlocking {
        val requests = CopyOnWriteArrayList<TestRequest>()
        TestServer { request ->
            requests += request
            when {
                request.target.startsWith("/connect?") -> connectResponse()
                request.target == "/api/\$events/result" -> TestResponse(
                    200,
                    emptyList(),
                    JSONObject()
                        .put("type", "server-response")
                        .put("rpcId", JSONObject(request.body).getString("rpcId"))
                        .put("result", JSONObject().put("ok", true))
                        .toString()
                )
                else -> TestResponse(404, emptyList(), "not found")
            }
        }.use { server ->
            fun socket() = ScriptedMuxSocket { current, endpoint, streamId ->
                if (endpoint == DshMuxProtocol.EVENTS_ENDPOINT) {
                    current.emit(item(streamId, JSONObject().put("type", "ready").put("clientId", "client-1")))
                    current.emit(
                        item(
                            streamId,
                            JSONObject()
                                .put("type", "waterfall")
                                .put("event", "approval/request")
                                .put("eventId", "event-1")
                                .put("agentId", "s1")
                                .put("request", JSONObject().put("toolName", "Bash"))
                        )
                    )
                }
            }
            val sockets = mutableListOf(socket(), socket())
            val client = DshNativeClient(
                socketFactory = { sockets.removeAt(0) },
                endpointProvider = { DshEndpoint(server.origin, "${server.origin}/?token=top-secret") }
            )
            fun collectUntilForwarded(expected: Int) = runBlocking {
                val collector = CoroutineScope(Dispatchers.Default).launch { client.live("s1").collect { } }
                withTimeout(3_000) {
                    while (requests.count { it.target == "/api/\$events/result" } < expected) delay(20)
                }
                collector.cancelAndJoin()
            }
            collectUntilForwarded(1)
            client.reset()
            collectUntilForwarded(2)

            val forwarded = requests.filter { it.target == "/api/\$events/result" }
            assertEquals(2, forwarded.size)
            forwarded.forEach { request ->
                val outcome = JSONObject(request.body)
                    .getJSONObject("payload").getJSONObject("args").getJSONObject("outcome")
                assertEquals("next", outcome.getString("kind"))
            }
        }
    }

    @Test fun rejectedNextClosesChatPeerAndReportsFailure() = assertNextFailure(status = 503)

    @Test fun expiredNextDoesNotRetryOldClientIdWithNewCookie() = assertNextFailure(status = 401)

    @Test fun businessRejectedNextClosesChatPeer() = assertNextFailure(status = 200, accepted = false)

    private fun assertNextFailure(status: Int, accepted: Boolean = true) = runBlocking {
        val requests = CopyOnWriteArrayList<TestRequest>()
        TestServer { request ->
            requests += request
            when {
                request.target.startsWith("/connect?") -> connectResponse()
                request.target == "/api/\$events/result" -> nextResponse(request, status, accepted)
                else -> TestResponse(404, emptyList(), "not found")
            }
        }.use { server ->
            val socket = approvalSocket()
            val client = DshNativeClient(
                socketFactory = { socket },
                endpointProvider = { DshEndpoint(server.origin, "${server.origin}/?token=top-secret") }
            )
            val events = withTimeout(3_000) { client.live("s1").toList() }
            val failure = events.filterIsInstance<DshLiveEvent.Failure>().single()
            assertTrue(failure.message, failure.message.contains("审批转交失败"))
            assertFalse(failure.message.contains("top-secret"))
            assertTrue("转交失败必须释放聊天回答者", socket.closed)
            assertEquals(DshConnectionState.FAILED, client.connection.value)
            assertEquals("不能用新 Peer 回答旧事件", 1, requests.count { it.target.startsWith("/connect?") })
            assertEquals(1, requests.count { it.target == "/api/\$events/result" })
        }
    }

    @Test fun cancelledNextPropagatesCancellationAndClosesChatSocket() = runBlocking {
        val requestSeen = CompletableDeferred<Unit>()
        val releaseResponse = CountDownLatch(1)
        TestServer { request ->
            when {
                request.target.startsWith("/connect?") -> connectResponse()
                request.target == "/api/\$events/result" -> {
                    requestSeen.complete(Unit)
                    // 模拟 Host 尚未接收 next：客户端取消应中止等待，不等服务端发回正文。
                    check(releaseResponse.await(5, TimeUnit.SECONDS))
                    nextResponse(request)
                }
                else -> TestResponse(404, emptyList(), "not found")
            }
        }.use { server ->
            val socket = approvalSocket()
            val client = DshNativeClient(
                socketFactory = { socket },
                endpointProvider = { DshEndpoint(server.origin, "${server.origin}/?token=top-secret") }
            )
            val events = CopyOnWriteArrayList<DshLiveEvent>()
            val collector = launch(Dispatchers.IO) { client.live("s1").collect { events += it } }
            try {
                withTimeout(3_000) { requestSeen.await() }
                withTimeout(2_000) { collector.cancelAndJoin() }
                assertTrue(collector.isCancelled)
                assertTrue(socket.closed)
                assertTrue("取消不能伪装成转交失败", events.none { it is DshLiveEvent.Failure })
                assertEquals(DshConnectionState.DISCONNECTED, client.connection.value)
            } finally {
                releaseResponse.countDown()
                collector.cancelAndJoin()
            }
        }
    }

    @Test fun waterfallBeforeReadyReleasesPeerInsteadOfSilentlyDroppingIt() = runBlocking {
        val requests = CopyOnWriteArrayList<TestRequest>()
        TestServer { request ->
            requests += request
            connectResponse()
        }.use { server ->
            val socket = approvalSocket(sendReady = false)
            val client = DshNativeClient(
                socketFactory = { socket },
                endpointProvider = { DshEndpoint(server.origin, "${server.origin}/?token=top-secret") }
            )
            val events = withTimeout(3_000) { client.live("s1").toList() }
            assertTrue(events.filterIsInstance<DshLiveEvent.Failure>().single().message.contains("审批转交失败"))
            assertTrue(socket.closed)
            assertTrue(requests.none { it.target == "/api/\$events/result" })
        }
    }

    private fun approvalSocket(sendReady: Boolean = true): ScriptedMuxSocket = ScriptedMuxSocket { current, endpoint, streamId ->
        if (endpoint == DshMuxProtocol.EVENTS_ENDPOINT) {
            if (sendReady) current.emit(item(streamId, JSONObject().put("type", "ready").put("clientId", "client-1")))
            current.emit(item(streamId, JSONObject()
                .put("type", "waterfall")
                .put("event", "approval/request")
                .put("eventId", "event-1")
                .put("agentId", "s1")
                .put("request", JSONObject().put("toolName", "Bash"))))
        }
    }

    private fun nextResponse(request: TestRequest, status: Int = 200, accepted: Boolean = true): TestResponse {
        val result = JSONObject().put("ok", accepted)
        if (!accepted) result.put("error", JSONObject().put("code", "gateway/result-rejected"))
        return TestResponse(status, emptyList(), JSONObject()
            .put("type", "server-response")
            .put("rpcId", JSONObject(request.body).getString("rpcId"))
            .put("result", result)
            .toString())
    }

    private fun connectResponse(): TestResponse = TestResponse(
        302,
        listOf(
            "Location" to "./api/remote.mux",
            "Set-Cookie" to "dsh-session=abc; Path=/; HttpOnly"
        ),
        ""
    )

    private fun accepted(request: TestRequest): String = JSONObject()
        .put("type", "server-response")
        .put("rpcId", JSONObject(request.body).getString("rpcId"))
        .put("result", JSONObject().put("ok", true).put("value", JSONObject().put("accepted", true)))
        .toString()

    private fun item(streamId: String, value: JSONObject): String =
        JSONObject().put("type", "item").put("streamId", streamId).put("value", value).toString()

    private fun record(type: String, seq: Int, data: JSONObject): JSONObject =
        JSONObject().put("type", "event").put("event", event(type, seq, data))

    private fun event(type: String, seq: Int, data: JSONObject): JSONObject =
        JSONObject().put("type", type).put("seq", seq).put("time", seq.toLong()).put("data", data)
            .put("surfaceOp", "append")

    private fun user(text: String): JSONObject = JSONObject()
        .put("source", JSONObject().put("kind", "user"))
        .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", text)))

    private fun assistant(text: String): JSONObject = JSONObject().put(
        "message",
        JSONObject().put("content", JSONArray().put(JSONObject().put("type", "text").put("text", text)))
    )

    /** 记录客户端发送的 mux 帧，并按脚本回流官方帧。 */
    private class ScriptedMuxSocket(
        private val script: suspend (ScriptedMuxSocket, String, String) -> Unit
    ) : DshMuxSocket {
        private val incoming = Channel<String>(Channel.UNLIMITED)
        val sent = CopyOnWriteArrayList<String>()
        @Volatile var closed = false
        @Volatile var cookie: String? = null

        override suspend fun connect(origin: String, cookie: String) {
            this.cookie = cookie
        }

        override suspend fun send(text: String) {
            sent += text
            val root = JSONObject(text)
            if (root.optString("type") == "open") {
                script(this, root.getString("endpoint"), root.getString("streamId"))
            }
        }

        override fun incoming(): Flow<String> = incoming.receiveAsFlow()

        suspend fun emit(text: String) {
            incoming.send(text)
        }

        fun finish() {
            incoming.close()
        }

        override fun close() {
            closed = true
            incoming.close()
        }
    }

    private data class TestRequest(
        val method: String,
        val target: String,
        val headers: Map<String, String>,
        val body: String
    )

    private data class TestResponse(val status: Int, val headers: List<Pair<String, String>>, val body: String)

    private class TestServer(private val handler: (TestRequest) -> TestResponse) : AutoCloseable {
        val origin: String
        private val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        private val failure = AtomicReference<Throwable?>(null)
        private val thread: Thread

        init {
            origin = "http://127.0.0.1:${server.localPort}"
            thread = Thread {
                try {
                    while (!server.isClosed) {
                        val socket = server.accept()
                        Thread {
                            try {
                                socket.use { serve(it) }
                            } catch (_: SocketException) {
                                // 关闭监听/客户端断开属正常收尾。
                            } catch (error: Throwable) {
                                failure.compareAndSet(null, error)
                            }
                        }.apply { isDaemon = true; start() }
                    }
                } catch (_: SocketException) {
                    // 关闭监听时 accept 抛出，属正常收尾。
                } catch (error: Throwable) {
                    failure.set(error)
                }
            }.apply { isDaemon = true; start() }
        }

        private fun serve(socket: java.net.Socket) {
            socket.soTimeout = 5_000
            val input = socket.getInputStream()
            fun readLine(): String? {
                val line = java.io.ByteArrayOutputStream()
                while (true) {
                    val value = input.read()
                    if (value < 0) return if (line.size() == 0) null else line.toString("UTF-8")
                    if (value == '\n'.code) break
                    if (value != '\r'.code) line.write(value)
                }
                return line.toString("UTF-8")
            }
            val requestLine = readLine() ?: return
            val parts = requestLine.split(' ')
            if (parts.size < 2) return
            val headers = LinkedHashMap<String, String>()
            while (true) {
                val line = readLine() ?: break
                if (line.isEmpty()) break
                val separator = line.indexOf(':')
                if (separator <= 0) continue
                headers[line.substring(0, separator).trim().lowercase()] = line.substring(separator + 1).trim()
            }
            val length = headers["content-length"]?.toIntOrNull() ?: 0
            val body = ByteArray(length)
            var read = 0
            while (read < length) {
                val count = input.read(body, read, length - read)
                if (count < 0) break
                read += count
            }
            val response = handler(TestRequest(parts[0], parts[1], headers, String(body, 0, read, Charsets.UTF_8)))
            val payload = response.body.toByteArray(Charsets.UTF_8)
            val text = buildString {
                append("HTTP/1.1 ${response.status} Test\r\n")
                response.headers.forEach { (name, value) -> append("$name: $value\r\n") }
                append("Content-Type: application/json; charset=utf-8\r\n")
                append("Content-Length: ${payload.size}\r\n")
                append("Connection: close\r\n\r\n")
            }
            socket.getOutputStream().use { output ->
                output.write(text.toByteArray(Charsets.UTF_8))
                output.write(payload)
                output.flush()
            }
        }

        override fun close() {
            server.close()
            thread.join(2_000)
            failure.get()?.let { throw AssertionError("测试服务器异常", it) }
        }
    }
}
