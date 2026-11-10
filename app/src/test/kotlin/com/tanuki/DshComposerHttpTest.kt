package com.tanuki

import com.tanuki.web.DshComposerClient
import com.tanuki.web.DshEndpoint
import com.tanuki.web.HttpDshComposerRemote
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/** 合成 loopback 服务，只验证输入/附件客户端的认证与信封边界。 */
class DshComposerHttpTest {
    private data class Request(
        val method: String,
        val target: String,
        val headers: Map<String, String>,
        val body: String
    )

    private data class Response(
        val status: Int,
        val headers: List<Pair<String, String>> = emptyList(),
        val body: String = ""
    )

    @Test fun sendsCookieOnlyToApiAndKeepsTokenOutOfRequestBody() {
        val requests = CopyOnWriteArrayList<Request>()
        TestServer { request ->
            requests += request
            when {
                request.target.startsWith("/connect?") -> Response(
                    302,
                    listOf("Set-Cookie" to "dsh-session=abc; Path=/; HttpOnly"),
                    ""
                )
                request.target == "/api/commands/list" -> Response(
                    200,
                    emptyList(),
                    okFor(
                        request,
                        JSONArray().put(JSONObject().put("name", "compact").put("description", "压缩"))
                    )
                )
                else -> Response(404, emptyList(), "not found")
            }
        }.use { server ->
            val remote = HttpDshComposerRemote {
                DshEndpoint(server.origin, "${server.origin}/?token=top-secret")
            }
            val commands = runBlocking { DshComposerClient(remote).commands("s1") }
            assertTrue(commands.exceptionOrNull()?.toString(), commands.isSuccess)
            assertEquals("compact", commands.getOrThrow().single().name)
        }

        val connect = requests.first { it.target.startsWith("/connect?") }
        assertTrue(connect.target.contains("token=top-secret"))
        assertTrue(connect.target.contains("interactions=none"))
        assertNull(connect.headers["cookie"])
        assertNull(connect.headers["origin"])
        val api = requests.filter { it.target.startsWith("/api/") }
        assertEquals(1, api.size)
        assertEquals("dsh-session=abc", api.single().headers["cookie"])
        assertNull(api.single().headers["origin"])
        assertFalse(api.single().body.contains("top-secret"))
        val envelope = JSONObject(api.single().body)
        assertEquals("client-request", envelope.getString("type"))
        assertEquals("commands/list", envelope.getString("method"))
        assertEquals("s1", envelope.getJSONObject("payload").getJSONObject("args").getString("agentId"))
    }

    @Test fun retries401OnceAndResetReconnects() {
        val connectCount = AtomicInteger()
        val apiCount = AtomicInteger()
        val cookies = CopyOnWriteArrayList<String>()
        TestServer { request ->
            when {
                request.target.startsWith("/connect?") -> {
                    val attempt = connectCount.incrementAndGet()
                    Response(302, listOf("Set-Cookie" to "dsh-session=c$attempt; Path=/"), "")
                }
                request.target == "/api/commands/list" -> {
                    val attempt = apiCount.incrementAndGet()
                    cookies += request.headers["cookie"].orEmpty()
                    if (attempt == 1) Response(401, emptyList(), "unauthorized")
                    else Response(200, emptyList(), okFor(request, JSONArray()))
                }
                else -> Response(404, emptyList(), "not found")
            }
        }.use { server ->
            val remote = HttpDshComposerRemote {
                DshEndpoint(server.origin, "${server.origin}/?token=top-secret")
            }
            val client = DshComposerClient(remote)
            assertTrue(runBlocking { client.commands("s1") }.isSuccess)
            assertEquals(2, connectCount.get())
            assertEquals(2, apiCount.get())
            assertEquals(listOf("dsh-session=c1", "dsh-session=c2"), cookies)

            remote.reset()
            assertTrue(runBlocking { client.commands("s1") }.isSuccess)
            assertEquals(3, connectCount.get())
        }
    }

    @Test fun missingEndpointDoesNotLeakTokenInError() {
        TestServer { request ->
            if (request.target.startsWith("/connect?")) {
                Response(302, listOf("Set-Cookie" to "dsh-session=abc; Path=/"), "")
            } else {
                Response(404, emptyList(), "not found")
            }
        }.use { server ->
            val remote = HttpDshComposerRemote {
                DshEndpoint(server.origin, "${server.origin}/?token=top-secret")
            }
            val result = runBlocking { DshComposerClient(remote).commands("s1") }
            assertTrue(result.isFailure)
            val text = result.exceptionOrNull()?.message.orEmpty()
            assertFalse(text.contains("top-secret"))
            assertTrue(text.contains("HTTP 404"))
        }
    }

    private fun okFor(request: Request, value: Any): String {
        val rpcId = JSONObject(request.body).getString("rpcId")
        return JSONObject()
            .put("type", "server-response")
            .put("rpcId", rpcId)
            .put("result", JSONObject().put("ok", true).put("value", value))
            .toString()
    }

    private class TestServer(private val handler: (Request) -> Response) : AutoCloseable {
        private val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val origin = "http://127.0.0.1:${socket.localPort}"
        private val worker = thread(isDaemon = true, name = "dsh-composer-fixture") {
            while (!socket.isClosed) {
                try {
                    socket.accept().use { client ->
                        client.soTimeout = 5_000
                        val input = client.getInputStream()
                        val head = StringBuilder()
                        while (!head.endsWith("\r\n\r\n") && head.length < 16_384) {
                            val value = input.read()
                            if (value < 0) break
                            head.append(value.toChar())
                        }
                        val lines = head.toString().split("\r\n")
                        val requestLine = lines.firstOrNull().orEmpty().split(' ')
                        val method = requestLine.getOrNull(0).orEmpty()
                        val target = requestLine.getOrNull(1).orEmpty()
                        val headers = lines.drop(1).mapNotNull { line ->
                            val separator = line.indexOf(':')
                            if (separator <= 0) null
                            else line.substring(0, separator).trim().lowercase() to line.substring(separator + 1).trim()
                        }.toMap()
                        val length = headers["content-length"]?.toIntOrNull() ?: 0
                        val body = ByteArray(length)
                        var offset = 0
                        while (offset < length) {
                            val read = input.read(body, offset, length - offset)
                            if (read <= 0) break
                            offset += read
                        }
                        val response = handler(Request(method, target, headers, body.toString(Charsets.UTF_8)))
                        val payload = response.body.toByteArray(Charsets.UTF_8)
                        val headText = buildString {
                            append("HTTP/1.1 ${response.status} ${if (response.status == 200) "OK" else "Error"}\r\n")
                            append("Content-Type: application/json; charset=utf-8\r\n")
                            append("Content-Length: ${payload.size}\r\n")
                            append("Cache-Control: no-store\r\n")
                            append("Connection: close\r\n")
                            response.headers.forEach { (name, value) -> append("$name: $value\r\n") }
                            append("\r\n")
                        }
                        client.getOutputStream().apply {
                            write(headText.toByteArray(Charsets.UTF_8))
                            write(payload)
                        }
                    }
                } catch (_: Exception) {
                    // 夹具关闭或客户端取消时结束当前连接。
                }
            }
        }

        override fun close() {
            socket.close()
            worker.join(5_500)
        }
    }
}
