package com.tanuki.runtime

import android.content.Context

/** 一条可命名的已保存连接；同一时间仍只允许一个活动远程连接。 */
data class RemoteHostProfile(
    val id: String,
    val name: String,
    val config: RemoteHostConfig
) {
    override fun toString(): String = "RemoteHostProfile(id=$id, name=$name, config=$config)"
}

/**
 * 命名连接的本地库。
 *
 * 非敏感字段存应用私有配置，密码/私钥/口令/token 只经 [SecretVault] 加密保存。
 * 每条连接使用稳定的随机 id，名称只用于展示；删除一条不会影响其他连接。
 */
class RemoteHostProfileStore(
    private val config: KeyValueStore,
    private val vault: SecretVault
) {
    /** 读取全部可解析连接；损坏的单条不会让整个列表不可用。 */
    fun list(): List<RemoteHostProfile> = ids().mapNotNull { id ->
        runCatching { load(id) }.getOrNull()
    }

    fun load(id: String): RemoteHostProfile? {
        requireValidId(id)
        val prefix = prefix(id)
        val host = config.getString(prefix + KEY_HOST) ?: return null
        val user = config.getString(prefix + KEY_USER) ?: return null
        val name = config.getString(prefix + KEY_NAME) ?: return null
        val auth = runCatching { RemoteAuthType.valueOf(config.getString(prefix + KEY_AUTH).orEmpty()) }
            .getOrDefault(RemoteAuthType.PRIVATE_KEY)
        val mode = config.getString(prefix + KEY_MODE) ?: MODE_DIRECT
        val relay = if (mode == MODE_RELAY) {
            val relayHost = config.getString(prefix + KEY_RELAY_HOST)
                ?: throw IllegalStateException("连接“$name”的配对信息不完整；请删除后重新添加")
            val relayPort = config.getString(prefix + KEY_RELAY_PORT)?.toIntOrNull()
                ?: throw IllegalStateException("连接“$name”的配对信息不完整；请删除后重新添加")
            val room = config.getString(prefix + KEY_RELAY_ROOM)
                ?: throw IllegalStateException("连接“$name”的配对信息不完整；请删除后重新添加")
            val hostId = config.getString(prefix + KEY_RELAY_HOST_ID)
                ?: throw IllegalStateException("连接“$name”的配对信息不完整；请删除后重新添加")
            val relayToken = runCatching { vault.get(prefix + SECRET_RELAY_TOKEN) }.getOrNull()
                ?: throw IllegalStateException("连接“$name”的配对密钥无法解密；请删除后重新添加")
            RemoteRelayPairing(
                relayHost = relayHost,
                relayPort = relayPort,
                roomId = room,
                token = relayToken,
                hostId = hostId,
                certPin = config.getString(prefix + KEY_RELAY_PIN).orEmpty()
            ).also { pairing ->
                pairing.validationError()?.let {
                    throw IllegalStateException("连接“$name”的配对信息无效：$it")
                }
            }
        } else {
            null
        }
        return RemoteHostProfile(
            id = id,
            name = name,
            config = RemoteHostConfig(
                host = host,
                sshPort = config.getString(prefix + KEY_SSH_PORT)?.toIntOrNull() ?: 22,
                user = user,
                auth = auth,
                password = runCatching { vault.get(prefix + SECRET_PASSWORD) }.getOrNull().orEmpty(),
                privateKey = runCatching { vault.get(prefix + SECRET_PRIVATE_KEY) }.getOrNull().orEmpty(),
                passphrase = runCatching { vault.get(prefix + SECRET_PASSPHRASE) }.getOrNull().orEmpty(),
                remoteDshPort = config.getString(prefix + KEY_REMOTE_DSH_PORT)?.toIntOrNull() ?: 3080,
                token = runCatching { vault.get(prefix + SECRET_TOKEN) }.getOrNull().orEmpty(),
                relay = relay
            )
        )
    }

    /**
     * 新增或编辑连接。
     *
     * @param id 为空表示新增；非空表示编辑已有连接。
     * @return 写入后的连接（包含生成的 id）。
     */
    fun save(name: String, value: RemoteHostConfig, id: String? = null): RemoteHostProfile {
        RemoteWizardValidation.name(name)?.let { throw IllegalArgumentException(it) }
        value.validationError()?.let { throw IllegalArgumentException("连接配置不完整：$it") }
        val trimmedName = name.trim()
        val existing = ids()
        val actualId = if (id == null) {
            if (existing.size >= MAX_PROFILES) throw IllegalArgumentException("最多保存 $MAX_PROFILES 个连接；请先删除不用的连接")
            java.util.UUID.randomUUID().toString()
        } else {
            requireValidId(id)
            if (id !in existing) throw IllegalArgumentException("要编辑的连接不存在；请刷新后重试")
            id
        }
        val prefix = prefix(actualId)

        // 先写凭据，再写非敏感字段，最后把 id 加入索引；读取方不会把半成品当成完整连接。
        vault.put(prefix + SECRET_TOKEN, value.token)
        when (value.auth) {
            RemoteAuthType.PASSWORD -> {
                vault.put(prefix + SECRET_PASSWORD, value.password)
                vault.remove(prefix + SECRET_PRIVATE_KEY)
                vault.remove(prefix + SECRET_PASSPHRASE)
            }
            RemoteAuthType.PRIVATE_KEY -> {
                vault.put(prefix + SECRET_PRIVATE_KEY, value.privateKey)
                vault.put(prefix + SECRET_PASSPHRASE, value.passphrase)
                vault.remove(prefix + SECRET_PASSWORD)
            }
        }
        config.putString(prefix + KEY_NAME, trimmedName)
        config.putString(prefix + KEY_HOST, value.host)
        config.putString(prefix + KEY_SSH_PORT, value.sshPort.toString())
        config.putString(prefix + KEY_USER, value.user)
        config.putString(prefix + KEY_AUTH, value.auth.name)
        config.putString(prefix + KEY_REMOTE_DSH_PORT, value.remoteDshPort.toString())
        val relay = value.relay
        if (relay == null) {
            config.putString(prefix + KEY_MODE, MODE_DIRECT)
            runCatching { vault.remove(prefix + SECRET_RELAY_TOKEN) }
            RELAY_KEYS.forEach { config.remove(prefix + it) }
        } else {
            vault.put(prefix + SECRET_RELAY_TOKEN, relay.token)
            config.putString(prefix + KEY_RELAY_HOST, relay.relayHost)
            config.putString(prefix + KEY_RELAY_PORT, relay.relayPort.toString())
            config.putString(prefix + KEY_RELAY_ROOM, relay.roomId)
            config.putString(prefix + KEY_RELAY_HOST_ID, relay.hostId)
            config.putString(prefix + KEY_RELAY_PIN, relay.certPin)
            config.putString(prefix + KEY_MODE, MODE_RELAY)
        }
        val updated = if (actualId in existing) existing else existing + actualId
        config.putString(KEY_IDS, updated.joinToString(","))
        return RemoteHostProfile(actualId, trimmedName, value)
    }

    /** 删除一条连接及其全部加密凭据；活动标记回退到剩余第一条。 */
    fun delete(id: String) {
        requireValidId(id)
        val wasActive = activeId() == id
        val prefix = prefix(id)
        config.remove(prefix + KEY_NAME)
        config.remove(prefix + KEY_HOST)
        config.remove(prefix + KEY_SSH_PORT)
        config.remove(prefix + KEY_USER)
        config.remove(prefix + KEY_AUTH)
        config.remove(prefix + KEY_REMOTE_DSH_PORT)
        config.remove(prefix + KEY_MODE)
        RELAY_KEYS.forEach { config.remove(prefix + it) }
        SECRET_KEYS.forEach { runCatching { vault.remove(prefix + it) } }
        val remaining = ids().filterNot { it == id }
        if (remaining.isEmpty()) {
            config.remove(KEY_IDS)
        } else {
            config.putString(KEY_IDS, remaining.joinToString(","))
        }
        if (wasActive) {
            if (remaining.isEmpty()) config.remove(KEY_ACTIVE) else config.putString(KEY_ACTIVE, remaining.first())
        }
    }

    fun activeId(): String? {
        val id = config.getString(KEY_ACTIVE) ?: return null
        return id.takeIf { it in ids() }
    }

    fun activate(id: String) {
        requireValidId(id)
        if (id !in ids()) throw IllegalArgumentException("要使用的连接不存在；请刷新后重试")
        config.putString(KEY_ACTIVE, id)
    }

    /** 该连接是否还有别的已保存连接使用同一 SSH 主机身份；删除时据此决定是否清理信任指纹。 */
    fun identityUsedByOthers(identity: String, excludingId: String): Boolean =
        list().any { it.id != excludingId && it.config.hostIdentity() == identity }

    /**
     * 是否还有别的 profile 指向完全相同的服务（主机身份 + 用户 + DSH 端口）。
     * 旧活动副本没有 sourceProfileId 时，用它判断凭据归属；不能复用 known_hosts 的主机身份判断，
     * 因为 bob@host 与 alice@host 共享主机密钥，但不拥有 alice 的密码/私钥。
     */
    fun serviceUsedByOthers(config: RemoteHostConfig, excludingId: String): Boolean =
        list().any { it.id != excludingId && it.config.sameServiceAs(config) }

    private fun ids(): List<String> = config.getString(KEY_IDS)
        .orEmpty()
        .split(',')
        .mapNotNull { raw ->
            val id = raw.trim()
            id.takeIf { it.isNotEmpty() && ID_PATTERN.matches(it) }
        }
        .distinct()

    private fun requireValidId(id: String) {
        require(ID_PATTERN.matches(id)) { "连接标识无效" }
    }

    private fun prefix(id: String): String = "p.$id."

    companion object {
        const val MAX_PROFILES = 50
        private val ID_PATTERN = Regex("[A-Za-z0-9-]{1,64}")
        private const val KEY_IDS = "ids"
        private const val KEY_ACTIVE = "active"
        private const val KEY_NAME = "name"
        private const val KEY_HOST = "host"
        private const val KEY_SSH_PORT = "sshPort"
        private const val KEY_USER = "user"
        private const val KEY_AUTH = "auth"
        private const val KEY_REMOTE_DSH_PORT = "remoteDshPort"
        private const val KEY_MODE = "mode"
        private const val KEY_RELAY_HOST = "relayHost"
        private const val KEY_RELAY_PORT = "relayPort"
        private const val KEY_RELAY_ROOM = "relayRoom"
        private const val KEY_RELAY_HOST_ID = "relayHostId"
        private const val KEY_RELAY_PIN = "relayPin"
        private const val MODE_DIRECT = "direct"
        private const val MODE_RELAY = "relay"
        private const val SECRET_PASSWORD = "password"
        private const val SECRET_PRIVATE_KEY = "privateKey"
        private const val SECRET_PASSPHRASE = "passphrase"
        private const val SECRET_TOKEN = "token"
        private const val SECRET_RELAY_TOKEN = "relayToken"
        private val RELAY_KEYS = listOf(
            KEY_RELAY_HOST,
            KEY_RELAY_PORT,
            KEY_RELAY_ROOM,
            KEY_RELAY_HOST_ID,
            KEY_RELAY_PIN
        )
        private val SECRET_KEYS = listOf(
            SECRET_PASSWORD,
            SECRET_PRIVATE_KEY,
            SECRET_PASSPHRASE,
            SECRET_TOKEN,
            SECRET_RELAY_TOKEN
        )
    }
}

/** 生产环境使用独立的 SharedPreferences 与同一 Android Keystore 密钥。 */
fun androidRemoteHostProfileStore(context: Context): RemoteHostProfileStore = RemoteHostProfileStore(
    SharedPreferencesKeyValueStore(context.getSharedPreferences("remote-host-profiles", Context.MODE_PRIVATE)),
    AndroidKeystoreVault(
        SharedPreferencesKeyValueStore(
            context.getSharedPreferences("remote-host-profile-credentials", Context.MODE_PRIVATE)
        )
    )
)
