package com.tanuki

import com.tanuki.runtime.AesGcmSecretVault
import com.tanuki.runtime.HostKeyProbe
import com.tanuki.runtime.KeyValueStore
import com.tanuki.runtime.RelayBridge
import com.tanuki.runtime.RelayClientSession
import com.tanuki.runtime.RelayFailureKind
import com.tanuki.runtime.RelayFrame
import com.tanuki.runtime.RelayFrameCodec
import com.tanuki.runtime.RelayFrameType
import com.tanuki.runtime.RelayPairingMaterial
import com.tanuki.runtime.RelayProof
import com.tanuki.runtime.RelayReconnectPolicy
import com.tanuki.runtime.RelaySocketConnector
import com.tanuki.runtime.RelayTlsPin
import com.tanuki.runtime.RemoteAuthType
import com.tanuki.runtime.RemoteHostConfig
import com.tanuki.runtime.RemoteHostController
import com.tanuki.runtime.RemoteHostMachine
import com.tanuki.runtime.RemoteHostStorage
import com.tanuki.runtime.RemotePhase
import com.tanuki.runtime.RemoteRelayException
import com.tanuki.runtime.RemoteRelayPairing
import com.tanuki.runtime.SshTunnel
import com.tanuki.runtime.relayRetry
import com.tanuki.runtime.SshTransport
import com.tanuki.runtime.parseRelayControl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.KeyGenerator

class RemoteRelayTest {
    private val scopes = mutableListOf<CoroutineScope>()
    private val servers = mutableListOf<EchoRelayServer>()

    @After
    fun tearDown() {
        servers.forEach { runCatching { it.close() } }
        scopes.forEach { it.cancel() }
    }

