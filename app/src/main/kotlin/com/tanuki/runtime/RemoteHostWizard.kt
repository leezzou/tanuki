package com.tanuki.runtime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 连接方式；三种方式最终都落到同一个严格 SSH 校验流程。 */
enum class RemoteConnectionMethod(
    val title: String,
    val summary: String,
    val example: String
) {
    DIRECT_SSH(
        "直连 SSH",
        "远程主机有公网 IP、域名或 IPv6，SSH 端口可直接访问。",
        "主机示例：dev.example.com 或 [2001:db8::10]"
    ),
    PAIRING_LINK(
        "配对链接",
        "双方都没有公网 IP 时，用主机端生成的一次性链接经 TLS 中继连接。",
        "链接形如 tanuki-relay://v1?relay=…（由主机端生成）"
    ),
    LAN(
        "局域网",
        "手机和电脑在同一 Wi-Fi/热点，直接用内网地址连接。",
        "主机示例：192.168.1.20 或 10.0.0.8"
    )
}

/** 向导步骤；顺序不可跳过，指纹步骤只在首次连接或信任记录缺失时出现。 */
enum class RemoteWizardStep(val title: String, val position: Int) {
    METHOD("选择连接方式", 1),
    HOST_ACCOUNT("填写主机与账号", 2),
    AUTHENTICATION("选择认证方式", 3),
    FINGERPRINT("核对主机指纹", 4),
    TEST("测试连接", 5),
    DONE("完成", 6);

    companion object {
        val total: Int = entries.size
    }
}

/** 字段级错误定位；UI 用它把错误显示在对应输入框下方。 */
enum class RemoteWizardField {
    METHOD,
    RELAY_LINK,
    HOST,
    SSH_PORT,
    USER,
    REMOTE_DSH_PORT,
    TOKEN,
    PASSWORD,
    PRIVATE_KEY,
    PASSPHRASE,
    PAIRING_CODE,
    NAME
}

/** 阶段在 UI 中的状态。 */
enum class RemoteStageStatus { PENDING, ACTIVE, DONE, FAILED }

/**
 * 向导中的连接草稿。
 *
 * 密码、私钥和口令只存在于内存草稿中；保存时交给加密存储，不写日志、不导出。
 * 覆盖 toString，防止调试输出带出秘密。
 */
data class RemoteHostDraft(
    val host: String = "",
    val sshPort: String = "22",
    val user: String = "",
    val auth: RemoteAuthType = RemoteAuthType.PRIVATE_KEY,
    val password: String = "",
    val privateKey: String = "",
    val privateKeyFileName: String = "",
    val passphrase: String = "",
    val remoteDshPort: String = "3080",
    val token: String = "",
    val relayUri: String = "",
    /** 电脑端显示的 6 位核对码；与 [relayUri] 一起使用，单独输入无法完成配对。 */
    val pairingCode: String = ""
) {
    override fun toString(): String =
        "RemoteHostDraft(host=$host, sshPort=$sshPort, user=$user, auth=$auth, " +
            "remoteDshPort=$remoteDshPort, relay=${if (relayUri.isBlank()) "<none>" else "<set>"}, " +
            "credentials=<redacted>)"

    companion object {
        fun from(config: RemoteHostConfig): RemoteHostDraft = RemoteHostDraft(
            host = config.host,
            sshPort = config.sshPort.toString(),
            user = config.user,
            auth = config.auth,
            password = config.password,
            privateKey = config.privateKey,
            privateKeyFileName = if (config.privateKey.isBlank()) "" else "已保存的私钥（不显示内容）",
            passphrase = config.passphrase,
            remoteDshPort = config.remoteDshPort.toString(),
            token = config.token,
            relayUri = config.relay?.toUri().orEmpty(),
            pairingCode = config.relay?.let(RemotePairingCode::of).orEmpty()
        )
    }
}

