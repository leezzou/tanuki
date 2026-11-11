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

/**
 * 向导步骤；顺序不可跳过，指纹步骤只在首次连接或信任记录缺失时出现。
 *
 * [PAIRING_CONFIRM] 只用于配对链接方式：解析出链接后先让用户确认“这是你的电脑”，
 * 再进入认证；直连/局域网不会出现这一步。
 */
enum class RemoteWizardStep(val title: String) {
    METHOD("选择连接方式"),
    HOST_ACCOUNT("填写主机与账号"),
    PAIRING_CONFIRM("确认这台电脑"),
    AUTHENTICATION("选择认证方式"),
    FINGERPRINT("核对主机指纹"),
    TEST("测试连接"),
    DONE("完成");

    /** 配对方式把主机/账号步骤显示为链接步骤；文案不改变步骤顺序。 */
    fun titleFor(method: RemoteConnectionMethod?): String =
        if (method == RemoteConnectionMethod.PAIRING_LINK && this == HOST_ACCOUNT) {
            "读取配对链接"
        } else {
            title
        }

    companion object {
        /** 当前连接方式实际会走到的步骤；配对方式多出确认页。 */
        fun forMethod(method: RemoteConnectionMethod?): List<RemoteWizardStep> =
            if (method == RemoteConnectionMethod.PAIRING_LINK) {
                entries.toList()
            } else {
                entries.filter { it != PAIRING_CONFIRM }
            }
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

/** 打开配对链接步骤时自动读取剪贴板的结果；UI 只按它显示一行说明。 */
enum class PairingClipboardStatus {
    /** 还没读取过剪贴板。 */
    IDLE,

    /** 剪贴板里是有效配对链接，已自动填入并算出 6 位码。 */
    AUTO_FILLED,

    /** 剪贴板里是 tanuki-relay:// 开头的链接，但解析失败；已填入原文供修改。 */
    INVALID_LINK,

    /** 剪贴板有内容但不是配对链接；不覆盖粘贴框，保留手动粘贴。 */
    NOT_A_LINK,

    /** 剪贴板为空或系统不允许读取；保留粘贴框。 */
    EMPTY,

    /** 已有手动粘贴/编辑的链接，不能悄悄覆盖；提供“改用剪贴板链接”。 */
    KEPT_EXISTING
}

/**
 * 配对确认页的只读展示模型。
 *
 * 从草稿里的链接解析而来，不包含 token；[code] 由链接现场计算，
 * 与电脑端显示值不一致时说明两次配对不是同一次。
 */
data class RemotePairingPreview(
    val pairing: RemoteRelayPairing,
    val code: String,
    val computerName: String,
    val computerNameSource: String,
    val hostIdShort: String,
    val relayLabel: String,
    val pin: String,
    val pinSummary: String,
    val sshUser: String,
    val sshPort: Int,
    val remoteDshPort: Int
) {
    /** 证书指纹是否固定；没有固定时依赖系统 CA，界面必须明确说明。 */
    val pinned: Boolean get() = pin.isNotEmpty()

    val sshHint: String get() = "$sshUser@127.0.0.1:$sshPort（经中继转发）"

    val dshHint: String get() = "127.0.0.1:$remoteDshPort"

    companion object {
        const val UNNAMED_SOURCE = "链接未包含电脑名，可返回上一步填写展示名"

        fun of(draft: RemoteHostDraft): RemotePairingPreview? {
            val pairing = RemoteRelayPairing.parse(draft.relayUri).getOrNull() ?: return null
            val name = draft.host.trim()
            val pin = pairing.certPin
            return RemotePairingPreview(
                pairing = pairing,
                code = RemotePairingCode.of(pairing),
                computerName = name.ifBlank { "未命名电脑（${pairing.hostId.take(8)}）" },
                computerNameSource = if (name.isBlank()) UNNAMED_SOURCE else "上一步填写的展示名",
                hostIdShort = pairing.hostId.take(8),
                relayLabel = pairing.endpointLabel(),
                pin = pin,
                pinSummary = if (pin.isBlank()) {
                    "系统 CA（链接未固定证书）"
                } else {
                    RelayTlsPin.abbreviate(pin)
                },
                sshUser = draft.user.trim().ifBlank { "<用户名>" },
                sshPort = draft.sshPort.trim().toIntOrNull()?.takeIf { it in 1..65535 } ?: 22,
                remoteDshPort = draft.remoteDshPort.trim().toIntOrNull()?.takeIf { it in 1..65535 } ?: 3080
            )
        }
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
    val notice: String = "",
    /** 用户已在确认页点过“这是你的电脑”；链接变化或重新进入时清零。 */
    val pairingAcknowledged: Boolean = false,
    val clipboardStatus: PairingClipboardStatus = PairingClipboardStatus.IDLE,
    val clipboardMessage: String = "",
    /** 每次进入配对链接步骤加一；UI 用它触发一次剪贴板读取，并丢弃迟到结果。 */
    val clipboardVisit: Int = 0,
    /** 剪贴板里有新链接但用户已手动编辑时暂存，等用户显式选择是否替换。 */
    val pendingClipboardLink: String = "",
    /** 上一次由本机自动填入的链接原文；只允许自动覆盖自己填的内容。 */
    val autoFilledLink: String? = null
) {
    val isEditing: Boolean get() = editingProfileId != null

    val steps: List<RemoteWizardStep> get() = RemoteWizardStep.forMethod(method)

    /** 当前步骤在本次连接方式可见步骤中的序号（从 1 开始）。 */
    val stepNumber: Int get() = (steps.indexOf(step) + 1).coerceAtLeast(1)

    val stepTotal: Int get() = steps.size

    /** 配对链接解析成功后的确认页数据；其他方式或链接无效时为 null。 */
    val pairingPreview: RemotePairingPreview?
        get() = if (method == RemoteConnectionMethod.PAIRING_LINK) {
            RemotePairingPreview.of(draft)
        } else {
            null
        }

    /** 指纹必须由用户显式勾选确认；没有默认信任。 */
    val canConfirmFingerprint: Boolean
        get() = step == RemoteWizardStep.FINGERPRINT &&
            hostKeyFingerprint != null &&
            hostKeyAcknowledged &&
            !busy

    /** 确认页只有在链接可解析、字段无错误时才能继续。 */
    val canConfirmPairing: Boolean
        get() = step == RemoteWizardStep.PAIRING_CONFIRM && pairingPreview != null && !busy
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
            RemoteWizardStep.PAIRING_CONFIRM -> validateHostAccount(state, errors)
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
                // 配对码已由手机按链接自动算出；只有用户主动校对时才校验输入值。
                if (pairing != null && state.draft.pairingCode.isNotBlank()) {
                    val codeError = RemotePairingCode.validate(state.draft.pairingCode)
                    if (codeError != null) {
                        errors[RemoteWizardField.PAIRING_CODE] = codeError
                    } else if (!RemotePairingCode.matches(pairing, state.draft.pairingCode)) {
                        val expected = RemotePairingCode.of(pairing)
                        errors[RemoteWizardField.PAIRING_CODE] =
                            "配对码与配对链接不一致；本链接自动算出的是 $expected。" +
                                "请确认你对照的是电脑上同一次配对，或点“填入自动码”修正"
                    }
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

    /** 私钥读取请求版本；步骤/认证/导航变化或新读取都会让旧回调失效。 */
    private var keyReadRequest = 0

    fun selectMethod(method: RemoteConnectionMethod) {
        _state.update {
            if (it.method == method) {
                it.copy(fieldErrors = it.fieldErrors - RemoteWizardField.METHOD)
            } else {
                it.copy(
                    method = method,
                    fieldErrors = it.fieldErrors - RemoteWizardField.METHOD,
                    pairingAcknowledged = false
                )
            }
        }
    }

    fun updateDraft(transform: (RemoteHostDraft) -> RemoteHostDraft) {
        _state.update {
            it.copy(
                draft = transform(it.draft),
                fieldErrors = emptyMap(),
                pairingAcknowledged = false
            )
        }
    }

    /**
     * 用户手动编辑/粘贴配对链接。
     *
     * 链接能解析时立即算出 6 位码写回草稿；这既是展示值，也让“下一步”不必要求手输。
     * 链接被改坏时清空旧码，避免旧码和新链接看起来仍然一致。
     */
    fun updateRelayLink(value: String) {
        val parsed = RemoteRelayPairing.parse(value).getOrNull()
        _state.update { current ->
            current.copy(
                draft = current.draft.copy(
                    relayUri = value,
                    pairingCode = parsed?.let(RemotePairingCode::of).orEmpty()
                ),
                fieldErrors = current.fieldErrors -
                    RemoteWizardField.RELAY_LINK - RemoteWizardField.PAIRING_CODE,
                pairingAcknowledged = false,
                clipboardStatus = PairingClipboardStatus.IDLE,
                clipboardMessage = "",
                pendingClipboardLink = "",
                autoFilledLink = null,
                tested = false
            )
        }
    }

    /**
     * 应用一次剪贴板读取结果。
     *
     * [visit] 必须与当前 [RemoteWizardState.clipboardVisit] 一致，避免用户已返回/离开后
     * 迟到的剪贴板结果覆盖新的草稿。剪贴板不是配对链接时只记录状态，绝不改粘贴框。
     *
     * @return true 表示已填入一条有效配对链接并算出了码。
     */
    fun applyClipboardPairing(text: String?, visit: Int): Boolean {
        val current = _state.value
        if (current.step != RemoteWizardStep.HOST_ACCOUNT) return false
        if (current.method != RemoteConnectionMethod.PAIRING_LINK) return false
        if (visit != current.clipboardVisit) return false

        val raw = text.orEmpty().trim()
        if (raw.isEmpty()) {
            _state.update { it.copy(clipboardStatus = PairingClipboardStatus.EMPTY, clipboardMessage = "") }
            return false
        }
        if (!raw.startsWith(RemoteRelayPairing.URI_PREFIX)) {
            _state.update {
                it.copy(
                    clipboardStatus = PairingClipboardStatus.NOT_A_LINK,
                    clipboardMessage = "剪贴板内容不是配对链接，已保留下面的粘贴框；也可以手动粘贴。",
                    pendingClipboardLink = ""
                )
            }
            return false
        }

        val result = RemoteRelayPairing.parse(raw)
        val parsed = result.getOrNull()
        if (parsed == null) {
            val reason = result.exceptionOrNull()?.message ?: "格式不符"
            _state.update {
                it.copy(
                    draft = it.draft.copy(relayUri = raw, pairingCode = ""),
                    fieldErrors = it.fieldErrors + (
                        RemoteWizardField.RELAY_LINK to
                            "剪贴板里的配对链接无法解析：$reason；请重新复制电脑上的完整链接，或手动粘贴"
                        ),
                    pairingAcknowledged = false,
                    clipboardStatus = PairingClipboardStatus.INVALID_LINK,
                    clipboardMessage = "已读取剪贴板，但链接不可用；请重新复制后再试。",
                    pendingClipboardLink = "",
                    autoFilledLink = null,
                    tested = false
                )
            }
            return false
        }

        // 只覆盖空白框或本机上一次自动填入的内容；用户手动编辑的链接先保留并给出替换入口。
        val existing = current.draft.relayUri.trim()
        if (existing == raw) {
            _state.update {
                it.copy(
                    draft = it.draft.copy(pairingCode = RemotePairingCode.of(parsed)),
                    fieldErrors = it.fieldErrors -
                        RemoteWizardField.RELAY_LINK - RemoteWizardField.PAIRING_CODE,
                    pairingAcknowledged = false,
                    clipboardStatus = PairingClipboardStatus.AUTO_FILLED,
                    clipboardMessage = "剪贴板里的配对链接与当前内容一致，已确认 6 位码。",
                    pendingClipboardLink = "",
                    autoFilledLink = raw
                )
            }
            return true
        }
        val canReplace = existing.isEmpty() || existing == current.autoFilledLink
        if (!canReplace) {
            _state.update {
                it.copy(
                    clipboardStatus = PairingClipboardStatus.KEPT_EXISTING,
                    clipboardMessage = "检测到新的配对链接，但当前粘贴框已有你编辑的内容，未自动替换。",
                    pendingClipboardLink = raw
                )
            }
            return false
        }

        _state.update {
            it.copy(
                draft = it.draft.copy(
                    relayUri = raw,
                    pairingCode = RemotePairingCode.of(parsed)
                ),
                fieldErrors = it.fieldErrors -
                    RemoteWizardField.RELAY_LINK - RemoteWizardField.PAIRING_CODE,
                pairingAcknowledged = false,
                clipboardStatus = PairingClipboardStatus.AUTO_FILLED,
                clipboardMessage = "已从剪贴板读取配对链接，并自动算出 6 位码。",
                pendingClipboardLink = "",
                autoFilledLink = raw,
                tested = false
            )
        }
        return true
    }

    /** 用户确认改用剪贴板里暂存的链接；没有暂存内容时返回 false。 */
    fun usePendingClipboardLink(): Boolean {
        val current = _state.value
        val pending = current.pendingClipboardLink
        if (pending.isBlank()) return false
        val parsed = RemoteRelayPairing.parse(pending).getOrNull() ?: return false
        _state.update {
            it.copy(
                draft = it.draft.copy(relayUri = pending, pairingCode = RemotePairingCode.of(parsed)),
                fieldErrors = it.fieldErrors -
                    RemoteWizardField.RELAY_LINK - RemoteWizardField.PAIRING_CODE,
                pairingAcknowledged = false,
                clipboardStatus = PairingClipboardStatus.AUTO_FILLED,
                clipboardMessage = "已改用剪贴板里的配对链接，并自动算出 6 位码。",
                pendingClipboardLink = "",
                autoFilledLink = pending
            )
        }
        return true
    }

    /** 把自动计算的码填回校对框；用户手输错误后可用它恢复一致。 */
    fun fillAutoPairingCode(): Boolean {
        val current = _state.value
        val pairing = RemoteRelayPairing.parse(current.draft.relayUri).getOrNull() ?: return false
        _state.update {
            it.copy(
                draft = it.draft.copy(pairingCode = RemotePairingCode.of(pairing)),
                fieldErrors = it.fieldErrors - RemoteWizardField.PAIRING_CODE
            )
        }
        return true
    }

    fun setAuth(auth: RemoteAuthType) {
        if (_state.value.draft.auth != auth) keyReadRequest++
        _state.update {
            it.copy(
                draft = it.draft.copy(auth = auth),
                fieldErrors = it.fieldErrors - RemoteWizardField.PASSWORD - RemoteWizardField.PRIVATE_KEY
            )
        }
    }

    /** 开始一次异步私钥读取；返回的版本号必须原样交回 [applyPrivateKeyRead]。 */
    fun beginPrivateKeyRead(): Int {
        keyReadRequest++
        return keyReadRequest
    }

    /**
     * 应用私钥读取结果。
     *
     * 只有请求仍是最新、当前仍在认证步骤且仍选择私钥时才写入；
     * 否则丢弃迟到结果，避免把 TEST/DONE 中已测试的草稿改成未测试的私钥。
     */
    fun applyPrivateKeyRead(request: Int, fileName: String, privateKey: String): Boolean {
        val current = _state.value
        if (request != keyReadRequest) return false
        if (current.step != RemoteWizardStep.AUTHENTICATION) return false
        if (current.draft.auth != RemoteAuthType.PRIVATE_KEY) return false
        _state.update {
            it.copy(
                draft = it.draft.copy(privateKey = privateKey, privateKeyFileName = fileName),
                fieldErrors = emptyMap(),
                tested = false,
                failure = null
            )
        }
        return true
    }

    /** 用户放弃/切换读取时让回调失效。 */
    fun cancelPrivateKeyRead() {
        keyReadRequest++
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
        keyReadRequest++
        val errors = RemoteWizardValidation.forStep(current)
        if (errors.isNotEmpty()) {
            _state.update { it.copy(fieldErrors = errors) }
            return false
        }
        return when (current.step) {
            RemoteWizardStep.METHOD -> {
                val pairing = current.method == RemoteConnectionMethod.PAIRING_LINK
                _state.update {
                    it.copy(
                        step = RemoteWizardStep.HOST_ACCOUNT,
                        fieldErrors = emptyMap(),
                        clipboardVisit = if (pairing) it.clipboardVisit + 1 else it.clipboardVisit
                    )
                }
                true
            }
            RemoteWizardStep.HOST_ACCOUNT -> {
                val next = if (current.method == RemoteConnectionMethod.PAIRING_LINK) {
                    RemoteWizardStep.PAIRING_CONFIRM
                } else {
                    RemoteWizardStep.AUTHENTICATION
                }
                _state.update { it.copy(step = next, fieldErrors = emptyMap()) }
                true
            }
            RemoteWizardStep.PAIRING_CONFIRM -> {
                _state.update {
                    it.copy(
                        step = RemoteWizardStep.AUTHENTICATION,
                        pairingAcknowledged = true,
                        fieldErrors = emptyMap()
                    )
                }
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

    /** 确认页主按钮：用户点“这是你的电脑”后进入认证步骤。 */
    fun confirmPairing(): Boolean {
        if (_state.value.step != RemoteWizardStep.PAIRING_CONFIRM) return false
        return next()
    }

    /** 返回上一步；连接进行中或指纹待确认时不擅自回退，避免留下未关闭的尝试。 */
    fun back(): Boolean {
        val current = _state.value
        if (current.busy) return false
        keyReadRequest++
        val previous = when (current.step) {
            RemoteWizardStep.METHOD -> return false
            RemoteWizardStep.HOST_ACCOUNT -> RemoteWizardStep.METHOD
            RemoteWizardStep.PAIRING_CONFIRM -> RemoteWizardStep.HOST_ACCOUNT
            RemoteWizardStep.AUTHENTICATION ->
                if (current.method == RemoteConnectionMethod.PAIRING_LINK) {
                    RemoteWizardStep.PAIRING_CONFIRM
                } else {
                    RemoteWizardStep.HOST_ACCOUNT
                }
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
                hostKeyAcknowledged = false,
                pairingAcknowledged = if (previous == RemoteWizardStep.HOST_ACCOUNT) false else it.pairingAcknowledged,
                // 回到链接步骤时重新读一次剪贴板；已编辑内容由 applyClipboardPairing 保护。
                clipboardVisit = if (previous == RemoteWizardStep.HOST_ACCOUNT &&
                    it.method == RemoteConnectionMethod.PAIRING_LINK
                ) {
                    it.clipboardVisit + 1
                } else {
                    it.clipboardVisit
                }
            )
        }
        return true
    }

    /** 进入测试阶段；控制器随后会收到 test/connect 调用。 */
    fun beginTest() {
        keyReadRequest++
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
                editingProfileId = initialProfileId,
                // 编辑已有配对时也在进入链接步骤后读一次剪贴板；已有内容不会被覆盖。
                clipboardVisit = if (method == RemoteConnectionMethod.PAIRING_LINK) 1 else 0
            )
        }
    }
}
