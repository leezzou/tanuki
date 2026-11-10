package com.tanuki.runtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 远程主机设置面板状态；saved 是当前活动配置，profiles 是命名连接库。 */
data class RemoteHostUiState(
    val saved: RemoteHostConfig? = null,
    val status: RemoteHostStatus = RemoteHostStatus(),
    val busy: Boolean = false,
    val message: String = "",
    val error: String = "",
    val pendingFingerprint: String? = null,
    val pendingHost: String = "",
    val profiles: List<RemoteHostProfile> = emptyList(),
    val activeProfileId: String? = null,
    val stage: RemoteConnectionStage? = null,
    val completedStages: Set<RemoteConnectionStage> = emptySet(),
    val failure: RemoteFailureInfo? = null,
    val attemptSucceeded: Boolean = false,
    val localPort: Int? = null
)

sealed interface RemoteAttemptResult {
    data class HostKeyConfirmation(val fingerprint: String) : RemoteAttemptResult
    data class Success(val message: String, val localPort: Int? = null) : RemoteAttemptResult
    data class Failure(val message: String) : RemoteAttemptResult
}

/**
 * 远程主机连接流程：校验配置 -> 首次主机密钥确认 -> SSH 认证/端口转发 -> 远程 DSH 探测 -> 切换 origin。
 *
 * 任何失败都会关闭隧道并回退本地；token 只用于本机 loopback 启动 URL，不写日志。
 * 阶段进度与结构化错误只用于展示，不改变严格的主机密钥校验。
 */