/** 向导完整状态；Compose 与 JVM 测试都只依赖这一份状态。 */
data class RemoteWizardState(
    val step: RemoteWizardStep = RemoteWizardStep.METHOD,
    val method: RemoteConnectionMethod? = null,
    val draft: RemoteHostDraft = RemoteHostDraft(),
    val fieldErrors: Map<RemoteWizardField, String> = emptyMap(),
    val name: String = "",
    val editingProfileId: String? = null,
    val hostKeyFingerprint: String? = null,
    val hostKeyAcknowledged: Boolean = false,
    val stage: RemoteConnectionStage? = null,
    val completedStages: Set<RemoteConnectionStage> = emptySet(),
    val failure: RemoteFailureInfo? = null,
    val busy: Boolean = false,
    val tested: Boolean = false,
    val localPort: Int? = null,
    val notice: String = ""
) {
    val isEditing: Boolean get() = editingProfileId != null

    /** 指纹必须由用户显式勾选确认；没有默认信任。 */
    val canConfirmFingerprint: Boolean
        get() = step == RemoteWizardStep.FINGERPRINT &&
            hostKeyFingerprint != null &&
            hostKeyAcknowledged &&
            !busy
}

/** 字段校验；错误文案包含示例或下一步动作，不直接复述用户输入。 */
object RemoteWizardValidation {
    const val MAX_PROFILE_NAME = 64
    private val USER_PATTERN = Regex("[A-Za-z0-9._-]{1,64}")

    fun host(raw: String): String? {
        val text = raw.trim()
        if (text.isEmpty()) return "请填写主机地址；示例：192.168.1.20 或 dev.example.com"
        if (text.contains("://")) return "只填主机名或 IP，不要带 http://、ssh:// 等协议前缀"
        if (text.contains("/")) return "主机地址不能包含路径；SSH 端口请填在下方端口框"
        if (text.contains("@")) return "主机地址不要包含用户名；用户名请填在账号框"
        if (text.any { it.isWhitespace() || it.isISOControl() }) return "主机地址不能包含空格或控制字符"
        if (text.contains(':') && !text.startsWith("[") && text.count { it == ':' } < 2) {
            return "不要在这里填写端口；IPv6 地址请用方括号，例如 [2001:db8::1]"
        }
        if (RemoteHostAddress.normalize(text) == null) {
            return "主机地址格式无效；示例：192.168.1.20、dev.example.com 或 [2001:db8::1]"
        }
        return null
    }

    fun port(raw: String, label: String, example: String): String? {
        val text = raw.trim()
        if (text.isEmpty()) return "请填写$label；示例：$example"
        val value = text.toIntOrNull() ?: return "$label 必须是数字；示例：$example"
        if (value !in 1..65535) return "$label 需在 1-65535 之间；示例：$example"
        return null
    }

    fun user(raw: String): String? {
        if (raw.isBlank()) return "请填写 SSH 用户名；示例：tanuki 或 ubuntu"
        if (!raw.trim().matches(USER_PATTERN)) return "用户名只能包含字母、数字、点、下划线或短横线（1-64 位）"
        return null
    }

    fun password(raw: String): String? {
        if (raw.isEmpty()) return "请填写 SSH 密码；如果使用私钥，请切换到私钥认证"
        if (raw.length > RemoteHostConfig.MAX_PASSWORD_CHARS) {
            return "SSH 密码过长（上限 ${RemoteHostConfig.MAX_PASSWORD_CHARS} 字符）"
        }
        return null
    }

    fun privateKey(raw: String, fileName: String): String? {
        val text = raw.trim()
        if (text.isEmpty()) {
            return if (fileName.isBlank()) "请选择私钥文件，或粘贴 OpenSSH/PEM 私钥全文" else "私钥文件内容为空，请重新选择"
        }
        if (text.toByteArray(Charsets.UTF_8).size > RemoteHostConfig.MAX_PRIVATE_KEY_BYTES) {
            return "私钥文件过大（上限 ${RemoteHostConfig.MAX_PRIVATE_KEY_BYTES / 1024} KiB）；请选择正确的私钥文件"
        }
        if (!text.contains("-----BEGIN ") || !text.contains("PRIVATE KEY-----") || !text.contains("-----END ")) {
            return "私钥格式无法识别；请选择 OpenSSH 或 PEM 私钥文件（当前支持 RSA/ECDSA）"
        }
        return null
    }

