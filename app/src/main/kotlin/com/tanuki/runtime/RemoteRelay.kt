package com.tanuki.runtime

import java.io.EOFException
import java.io.InputStream
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 无公网 IP 远程连接的中继配对契约。
 *
 * 设计边界：
 * - 手机与主机端都只向中继发起出站 TLS 连接，不在手机或远程主机上开放公网监听端口；
 * - 中继只负责房间配对与字节转发，SSH 仍由手机上的 JSch 与远程 sshd 端到端协商，
 *   因此中继既看不到 SSH 明文，也不能替代既有主机密钥指纹校验；
 * - 配对链接里的 token 是房间级一次性秘密，不是 SSH 密码/私钥，也绝不写入日志。
 */
data class RemoteRelayPairing(
    val relayHost: String,
    val relayPort: Int,
    val roomId: String,
    val token: String,
    val hostId: String,
    /** 空串表示使用系统 CA 并校验中继主机名；非空必须是 `sha256-<base64url>` 的证书钉扎。 */
    val certPin: String = ""
) {
    fun normalizedRelayHost(): String? = RemoteHostAddress.normalizeRelay(relayHost)

    fun validationError(): String? {
        if (normalizedRelayHost() == null) {
            return "中继地址格式无效；请使用 IPv4、IPv6 或域名生成配对链接"
        }
        if (relayPort !in 1..65535) return "中继端口必须在 1-65535 之间"
        if (decodeCanonical(roomId, 16) == null) return "配对房间号无效；请重新生成配对链接"
        if (decodeCanonical(token, 32) == null) return "配对密钥无效；请重新生成配对链接"
        if (!hostId.matches(HEX_16_BYTES)) return "主机标识无效；请重新生成配对链接"
        if (certPin.isNotEmpty() && RelayTlsPin.decode(certPin) == null) {
            return "中继证书指纹无效；必须是 sha256- 开头的 base64url SHA-256"
        }
        return null
    }

    /** 展示用中继端点；绝不包含 token。 */
    fun endpointLabel(): String {
        val host = normalizedRelayHost() ?: relayHost.trim()
        val shown = if (host.contains(':')) "[$host]" else host
        return "tls://$shown:$relayPort"
    }

    fun displayName(): String = "relay:${hostId.take(8)}"

    /** 生成可放进二维码/剪贴板的配对链接；调用方必须把返回值当作秘密处理。 */
    fun toUri(): String {
        val host = normalizedRelayHost() ?: relayHost.trim()
        val shown = if (host.contains(':')) "[$host]" else host
        val relay = "$shown:$relayPort"
        val builder = StringBuilder(URI_PREFIX)
        builder.append("relay=").append(encode(relay))
        builder.append("&room=").append(roomId)
        builder.append("&token=").append(token)
        builder.append("&host=").append(hostId)
        if (certPin.isNotEmpty()) builder.append("&pin=").append(certPin)
        return builder.toString()
    }

    override fun toString(): String =
        "RemoteRelayPairing(relay=${endpointLabel()}, room=${roomId.take(8)}…, hostId=$hostId, " +
            "certPin=${if (certPin.isEmpty()) "<system-ca>" else certPin}, token=<redacted>)"

    companion object {
        const val URI_PREFIX = "tanuki-relay://v1?"
        private val HEX_16_BYTES = Regex("[0-9a-f]{32}")
        private val ALLOWED_KEYS = setOf("relay", "room", "token", "host", "pin")

        /**
         * 严格解析配对链接：未知字段、重复字段、非规范 base64url、缺失 pin 之外的任何歧义都拒绝。
         * 返回 Result，异常消息只描述格式，不回显 token。
         */
        fun parse(uri: String): Result<RemoteRelayPairing> = runCatching {
            val trimmed = uri.trim()
            if (!trimmed.startsWith(URI_PREFIX)) {
                throw IllegalArgumentException("配对链接格式无效；应以 $URI_PREFIX 开头")
            }
            if (trimmed.contains('#') || trimmed.any { it.isISOControl() }) {
                throw IllegalArgumentException("配对链接包含不允许的字符")
            }
            val values = LinkedHashMap<String, String>()
            for (part in trimmed.substring(URI_PREFIX.length).split('&')) {
                if (part.isEmpty()) throw IllegalArgumentException("配对链接包含空字段")
                val separator = part.indexOf('=')
                if (separator <= 0) throw IllegalArgumentException("配对链接字段格式无效")
                val key = decode(part.substring(0, separator))
                val value = decode(part.substring(separator + 1))
                if (key !in ALLOWED_KEYS) throw IllegalArgumentException("配对链接包含未知字段")
                if (values.put(key, value) != null) throw IllegalArgumentException("配对链接字段重复")
            }
            val relay = values["relay"] ?: throw IllegalArgumentException("配对链接缺少中继地址")
            val room = values["room"] ?: throw IllegalArgumentException("配对链接缺少房间号")
            val token = values["token"] ?: throw IllegalArgumentException("配对链接缺少配对密钥")
            val hostId = values["host"] ?: throw IllegalArgumentException("配对链接缺少主机标识")
            val pin = values["pin"].orEmpty()
            val endpoint = parseEndpoint(relay)
            val pairing = RemoteRelayPairing(
                relayHost = endpoint.first,
                relayPort = endpoint.second,
                roomId = room,
                token = token,
                hostId = hostId,
                certPin = pin
            )
            pairing.validationError()?.let { throw IllegalArgumentException(it) }
            pairing
        }

        private fun parseEndpoint(value: String): Pair<String, Int> {
            val separator: Int
            val host: String
            if (value.startsWith("[")) {
                val close = value.indexOf(']')
                if (close <= 1 || close + 1 >= value.length || value[close + 1] != ':') {
                    throw IllegalArgumentException("中继地址格式无效")
                }
                host = value.substring(1, close)
                separator = close + 1
            } else {
                separator = value.lastIndexOf(':')
                if (separator <= 0) throw IllegalArgumentException("中继地址必须包含端口")
                host = value.substring(0, separator)
                if (host.contains(':')) throw IllegalArgumentException("IPv6 中继地址必须使用方括号")
            }
            if (value.indexOf(':', separator + 1) >= 0) throw IllegalArgumentException("中继地址格式无效")
            val port = value.substring(separator + 1).toIntOrNull()
                ?: throw IllegalArgumentException("中继端口无效")
            if (port !in 1..65535) throw IllegalArgumentException("中继端口必须在 1-65535 之间")
            val normalized = RemoteHostAddress.normalizeRelay(host)
                ?: throw IllegalArgumentException("中继地址格式无效")
            return normalized to port
        }

        private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

        private fun decode(value: String): String = runCatching { URLDecoder.decode(value, "UTF-8") }
            .getOrElse { throw IllegalArgumentException("配对链接字段编码无效") }
    }
}

