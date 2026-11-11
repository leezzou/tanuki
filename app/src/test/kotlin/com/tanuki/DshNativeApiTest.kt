package com.tanuki

import com.tanuki.web.DshApiProtocol
import com.tanuki.web.DshEndpoint
import com.tanuki.web.DshMessageRole
import com.tanuki.web.DshNativeClient
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class DshNativeApiTest {
    @Test fun parsesOfficialSessionListTitlesAndCuts() {
        val body = responseOk(
            "rpc-list",
            JSONObject().put(
                "items",
                JSONArray()
                    .put(session("s1", "修复登录", 2_000L, asOfSeq = 12))
                    .put(session("s2", "", 1_000L, asOfSeq = null))
            )
        )
        val sessions = DshApiProtocol.parseSessionList(body)
        assertEquals(listOf("s1", "s2"), sessions.map { it.id })
        assertEquals("修复登录", sessions[0].title)
        assertEquals(12, sessions[0].asOfSeq)
        assertEquals("新对话", sessions[1].displayTitle)
        assertTrue(sessions[0].running)
    }

    @Test fun absentNullAndMalformedSessionTitlesNeverRenderNull() {
        val items = JSONArray()
        listOf(JSONObject.NULL, "", "   ", "null", " NULL ", 42).forEachIndexed { index, title ->
            val item = session("s$index", "", index.toLong(), null)
            item.getJSONObject("projections").getJSONObject("values").put("title", title)
            items.put(item)
        }
        items.put(JSONObject().put("sessionId", "missing").put("updatedAt", 9L))
        val sessions = DshApiProtocol.parseSessionList(responseOk("rpc-titles", JSONObject().put("items", items)))
        assertEquals(7, sessions.size)
        assertTrue(sessions.all { it.displayTitle == "新对话" })
        assertEquals("", sessions.single { it.id == "s0" }.title)
        assertEquals("", sessions.single { it.id == "s5" }.title)
    }

    @Test fun projectionCutOnlyAcceptsSequencedValues() {
        val sequenced = responseOk(
            "rpc-1",
            JSONObject().put("kind", "sequenced").put("asOfSeq", 12).put("values", JSONObject())
        )
        assertEquals(12, DshApiProtocol.parseProjectionSeq(sequenced))
        val migration = responseOk("rpc-2", JSONObject().put("kind", "migration-required").put("values", JSONObject()))
        assertNull(DshApiProtocol.parseProjectionSeq(migration))
        val nullValue = JSONObject()
            .put("type", "server-response")
            .put("rpcId", "rpc-3")
            .put("result", JSONObject().put("ok", true).put("value", JSONObject.NULL))
            .toString()
        assertNull(DshApiProtocol.parseProjectionSeq(nullValue))
    }

    @Test fun parsesOnlyTextFromOfficialUserAndAssistantEvents() {
        val records = JSONArray()
            .put(
                event(
                    "user/message", 1,
                    JSONObject()
                        .put("source", JSONObject().put("kind", "user"))
                        .put(
                            "content",
                            JSONArray()
                                .put(JSONObject().put("type", "text").put("text", "请检查登录"))
                                .put(JSONObject().put("type", "image").put("data", "ignored"))
                        )
                )
            )
            .put(
                event(
                    "assistant/message", 2,
                    JSONObject().put(
                        "message",
                        JSONObject().put(
                            "content",
                            JSONArray()
                                .put(JSONObject().put("type", "text").put("text", "已定位到 token 刷新"))
                                .put(JSONObject().put("type", "tool-call").put("name", "read"))
                        )
                    )
                )
            )
            .put(event("tool/result", 3, JSONObject().put("content", JSONArray().put(JSONObject().put("type", "text").put("text", "忽略")))))
            .put(event("turn/end", 4, JSONObject().put("reason", "completed")))
        val page = DshApiProtocol.parsePage(
            responseOk("rpc-page", JSONObject().put("records", records).put("hasMore", true))
        )
        assertEquals(2, page.messages.size)
        assertEquals(DshMessageRole.USER, page.messages[0].role)
        assertEquals("请检查登录", page.messages[0].text)
        assertEquals(DshMessageRole.ASSISTANT, page.messages[1].role)
        assertEquals("已定位到 token 刷新", page.messages[1].text)
        assertTrue(page.hasMore)
    }

    @Test fun skipsReplaceSurfaceCopiesAndInjectedContext() {
        val records = JSONArray()
            .put(
                event(
                    "user/message", 1,
                    JSONObject().put("source", JSONObject().put("kind", "user"))
                        .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", "真实用户消息")))
                )
            )
            .put(
                event(
                    "user/message", 2,
                    JSONObject().put("source", JSONObject().put("kind", "compact-checkpoint"))
                        .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", "替换副本"))),
                    surfaceOp = JSONObject().put("op", "replace").put("startSeq", 1).put("endSeq", 1)
                )
            )
            .put(
                event(
                    "user/message", 3,
                    JSONObject().put("source", JSONObject().put("kind", "agent"))
                        .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", "注入上下文")))
                )
            )
            .put(
                event(
                    "user/message", 4,
                    JSONObject().put("source", JSONObject().put("kind", "user"))
                        .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", "缺少 surfaceOp"))),
                    surfaceOp = null
                )
            )
            .put(
                event(
                    "assistant/message", 5,
                    JSONObject().put(
                        "message",
                        JSONObject().put("content", JSONArray().put(JSONObject().put("type", "text").put("text", "真实回答")))
                    )
                )
            )
            .put(
                event(
                    "assistant/message", 6,
                    JSONObject().put(
                        "message",
                        JSONObject().put("content", JSONArray().put(JSONObject().put("type", "text").put("text", "替换回答")))
                    ),
                    surfaceOp = JSONObject().put("op", "replace").put("startSeq", 5).put("endSeq", 5)
                )
            )
        val page = DshApiProtocol.parsePage(responseOk("rpc-page", JSONObject().put("records", records).put("hasMore", false)))
        assertEquals(listOf("真实用户消息", "真实回答"), page.messages.map { it.text })
    }

    @Test fun rejectsBusinessErrorWithoutEchoingResponseDetails() {
        val body = JSONObject()
            .put("type", "server-response")
            .put("rpcId", "rpc-x")
            .put(
                "result",
                JSONObject().put(
                    "ok", false
                ).put(
                    "error",
                    JSONObject().put("code", "session/conflict")
                        .put("message", "token=should-not-appear")
                        .put("details", JSONObject())
                )
            ).toString()
        val error = runCatching { DshApiProtocol.parseSessionList(body) }.exceptionOrNull()
        assertTrue(error is com.tanuki.web.DshApiException)
        assertEquals("session/conflict", (error as com.tanuki.web.DshApiException).code)
        assertFalse(error.message.orEmpty().contains("should-not-appear"))
    }

    @Test fun cookieExtractionKeepsOnlyOneSessionPair() {
        val cookie = DshApiProtocol.cookieFrom(
            listOf(
                "dsh-session=abc.def; Path=/; HttpOnly; SameSite=Strict",
                "other=value; Path=/"
            )
        )
        assertEquals("dsh-session=abc.def", cookie)
        assertNull(DshApiProtocol.cookieFrom(listOf("no-equals", "=empty", "bad name=value", "name=bad value")))
        assertEquals("name=bad", DshApiProtocol.cookieFrom(listOf("name=bad;value")))
        assertNull(DshApiProtocol.cookieFrom(listOf("name=bad\r\nInjected: yes")))
    }

    @Test fun responseRpcIdMustMatchRequest() {
        val wrong = responseOk("other", JSONObject().put("items", JSONArray()))
        val error = runCatching { DshApiProtocol.checkEnvelope(wrong, "expected") }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertFalse(error!!.message.orEmpty().contains("other"))
    }

    @Test fun clientUsesOnlyOfficialEndpointsAndSendsCookieOnlyToApi() {
        val requests = CopyOnWriteArrayList<TestRequest>()
        TestServer { request ->
            requests += request
            when {
                request.target.startsWith("/connect?") -> TestResponse(
                    302,
                    listOf(
                        "Location" to "./api/remote.mux",
                        "Set-Cookie" to "dsh-session=abc; Path=/; HttpOnly"
                    ),
                    ""
                )
                request.target == "/api/session/list" -> TestResponse(200, listOf(), okFor(request, sessionListValue()))
                request.target == "/api/session/projections" -> TestResponse(
                    200, listOf(),
                    okFor(request, JSONObject().put("kind", "sequenced").put("asOfSeq", 12).put("values", JSONObject()))
                )
                request.target == "/api/session/page" -> TestResponse(200, listOf(), okFor(request, pageValue()))
                else -> TestResponse(404, listOf(), "not found")
            }
        }.use { server ->
            val launchUrl = "${server.origin}/?token=top-secret"
            val client = DshNativeClient { DshEndpoint(server.origin, launchUrl) }
            val sessions = runBlocking { client.sessions() }
            assertTrue(sessions.exceptionOrNull()?.toString(), sessions.isSuccess)
            assertEquals(listOf("s1"), sessions.getOrThrow().map { it.id })
            val page = runBlocking { client.messages("s1") }
            assertTrue(page.exceptionOrNull()?.toString(), page.isSuccess)
            assertEquals(listOf("请检查登录", "已收到"), page.getOrThrow().messages.map { it.text })
        }

        assertEquals(4, requests.size)
        val connect = requests.first { it.target.startsWith("/connect?") }
        assertTrue(connect.target.contains("token=top-secret"))
        assertTrue(connect.target.contains("interactions=answer"))
        assertNull(connect.headers["cookie"])
        assertNull(connect.headers["origin"])
        val api = requests.filter { it.target.startsWith("/api/") }
        assertEquals(3, api.size)
        api.forEach {
            assertEquals("dsh-session=abc", it.headers["cookie"])
            assertNull(it.headers["origin"])
            assertFalse(it.body.contains("top-secret"))
        }
        val pageRequest = JSONObject(api.first { it.target == "/api/session/page" }.body)
        val args = pageRequest.getJSONObject("payload").getJSONObject("args").getJSONObject("request")
        assertEquals(12, args.getInt("throughSeq"))
        assertEquals("session", args.getJSONObject("address").getString("kind"))
        assertEquals("s1", args.getJSONObject("address").getString("sessionId"))
    }

    @Test fun olderHistoryPageUsesBeforeSeqAsThroughSeqAndBoundsPageSize() {
        val requests = CopyOnWriteArrayList<TestRequest>()
        TestServer { request ->
            requests += request
            when {
                request.target.startsWith("/connect?") -> TestResponse(
                    302,
                    listOf(
                        "Location" to "./api/remote.mux",
                        "Set-Cookie" to "dsh-session=abc; Path=/; HttpOnly"
                    ),
                    ""
                )
                request.target == "/api/session/page" -> TestResponse(200, listOf(), okFor(request, pageValue()))
                else -> TestResponse(404, listOf(), "not found")
            }
        }.use { server ->
            val client = DshNativeClient { DshEndpoint(server.origin, "${server.origin}/?token=top-secret") }
            val page = runBlocking { client.messagesBefore("s1", 41, 50) }
            assertTrue(page.exceptionOrNull()?.toString(), page.isSuccess)
        }
        val request = JSONObject(requests.first { it.target == "/api/session/page" }.body)
        val args = request.getJSONObject("payload").getJSONObject("args").getJSONObject("request")
        assertEquals(41, args.getInt("throughSeq"))
        assertEquals(50, args.getInt("maxMessages"))
        assertEquals("s1", args.getJSONObject("address").getString("sessionId"))
    }

    @Test fun missingEndpointFailsWithoutLeakingLaunchToken() {
        TestServer { request ->
            if (request.target.startsWith("/connect?")) {
                TestResponse(302, listOf("Location" to "./api/remote.mux", "Set-Cookie" to "dsh-session=abc; Path=/"), "")
            } else {
                TestResponse(404, listOf(), "not found")
            }
        }.use { server ->
            val client = DshNativeClient { DshEndpoint(server.origin, "${server.origin}/?token=top-secret") }
            val result = runBlocking { client.sessions() }
            assertTrue(result.isFailure)
            val text = result.exceptionOrNull()?.message.orEmpty()
            assertFalse(text.contains("top-secret"))
            assertTrue(text.contains("HTTP 404"))
        }
    }

    @Test fun doesNotRetry401AfterReset() {
        val apiStarted = CountDownLatch(1)
        val releaseApi = CountDownLatch(1)
        val connectCount = AtomicInteger()
        val apiCount = AtomicInteger()
        val apiCookies = CopyOnWriteArrayList<String>()
        TestServer { request ->
            when {
                request.target.startsWith("/connect?") -> {
                    val attempt = connectCount.incrementAndGet()
                    TestResponse(302, listOf("Set-Cookie" to "dsh-session=c$attempt; Path=/"), "")
                }
                request.target == "/api/session/list" -> {
                    val attempt = apiCount.incrementAndGet()
                    apiCookies += request.headers["cookie"].orEmpty()
                    if (attempt == 1) {
                        apiStarted.countDown()
                        releaseApi.await(5, TimeUnit.SECONDS)
                        TestResponse(401, listOf(), "unauthorized")
                    } else {
                        TestResponse(200, listOf(), okFor(request, sessionListValue()))
                    }
                }
                else -> TestResponse(404, listOf(), "not found")
            }
        }.use { server ->
            val client = DshNativeClient { DshEndpoint(server.origin, "${server.origin}/?token=top-secret") }
            val firstResult = AtomicReference<Result<List<com.tanuki.web.DshSession>>?>()
            val first = Thread { firstResult.set(runBlocking { client.sessions() }) }.apply { isDaemon = true; start() }
            assertTrue("API request was not reached", apiStarted.await(2, TimeUnit.SECONDS))
            client.reset()
            releaseApi.countDown()
            first.join(5_000)
            assertFalse(first.isAlive)
            assertTrue("reset request must not retry its 401", firstResult.get()?.isFailure == true)
            assertEquals("old 401 must not perform another /connect", 1, connectCount.get())
            val second = runBlocking { client.sessions() }
            assertTrue(second.exceptionOrNull()?.toString(), second.isSuccess)
            assertEquals(2, connectCount.get())
            assertTrue(apiCookies.contains("dsh-session=c2"))
            assertFalse(apiCookies.contains("dsh-session=c3"))
        }
    }

    @Test fun delayed401AfterResetDoesNotInvalidateNewRequest() {
        val apiStarted = CountDownLatch(1)
        val releaseApi = CountDownLatch(1)
        val connectCount = AtomicInteger()
        val apiCount = AtomicInteger()
        val apiCookies = CopyOnWriteArrayList<String>()
        TestServer { request ->
            when {
                request.target.startsWith("/connect?") -> {
                    val attempt = connectCount.incrementAndGet()
                    TestResponse(302, listOf("Set-Cookie" to "dsh-session=c$attempt; Path=/"), "")
                }
                request.target == "/api/session/list" -> {
                    val attempt = apiCount.incrementAndGet()
                    apiCookies += request.headers["cookie"].orEmpty()
                    if (attempt == 1) {
                        apiStarted.countDown()
                        releaseApi.await(5, TimeUnit.SECONDS)
                        TestResponse(401, listOf(), "unauthorized")
                    } else {
                        TestResponse(200, listOf(), okFor(request, sessionListValue()))
                    }
                }
                else -> TestResponse(404, listOf(), "not found")
            }
        }.use { server ->
            val client = DshNativeClient { DshEndpoint(server.origin, "${server.origin}/?token=top-secret") }
            val staleResult = AtomicReference<Result<List<com.tanuki.web.DshSession>>?>()
            val stale = Thread { staleResult.set(runBlocking { client.sessions() }) }.apply { isDaemon = true; start() }
            assertTrue("delayed 401 was not reached", apiStarted.await(2, TimeUnit.SECONDS))
            // 旧请求等待 401 时 reset，并启动新请求 B；B 必须完成且 Cookie 不被旧请求清掉。
            client.reset()
            val fresh = runBlocking { client.sessions() }
            assertTrue(fresh.exceptionOrNull()?.toString(), fresh.isSuccess)
            assertEquals("new request should have its own /connect", 2, connectCount.get())
            assertTrue(apiCookies.contains("dsh-session=c2"))
            releaseApi.countDown()
            stale.join(5_000)
            assertFalse(stale.isAlive)
            assertTrue("stale 401 must fail after reset", staleResult.get()?.isFailure == true)
            assertEquals("stale 401 must not perform another /connect", 2, connectCount.get())
            assertFalse("stale 401 must not publish a new cookie", apiCookies.contains("dsh-session=c3"))
        }
    }

    @Test fun resetReturnsImmediatelyDuringDelayedAuthenticationAndRejectsStaleCookie() {
        val connectStarted = CountDownLatch(1)
        val releaseConnect = CountDownLatch(1)
        val connectCount = AtomicInteger()
        val apiCookies = CopyOnWriteArrayList<String>()
        TestServer { request ->
            when {
                request.target.startsWith("/connect?") -> {
                    val attempt = connectCount.incrementAndGet()
                    if (attempt == 1) {
                        connectStarted.countDown()
                        releaseConnect.await(5, TimeUnit.SECONDS)
                    }
                    TestResponse(
                        302,
                        listOf("Set-Cookie" to "dsh-session=${if (attempt == 1) "stale" else "fresh"}; Path=/"),
                        ""
                    )
                }
                request.target == "/api/session/list" -> {
                    apiCookies += request.headers["cookie"].orEmpty()
                    TestResponse(200, listOf(), okFor(request, sessionListValue()))
                }
                else -> TestResponse(404, listOf(), "not found")
            }
        }.use { server ->
            val client = DshNativeClient { DshEndpoint(server.origin, "${server.origin}/?token=top-secret") }
            val firstResult = AtomicReference<Result<List<com.tanuki.web.DshSession>>?>()
            val first = Thread { firstResult.set(runBlocking { client.sessions() }) }.apply { isDaemon = true; start() }
            assertTrue("delayed /connect was not reached", connectStarted.await(2, TimeUnit.SECONDS))
            val resetDone = CountDownLatch(1)
            val resetter = Thread { client.reset(); resetDone.countDown() }.apply { isDaemon = true; start() }
            val resetWasFast = resetDone.await(500, TimeUnit.MILLISECONDS)
            releaseConnect.countDown()
            assertTrue("reset() waited for the delayed authentication I/O", resetWasFast)
            first.join(5_000)
            assertFalse("first request did not finish after releasing /connect", first.isAlive)
            assertTrue("stale authentication should be rejected", firstResult.get()?.isFailure == true)
            val second = runBlocking { client.sessions() }
            assertTrue(second.exceptionOrNull()?.toString(), second.isSuccess)
            assertTrue(apiCookies.contains("dsh-session=fresh"))
            assertFalse(apiCookies.contains("dsh-session=stale"))
            resetter.join(1_000)
        }
    }

    @Test fun transportCallReturnsOfficialValueAndKeepsBusinessCode() {
        var fail = false
        TestServer { request ->
            when {
                request.target.startsWith("/connect?") -> TestResponse(
                    302,
                    listOf("Set-Cookie" to "dsh-session=abc; Path=/"),
                    ""
                )
                request.target == "/api/session/list" -> {
                    if (!fail) {
                        TestResponse(
                            200, listOf(),
                            okFor(request, JSONObject().put("items", JSONArray()).put("valueMarker", "ok"))
                        )
                    } else {
                        val rpcId = JSONObject(request.body).getString("rpcId")
                        TestResponse(
                            200, listOf(),
                            JSONObject()
                                .put("type", "server-response")
                                .put("rpcId", rpcId)
                                .put(
                                    "result",
                                    JSONObject().put("ok", false)
                                        .put("error", JSONObject().put("code", "session/not-found"))
                                ).toString()
                        )
                    }
                }
                else -> TestResponse(404, listOf(), "not found")
            }
        }.use { server ->
            val client = DshNativeClient { DshEndpoint(server.origin, "${server.origin}/?token=top-secret") }
            val value = runBlocking { client.call("session/list", JSONObject()) }
            assertTrue(value is JSONObject)
            assertEquals("ok", (value as JSONObject).getString("valueMarker"))
            fail = true
            val error = runCatching { runBlocking { client.call("session/list", JSONObject()) } }.exceptionOrNull()
            assertTrue(error is com.tanuki.web.DshApiException)
            assertEquals("session/not-found", (error as com.tanuki.web.DshApiException).code)
        }
    }

    private fun session(id: String, title: String, updatedAt: Long, asOfSeq: Int?): JSONObject {
        val projections = JSONObject().put("kind", "sequenced").put("values", JSONObject().put("title", title))
        if (asOfSeq != null) projections.put("asOfSeq", asOfSeq)
        return JSONObject()
            .put("sessionId", id)
            .put("updatedAt", updatedAt)
            .put("running", true)
            .put("blank", false)
            .put("agentAvailable", true)
            .put("projections", projections)
    }

    private fun event(type: String, seq: Int, data: JSONObject, surfaceOp: Any? = "append"): JSONObject {
        val event = JSONObject().put("type", type).put("seq", seq).put("time", 1_000L + seq).put("data", data)
        if (surfaceOp != null) event.put("surfaceOp", surfaceOp)
        return JSONObject().put("type", "event").put("event", event)
    }

    private fun sessionListValue(): JSONObject = JSONObject().put(
        "items",
        JSONArray().put(session("s1", "修复登录", 2_000L, 12))
    )

    private fun pageValue(): JSONObject = JSONObject().put(
        "records",
        JSONArray()
            .put(
                event(
                    "user/message", 1,
                    JSONObject().put("source", JSONObject().put("kind", "user"))
                        .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", "请检查登录")))
                )
            )
            .put(
                event(
                    "assistant/message", 2,
                    JSONObject().put(
                        "message",
                        JSONObject().put("content", JSONArray().put(JSONObject().put("type", "text").put("text", "已收到")))
                    )
                )
            )
    ).put("hasMore", false)

    private fun okFor(request: TestRequest, value: Any): String {
        val rpcId = JSONObject(request.body).getString("rpcId")
        return responseOk(rpcId, value)
    }

    private fun responseOk(rpcId: String, value: Any): String = JSONObject()
        .put("type", "server-response")
        .put("rpcId", rpcId)
        .put("result", JSONObject().put("ok", true).put("value", value))
        .toString()

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
                        // 每个连接独立处理，便于回归“旧请求阻塞时新请求仍可完成”。
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

        private fun serve(socket: Socket) {
            socket.soTimeout = 5_000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(' ')
            if (parts.size < 2) return
            val headers = LinkedHashMap<String, String>()
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                val separator = line.indexOf(':')
                if (separator <= 0) continue
                headers[line.substring(0, separator).trim().lowercase()] = line.substring(separator + 1).trim()
            }
            val length = headers["content-length"]?.toIntOrNull() ?: 0
            val body = CharArray(length)
            var read = 0
            while (read < length) {
                val count = reader.read(body, read, length - read)
                if (count < 0) break
                read += count
            }
            val response = handler(TestRequest(parts[0], parts[1], headers, String(body, 0, read)))
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
