package com.tanuki.runtime

/**
 * 远程连接的分阶段进度。
 *
 * 顺序固定为 TCP -> SSH 握手 -> 用户认证 -> 端口转发 -> DSH 探测 -> 完成；
 * UI 只按这个顺序展示，失败时停在对应阶段，避免把“连接失败”混成一句话。
 */
enum class RemoteConnectionStage(val label: String, val order: Int) {
    TCP("TCP 连接", 0),
    SSH_HANDSHAKE("SSH 握手", 1),
    AUTHENTICATION("用户认证", 2),
    PORT_FORWARD("端口转发", 3),
    DSH_PROBE("DSH 探测", 4),
    COMPLETE("完成", 5);

    companion object {
        /** 测试连接需要展示的五个阶段；完成态单独展示。 */
        val testStages: List<RemoteConnectionStage> = entries.filter { it != COMPLETE }
    }
}

/** 失败分类；用于决定 UI 是否提供重试，以及提示用户先修正哪一类输入。 */
enum class RemoteFailureKind {
    VALIDATION,
    RELAY,
    TCP,
    TIMEOUT,
    SSH_HANDSHAKE,
    AUTHENTICATION,
    HOST_KEY,
    PORT_FORWARD,
    DSH_PROBE,
    NETWORK,
    UNKNOWN
}

/**
 * 可直接展示的失败信息：阶段 + 中文原因 + 下一步建议。
 *
 * 这里只使用固定的中文文案和经过审核的领域异常消息，不回显底层异常文本，
 * 避免把密码、私钥或 token 带进错误提示。
 */
data class RemoteFailureInfo(
    val stage: RemoteConnectionStage?,
    val kind: RemoteFailureKind,
    val reason: String,
    val suggestion: String,
    val retryable: Boolean = true
) {
    val stageLabel: String get() = stage?.label ?: "连接"

    val message: String get() = "$reason；$suggestion"
}

/**
 * 配对失败的中文原因与下一步。
 *
 * 中继错误码先按 [RelayFailureKind] 分流，再给用户一个可以立即执行的动作；
 * 这里只使用固定文案，不回显 token、配对链接或原始对端数据。
 */
object RemotePairingGuidance {
    /** 一条配对失败建议；[reason] 说明发生了什么，[suggestion] 说明下一步做什么。 */
    data class Advice(val reason: String, val suggestion: String)

    fun of(kind: RelayFailureKind): Advice = when (kind) {
        RelayFailureKind.EXPIRED -> Advice(
            reason = "配对链接已过期：中继已不保留这次配对",
            suggestion = "回到电脑上重新生成配对链接，再把新链接复制到手机；旧链接不能继续使用"
        )
        RelayFailureKind.ROOM_NOT_FOUND -> Advice(
            reason = "中继上找不到这个配对房间",
            suggestion = "确认链接来自电脑上正在运行的那次配对；中继重启或链接失效时，请在电脑上重新生成链接"
        )
        RelayFailureKind.HOST_OFFLINE -> Advice(
            reason = "电脑端当前不在线，中继无法把它和手机接起来",
            suggestion = "确认电脑上的 DSH 和远程连接服务仍在运行并已连上中继，然后重试；电脑刚重启时请重新生成链接"
        )
        RelayFailureKind.PIN_MISMATCH -> Advice(
            reason = "中继证书指纹与配对链接不一致，已拒绝连接",
            suggestion = "先不要继续连接：重新从电脑复制最新配对链接；若仍不一致，可能有人在替换中继，请核对电脑上显示的中继证书指纹"
        )
        RelayFailureKind.NETWORK, RelayFailureKind.IO -> Advice(
            reason = "中继端口不可达：手机没能和链接里的中继地址建立连接",
            suggestion = "确认手机当前网络能访问该地址和端口（可切换 Wi-Fi/移动网络重试），并确认中继服务在运行；地址或端口变化后需重新生成链接"
        )
        RelayFailureKind.TLS -> Advice(
            reason = "中继 TLS 握手失败，无法确认中继身份",
            suggestion = "确认中继证书仍有效、手机系统时间正确；使用自签名中继时必须使用带证书指纹的新配对链接"
        )
        RelayFailureKind.AUTH -> Advice(
            reason = "中继拒绝了配对密钥",
            suggestion = "链接里的配对密钥已失效或不属于该房间；请在电脑上重新生成配对链接后重试"
        )
        RelayFailureKind.ROOM_BUSY -> Advice(
            reason = "该配对房间已有另一个客户端在使用",
            suggestion = "先在另一台手机上断开，或等当前连接结束后重试；必要时在电脑上重新生成链接"
        )
        RelayFailureKind.PROTOCOL -> Advice(
            reason = "中继协议不兼容，连接已停止",
            suggestion = "确认手机和电脑上的连接组件版本匹配；更新后再试，仍失败请保留错误阶段信息反馈"
        )
    }
}