/** 与 SSH 配置共用的主机规范化；拒绝协议、路径、userinfo 与不合法的 IPv6。 */
internal object RemoteHostAddress {
    fun normalize(value: String): String? {
        val text = value.trim()
        if (text.isEmpty()) return null
        if (text.startsWith("[") && text.endsWith("]")) {
            val inner = text.substring(1, text.length - 1)
            if (inner.length !in 2..45) return null
            if (!inner.matches(Regex("[0-9A-Fa-f:.]+")) || !inner.contains(':')) return null
            if (inner.contains(":::")) return null
            return inner.lowercase()
        }
        // 同时接受不带方括号的 IPv6，保证 normalize(normalize(x)) 幂等；
        // 单冒号仍视为“主机:端口”拒绝，避免把端口拼进主机名。
        if (text.count { it == ':' } >= 2) {
            if (text.length !in 2..45) return null
            if (!text.matches(Regex("[0-9A-Fa-f:.]+"))) return null
            if (text.contains(":::")) return null
            return text.lowercase()
        }
        if (text.length > 253) return null
        if (!text.matches(Regex("[A-Za-z0-9](?:[A-Za-z0-9._-]{0,251}[A-Za-z0-9])?"))) return null
        if (text.contains("..")) return null
        return text.lowercase()
    }