    fun passphrase(raw: String): String? =
        if (raw.length > RemoteHostConfig.MAX_PASSPHRASE_CHARS) {
            "私钥口令过长（上限 ${RemoteHostConfig.MAX_PASSPHRASE_CHARS} 字符）"
        } else {
            null
        }

    fun token(raw: String): String? {
        if (raw.length > RemoteHostConfig.MAX_TOKEN_CHARS) {
            return "远程 DSH token 过长（上限 ${RemoteHostConfig.MAX_TOKEN_CHARS} 字符）"
        }
        if (raw.any { it.isWhitespace() || it.isISOControl() }) return "远程 DSH token 不能包含空白或控制字符"
        return null
    }

    fun relayLink(raw: String): String? {
        val text = raw.trim()
        if (text.isEmpty()) return "请粘贴主机端生成的 tanuki-relay:// 配对链接"
        val error = RemoteRelayPairing.parse(text).exceptionOrNull() ?: return null
        return "配对链接无效：${error.message ?: "格式不符"}；请重新复制完整链接"
    }

    fun name(raw: String): String? {
        val text = raw.trim()
        if (text.isEmpty()) return "请为连接起一个名称；示例：家里电脑、公司开发机"
        if (text.length > MAX_PROFILE_NAME) return "连接名称过长（上限 $MAX_PROFILE_NAME 字符）"
        if (text.any { it.isISOControl() }) return "连接名称不能包含控制字符"
        return null
    }

    /** 当前步骤需要校验的字段；错误只包含字段名与可操作提示。 */
    fun forStep(state: RemoteWizardState): Map<RemoteWizardField, String> {
        val errors = linkedMapOf<RemoteWizardField, String>()
        when (state.step) {
            RemoteWizardStep.METHOD -> {
                if (state.method == null) errors[RemoteWizardField.METHOD] = "请选择一种连接方式后再继续"
            }
            RemoteWizardStep.HOST_ACCOUNT -> validateHostAccount(state, errors)
            RemoteWizardStep.AUTHENTICATION -> validateAuthentication(state, errors)
            RemoteWizardStep.DONE -> name(state.name)?.let { errors[RemoteWizardField.NAME] = it }
            RemoteWizardStep.FINGERPRINT, RemoteWizardStep.TEST -> Unit
        }
        return errors
    }

    fun hostExample(method: RemoteConnectionMethod?): String = when (method) {
        RemoteConnectionMethod.LAN -> "192.168.1.20"
        RemoteConnectionMethod.PAIRING_LINK -> "家里电脑（可留空）"
        else -> "dev.example.com"
    }

    private fun validateHostAccount(
        state: RemoteWizardState,
        errors: MutableMap<RemoteWizardField, String>
    ) {
        val method = state.method
        if (method == null) {
            errors[RemoteWizardField.METHOD] = "请先选择连接方式"
            return
        }
        if (method == RemoteConnectionMethod.PAIRING_LINK) {
            val linkError = relayLink(state.draft.relayUri)
            if (linkError != null) {
                errors[RemoteWizardField.RELAY_LINK] = linkError
            } else {
                val pairing = RemoteRelayPairing.parse(state.draft.relayUri).getOrNull()
                val codeError = RemotePairingCode.validate(state.draft.pairingCode)
                if (codeError != null) {
                    errors[RemoteWizardField.PAIRING_CODE] = codeError
                } else if (pairing != null && !RemotePairingCode.matches(pairing, state.draft.pairingCode)) {
                    errors[RemoteWizardField.PAIRING_CODE] =
                        "配对码与配对链接不一致；请确认两者来自电脑上的同一次配对"
                }
            }
            val displayName = state.draft.host
            if (displayName.length > 128) {
                errors[RemoteWizardField.HOST] = "远程主机展示名过长（上限 128 字符）"
            } else if (displayName.any { it.isISOControl() }) {
                errors[RemoteWizardField.HOST] = "远程主机展示名不能包含控制字符"
            }
        } else {
            host(state.draft.host)?.let { errors[RemoteWizardField.HOST] = it }
        }
        port(state.draft.sshPort, "SSH 端口", "22")?.let { errors[RemoteWizardField.SSH_PORT] = it }
        user(state.draft.user)?.let { errors[RemoteWizardField.USER] = it }
        port(state.draft.remoteDshPort, "远程 DSH 端口", "3080")
            ?.let { errors[RemoteWizardField.REMOTE_DSH_PORT] = it }
        token(state.draft.token)?.let { errors[RemoteWizardField.TOKEN] = it }
    }

