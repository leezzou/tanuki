package com.tanuki.runtime

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** 简单键值存储抽象，便于 JVM 测试；生产实现落在应用私有 SharedPreferences。 */
interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
    fun clear()
}

// commit() 的返回值必须检查，不能只用 apply；因此保留同步写入并豁免 UseKtx 风格告警。
@SuppressLint("UseKtx")
class SharedPreferencesKeyValueStore(private val preferences: SharedPreferences) : KeyValueStore {
    override fun getString(key: String): String? = preferences.getString(key, null)

    override fun putString(key: String, value: String) {
        check(preferences.edit().putString(key, value).commit()) { "远程主机设置保存失败" }
    }

    override fun remove(key: String) {
        check(preferences.edit().remove(key).commit()) { "远程主机设置删除失败" }
    }

    override fun clear() {
        check(preferences.edit().clear().commit()) { "远程主机设置清理失败" }
    }
}

/** 凭据存储抽象；get 返回 null 表示不存在或无法解密，不能回退明文。 */
interface SecretVault {
    fun put(name: String, value: String)
    fun get(name: String): String?
    fun remove(name: String)
}

internal object SecretCipher {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    fun encrypt(key: SecretKey, plaintext: String): String {
        // Android Keystore 的 setRandomizedEncryptionRequired(true) 要求由 provider 生成 IV；
        // 调用方显式传 GCMParameterSpec 会抛异常，因此加密后读取 cipher.iv 一并保存。
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        if (iv.size != IV_BYTES) throw IllegalStateException("AES-GCM IV 长度异常")
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val encoder = Base64.getEncoder().withoutPadding()
        return encoder.encodeToString(iv) + "." + encoder.encodeToString(ciphertext)
    }

    fun decrypt(key: SecretKey, stored: String): String? = runCatching {
        val separator = stored.indexOf('.')
        if (separator <= 0 || separator == stored.lastIndex) return null
        val decoder = Base64.getDecoder()
        val iv = decoder.decode(stored.substring(0, separator))
        val ciphertext = decoder.decode(stored.substring(separator + 1))
        if (iv.size != IV_BYTES || ciphertext.isEmpty()) return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }.getOrNull()
}

/** 测试可用的 AES-GCM 凭据存储；生产密钥来自 Android Keystore。 */
class AesGcmSecretVault(private val key: SecretKey, private val backing: KeyValueStore) : SecretVault {
    override fun put(name: String, value: String) = backing.putString(name, SecretCipher.encrypt(key, value))

    override fun get(name: String): String? = backing.getString(name)?.let { SecretCipher.decrypt(key, it) }

    override fun remove(name: String) {
        backing.remove(name)
    }
}

/**
 * 凭据只以 Android Keystore 中的 AES-256-GCM 密钥加密后落盘。
 * Keystore 不可用时直接失败，绝不降级为明文或普通 SharedPreferences。
 */
class AndroidKeystoreVault(private val backing: KeyValueStore) : SecretVault {
    private val key: SecretKey by lazy { loadOrCreateKey() }

    override fun put(name: String, value: String) = backing.putString(name, SecretCipher.encrypt(key, value))

    override fun get(name: String): String? = backing.getString(name)?.let { SecretCipher.decrypt(key, it) }

    override fun remove(name: String) {
        backing.remove(name)
    }

    @Synchronized
    private fun loadOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null)
        if (existing is SecretKey) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "tanuki.remote-host.credentials.v1"
    }
}

/**
 * 远程主机配置持久化：主机/端口/用户名等非敏感字段放私有 SharedPreferences，
 * 密码、私钥、口令和 token 只经 [SecretVault] 加密保存；主机公钥指纹单独保存。
 */
