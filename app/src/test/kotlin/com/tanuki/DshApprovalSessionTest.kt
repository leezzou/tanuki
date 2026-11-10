package com.tanuki

import com.tanuki.web.DshApprovalOutcome
import com.tanuki.web.DshApprovalRequest
import com.tanuki.web.DshApprovalSession
import com.tanuki.web.DshEndpoint
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class DshApprovalSessionTest {
    @Test fun connectsAnswersAllowedOnceAndCloses() {
        val server = FakeDshServer()
        try {
            val listener = RecordingListener()
            val session = DshApprovalSession({ DshEndpoint(server.origin, server.launchUrl) }, listener)
            session.connect()
            assertTrue("未收到 ready", listener.ready.await(5, TimeUnit.SECONDS))
            assertTrue("未收到审批请求", listener.approvalLatch.await(5, TimeUnit.SECONDS))
            assertEquals("bash", listener.approval.get()!!.toolName)
            val result = session.answer("event-1", DshApprovalOutcome.ALLOWED_ONCE)
            assertTrue(result.exceptionOrNull()?.message, result.isSuccess)
            assertEquals(1, server.answerBodies.size)
            val body = JSONObject(server.answerBodies[0])
            assertEquals("\$events/result", body.getString("method"))
            val args = body.getJSONObject("payload").getJSONObject("args")
            assertEquals("client-1", args.getString("clientId"))
            assertEquals("event-1", args.getString("eventId"))
            assertEquals("allowed-once", args.getJSONObject("outcome").getString("value"))
            session.close()
            assertNull(listener.disconnected.get())
        } finally {
            server.close()
        }
    }

    @Test fun expiredCookieReauthenticatesAndRetriesOnce() {
        val server = FakeDshServer().apply { failFirstAnswer = true }
        try {
            val listener = RecordingListener()
            val session = DshApprovalSession({ DshEndpoint(server.origin, server.launchUrl) }, listener)
            session.connect()
            assertTrue(listener.ready.await(5, TimeUnit.SECONDS))
            val result = session.answer("event-1", DshApprovalOutcome.REJECTED)
            assertTrue(result.exceptionOrNull()?.message, result.isSuccess)
            assertEquals(2, server.answerBodies.size)
            assertEquals(2, server.connectCalls.get())
            assertEquals("rejected", JSONObject(server.answerBodies[1])
                .getJSONObject("payload").getJSONObject("args")
                .getJSONObject("outcome").getString("value"))
            session.close()
        } finally {
            server.close()
        }
    }

    @Test fun serverErrorIsReportedAsChineseFailure() {
        val server = FakeDshServer().apply { answerStatus = 500 }
        try {
            val listener = RecordingListener()
            val session = DshApprovalSession({ DshEndpoint(server.origin, server.launchUrl) }, listener)
            session.connect()
            assertTrue(listener.ready.await(5, TimeUnit.SECONDS))
            val result = session.answer("event-1", DshApprovalOutcome.ALLOWED_ONCE)
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()!!.message!!.contains("HTTP 500"))
            session.close()
        } finally {
            server.close()
        }
    }

    @Test fun missingEndpointFailsWithChineseMessage() {
        val session = DshApprovalSession({ null }, RecordingListener())
        try {
            session.connect()
            fail("缺少端点必须失败")
        } catch (error: IOException) {
            assertTrue(error.message!!.contains("尚未就绪"))
        } finally {
            session.close()
        }
    }

    @Test fun nonLoopbackEndpointIsRejectedBeforeNetwork() {
        val session = DshApprovalSession(
            { DshEndpoint("http://example.com:1234", "http://example.com:1234/?token=t") },
            RecordingListener()
        )
        try {
            session.connect()
            fail("非 loopback 端点必须失败")
        } catch (error: IOException) {
            assertTrue(error.message!!.contains("服务地址无效"))
        } finally {
            session.close()
        }
    }

    private class RecordingListener : DshApprovalSession.Listener {
        val ready = CountDownLatch(1)
        val approvalLatch = CountDownLatch(1)
        val approval = AtomicReference<DshApprovalRequest>()
        val disconnected = AtomicReference<String?>()
        override fun onReady() {
            ready.countDown()
        }
        override fun onApproval(request: DshApprovalRequest) {
            approval.set(request)
            approvalLatch.countDown()
        }
        override fun onCancel(eventId: String) = Unit
        override fun onDisconnected(message: String) {
            disconnected.set(message)
        }
    }

    private class FakeDshServer {
        private val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        val port: Int = server.localPort
        val origin: String = "http://127.0.0.1:$port"
        val launchUrl: String = "$origin/?token=secret-token"
        val answerBodies = CopyOnWriteArrayList<String>()
        val connectCalls = AtomicInteger()
        private val resultCalls = AtomicInteger()
        private val pool = Executors.newCachedThreadPool()
        @Volatile var failFirstAnswer = false
        @Volatile var answerStatus = 200
        private val acceptThread = thread(name = "fake-dsh-accept") {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                pool.execute { runCatching { handle(socket) }.also { socket.close() } }
            }
        }

        fun close() {
            runCatching { server.close() }
            pool.shutdownNow()
            acceptThread.join(1_000)
        }

        private fun handle(socket: Socket) {
            val input = BufferedInputStream(socket.getInputStream())
            val output = socket.getOutputStream()
            val head = readHead(input)
            val path = head.requestLine.split(' ').getOrNull(1).orEmpty()
            when {
                path.startsWith("/connect") -> {
                    connectCalls.incrementAndGet()
                    assertTrue(path.contains("token=secret-token"))
                    assertTrue(path.contains("interactions=none"))
                    output.write(
                        ("HTTP/1.1 302 Found\r\n" +
                            "Set-Cookie: dsh=api-cookie; Path=/\r\n" +
                            "Location: /\r\nContent-Length: 0\r\n\r\n").toByteArray(Charsets.US_ASCII)
                    )
                    output.flush()
                }
                path == "/api/remote.mux" -> {
                    val key = head.headers["sec-websocket-key"] ?: throw IOException("缺少 Sec-WebSocket-Key")
                    output.write(
                        ("HTTP/1.1 101 Switching Protocols\r\n" +
                            "Upgrade: websocket\r\n" +
                            "Connection: Upgrade\r\n" +
                            "Sec-WebSocket-Accept: ${com.tanuki.web.DshWebSocketFrames.accept(key)}\r\n\r\n")
                            .toByteArray(Charsets.US_ASCII)
                    )
                    output.write(serverText(item(JSONObject()
                        .put("type", "ready").put("clientId", "client-1"))))
                    output.write(serverText(item(JSONObject()
                        .put("type", "waterfall")
                        .put("event", "approval/request")
                        .put("eventId", "event-1")
                        .put("agentId", "session-1")
                        .put("request", JSONObject().put("toolName", "bash").put("callId", "call-1")))))
                    output.flush()
                    // 保持连接直到客户端关闭；读取客户端帧只为消费 close。
                    while (true) readClientFrame(input)
                }
                path == "/api/\$events/result" -> {
                    val length = head.headers["content-length"]?.toIntOrNull() ?: 0
                    val body = readBody(input, length)
                    answerBodies += body
                    val rpcId = JSONObject(body).getString("rpcId")
                    val call = resultCalls.incrementAndGet()
                    if (failFirstAnswer && call == 1) {
                        output.write("HTTP/1.1 401 Unauthorized\r\nContent-Length: 0\r\n\r\n".toByteArray(Charsets.US_ASCII))
                    } else {
                        val response = JSONObject()
                            .put("type", "server-response")
                            .put("rpcId", rpcId)
                            .put("result", JSONObject().put("ok", true).put("value", JSONObject.NULL))
                            .toString()
                        val status = if (answerStatus in 200..299) "200 OK" else "$answerStatus Error"
                        output.write(
                            ("HTTP/1.1 $status\r\nContent-Type: application/json\r\n" +
                                "Content-Length: ${response.toByteArray(Charsets.UTF_8).size}\r\n\r\n$response")
                                .toByteArray(Charsets.US_ASCII)
                        )
                    }
                    output.flush()
                }
                else -> {
                    output.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n".toByteArray(Charsets.US_ASCII))
                    output.flush()
                }
            }
        }

        private fun item(value: JSONObject): String = JSONObject()
            .put("type", "item")
            .put("streamId", "stream-1")
            .put("value", value)
            .toString()

        private fun serverText(text: String): ByteArray {
            val payload = text.toByteArray(Charsets.UTF_8)
            val header = when {
                payload.size <= 125 -> byteArrayOf(0x81.toByte(), payload.size.toByte())
                payload.size <= 0xffff -> byteArrayOf(
                    0x81.toByte(), 126, (payload.size ushr 8).toByte(), payload.size.toByte()
                )
                else -> throw IllegalArgumentException("测试帧过大")
            }
            return header + payload
        }

        private fun readHead(input: InputStream): Head {
            val output = ByteArrayOutputStream()
            var matched = 0
            val terminator = byteArrayOf(13, 10, 13, 10)
            while (output.size() < 8192) {
                val value = input.read()
                if (value < 0) throw IOException("提前结束")
                output.write(value)
                if (value.toByte() == terminator[matched]) {
                    matched++
                    if (matched == terminator.size) break
                } else {
                    matched = if (value.toByte() == terminator[0]) 1 else 0
                }
            }
            val lines = output.toString("US-ASCII").split("\r\n")
            val requestLine = lines.firstOrNull().orEmpty()
            val headers = HashMap<String, String>()
            for (line in lines.drop(1)) {
                val separator = line.indexOf(':')
                if (separator <= 0) continue
                headers[line.substring(0, separator).trim().lowercase()] =
                    line.substring(separator + 1).trim()
            }
            return Head(requestLine, headers)
        }

        private fun readBody(input: InputStream, length: Int): String {
            val bytes = ByteArray(length)
            var offset = 0
            while (offset < length) {
                val read = input.read(bytes, offset, length - offset)
                if (read < 0) throw IOException("请求体提前结束")
                offset += read
            }
            return String(bytes, Charsets.UTF_8)
        }

        /** 读取一帧客户端掩码文本并解掩码。 */
        private fun readClientFrame(input: InputStream): ByteArray {
            val first = input.read()
            val second = input.read()
            if (first < 0 || second < 0) throw IOException("提前结束")
            if (second and 0x80 == 0) throw IOException("客户端帧必须掩码")
            var length = second and 0x7f
            if (length == 126) {
                length = (input.read() shl 8) or input.read()
            } else if (length == 127) {
                length = 0
                repeat(8) { length = (length shl 8) or input.read() }
            }
            val mask = ByteArray(4) { input.read().toByte() }
            val payload = ByteArray(length) { input.read().toByte() }
            return ByteArray(length) { index -> (payload[index].toInt() xor mask[index % 4].toInt()).toByte() }
        }

        private data class Head(val requestLine: String, val headers: Map<String, String>)
    }
}