/** 把底层异常映射成阶段化中文提示；纯函数，便于 JVM 回归。 */
object RemoteConnectionErrors {
    fun validation(problem: String): RemoteFailureInfo = RemoteFailureInfo(
        stage = null,
        kind = RemoteFailureKind.VALIDATION,
        reason = problem,
        suggestion = "请按上方提示修改后再试"
    )

    fun from(error: Throwable, stage: RemoteConnectionStage?): RemoteFailureInfo {
        // 主机密钥变化优先于普通 SSH 错误：必须明确拒绝，且不提供“直接重试”。
        if (error is RemoteHostKeyChangedException) {
            return RemoteFailureInfo(
                stage = RemoteConnectionStage.SSH_HANDSHAKE,
                kind = RemoteFailureKind.HOST_KEY,
                reason = "远程主机密钥已变化：与已保存记录不一致，已拒绝连接",
                suggestion = "这可能是主机重装，也可能是中间人攻击。请先在远程主机核对新的 SHA256 指纹；确认安全后删除旧连接再重新添加",
                retryable = false
            )
        }
        if (error is RemoteRelayException) {
            val advice = RemotePairingGuidance.of(error.kind)
            return RemoteFailureInfo(
                stage = RemoteConnectionStage.TCP,
                kind = RemoteFailureKind.RELAY,
                reason = advice.reason,
                suggestion = advice.suggestion,
                retryable = error.retryable
            )
        }
        if (error is RemoteHostFailure) {
            val message = error.message.orEmpty()
            if (message.contains("DSH")) {
                return RemoteFailureInfo(
                    stage = RemoteConnectionStage.DSH_PROBE,
                    kind = RemoteFailureKind.DSH_PROBE,
                    reason = message,
                    suggestion = "在远程主机确认 DSH 已启动并监听 127.0.0.1:<远程 DSH 端口>；如端口不同，返回上一步修改端口后重试"
                )
            }
            if (message.contains("主机密钥交换")) {
                return RemoteFailureInfo(
                    stage = RemoteConnectionStage.SSH_HANDSHAKE,
                    kind = RemoteFailureKind.SSH_HANDSHAKE,
                    reason = message,
                    suggestion = "确认远程 sshd 正常运行，并使用 RSA/ECDSA 主机密钥；当前暂不支持 Ed25519 主机密钥"
                )
            }
            if (message.contains("中继")) {
                return RemoteFailureInfo(
                    stage = RemoteConnectionStage.TCP,
                    kind = RemoteFailureKind.RELAY,
                    reason = message,
                    suggestion = "确认中继地址可达、配对链接未过期；必要时重新生成配对链接"
                )
            }
            if (message.contains("凭据") || message.contains("Cookie") || message.contains("信任记录") ||
                message.contains("隧道端口")
            ) {
                // 这些是收尾/本机存储问题，不应误报成 TCP 或 SSH 阶段失败。
                return RemoteFailureInfo(
                    stage = null,
                    kind = RemoteFailureKind.UNKNOWN,
                    reason = message,
                    suggestion = "请重试；若持续失败，重启应用后重新配置，仍失败请保留错误阶段信息反馈"
                )
            }
            return forStage(stage, message)
        }

        val text = (error.message ?: "").lowercase()
        return when {
            error is java.net.UnknownHostException || text.contains("unknownhost") -> RemoteFailureInfo(
                stage = RemoteConnectionStage.TCP,
                kind = RemoteFailureKind.TCP,
                reason = "无法解析主机地址",
                suggestion = "检查主机名拼写，或改用 IP 地址；局域网连接请确认手机和电脑在同一 Wi-Fi/热点"
            )
            error is java.net.SocketTimeoutException || text.contains("timeout") -> RemoteFailureInfo(
                stage = stage ?: RemoteConnectionStage.TCP,
                kind = RemoteFailureKind.TIMEOUT,
                reason = "连接超时：${(stage ?: RemoteConnectionStage.TCP).label}未在限定时间内完成",
                suggestion = "检查网络、主机地址和 SSH 端口是否可达；确认防火墙或安全组已放行后重试"
            )
            error is java.net.ConnectException || text.contains("connection refused") -> RemoteFailureInfo(
                stage = RemoteConnectionStage.TCP,
                kind = RemoteFailureKind.TCP,
                reason = "目标端口拒绝连接",
                suggestion = "确认远程 sshd 已启动、SSH 端口填写正确；局域网连接确认电脑未休眠且防火墙放行"
            )
            text.contains("auth fail") || text.contains("auth cancel") || text.contains("userauth") ||
                text.contains("permission denied") -> RemoteFailureInfo(
                stage = RemoteConnectionStage.AUTHENTICATION,
                kind = RemoteFailureKind.AUTHENTICATION,
                reason = "SSH 用户认证失败",
                suggestion = "核对用户名和密码/私钥；私钥必须与远程 authorized_keys 匹配，带口令的私钥要填写正确口令"
            )
            text.contains("privatekey") || text.contains("invalid key") || text.contains("passphrase") ->
                RemoteFailureInfo(
                    stage = RemoteConnectionStage.AUTHENTICATION,
                    kind = RemoteFailureKind.AUTHENTICATION,
                    reason = "私钥无法读取或口令不正确",
                    suggestion = "重新选择未损坏的 RSA/ECDSA 私钥文件；确认私钥口令正确，且私钥未设置额外权限限制"
                )
            text.contains("hostkey") || text.contains("host key") -> RemoteFailureInfo(
                stage = RemoteConnectionStage.SSH_HANDSHAKE,
                kind = RemoteFailureKind.SSH_HANDSHAKE,
                reason = "远程主机密钥校验失败，已拒绝连接",
                suggestion = "确认远程 sshd 主机密钥正常；不要通过关闭校验来绕过，请先核对指纹"
            )
            text.contains("forward") || text.contains("direct-tcpip") -> RemoteFailureInfo(
                stage = RemoteConnectionStage.PORT_FORWARD,
                kind = RemoteFailureKind.PORT_FORWARD,
                reason = "SSH 已认证，但远程端口转发失败",
                suggestion = "确认远程 DSH 正在监听 127.0.0.1:<远程 DSH 端口>，且 sshd 未禁用 AllowTcpForwarding"
            )
            text.contains("algorithm negotiation") || text.contains("ed25519") || text.contains("eddsa") ||
                text.contains("unsupported") -> RemoteFailureInfo(
                stage = RemoteConnectionStage.SSH_HANDSHAKE,
                kind = RemoteFailureKind.SSH_HANDSHAKE,
                reason = "SSH 算法协商失败",
                suggestion = "当前支持 RSA/ECDSA 主机密钥与私钥；请在远程主机启用 RSA/ECDSA，Ed25519 暂不支持"
            )
            error is java.io.IOException -> forStage(stage, "网络连接中断")
            else -> forStage(stage, "连接未完成")
        }
    }

