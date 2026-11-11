package com.tanuki

import com.tanuki.runtime.AesGcmSecretVault
import com.tanuki.runtime.HostKeyProbe
import com.tanuki.runtime.KeyValueStore
import com.tanuki.runtime.PairingClipboardStatus
import com.tanuki.runtime.RelayErrors
import com.tanuki.runtime.RelayFailureKind
import com.tanuki.runtime.RelayTlsPin
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
import com.tanuki.runtime.RemoteHostStatus
import com.tanuki.ui.RemoteHostSwitchPresentation
import com.tanuki.runtime.RemoteHostWizard
import com.tanuki.runtime.RemotePairingCode
import com.tanuki.runtime.RemotePairingGuidance
import com.tanuki.runtime.RemotePhase
import com.tanuki.runtime.RemoteRelayException
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
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

    private fun pairingForWizard(pin: String = "") = RemoteRelayPairing(
        relayHost = "relay.example.test",
        relayPort = 8443,
        roomId = "ICEiIyQlJicoKSorLC0uLw",
        token = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8",
        hostId = "303132333435363738393a3b3c3d3e3f",
        certPin = pin
    )

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
    fun lanDiscoveryPrefillsHostAccountWithoutMethodStep() {
        val wizard = RemoteHostWizard()
        assertTrue(wizard.startFromLanDiscovery("192.168.1.20", 2222, 3080, "书房 Mac"))

        val state = wizard.state.value
        assertEquals(RemoteWizardStep.HOST_ACCOUNT, state.step)
        assertEquals(RemoteConnectionMethod.LAN, state.method)
        assertEquals("192.168.1.20", state.draft.host)
        assertEquals("2222", state.draft.sshPort)
        assertEquals("3080", state.draft.remoteDshPort)
        assertEquals("书房 Mac", state.name)
        assertTrue(state.fieldErrors.isEmpty())
    }

    @Test
    fun discoveredComputerDoesNotReuseSavedCredentialsOrProfile() {
        val wizard = RemoteHostWizard(config(auth = RemoteAuthType.PASSWORD, password = "synthetic-password"), "旧电脑", "old-profile")
        assertTrue(wizard.startFromLanDiscovery("192.168.1.20", 22, 3080, "新电脑"))
        val state = wizard.state.value
        assertEquals("", state.draft.password)
        assertEquals("", state.draft.privateKey)
        assertNull(state.editingProfileId)
        assertNull(state.hostKeyFingerprint)
        assertFalse(state.tested)
        assertFalse(state.pairingAcknowledged)
    }

    @Test
    fun lanDiscoveryRejectsInvalidAddressOrPortWithoutChangingDraft() {
        val wizard = RemoteHostWizard()
        assertFalse(wizard.startFromLanDiscovery("ssh://bad.example", 22, 3080, "坏主机"))
        assertFalse(wizard.startFromLanDiscovery("192.168.1.20", 0, 3080, "坏主机"))
        assertFalse(wizard.startFromLanDiscovery("192.168.1.20", 22, 70000, "坏主机"))
        assertEquals(RemoteWizardStep.METHOD, wizard.state.value.step)
        assertEquals("", wizard.state.value.draft.host)
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

    @Test
    fun latePrivateKeyReadIsDiscardedAfterAuthSwitch() {
        val wizard = RemoteHostWizard()
        wizard.selectMethod(RemoteConnectionMethod.DIRECT_SSH)
        wizard.next()
        wizard.updateDraft { it.copy(host = "dev.example.test", user = "tanuki") }
        wizard.next()
        wizard.setAuth(RemoteAuthType.PRIVATE_KEY)
        val request = wizard.beginPrivateKeyRead()
        wizard.setAuth(RemoteAuthType.PASSWORD)
        assertFalse(wizard.applyPrivateKeyRead(request, "id_rsa", SYNTHETIC_KEY))
        assertEquals("", wizard.state.value.draft.privateKey)
    }

    @Test
    fun latePrivateKeyReadIsDiscardedAfterTestStarts() {
        val wizard = RemoteHostWizard()
        wizard.selectMethod(RemoteConnectionMethod.DIRECT_SSH)
        wizard.next()
        wizard.updateDraft { it.copy(host = "dev.example.test", user = "tanuki") }
        wizard.next()
        wizard.setAuth(RemoteAuthType.PRIVATE_KEY)
        wizard.updateDraft { it.copy(privateKey = SYNTHETIC_KEY) }
        val request = wizard.beginPrivateKeyRead()
        assertTrue(wizard.next())
        assertEquals(RemoteWizardStep.TEST, wizard.state.value.step)

        assertFalse(wizard.applyPrivateKeyRead(request, "new-key", "-----BEGIN NEW-----"))
        assertEquals(SYNTHETIC_KEY, wizard.state.value.draft.privateKey)
        assertEquals(RemoteWizardStep.TEST, wizard.state.value.step)
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
    fun ipv6WizardConfigPassesDownstreamValidation() {
        val wizard = RemoteHostWizard()
        wizard.selectMethod(RemoteConnectionMethod.LAN)
        wizard.next()
        wizard.updateDraft {
            it.copy(host = "[2001:db8::10]", sshPort = "22", user = "tanuki", remoteDshPort = "3080")
        }
        wizard.next()
        wizard.setAuth(RemoteAuthType.PASSWORD)
        wizard.updateDraft { it.copy(password = "synthetic-password") }
        wizard.next()

        val config = wizard.buildConfig()!!
        assertEquals("2001:db8::10", config.normalizedHost())
        assertNull("向导生成的 IPv6 配置必须能被控制器再次校验", config.validationError())
        assertNull(RemoteWizardValidation.host("2001:db8::10"))
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
    fun clipboardPairingAutoFillsLinkComputesCodeAndBuildsPreview() {
        val pin = RelayTlsPin.fingerprint("synthetic-relay-cert".toByteArray())
        val pairing = pairingForWizard(pin)
        val wizard = RemoteHostWizard()
        wizard.selectMethod(RemoteConnectionMethod.PAIRING_LINK)
        assertTrue(wizard.next())
        val visit = wizard.state.value.clipboardVisit

        // 剪贴板常带首尾空白；自动填入必须使用 trim 后的规范链接，码也必须按同一文本计算。
        assertTrue(wizard.applyClipboardPairing("\n  ${pairing.toUri()}  \n", visit))
        val state = wizard.state.value
        val expected = RemotePairingCode.of(pairing)
        assertEquals(pairing.toUri(), state.draft.relayUri)
        assertEquals(expected, state.draft.pairingCode)
        assertEquals(expected, RemotePairingCode.ofLink(state.draft.relayUri))
        assertTrue(RemotePairingCode.matches(pairing, state.draft.pairingCode))
        assertEquals(PairingClipboardStatus.AUTO_FILLED, state.clipboardStatus)
        assertTrue(state.clipboardMessage.contains("自动算出"))

        val preview = requireNotNull(state.pairingPreview)
        assertEquals(expected, preview.code)
        assertEquals("tls://relay.example.test:8443", preview.relayLabel)
        assertEquals(pin, preview.pin)
        assertTrue(preview.pinned)
        assertTrue(preview.pinSummary.contains("…"))
        assertTrue(preview.pinSummary.length < pin.length)
        assertTrue(preview.computerName.contains("未命名电脑"))
        assertTrue(preview.sshHint.contains("127.0.0.1:22"))

        wizard.updateDraft { it.copy(user = "tanuki", host = "家里电脑") }
        assertTrue(wizard.next())
        assertEquals(RemoteWizardStep.PAIRING_CONFIRM, wizard.state.value.step)
        assertEquals("家里电脑", requireNotNull(wizard.state.value.pairingPreview).computerName)
        assertTrue(wizard.state.value.canConfirmPairing)
        assertTrue(wizard.confirmPairing())
        assertEquals(RemoteWizardStep.AUTHENTICATION, wizard.state.value.step)
        assertTrue(wizard.state.value.pairingAcknowledged)
    }

    @Test
    fun clipboardDoesNotOverwriteManualPairingLinkAndFallbackStillWorks() {
        val first = pairingForWizard()
        val second = first.copy(roomId = "MDEyMzQ1Njc4OWFiY2RlZg")
        val wizard = RemoteHostWizard()
        wizard.selectMethod(RemoteConnectionMethod.PAIRING_LINK)
        assertTrue(wizard.next())
        val visit = wizard.state.value.clipboardVisit

        // 手动粘贴回退：链接变化后自动算码，仍不要求用户手输。
        wizard.updateRelayLink(first.toUri())
        assertEquals(RemotePairingCode.of(first), wizard.state.value.draft.pairingCode)

        assertFalse(wizard.applyClipboardPairing(second.toUri(), visit))
        assertEquals(first.toUri(), wizard.state.value.draft.relayUri)
        assertEquals(PairingClipboardStatus.KEPT_EXISTING, wizard.state.value.clipboardStatus)
        assertEquals(second.toUri(), wizard.state.value.pendingClipboardLink)

        // 用户显式选择后才替换，并重新计算码。
        assertTrue(wizard.usePendingClipboardLink())
        assertEquals(second.toUri(), wizard.state.value.draft.relayUri)
        assertEquals(RemotePairingCode.of(second), wizard.state.value.draft.pairingCode)
        assertEquals("", wizard.state.value.pendingClipboardLink)
    }

    @Test
    fun clipboardNonLinkContentAndInvalidLinkKeepPasteFallback() {
        val wizard = RemoteHostWizard()
        wizard.selectMethod(RemoteConnectionMethod.PAIRING_LINK)
        assertTrue(wizard.next())
        val visit = wizard.state.value.clipboardVisit

        assertFalse(wizard.applyClipboardPairing(null, visit))
        assertEquals(PairingClipboardStatus.EMPTY, wizard.state.value.clipboardStatus)
        assertEquals("", wizard.state.value.draft.relayUri)

        assertFalse(wizard.applyClipboardPairing("https://example.test/not-a-pairing", visit))
        assertEquals(PairingClipboardStatus.NOT_A_LINK, wizard.state.value.clipboardStatus)
        assertEquals("", wizard.state.value.draft.relayUri)
        assertTrue(wizard.state.value.clipboardMessage.contains("手动粘贴"))

        // 确实是 tanuki-relay:// 但字段缺失：填入原文、给出字段错误，并保留修改入口。
        val malformed = "tanuki-relay://v1?relay=relay.example.test%3A8443"
        assertFalse(wizard.applyClipboardPairing(malformed, visit))
        assertEquals(PairingClipboardStatus.INVALID_LINK, wizard.state.value.clipboardStatus)
        assertEquals(malformed, wizard.state.value.draft.relayUri)
        assertNull(wizard.state.value.pairingPreview)
        assertNotNull(wizard.state.value.fieldErrors[RemoteWizardField.RELAY_LINK])
        assertFalse(wizard.next())
    }

    @Test
    fun pairingCodeIsOptionalButMismatchedManualCodeExplainsAutoValue() {
        val pairing = pairingForWizard()
        val wizard = RemoteHostWizard()
        wizard.selectMethod(RemoteConnectionMethod.PAIRING_LINK)
        assertTrue(wizard.next())
        wizard.updateRelayLink(pairing.toUri())
        wizard.updateDraft { it.copy(user = "tanuki") }

        // 自动算出的码已足够推进；确认页在认证之前。
        assertTrue(wizard.next())
        assertEquals(RemoteWizardStep.PAIRING_CONFIRM, wizard.state.value.step)
        assertTrue(wizard.next())
        assertEquals(RemoteWizardStep.AUTHENTICATION, wizard.state.value.step)

        // 回退：用户从别处拿到码时可在校对框比对；不一致时给出自动值和修正入口。
        wizard.back()
        assertEquals(RemoteWizardStep.PAIRING_CONFIRM, wizard.state.value.step)
        assertTrue(wizard.back())
        assertEquals(RemoteWizardStep.HOST_ACCOUNT, wizard.state.value.step)
        val expected = RemotePairingCode.of(pairing)
        val wrong = if (expected == "999999") "000000" else "999999"
        wizard.updateDraft { it.copy(pairingCode = wrong) }
        assertFalse(wizard.next())
        val error = wizard.state.value.fieldErrors[RemoteWizardField.PAIRING_CODE].orEmpty()
        assertTrue(error.contains(expected))
        assertTrue(error.contains("填入自动码"))

        assertTrue(wizard.fillAutoPairingCode())
        assertEquals(expected, wizard.state.value.draft.pairingCode)
        assertTrue(wizard.next())
        assertEquals(RemoteWizardStep.PAIRING_CONFIRM, wizard.state.value.step)
    }

    @Test
    fun pairingStepsIncludeConfirmationOnlyForRelayMethod() {
        val pairingWizard = RemoteHostWizard()
        pairingWizard.selectMethod(RemoteConnectionMethod.PAIRING_LINK)
        assertEquals(7, pairingWizard.state.value.stepTotal)
        assertTrue(pairingWizard.state.value.steps.contains(RemoteWizardStep.PAIRING_CONFIRM))
        assertEquals(
            listOf(
                RemoteWizardStep.METHOD,
                RemoteWizardStep.HOST_ACCOUNT,
                RemoteWizardStep.PAIRING_CONFIRM,
                RemoteWizardStep.AUTHENTICATION,
                RemoteWizardStep.FINGERPRINT,
                RemoteWizardStep.TEST,
                RemoteWizardStep.DONE
            ),
            pairingWizard.state.value.steps
        )
        assertEquals("读取配对链接", RemoteWizardStep.HOST_ACCOUNT.titleFor(RemoteConnectionMethod.PAIRING_LINK))
        assertEquals("填写主机与账号", RemoteWizardStep.HOST_ACCOUNT.titleFor(RemoteConnectionMethod.DIRECT_SSH))

        val directWizard = RemoteHostWizard()
        directWizard.selectMethod(RemoteConnectionMethod.DIRECT_SSH)
        assertFalse(directWizard.state.value.steps.contains(RemoteWizardStep.PAIRING_CONFIRM))
        assertEquals(6, directWizard.state.value.stepTotal)
    }

    @Test
    fun relayFailureGuidanceCoversExpiryPinMismatchOfflineAndUnreachablePort() {
        val expired = RemoteConnectionErrors.from(
            RemoteRelayException(RelayFailureKind.EXPIRED, "synthetic"),
            RemoteConnectionStage.TCP
        )
        assertEquals(RemoteFailureKind.RELAY, expired.kind)
        assertTrue(expired.reason.contains("过期"))
        assertTrue(expired.suggestion.contains("重新生成"))
        assertFalse(expired.retryable)
        assertEquals(RelayFailureKind.EXPIRED, RelayErrors.fromCode("expired").kind)

        val missing = RemoteConnectionErrors.from(RelayErrors.fromCode("room_not_found"), RemoteConnectionStage.TCP)
        assertTrue(missing.reason.contains("找不到"))
        assertTrue(missing.suggestion.contains("重新生成"))

        val offline = RemoteConnectionErrors.from(RelayErrors.fromCode("host_offline"), RemoteConnectionStage.TCP)
        assertTrue(offline.reason.contains("电脑端"))
        assertTrue(offline.suggestion.contains("电脑"))
        assertTrue(offline.retryable)

        val pin = RemoteConnectionErrors.from(
            RemoteRelayException(RelayFailureKind.PIN_MISMATCH, "中继证书指纹不匹配，已拒绝连接"),
            RemoteConnectionStage.TCP
        )
        assertTrue(pin.reason.contains("证书"))
        assertTrue(pin.reason.contains("不一致"))
        assertTrue(pin.suggestion.contains("重新"))
        assertTrue(pin.suggestion.contains("中继"))
        assertFalse(pin.retryable)
        assertFalse(pin.message.contains("AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8"))

        val unreachable = RemoteConnectionErrors.from(
            RemoteRelayException(RelayFailureKind.NETWORK, "无法连接中继"),
            RemoteConnectionStage.TCP
        )
        assertTrue(unreachable.reason.contains("端口"))
        assertTrue(unreachable.suggestion.contains("网络"))
        assertTrue(unreachable.retryable)

        // 五类失败都要有中文原因和可执行下一步，不能只有一句“连接失败”。
        listOf(
            RelayFailureKind.EXPIRED,
            RelayFailureKind.ROOM_NOT_FOUND,
            RelayFailureKind.HOST_OFFLINE,
            RelayFailureKind.PIN_MISMATCH,
            RelayFailureKind.NETWORK
        ).forEach { kind ->
            val advice = RemotePairingGuidance.of(kind)
            assertTrue(advice.reason.isNotBlank())
            assertTrue(advice.suggestion.contains("重") || advice.suggestion.contains("确认"))
        }
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
    fun pairingCodeMatchesSharedVector() {
        val uri = "tanuki-relay://v1?relay=relay.example.test%3A8443" +
            "&room=ICEiIyQlJicoKSorLC0uLw" +
            "&token=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8" +
            "&host=303132333435363738393a3b3c3d3e3f"
        assertEquals("308002", RemotePairingCode.ofLink(uri))
        assertNull(RemotePairingCode.validate("308002"))
        assertNotNull(RemotePairingCode.validate("12345"))
        assertNotNull(RemotePairingCode.validate("12345a"))
    }

    @Test
    fun switchChipReflectsRealStatus() {
        assertEquals(
            "本机·运行中",
            RemoteHostSwitchPresentation.of(RemoteHostStatus(phase = RemotePhase.LOCAL), localRunning = true).label
        )
        assertEquals(
            "本机·未运行",
            RemoteHostSwitchPresentation.of(RemoteHostStatus(phase = RemotePhase.LOCAL), localRunning = false).label
        )
        assertEquals(
            "电脑·已连接",
            RemoteHostSwitchPresentation.of(
                RemoteHostStatus(phase = RemotePhase.REMOTE, host = "家里电脑"),
                localRunning = true
            ).label
        )
        assertEquals(
            "切换中…",
            RemoteHostSwitchPresentation.of(RemoteHostStatus(phase = RemotePhase.CONNECTING), localRunning = true).label
        )
        assertEquals(
            "本机·已回退",
            RemoteHostSwitchPresentation.of(RemoteHostStatus(phase = RemotePhase.FALLBACK), localRunning = true).label
        )
        assertFalse(RemoteHostSwitchPresentation.canSwitch(RemoteHostStatus(phase = RemotePhase.CONNECTING)))
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
    fun deletingProfileClearsActiveCopyButKeepsEstablishedTunnel() = runBlocking {
        val fixture = Fixture()
        val profileStore = RemoteHostProfileStore(fixture.profileConfig, AesGcmSecretVault(fixture.key, fixture.profileSecrets))
        val tunnel = FakeTunnel(45678)
        val transport = StagedFakeTransport(tunnel = tunnel)
        fixture.storage.trustFingerprint("dev.example.test", 22, "SHA256:staged")
        val machine = RemoteHostMachine()
        machine.localConnected("http://127.0.0.1:41000", "http://127.0.0.1:41000/?token=local")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        scopes += scope
        val controller = RemoteHostController(
            storage = fixture.storage,
            transport = transport,
            scope = scope,
            machine = machine,
            httpProbe = { true },
            profileStore = profileStore
        )
        val profile = profileStore.save("家里电脑", config(auth = RemoteAuthType.PASSWORD, password = "synthetic-password"))

        assertTrue(controller.attempt(profile.config, keepTunnel = true, profileId = profile.id) is RemoteAttemptResult.Success)
        assertNotNull(fixture.storage.load())
        assertEquals(profile.id, fixture.storage.sourceProfileId())
        assertEquals(RemotePhase.REMOTE, machine.status.phase)

        controller.deleteProfile(profile.id)

        assertNull("删除命名连接后不得留下活动配置副本", fixture.storage.load())
        assertNull(controller.ui.value.saved)
        assertTrue(controller.ui.value.profiles.isEmpty())
        assertFalse("已建立的隧道不应因删除保存记录而被关闭", tunnel.closed)
        assertEquals(RemotePhase.REMOTE, machine.status.phase)
    }

    @Test
    fun editingProfileTargetThenDeletingStillClearsOldActiveCopy() = runBlocking {
        val fixture = Fixture()
        val profileStore = RemoteHostProfileStore(fixture.profileConfig, AesGcmSecretVault(fixture.key, fixture.profileSecrets))
        val tunnel = FakeTunnel(45678)
        val transport = StagedFakeTransport(tunnel = tunnel)
        fixture.storage.trustFingerprint("a.example.test", 22, "SHA256:staged")
        val machine = RemoteHostMachine()
        machine.localConnected("http://127.0.0.1:41000", "http://127.0.0.1:41000/?token=local")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        scopes += scope
        val controller = RemoteHostController(
            storage = fixture.storage,
            transport = transport,
            scope = scope,
            machine = machine,
            httpProbe = { true },
            profileStore = profileStore
        )
        val profile = profileStore.save(
            "家里电脑",
            config(host = "a.example.test", auth = RemoteAuthType.PASSWORD, password = "synthetic-a")
        )
        assertTrue(controller.attempt(profile.config, keepTunnel = true, profileId = profile.id) is RemoteAttemptResult.Success)
        assertEquals(profile.id, fixture.storage.sourceProfileId())

        // 只编辑目标并保存，不连接：活动存储仍是 A，但 profile 已变成 B。
        profileStore.save(
            "家里电脑",
            config(host = "b.example.test", auth = RemoteAuthType.PASSWORD, password = "synthetic-b"),
            profile.id
        )
        assertEquals("a.example.test", fixture.storage.load()?.host)

        controller.deleteProfile(profile.id)
        assertNull(fixture.storage.load())
        assertNull(fixture.storage.sourceProfileId())
        assertNull(controller.ui.value.saved)
        assertFalse(tunnel.closed)
        assertEquals(RemotePhase.REMOTE, machine.status.phase)
    }

    @Test
    fun deletingOneOfTwoProfilesWithSameServiceKeepsActiveCopyOfOther() = runBlocking {
        val fixture = Fixture()
        val profileStore = RemoteHostProfileStore(fixture.profileConfig, AesGcmSecretVault(fixture.key, fixture.profileSecrets))
        val tunnel = FakeTunnel(45678)
        val transport = StagedFakeTransport(tunnel = tunnel)
        fixture.storage.trustFingerprint("a.example.test", 22, "SHA256:staged")
        val machine = RemoteHostMachine()
        machine.localConnected("http://127.0.0.1:41000", "http://127.0.0.1:41000/?token=local")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        scopes += scope
        val controller = RemoteHostController(
            storage = fixture.storage,
            transport = transport,
            scope = scope,
            machine = machine,
            httpProbe = { true },
            profileStore = profileStore
        )
        val value = config(host = "a.example.test", auth = RemoteAuthType.PASSWORD, password = "synthetic-a")
        val first = profileStore.save("家里电脑", value)
        val second = profileStore.save("同一台电脑（备用）", value)
        assertTrue(controller.attempt(value, keepTunnel = true, profileId = first.id) is RemoteAttemptResult.Success)

        controller.deleteProfile(second.id)
        assertNotNull("删除同服务的另一条 profile 不应清除活动副本", fixture.storage.load())
        assertEquals(first.id, fixture.storage.sourceProfileId())
        assertEquals("a.example.test", controller.ui.value.saved?.host)
        assertFalse(tunnel.closed)

        controller.deleteProfile(first.id)
        assertNull(fixture.storage.load())
        assertNull(controller.ui.value.saved)
        assertFalse(tunnel.closed)
    }

    @Test
    fun publicReconnectAfterRestartPreservesSourceProfileId() = runBlocking {
        val fixture = Fixture()
        val profileStore = RemoteHostProfileStore(fixture.profileConfig, AesGcmSecretVault(fixture.key, fixture.profileSecrets))
        val tunnel = FakeTunnel(45678)
        val transport = StagedFakeTransport(tunnel = tunnel)
        fixture.storage.trustFingerprint("a.example.test", 22, "SHA256:staged")
        val machine = RemoteHostMachine()
        machine.localConnected("http://127.0.0.1:41000", "http://127.0.0.1:41000/?token=local")
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        scopes += firstScope
        val profile = profileStore.save(
            "家里电脑",
            config(host = "a.example.test", auth = RemoteAuthType.PASSWORD, password = "synthetic-a")
        )
        val first = RemoteHostController(
            storage = fixture.storage,
            transport = transport,
            scope = firstScope,
            machine = machine,
            httpProbe = { true },
            profileStore = profileStore
        )
        first.connect(profile.config, profile.id)
        withTimeout(5_000) {
            while (machine.status.phase != RemotePhase.REMOTE) delay(10)
        }
        assertEquals(profile.id, fixture.storage.sourceProfileId())

        // 模拟重启：新控制器从同一存储加载活动配置，再用公开 connect(config) 重连。
        val restartedMachine = RemoteHostMachine()
        restartedMachine.localConnected("http://127.0.0.1:41000", "http://127.0.0.1:41000/?token=local")
        val restartedScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        scopes += restartedScope
        val restarted = RemoteHostController(
            storage = fixture.storage,
            transport = transport,
            scope = restartedScope,
            machine = restartedMachine,
            httpProbe = { true },
            profileStore = profileStore
        )
        withTimeout(5_000) {
            while (restarted.ui.value.saved == null) delay(10)
        }
        restarted.connect(restarted.ui.value.saved!!)
        withTimeout(5_000) {
            while (restartedMachine.status.phase != RemotePhase.REMOTE) delay(10)
        }
        assertEquals("公开重连必须保留来源 profile", profile.id, fixture.storage.sourceProfileId())

        // 编辑同一 profile 目标为 B（仅保存），再删除：仍应按来源清理 A 的旧副本。
        profileStore.save(
            "家里电脑",
            config(host = "b.example.test", auth = RemoteAuthType.PASSWORD, password = "synthetic-b"),
            profile.id
        )
        restarted.deleteProfile(profile.id)
        withTimeout(5_000) {
            while (restarted.ui.value.profiles.isNotEmpty()) delay(10)
        }
        assertNull(fixture.storage.load())
        assertNull(fixture.storage.sourceProfileId())
        assertNull(restarted.ui.value.saved)
        assertFalse(tunnel.closed)
    }

    @Test
    fun newManualConnectDoesNotInheritPreviousSourceProfile() = runBlocking {
        val fixture = Fixture()
        val profileStore = RemoteHostProfileStore(fixture.profileConfig, AesGcmSecretVault(fixture.key, fixture.profileSecrets))
        val transport = StagedFakeTransport(tunnel = FakeTunnel(45678))
        fixture.storage.trustFingerprint("a.example.test", 22, "SHA256:staged")
        fixture.storage.trustFingerprint("c.example.test", 22, "SHA256:staged")
        val machine = RemoteHostMachine()
        machine.localConnected("http://127.0.0.1:41000", "http://127.0.0.1:41000/?token=local")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        scopes += scope
        val controller = RemoteHostController(
            storage = fixture.storage,
            transport = transport,
            scope = scope,
            machine = machine,
            httpProbe = { true },
            profileStore = profileStore
        )
        val profile = profileStore.save(
            "家里电脑",
            config(host = "a.example.test", auth = RemoteAuthType.PASSWORD, password = "synthetic-a")
        )
        controller.connect(profile.config, profile.id)
        withTimeout(5_000) {
            while (machine.status.phase != RemotePhase.REMOTE) delay(10)
        }
        assertEquals(profile.id, fixture.storage.sourceProfileId())

        // 新的手工配置成功后不能继承 P 的来源；删除 P 也不应清掉 C 的活动副本。
        controller.connect(
            config(host = "c.example.test", user = "c", auth = RemoteAuthType.PASSWORD, password = "synthetic-c")
        )
        withTimeout(5_000) {
            while (controller.ui.value.saved?.host != "c.example.test") delay(10)
        }
        assertNull(fixture.storage.sourceProfileId())
        controller.deleteProfile(profile.id)
        withTimeout(5_000) {
            while (controller.ui.value.profiles.isNotEmpty()) delay(10)
        }
        assertEquals("c.example.test", fixture.storage.load()?.host)
        assertEquals("c.example.test", controller.ui.value.saved?.host)
    }

    @Test
    fun legacyActiveCopyWithoutSourceClearsForSameHostDifferentAccount() = runBlocking {
        val fixture = Fixture()
        val profileStore = RemoteHostProfileStore(fixture.profileConfig, AesGcmSecretVault(fixture.key, fixture.profileSecrets))
        val tunnel = FakeTunnel(45678)
        val transport = StagedFakeTransport(tunnel = tunnel)
        fixture.storage.trustFingerprint("a.example.test", 22, "SHA256:staged")
        val machine = RemoteHostMachine()
        machine.localConnected("http://127.0.0.1:41000", "http://127.0.0.1:41000/?token=local")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        scopes += scope
        val controller = RemoteHostController(
            storage = fixture.storage,
            transport = transport,
            scope = scope,
            machine = machine,
            httpProbe = { true },
            profileStore = profileStore
        )
        val alice = profileStore.save(
            "alice",
            config(host = "a.example.test", user = "alice", auth = RemoteAuthType.PASSWORD, password = "alice-secret")
        )
        profileStore.save(
            "bob",
            config(host = "a.example.test", user = "bob", auth = RemoteAuthType.PASSWORD, password = "bob-secret")
        )
        assertTrue(controller.attempt(alice.config, keepTunnel = true, profileId = alice.id) is RemoteAttemptResult.Success)

        // 模拟从旧版本升级：活动副本存在，但没有 sourceProfileId。
        fixture.storage.save(alice.config)
        assertNull(fixture.storage.sourceProfileId())

        controller.deleteProfile(alice.id)
        withTimeout(5_000) {
            while (controller.ui.value.profiles.any { it.id == alice.id }) delay(10)
        }
        assertNull("同主机不同账号不能阻止删除方清理自己的活动凭据", fixture.storage.load())
        assertNull(controller.ui.value.saved)
        assertFalse(tunnel.closed)
        // bob 仍共享同一主机密钥信任，但 alice 的凭据副本已被清理。
        assertNotNull(fixture.storage.knownFingerprint("a.example.test", 22))
        assertTrue(controller.ui.value.profiles.any { it.name == "bob" })
    }

    @Test
    fun failedAttemptConfigKeepsRetryOnFailedHostInsteadOfPreviousSavedHost() = runBlocking {
        val fixture = Fixture()
        fixture.storage.trustFingerprint("a.example.test", 22, "SHA256:staged")
        fixture.storage.trustFingerprint("b.example.test", 22, "SHA256:staged")
        val transport = StagedFakeTransport(
            stages = listOf(
                RemoteConnectionStage.TCP,
                RemoteConnectionStage.SSH_HANDSHAKE,
                RemoteConnectionStage.AUTHENTICATION
            ),
            openError = IOException("Auth fail"),
            openErrorHost = "b.example.test"
        )
        val machine = RemoteHostMachine()
        machine.localConnected("http://127.0.0.1:41000", "http://127.0.0.1:41000/?token=local")
        val controller = controller(fixture, transport, machine)

        assertTrue(controller.attempt(config(host = "a.example.test"), keepTunnel = true) is RemoteAttemptResult.Success)
        assertEquals("a.example.test", controller.ui.value.saved?.host)

        val failed = controller.attempt(config(host = "b.example.test"), keepTunnel = true)
        assertTrue(failed is RemoteAttemptResult.Failure)
        assertEquals("b.example.test", controller.ui.value.attemptConfig?.host)
        assertEquals("a.example.test", controller.ui.value.saved?.host)
        assertEquals(RemoteFailureKind.AUTHENTICATION, controller.ui.value.failure?.kind)
    }

    @Test
    fun firstConnectionFailureStillRetainsRetryConfig() = runBlocking {
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

        assertTrue(controller.attempt(config(), keepTunnel = false) is RemoteAttemptResult.Failure)
        assertNull(controller.ui.value.saved)
        assertEquals("dev.example.test", controller.ui.value.attemptConfig?.host)
        assertEquals(RemoteFailureKind.AUTHENTICATION, controller.ui.value.failure?.kind)
    }

    @Test
    fun wizardLeavesBusyAfterControllerSuccessAndShowsFailureStage() = runBlocking {
        // 成功：控制器 attemptSucceeded/localPort 驱动向导离开 TEST，而不是永久 busy。
        val successFixture = Fixture()
        successFixture.storage.trustFingerprint("dev.example.test", 22, "SHA256:staged")
        val successController = controller(
            successFixture,
            StagedFakeTransport(tunnel = FakeTunnel(45678)),
            RemoteHostMachine()
        )
        val successWizard = readyToTestWizard()
        val success = successController.attempt(successWizard.buildConfig()!!, keepTunnel = false)
        assertTrue(success is RemoteAttemptResult.Success)
        successController.ui.value.stage?.let(successWizard::onStage)
        successController.ui.value.failure?.let(successWizard::onFailure)
        if (successController.ui.value.attemptSucceeded) {
            successWizard.onTestSucceeded(successController.ui.value.localPort)
        }
        assertEquals(RemoteWizardStep.DONE, successWizard.state.value.step)
        assertFalse(successWizard.state.value.busy)
        assertEquals(45678, successWizard.state.value.localPort)

        // 失败：控制器 failure/stage 驱动向导退出 busy，并停在失败阶段。
        val failFixture = Fixture()
        failFixture.storage.trustFingerprint("dev.example.test", 22, "SHA256:staged")
        val failController = controller(
            failFixture,
            StagedFakeTransport(
                stages = listOf(
                    RemoteConnectionStage.TCP,
                    RemoteConnectionStage.SSH_HANDSHAKE,
                    RemoteConnectionStage.AUTHENTICATION
                ),
                openError = IOException("Auth fail")
            ),
            RemoteHostMachine()
        )
        val failWizard = readyToTestWizard()
        val failed = failController.attempt(failWizard.buildConfig()!!, keepTunnel = false)
        assertTrue(failed is RemoteAttemptResult.Failure)
        failController.ui.value.stage?.let(failWizard::onStage)
        failController.ui.value.failure?.let(failWizard::onFailure)
        assertEquals(RemoteWizardStep.TEST, failWizard.state.value.step)
        assertFalse(failWizard.state.value.busy)
        assertEquals(RemoteConnectionStage.AUTHENTICATION, failWizard.state.value.failure?.stage)
        assertEquals(RemoteStageStatus.FAILED, failWizard.stageStatus(RemoteConnectionStage.AUTHENTICATION))
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
        /** 只让指定主机的 open 失败，用于“A 成功、B 失败”的重试归属回归。 */
        private val openErrorHost: String? = null,
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
            openError?.let {
                if (openErrorHost == null || config.host == openErrorHost) throw it
            }
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
