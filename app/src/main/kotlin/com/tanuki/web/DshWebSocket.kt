package com.tanuki.web

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * remote.mux 的最小传输抽象；生产实现是官方 WebSocket，测试可注入合成实现。
 * 连接建立后只收发文本帧，不把 Cookie 或 token 写进异常文本。
 */
interface DshMuxSocket {
    suspend fun connect(origin: String, cookie: String)
    suspend fun send(text: String)
    fun incoming(): Flow<String>
    fun close()
}

/**
 * 官方 `/api/remote.mux` 的最小 WebSocket 客户端。
 *
 * Android 没有 `java.net.http.WebSocket`，项目也不引入额外网络依赖，因此这里只实现
 * 该端点需要的 RFC6455 子集：HTTP Upgrade 握手、客户端掩码文本帧、分片重组、
 * ping/pong 与 close。服务端帧必须未掩码；二进制帧、扩展位和非 loopback origin
 * 一律拒绝。连接只用于当前精确 origin，不跟随重定向。
 */
class DshWebSocketMuxSocket(
    private val random: SecureRandom = SecureRandom()
) : DshMuxSocket {
    private val writeLock = Any()
    @Volatile private var closed = false
    private var socket: Socket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    override suspend fun connect(origin: String, cookie: String) = withContext(Dispatchers.IO) {
        require(DshApiProtocol.validOrigin(origin)) { "WebSocket 地址不是本机 loopback" }
        require(cookie.isNotBlank() && cookie.none { it <= '\u0020' || it == '\u007f' || it == ';' }) {
            "WebSocket 认证 Cookie 无效"
        }
        val uri = URI(origin)
        val keyBytes = ByteArray(16).also(random::nextBytes)
        val key = Base64.getEncoder().encodeToString(keyBytes)
        closed = false
        val target = Socket()
        try {
            target.connect(InetSocketAddress(uri.host, uri.port), CONNECT_TIMEOUT_MS)
            target.soTimeout = HANDSHAKE_TIMEOUT_MS
            val request = buildString {
                append("GET ${DshMuxProtocol.MUX_PATH} HTTP/1.1\r\n")
                append("Host: ${uri.host}:${uri.port}\r\n")
                append("Upgrade: websocket\r\n")
                append("Connection: Upgrade\r\n")
                append("Sec-WebSocket-Key: $key\r\n")
                append("Sec-WebSocket-Version: 13\r\n")
                append("Cookie: $cookie\r\n")
                append("\r\n")
            }
            // 不能 use/close 握手输出流，否则后续帧写入会落在已关闭的流上。
            target.getOutputStream().apply {
                write(request.toByteArray(Charsets.UTF_8))
                flush()
            }
            val head = readHttpHead(target.getInputStream())
            val lines = head.split("\r\n")
            val status = lines.firstOrNull()?.split(' ')?.getOrNull(1)?.toIntOrNull()
            if (status != 101) throw IOException("WebSocket 握手被拒绝")
            val headers = lines.drop(1).mapNotNull { line ->
                val separator = line.indexOf(':')
                if (separator <= 0) null
                else line.substring(0, separator).trim().lowercase() to line.substring(separator + 1).trim()
            }.toMap()
            val accept = headers["sec-websocket-accept"]
            if (accept != DshWsCodec.expectedAccept(key)) throw IOException("WebSocket 握手校验失败")
            // close() 可能发生在握手 I/O 期间；取消后不能把已关闭的 socket 再发布为活动连接。
            if (closed) throw IOException("WebSocket 连接已取消")
            target.soTimeout = 0
            socket = target
            input = target.getInputStream()
            output = target.getOutputStream()
        } catch (error: Throwable) {
            runCatching { target.close() }
            throw if (error is IOException) error else IOException("WebSocket 连接失败")
        }
    }

    override suspend fun send(text: String) = withContext(Dispatchers.IO) {
        if (text.toByteArray(Charsets.UTF_8).size > DshMuxProtocol.MAX_TEXT_BYTES) {
            throw IOException("WebSocket 文本帧过大")
        }
        writeFrame(DshWsCodec.OP_TEXT, text.toByteArray(Charsets.UTF_8))
    }

    override fun incoming(): Flow<String> = channelFlow {
        val reader = launch(Dispatchers.IO) {
            try {
                while (!closed) {
                    val text = readTextMessage() ?: break
                    send(text)
                }
            } catch (error: Throwable) {
                if (!closed) throw error
            } finally {
                // 读取结束（对端关闭/取消）时结束通道，触发 awaitClose 统一关闭 socket。
                close()
            }
        }
        awaitClose {
            // 协程取消时 InputStream.read 不会响应取消；必须主动关闭 socket 才能退出阻塞读。
            closed = true
            reader.cancel()
            runCatching { socket?.close() }
        }
    }.flowOn(Dispatchers.IO)

    override fun close() {
        closed = true
        val current = socket
        if (current != null) {
            runCatching { writeFrame(DshWsCodec.OP_CLOSE, ByteArray(0)) }
        }
        socket = null
        input = null
        output = null
        runCatching { current?.close() }
    }

    private fun readTextMessage(): String? {
        val assembled = ByteArrayOutputStream()
        var fragmentOpen = false
        while (true) {
            val frame = try {
                readFrame()
            } catch (error: IOException) {
                if (closed) return null else throw error
            } ?: return null
            when (frame.opcode) {
                DshWsCodec.OP_PING -> {
                    if (frame.payload.size <= 125) runCatching { writeFrame(DshWsCodec.OP_PONG, frame.payload) }
                }
                DshWsCodec.OP_PONG -> Unit
                DshWsCodec.OP_CLOSE -> {
                    runCatching {
                        writeFrame(DshWsCodec.OP_CLOSE, frame.payload.copyOfRange(0, minOf(frame.payload.size, 125)))
                    }
                    return null
                }
                DshWsCodec.OP_TEXT, DshWsCodec.OP_BINARY -> {
                    if (fragmentOpen) throw IOException("WebSocket 帧序无效")
                    if (frame.opcode == DshWsCodec.OP_BINARY) throw IOException("WebSocket 只接受文本帧")
                    if (frame.fin) return frame.payload.toString(Charsets.UTF_8)
                    assembled.write(frame.payload)
                    if (assembled.size() > DshMuxProtocol.MAX_TEXT_BYTES) throw IOException("WebSocket 消息过大")
                    fragmentOpen = true
                }
                DshWsCodec.OP_CONTINUATION -> {
                    if (!fragmentOpen) throw IOException("WebSocket 续帧没有起始帧")
                    assembled.write(frame.payload)
                    if (assembled.size() > DshMuxProtocol.MAX_TEXT_BYTES) throw IOException("WebSocket 消息过大")
                    if (frame.fin) return assembled.toString("UTF-8")
                }
                else -> throw IOException("WebSocket 帧类型不支持")
            }
        }
    }

    private fun readFrame(): DshWsFrame? {
        val source = input ?: throw IOException("WebSocket 尚未连接")
        val first = source.read()
        if (first < 0) return null
        val second = source.read()
        if (second < 0) throw EOFException("WebSocket 帧头被截断")
        if (first and 0x70 != 0) throw IOException("WebSocket 扩展位未协商")
        val fin = first and 0x80 != 0
        val opcode = first and 0x0f
        if (second and 0x80 != 0) throw IOException("WebSocket 服务端帧不得掩码")
        var length = (second and 0x7f).toLong()
        if (length == 126L) {
            length = ((readByte(source).toLong() shl 8) or readByte(source).toLong())
        } else if (length == 127L) {
            var value = 0L
            repeat(8) { value = (value shl 8) or readByte(source).toLong() }
            length = value
        }
        if (length < 0 || length > DshMuxProtocol.MAX_TEXT_BYTES) throw IOException("WebSocket 帧过大")
        if (opcode >= 0x8 && (!fin || length > 125)) throw IOException("WebSocket 控制帧无效")
        val payload = ByteArray(length.toInt())
        var read = 0
        while (read < payload.size) {
            val count = source.read(payload, read, payload.size - read)
            if (count < 0) throw EOFException("WebSocket 帧被截断")
            read += count
        }
        return DshWsFrame(fin, opcode, payload)
    }

    private fun readByte(source: InputStream): Int {
        val value = source.read()
        if (value < 0) throw EOFException("WebSocket 帧被截断")
        return value
    }

    private fun writeFrame(opcode: Int, payload: ByteArray) {
        val stream = output ?: throw IOException("WebSocket 尚未连接")
        val mask = ByteArray(4).also(random::nextBytes)
        val frame = DshWsCodec.encodeClientFrame(opcode, payload, mask)
        synchronized(writeLock) {
            stream.write(frame)
            stream.flush()
        }
    }

    private fun readHttpHead(source: InputStream): String {
        val output = StringBuilder()
        while (output.length <= MAX_HEAD_BYTES) {
            val value = source.read()
            if (value < 0) throw EOFException("WebSocket 握手响应被截断")
            output.append(value.toChar())
            if (output.endsWith("\r\n\r\n")) return output.toString()
        }
        throw IOException("WebSocket 握手响应过大")
    }

    private data class DshWsFrame(val fin: Boolean, val opcode: Int, val payload: ByteArray)

    private companion object {
        const val CONNECT_TIMEOUT_MS = 5_000
        const val HANDSHAKE_TIMEOUT_MS = 5_000
        const val MAX_HEAD_BYTES = 16 * 1024
    }
}

