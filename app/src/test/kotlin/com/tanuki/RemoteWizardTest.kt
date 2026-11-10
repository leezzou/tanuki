package com.tanuki

import com.tanuki.runtime.AesGcmSecretVault
import com.tanuki.runtime.HostKeyProbe
import com.tanuki.runtime.KeyValueStore
import com.tanuki.runtime.RemoteAttemptResult
import com.tanuki.runtime.RemoteAuthType
import com.tanuki.runtime.RemoteConnectionErrors
import com.tanuki.runtime.RemoteConnectionMethod
import com.tanuki.runtime.RemoteConnectionStage
import com.tanuki.runtime.RemoteFailureKind
import com.tanuki.runtime.RemoteHostConfig
import com.tanuki.runtime.RemoteHostController
import com.tanuki.runtime.RemoteHostKeyChangedException
import com.tanuki.runtime.RemoteHostMachine
import com.tanuki.runtime.RemoteHostProfileStore
import com.tanuki.runtime.RemoteHostStorage
import com.tanuki.runtime.RemoteHostWizard
import com.tanuki.runtime.RemotePhase
import com.tanuki.runtime.RemoteRelayPairing
import com.tanuki.runtime.RemoteStageStatus
import com.tanuki.runtime.RemoteWizardField
import com.tanuki.runtime.RemoteWizardStep
import com.tanuki.runtime.RemoteWizardValidation
import com.tanuki.runtime.SshTunnel
import com.tanuki.runtime.StagedSshTransport
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
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.crypto.KeyGenerator