class RemoteHostStorage(
    private val config: KeyValueStore,
    private val knownHosts: KeyValueStore,
    private val vault: SecretVault
) {
    fun load(): RemoteHostConfig? {
        val host = config.getString(KEY_HOST) ?: return null
        val user = config.getString(KEY_USER) ?: return null
        val auth = runCatching { RemoteAuthType.valueOf(config.getString(KEY_AUTH).orEmpty()) }
            .getOrDefault(RemoteAuthType.PRIVATE_KEY)
        val mode = config.getString(KEY_MODE) ?: MODE_DIRECT
        val relay = if (mode == MODE_RELAY) {
            val relayHost = config.getString(KEY_RELAY_HOST)
                ?: throw IllegalStateException("中继配对信息不完整；请清除远程主机配置后重新配对")
            val relayPort = config.getString(KEY_RELAY_PORT)?.toIntOrNull()
                ?: throw IllegalStateException("中继配对信息不完整；请清除远程主机配置后重新配对")
            val room = config.getString(KEY_RELAY_ROOM)
                ?: throw IllegalStateException("中继配对信息不完整；请清除远程主机配置后重新配对")
            val hostId = config.getString(KEY_RELAY_HOST_ID)
                ?: throw IllegalStateException("中继配对信息不完整；请清除远程主机配置后重新配对")
            val relayToken = runCatching { vault.get(SECRET_RELAY_TOKEN) }.getOrNull()
                ?: throw IllegalStateException("中继配对密钥无法解密；请重新配对")
            RemoteRelayPairing(
                relayHost = relayHost,
                relayPort = relayPort,
                roomId = room,
                token = relayToken,
                hostId = hostId,
                certPin = config.getString(KEY_RELAY_PIN).orEmpty()
            ).also { pairing ->
                pairing.validationError()?.let { throw IllegalStateException("中继配对信息无效：$it") }
            }
        } else {
            null
        }
        return RemoteHostConfig(
            host = host,
            sshPort = config.getString(KEY_SSH_PORT)?.toIntOrNull() ?: 22,
            user = user,
            auth = auth,
            password = runCatching { vault.get(SECRET_PASSWORD) }.getOrNull().orEmpty(),
            privateKey = runCatching { vault.get(SECRET_PRIVATE_KEY) }.getOrNull().orEmpty(),
            passphrase = runCatching { vault.get(SECRET_PASSPHRASE) }.getOrNull().orEmpty(),
            remoteDshPort = config.getString(KEY_REMOTE_DSH_PORT)?.toIntOrNull() ?: 3080,
            token = runCatching { vault.get(SECRET_TOKEN) }.getOrNull().orEmpty(),
            relay = relay
        )
    }

    fun save(value: RemoteHostConfig) {
        // 先写凭据再写非敏感配置，最后写 mode 作为提交标记；load() 不会把半成品当成可连接配置。
        vault.put(SECRET_TOKEN, value.token)
        when (value.auth) {
            RemoteAuthType.PASSWORD -> {
                vault.put(SECRET_PASSWORD, value.password)
                vault.remove(SECRET_PRIVATE_KEY)
                vault.remove(SECRET_PASSPHRASE)
            }
            RemoteAuthType.PRIVATE_KEY -> {
                vault.put(SECRET_PRIVATE_KEY, value.privateKey)
                vault.put(SECRET_PASSPHRASE, value.passphrase)
                vault.remove(SECRET_PASSWORD)
            }
        }
        config.putString(KEY_HOST, value.host.trim())
        config.putString(KEY_SSH_PORT, value.sshPort.toString())
        config.putString(KEY_USER, value.user)
        config.putString(KEY_AUTH, value.auth.name)
        config.putString(KEY_REMOTE_DSH_PORT, value.remoteDshPort.toString())
        val relay = value.relay
        if (relay == null) {
            config.putString(KEY_MODE, MODE_DIRECT)
            runCatching { vault.remove(SECRET_RELAY_TOKEN) }
            listOf(KEY_RELAY_HOST, KEY_RELAY_PORT, KEY_RELAY_ROOM, KEY_RELAY_HOST_ID, KEY_RELAY_PIN).forEach {
                config.remove(it)
            }
        } else {
            vault.put(SECRET_RELAY_TOKEN, relay.token)
            config.putString(KEY_RELAY_HOST, relay.relayHost)
            config.putString(KEY_RELAY_PORT, relay.relayPort.toString())
            config.putString(KEY_RELAY_ROOM, relay.roomId)
            config.putString(KEY_RELAY_HOST_ID, relay.hostId)
            config.putString(KEY_RELAY_PIN, relay.certPin)
            config.putString(KEY_MODE, MODE_RELAY)
        }
    }

    fun clear() {
        clearConfig()
        knownHosts.clear()
    }

    /**
     * 只清理活动配置与加密凭据，保留 known_hosts 中其他连接的信任。
     * 删除某条命名连接时使用，避免误删其他主机/连接的指纹。
     */
    fun clearConfig() {
        config.clear()
        listOf(SECRET_PASSWORD, SECRET_PRIVATE_KEY, SECRET_PASSPHRASE, SECRET_TOKEN, SECRET_RELAY_TOKEN).forEach {
            runCatching { vault.remove(it) }
        }
    }

    /** 连接身份由 [RemoteHostConfig.hostIdentity] 提供；直连为 host:port，中继为 relay:hostId。 */
    fun knownFingerprint(identity: String): String? = knownHosts.getString(identity)

    fun trustFingerprint(identity: String, fingerprint: String) {
        knownHosts.putString(identity, fingerprint)
    }

    fun forgetFingerprint(identity: String) {
        knownHosts.remove(identity)
    }

    fun knownFingerprint(host: String, sshPort: Int): String? = knownFingerprint("$host:$sshPort")

    fun trustFingerprint(host: String, sshPort: Int, fingerprint: String) =
        trustFingerprint("$host:$sshPort", fingerprint)

    fun forgetFingerprint(host: String, sshPort: Int) = forgetFingerprint("$host:$sshPort")

    companion object {
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
    }
}

fun androidRemoteHostStorage(context: Context): RemoteHostStorage = RemoteHostStorage(
    SharedPreferencesKeyValueStore(context.getSharedPreferences("remote-host", Context.MODE_PRIVATE)),
    SharedPreferencesKeyValueStore(context.getSharedPreferences("remote-host-known-hosts", Context.MODE_PRIVATE)),
    AndroidKeystoreVault(
        SharedPreferencesKeyValueStore(context.getSharedPreferences("remote-host-credentials", Context.MODE_PRIVATE))
    )
)