    private fun forStage(stage: RemoteConnectionStage?, reason: String): RemoteFailureInfo {
        val actual = stage ?: RemoteConnectionStage.TCP
        val kind = when (actual) {
            RemoteConnectionStage.TCP -> RemoteFailureKind.TCP
            RemoteConnectionStage.SSH_HANDSHAKE -> RemoteFailureKind.SSH_HANDSHAKE
            RemoteConnectionStage.AUTHENTICATION -> RemoteFailureKind.AUTHENTICATION
            RemoteConnectionStage.PORT_FORWARD -> RemoteFailureKind.PORT_FORWARD
            RemoteConnectionStage.DSH_PROBE -> RemoteFailureKind.DSH_PROBE
            RemoteConnectionStage.COMPLETE -> RemoteFailureKind.UNKNOWN
        }
        val suggestion = when (actual) {
            RemoteConnectionStage.TCP ->
                "确认主机地址、SSH 端口和网络可达；局域网连接确认手机与电脑在同一网络，防火墙已放行"
            RemoteConnectionStage.SSH_HANDSHAKE ->
                "确认对端端口运行的是 sshd，且主机密钥算法受支持；可先在电脑上执行 ssh -p <端口> <用户>@<主机> 验证"
            RemoteConnectionStage.AUTHENTICATION ->
                "核对用户名与密码/私钥；确认远程账号允许 SSH 登录，私钥已加入 authorized_keys"
            RemoteConnectionStage.PORT_FORWARD ->
                "确认远程 DSH 监听 127.0.0.1:<远程 DSH 端口>，且 sshd 允许 TCP 转发"
            RemoteConnectionStage.DSH_PROBE ->
                "确认远程 DSH 已启动并监听配置的端口；必要时在远程主机查看 DSH 日志"
            RemoteConnectionStage.COMPLETE ->
                "连接已建立但收尾失败，请重试；若持续失败请返回上一步检查配置"
        }
        return RemoteFailureInfo(actual, kind, reason, suggestion)
    }
}