class RemoteWizardTest {
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
        token: String = ""
    ) = RemoteHostConfig(host, sshPort, user, auth, password, privateKey, passphrase, remoteDshPort, token)

    // ---------- 向导状态机 ----------

    @Test
    fun methodIsRequiredBeforeHostStep() {
        val wizard = RemoteHostWizard()
        assertEquals(RemoteWizardStep.METHOD, wizard.state.value.step)
        assertFalse(wizard.next())
        assertNotNull(wizard.state.value.fieldErrors[RemoteWizardField.METHOD])

        wizard.selectMethod(RemoteConnectionMethod.DIRECT_SSH)
        assertTrue(wizard.next())
        assertEquals(RemoteWizardStep.HOST_ACCOUNT, wizard.state.value.step)
    }

    @Test
    fun wizardAdvancesThroughAllStepsAndBuildsConfig() {
        val wizard = RemoteHostWizard()
        wizard.selectMethod(RemoteConnectionMethod.LAN)
        assertTrue(wizard.next())
        wizard.updateDraft {
            it.copy(
                host = "192.168.1.20",
                sshPort = "2222",
                user = "tanuki",
                remoteDshPort = "3080",
                token = "synthetic-token"
            )
        }
        assertTrue(wizard.next())
        assertEquals(RemoteWizardStep.AUTHENTICATION, wizard.state.value.step)
        wizard.setAuth(RemoteAuthType.PASSWORD)
        wizard.updateDraft { it.copy(password = "synthetic-password") }
        assertTrue(wizard.next())
        assertEquals(RemoteWizardStep.TEST, wizard.state.value.step)
        assertTrue(wizard.state.value.busy)

        val built = wizard.buildConfig()!!
        assertEquals("192.168.1.20", built.host)
        assertEquals(2222, built.sshPort)
        assertEquals(RemoteAuthType.PASSWORD, built.auth)
        assertEquals("synthetic-password", built.password)
        assertEquals("synthetic-token", built.token)

        wizard.onHostKeyRequired("SHA256:test")
        assertEquals(RemoteWizardStep.FINGERPRINT, wizard.state.value.step)
        assertFalse(wizard.state.value.canConfirmFingerprint)
        assertFalse(wizard.confirmFingerprint())

        wizard.acknowledgeHostKey(true)
        assertTrue(wizard.state.value.canConfirmFingerprint)
        assertTrue(wizard.confirmFingerprint())
        assertEquals(RemoteWizardStep.TEST, wizard.state.value.step)
        assertTrue(wizard.state.value.busy)

        wizard.onStage(RemoteConnectionStage.TCP)
        wizard.onStage(RemoteConnectionStage.SSH_HANDSHAKE)
        wizard.onStage(RemoteConnectionStage.AUTHENTICATION)
        wizard.onStage(RemoteConnectionStage.PORT_FORWARD)
        wizard.onStage(RemoteConnectionStage.DSH_PROBE)
        wizard.onStage(RemoteConnectionStage.COMPLETE)
        wizard.onTestSucceeded(45678)

        assertEquals(RemoteWizardStep.DONE, wizard.state.value.step)
        assertTrue(wizard.state.value.tested)
        assertEquals(45678, wizard.state.value.localPort)
        assertEquals(RemoteConnectionStage.COMPLETE, wizard.state.value.stage)
    }

    @Test
    fun fingerprintCannotBeTrustedWithoutExplicitAcknowledgement() {
        val wizard = readyToTestWizard()
        wizard.onHostKeyRequired("SHA256:first-use")
        assertFalse(wizard.confirmFingerprint())
        assertEquals(RemoteWizardStep.FINGERPRINT, wizard.state.value.step)
        wizard.acknowledgeHostKey(true)
        assertTrue(wizard.confirmFingerprint())
        assertTrue(wizard.state.value.busy)
    }

    @Test
    fun backKeepsDraftAndDoneCannotGoBack() {
        val wizard = RemoteHostWizard()
        wizard.selectMethod(RemoteConnectionMethod.DIRECT_SSH)
        wizard.next()
        wizard.updateDraft { it.copy(host = "dev.example.test", user = "tanuki") }
        wizard.next()
        assertEquals(RemoteWizardStep.AUTHENTICATION, wizard.state.value.step)
        assertTrue(wizard.back())
        assertEquals(RemoteWizardStep.HOST_ACCOUNT, wizard.state.value.step)
        assertEquals("dev.example.test", wizard.state.value.draft.host)
        assertEquals("tanuki", wizard.state.value.draft.user)

        wizard.updateDraft { it.copy(auth = RemoteAuthType.PASSWORD, password = "synthetic-password") }
        assertTrue(wizard.next())
        assertEquals(RemoteWizardStep.AUTHENTICATION, wizard.state.value.step)
        assertTrue(wizard.next())
        assertEquals(RemoteWizardStep.TEST, wizard.state.value.step)
        wizard.onTestSucceeded(1234)
        assertEquals(RemoteWizardStep.DONE, wizard.state.value.step)
        assertFalse(wizard.back())
    }

    @Test
    fun editingStartsAtHostStepAndKeepsName() {
        val wizard = RemoteHostWizard(
            initialConfig = config(user = "saved", auth = RemoteAuthType.PASSWORD, password = "synthetic-password"),
            initialName = "家里电脑",
            initialProfileId = "profile-1"
        )
        assertEquals(RemoteWizardStep.HOST_ACCOUNT, wizard.state.value.step)
        assertEquals(RemoteConnectionMethod.DIRECT_SSH, wizard.state.value.method)
        assertEquals("家里电脑", wizard.state.value.name)
        assertTrue(wizard.state.value.isEditing)
        assertNotNull(wizard.buildConfig())
    }

    @Test
    fun failureStopsAtStageAndRetryResetsProgress() {
        val wizard = readyToTestWizard()
        wizard.onStage(RemoteConnectionStage.TCP)
        wizard.onStage(RemoteConnectionStage.SSH_HANDSHAKE)
        wizard.onStage(RemoteConnectionStage.AUTHENTICATION)
        val info = RemoteConnectionErrors.from(IOException("Auth fail"), RemoteConnectionStage.AUTHENTICATION)
        wizard.onFailure(info)

        assertEquals(RemoteWizardStep.TEST, wizard.state.value.step)
        assertFalse(wizard.state.value.busy)
        assertEquals(RemoteConnectionStage.AUTHENTICATION, wizard.state.value.stage)
        assertEquals(RemoteStageStatus.FAILED, wizard.stageStatus(RemoteConnectionStage.AUTHENTICATION))
        assertEquals(RemoteStageStatus.DONE, wizard.stageStatus(RemoteConnectionStage.SSH_HANDSHAKE))

        assertTrue(wizard.retry())
        assertEquals(RemoteConnectionStage.TCP, wizard.state.value.stage)
        assertTrue(wizard.state.value.completedStages.isEmpty())
        assertNull(wizard.state.value.failure)
        assertTrue(wizard.state.value.busy)
    }

    @Test
    fun hostKeyChangeFailureIsNotRetryable() {
        val wizard = readyToTestWizard()
        val info = RemoteConnectionErrors.from(
            RemoteHostKeyChangedException("SHA256:new"),
            RemoteConnectionStage.SSH_HANDSHAKE
        )
        wizard.onFailure(info)
        assertFalse(info.retryable)
        assertFalse(wizard.retry())
        assertEquals(RemoteFailureKind.HOST_KEY, wizard.state.value.failure?.kind)
    }

    @Test
    fun stageStatusOnlyMovesForward() {
        val wizard = readyToTestWizard()
        wizard.onStage(RemoteConnectionStage.PORT_FORWARD)
        wizard.onStage(RemoteConnectionStage.TCP)
        assertEquals(RemoteConnectionStage.PORT_FORWARD, wizard.state.value.stage)
        assertEquals(RemoteStageStatus.DONE, wizard.stageStatus(RemoteConnectionStage.AUTHENTICATION))
        assertEquals(RemoteStageStatus.ACTIVE, wizard.stageStatus(RemoteConnectionStage.PORT_FORWARD))
    }

    // ---------- 字段校验 ----------

    @Test
    fun hostValidationRejectsSchemesPathsPortsAndEmptyValues() {
        listOf("", "http://dev.example.test", "ssh://dev.example.test", "dev.example.test/path", "dev example.test").forEach {
            assertNotNull("应拒绝：$it", RemoteWizardValidation.host(it))
        }
        assertNotNull(RemoteWizardValidation.host("dev.example.test:2222"))
        assertNull(RemoteWizardValidation.host("192.168.1.20"))
        assertNull(RemoteWizardValidation.host("dev.example.test"))
        assertNull(RemoteWizardValidation.host("[2001:db8::1]"))
    }

    @Test
    fun portAndUserValidationExplainsRangeAndExamples() {
        assertTrue(RemoteWizardValidation.port("", "SSH 端口", "22")!!.contains("示例"))
        assertTrue(RemoteWizardValidation.port("abc", "SSH 端口", "22")!!.contains("数字"))
        assertTrue(RemoteWizardValidation.port("0", "SSH 端口", "22")!!.contains("1-65535"))
        assertTrue(RemoteWizardValidation.port("65536", "SSH 端口", "22")!!.contains("1-65535"))
        assertNull(RemoteWizardValidation.port("65535", "SSH 端口", "22"))
        assertTrue(RemoteWizardValidation.user("")!!.contains("用户名"))
        assertNotNull(RemoteWizardValidation.user("bad user"))
        assertNull(RemoteWizardValidation.user("tanuki"))
    }

    @Test
    fun authenticationAndTokenValidationDoNotLeakValues() {
        assertNotNull(RemoteWizardValidation.password(""))
        assertNull(RemoteWizardValidation.password("synthetic-password"))
        assertNotNull(RemoteWizardValidation.privateKey("", ""))
        assertNotNull(RemoteWizardValidation.privateKey("not a key", "id_rsa"))
        assertNull(RemoteWizardValidation.privateKey(SYNTHETIC_KEY, "id_rsa"))
        assertTrue(RemoteWizardValidation.privateKey("x".repeat(70 * 1024), "big").orEmpty().contains("64 KiB"))
        assertNotNull(RemoteWizardValidation.token("has space"))
        assertNull(RemoteWizardValidation.token("synthetic-token"))
    }

    @Test
    fun relayLinkValidationAcceptsGeneratedLinkAndRejectsMalformed() {
        val pairing = RemoteRelayPairing(
            relayHost = "relay.example.test",
            relayPort = 8443,
            roomId = "ICEiIyQlJicoKSorLC0uLw",
            token = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8",
            hostId = "303132333435363738393a3b3c3d3e3f"
        )
        assertNull(RemoteWizardValidation.relayLink(pairing.toUri()))
        assertNotNull(RemoteWizardValidation.relayLink(""))
        assertNotNull(RemoteWizardValidation.relayLink("https://example.test"))
    }

    @Test
    fun profileNameValidationRequiresReadableName() {
        assertNotNull(RemoteWizardValidation.name(""))
        assertNotNull(RemoteWizardValidation.name("x".repeat(65)))
        assertNull(RemoteWizardValidation.name("家里电脑"))
    }

    @Test
    fun stepValidationTargetsOnlyCurrentStepFields() {
        val wizard = RemoteHostWizard()
        wizard.selectMethod(RemoteConnectionMethod.DIRECT_SSH)
        wizard.next()
        assertFalse(wizard.next())
        assertNotNull(wizard.state.value.fieldErrors[RemoteWizardField.HOST])
        assertNotNull(wizard.state.value.fieldErrors[RemoteWizardField.USER])
        assertNull(wizard.state.value.fieldErrors[RemoteWizardField.SSH_PORT])
        assertNull(wizard.state.value.fieldErrors[RemoteWizardField.REMOTE_DSH_PORT])
        assertNull(wizard.state.value.fieldErrors[RemoteWizardField.PASSWORD])

        wizard.updateDraft { it.copy(sshPort = "", remoteDshPort = "") }
        assertFalse(wizard.next())
        assertNotNull(wizard.state.value.fieldErrors[RemoteWizardField.SSH_PORT])
        assertNotNull(wizard.state.value.fieldErrors[RemoteWizardField.REMOTE_DSH_PORT])
    }

    // ---------- 阶段错误映射 ----------

    @Test
    fun networkErrorsMapToStageAndNextStep() {
        val tcp = RemoteConnectionErrors.from(ConnectException("Connection refused"), RemoteConnectionStage.TCP)
        assertEquals(RemoteConnectionStage.TCP, tcp.stage)
        assertEquals(RemoteFailureKind.TCP, tcp.kind)
        assertTrue(tcp.reason.contains("拒绝"))
        assertTrue(tcp.suggestion.contains("sshd"))

        val dns = RemoteConnectionErrors.from(UnknownHostException("synthetic-host"), RemoteConnectionStage.TCP)
        assertEquals(RemoteFailureKind.TCP, dns.kind)
        assertTrue(dns.reason.contains("解析"))
        assertTrue(dns.suggestion.contains("IP"))

        val timeout = RemoteConnectionErrors.from(SocketTimeoutException("timeout"), RemoteConnectionStage.SSH_HANDSHAKE)
        assertEquals(RemoteFailureKind.TIMEOUT, timeout.kind)
        assertEquals(RemoteConnectionStage.SSH_HANDSHAKE, timeout.stage)
        assertTrue(timeout.suggestion.contains("防火墙"))
    }

    @Test
    fun authForwardAndDshErrorsMapToTheirStages() {
        val auth = RemoteConnectionErrors.from(IOException("Auth fail for user"), RemoteConnectionStage.AUTHENTICATION)
        assertEquals(RemoteFailureKind.AUTHENTICATION, auth.kind)
        assertEquals(RemoteConnectionStage.AUTHENTICATION, auth.stage)
        assertTrue(auth.suggestion.contains("私钥"))

        val forward = RemoteConnectionErrors.from(IOException("forward failed"), RemoteConnectionStage.PORT_FORWARD)
        assertEquals(RemoteFailureKind.PORT_FORWARD, forward.kind)
        assertEquals(RemoteConnectionStage.PORT_FORWARD, forward.stage)

        val dsh = RemoteConnectionErrors.from(
            com.tanuki.runtime.RemoteHostFailure("远程 DSH 端口未响应"),
            RemoteConnectionStage.PORT_FORWARD
        )
        assertEquals(RemoteFailureKind.DSH_PROBE, dsh.kind)
        assertEquals(RemoteConnectionStage.DSH_PROBE, dsh.stage)
        assertTrue(dsh.suggestion.contains("DSH"))
    }

    @Test
    fun hostKeyChangeIsRejectedAndNeverOffersPlainRetry() {
        val info = RemoteConnectionErrors.from(
            RemoteHostKeyChangedException("SHA256:new"),
            RemoteConnectionStage.SSH_HANDSHAKE
        )
        assertEquals(RemoteFailureKind.HOST_KEY, info.kind)
        assertFalse(info.retryable)
        assertTrue(info.reason.contains("密钥已变化"))
        assertTrue(info.suggestion.contains("核对"))
    }

    @Test
    fun errorMessageNeverEchoesRawExceptionText() {
        val secret = "very-secret-password"
        val info = RemoteConnectionErrors.from(IOException("password=$secret"), RemoteConnectionStage.AUTHENTICATION)
        assertFalse(info.reason.contains(secret))
        assertFalse(info.suggestion.contains(secret))
        assertTrue(info.message.contains("下一步") || info.suggestion.contains("核对"))
    }

    // ---------- 命名连接存储 ----------

    @Test
    fun profileStoreAddsEditsDeletesAndEncryptsSecrets() {
        val fixture = ProfileFixture()
        val first = fixture.store.save(
            "家里电脑",
            config(user = "home", auth = RemoteAuthType.PASSWORD, password = "synthetic-home-password")
        )
        val second = fixture.store.save(
            "公司开发机",
            config(host = "work.example.test", user = "work", auth = RemoteAuthType.PASSWORD, password = "synthetic-work-password")
        )
        assertEquals(2, fixture.store.list().size)
        assertEquals("家里电脑", fixture.store.load(first.id)?.name)
        assertEquals("synthetic-home-password", fixture.store.load(first.id)?.config?.password)

        fixture.store.activate(first.id)
        assertEquals(first.id, fixture.store.activeId())

        val edited = fixture.store.save(
            "家里电脑（新）",
            config(user = "home", auth = RemoteAuthType.PASSWORD, password = "synthetic-home-password-2"),
            first.id
        )
        assertEquals(first.id, edited.id)
        assertEquals("家里电脑（新）", fixture.store.load(first.id)?.name)
        assertEquals(2, fixture.store.list().size)
        assertEquals(second.id, fixture.store.list().last().id)

        fixture.store.delete(first.id)
        assertNull(fixture.store.load(first.id))
        assertEquals(1, fixture.store.list().size)
        assertEquals(second.id, fixture.store.activeId())

        assertTrue(fixture.store.identityUsedByOthers(second.config.hostIdentity()!!, "other"))
        fixture.store.delete(second.id)
        assertTrue(fixture.store.list().isEmpty())
        assertTrue(fixture.config.values.isEmpty())
        assertTrue(fixture.secrets.values.isEmpty())
    }

    @Test
    fun profileStoreNeverStoresPlaintextSecrets() {
        val fixture = ProfileFixture()
        fixture.store.save(
            "加密检查",
            config(auth = RemoteAuthType.PRIVATE_KEY, privateKey = SYNTHETIC_KEY, passphrase = "synthetic-passphrase")
        )
        assertTrue(fixture.config.values.values.none { it.contains("synthetic-passphrase") || it.contains("PRIVATE KEY") })
        assertTrue(fixture.secrets.values.values.none { it.contains("synthetic-passphrase") || it.contains("PRIVATE KEY") })
        assertTrue(fixture.secrets.values.values.all { it.contains(".") })
    }

    @Test
    fun profileStoreRejectsInvalidNamesAndConfigs() {
        val fixture = ProfileFixture()
        assertThrows(IllegalArgumentException::class.java) {
            fixture.store.save(" ", config())
        }
        assertThrows(IllegalArgumentException::class.java) {
            fixture.store.save("坏配置", config(host = "http://bad.example.test"))
        }
    }

    // ---------- 控制器阶段进度 ----------

    @Test
    fun testConnectionReportsAllStagesAndDoesNotSwitchOrigin() = runBlocking {
        val fixture = Fixture()
        val transport = StagedFakeTransport(
            stages = listOf(
                RemoteConnectionStage.TCP,
                RemoteConnectionStage.SSH_HANDSHAKE,
                RemoteConnectionStage.AUTHENTICATION,
                RemoteConnectionStage.PORT_FORWARD
            ),
            tunnel = FakeTunnel(45678)
        )
        fixture.storage.trustFingerprint("dev.example.test", 22, "SHA256:staged")
        val machine = RemoteHostMachine()
        machine.localConnected("http://127.0.0.1:41000", "http://127.0.0.1:41000/?token=local")
        val controller = controller(fixture, transport, machine)

        val result = controller.attempt(config(), keepTunnel = false)
        assertTrue(result is RemoteAttemptResult.Success)
        assertEquals(45678, (result as RemoteAttemptResult.Success).localPort)
        assertEquals(RemoteConnectionStage.COMPLETE, controller.ui.value.stage)
        assertEquals(RemoteConnectionStage.testStages.toSet(), controller.ui.value.completedStages)
        assertTrue(controller.ui.value.attemptSucceeded)
        assertEquals(45678, controller.ui.value.localPort)
        assertEquals("http://127.0.0.1:41000", machine.origin)
        assertEquals(RemotePhase.LOCAL, machine.status.phase)
    }

    @Test
    fun failureKeepsFailingStageAndProvidesChineseSuggestion() = runBlocking {
        val fixture = Fixture()
        fixture.storage.trustFingerprint("dev.example.test", 22, "SHA256:staged")
        val transport = StagedFakeTransport(
            stages = listOf(
                RemoteConnectionStage.TCP,
                RemoteConnectionStage.SSH_HANDSHAKE,
                RemoteConnectionStage.AUTHENTICATION
            ),
            openError = IOException("Auth fail")
        )
        val controller = controller(fixture, transport, RemoteHostMachine())

        val result = controller.attempt(config(), keepTunnel = false)
        assertTrue(result is RemoteAttemptResult.Failure)
        assertEquals(RemoteConnectionStage.AUTHENTICATION, controller.ui.value.stage)
        assertEquals(RemoteFailureKind.AUTHENTICATION, controller.ui.value.failure?.kind)
        assertFalse(controller.ui.value.attemptSucceeded)
        assertTrue(controller.ui.value.error.contains("认证"))
        assertTrue(controller.ui.value.error.contains("核对用户名"))
        assertTrue(
            controller.ui.value.completedStages.containsAll(
                setOf(RemoteConnectionStage.TCP, RemoteConnectionStage.SSH_HANDSHAKE)
            )
        )
    }

    @Test
    fun unknownHostStopsAtFingerprintAndContinuesOnlyAfterConfirmation() = runBlocking {
        val fixture = Fixture()
        val transport = StagedFakeTransport(
            stages = listOf(
                RemoteConnectionStage.TCP,
                RemoteConnectionStage.SSH_HANDSHAKE,
                RemoteConnectionStage.AUTHENTICATION,
                RemoteConnectionStage.PORT_FORWARD
            ),
            tunnel = FakeTunnel(45678)
        )
        val controller = controller(fixture, transport, RemoteHostMachine())

        val first = controller.attempt(config(), keepTunnel = false)
        assertTrue(first is RemoteAttemptResult.HostKeyConfirmation)
        assertEquals("SHA256:staged", (first as RemoteAttemptResult.HostKeyConfirmation).fingerprint)
        assertEquals(1, transport.probeCalls)
        assertEquals(0, transport.openCalls)
        assertEquals(
            setOf(RemoteConnectionStage.TCP, RemoteConnectionStage.SSH_HANDSHAKE),
            controller.ui.value.completedStages
        )
        assertNull(fixture.storage.knownFingerprint("dev.example.test", 22))

        val confirmed = controller.confirmHostKeyNow()
        assertTrue(confirmed is RemoteAttemptResult.Success)
        assertEquals(1, transport.openCalls)
        assertEquals("SHA256:staged", fixture.storage.knownFingerprint("dev.example.test", 22))
        assertEquals(RemoteConnectionStage.COMPLETE, controller.ui.value.stage)
    }

    @Test
    fun dshProbeFailureClosesTunnelAndMapsToDshStage() = runBlocking {
        val fixture = Fixture()
        fixture.storage.trustFingerprint("dev.example.test", 22, "SHA256:staged")
        val tunnel = FakeTunnel(45678)
        val transport = StagedFakeTransport(tunnel = tunnel)
        val machine = RemoteHostMachine()
        machine.localConnected("http://127.0.0.1:41000", "http://127.0.0.1:41000/?token=local")
        val controller = controller(fixture, transport, machine, probeResult = false)

        val result = controller.attempt(config(), keepTunnel = true)
        assertTrue(result is RemoteAttemptResult.Failure)
        assertEquals(RemoteConnectionStage.DSH_PROBE, controller.ui.value.stage)
        assertEquals(RemoteFailureKind.DSH_PROBE, controller.ui.value.failure?.kind)
        assertTrue(tunnel.closed)
        assertEquals("http://127.0.0.1:41000", machine.origin)
    }

    @Test
    fun savedProfilesAreVisibleToControllerAndCanBeDeleted() = runBlocking {
        val fixture = Fixture()
        val profileStore = RemoteHostProfileStore(fixture.profileConfig, AesGcmSecretVault(fixture.key, fixture.profileSecrets))
        val transport = StagedFakeTransport(tunnel = FakeTunnel(45678))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        scopes += scope
        val controller = RemoteHostController(
            storage = fixture.storage,
            transport = transport,
            scope = scope,
            machine = RemoteHostMachine(),
            httpProbe = { true },
            profileStore = profileStore
        )
        controller.saveProfile(
            "家里电脑",
            config(user = "home", auth = RemoteAuthType.PASSWORD, password = "synthetic-password"),
            id = null,
            connect = false
        )
        assertEquals(1, controller.ui.value.profiles.size)
        assertEquals("家里电脑", controller.ui.value.profiles.first().name)

        val id = controller.ui.value.profiles.first().id
        controller.deleteProfile(id)
        assertTrue(controller.ui.value.profiles.isEmpty())
        assertNull(profileStore.load(id))
    }

    private fun readyToTestWizard(): RemoteHostWizard {
        val wizard = RemoteHostWizard()
        wizard.selectMethod(RemoteConnectionMethod.DIRECT_SSH)
        wizard.next()
        wizard.updateDraft { it.copy(host = "dev.example.test", user = "tanuki") }
        wizard.next()
        wizard.setAuth(RemoteAuthType.PASSWORD)
        wizard.updateDraft { it.copy(password = "synthetic-password") }
        wizard.next()
        assertEquals(RemoteWizardStep.TEST, wizard.state.value.step)
        return wizard
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

    private class Fixture {
        val config = MapStore()
        val known = MapStore()
        val secrets = MapStore()
        val profileConfig = MapStore()
        val profileSecrets = MapStore()
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val storage = RemoteHostStorage(config, known, AesGcmSecretVault(key, secrets))
    }

    private class ProfileFixture {
        val config = MapStore()
        val secrets = MapStore()
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val store = RemoteHostProfileStore(config, AesGcmSecretVault(key, secrets))
    }

    private class FakeTunnel(
        override val localPort: Int = 45678,
        private val alive: Boolean = true
    ) : SshTunnel {
        var closed = false
        override fun isAlive(): Boolean = alive && !closed
        override fun close() {
            closed = true
        }
    }

    private class StagedFakeTransport(
        private val fingerprint: String = "SHA256:staged",
        private val stages: List<RemoteConnectionStage> = RemoteConnectionStage.testStages,
        private val openError: Throwable? = null,
        private val tunnel: FakeTunnel = FakeTunnel()
    ) : StagedSshTransport {
        var probeCalls = 0
        var openCalls = 0

        override suspend fun probeHostKey(config: RemoteHostConfig): HostKeyProbe = probeHostKey(config) {}

        override suspend fun probeHostKey(
            config: RemoteHostConfig,
            onStage: (RemoteConnectionStage) -> Unit
        ): HostKeyProbe {
            probeCalls++
            onStage(RemoteConnectionStage.TCP)
            onStage(RemoteConnectionStage.SSH_HANDSHAKE)
            return HostKeyProbe(fingerprint)
        }

        override suspend fun open(config: RemoteHostConfig, expectedFingerprint: String): SshTunnel =
            open(config, expectedFingerprint) {}

        override suspend fun open(
            config: RemoteHostConfig,
            expectedFingerprint: String,
            onStage: (RemoteConnectionStage) -> Unit
        ): SshTunnel {
            openCalls++
            assertEquals(fingerprint, expectedFingerprint)
            stages.forEach(onStage)
            openError?.let { throw it }
            return tunnel
        }
    }

    private fun controller(
        fixture: Fixture,
        transport: StagedFakeTransport,
        machine: RemoteHostMachine = RemoteHostMachine(),
        probeResult: Boolean = true
    ): RemoteHostController {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        scopes += scope
        return RemoteHostController(
            storage = fixture.storage,
            transport = transport,
            scope = scope,
            machine = machine,
            httpProbe = { probeResult }
        )
    }

    companion object {
        private const val SYNTHETIC_KEY =
            "-----BEGIN OPENSSH " + "PRIVATE KEY-----\nsynthetic\n-----END OPENSSH " + "PRIVATE KEY-----"
    }
}