    /** 中继配对链接同时接受带/不带方括号的 IPv6；存储和拨号使用不带方括号的形式。 */
    fun normalizeRelay(value: String): String? {
        val text = value.trim()
        if (text.startsWith("[") && text.endsWith("]")) return normalize(text)
        if (text.count { it == ':' } >= 2) {
            if (text.length !in 2..45) return null
            if (!text.matches(Regex("[0-9A-Fa-f:.]+"))) return null
            if (text.contains(":::")) return null
            return text.lowercase()
        }
        return normalize(text)
    }
}

/** 中继控制帧类型；OPEN/OPEN_OK/CLOSE 只用于流生命周期，DATA 承载 SSH 密文。 */
enum class RelayFrameType(val code: Int) {
    CONTROL(1),
    OPEN(2),
    OPEN_OK(3),
    DATA(4),
    CLOSE(5),
    PING(6),
    PONG(7),
    ERROR(8);

    companion object {
        fun of(code: Int): RelayFrameType? = entries.firstOrNull { it.code == code }
    }
}

data class RelayFrame(val type: RelayFrameType, val streamId: Int, val payload: ByteArray) {
    fun payloadText(): String = payload.toString(Charsets.UTF_8)
}

/**
 * 长度前缀帧：4 字节大端总长（包含 1 字节类型与 4 字节流号），后面是负载。
 * 控制帧限制 1 KiB，所有帧总长限制 64 KiB，避免中继/客户端被超大帧拖垮。
 */
object RelayFrameCodec {
    const val MAX_FRAME_BYTES = 64 * 1024
    const val MAX_CONTROL_BYTES = 1024
    const val MAX_STREAM_ID = Int.MAX_VALUE

    fun control(text: String): RelayFrame {
        require(!text.contains('\n') && !text.contains('\r') && text.isNotEmpty()) { "控制帧必须是非空单行文本" }
        return RelayFrame(RelayFrameType.CONTROL, 0, text.toByteArray(Charsets.UTF_8))
    }

    fun encode(frame: RelayFrame): ByteArray {
        require(frame.streamId in 0..MAX_STREAM_ID) { "流号无效" }
        require(frame.payload.size + 5 <= MAX_FRAME_BYTES) { "中继帧过大" }
        if (frame.type == RelayFrameType.CONTROL) {
            require(frame.payload.size <= MAX_CONTROL_BYTES) { "中继控制帧过大" }
        }
        val buffer = ByteBuffer.allocate(4 + 5 + frame.payload.size)
        buffer.putInt(5 + frame.payload.size)
        buffer.put(frame.type.code.toByte())
        buffer.putInt(frame.streamId)
        buffer.put(frame.payload)
        return buffer.array()
    }

    /** 读取一帧；对端正常关闭返回 null，截断/非法帧抛出协议错误。 */
    fun read(input: InputStream): RelayFrame? {
        val header = ByteArray(4)
        val first = input.read()
        if (first == -1) return null
        header[0] = first.toByte()
        readFully(input, header, 1, 3)
        val length = ByteBuffer.wrap(header).int
        if (length < 5 || length > MAX_FRAME_BYTES) {
            throw RemoteRelayException(RelayFailureKind.PROTOCOL, "中继帧长度无效")
        }
        val body = ByteArray(length)
        readFully(input, body, 0, length)
        val buffer = ByteBuffer.wrap(body)
        val type = RelayFrameType.of(buffer.get().toInt() and 0xFF)
            ?: throw RemoteRelayException(RelayFailureKind.PROTOCOL, "中继帧类型未知")
        val stream = buffer.int
        if (stream < 0) throw RemoteRelayException(RelayFailureKind.PROTOCOL, "中继流号无效")
        val payload = ByteArray(length - 5)
        buffer.get(payload)
        if (type == RelayFrameType.CONTROL && payload.size > MAX_CONTROL_BYTES) {
            throw RemoteRelayException(RelayFailureKind.PROTOCOL, "中继控制帧过大")
        }
        return RelayFrame(type, stream, payload)
    }