    private fun validateAuthentication(
        state: RemoteWizardState,
        errors: MutableMap<RemoteWizardField, String>
    ) {
        when (state.draft.auth) {
            RemoteAuthType.PASSWORD ->
                password(state.draft.password)?.let { errors[RemoteWizardField.PASSWORD] = it }
            RemoteAuthType.PRIVATE_KEY -> {
                privateKey(state.draft.privateKey, state.draft.privateKeyFileName)
                    ?.let { errors[RemoteWizardField.PRIVATE_KEY] = it }
                passphrase(state.draft.passphrase)?.let { errors[RemoteWizardField.PASSPHRASE] = it }
            }
        }
    }
}

/**
 * 向导状态机。
 *
 * 只负责步骤、字段校验、指纹确认和阶段展示；真正的 SSH 连接由 [RemoteHostController] 执行。
 * UI 通过 [next] 推进，通过 [onStage]/[onHostKeyRequired]/[onTestSucceeded]/[onFailure] 接收控制器事件。
 */
class RemoteHostWizard(
    initialConfig: RemoteHostConfig? = null,
    initialName: String = "",
    initialProfileId: String? = null
) {
    private val _state = MutableStateFlow(initialState(initialConfig, initialName, initialProfileId))
    val state: StateFlow<RemoteWizardState> = _state.asStateFlow()

    fun selectMethod(method: RemoteConnectionMethod) {
        _state.update {
            it.copy(
                method = method,
                fieldErrors = it.fieldErrors - RemoteWizardField.METHOD
            )
        }
    }

    fun updateDraft(transform: (RemoteHostDraft) -> RemoteHostDraft) {
        _state.update { it.copy(draft = transform(it.draft), fieldErrors = emptyMap()) }
    }

    fun setAuth(auth: RemoteAuthType) {
        _state.update {
            it.copy(
                draft = it.draft.copy(auth = auth),
                fieldErrors = it.fieldErrors - RemoteWizardField.PASSWORD - RemoteWizardField.PRIVATE_KEY
            )
        }
    }

    fun setName(value: String) {
        _state.update { it.copy(name = value, fieldErrors = it.fieldErrors - RemoteWizardField.NAME) }
    }

    fun acknowledgeHostKey(value: Boolean) {
        _state.update { it.copy(hostKeyAcknowledged = value) }
    }

    /** 推进到下一步；当前步骤校验失败时返回 false 并保留错误。 */
    fun next(): Boolean {
        val current = _state.value
        if (current.busy) return false
        val errors = RemoteWizardValidation.forStep(current)
        if (errors.isNotEmpty()) {
            _state.update { it.copy(fieldErrors = errors) }
            return false
        }
        return when (current.step) {
            RemoteWizardStep.METHOD -> {
                _state.update { it.copy(step = RemoteWizardStep.HOST_ACCOUNT, fieldErrors = emptyMap()) }
                true
            }
            RemoteWizardStep.HOST_ACCOUNT -> {
                _state.update { it.copy(step = RemoteWizardStep.AUTHENTICATION, fieldErrors = emptyMap()) }
                true
            }
            RemoteWizardStep.AUTHENTICATION -> {
                beginTest()
                true
            }
            RemoteWizardStep.TEST -> {
                if (!current.tested) return false
                _state.update { it.copy(step = RemoteWizardStep.DONE, fieldErrors = emptyMap()) }
                true
            }
            RemoteWizardStep.FINGERPRINT, RemoteWizardStep.DONE -> false
        }
    }

    /** 返回上一步；连接进行中或指纹待确认时不擅自回退，避免留下未关闭的尝试。 */
    fun back(): Boolean {
        val current = _state.value
        if (current.busy) return false
        val previous = when (current.step) {
            RemoteWizardStep.METHOD -> return false
            RemoteWizardStep.HOST_ACCOUNT -> RemoteWizardStep.METHOD
            RemoteWizardStep.AUTHENTICATION -> RemoteWizardStep.HOST_ACCOUNT
            RemoteWizardStep.FINGERPRINT, RemoteWizardStep.TEST -> RemoteWizardStep.AUTHENTICATION
            // 完成页不允许退回测试，避免把已通过的状态误当成“待重测”。
            RemoteWizardStep.DONE -> return false
        }
        _state.update {
            it.copy(
                step = previous,
                fieldErrors = emptyMap(),
                failure = if (previous == RemoteWizardStep.AUTHENTICATION) null else it.failure,
                hostKeyFingerprint = if (previous == RemoteWizardStep.AUTHENTICATION) null else it.hostKeyFingerprint,
                hostKeyAcknowledged = false
            )
        }
        return true
    }

    /** 进入测试阶段；控制器随后会收到 test/connect 调用。 */
    fun beginTest() {
        _state.update {
            it.copy(
                step = RemoteWizardStep.TEST,
                busy = true,
                stage = RemoteConnectionStage.TCP,
                completedStages = emptySet(),
                failure = null,
                tested = false,
                localPort = null,
                hostKeyFingerprint = null,
                hostKeyAcknowledged = false,
                notice = ""
            )
        }
    }

    /** 控制器回报阶段；只前进不后退，避免异步回调把进度打乱。 */
    fun onStage(stage: RemoteConnectionStage) {
        _state.update { current ->
            if (current.step != RemoteWizardStep.TEST) return@update current
            if (current.stage != null && current.stage.order > stage.order) return@update current
            current.copy(
                stage = stage,
                completedStages = current.completedStages +
                    RemoteConnectionStage.entries.filter { it.order < stage.order }
            )
        }
    }

    /** 首次连接必须停在指纹步骤，等待用户显式核对。 */
    fun onHostKeyRequired(fingerprint: String) {
        _state.update { current ->
            if (current.step != RemoteWizardStep.TEST) return@update current
            current.copy(
                step = RemoteWizardStep.FINGERPRINT,
                busy = false,
                stage = RemoteConnectionStage.SSH_HANDSHAKE,
                completedStages = combo(RemoteConnectionStage.TCP, RemoteConnectionStage.SSH_HANDSHAKE),
                hostKeyFingerprint = fingerprint,
                hostKeyAcknowledged = false,
                failure = null
            )
        }
    }

    /** 用户勾选“已核对”后才能继续；返回 true 表示可以调用控制器的 confirmHostKey。 */
    fun confirmFingerprint(): Boolean {
        val current = _state.value
        if (!current.canConfirmFingerprint) return false
        _state.update {
            it.copy(
                step = RemoteWizardStep.TEST,
                busy = true,
                hostKeyAcknowledged = false,
                stage = RemoteConnectionStage.TCP,
                completedStages = emptySet(),
                failure = null,
                tested = false,
                localPort = null
            )
        }
        return true
    }

    fun onTestSucceeded(localPort: Int?) {
        _state.update { current ->
            if (current.step != RemoteWizardStep.TEST) return@update current
            current.copy(
                step = RemoteWizardStep.DONE,
                busy = false,
                tested = true,
                localPort = localPort,
                stage = RemoteConnectionStage.COMPLETE,
                completedStages = RemoteConnectionStage.testStages.toSet(),
                failure = null
            )
        }
    }

    fun onFailure(failure: RemoteFailureInfo) {
        _state.update { current ->
            if (current.step != RemoteWizardStep.TEST && current.step != RemoteWizardStep.DONE) {
                return@update current
            }
            current.copy(
                busy = false,
                failure = failure,
                stage = failure.stage ?: current.stage
            )
        }
    }

    /** 重试当前测试；主机密钥变化等不可重试错误不提供该动作。 */
    fun retry(): Boolean {
        val current = _state.value
        if (current.step != RemoteWizardStep.TEST) return false
        if (current.failure?.retryable == false) return false
        beginTest()
        return true
    }

    fun setLocalPort(port: Int?) {
        _state.update { it.copy(localPort = port) }
    }

    fun markSaved(profileId: String) {
        _state.update { it.copy(editingProfileId = profileId, notice = "已保存到“我的连接”") }
    }

    /** 完成页保存前校验名称；失败时把错误挂到名称字段。 */
    fun requireValidName(): Boolean {
        val problem = RemoteWizardValidation.name(_state.value.name)
        return if (problem == null) {
            _state.update { it.copy(fieldErrors = it.fieldErrors - RemoteWizardField.NAME) }
            true
        } else {
            _state.update { it.copy(fieldErrors = it.fieldErrors + (RemoteWizardField.NAME to problem)) }
            false
        }
    }

    /** 文件选择/读取失败等一次性提示；不包含私钥内容。 */
    fun setNotice(text: String) {
        _state.update { it.copy(notice = text) }
    }

    /** 构建连接配置；只有当前步骤校验通过时才返回非空。 */
    fun buildConfig(): RemoteHostConfig? {
        val current = _state.value
        // 连接配置必须同时满足主机/账号与认证两步，避免从测试页构造出半成品配置。
        if (RemoteWizardValidation.forStep(current.copy(step = RemoteWizardStep.HOST_ACCOUNT)).isNotEmpty()) {
            return null
        }
        if (RemoteWizardValidation.forStep(current.copy(step = RemoteWizardStep.AUTHENTICATION)).isNotEmpty()) {
            return null
        }
        val method = current.method ?: return null
        val draft = current.draft
        val relay = if (method == RemoteConnectionMethod.PAIRING_LINK) {
            RemoteRelayPairing.parse(draft.relayUri).getOrNull() ?: return null
        } else {
            null
        }
        val host = if (method == RemoteConnectionMethod.PAIRING_LINK) {
            draft.host.trim()
        } else {
            RemoteHostAddress.normalize(draft.host) ?: return null
        }
        return RemoteHostConfig(
            host = host,
            sshPort = draft.sshPort.trim().toIntOrNull() ?: return null,
            user = draft.user.trim(),
            auth = draft.auth,
            password = draft.password,
            privateKey = draft.privateKey,
            passphrase = draft.passphrase,
            remoteDshPort = draft.remoteDshPort.trim().toIntOrNull() ?: return null,
            token = draft.token,
            relay = relay
        )
    }

    /** 某个阶段当前应显示的图标/颜色状态。 */
    fun stageStatus(stage: RemoteConnectionStage): RemoteStageStatus {
        val current = _state.value
        if (current.failure?.stage == stage) return RemoteStageStatus.FAILED
        if (current.stage == RemoteConnectionStage.COMPLETE) return RemoteStageStatus.DONE
        if (stage in current.completedStages) return RemoteStageStatus.DONE
        if (current.stage == stage) return RemoteStageStatus.ACTIVE
        return RemoteStageStatus.PENDING
    }

    private fun combo(vararg stages: RemoteConnectionStage): Set<RemoteConnectionStage> = stages.toSet()

    private companion object {
        fun initialState(
            initialConfig: RemoteHostConfig?,
            initialName: String,
            initialProfileId: String?
        ): RemoteWizardState {
            val method = initialConfig?.let {
                if (it.relay != null) RemoteConnectionMethod.PAIRING_LINK else RemoteConnectionMethod.DIRECT_SSH
            }
            return RemoteWizardState(
                step = if (initialConfig == null) RemoteWizardStep.METHOD else RemoteWizardStep.HOST_ACCOUNT,
                method = method,
                draft = initialConfig?.let { RemoteHostDraft.from(it) } ?: RemoteHostDraft(),
                name = initialName.ifBlank { initialConfig?.displayName().orEmpty() },
                editingProfileId = initialProfileId
            )
        }
    }
}