    private val vectors: JSONObject by lazy {
        val stream = javaClass.classLoader!!.getResourceAsStream("relay-protocol-vectors.json")
            ?: error("missing relay-protocol-vectors.json")
        JSONObject(stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
    }

    private fun pairing(
        relayHost: String = "relay.example.test",
        relayPort: Int = 8443,
        room: String = "ICEiIyQlJicoKSorLC0uLw",
        token: String = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8",
        hostId: String = "303132333435363738393a3b3c3d3e3f",
        pin: String = ""
    ) = RemoteRelayPairing(relayHost, relayPort, room, token, hostId, pin)

    @Test
    fun pairingUriRoundTripsForDomainIpv4AndIpv6() {
        listOf("relay.example.test", "203.0.113.7", "2001:db8::9").forEach { host ->
            val original = pairing(relayHost = host)
            val parsed = RemoteRelayPairing.parse(original.toUri()).getOrThrow()
            assertEquals(host.lowercase(), parsed.normalizedRelayHost())
            assertEquals(original, parsed)
        }
        val pinned = pairing(relayHost = "2001:db8::9", pin = RelayTlsPin.fingerprint("synthetic-cert".toByteArray()))
        assertEquals(pinned, RemoteRelayPairing.parse(pinned.toUri()).getOrThrow())
    }

    @Test
    fun pairingUriRejectsUnknownDuplicateAndNonCanonicalFields() {
        val uri = pairing().toUri()
        assertTrue(RemoteRelayPairing.parse("$uri&extra=1").isFailure)
        assertTrue(RemoteRelayPairing.parse("$uri&room=ICEiIyQlJicoKSorLC0uLw").isFailure)
        assertTrue(RemoteRelayPairing.parse(uri.replace("relay=", "relay=%00")).isFailure)
        assertTrue(RemoteRelayPairing.parse("$uri#fragment").isFailure)
        assertTrue(RemoteRelayPairing.parse("https://relay.example.test").isFailure)
        // 带 padding 的 base64url 不是规范形式，必须拒绝。
        assertTrue(RemoteRelayPairing.parse(uri.replace("ICEiIyQlJicoKSorLC0uLw", "ICEiIyQlJicoKSorLC0uLw==")).isFailure)
    }

    @Test
    fun pairingValidationRejectsWeakMaterialAndBadPin() {
        assertNotNull(pairing(relayPort = 0).validationError())
        assertNotNull(pairing(relayPort = 65536).validationError())
        assertNotNull(pairing(relayHost = "http://relay.example.test").validationError())
        assertNotNull(pairing(room = "short").validationError())
        assertNotNull(pairing(token = "short").validationError())
        assertNotNull(pairing(hostId = "ABCDEF").validationError())
        assertNotNull(pairing(pin = "sha256-not-base64!").validationError())
        assertNotNull(pairing(pin = "sha256-AAAA").validationError())
        assertNull(pairing(pin = RelayTlsPin.fingerprint("cert".toByteArray())).validationError())
    }

    @Test
    fun pairingStringAndEndpointNeverLeakToken() {
        val value = pairing(relayHost = "2001:db8::9")
        val text = value.toString() + value.endpointLabel() + value.displayName()
        assertFalse(text.contains(value.token))
        assertFalse(value.endpointLabel().contains("token"))
        assertTrue(value.endpointLabel().startsWith("tls://[2001:db8::9]:"))
        assertTrue(value.toUri().contains(value.token))
    }

    @Test
    fun frameCodecRoundTripsDataAndControl() {
        val control = RelayFrameCodec.control("HELLO 1 client room - nonce")
        val decodedControl = RelayFrameCodec.read(ByteArrayInputStream(RelayFrameCodec.encode(control)))!!
        assertEquals(RelayFrameType.CONTROL, decodedControl.type)
        assertEquals(0, decodedControl.streamId)
        assertEquals("HELLO 1 client room - nonce", decodedControl.payloadText())
        assertEquals(listOf("HELLO", "1", "client", "room", "-", "nonce"), parseRelayControl(decodedControl))

        val data = RelayFrame(RelayFrameType.DATA, 42, byteArrayOf(1, 2, 3, 4))
        val decodedData = RelayFrameCodec.read(ByteArrayInputStream(RelayFrameCodec.encode(data)))!!
        assertEquals(RelayFrameType.DATA, decodedData.type)
        assertEquals(42, decodedData.streamId)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), decodedData.payload)
        assertNull(parseRelayControl(decodedData))
    }

    @Test
    fun frameCodecRejectsOversizedControlUnknownTypeAndTruncation() {
        assertThrows(IllegalArgumentException::class.java) {
            RelayFrameCodec.encode(RelayFrameCodec.control("x".repeat(RelayFrameCodec.MAX_CONTROL_BYTES + 1)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RelayFrameCodec.encode(RelayFrame(RelayFrameType.DATA, 1, ByteArray(RelayFrameCodec.MAX_FRAME_BYTES)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RelayFrameCodec.encode(RelayFrame(RelayFrameType.DATA, -1, ByteArray(1)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RelayFrameCodec.control("line\nbreak")
        }
        val unknown = byteArrayOf(0, 0, 0, 5, 99, 0, 0, 0, 1)
        assertThrows(RemoteRelayException::class.java) {
            RelayFrameCodec.read(ByteArrayInputStream(unknown))
        }
        assertThrows(EOFException::class.java) {
            RelayFrameCodec.read(ByteArrayInputStream(byteArrayOf(0, 0, 0, 10, 1)))
        }
    }

    @Test
    fun proofVectorsMatchSharedFixture() {
        val token = vectors.getString("token")
        val room = vectors.getString("room")
        val hostId = vectors.getString("hostId")
        val clientNonce = vectors.getString("clientNonce")
        val serverNonce = vectors.getString("serverNonce")
        val hostNonce = vectors.getString("hostNonce")
        assertEquals(vectors.getString("clientProof"), RelayProof.clientProof(token, room, clientNonce, serverNonce))
        assertEquals(vectors.getString("hostProof"), RelayProof.hostProof(token, room, hostId, hostNonce, serverNonce))
        assertTrue(
            RelayProof.verify(
                vectors.getString("clientProof"),
                RelayProof.clientProof(token, room, clientNonce, serverNonce)
            )
        )
        assertFalse(RelayProof.verify(vectors.getString("clientProof"), vectors.getString("hostProof")))
    }

    @Test
    fun tlsPinMatchesCertificateDigestAndRejectsOther() {
        val der = "synthetic-certificate-der".toByteArray()
        val pin = RelayTlsPin.fingerprint(der)
        assertTrue(pin.startsWith("sha256-"))
        assertTrue(RelayTlsPin.matches(pin, der))
        assertFalse(RelayTlsPin.matches(pin, "other".toByteArray()))
        assertNull(RelayTlsPin.decode("sha256-AAAA"))
        assertNull(RelayTlsPin.decode("md5-abc"))
        assertNull(RelayTlsPin.decode(pin + "="))
    }

    @Test
    fun reconnectPolicyRetriesOnlyTransientFailures() {
        val policy = RelayReconnectPolicy(maxAttempts = 4, baseDelayMillis = 1000, maxDelayMillis = 8000, jitterFraction = 0.0)
        assertEquals(1000, policy.delayMillis(1))
        assertEquals(2000, policy.delayMillis(2))
        assertEquals(4000, policy.delayMillis(3))
        assertEquals(8000, policy.delayMillis(4))
        assertEquals(8000, policy.delayMillis(9))
        assertTrue(RemoteRelayException(RelayFailureKind.NETWORK, "x").retryable)
        assertTrue(RemoteRelayException(RelayFailureKind.ROOM_BUSY, "x").retryable)
        assertTrue(RemoteRelayException(RelayFailureKind.HOST_OFFLINE, "x").retryable)
        assertFalse(RemoteRelayException(RelayFailureKind.AUTH, "x").retryable)
        assertFalse(RemoteRelayException(RelayFailureKind.PIN_MISMATCH, "x").retryable)
        assertFalse(RemoteRelayException(RelayFailureKind.PROTOCOL, "x").retryable)
    }

    @Test
    fun relayClientJoinsAndBridgesBytesWithoutSwallowingEarlyData() {
        val banner = "SSH-2.0-synthetic\r\n".toByteArray()
        val relay = EchoRelayServer(vectors.getString("token"), banner = banner)
        servers += relay
        val value = pairing(relayHost = "127.0.0.1", relayPort = relay.server.localPort)
        val session = RelayClientSession.join(value, relay.connector, 3_000)
        val bridge = session.openBridge()
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", bridge.localPort), 3_000)
                socket.soTimeout = 3_000
                assertArrayEquals(banner, readFully(socket.getInputStream(), banner.size))
                val payload = "SSH-2.0-client\r\n".toByteArray()
                socket.getOutputStream().write(payload)
                socket.getOutputStream().flush()
                assertArrayEquals(payload, readFully(socket.getInputStream(), payload.size))
            }
        } finally {
            bridge.close()
            session.close()
        }
    }

    @Test
    fun relayClientRejectsBadToken() {
        val relay = EchoRelayServer(RelayPairingMaterial.token())
        servers += relay
        val value = pairing(relayHost = "127.0.0.1", relayPort = relay.server.localPort)
        val error = assertThrows(RemoteRelayException::class.java) {
            RelayClientSession.join(value, relay.connector, 3_000)
        }
        assertEquals(RelayFailureKind.AUTH, error.kind)
        assertFalse(error.message.orEmpty().contains(value.token))
    }

    @Test
    fun relayConfigUsesStableIdentityAndCookieScope() {
        val value = relayConfig()
        val otherRoom = value.copy(relay = value.relay!!.copy(roomId = "ICEiIyQlJicoKSorLC0uLw"))
        val thirdRoom = value.copy(relay = value.relay!!.copy(roomId = "MDEyMzQ1Njc4OWFiY2RlZg"))
        assertEquals("relay:${value.relay!!.hostId}", value.hostIdentity())
        assertEquals(value.hostIdentity(), otherRoom.hostIdentity())
        assertEquals(value.hostIdentity(), thirdRoom.hostIdentity())
        assertEquals(value.cookieScope(), thirdRoom.cookieScope())
        assertFalse(value.cookieScope().contains(value.relay!!.roomId))
        assertEquals("relay-display", value.displayName())
        assertEquals(value.relay!!.displayName(), value.copy(host = "").displayName())
        assertNull(value.validationError())
    }

    @Test
    fun storageRoundTripsRelayAndEncryptsToken() {
        val fixture = Fixture()
        val value = relayConfig()
        fixture.storage.save(value)
        val loaded = fixture.storage.load()
        assertEquals(value.relay, loaded?.relay)
        assertEquals(value.user, loaded?.user)
        assertEquals(value.remoteDshPort, loaded?.remoteDshPort)
        assertTrue(fixture.config.values.values.none { it.contains(value.relay!!.token) })
        assertTrue(fixture.secrets.values.values.none { it.contains(value.relay!!.token) })
        assertTrue(fixture.secrets.values.values.all { it.contains(".") })
    }

    @Test
    fun storageRejectsTamperedRelayTokenInsteadOfSilentDowngrade() {
        val fixture = Fixture()
        fixture.storage.save(relayConfig())
        fixture.secrets.values["relayToken"] = "not-a-valid-ciphertext"
        val error = assertThrows(IllegalStateException::class.java) { fixture.storage.load() }
        assertTrue(error.message.orEmpty().contains("中继配对密钥"))
    }

    @Test
    fun switchingToDirectClearsRelayModeAndSecret() {
        val fixture = Fixture()
        fixture.storage.save(relayConfig())
        fixture.storage.save(relayConfig().copy(relay = null))
        assertNull(fixture.storage.load()?.relay)
        assertFalse(fixture.config.values.containsKey("relayRoom"))
        assertFalse(fixture.secrets.values.containsKey("relayToken"))
    }

    @Test
    fun controllerUsesStableRelayIdentityForTrustedFingerprint() = runBlocking {
        val fixture = Fixture()
        val value = relayConfig()
        fixture.storage.trustFingerprint(value.hostIdentity()!!, "SHA256:relay")
        val transport = FakeTransport("SHA256:relay")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        scopes += scope
        val machine = RemoteHostMachine()
        val controller = RemoteHostController(
            storage = fixture.storage,
            transport = transport,
            scope = scope,
            machine = machine,
            httpProbe = { true }
        )
        try {
            val result = controller.attempt(value, keepTunnel = true)
            assertTrue(result is com.tanuki.runtime.RemoteAttemptResult.Success)
            assertEquals(0, transport.probeCalls)
            assertEquals(1, transport.openCalls)
            assertEquals("relay:${value.relay!!.hostId}:${value.user}:${value.remoteDshPort}", value.cookieScope())
            assertEquals(RemotePhase.REMOTE, machine.status.phase)
            assertEquals("relay-display", machine.status.host)
        } finally {
            controller.close()
        }
    }

    @Test
    fun relayRetrySucceedsAfterTransientFailures() = runBlocking {
        var attempts = 0
        val policy = RelayReconnectPolicy(maxAttempts = 4, baseDelayMillis = 100, maxDelayMillis = 100, jitterFraction = 0.0)
        val result = relayRetry(policy) {
            attempts++
            if (attempts < 3) throw RemoteRelayException(RelayFailureKind.NETWORK, "temporary")
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(3, attempts)
    }

    @Test
    fun relayRetryStopsAfterMaxAttempts() {
        var attempts = 0
        val policy = RelayReconnectPolicy(maxAttempts = 4, baseDelayMillis = 100, maxDelayMillis = 100, jitterFraction = 0.0)
        val error = assertThrows(RemoteRelayException::class.java) {
            runBlocking {
                relayRetry(policy) {
                    attempts++
                    throw RemoteRelayException(RelayFailureKind.NETWORK, "still down")
                }
            }
        }
        assertEquals(4, attempts)
        assertEquals(RelayFailureKind.NETWORK, error.kind)
    }

    @Test
    fun relayRetryDoesNotRetryAuthPinOrProtocolErrors() {
        val policy = RelayReconnectPolicy(maxAttempts = 4, baseDelayMillis = 100, maxDelayMillis = 100, jitterFraction = 0.0)
        listOf(RelayFailureKind.AUTH, RelayFailureKind.PIN_MISMATCH, RelayFailureKind.PROTOCOL).forEach { kind ->
            var attempts = 0
            val error = assertThrows(RemoteRelayException::class.java) {
                runBlocking {
                    relayRetry(policy) {
                        attempts++
                        throw RemoteRelayException(kind, "terminal")
                    }
                }
            }
            assertEquals(1, attempts)
            assertEquals(kind, error.kind)
        }
    }

    @Test
    fun relayRetryIsCancellableDuringBackoff() {
        assertThrows(CancellationException::class.java) {
            runBlocking {
                withTimeout(80) {
                    relayRetry(
                        RelayReconnectPolicy(maxAttempts = 4, baseDelayMillis = 500, maxDelayMillis = 500, jitterFraction = 0.0)
                    ) {
                        throw RemoteRelayException(RelayFailureKind.NETWORK, "temporary")
                    }
                }
            }
        }
    }

    @Test
    fun bridgeCloseDoesNotWaitForBlockedTlsWrite() {
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val fake = BlockingWriteSocket()
        val pipeIn = PipedInputStream()
        val pipeOut = PipedOutputStream(pipeIn)
        val bridge = RelayBridge(
            server,
            fake,
            1,
            DataInputStream(pipeIn),
            DataOutputStream(fake.getOutputStream()),
            Any(),
            AtomicBoolean(false)
        )
        bridge.start()
        try {
            Socket().use { local ->
                local.connect(InetSocketAddress("127.0.0.1", bridge.localPort), 3_000)
                local.getOutputStream().write(byteArrayOf(1, 2, 3))
                local.getOutputStream().flush()
                assertTrue("uplink should enter blocked TLS write", fake.awaitEntered())
                val started = System.nanoTime()
                bridge.close()
                val elapsedMs = (System.nanoTime() - started) / 1_000_000
                assertTrue("bridge.close must not wait for writerLock; took ${elapsedMs}ms", elapsedMs < 1_500)
                assertTrue(fake.closedByPeer)
            }
        } finally {
            bridge.close()
            pipeOut.close()
            runCatching { server.close() }
        }
    }

    @Test
    fun sessionCloseDoesNotAttemptBlockingCloseFrameWrite() {
        val fake = BlockingWriteSocket()
        val session = RelayClientSession(fake, 1, pairing())
        val started = System.nanoTime()
        session.close()
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertTrue("session.close must not write CLOSE synchronously; took ${elapsedMs}ms", elapsedMs < 1_000)
        assertTrue(fake.closedByPeer)
    }

    private fun relayConfig() = RemoteHostConfig(
        host = "relay-display",
        sshPort = 22,
        user = "tanuki",
        auth = RemoteAuthType.PASSWORD,
        password = "synthetic-password",
        remoteDshPort = 3080,
        token = "synthetic-dsh-token",
        relay = pairing()
    )

    private class Fixture {
        val config = MapStore()
        val known = MapStore()
        val secrets = MapStore()
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val storage = RemoteHostStorage(config, known, AesGcmSecretVault(key, secrets))
    }

    private class MapStore : KeyValueStore {
        val values = LinkedHashMap<String, String>()
        override fun getString(key: String): String? = values[key]
        override fun putString(key: String, value: String) {
            values[key] = value
        }
        override fun remove(key: String) {
            values.remove(key)
        }
        override fun clear() {
            values.clear()
        }
    }

    private class FakeTunnel : SshTunnel {
        override val localPort: Int = 34567
        override fun isAlive(): Boolean = true
        override fun close() = Unit
    }

    private class FakeTransport(private val fingerprint: String) : SshTransport {
        var probeCalls = 0
        var openCalls = 0
        override suspend fun probeHostKey(config: RemoteHostConfig): HostKeyProbe {
            probeCalls++
            return HostKeyProbe(fingerprint)
        }
        override suspend fun open(config: RemoteHostConfig, expectedFingerprint: String): SshTunnel {
            openCalls++
            assertEquals(fingerprint, expectedFingerprint)
            return FakeTunnel()
        }
    }

    private class BlockingWriteSocket : Socket() {
        private val entered = CountDownLatch(1)
        private val released = CountDownLatch(1)
        private val emptyInput = java.io.ByteArrayInputStream(ByteArray(0))
        @Volatile var closedByPeer = false
        private val blockingOutput = object : OutputStream() {
            override fun write(value: Int) = write(byteArrayOf(value.toByte()))
            override fun write(buffer: ByteArray, offset: Int, length: Int) {
                entered.countDown()
                if (!released.await(2, TimeUnit.SECONDS)) throw IOException("blocked write")
                if (closedByPeer) throw IOException("closed")
            }
        }

        override fun getInputStream(): InputStream = emptyInput

        override fun getOutputStream(): OutputStream = blockingOutput

        override fun close() {
            closedByPeer = true
            released.countDown()
            super.close()
        }

        override fun isConnected(): Boolean = true

        override fun isClosed(): Boolean = closedByPeer

        override fun setTcpNoDelay(on: Boolean) = Unit

        fun awaitEntered(): Boolean = entered.await(2, TimeUnit.SECONDS)
    }

    private class EchoRelayServer(
        private val expectedToken: String,
        private val streamId: Int = 7,
        private val banner: ByteArray? = null
    ) : AutoCloseable {
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val connector = RelaySocketConnector { _, _ -> Socket("127.0.0.1", server.localPort) }
        private val thread = Thread({ serve() }, "fake-relay").apply {
            isDaemon = true
            start()
        }

        private fun serve() {
            try {
                server.accept().use { client ->
                    client.soTimeout = 3_000
                    val input = DataInputStream(client.getInputStream())
                    val output = DataOutputStream(client.getOutputStream())
                    val hello = parseRelayControl(RelayFrameCodec.read(input) ?: return) ?: return
                    if (hello.size != 6 || hello[0] != "HELLO" || hello[1] != "1" || hello[2] != "client") return
                    val room = hello[3]
                    val clientNonce = hello[5]
                    val serverNonce = RelayPairingMaterial.nonce()
                    RelayFrameCodec.write(output, RelayFrameCodec.control("CHALLENGE $serverNonce"))
                    val auth = parseRelayControl(RelayFrameCodec.read(input) ?: return) ?: return
                    val expected = RelayProof.clientProof(expectedToken, room, clientNonce, serverNonce)
                    if (auth.size != 2 || auth[0] != "AUTH" || !RelayProof.verify(expected, auth[1])) {
                        RelayFrameCodec.write(output, RelayFrameCodec.control("ERROR bad_token"))
                        return
                    }
                    RelayFrameCodec.write(output, RelayFrameCodec.control("QUEUED"))
                    RelayFrameCodec.write(output, RelayFrame(RelayFrameType.OPEN_OK, streamId, ByteArray(0)))
                    banner?.let {
                        RelayFrameCodec.write(output, RelayFrame(RelayFrameType.DATA, streamId, it))
                    }
                    while (true) {
                        val frame = RelayFrameCodec.read(input) ?: break
                        when (frame.type) {
                            RelayFrameType.DATA -> RelayFrameCodec.write(
                                output,
                                RelayFrame(RelayFrameType.DATA, streamId, frame.payload)
                            )
                            RelayFrameType.CLOSE -> break
                            RelayFrameType.PONG -> Unit
                            else -> break
                        }
                    }
                }
            } catch (_: IOException) {
                // 测试夹具主动关闭时忽略。
            } finally {
                runCatching { server.close() }
            }
        }

        override fun close() {
            runCatching { server.close() }
            runCatching { thread.join(1_000) }
        }
    }

    private fun readFully(input: java.io.InputStream, size: Int): ByteArray {
        val result = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val count = input.read(result, offset, size - offset)
            if (count == -1) throw EOFException("unexpected EOF")
            offset += count
        }
        return result
    }
}