    fun write(output: java.io.OutputStream, frame: RelayFrame) {
        output.write(encode(frame))
        output.flush()
    }

    private fun readFully(input: InputStream, target: ByteArray, offset: Int, length: Int) {
        var read = 0
        while (read < length) {
            val count = input.read(target, offset + read, length - read)
            if (count == -1) throw EOFException("中继连接在帧中途关闭")
            read += count
        }
    }
}

/** 配对证明：token 只作为 HMAC 密钥，不在控制帧里出现。 */
object RelayProof {
    private const val DOMAIN = "tanuki-relay-v1"

    fun clientProof(token: String, roomId: String, clientNonce: String, serverNonce: String): String =
        proof(token, listOf("client", roomId, clientNonce, serverNonce))

    fun hostProof(token: String, roomId: String, hostId: String, hostNonce: String, serverNonce: String): String =
        proof(token, listOf("host", roomId, hostId, hostNonce, serverNonce))

    fun verify(expected: String, actual: String): Boolean =
        MessageDigest.isEqual(expected.toByteArray(Charsets.UTF_8), actual.toByteArray(Charsets.UTF_8))

    private fun proof(token: String, fields: List<String>): String {
        val key = decodeSecret(token) ?: throw IllegalArgumentException("配对密钥无效")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        val message = (listOf(DOMAIN) + fields).joinToString("|")
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(message.toByteArray(Charsets.UTF_8)))
    }

    private fun decodeSecret(token: String): ByteArray? =
        runCatching { Base64.getUrlDecoder().decode(token) }.getOrNull()?.takeIf { it.size == 32 }
}

/** 中继证书钉扎：只接受 `sha256-<base64url(证书 DER 的 SHA-256)>`，不提供关闭 TLS 的开关。 */
object RelayTlsPin {
    private const val PREFIX = "sha256-"

    fun fingerprint(certificateDer: ByteArray): String =
        PREFIX + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(certificateDer))

    fun decode(pin: String): ByteArray? {
        if (!pin.startsWith(PREFIX)) return null
        val value = pin.substring(PREFIX.length)
        if (value.isEmpty() || !value.matches(Regex("[A-Za-z0-9_-]+"))) return null
        val decoded = runCatching { Base64.getUrlDecoder().decode(value) }.getOrNull() ?: return null
        if (decoded.size != 32) return null
        val canonical = Base64.getUrlEncoder().withoutPadding().encodeToString(decoded)
        return decoded.takeIf { canonical == value }
    }

    fun matches(pin: String, certificateDer: ByteArray): Boolean {
        val expected = decode(pin) ?: return false
        val actual = MessageDigest.getInstance("SHA-256").digest(certificateDer)
        return MessageDigest.isEqual(expected, actual)
    }

    /**
     * 确认页用的缩略指纹：保留前缀和末尾各一段，中间用省略号。
     *
     * 缩略只用于界面排版；展开后仍展示完整值，复制/核对时不能依赖缩略文本。
     */
    fun abbreviate(pin: String, head: Int = 16, tail: Int = 8): String {
        val text = pin.trim()
        if (text.length <= head + tail + 1) return text
        return text.take(head) + "…" + text.takeLast(tail)
    }
}

enum class RelayFailureKind {
    NETWORK,
    TLS,
    PIN_MISMATCH,
    AUTH,
    ROOM_NOT_FOUND,
    /** 房间曾经存在但已超过中继 TTL；与“从未存在”分开，方便给出重新生成链接的建议。 */
    EXPIRED,
    ROOM_BUSY,
    HOST_OFFLINE,
    PROTOCOL,
    IO
}

/** 中继失败；消息可直接展示给用户，不含 token、配对链接或原始对端数据。 */
class RemoteRelayException(
    val kind: RelayFailureKind,
    message: String
) : Exception(message) {
    /** 只重试可恢复的网络/房间临时状态；认证、指纹和协议错误立即失败。 */
    val retryable: Boolean
        get() = kind == RelayFailureKind.NETWORK ||
            kind == RelayFailureKind.ROOM_BUSY ||
            kind == RelayFailureKind.HOST_OFFLINE ||
            kind == RelayFailureKind.IO
}

