package com.tanuki.runtime

import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Base64

/** 远程主机认证方式；密码与私钥都只保存在加密存储中。 */
enum class RemoteAuthType { PASSWORD, PRIVATE_KEY }

/**
 * 远程 DSH 主机配置。
 *
 * 这里只描述直连 SSH + 本地端口转发所需的最小字段；中继/WebRTC 不在此配置内。
 * 覆盖 toString，避免日志或异常把密码、私钥、口令和 token 带出去。
 */
data class RemoteHostConfig(
    val host: String = "",
    val sshPort: Int = 22,
    val user: String = "",
    val auth: RemoteAuthType = RemoteAuthType.PRIVATE_KEY,
    val password: String = "",
    val privateKey: String = "",
    val passphrase: String = "",
    val remoteDshPort: Int = 3080,
    val token: String = ""
) {
    /** 管理页 Cookie 的逻辑主机身份；同一 SSH/DSH 端口的不同用户也不共享认证 Cookie。 */
    fun cookieScope(): String = "remote:${normalizedHost() ?: host.trim()}:$sshPort:$user:$remoteDshPort"

    /**
     * 规范化主机名：IPv4/域名返回小写形式，方括号 IPv6 去掉方括号。
     * 不允许协议、路径、端口或 userinfo，防止配置被拼成另一个 URL/主机。
     */
    fun normalizedHost(): String? {
        val value = host.trim()
        if (value.isEmpty()) return null
        if (value.startsWith("[") && value.endsWith("]")) {
            val inner = value.substring(1, value.length - 1)
            if (inner.length !in 2..45) return null
            if (!inner.matches(Regex("[0-9A-Fa-f:.]+")) || !inner.contains(':')) return null
            if (inner.contains(":::")) return null
            return inner.lowercase()
        }
        if (value.length > 253) return null
        if (!value.matches(Regex("[A-Za-z0-9](?:[A-Za-z0-9._-]{0,251}[A-Za-z0-9])?"))) return null
        if (value.contains("..")) return null
        return value.lowercase()
    }

    /** 返回中文校验错误；成功返回 null。错误信息只描述字段，不回显字段值。 */
    fun validationError(): String? {
        if (normalizedHost() == null) {
            return "远程主机格式无效；请填写 IPv4、IPv6 或域名，不要包含协议、路径或端口"
        }
        if (sshPort !in 1..65535) return "SSH 端口必须在 1-65535 之间"
        if (!user.matches(Regex("[A-Za-z0-9._-]{1,64}"))) return "SSH 用户名格式无效（1-64 位字母、数字、点、下划线或短横线）"
        if (remoteDshPort !in 1..65535) return "远程 DSH 端口必须在 1-65535 之间"
        when (auth) {
            RemoteAuthType.PASSWORD -> {
                if (password.isEmpty()) return "请填写 SSH 密码"
                if (password.length > MAX_PASSWORD_CHARS) return "SSH 密码过长（上限 $MAX_PASSWORD_CHARS 字符）"
            }
            RemoteAuthType.PRIVATE_KEY -> {
                val key = privateKey.trim()
                if (key.isEmpty()) return "请粘贴 SSH 私钥"
                if (key.toByteArray(Charsets.UTF_8).size > MAX_PRIVATE_KEY_BYTES) {
                    return "私钥文件过大（上限 ${MAX_PRIVATE_KEY_BYTES / 1024} KiB）"
                }
                if (!key.contains("-----BEGIN ") || !key.contains("PRIVATE KEY-----") || !key.contains("-----END ")) {
                    return "私钥格式无效；请粘贴 OpenSSH 或 PEM 私钥全文"
                }
                if (passphrase.length > MAX_PASSPHRASE_CHARS) return "私钥口令过长（上限 $MAX_PASSPHRASE_CHARS 字符）"
            }
        }
        if (token.length > MAX_TOKEN_CHARS) return "远程 DSH token 过长（上限 $MAX_TOKEN_CHARS 字符）"
        if (token.any { it.isWhitespace() || it.isISOControl() }) return "远程 DSH token 不能包含空白或控制字符"
        return null
    }

    /** 连接身份用于 known_hosts 查询；使用规范化主机和 SSH 端口。 */
    fun hostIdentity(): String? {
        val host = normalizedHost() ?: return null
        return "$host:$sshPort"
    }

    fun endpointFor(localPort: Int): RemoteEndpoint? = RemoteEndpoint.loopback(localPort, token)

    override fun toString(): String =
        "RemoteHostConfig(host=$host, sshPort=$sshPort, user=$user, auth=$auth, " +
            "remoteDshPort=$remoteDshPort, credentials=<redacted>, token=<redacted>)"

    companion object {
        const val MAX_PASSWORD_CHARS = 1024
        const val MAX_PASSPHRASE_CHARS = 1024
        const val MAX_PRIVATE_KEY_BYTES = 64 * 1024
        const val MAX_TOKEN_CHARS = 4096
    }
}

/**
 * 管理 WebView 使用的精确 origin 与带一次性 token 的启动 URL。
 * 远程模式也只暴露本机 loopback 地址，远程 token 只通过 SSH 隧道传输。
 */
data class RemoteEndpoint(val origin: String, val authenticatedUrl: String) {
    override fun toString(): String = "RemoteEndpoint(origin=$origin, authenticatedUrl=<redacted>)"