/** RFC6455 帧编码与握手校验的小型纯函数集合；JVM 回归直接验证这些边界。 */
internal object DshWsCodec {
    const val OP_CONTINUATION = 0x0
    const val OP_TEXT = 0x1
    const val OP_BINARY = 0x2
    const val OP_CLOSE = 0x8
    const val OP_PING = 0x9
    const val OP_PONG = 0xA

    private const val GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
    private const val MAX_PAYLOAD = 2 * 1024 * 1024

    fun expectedAccept(key: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest((key + GUID).toByteArray(Charsets.US_ASCII))
        return Base64.getEncoder().encodeToString(digest)
    }

    fun encodeClientFrame(opcode: Int, payload: ByteArray, mask: ByteArray): ByteArray {
        require(mask.size == 4) { "掩码必须是 4 字节" }
        require(payload.size <= MAX_PAYLOAD) { "帧载荷过大" }
        val out = ByteArrayOutputStream(payload.size + 14)
        out.write(0x80 or (opcode and 0x0f))
        when {
            payload.size < 126 -> out.write(0x80 or payload.size)
            payload.size <= 0xffff -> {
                out.write(0x80 or 126)
                out.write((payload.size ushr 8) and 0xff)
                out.write(payload.size and 0xff)
            }
            else -> {
                out.write(0x80 or 127)
                // 帧长上限 2 MiB，高 4 字节固定为 0。
                repeat(4) { out.write(0) }
                repeat(4) { index -> out.write((payload.size ushr ((3 - index) * 8)) and 0xff) }
            }
        }
        out.write(mask)
        val masked = ByteArray(payload.size) { index ->
            (payload[index].toInt() xor mask[index % 4].toInt()).toByte()
        }
        out.write(masked)
        return out.toByteArray()
    }
}