/**
 * 控制通道重连退避。maxAttempts 是总尝试次数（首次 + 至多 maxAttempts-1 次退避）；
 * 已建立的 SSH 会话重连属于后续切片，不在这里伪装成透明恢复。
 */
data class RelayReconnectPolicy(
    val maxAttempts: Int = 4,
    val baseDelayMillis: Long = 1_000,
    val maxDelayMillis: Long = 15_000,
    val jitterFraction: Double = 0.2
) {
    init {
        require(maxAttempts in 1..10)
        require(baseDelayMillis in 100..60_000)
        require(maxDelayMillis >= baseDelayMillis)
        require(jitterFraction in 0.0..1.0)
    }

    /** attempt 从 1 开始；返回带抖动的等待时间，避免同一中继上所有客户端同时重试。 */
    fun delayMillis(attempt: Int, random: java.util.Random = java.util.Random()): Long {
        val safeAttempt = attempt.coerceAtLeast(1)
        val exponential = (baseDelayMillis shl (safeAttempt - 1).coerceAtMost(20)).coerceAtMost(maxDelayMillis)
        val jitter = (exponential * jitterFraction).toLong()
        if (jitter <= 0) return exponential
        return exponential - jitter + (random.nextDouble() * (jitter * 2 + 1)).toLong()
    }
}

/** 中继 ERROR 码到可展示失败的映射；未知码按协议错误处理，不回显原始内容。 */
object RelayErrors {
    fun fromCode(code: String): RemoteRelayException = when (code) {
        "room_not_found" -> RemoteRelayException(
            RelayFailureKind.ROOM_NOT_FOUND,
            "中继上找不到这个配对房间；请确认链接来自电脑上正在运行的那次配对"
        )
        "bad_token" -> RemoteRelayException(
            RelayFailureKind.AUTH,
            "配对密钥不正确；请重新复制电脑上的配对链接"
        )
        "room_busy" -> RemoteRelayException(
            RelayFailureKind.ROOM_BUSY,
            "该配对房间已有客户端连接；请稍后重试或重新配对"
        )
        "host_offline" -> RemoteRelayException(
            RelayFailureKind.HOST_OFFLINE,
            "电脑端当前未连接到中继；请确认电脑上的远程连接服务仍在运行"
        )
        "host_timeout" -> RemoteRelayException(
            RelayFailureKind.HOST_OFFLINE,
            "电脑连接本机 SSH 超时；请确认远程电脑的 sshd 已启动"
        )
        "expired" -> RemoteRelayException(
            RelayFailureKind.EXPIRED,
            "配对链接已过期；请在电脑上重新生成后再复制"
        )
        "too_large" -> RemoteRelayException(RelayFailureKind.PROTOCOL, "中继控制帧过大")
        "protocol" -> RemoteRelayException(RelayFailureKind.PROTOCOL, "中继协议错误")
        else -> RemoteRelayException(
            RelayFailureKind.IO,
            "中继返回错误：" + code.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.take(64).ifBlank { "protocol" }
        )
    }
}

/** 随机配对材料只用于主机端生成链接与测试；手机端只解析，不自行创建房间。 */
object RelayPairingMaterial {
    private val random = SecureRandom()

    fun roomId(): String = randomBytes(16)

    fun token(): String = randomBytes(32)

    fun hostId(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return buildString(bytes.size * 2) {
            for (byte in bytes) append("%02x".format(byte.toInt() and 0xFF))
        }
    }

    fun nonce(): String = randomBytes(16)

    private fun randomBytes(size: Int): String {
        val bytes = ByteArray(size)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}

private fun decodeCanonical(value: String, expectedBytes: Int): ByteArray? {
    if (value.isEmpty() || !value.matches(Regex("[A-Za-z0-9_-]+"))) return null
    val decoded = runCatching { Base64.getUrlDecoder().decode(value) }.getOrNull() ?: return null
    if (decoded.size != expectedBytes) return null
    val canonical = Base64.getUrlEncoder().withoutPadding().encodeToString(decoded)
    return decoded.takeIf { canonical == value }
}
