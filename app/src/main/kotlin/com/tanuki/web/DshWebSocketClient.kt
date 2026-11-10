package com.tanuki.web

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 只服务于 DSH loopback `/api/remote.mux` 的最小 RFC6455 客户端。
 *
 * 不引入第三方 WebSocket 库：审批通道只需要文本帧、ping/pong、close 和掩码，
 * 且必须复用官方 API Cookie、拒绝重定向、限制帧大小。客户端帧全部掩码，
 * 服务端帧必须未掩码；握手校验 `Sec-WebSocket-Accept`，避免连到错误服务。
 */
class DshWebSocketClient(
    private val listener: Listener,
    private val random: SecureRandom = SecureRandom(),
    private val socketFactory: () -> Socket = { Socket() },
    private val handshakeTimeoutMs: Int = HANDSHAKE_TIMEOUT_MS
) {
    interface Listener {
        fun onOpen()
        fun onText(text: String)
        fun onClosed(reason: String?)
        fun onFailure(message: String)
    }

    private val closed = AtomicBoolean(false)
    private val failed = AtomicBoolean(false)
    private val writeLock = Any()
    @Volatile private var socket: Socket? = null
    @Volatile private var output: OutputStream? = null
    @Volatile private var handshakeComplete = false
    private var reader: Thread? = null

    /**
     * 建立握手并启动读线程。调用方负责在 IO 线程执行；本方法阻塞到握手完成。
     *
     * @param origin 精确 loopback origin，例如 `http://127.0.0.1:1234`。
     * @param cookie 官方 `/connect` 换到的 API Cookie；只放在握手头，不写日志。
     * @param path 固定 `/api/remote.mux`。
     */
    @Throws(IOException::class)
    fun connect(origin: String, cookie: String, path: String = DshApprovalProtocol.MUX_PATH) {
        if (closed.get()) throw IOException("审批通道已关闭")
        if (!DshApiProtocol.validOrigin(origin)) throw IOException("DSH 服务地址无效")
        if (cookie.isBlank() || cookie.any { it <= '\u0020' || it == '\u007f' || it == ';' }) {
            throw IOException("DSH 审批通道认证无效")
        }
        val uri = URI(origin)
        val host = uri.host ?: throw IOException("DSH 服务地址无效")
        val port = uri.port
        val keyBytes = ByteArray(16).also(random::nextBytes)
        val key = Base64.getEncoder().encodeToString(keyBytes)
        val socket = socketFactory()
        try {
            socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            // 握手阶段必须有界；否则对端接受 TCP 却不返回 Upgrade 头会永久占用 IO 线程。
            socket.soTimeout = handshakeTimeoutMs
            socket.tcpNoDelay = true
            val input = BufferedInputStream(socket.getInputStream())
            val output = socket.getOutputStream()
            // 先发布 socket/output，close()/协程取消才能关掉正在握手的连接。
            this.socket = socket
            this.output = output
            this.handshakeComplete = false
            val request = buildString {
                append("GET ").append(path).append(" HTTP/1.1\r\n")
                append("Host: ").append(host).append(':').append(port).append("\r\n")
                append("Upgrade: websocket\r\n")
                append("Connection: Upgrade\r\n")
                append("Sec-WebSocket-Key: ").append(key).append("\r\n")
                append("Sec-WebSocket-Version: 13\r\n")
                append("Cookie: ").append(cookie).append("\r\n")
                append("User-Agent: Tanuki\r\n")
                append("\r\n")
            }
            output.write(request.toByteArray(Charsets.US_ASCII))
            output.flush()
            val response = readHeaders(input, socket, handshakeTimeoutMs)
            validateHandshake(response, key)
            this.handshakeComplete = true
            socket.soTimeout = 0
            listener.onOpen()
            val thread = Thread({ readLoop(input) }, "dsh-approval-stream")
            thread.isDaemon = true
            reader = thread
            thread.start()
        } catch (error: Throwable) {
            val alreadyClosed = closed.get()
            close()
            runCatching { socket.close() }
            if (alreadyClosed) throw IOException("审批通道已关闭")
            if (error is SocketTimeoutException) throw IOException("DSH 审批通道握手超时")
            if (error is IOException) throw error
            throw IOException("DSH 审批通道连接失败", error)
        }
    }

    /** 发送一条文本帧；连接已关闭或写入失败返回 false，由控制器显示中文错误。 */
    fun sendText(text: String): Boolean {
        if (closed.get() || !handshakeComplete) return false
        val payload = text.toByteArray(Charsets.UTF_8)
        if (payload.size > DshApprovalProtocol.MAX_FRAME_BYTES) return false
        val mask = ByteArray(4).also(random::nextBytes)
        val frame = DshWebSocketFrames.encode(0x1, payload, mask, fin = true)
        return try {
            synchronized(writeLock) {
                val stream = output ?: return false
                stream.write(frame)
                stream.flush()
            }
            true
        } catch (_: IOException) {
            fail("审批通道写入失败")
            false
        }
    }

    /** 主动关闭；幂等，可在主线程调用，不等待读线程。 */
    fun close() {
        if (!closed.compareAndSet(false, true)) return
        val wasHandshaking = !handshakeComplete
        handshakeComplete = false
        runCatching {
            val stream = output
            if (stream != null && !wasHandshaking) {
                synchronized(writeLock) {
                    stream.write(DshWebSocketFrames.encode(0x8, byteArrayOf(0x03, 0xe8.toByte()), ByteArray(4), fin = true))
                    stream.flush()
                }
            }
        }
        runCatching { socket?.close() }
        socket = null
        output = null
    }

    private fun readLoop(input: InputStream) {
        var fragmentedOpcode = -1
        val fragmented = ByteArrayOutputStream()
        try {
            while (!closed.get()) {
                val frame = DshWebSocketFrames.read(input, DshApprovalProtocol.MAX_FRAME_BYTES)
                when (frame.opcode) {
                    0x1, 0x2 -> {
                        if (frame.fin) {
                            deliver(frame.opcode, frame.payload)
                        } else {
                            fragmentedOpcode = frame.opcode
                            fragmented.reset()
                            fragmented.write(frame.payload)
                        }
                    }
                    0x0 -> {
                        if (fragmentedOpcode < 0) throw IOException("审批通道收到意外的续帧")
                        if (fragmented.size() + frame.payload.size > DshApprovalProtocol.MAX_FRAME_BYTES) {
                            throw IOException("审批通道消息过大")
                        }
                        fragmented.write(frame.payload)
                        if (frame.fin) {
                            deliver(fragmentedOpcode, fragmented.toByteArray())
                            fragmentedOpcode = -1
                            fragmented.reset()
                        }
                    }
                    0x8 -> {
                        val reason = closeReason(frame.payload)
                        close()
                        listener.onClosed(reason)
                        return
                    }
                    0x9 -> sendControl(0xA, frame.payload)
                    0xA -> Unit
                    else -> throw IOException("审批通道收到不支持的控制帧")
                }
            }
        } catch (error: EOFException) {
            if (!closed.get()) fail("审批通道已断开")
        } catch (error: Throwable) {
            if (!closed.get()) fail(error.message?.takeIf { it.isNotBlank() } ?: "审批通道读取失败")
        } finally {
            if (!closed.get()) close()
        }
    }

    private fun deliver(opcode: Int, payload: ByteArray) {
        if (opcode != 0x1) return
        val text = decodeUtf8(payload) ?: run {
            fail("审批通道收到非法文本")
            return
        }
        listener.onText(text)
    }

    private fun sendControl(opcode: Int, payload: ByteArray) {
        if (payload.size > 125) return
        val mask = ByteArray(4).also(random::nextBytes)
        runCatching {
            synchronized(writeLock) {
                val stream = output ?: return
                stream.write(DshWebSocketFrames.encode(opcode, payload, mask, fin = true))
                stream.flush()
            }
        }
    }

    private fun fail(message: String) {
        if (!failed.compareAndSet(false, true)) return
        close()
        listener.onFailure(message)
    }

    private fun readHeaders(input: InputStream, socket: Socket, timeoutMs: Int): String {
        val output = ByteArrayOutputStream()
        var matched = 0
        val terminator = byteArrayOf(13, 10, 13, 10)
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        while (output.size() < MAX_HEADER_BYTES) {
            // soTimeout 是单次读期限；用总 deadline 防止对端逐字节慢速拖延握手。
            val remaining = (deadline - System.nanoTime()) / 1_000_000L
            if (remaining <= 0L) throw SocketTimeoutException("审批通道握手超时")
            socket.soTimeout = remaining.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val value = input.read()
            if (value < 0) throw EOFException("审批通道握手提前结束")
            output.write(value)
            if (value.toByte() == terminator[matched]) {
                matched++
                if (matched == terminator.size) return output.toString("US-ASCII")
            } else {
                matched = if (value.toByte() == terminator[0]) 1 else 0
            }
        }
        throw IOException("审批通道握手响应过大")
    }

    private fun validateHandshake(response: String, key: String) {
        val lines = response.split("\r\n")
        val status = lines.firstOrNull()?.split(' ')?.getOrNull(1)?.toIntOrNull()
            ?: throw IOException("审批通道握手响应无效")
        if (status != 101) throw IOException("DSH 审批通道连接失败（HTTP $status）")
        val headers = HashMap<String, String>()
        for (line in lines.drop(1)) {
            val separator = line.indexOf(':')
            if (separator <= 0) continue
            headers[line.substring(0, separator).trim().lowercase()] = line.substring(separator + 1).trim()
        }
        if (!headers["upgrade"].orEmpty().equals("websocket", ignoreCase = true)) {
            throw IOException("DSH 审批通道未升级为 WebSocket")
        }
        if (!headers["connection"].orEmpty().lowercase().contains("upgrade")) {
            throw IOException("DSH 审批通道连接头无效")
        }
        val expected = DshWebSocketFrames.accept(key)
        if (headers["sec-websocket-accept"] != expected) {
            throw IOException("DSH 审批通道握手校验失败")
        }
    }

    private fun decodeUtf8(payload: ByteArray): String? = runCatching {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(payload))
            .toString()
    }.getOrNull()

    private fun closeReason(payload: ByteArray): String? {
        if (payload.size < 2) return null
        val code = ((payload[0].toInt() and 0xff) shl 8) or (payload[1].toInt() and 0xff)
        return if (code == 1000) null else "审批通道已关闭（$code）"
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val HANDSHAKE_TIMEOUT_MS = 10_000
        private const val MAX_HEADER_BYTES = 8_192
    }
}

