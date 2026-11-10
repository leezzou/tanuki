package com.tanuki

import com.tanuki.web.DshWebSocketMuxSocket
import com.tanuki.web.DshWsCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class DshMuxWebSocketTest {
    @Test fun handshakeAcceptsPingPongAndMaskedText() {
        val server = FakeWebSocketServer()
        try {
            val socket = DshWebSocketMuxSocket()
            runBlocking {
                socket.connect(server.origin, "dsh-session=abc")
                socket.send("world")
                val hello = withTimeout(3_000) { socket.incoming().first() }
                assertEquals("hello", hello)
            }
            assertTrue(server.done.await(3, TimeUnit.SECONDS))
            server.failure.get()?.let { throw AssertionError("WebSocket 合成服务器异常", it) }
            socket.close()
        } finally {
            server.close()
        }
    }

    @Test fun handshakeRejectsWrongAccept() {
        val server = FakeWebSocketServer(correctAccept = false)
        try {
            val socket = DshWebSocketMuxSocket()
            val result = runCatching { runBlocking { socket.connect(server.origin, "dsh-session=abc") } }
            assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("握手"))
            socket.close()
        } finally {
            server.close()
        }
    }

    @Test fun idleConnectionCancellationClosesSocketAndAllowsSecondConnection() = runBlocking {
        val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val origin = "http://127.0.0.1:${server.localPort}"
        val firstHandshake = CountDownLatch(1)
        val secondHandshake = CountDownLatch(1)
        val connectionIndex = AtomicInteger()
        val failure = AtomicReference<Throwable?>(null)
        val serverThread = Thread {
            try {
                while (!server.isClosed) {
                    val accepted = server.accept()
                    Thread {
                        try {
                            accepted.use { socket ->
                                socket.soTimeout = 3_000
                                val input = socket.getInputStream()
                                val output = socket.getOutputStream()
                                var key: String? = null
                                while (true) {
                                    val line = readLine(input) ?: break
                                    if (line.isEmpty()) break
                                    val separator = line.indexOf(':')
                                    if (separator > 0 && line.substring(0, separator).trim().equals("Sec-WebSocket-Key", true)) {
                                        key = line.substring(separator + 1).trim()
                                    }
                                }
                                val accept = DshWsCodec.expectedAccept(key ?: "")
                                output.write(
                                    ("HTTP/1.1 101 Switching Protocols\r\n" +
                                        "Upgrade: websocket\r\n" +
                                        "Connection: Upgrade\r\n" +
                                        "Sec-WebSocket-Accept: $accept\r\n\r\n").toByteArray(Charsets.US_ASCII)
                                )
                                output.flush()
                                if (connectionIndex.getAndIncrement() == 0) firstHandshake.countDown()
                                else secondHandshake.countDown()
                                // 保持空闲 5 秒；客户端取消若不能立即 close，测试会在 2 秒超时。
                                Thread.sleep(5_000)
                            }
                        } catch (_: SocketException) {
                            // 客户端关闭/测试收尾属正常。
                        } catch (error: Throwable) {
                            failure.compareAndSet(null, error)
                        }
                    }.apply { isDaemon = true; start() }
                }
            } catch (_: SocketException) {
                // 关闭监听属正常收尾。
            } catch (error: Throwable) {
                failure.compareAndSet(null, error)
            }
        }.apply { isDaemon = true; start() }
        try {
            val first = DshWebSocketMuxSocket()
            first.connect(origin, "dsh-session=abc")
            val collector = launch(Dispatchers.Default) { first.incoming().collect { } }
            assertTrue(firstHandshake.await(2, TimeUnit.SECONDS))
            delay(50)
            withTimeout(2_000) { collector.cancelAndJoin() }
            first.close()

            val second = DshWebSocketMuxSocket()
            second.connect(origin, "dsh-session=def")
            assertTrue("第二连接未建立", secondHandshake.await(2, TimeUnit.SECONDS))
            second.close()
        } finally {
            server.close()
            serverThread.join(2_000)
            failure.get()?.let { throw AssertionError("空闲 WebSocket 合成服务器异常", it) }
        }
    }

    private inner class FakeWebSocketServer(private val correctAccept: Boolean = true) : AutoCloseable {
        val origin: String
        val done = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>(null)
        private val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        private val thread: Thread

        init {
            origin = "http://127.0.0.1:${server.localPort}"
            thread = Thread {
                try {
                    server.accept().use { socket ->
                        socket.soTimeout = 5_000
                        val input = socket.getInputStream()
                        val output = socket.getOutputStream()
                        var key: String? = null
                        while (true) {
                            val line = readLine(input) ?: break
                            if (line.isEmpty()) break
                            val separator = line.indexOf(':')
                            if (separator > 0 && line.substring(0, separator).trim().equals("Sec-WebSocket-Key", true)) {
                                key = line.substring(separator + 1).trim()
                            }
                        }
                        val accept = DshWsCodec.expectedAccept(key ?: "")
                        val response = buildString {
                            append("HTTP/1.1 101 Switching Protocols\r\n")
                            append("Upgrade: websocket\r\n")
                            append("Connection: Upgrade\r\n")
                            append("Sec-WebSocket-Accept: ${if (correctAccept) accept else "wrong"}\r\n")
                            append("\r\n")
                        }
                        output.write(response.toByteArray(Charsets.US_ASCII))
                        output.flush()
                        if (correctAccept) {
                            // 客户端先发送文本；服务器随后 ping，客户端必须回复 pong，最后回 hello。
                            val text = readFrame(input)
                            assertEquals(DshWsCodec.OP_TEXT, text.opcode)
                            assertTrue(text.masked)
                            assertEquals("world", String(text.payload))
                            writeFrame(output, DshWsCodec.OP_PING, "p".toByteArray())
                            val pong = readFrame(input)
                            assertEquals(DshWsCodec.OP_PONG, pong.opcode)
                            assertEquals("p", String(pong.payload))
                            assertTrue(pong.masked)
                            writeFrame(output, DshWsCodec.OP_TEXT, "hello".toByteArray())
                            writeFrame(output, DshWsCodec.OP_CLOSE, ByteArray(0))
                        } else {
                            Thread.sleep(200)
                        }
                    }
                } catch (_: SocketException) {
                    // 客户端拒绝握手后关闭连接属正常收尾。
                } catch (error: Throwable) {
                    failure.set(error)
                } finally {
                    done.countDown()
                }
            }.apply { isDaemon = true; start() }
        }

        override fun close() {
            server.close()
            thread.join(2_000)
            failure.get()?.let { throw AssertionError("WebSocket 合成服务器异常", it) }
        }
    }

    private data class Frame(val fin: Boolean, val opcode: Int, val masked: Boolean, val payload: ByteArray)

    private fun readLine(input: InputStream): String? {
        val line = ByteArrayOutputStream()
        while (true) {
            val value = input.read()
            if (value < 0) return if (line.size() == 0) null else line.toString("UTF-8")
            if (value == '\n'.code) break
            if (value != '\r'.code) line.write(value)
        }
        return line.toString("UTF-8")
    }

    private fun readFrame(input: InputStream): Frame {
        val first = input.read()
        val second = input.read()
        if (first < 0 || second < 0) throw EOFException("WebSocket 帧头被截断")
        val fin = first and 0x80 != 0
        val opcode = first and 0x0f
        val masked = second and 0x80 != 0
        var length = (second and 0x7f).toLong()
        if (length == 126L) {
            length = ((input.read() shl 8) or input.read()).toLong()
        } else if (length == 127L) {
            var value = 0L
            repeat(8) { value = (value shl 8) or input.read().toLong() }
            length = value
        }
        val mask = if (masked) ByteArray(4).also { input.read(it) } else null
        val payload = ByteArray(length.toInt())
        var read = 0
        while (read < payload.size) {
            val count = input.read(payload, read, payload.size - read)
            if (count < 0) throw EOFException("WebSocket 帧被截断")
            read += count
        }
        if (mask != null) for (index in payload.indices) {
            payload[index] = (payload[index].toInt() xor mask[index % 4].toInt()).toByte()
        }
        return Frame(fin, opcode, masked, payload)
    }

    private fun writeFrame(output: OutputStream, opcode: Int, payload: ByteArray) {
        val frame = ByteArrayOutputStream()
        frame.write(0x80 or (opcode and 0x0f))
        when {
            payload.size < 126 -> frame.write(payload.size)
            payload.size <= 0xffff -> {
                frame.write(126)
                frame.write((payload.size ushr 8) and 0xff)
                frame.write(payload.size and 0xff)
            }
            else -> {
                frame.write(127)
                repeat(4) { frame.write(0) }
                repeat(4) { index -> frame.write((payload.size ushr ((3 - index) * 8)) and 0xff) }
            }
        }
        frame.write(payload)
        output.write(frame.toByteArray())
        output.flush()
    }
}
