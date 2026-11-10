package com.tanuki.runtime

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * 中继 socket 连接器。生产实现 [RelayTlsSocket] 只允许 TLS；内部可见的替换点
 * 只用于 JVM 回归注入 loopback 假中继，不暴露给设置页，也不存在“关闭 TLS”的用户开关。
 */
internal fun interface RelaySocketConnector {
    fun connect(pairing: RemoteRelayPairing, timeoutMs: Int): Socket
}

/** 生产 TLS 连接：系统 CA + 主机名校验，或配对链接里的证书 SHA-256 钉扎；两者必选其一。 */
internal object RelayTlsSocket {
    fun connect(pairing: RemoteRelayPairing, timeoutMs: Int): Socket {
        val host = pairing.normalizedRelayHost()
            ?: throw RemoteRelayException(RelayFailureKind.NETWORK, "中继地址格式无效")
        val context = SSLContext.getInstance("TLS")
        val pin = pairing.certPin
        if (pin.isEmpty()) {
            // 没有钉扎时使用系统 CA 与 HTTPS 主机名校验；失败就失败，不回退证书信任。
            context.init(null, null, null)
        } else {
            val expected = RelayTlsPin.decode(pin)
                ?: throw RemoteRelayException(RelayFailureKind.PIN_MISMATCH, "中继证书指纹格式无效")
            context.init(null, arrayOf(PinnedTrustManager(expected, pin)), null)
        }
        val socket = context.socketFactory.createSocket() as SSLSocket
        try {
            socket.connect(InetSocketAddress(host, pairing.relayPort), timeoutMs)
            socket.soTimeout = timeoutMs
            val parameters = socket.sslParameters
            val protocols = parameters.protocols.filter { it == "TLSv1.2" || it == "TLSv1.3" }
            if (protocols.isEmpty()) {
                throw RemoteRelayException(RelayFailureKind.TLS, "当前系统不支持 TLS 1.2/1.3")
            }
            parameters.protocols = protocols.toTypedArray()
            // 钉扎时证书本身就是身份；没有钉扎必须做标准主机名校验。
            parameters.endpointIdentificationAlgorithm = if (pin.isEmpty()) "HTTPS" else null
            socket.sslParameters = parameters
            socket.startHandshake()
            val protocol = socket.session.protocol
            if (protocol != "TLSv1.2" && protocol != "TLSv1.3") {
                throw RemoteRelayException(RelayFailureKind.TLS, "中继 TLS 版本过低")
            }
            return socket
        } catch (error: Throwable) {
            runCatching { socket.close() }
            throw translate(error)
        }
    }

    private fun translate(error: Throwable): RemoteRelayException {
        if (error is RemoteRelayException) return error
        if (error is SSLHandshakeException) {
            val text = generateSequence(error as Throwable) { it.cause }
                .mapNotNull { it.message }
                .joinToString(" ")
            if (text.contains("中继证书指纹不匹配") || text.contains("PIN")) {
                return RemoteRelayException(RelayFailureKind.PIN_MISMATCH, "中继证书指纹不匹配，已拒绝连接")
            }
            return RemoteRelayException(RelayFailureKind.TLS, "中继 TLS 握手失败；请检查网络和证书")
        }
        if (error is SocketTimeoutException) {
            return RemoteRelayException(RelayFailureKind.NETWORK, "连接中继超时；请检查网络")
        }
        if (error is UnknownHostException) {
            return RemoteRelayException(RelayFailureKind.NETWORK, "无法解析中继地址")
        }
        if (error is IOException) {
            return RemoteRelayException(RelayFailureKind.NETWORK, "无法连接中继；请检查网络")
        }
        return RemoteRelayException(RelayFailureKind.IO, "中继连接失败")
    }

    /** 只信任配对链接中证书 DER 的 SHA-256；仍检查证书有效期，不做链验证。 */
    private class PinnedTrustManager(
        private val expected: ByteArray,
        private val pin: String
    ) : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {
            throw CertificateException("中继客户端证书不受支持")
        }