    companion object {
        fun loopback(localPort: Int, token: String): RemoteEndpoint? {
            if (localPort !in 1..65535) return null
            val origin = "http://127.0.0.1:$localPort"
            if (token.isEmpty()) return RemoteEndpoint(origin, "$origin/")
            val encoded = runCatching { URLEncoder.encode(token, "UTF-8") }.getOrNull() ?: return null
            return RemoteEndpoint(origin, "$origin/?token=$encoded")
        }
    }
}

/** 主机公钥指纹（SHA-256，OpenSSH 展示格式），不是私钥内容。 */
object HostKeyFingerprint {
    fun of(rawKey: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(rawKey)
        return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest)
    }
}

enum class RemotePhase { LOCAL, CONNECTING, REMOTE, FALLBACK }

/** 远程主机连接状态；FALLBACK 表示失败后已回退本地。 */
data class RemoteHostStatus(
    val phase: RemotePhase = RemotePhase.LOCAL,
    val host: String = "",
    val localPort: Int? = null,
    val fingerprint: String = "",
    val message: String = "",
    val error: String = "",
    val cookieScope: String = DshCookieIsolation.LOCAL_SCOPE
) {
    val connected: Boolean get() = phase == RemotePhase.REMOTE
}

/**
 * origin 切换状态机：本地与远程各自保存端点，远程优先；远程断开后自动恢复本地。
 * 纯 Kotlin，便于 JVM 回归，不直接接触 WebView 或 SSH。
 */
class RemoteHostMachine {
    private var localEndpoint: RemoteEndpoint? = null
    private var remoteEndpoint: RemoteEndpoint? = null
    private var current = RemoteHostStatus()

    @get:Synchronized
    val status: RemoteHostStatus get() = current

    @get:Synchronized
    val origin: String? get() = remoteEndpoint?.origin ?: localEndpoint?.origin

    @get:Synchronized
    val authenticatedUrl: String? get() = remoteEndpoint?.authenticatedUrl ?: localEndpoint?.authenticatedUrl

    @get:Synchronized
    val localOrigin: String? get() = localEndpoint?.origin

    @get:Synchronized
    val localAuthenticatedUrl: String? get() = localEndpoint?.authenticatedUrl

    @get:Synchronized
    val remoteActive: Boolean get() = remoteEndpoint != null

    /** 本地 DSH 就绪，保存为回退端点；远程已连接时不覆盖当前 origin。 */
    @Synchronized
    fun localConnected(origin: String, authenticatedUrl: String) {
        localEndpoint = RemoteEndpoint(origin, authenticatedUrl)
        if (remoteEndpoint == null && current.phase != RemotePhase.FALLBACK) {
            current = current.copy(
                phase = RemotePhase.LOCAL,
                host = "",
                localPort = null,
                fingerprint = "",
                message = "本地运行时",
                error = "",
                cookieScope = DshCookieIsolation.LOCAL_SCOPE
            )
        }
    }

    /** 本地 DSH 停止或启动前清理；远程仍连接时保持远程 origin。 */
    @Synchronized
    fun localCleared() {
        localEndpoint = null
        if (remoteEndpoint == null) {
            current = current.copy(
                phase = RemotePhase.LOCAL,
                host = "",
                localPort = null,
                fingerprint = "",
                message = "本地运行时未启动",
                error = "",
                cookieScope = DshCookieIsolation.LOCAL_SCOPE
            )
        }
    }

    @Synchronized
    fun remoteConnecting(host: String) {
        // 新连接会替换旧远程端点；失败后统一回退本地，避免 UI 与实际隧道不一致。
        remoteEndpoint = null
        current = current.copy(
            phase = RemotePhase.CONNECTING,
            host = host,
            localPort = null,
            fingerprint = "",
            message = "正在建立 SSH 隧道并检测远程 DSH…",
            error = "",
            cookieScope = DshCookieIsolation.LOCAL_SCOPE
        )
    }

    @Synchronized
    fun remoteConnected(
        endpoint: RemoteEndpoint,
        host: String,
        localPort: Int,
        fingerprint: String,
        cookieScope: String = "remote:$host"
    ) {
        remoteEndpoint = endpoint
        current = current.copy(
            phase = RemotePhase.REMOTE,
            host = host,
            localPort = localPort,
            fingerprint = fingerprint,
            message = "已通过 SSH 隧道连接远程主机 $host",
            error = "",
            cookieScope = cookieScope
        )
    }

    /** 连接失败或隧道断开：关闭远程端点并回退到本地（若存在）。 */
    @Synchronized
    fun remoteFailed(message: String) {
        remoteEndpoint = null
        current = current.copy(
            phase = RemotePhase.FALLBACK,
            localPort = null,
            message = "已回退本地运行时：$message",
            error = message,
            cookieScope = DshCookieIsolation.LOCAL_SCOPE
        )
    }

    @Synchronized
    fun remoteDisconnected(message: String) {
        remoteEndpoint = null
        current = current.copy(
            phase = RemotePhase.LOCAL,
            host = "",
            localPort = null,
            fingerprint = "",
            message = message,
            error = "",
            cookieScope = DshCookieIsolation.LOCAL_SCOPE
        )
    }
}
