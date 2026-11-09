package com.tanuki

import com.tanuki.runtime.AesGcmSecretVault
import com.tanuki.runtime.HostKeyFingerprint
import com.tanuki.runtime.HostKeyProbe
import com.tanuki.runtime.KeyValueStore
import com.tanuki.runtime.RemoteAttemptResult
import com.tanuki.runtime.RemoteAuthType
import com.tanuki.runtime.RemoteEndpoint
import com.tanuki.runtime.RemoteHostConfig
import com.tanuki.runtime.RemoteHostController
import com.tanuki.runtime.RemoteHostFailure
import com.tanuki.runtime.RemoteHostKeyChangedException
import com.tanuki.runtime.RemoteHostMachine
import com.tanuki.runtime.RemoteHostStorage
import com.tanuki.runtime.RemotePhase
import com.tanuki.runtime.SshTunnel
import com.tanuki.runtime.SshTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import javax.crypto.KeyGenerator as JvmKeyGenerator

class RemoteHostTest {
    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
    }

    private fun config(
        host: String = "dev.example.test",
        sshPort: Int = 22,
        user: String = "tanuki",
        auth: RemoteAuthType = RemoteAuthType.PRIVATE_KEY,
        password: String = "",
        privateKey: String = SYNTHETIC_KEY,
        passphrase: String = "",
        remoteDshPort: Int = 3080,
        token: String = "synthetic-token"
    ) = RemoteHostConfig(host, sshPort, user, auth, password, privateKey, passphrase, remoteDshPort, token)

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

    private class Fixture {
        val config = MapStore()
        val known = MapStore()
        val secrets = MapStore()
        val key = JvmKeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val storage = RemoteHostStorage(config, known, AesGcmSecretVault(key, secrets))
    }

    private class FakeTunnel(override val localPort: Int = 23456, private val alive: Boolean = true) : SshTunnel {
        var closed = false
        override fun isAlive(): Boolean = alive && !closed
        override fun close() {
            closed = true
        }
    }

    private class FakeTransport(
        private val fingerprint: String = "SHA256:confirmed",
        private val openError: Throwable? = null,
        private val tunnel: FakeTunnel = FakeTunnel()
    ) : SshTransport {
        var probeCalls = 0
        var openCalls = 0
        override suspend fun probeHostKey(config: RemoteHostConfig): HostKeyProbe {
            probeCalls++
            return HostKeyProbe(fingerprint)
        }
        override suspend fun open(config: RemoteHostConfig, expectedFingerprint: String): SshTunnel {
            openCalls++
            openError?.let { throw it }
            assertEquals(fingerprint, expectedFingerprint)
            return tunnel
        }
    }

    private fun controller(
        fixture: Fixture,
        transport: FakeTransport,
        machine: RemoteHostMachine = RemoteHostMachine(),
        probeResult: Boolean = true
    ): Pair<RemoteHostController, CoroutineScope> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        scopes += scope
        val controller = RemoteHostController(
            storage = fixture.storage,
            transport = transport,
            scope = scope,
            machine = machine,
            httpProbe = { probeResult }
        )
        return controller to scope
    }

    @Test
    fun passwordAndKeyConfigurationsPassValidation() {
        assertNull(config(auth = RemoteAuthType.PASSWORD, password = "synthetic-password").validationError())
        assertNull(config().validationError())
        assertNull(config(host = "[2001:db8::1]").validationError())
    }

    @Test
    fun hostRejectsSchemePathPortAndWhitespace() {
        listOf(
            "http://dev.example.test",
            "dev.example.test/path",
            "dev.example.test:2222",
            "dev example.test",
            "dev.example.test#user",
            "",
            "dev..example.test"
        ).forEach { bad ->
            assertNotNull("host should be rejected: $bad", config(host = bad).validationError())
        }
    }

    @Test
    fun portsUserAndCredentialsAreValidated() {
        assertNotNull(config(sshPort = 0).validationError())
        assertNotNull(config(sshPort = 65536).validationError())
        assertNotNull(config(remoteDshPort = 0).validationError())
        assertNotNull(config(user = "bad user").validationError())
        assertNotNull(config(auth = RemoteAuthType.PASSWORD, password = "").validationError())
        assertNotNull(config(privateKey = "").validationError())
        assertNotNull(config(privateKey = "not a key").validationError())
        assertNotNull(
            config(privateKey = "-----BEGIN " + "PRIVATE KEY-----\n" + "a".repeat(70 * 1024)).validationError()
        )
        assertNotNull(config(token = "has space").validationError())
        assertNotNull(config(token = "line\nbreak").validationError())
    }

    @Test
    fun ipv6HostIsNormalizedWithoutBrackets() {
        assertEquals("2001:db8::1", config(host = "[2001:DB8::1]").normalizedHost())
        assertEquals("dev.example.test", config(host = "Dev.Example.Test").normalizedHost())
    }

    @Test
    fun tokenUrlStaysOnEphemeralLoopbackPortAndIsEncoded() {
        val endpoint = RemoteEndpoint.loopback(34567, "a&b=c d")
        assertNotNull(endpoint)
        assertEquals("http://127.0.0.1:34567", endpoint!!.origin)
        assertTrue(endpoint.authenticatedUrl.startsWith("http://127.0.0.1:34567/?token="))
        assertFalse(endpoint.authenticatedUrl.contains(" "))
        assertNull(RemoteEndpoint.loopback(0, "token"))
        assertNull(RemoteEndpoint.loopback(70000, "token"))
    }

    @Test
    fun secretsNeverAppearInToStringOrEndpoint() {
        val value = config(
            auth = RemoteAuthType.PRIVATE_KEY,
            privateKey = SYNTHETIC_KEY,
            passphrase = "very-secret-passphrase",
            token = "very-secret-token"
        )
        val text = value.toString()
        assertFalse(text.contains("very-secret"))
        assertFalse(text.contains("BEGIN"))
        assertFalse(text.contains("very-secret-token"))
        val endpoint = value.endpointFor(1234)!!
        assertFalse(endpoint.toString().contains("very-secret-token"))
    }

    @Test
    fun hostKeyFingerprintUsesOpenSshSha256Format() {
        assertEquals(
            "SHA256:91QB36lpOaKoQLfYR3foL0H7nAR+15ClVF1dJMy5Ano",
            HostKeyFingerprint.of("synthetic-host-key".toByteArray())
        )
    }

    @Test
    fun machineSwitchesToRemoteAndFallsBackToLocal() {
        val machine = RemoteHostMachine()
        machine.localConnected("http://127.0.0.1:41000", "http://127.0.0.1:41000/?token=local")
        assertEquals("http://127.0.0.1:41000", machine.origin)
        assertEquals(RemotePhase.LOCAL, machine.status.phase)

        machine.remoteConnecting("dev.example.test")
        assertEquals(RemotePhase.CONNECTING, machine.status.phase)
        machine.remoteConnected(
            RemoteEndpoint.loopback(42000, "remote-token")!!,
            "dev.example.test",
            42000,
            "SHA256:confirmed"
        )
        assertEquals("http://127.0.0.1:42000", machine.origin)
        assertEquals("http://127.0.0.1:42000/?token=remote-token", machine.authenticatedUrl)
        assertEquals(RemotePhase.REMOTE, machine.status.phase)

        machine.remoteFailed("SSH 隧道已断开")
        assertEquals("http://127.0.0.1:41000", machine.origin)
        assertEquals(RemotePhase.FALLBACK, machine.status.phase)
        assertTrue(machine.status.message.contains("回退"))
        assertTrue(machine.status.error.contains("隧道"))
    }

    @Test
    fun machineKeepsRemoteOriginWhenLocalStops() {
        val machine = RemoteHostMachine()
        machine.localConnected("http://127.0.0.1:41000", "http://127.0.0.1:41000/?token=local")
        machine.remoteConnected(RemoteEndpoint.loopback(42000, "remote")!!, "dev.example.test", 42000, "SHA256:x")
        machine.localCleared()
        assertEquals("http://127.0.0.1:42000", machine.origin)
        assertEquals("http://127.0.0.1:42000/?token=remote", machine.authenticatedUrl)
        assertNull(machine.localOrigin)
    }

    @Test
    fun machineFailureWithoutLocalLeavesNoOrigin() {
        val machine = RemoteHostMachine()
        machine.remoteConnecting("dev.example.test")
        machine.remoteFailed("认证失败")
        assertNull(machine.origin)
        assertNull(machine.authenticatedUrl)
        assertEquals(RemotePhase.FALLBACK, machine.status.phase)
    }

    @Test
    fun storageRoundTripsAndNeverStoresSecretsInPlaintext() {
        val fixture = Fixture()
        val value = config(
            auth = RemoteAuthType.PRIVATE_KEY,
            privateKey = SYNTHETIC_KEY,
            passphrase = "very-secret-passphrase",
            token = "very-secret-token"
        )
        fixture.storage.save(value)
        assertEquals(value.host, fixture.storage.load()?.host)
        assertEquals(value.user, fixture.storage.load()?.user)
        assertEquals(value.token, fixture.storage.load()?.token)
        assertEquals(value.privateKey, fixture.storage.load()?.privateKey)
        assertEquals(value.passphrase, fixture.storage.load()?.passphrase)
        assertTrue(fixture.config.values.values.none { it.contains("very-secret") || it.contains("PRIVATE KEY") })
        assertTrue(fixture.secrets.values.values.none {
            it.contains("very-secret") || it.contains("PRIVATE KEY") || it.contains("synthetic-token")
        })
        assertTrue(fixture.secrets.values.values.all { it.contains(".") })
    }

    @Test
    fun tamperedCiphertextDoesNotFallBackToPlaintext() {
        val fixture = Fixture()
        fixture.storage.save(config(auth = RemoteAuthType.PASSWORD, password = "synthetic-password"))
        fixture.secrets.values["password"] = "not-a-valid-ciphertext"
        val loaded = fixture.storage.load()
        assertNotNull(loaded)
        assertTrue(loaded!!.password.isEmpty())
        assertNotNull(loaded.validationError())
    }

    @Test
    fun clearRemovesConfigSecretsAndKnownHost() {
        val fixture = Fixture()
        fixture.storage.save(config())
        fixture.storage.trustFingerprint("dev.example.test", 22, "SHA256:trusted")
        assertEquals("SHA256:trusted", fixture.storage.knownFingerprint("dev.example.test", 22))
        fixture.storage.clear()
        assertNull(fixture.storage.load())
        assertNull(fixture.storage.knownFingerprint("dev.example.test", 22))
        assertTrue(fixture.config.values.isEmpty())
        assertTrue(fixture.secrets.values.isEmpty())
    }

    @Test
    fun firstConnectRequiresExplicitHostKeyConfirmation() = runBlocking {
        val fixture = Fixture()
        val transport = FakeTransport(fingerprint = "SHA256:first-use")
        val (controller, _) = controller(fixture, transport)
        val result = controller.attempt(config(), keepTunnel = true)
        assertTrue(result is RemoteAttemptResult.HostKeyConfirmation)
        assertEquals("SHA256:first-use", (result as RemoteAttemptResult.HostKeyConfirmation).fingerprint)
        assertEquals(0, transport.openCalls)
        assertEquals("SHA256:first-use", controller.ui.value.pendingFingerprint)
        assertNull(fixture.storage.knownFingerprint("dev.example.test", 22))
    }

    @Test
    fun confirmedConnectionSwitchesOriginAndKeepsTokenOnLoopback() = runBlocking {
        val fixture = Fixture()
        fixture.storage.save(config(token = "remote-secret"))
        fixture.storage.trustFingerprint("dev.example.test", 22, "SHA256:confirmed")
        val transport = FakeTransport(tunnel = FakeTunnel(localPort = 45678))
        val machine = RemoteHostMachine()
        machine.localConnected("http://127.0.0.1:41000", "http://127.0.0.1:41000/?token=local")
        val (controller, _) = controller(fixture, transport, machine)
        val result = controller.attempt(config(token = "remote-secret"), keepTunnel = true)
        assertTrue(result is RemoteAttemptResult.Success)
        assertEquals(1, transport.openCalls)
        assertEquals("http://127.0.0.1:45678", machine.origin)
        assertEquals("http://127.0.0.1:45678/?token=remote-secret", machine.authenticatedUrl)
        assertEquals(RemotePhase.REMOTE, machine.status.phase)
        assertEquals("http://127.0.0.1:41000", machine.localOrigin)
        assertEquals("remote-secret", fixture.storage.load()?.token)
        assertTrue(controller.ui.value.saved != null)
    }

    @Test
    fun explicitHostKeyConfirmationConnectsAndTrustsOnlyThen() = runBlocking {
        val fixture = Fixture()
        val transport = FakeTransport(fingerprint = "SHA256:first-use", tunnel = FakeTunnel(localPort = 45678))
        val machine = RemoteHostMachine()
        machine.localConnected("http://127.0.0.1:41000", "http://127.0.0.1:41000/?token=local")
        val (controller, _) = controller(fixture, transport, machine)
        assertTrue(controller.attempt(config(), keepTunnel = true) is RemoteAttemptResult.HostKeyConfirmation)
        assertNull(fixture.storage.knownFingerprint("dev.example.test", 22))
        val confirmed = controller.confirmHostKeyNow()
        assertTrue(confirmed is RemoteAttemptResult.Success)
        assertEquals("SHA256:first-use", fixture.storage.knownFingerprint("dev.example.test", 22))
        assertEquals("http://127.0.0.1:45678", machine.origin)
        assertEquals(RemotePhase.REMOTE, machine.status.phase)
    }

    @Test
    fun invalidConfigDoesNotDropActiveRemoteTunnel() = runBlocking {
        val fixture = Fixture()
        fixture.storage.trustFingerprint("dev.example.test", 22, "SHA256:confirmed")
        val tunnel = FakeTunnel(localPort = 45678)
        val transport = FakeTransport(tunnel = tunnel)
        val machine = RemoteHostMachine()
        machine.localConnected("http://127.0.0.1:41000", "http://127.0.0.1:41000/?token=local")
        val (controller, _) = controller(fixture, transport, machine)
        assertTrue(controller.attempt(config(), keepTunnel = true) is RemoteAttemptResult.Success)
        val result = controller.attempt(config(host = "bad host"), keepTunnel = true)
        assertTrue(result is RemoteAttemptResult.Failure)
        assertEquals(RemotePhase.REMOTE, machine.status.phase)
        assertEquals("http://127.0.0.1:45678", machine.origin)
        assertFalse(tunnel.closed)
    }

    @Test
    fun connectionFailureFallsBackToLocalWithChineseErrorAndNoLeak() = runBlocking {
        val fixture = Fixture()
        fixture.storage.trustFingerprint("dev.example.test", 22, "SHA256:confirmed")
        val transport = FakeTransport(openError = IOException("password=very-secret-value"))
        val machine = RemoteHostMachine()
        machine.localConnected("http://127.0.0.1:41000", "http://127.0.0.1:41000/?token=local")
        val (controller, _) = controller(fixture, transport, machine)
        val result = controller.attempt(config(password = "very-secret-value"), keepTunnel = true)
        assertTrue(result is RemoteAttemptResult.Failure)
        val message = (result as RemoteAttemptResult.Failure).message
        assertTrue(message.contains("SSH") || message.contains("连接"))
        assertFalse(message.contains("very-secret-value"))
        assertEquals("http://127.0.0.1:41000", machine.origin)
        assertEquals(RemotePhase.FALLBACK, machine.status.phase)
        assertTrue(machine.status.error.isNotBlank())
        assertFalse(controller.ui.value.error.contains("very-secret-value"))
    }

    @Test
    fun remoteDshProbeFailureClosesTunnelAndFallsBack() = runBlocking {
        val fixture = Fixture()
        fixture.storage.trustFingerprint("dev.example.test", 22, "SHA256:confirmed")
        val tunnel = FakeTunnel(localPort = 45678)
        val transport = FakeTransport(tunnel = tunnel)
        val machine = RemoteHostMachine()
        machine.localConnected("http://127.0.0.1:41000", "http://127.0.0.1:41000/?token=local")
        val (controller, _) = controller(fixture, transport, machine, probeResult = false)
        val result = controller.attempt(config(), keepTunnel = true)
        assertTrue(result is RemoteAttemptResult.Failure)
        assertTrue(tunnel.closed)
        assertEquals("http://127.0.0.1:41000", machine.origin)
        assertEquals(RemotePhase.FALLBACK, machine.status.phase)
        assertTrue((result as RemoteAttemptResult.Failure).message.contains("DSH"))
    }

    @Test
    fun changedHostKeyIsRejectedWithoutTrustingNewKey() = runBlocking {
        val fixture = Fixture()
        fixture.storage.trustFingerprint("dev.example.test", 22, "SHA256:old")
        val transport = FakeTransport(openError = RemoteHostKeyChangedException("SHA256:new"))
        val machine = RemoteHostMachine()
        machine.localConnected("http://127.0.0.1:41000", "http://127.0.0.1:41000/?token=local")
        val (controller, _) = controller(fixture, transport, machine)
        val result = controller.attempt(config(), keepTunnel = true)
        assertTrue(result is RemoteAttemptResult.Failure)
        assertTrue((result as RemoteAttemptResult.Failure).message.contains("密钥已变化"))
        assertEquals("SHA256:old", fixture.storage.knownFingerprint("dev.example.test", 22))
        assertEquals("http://127.0.0.1:41000", machine.origin)
    }

    @Test
    fun testConnectionDoesNotSwitchOriginOrPersistConfig() = runBlocking {
        val fixture = Fixture()
        fixture.storage.trustFingerprint("dev.example.test", 22, "SHA256:confirmed")
        val tunnel = FakeTunnel(localPort = 45678)
        val transport = FakeTransport(tunnel = tunnel)
        val machine = RemoteHostMachine()
        machine.localConnected("http://127.0.0.1:41000", "http://127.0.0.1:41000/?token=local")
        val (controller, _) = controller(fixture, transport, machine)
        val result = controller.attempt(config(), keepTunnel = false)
        assertTrue(result is RemoteAttemptResult.Success)
        assertTrue(tunnel.closed)
        assertEquals("http://127.0.0.1:41000", machine.origin)
        assertNull(fixture.storage.load())
        assertEquals(RemotePhase.LOCAL, machine.status.phase)
    }

    @Test
    fun disconnectRestoresLocalAndForgetClearsTrust() = runBlocking {
        val fixture = Fixture()
        fixture.storage.trustFingerprint("dev.example.test", 22, "SHA256:confirmed")
        val transport = FakeTransport(tunnel = FakeTunnel(localPort = 45678))
        val machine = RemoteHostMachine()
        machine.localConnected("http://127.0.0.1:41000", "http://127.0.0.1:41000/?token=local")
        val (controller, _) = controller(fixture, transport, machine)
        controller.attempt(config(), keepTunnel = true)
        assertEquals(RemotePhase.REMOTE, machine.status.phase)
        controller.disconnectNow()
        assertEquals("http://127.0.0.1:41000", machine.origin)
        assertEquals(RemotePhase.LOCAL, machine.status.phase)
        controller.forgetNow()
        assertNull(fixture.storage.load())
        assertNull(fixture.storage.knownFingerprint("dev.example.test", 22))
    }

    @Test
    fun httpProbeAcceptsDshResponsesAndRejectsServerErrors() {
        assertTrue(probeAgainst(200))
        assertTrue(probeAgainst(302))
        assertTrue(probeAgainst(401))
        assertFalse(probeAgainst(500))
        val closed = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val port = closed.localPort
        closed.close()
        assertFalse(com.tanuki.runtime.RemoteHttpProbe.probe(port, 500))
    }

    /** 不依赖 com.sun.net.httpserver（Android 单元测试 bootclasspath 不提供）。 */
    private fun probeAgainst(status: Int): Boolean {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val worker = Thread {
            runCatching {
                server.accept().use { socket ->
                    socket.soTimeout = 2_000
                    val reader = socket.getInputStream().bufferedReader()
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                    }
                    val body = ""
                    val response = "HTTP/1.1 $status Test\r\nContent-Length: 0\r\nConnection: close\r\n\r\n$body"
                    socket.getOutputStream().write(response.toByteArray(Charsets.UTF_8))
                    socket.getOutputStream().flush()
                }
            }
        }.apply { isDaemon = true; start() }
        return try {
            com.tanuki.runtime.RemoteHttpProbe.probe(server.localPort, 2_000)
        } finally {
            runCatching { server.close() }
            worker.join(2_000)
        }
    }

    @Test
    fun probeFailureMessageNamesRemoteDshWithoutSecrets() {
        val failure = RemoteHostFailure("远程 DSH 端口未响应")
        assertTrue(failure.message!!.contains("DSH"))
    }

    companion object {
        private const val SYNTHETIC_KEY =
            "-----BEGIN OPENSSH " + "PRIVATE KEY-----\nsynthetic\n-----END OPENSSH " + "PRIVATE KEY-----"
    }
}