/** RFC6455 帧编解码；只支持服务端需要的文本/二进制/控制帧与客户端掩码。 */
internal object DshWebSocketFrames {
    data class Frame(val fin: Boolean, val opcode: Int, val payload: ByteArray)

    fun encode(opcode: Int, payload: ByteArray, mask: ByteArray, fin: Boolean): ByteArray {
        require(mask.size == 4) { "mask 必须为 4 字节" }
        val header = ByteArrayOutputStream()
        header.write((if (fin) 0x80 else 0x00) or (opcode and 0x0f))
        when {
            payload.size <= 125 -> header.write(0x80 or payload.size)
            payload.size <= 0xffff -> {
                header.write(0x80 or 126)
                header.write((payload.size ushr 8) and 0xff)
                header.write(payload.size and 0xff)
            }
            else -> {
                header.write(0x80 or 127)
                val length = payload.size.toLong()
                for (shift in 56 downTo 0 step 8) header.write(((length ushr shift) and 0xff).toInt())
            }
        }
        header.write(mask)
        val masked = ByteArray(payload.size) { index ->
            (payload[index].toInt() xor mask[index % 4].toInt()).toByte()
        }
        header.write(masked)
        return header.toByteArray()
    }

    @Throws(IOException::class)
    fun read(input: InputStream, maxPayload: Int): Frame {
        val first = readByte(input)
        val second = readByte(input)
        val fin = first and 0x80 != 0
        val opcode = first and 0x0f
        val masked = second and 0x80 != 0
        var length = (second and 0x7f).toLong()
        if (length == 126L) {
            length = ((readByte(input).toLong() shl 8) or readByte(input).toLong())
        } else if (length == 127L) {
            length = 0
            repeat(8) { length = (length shl 8) or readByte(input).toLong() }
        }
        if (length < 0 || length > maxPayload) throw IOException("审批通道帧过大")
        if (masked) throw IOException("审批通道收到掩码服务端帧")
        val payload = ByteArray(length.toInt())
        var offset = 0
        while (offset < payload.size) {
            val read = input.read(payload, offset, payload.size - offset)
            if (read < 0) throw EOFException("审批通道帧提前结束")
            offset += read
        }
        return Frame(fin, opcode, payload)
    }

    fun accept(key: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
        val bytes = digest.digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray(Charsets.US_ASCII))
        return Base64.getEncoder().encodeToString(bytes)
    }

    private fun readByte(input: InputStream): Int {
        val value = input.read()
        if (value < 0) throw EOFException("审批通道提前结束")
        return value
    }
}
