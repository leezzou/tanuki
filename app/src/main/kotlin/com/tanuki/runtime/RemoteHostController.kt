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
import java.io.IOException

/** 远程主机设置面板状态；saved 只保存在进程内，落盘由加密存储负责。 */
data class RemoteHostUiState(
    val saved: RemoteHostConfig? = null,
    val status: RemoteHostStatus = RemoteHostStatus(),
    val busy: Boolean = false,
    val message: String = "",
    val error: String = "",
    val pendingFingerprint: String? = null,
    val pendingHost: String = ""
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
 */
class RemoteHostController(
    private val storage: RemoteHostStorage,
    private val transport: SshTransport,
    private val scope: CoroutineScope,
    private val machine: RemoteHostMachine,
    private val onStatus: (RemoteHostStatus) -> Unit = {},
    private val httpProbe: (Int) -> Boolean = { RemoteHttpProbe.probe(it) },
    /** 在切换管理页端点前交换 Cookie 容器；失败时拒绝远程连接/回退。 */
    private val activateScope: (String) -> Unit = {}
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
                mutableUi.update { it.copy(error = "已保存的远程配置无法读取：${friendly(error)}") }
                null
            }
            mutableUi.update { it.copy(saved = saved) }
        }
    }

    fun test(config: RemoteHostConfig) = launchAttempt(config, keepTunnel = false)

    fun connect(config: RemoteHostConfig) = launchAttempt(config, keepTunnel = true)

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
        val value = pending
        pending = null
        mutableUi.update { it.copy(pendingFingerprint = null, pendingHost = "") }
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
        mutableUi.update { it.copy(busy = true, error = "", message = "正在校验远程主机配置…", pendingFingerprint = null, pendingHost = "") }
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
        if (problem != null) return fail(problem, keepTunnel = false)
        val host = config.normalizedHost() ?: return fail("远程主机格式无效", keepTunnel = false)

        mutableUi.update {
            it.copy(busy = true, error = "", message = "正在连接 ${config.user}@$host:${config.sshPort}…", pendingFingerprint = null)
        }
        if (keepTunnel) {
            // 先切到本地 Cookie 作用域，再撤销旧远程端点；失败时保留原连接。
            val cookieError = switchScope(DshCookieIsolation.LOCAL_SCOPE)
            if (cookieError != null) {
                return fail(cookieError, keepTunnel = false)
            }
            closeTunnel()
            machine.remoteConnecting(config.host.trim())
            onStatus(machine.status)
        }
        try {
            val trusted = forcedFingerprint ?: storage.knownFingerprint(host, config.sshPort)
            if (trusted == null) {
                val probe = transport.probeHostKey(config)
                pending = PendingConnection(config, keepTunnel, probe.fingerprint)
                mutableUi.update {
                    it.copy(
                        busy = false,
                        pendingFingerprint = probe.fingerprint,
                        pendingHost = host,
                        message = "首次连接 $host，请核对主机密钥指纹后确认"
                    )
                }
                return RemoteAttemptResult.HostKeyConfirmation(probe.fingerprint)
            }
            openAndVerify(config, host, trusted, keepTunnel)
        } catch (error: Throwable) {
            fail(friendly(error), keepTunnel, error)
        } finally {
            mutableUi.update { it.copy(busy = false) }
        }
    }

    private suspend fun openAndVerify(
        config: RemoteHostConfig,
        host: String,
        fingerprint: String,
        keepTunnel: Boolean
    ): RemoteAttemptResult {
        mutableUi.update { it.copy(message = "正在建立 SSH 隧道并检测远程 DSH…") }
        var candidate: SshTunnel? = null
        try {
            candidate = transport.open(config, fingerprint)
            if (!httpProbe(candidate.localPort)) {
                throw RemoteHostFailure("远程 DSH 端口未响应；请确认远程 DSH 已启动并监听 127.0.0.1:${config.remoteDshPort}")
            }
            runCatching { storage.trustFingerprint(host, config.sshPort, fingerprint) }.getOrElse {
                throw RemoteHostFailure("主机密钥信任记录无法保存，已取消远程连接")
            }
            if (!keepTunnel) {
                candidate.close()
                candidate = null
                val message = "连接测试成功：SSH 认证通过，远程 DSH 端口可达"
                mutableUi.update { it.copy(busy = false, error = "", message = message) }
                return RemoteAttemptResult.Success(message)
            }
            runCatching { storage.save(config) }.getOrElse {
                throw RemoteHostFailure("凭据无法加密保存，已取消远程连接")
            }
            val endpoint = config.endpointFor(candidate.localPort)
                ?: throw RemoteHostFailure("本机 SSH 隧道端口无效")
            if (keepTunnel && switchScope(config.cookieScope()) != null) {
                throw RemoteHostFailure("无法隔离管理网页 Cookie，已取消远程连接")
            }
            val established = candidate
            closeTunnel()
            tunnel = established
            candidate = null
            machine.remoteConnected(
                endpoint,
                config.host.trim(),
                established.localPort,
                fingerprint,
                config.cookieScope()
            )
            publish(
                machine.status,
                message = machine.status.message,
                saved = config,
                error = ""
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
                            publish(machine.status, message = machine.status.message, error = machine.status.error)
                        }
                    }
                    return@launch
                }
            }
        }
    }

    private fun fail(message: String, keepTunnel: Boolean, cause: Throwable? = null): RemoteAttemptResult {
        val wasConnecting = machine.status.phase == RemotePhase.CONNECTING || machine.remoteActive
        if (keepTunnel && wasConnecting) {
            closeTunnel()
            val cookieError = switchScope(DshCookieIsolation.LOCAL_SCOPE)
            machine.remoteFailed(if (cookieError == null) message else "$message；$cookieError")
            publish(
                machine.status,
                message = machine.status.message,
                error = machine.status.error
            )
        } else {
            mutableUi.update { it.copy(busy = false, error = message, message = "") }
        }
        if (cause is RemoteHostKeyChangedException) {
            mutableUi.update {
                it.copy(
                    error = "远程主机密钥已变化，已拒绝连接；如确认主机重装，请清除远程主机配置后重试"
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
        error: String = ""
    ) {
        mutableUi.update {
            it.copy(
                saved = saved,
                status = status,
                busy = false,
                message = message,
                error = error,
                pendingFingerprint = null,
                pendingHost = ""
            )
        }
        onStatus(status)
    }

    private data class PendingConnection(
        val config: RemoteHostConfig,
        val keepTunnel: Boolean,
        val fingerprint: String
    )

    private fun friendly(error: Throwable): String {
        if (error is RemoteHostFailure) return error.message ?: "远程主机连接失败"
        if (error is RemoteHostKeyChangedException) {
            return "远程主机密钥已变化（新指纹 ${error.actualFingerprint}），已拒绝连接；如确认主机重装，请清除远程主机配置后重试"
        }
        val text = (error.message ?: "").lowercase()
        return when {
            error is java.net.UnknownHostException || text.contains("unknownhost") ->
                "无法解析远程主机地址"
            error is java.net.SocketTimeoutException || text.contains("timeout") ->
                "SSH 连接超时；请检查主机、端口、网络或防火墙"
            error is java.net.ConnectException || text.contains("connection refused") ->
                "无法连接远程 SSH 端口；请确认 sshd 已启动且端口正确"
            text.contains("auth fail") || text.contains("auth cancel") || text.contains("userauth") ->
                "SSH 认证失败；请检查用户名与密码/私钥是否正确"
            text.contains("privatekey") || text.contains("invalid key") || text.contains("passphrase") ->
                "私钥无法读取；请检查私钥格式和口令"
            text.contains("hostkey") || text.contains("host key") ->
                "远程主机密钥校验失败，已拒绝连接"
            text.contains("forward") ->
                "SSH 隧道端口转发失败；请确认远程 DSH 端口正确"
            text.contains("algorithm negotiation") || text.contains("ed25519") ||
                text.contains("eddsa") || text.contains("unsupported") ->
                "SSH 算法协商失败；当前切片仅支持 RSA/ECDSA 主机密钥与私钥，Ed25519 待后续"
            error is IOException ->
                "SSH 连接失败；请检查网络后重试"
            else ->
                "远程主机连接失败；请检查配置后重试"
        }
    }
}
