package com.tanuki

import com.tanuki.web.DshApprovalProtocol
import com.tanuki.web.DshEventFrame
import com.tanuki.web.DshWebSocketClient
import com.tanuki.web.DshWebSocketFrames
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class DshWebSocketTest {
    @Test fun rfcAcceptExampleMatchesOfficialAlgorithm() {
        assertEquals(
            "s3pPLMBiTxaQ9kYGzzhZRbK+xOo=",
            DshWebSocketFrames.accept("dGhlIHNhbXBsZSBub25jZQ==")
        )
    }

    @Test fun clientFrameIsMaskedAndServerFrameReaderRejectsMasking() {
        val mask = byteArrayOf(1, 2, 3, 4)
        val frame = DshWebSocketFrames.encode(0x1, "hi".toByteArray(), mask, fin = true)
        assertEquals(0x81, frame[0].toInt() and 0xff)
        assertEquals(0x82, frame[1].toInt() and 0xff)
        assertEquals(('h'.code xor 1).toByte(), frame[6])
        assertEquals(('i'.code xor 2).toByte(), frame[7])
        try {
            DshWebSocketFrames.read(ByteArrayInputStream(frame), 1024)
            fail("服务端掩码帧必须被拒绝")
        } catch (error: IOException) {
            assertTrue(error.message!!.contains("掩码"))
        }
    }

    @Test fun readsUnmaskedServerTextFrame() {
        val frame = serverText("""{"type":"item","value":{"type":"ready","clientId":"c1"}}""")
        val parsed = DshWebSocketFrames.read(ByteArrayInputStream(frame), 1024)
        assertEquals(0x1, parsed.opcode)
        assertTrue(parsed.fin)
        assertTrue(String(parsed.payload, Charsets.UTF_8).contains("\"ready\""))
    }

    @Test fun connectsWithCookieAndParsesReadyApprovalCancel() {
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val request = AtomicReference<String>()
        val failure = AtomicReference<String?>()
        val frames = CopyOnWriteArrayList<String>()
        val latch = CountDownLatch(3)
        val serverThread = thread(name = "fake-ws-server") {
            runCatching {
                server.accept().use { socket ->
                    val input = BufferedInputStream(socket.getInputStream())
                    request.set(readHead(input))
                    val key = Regex("Sec-WebSocket-Key: (.+)", RegexOption.IGNORE_CASE)
                        .find(request.get())!!.groupValues[1].trim()
                    val output = socket.getOutputStream()
                    output.write(
                        ("HTTP/1.1 101 Switching Protocols\r\n" +
                            "Upgrade: websocket\r\n" +
                            "Connection: Upgrade\r\n" +
                            "Sec-WebSocket-Accept: ${DshWebSocketFrames.accept(key)}\r\n\r\n")
                            .toByteArray(Charsets.US_ASCII)
                    )
                    output.flush()
                    // 客户端打开 $events 流；读一帧确认是掩码客户端帧。
                    val open = readClientFrame(input)
                    assertTrue(String(open, Charsets.UTF_8).contains("\$events"))
                    output.write(serverText(item(JSONObject()
                        .put("type", "ready").put("clientId", "client-1"))))
                    output.write(serverText(item(JSONObject()
                        .put("type", "waterfall")
                        .put("event", "approval/request")
                        .put("eventId", "event-1")
                        .put("agentId", "session-1")
                        .put("request", JSONObject().put("toolName", "bash")))))
                    output.write(serverText(item(JSONObject()
                        .put("type", "cancel").put("eventId", "event-1"))))
                    output.flush()
                    Thread.sleep(300)
                }
            }
        }
        val listener = object : DshWebSocketClient.Listener {
            override fun onOpen() = Unit
            override fun onText(text: String) {
                frames += text
                latch.countDown()
            }
            override fun onClosed(reason: String?) = Unit
            override fun onFailure(message: String) {
                failure.set(message)
            }
        }
        val client = DshWebSocketClient(listener)
        client.connect("http://127.0.0.1:${server.localPort}", "dsh=api-cookie")
        client.sendText(DshApprovalProtocol.openStreamBody("stream-1"))
        assertTrue("未收到三条审批帧", latch.await(5, TimeUnit.SECONDS))
        assertEquals(null, failure.get())
        val head = request.get()
        assertTrue(head.contains("Cookie: dsh=api-cookie"))
        assertFalse(head.contains("\r\nOrigin:"))
        val parsed = frames.map(DshApprovalProtocol::parseStreamItem)
        assertTrue(parsed.any { it is DshEventFrame.Ready })
        assertTrue(parsed.any { it is DshEventFrame.Approval })
        assertTrue(parsed.any { it is DshEventFrame.Cancelled })
        client.close()
        server.close()
        serverThread.join(2_000)
    }

    @Test fun handshakeTimesOutWhenServerAcceptsButNeverReplies() {
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val serverThread = thread(name = "fake-ws-silent") {
            runCatching { server.accept().use { Thread.sleep(1_500) } }
        }
        val client = DshWebSocketClient(object : DshWebSocketClient.Listener {
            override fun onOpen() = Unit
            override fun onText(text: String) = Unit
            override fun onClosed(reason: String?) = Unit
            override fun onFailure(message: String) = Unit
        }, handshakeTimeoutMs = 300)
        try {
            client.connect("http://127.0.0.1:${server.localPort}", "dsh=api-cookie")
            fail("握手无响应必须超时")
        } catch (error: IOException) {
            assertTrue(error.message!!.contains("握手超时"))
        } finally {
            client.close()
            server.close()
            serverThread.join(2_000)
        }
    }

    @Test fun closeDuringHandshakeUnblocksConnect() {
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val serverThread = thread(name = "fake-ws-silent-close") {
            runCatching { server.accept().use { Thread.sleep(2_000) } }
        }
        val client = DshWebSocketClient(object : DshWebSocketClient.Listener {
            override fun onOpen() = Unit
            override fun onText(text: String) = Unit
            override fun onClosed(reason: String?) = Unit
            override fun onFailure(message: String) = Unit
        }, handshakeTimeoutMs = 5_000)
        val error = AtomicReference<Throwable?>()
        val connectThread = thread(name = "client-connect") {
            error.set(runCatching {
                client.connect("http://127.0.0.1:${server.localPort}", "dsh=api-cookie")
            }.exceptionOrNull())
        }
        Thread.sleep(250)
        client.close()
        connectThread.join(2_000)
        assertFalse("close() 后 connect 仍阻塞", connectThread.isAlive)
        assertTrue(error.get() is IOException)
        assertTrue(error.get()!!.message!!.contains("已关闭"))
        server.close()
        serverThread.join(2_000)
    }

    @Test fun rejectsHandshakeWithWrongAccept() {
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val serverThread = thread(name = "fake-ws-bad-accept") {
            runCatching {
                server.accept().use { socket ->
                    val input = BufferedInputStream(socket.getInputStream())
                    readHead(input)
                    socket.getOutputStream().write(
                        ("HTTP/1.1 101 Switching Protocols\r\n" +
                            "Upgrade: websocket\r\n" +
                            "Connection: Upgrade\r\n" +
                            "Sec-WebSocket-Accept: wrong\r\n\r\n")
                            .toByteArray(Charsets.US_ASCII)
                    )
                    socket.getOutputStream().flush()
                }
            }
        }
        val client = DshWebSocketClient(object : DshWebSocketClient.Listener {
            override fun onOpen() = Unit
            override fun onText(text: String) = Unit
            override fun onClosed(reason: String?) = Unit
            override fun onFailure(message: String) = Unit
        })
        try {
            client.connect("http://127.0.0.1:${server.localPort}", "dsh=api-cookie")
            fail("错误 Accept 必须拒绝")
        } catch (error: IOException) {
            assertTrue(error.message!!.contains("握手校验失败"))
        } finally {
            client.close()
            server.close()
            serverThread.join(2_000)
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

    private fun readHead(input: InputStream): String {
        val output = java.io.ByteArrayOutputStream()
        var matched = 0
        val terminator = byteArrayOf(13, 10, 13, 10)
        while (output.size() < 8192) {
            val value = input.read()
            if (value < 0) throw IOException("提前结束")
            output.write(value)
            if (value.toByte() == terminator[matched]) {
                matched++
                if (matched == terminator.size) return output.toString("US-ASCII")
            } else {
                matched = if (value.toByte() == terminator[0]) 1 else 0
            }
        }
        throw IOException("头部过大")
    }

    /** 读取一帧客户端掩码文本并解掩码，供测试服务器断言 open 信封。 */
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
}