        override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {
            val leaf = chain?.firstOrNull() ?: throw CertificateException("中继未提供证书")
            try {
                leaf.checkValidity()
            } catch (error: Exception) {
                throw CertificateException("中继证书已过期或尚未生效")
            }
            val actual = MessageDigest.getInstance("SHA-256").digest(leaf.encoded)
            if (!MessageDigest.isEqual(expected, actual) || !RelayTlsPin.matches(pin, leaf.encoded)) {
                throw CertificateException("中继证书指纹不匹配")
            }
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
}

/**
 * 已完成配对握手的客户端会话。握手成功后中继把该 TLS 流当作 SSH 字节管道；
 * 客户端再把它桥接到本机回环 TCP，JSch 只看到普通的 127.0.0.1 连接。
 */
class RelayClientSession internal constructor(
    private val socket: Socket,
    private val streamId: Int,
    private val pairing: RemoteRelayPairing,
    private val acceptTimeoutMs: Int = 15_000
) : AutoCloseable {
    internal companion object {
        internal fun join(
            pairing: RemoteRelayPairing,
            connector: RelaySocketConnector,
            timeoutMs: Int = 15_000
        ): RelayClientSession = RelayClientHandshake.join(pairing, connector, timeoutMs)
    }
    private val closed = AtomicBoolean(false)
    private val writerLock = Any()
    // 不使用 Buffered*：握手读取若预读，会把 OPEN_OK 之后紧邻的首批 SSH 字节吞进旧缓冲区。
    private val input = DataInputStream(socket.getInputStream())
    private val output = DataOutputStream(socket.getOutputStream())

    @Volatile
    private var bridge: RelayBridge? = null

    /** 建立只监听 127.0.0.1 的桥；JSch 连接到返回的 localPort 后开始透传。 */
    fun openBridge(): RelayBridge = synchronized(this) {
        check(!closed.get()) { "中继会话已关闭" }
        if (bridge != null) throw IllegalStateException("中继桥已建立")
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        server.soTimeout = acceptTimeoutMs
        val created = RelayBridge(server, socket, streamId, input, output, writerLock, closed)
        bridge = created
        created.start()
        created
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { RelayFrameCodec.write(output, RelayFrame(RelayFrameType.CLOSE, streamId, ByteArray(0))) }
        runCatching { socket.close() }
        bridge?.close()
    }
}

/**
 * loopback 桥：接受一个本机连接，把 SSH 字节封装成 DATA 帧发给中继，并把中继 DATA 写回本机。
 * 桥只绑定回环地址，且拒绝非回环 peer；不向局域网/中继暴露额外的监听端口。
 */
class RelayBridge internal constructor(
    private val server: ServerSocket,
    private val relaySocket: Socket,
    private val streamId: Int,
    private val input: DataInputStream,
    private val output: DataOutputStream,
    private val writerLock: Any,
    private val ownerClosed: AtomicBoolean
) : AutoCloseable {
    val localPort: Int = server.localPort

    private val closed = AtomicBoolean(false)
    @Volatile private var local: Socket? = null
    @Volatile private var failure: Throwable? = null
    private var thread: Thread? = null

    /** 测试/诊断用；生产 UI 只展示经过映射的中文错误。 */
    internal fun failureReason(): Throwable? = failure

    internal fun start() {
        val worker = Thread({ run() }, "tanuki-relay-bridge")
        worker.isDaemon = true
        thread = worker
        worker.start()
    }

    fun isAlive(): Boolean {
        val socket = local
        return !closed.get() && !ownerClosed.get() && socket != null && !socket.isClosed &&
            relaySocket.isConnected && !relaySocket.isClosed
    }

    private fun run() {
        try {
            val accepted = server.accept()
            if (!accepted.inetAddress.isLoopbackAddress) {
                runCatching { accepted.close() }
                throw RemoteRelayException(RelayFailureKind.PROTOCOL, "中继桥拒绝了非回环连接")
            }
            local = accepted
            accepted.tcpNoDelay = true
            relaySocket.tcpNoDelay = true
            val uplink = Thread({ pumpLocalToRelay(accepted) }, "tanuki-relay-uplink")
            uplink.isDaemon = true
            uplink.start()
            pumpRelayToLocal(accepted)
        } catch (error: Throwable) {
            failure = error
            close()
        } finally {
            runCatching { server.close() }
        }
    }

    private fun pumpLocalToRelay(source: Socket) {
        val buffer = ByteArray(8 * 1024)
        try {
            val stream = source.getInputStream()
            while (!closed.get()) {
                val count = stream.read(buffer)
                if (count == -1) break
                if (count == 0) continue
                synchronized(writerLock) {
                    if (!closed.get()) {
                        RelayFrameCodec.write(output, RelayFrame(RelayFrameType.DATA, streamId, buffer.copyOf(count)))
                    }
                }
            }
            if (!closed.get()) {
                synchronized(writerLock) {
                    runCatching { RelayFrameCodec.write(output, RelayFrame(RelayFrameType.CLOSE, streamId, ByteArray(0))) }
                }
            }
        } catch (error: Throwable) {
            if (!closed.get()) failure = error
        } finally {
            close()
        }
    }

    private fun pumpRelayToLocal(target: Socket) {
        try {
            val stream = target.getOutputStream()
            while (!closed.get()) {
                val frame = RelayFrameCodec.read(input) ?: break
                when (frame.type) {
                    RelayFrameType.DATA -> {
                        if (frame.streamId != streamId) {
                            throw RemoteRelayException(RelayFailureKind.PROTOCOL, "中继返回了未知流的数据")
                        }
                        stream.write(frame.payload)
                        stream.flush()
                    }
                    RelayFrameType.OPEN_OK -> Unit
                    RelayFrameType.CLOSE -> break
                    RelayFrameType.PING -> synchronized(writerLock) {
                        if (!closed.get()) {
                            RelayFrameCodec.write(output, RelayFrame(RelayFrameType.PONG, streamId, ByteArray(0)))
                        }
                    }
                    RelayFrameType.PONG -> Unit
                    RelayFrameType.ERROR -> {
                        val code = frame.payloadText().ifBlank { "protocol" }
                        throw RelayErrors.fromCode(code)
                    }
                    RelayFrameType.CONTROL -> throw RemoteRelayException(
                        RelayFailureKind.PROTOCOL,
                        "中继在数据阶段发送了控制帧"
                    )
                    RelayFrameType.OPEN -> throw RemoteRelayException(
                        RelayFailureKind.PROTOCOL,
                        "中继在数据阶段发送了重复的流打开帧"
                    )
                }
            }
        } catch (error: Throwable) {
            if (!closed.get() && error !is EOFException) failure = error
        } finally {
            close()
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { server.close() }
        runCatching { local?.close() }
        runCatching {
            synchronized(writerLock) {
                RelayFrameCodec.write(output, RelayFrame(RelayFrameType.CLOSE, streamId, ByteArray(0)))
            }
        }
        runCatching { relaySocket.close() }
        thread?.interrupt()
    }
}

/** 解析中继控制帧文本；只接受 ASCII 单词与有限字段数。 */
internal fun parseRelayControl(frame: RelayFrame): List<String>? {
    if (frame.type != RelayFrameType.CONTROL || frame.streamId != 0) return null
    val text = frame.payloadText()
    if (text.isEmpty() || text.contains('\n') || text.contains('\r')) return null
    val parts = text.split(' ')
    if (parts.isEmpty() || parts.size > 8 || parts.any { it.isEmpty() }) return null
    return parts
}

/**
 * 把既有 JSch SSH 隧道切到中继：先建立中继桥，再让 JSch 连接 127.0.0.1:<桥端口>。
 * 主机密钥仍由 [JschSshTransport] 的指纹仓库校验，中继无法替代或关闭该检查。
 */
internal class RelaySshTransport(
    private val direct: JschSshTransport = JschSshTransport(),
    private val connector: RelaySocketConnector = RelaySocketConnector { pairing, timeout ->
        RelayTlsSocket.connect(pairing, timeout)
    },
    private val connectTimeoutMs: Int = 15_000
) : SshTransport {
    override suspend fun probeHostKey(config: RemoteHostConfig): HostKeyProbe = withContext(Dispatchers.IO) {
        withBridge(config) { bridge ->
            direct.dialing("127.0.0.1", bridge.localPort).probeHostKey(config)
        }
    }

    override suspend fun open(config: RemoteHostConfig, expectedFingerprint: String): SshTunnel =
        withContext(Dispatchers.IO) {
            val pairing = config.relay ?: throw RemoteHostFailure("远程配置未包含中继配对信息")
            val session = join(pairing)
            val bridge = session.openBridge()
            try {
                val tunnel = direct.dialing("127.0.0.1", bridge.localPort)
                    .open(config, expectedFingerprint)
                RelaySshTunnel(tunnel, bridge, session)
            } catch (error: Throwable) {
                bridge.close()
                session.close()
                throw error
            }
        }

    private suspend fun <T> withBridge(config: RemoteHostConfig, block: suspend (RelayBridge) -> T): T {
        val pairing = config.relay ?: throw RemoteHostFailure("远程配置未包含中继配对信息")
        val session = join(pairing)
        val bridge = session.openBridge()
        return try {
            block(bridge)
        } finally {
            bridge.close()
            session.close()
        }
    }

    private fun join(pairing: RemoteRelayPairing): RelayClientSession =
        RelayClientSession.join(pairing, connector, connectTimeoutMs)
}

private class RelaySshTunnel(
    private val tunnel: SshTunnel,
    private val bridge: RelayBridge,
    private val session: RelayClientSession
) : SshTunnel {
    override val localPort: Int get() = tunnel.localPort

    override fun isAlive(): Boolean = tunnel.isAlive() && bridge.isAlive()

    override fun close() {
        runCatching { tunnel.close() }
        runCatching { bridge.close() }
        runCatching { session.close() }
    }
}

/**
 * 客户端 JOIN 流程：HELLO -> CHALLENGE -> AUTH -> QUEUED -> OPEN_OK。
 * 控制帧只出现在 SSH 数据开始前；OPEN_OK 之后同一 TLS 流只承载 DATA/CLOSE/PING。
 */
internal object RelayClientHandshake {
    fun join(
        pairing: RemoteRelayPairing,
        connector: RelaySocketConnector,
        timeoutMs: Int
    ): RelayClientSession {
        val problem = pairing.validationError()
        if (problem != null) throw RemoteRelayException(RelayFailureKind.PROTOCOL, problem)
        val socket = connector.connect(pairing, timeoutMs)
        try {
            socket.soTimeout = timeoutMs
            val input = DataInputStream(socket.getInputStream())
            val output = DataOutputStream(socket.getOutputStream())
            val clientNonce = RelayPairingMaterial.nonce()
            RelayFrameCodec.write(
                output,
                RelayFrameCodec.control("HELLO 1 client ${pairing.roomId} - $clientNonce")
            )
            val challenge = readControl(input)
            if (challenge.firstOrNull() != "CHALLENGE" || challenge.size != 2) {
                throw RemoteRelayException(RelayFailureKind.PROTOCOL, "中继未返回配对挑战")
            }
            val serverNonce = challenge[1]
            val proof = runCatching {
                RelayProof.clientProof(pairing.token, pairing.roomId, clientNonce, serverNonce)
            }.getOrElse {
                throw RemoteRelayException(RelayFailureKind.AUTH, "配对密钥格式无效；请重新生成配对链接")
            }
            RelayFrameCodec.write(output, RelayFrameCodec.control("AUTH $proof"))
            var streamId = -1
            while (streamId < 0) {
                val frame = RelayFrameCodec.read(input)
                    ?: throw RemoteRelayException(RelayFailureKind.NETWORK, "中继在配对完成前关闭了连接")
                when (frame.type) {
                    RelayFrameType.CONTROL -> {
                        val parts = parseRelayControl(frame)
                            ?: throw RemoteRelayException(RelayFailureKind.PROTOCOL, "中继控制帧格式无效")
                        when (parts.firstOrNull()) {
                            "QUEUED" -> Unit
                            "ERROR" -> throw RelayErrors.fromCode(parts.getOrNull(1).orEmpty())
                            "PING" -> RelayFrameCodec.write(
                                output,
                                RelayFrame(RelayFrameType.PONG, 0, ByteArray(0))
                            )
                            else -> throw RemoteRelayException(
                                RelayFailureKind.PROTOCOL,
                                "中继返回了未预期的配对状态"
                            )
                        }
                    }
                    RelayFrameType.OPEN_OK -> {
                        if (frame.streamId <= 0) {
                            throw RemoteRelayException(RelayFailureKind.PROTOCOL, "中继返回了无效流号")
                        }
                        streamId = frame.streamId
                    }
                    RelayFrameType.ERROR -> throw RelayErrors.fromCode(frame.payloadText())
                    else -> throw RemoteRelayException(RelayFailureKind.PROTOCOL, "中继返回了未预期的帧")
                }
            }
            socket.soTimeout = 0
            return RelayClientSession(socket, streamId, pairing)
        } catch (error: Throwable) {
            runCatching { socket.close() }
            throw translateHandshake(error)
        }
    }

    private fun readControl(input: DataInputStream): List<String> {
        val frame = RelayFrameCodec.read(input)
            ?: throw RemoteRelayException(RelayFailureKind.NETWORK, "中继在配对完成前关闭了连接")
        if (frame.type == RelayFrameType.ERROR) throw RelayErrors.fromCode(frame.payloadText())
        return parseRelayControl(frame)
            ?: throw RemoteRelayException(RelayFailureKind.PROTOCOL, "中继控制帧格式无效")
    }

    private fun translateHandshake(error: Throwable): RemoteRelayException {
        if (error is RemoteRelayException) return error
        if (error is SocketTimeoutException) {
            return RemoteRelayException(RelayFailureKind.NETWORK, "中继配对超时；请确认主机端 agent 在线")
        }
        if (error is UnknownHostException) {
            return RemoteRelayException(RelayFailureKind.NETWORK, "无法解析中继地址")
        }
        if (error is IOException) {
            return RemoteRelayException(RelayFailureKind.NETWORK, "中继连接中断；请检查网络")
        }
        return RemoteRelayException(RelayFailureKind.IO, "中继配对失败")
    }
}

/**
 * 按配置选择直连或中继；不改变 [SshTransport] 契约，因此控制器、origin 状态机和
 * Cookie 隔离都不需要知道底层是 TCP 直连还是 TLS 中继桥。
 */
class RoutingSshTransport(
    private val direct: SshTransport = JschSshTransport(),
    private val relay: SshTransport = RelaySshTransport()
) : SshTransport {
    override suspend fun probeHostKey(config: RemoteHostConfig): HostKeyProbe =
        if (config.relay != null) relay.probeHostKey(config) else direct.probeHostKey(config)

    override suspend fun open(config: RemoteHostConfig, expectedFingerprint: String): SshTunnel =
        if (config.relay != null) relay.open(config, expectedFingerprint) else direct.open(config, expectedFingerprint)
}