class RemoteHostController(
    private val storage: RemoteHostStorage,
    private val transport: SshTransport,
    private val scope: CoroutineScope,
    private val machine: RemoteHostMachine,
    private val onStatus: (RemoteHostStatus) -> Unit = {},
    private val httpProbe: (Int) -> Boolean = { RemoteHttpProbe.probe(it) },
    /** 在切换管理页端点前交换 Cookie 容器；失败时拒绝远程连接/回退。 */
    private val activateScope: (String) -> Unit = {},
    /** 命名连接库；为空时仍可连接，只是不能保存到“我的连接”。 */
    private val profileStore: RemoteHostProfileStore? = null
) {
    private val mutex = Mutex()
    private val mutableUi = MutableStateFlow(RemoteHostUiState(status = machine.status))
    val ui: StateFlow<RemoteHostUiState> = mutableUi.asStateFlow()

    private var tunnel: SshTunnel? = null
    private var watchJob: Job? = null
    private var pending: PendingConnection? = null

    init {
        scope.launch {
            val saved = runCatching { storage.load() }.getOrElse { error ->
                mutableUi.update { it.copy(error = "已保存的远程配置无法读取：${friendlyStorage(error)}") }
                null
            }
            mutableUi.update { it.copy(saved = saved) }
            refreshProfiles()
        }
    }

    fun test(config: RemoteHostConfig) = launchAttempt(config, keepTunnel = false)

    fun connect(config: RemoteHostConfig) = launchAttempt(config, keepTunnel = true)

    /** 连接命名连接：先标记“上次使用”，再走与手动配置相同的严格流程。 */
    fun connectProfile(profile: RemoteHostProfile) {
        scope.launch {
            runCatching { profileStore?.activate(profile.id) }
            refreshProfiles()
            connect(profile.config)
        }
    }

    /**
     * 保存命名连接。
     *
     * @param connect 保存后立即连接；连接仍会重新执行主机密钥/认证/DSH 探测。
     */
    fun saveProfile(name: String, config: RemoteHostConfig, id: String?, connect: Boolean) {
        scope.launch {
            val store = profileStore
            if (store == null) {
                mutableUi.update { it.copy(error = "命名连接库不可用，未保存；本次仍可直接连接") }
                if (connect) connect(config)
                return@launch
            }
            val profile = runCatching { store.save(name, config, id) }.getOrElse { error ->
                mutableUi.update { it.copy(error = error.message ?: "保存连接失败，请重试") }
                return@launch
            }
            runCatching { store.activate(profile.id) }
            refreshProfiles()
            mutableUi.update {
                it.copy(
                    message = "已保存连接“${profile.name}”",
                    error = "",
                    activeProfileId = profile.id
                )
            }
            if (connect) connect(config)
        }
    }

    /** 删除命名连接及加密凭据；当前已建立的远程会话不受影响，直到用户断开。 */
    fun deleteProfile(id: String) {
        scope.launch {
            val store = profileStore ?: return@launch
            val removed = runCatching { store.list().firstOrNull { it.id == id } }.getOrNull()
            runCatching { store.delete(id) }.getOrElse { error ->
                mutableUi.update { it.copy(error = error.message ?: "删除连接失败，请重试") }
                return@launch
            }
            val identity = removed?.config?.hostIdentity()
            if (identity != null && !store.identityUsedByOthers(identity, id)) {
                // 只有没有其他连接复用同一主机身份时才清理信任指纹，避免误伤。
                runCatching { storage.forgetFingerprint(identity) }
            }
            refreshProfiles()
            mutableUi.update {
                it.copy(
                    message = "已删除连接${removed?.name?.let { name -> "“$name”" } ?: ""}；当前远程会话不受影响",
                    error = ""
                )
            }
        }
    }

    /** 用户核对指纹后继续首次连接；仅在成功认证并探测到 DSH 后写入信任。 */
    fun confirmHostKey() {
        scope.launch { confirmHostKeyNow() }
    }

    internal suspend fun confirmHostKeyNow(): RemoteAttemptResult {
        val value = pending ?: return RemoteAttemptResult.Failure("没有待确认的主机密钥")
        pending = null
        mutableUi.update { it.copy(pendingFingerprint = null, pendingHost = "") }
        return attempt(value.config, value.keepTunnel, value.fingerprint)
    }

    fun cancelHostKey() {
        val value = pending ?: return
        pending = null
        mutableUi.update {
            it.copy(
                pendingFingerprint = null,
                pendingHost = "",
                busy = false,
                failure = null,
                stage = null,
                completedStages = emptySet()
            )
        }
        if (value != null && !value.keepTunnel && machine.remoteActive) {
            // 测试连接不改变活动端点；取消测试必须保留原来的远程 A。
            publish(machine.status, message = "已取消连接测试，继续使用当前远程连接")
            return
        }
        machine.remoteDisconnected("已取消首次主机密钥确认，继续使用本地运行时")
        publish(machine.status, message = machine.status.message)
    }

    fun disconnect() {
        scope.launch { disconnectNow() }
    }

    internal suspend fun disconnectNow() = mutex.withLock {
        closeTunnel()
        val cookieError = switchScope(DshCookieIsolation.LOCAL_SCOPE)
        machine.remoteDisconnected("已断开远程主机，WebView 已切回本地运行时")
        publish(
            machine.status.copy(error = cookieError.orEmpty()),
            message = machine.status.message,
            error = cookieError.orEmpty()
        )
    }

    fun forget() {
        scope.launch { forgetNow() }
    }

    internal suspend fun forgetNow() = mutex.withLock {
        closeTunnel()
        val cookieError = switchScope(DshCookieIsolation.LOCAL_SCOPE)
        val cleared = runCatching { storage.clear() }.isSuccess
        machine.remoteDisconnected(
            if (cleared) "已清除远程主机配置，WebView 已切回本地运行时"
            else "已断开远程主机，但清除本机配置失败；请重试"
        )
        publish(
            machine.status.copy(error = cookieError.orEmpty()),
            message = machine.status.message,
            saved = if (cleared) null else mutableUi.value.saved,
            error = cookieError ?: if (cleared) "" else "远程主机配置清除失败，请重试"
        )
    }

    fun close() {
        watchJob?.cancel()
        watchJob = null
        tunnel?.close()
        tunnel = null
    }

    private fun launchAttempt(config: RemoteHostConfig, keepTunnel: Boolean) {
        if (mutableUi.value.busy) return
        mutableUi.update {
            it.copy(
                busy = true,
                error = "",
                message = "正在校验远程主机配置…",
                pendingFingerprint = null,
                pendingHost = "",
                stage = RemoteConnectionStage.TCP,
                completedStages = emptySet(),
                failure = null,
                attemptSucceeded = false,
                localPort = null
            )
        }
        scope.launch { attempt(config, keepTunnel) }
    }

    internal suspend fun attempt(
        config: RemoteHostConfig,
        keepTunnel: Boolean,
        forcedFingerprint: String? = null
    ): RemoteAttemptResult = mutex.withLock {
        pending = null
        val problem = config.validationError()
        // 配置本身无效时不要拆掉已经连着的远程隧道。
        if (problem != null) return failWith(RemoteConnectionErrors.validation(problem), keepTunnel = false)
        val identity = config.hostIdentity()
            ?: return failWith(RemoteConnectionErrors.validation("远程主机身份无效"), keepTunnel = false)
        val host = config.displayName()

        mutableUi.update {
            it.copy(
                busy = true,
                error = "",
                message = if (config.relay != null) {
                    "正在通过中继 ${config.relay.endpointLabel()} 配对 $host…"
                } else {
                    "正在连接 ${config.user}@$host:${config.sshPort}…"
                },
                pendingFingerprint = null,
                stage = RemoteConnectionStage.TCP,
                completedStages = emptySet(),
                failure = null,
                attemptSucceeded = false,
                localPort = null
            )
        }
        if (keepTunnel) {
            // 先切到本地 Cookie 作用域，再撤销旧远程端点；失败时保留原连接。
            val cookieError = switchScope(DshCookieIsolation.LOCAL_SCOPE)
            if (cookieError != null) {
                return failWith(RemoteConnectionErrors.validation(cookieError), keepTunnel = false)
            }
            closeTunnel()
            machine.remoteConnecting(host)
            onStatus(machine.status)
        }
        try {
            val trusted = forcedFingerprint ?: storage.knownFingerprint(identity)
            if (trusted == null) {
                publishStage(RemoteConnectionStage.TCP)
                val probe = probeHostKey(config)
                pending = PendingConnection(config, keepTunnel, probe.fingerprint)
                mutableUi.update {
                    it.copy(
                        busy = false,
                        pendingFingerprint = probe.fingerprint,
                        pendingHost = host,
                        message = "首次连接 $host，请核对主机密钥指纹后确认",
                        stage = RemoteConnectionStage.SSH_HANDSHAKE,
                        completedStages = setOf(
                            RemoteConnectionStage.TCP,
                            RemoteConnectionStage.SSH_HANDSHAKE
                        )
                    )
                }
                return RemoteAttemptResult.HostKeyConfirmation(probe.fingerprint)
            }
            openAndVerify(config, identity, trusted, keepTunnel)
        } catch (error: Throwable) {
            failWith(RemoteConnectionErrors.from(error, mutableUi.value.stage), keepTunnel, error)
        } finally {
            mutableUi.update { it.copy(busy = false) }
        }
    }

    private suspend fun openAndVerify(
        config: RemoteHostConfig,
        identity: String,
        fingerprint: String,
        keepTunnel: Boolean
    ): RemoteAttemptResult {
        mutableUi.update {
            it.copy(
                message = if (config.relay != null) {
                    "正在通过中继建立 SSH 通道并检测远程 DSH…"
                } else {
                    "正在建立 SSH 隧道并检测远程 DSH…"
                },
                busy = true,
                failure = null,
                stage = RemoteConnectionStage.TCP,
                completedStages = emptySet(),
                attemptSucceeded = false,
                localPort = null
            )
        }
        publishStage(RemoteConnectionStage.TCP)
        var candidate: SshTunnel? = null
        try {
            candidate = openTunnel(config, fingerprint)
            publishStage(RemoteConnectionStage.PORT_FORWARD)
            publishStage(RemoteConnectionStage.DSH_PROBE)
            if (!httpProbe(candidate.localPort)) {
                throw RemoteHostFailure(
                    "远程 DSH 端口未响应；请确认远程 DSH 已启动并监听 127.0.0.1:${config.remoteDshPort}"
                )
            }
            runCatching { storage.trustFingerprint(identity, fingerprint) }.getOrElse {
                throw RemoteHostFailure("主机密钥信任记录无法保存，已取消远程连接")
            }
            if (!keepTunnel) {
                val port = candidate.localPort
                candidate.close()
                candidate = null
                publishStage(RemoteConnectionStage.COMPLETE)
                val message = "连接测试成功：SSH 认证通过，远程 DSH 端口可达"
                mutableUi.update {
                    it.copy(
                        busy = false,
                        error = "",
                        message = message,
                        attemptSucceeded = true,
                        localPort = port,
                        stage = RemoteConnectionStage.COMPLETE,
                        completedStages = RemoteConnectionStage.testStages.toSet(),
                        failure = null
                    )
                }
                return RemoteAttemptResult.Success(message, port)
            }
            runCatching { storage.save(config) }.getOrElse {
                throw RemoteHostFailure("凭据无法加密保存，已取消远程连接")
            }
            val endpoint = config.endpointFor(candidate.localPort)
                ?: throw RemoteHostFailure("本机 SSH 隧道端口无效")
            if (switchScope(config.cookieScope()) != null) {
                throw RemoteHostFailure("无法隔离管理网页 Cookie，已取消远程连接")
            }
            val established = candidate
            closeTunnel()
            tunnel = established
            candidate = null
            machine.remoteConnected(
                endpoint,
                config.displayName(),
                established.localPort,
                fingerprint,
                config.cookieScope()
            )
            publishStage(RemoteConnectionStage.COMPLETE)
            publish(
                machine.status,
                message = machine.status.message,
                saved = config,
                attemptSucceeded = true,
                localPort = established.localPort,
                stage = RemoteConnectionStage.COMPLETE,
                completedStages = RemoteConnectionStage.testStages.toSet()
            )
            watchTunnel(established)
            return RemoteAttemptResult.Success(machine.status.message, established.localPort)
        } catch (error: Throwable) {
            candidate?.close()
            throw error
        }
    }

    private fun watchTunnel(value: SshTunnel) {
        watchJob?.cancel()
        watchJob = scope.launch {
            while (isActive) {
                delay(5_000)
                if (tunnel !== value) return@launch
                if (!value.isAlive()) {
                    mutex.withLock {
                        if (tunnel === value) {
                            closeTunnel()
                            val cookieError = switchScope(DshCookieIsolation.LOCAL_SCOPE)
                            machine.remoteFailed(
                                if (cookieError == null) "SSH 隧道已断开" else "SSH 隧道已断开；$cookieError"
                            )
                            publish(
                                machine.status,
                                message = machine.status.message,
                                error = machine.status.error,
                                failure = RemoteFailureInfo(
                                    stage = null,
                                    kind = RemoteFailureKind.NETWORK,
                                    reason = "SSH 隧道已断开",
                                    suggestion = "已切回本地运行时；检查网络后重新连接远程主机"
                                )
                            )
                        }
                    }
                    return@launch
                }
            }
        }
    }

    private fun failWith(
        info: RemoteFailureInfo,
        keepTunnel: Boolean,
        cause: Throwable? = null
    ): RemoteAttemptResult {
        val message = info.message
        val wasConnecting = machine.status.phase == RemotePhase.CONNECTING || machine.remoteActive
        if (keepTunnel && wasConnecting) {
            closeTunnel()
            val cookieError = switchScope(DshCookieIsolation.LOCAL_SCOPE)
            machine.remoteFailed(if (cookieError == null) message else "$message；$cookieError")
            publish(
                machine.status,
                message = machine.status.message,
                error = machine.status.error,
                failure = info,
                stage = info.stage ?: mutableUi.value.stage,
                completedStages = mutableUi.value.completedStages
            )
        } else {
            mutableUi.update {
                it.copy(
                    busy = false,
                    error = message,
                    message = "",
                    failure = info,
                    stage = if (info.kind == RemoteFailureKind.VALIDATION) null else info.stage ?: it.stage,
                    completedStages = if (info.kind == RemoteFailureKind.VALIDATION) {
                        emptySet()
                    } else {
                        it.completedStages
                    },
                    attemptSucceeded = false
                )
            }
        }
        // cause 仅用于保留异常类型判断，错误文案已由映射器固定，不输出异常原文。
        if (cause is RemoteHostKeyChangedException) {
            mutableUi.update {
                it.copy(
                    error = RemoteConnectionErrors.from(cause, info.stage).message
                )
            }
        }
        return RemoteAttemptResult.Failure(mutableUi.value.error.ifBlank { message })
    }

    private fun switchScope(scope: String): String? =
        runCatching { activateScope(scope) }.exceptionOrNull()?.let { "管理页 Cookie 隔离失败，请重启应用后重试" }

    private fun closeTunnel() {
        watchJob?.cancel()
        watchJob = null
        tunnel?.close()
        tunnel = null
    }

    private fun publish(
        status: RemoteHostStatus,
        message: String,
        saved: RemoteHostConfig? = mutableUi.value.saved,
        error: String = "",
        failure: RemoteFailureInfo? = null,
        stage: RemoteConnectionStage? = null,
        completedStages: Set<RemoteConnectionStage> = emptySet(),
        attemptSucceeded: Boolean = false,
        localPort: Int? = null
    ) {
        mutableUi.update {
            it.copy(
                saved = saved,
                status = status,
                busy = false,
                message = message,
                error = error,
                pendingFingerprint = null,
                pendingHost = "",
                failure = failure,
                stage = stage,
                completedStages = completedStages,
                attemptSucceeded = attemptSucceeded,
                localPort = localPort
            )
        }
        onStatus(status)
    }

    private fun publishStage(stage: RemoteConnectionStage) {
        mutableUi.update { current ->
            val existing = current.stage
            if (existing != null && existing.order > stage.order) {
                current
            } else {
                current.copy(
                    stage = stage,
                    completedStages = current.completedStages +
                        RemoteConnectionStage.entries.filter { it.order < stage.order }
                )
            }
        }
    }

    private suspend fun probeHostKey(config: RemoteHostConfig): HostKeyProbe {
        val staged = transport as? StagedSshTransport
        return if (staged != null) {
            staged.probeHostKey(config) { publishStage(it) }
        } else {
            publishStage(RemoteConnectionStage.TCP)
            transport.probeHostKey(config).also { publishStage(RemoteConnectionStage.SSH_HANDSHAKE) }
        }
    }

    private suspend fun openTunnel(config: RemoteHostConfig, fingerprint: String): SshTunnel {
        val staged = transport as? StagedSshTransport
        return if (staged != null) {
            staged.open(config, fingerprint) { publishStage(it) }
        } else {
            publishStage(RemoteConnectionStage.TCP)
            publishStage(RemoteConnectionStage.SSH_HANDSHAKE)
            publishStage(RemoteConnectionStage.AUTHENTICATION)
            transport.open(config, fingerprint).also { publishStage(RemoteConnectionStage.PORT_FORWARD) }
        }
    }

    private fun refreshProfiles() {
        val store = profileStore ?: return
        val profiles = runCatching { store.list() }.getOrElse { emptyList() }
        val active = runCatching { store.activeId() }.getOrNull()
        mutableUi.update { it.copy(profiles = profiles, activeProfileId = active) }
    }

    /** 存储层异常可能包含路径等本机信息；这里只保留可操作的中文原因。 */
    private fun friendlyStorage(error: Throwable): String =
        error.message?.takeIf { it.startsWith("连接") || it.startsWith("中继") || it.startsWith("已保存") }
            ?: "请重试，或删除本机远程配置后重新添加"

    private data class PendingConnection(
        val config: RemoteHostConfig,
        val keepTunnel: Boolean,
        val fingerprint: String
    )
}
