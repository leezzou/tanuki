package com.tanuki.runtime

import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import com.jcraft.jsch.UserInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties
import java.util.concurrent.atomic.AtomicReference

data class HostKeyProbe(val fingerprint: String)

/** 已保存的 known_hosts 指纹与本次主机公钥不一致。 */
class RemoteHostKeyChangedException(val actualFingerprint: String) : Exception("远程主机密钥已变化")

/** 远程 DSH 端口通过隧道不可达等可展示给用户的中文失败。 */
class RemoteHostFailure(message: String) : Exception(message)

interface SshTunnel {
    val localPort: Int
    fun isAlive(): Boolean
    fun close()
}

/**
 * SSH 连接抽象。JVM 回归使用假实现，不启动 sshd；
 * 生产实现见 [JschSshTransport]。
 */
interface SshTransport {
    /** 仅用于首次连接：在不发送认证信息的前提下取得主机公钥指纹。 */
    suspend fun probeHostKey(config: RemoteHostConfig): HostKeyProbe

    /** 使用已确认指纹连接并建立到远程 loopback DSH 端口的本地转发。 */
    suspend fun open(config: RemoteHostConfig, expectedFingerprint: String): SshTunnel
}

/**
 * 基于 JSch 的直连 SSH 实现。
 *
 * - 只连接配置中的 SSH 主机/端口，不做中继；
 * - 主机密钥必须与已确认指纹一致，首次确认由 UI 完成；
 * - 本地转发绑定 127.0.0.1 的随机端口，远程 DSH 仍只看到 loopback；
 * - 私钥/密码只传给 JSch，不写日志。
 */
class JschSshTransport(private val timeoutMs: Int = 15_000) : SshTransport {
    override suspend fun probeHostKey(config: RemoteHostConfig): HostKeyProbe = withContext(Dispatchers.IO) {
        val session = newSession(JSch(), config)
        val observed = AtomicReference<String>()
        session.hostKeyRepository = recordingRepository(observed)
        try {
            // 未确认主机时 StrictHostKeyChecking=yes 会在认证前拒绝连接。
            session.connect(timeoutMs)
        } catch (_: JSchException) {
            // 预期失败；只要拿到了主机公钥就交给用户确认。
        } finally {
            runCatching { session.disconnect() }
        }
        val fingerprint = observed.get()
            ?: throw RemoteHostFailure("未获得远程主机密钥；请确认远程 SSH 服务可用")
        HostKeyProbe(fingerprint)
    }

    override suspend fun open(config: RemoteHostConfig, expectedFingerprint: String): SshTunnel = withContext(Dispatchers.IO) {
        val jsch = JSch()
        val session = newSession(jsch, config)
        val repository = checkingRepository(expectedFingerprint)
        session.hostKeyRepository = repository
        if (config.auth == RemoteAuthType.PRIVATE_KEY) {
            val passphrase = config.passphrase.ifBlank { null }?.toByteArray(Charsets.UTF_8)
            jsch.addIdentity(
                "tanuki-remote",
                config.privateKey.toByteArray(Charsets.UTF_8),
                null,
                passphrase
            )
        } else {
            session.setPassword(config.password.toByteArray(Charsets.UTF_8))
        }
        try {
            session.connect(timeoutMs)
            val localPort = session.setPortForwardingL(0, "127.0.0.1", config.remoteDshPort)
            JschTunnel(session, localPort)
        } catch (error: JSchException) {
            runCatching { session.disconnect() }
            if (repository.changed) throw RemoteHostKeyChangedException(repository.actualFingerprint)
            throw error
        } catch (error: Throwable) {
            runCatching { session.disconnect() }
            throw error
        }
    }

    private fun newSession(jsch: JSch, config: RemoteHostConfig): Session {
        val host = config.normalizedHost() ?: throw RemoteHostFailure("远程主机格式无效")
        val session = jsch.getSession(config.user, host, config.sshPort)
        session.setConfig(
            Properties().apply {
                setProperty("StrictHostKeyChecking", "yes")
                setProperty(
                    "PreferredAuthentications",
                    if (config.auth == RemoteAuthType.PRIVATE_KEY) "publickey" else "password,keyboard-interactive"
                )
                // Android 没有 Java 15+ 的 Ed25519 JCE 实现；本切片明确只支持 RSA/ECDSA，
                // 避免协商到无法验证的主机密钥算法。Ed25519 需要额外 BouncyCastle，后续切片处理。
                setProperty(
                    "server_host_key",
                    "rsa-sha2-512,rsa-sha2-256,ssh-rsa,ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,ecdsa-sha2-nistp521"
                )
            }
        )
        session.setTimeout(timeoutMs)
        session.setServerAliveInterval(15_000)
        session.setServerAliveCountMax(3)
        return session
    }

    private fun recordingRepository(observed: AtomicReference<String>): HostKeyRepository =
        object : HostKeyRepository {
            override fun check(host: String, key: ByteArray): Int {
                observed.compareAndSet(null, HostKeyFingerprint.of(key))
                return HostKeyRepository.NOT_INCLUDED
            }

            override fun add(hostkey: HostKey, ui: UserInfo?) = Unit
            override fun remove(host: String, type: String) = Unit
            override fun remove(host: String, type: String, key: ByteArray?) = Unit
            override fun getKnownHostsRepositoryID(): String = "tanuki-remote"
            override fun getHostKey(): Array<HostKey> = emptyArray()
            override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
        }

    private fun checkingRepository(expectedFingerprint: String): CheckingHostKeyRepository =
        CheckingHostKeyRepository(expectedFingerprint)

    private class CheckingHostKeyRepository(private val expected: String) : HostKeyRepository {
        @Volatile var changed: Boolean = false
        @Volatile var actualFingerprint: String = ""

        override fun check(host: String, key: ByteArray): Int {
            val actual = HostKeyFingerprint.of(key)
            actualFingerprint = actual
            if (actual == expected) return HostKeyRepository.OK
            changed = true
            return HostKeyRepository.CHANGED
        }

        override fun add(hostkey: HostKey, ui: UserInfo?) = Unit
        override fun remove(host: String, type: String) = Unit
        override fun remove(host: String, type: String, key: ByteArray?) = Unit
        override fun getKnownHostsRepositoryID(): String = "tanuki-remote"
        override fun getHostKey(): Array<HostKey> = emptyArray()
        override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
    }
}

private class JschTunnel(private val session: Session, override val localPort: Int) : SshTunnel {
    @Volatile private var closed = false

    override fun isAlive(): Boolean = !closed && session.isConnected

    override fun close() {
        if (closed) return
        closed = true
        runCatching { session.disconnect() }
    }
}

/** 经本地转发检查远程 DSH 是否响应；只访问 127.0.0.1，不直接访问远程主机。 */
object RemoteHttpProbe {
    fun probe(localPort: Int, timeoutMs: Int = 5_000): Boolean = runCatching {
        val connection = java.net.URL("http://127.0.0.1:$localPort/").openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
        connection.instanceFollowRedirects = false
        connection.useCaches = false
        try {
            val code = connection.responseCode
            code in 200..399 || code == 401
        } finally {
            connection.disconnect()
        }
    }.getOrDefault(false)
}
