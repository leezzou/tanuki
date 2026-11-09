package com.tanuki.runtime

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
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
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
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
        return RemoteHostConfig(
            host = host,
            sshPort = config.getString(KEY_SSH_PORT)?.toIntOrNull() ?: 22,
            user = user,
            auth = auth,
            password = runCatching { vault.get(SECRET_PASSWORD) }.getOrNull().orEmpty(),
            privateKey = runCatching { vault.get(SECRET_PRIVATE_KEY) }.getOrNull().orEmpty(),
            passphrase = runCatching { vault.get(SECRET_PASSPHRASE) }.getOrNull().orEmpty(),
            remoteDshPort = config.getString(KEY_REMOTE_DSH_PORT)?.toIntOrNull() ?: 3080,
            token = runCatching { vault.get(SECRET_TOKEN) }.getOrNull().orEmpty()
        )
    }

    fun save(value: RemoteHostConfig) {
        // 先写凭据再写非敏感配置；任一步失败都不会让 load() 看到“有配置但无凭据”的半成品。
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
    }

    fun clear() {
        config.clear()
        knownHosts.clear()
        listOf(SECRET_PASSWORD, SECRET_PRIVATE_KEY, SECRET_PASSPHRASE, SECRET_TOKEN).forEach {
            runCatching { vault.remove(it) }
        }
    }

    fun knownFingerprint(host: String, sshPort: Int): String? =
        knownHosts.getString("$host:$sshPort")

    fun trustFingerprint(host: String, sshPort: Int, fingerprint: String) {
        knownHosts.putString("$host:$sshPort", fingerprint)
    }

    fun forgetFingerprint(host: String, sshPort: Int) {
        knownHosts.remove("$host:$sshPort")
    }

    companion object {
        private const val KEY_HOST = "host"
        private const val KEY_SSH_PORT = "sshPort"
        private const val KEY_USER = "user"
        private const val KEY_AUTH = "auth"
        private const val KEY_REMOTE_DSH_PORT = "remoteDshPort"
        private const val SECRET_PASSWORD = "password"
        private const val SECRET_PRIVATE_KEY = "privateKey"
        private const val SECRET_PASSPHRASE = "passphrase"
        private const val SECRET_TOKEN = "token"
    }
}

fun androidRemoteHostStorage(context: Context): RemoteHostStorage = RemoteHostStorage(
    SharedPreferencesKeyValueStore(context.getSharedPreferences("remote-host", Context.MODE_PRIVATE)),
    SharedPreferencesKeyValueStore(context.getSharedPreferences("remote-host-known-hosts", Context.MODE_PRIVATE)),
    AndroidKeystoreVault(
        SharedPreferencesKeyValueStore(context.getSharedPreferences("remote-host-credentials", Context.MODE_PRIVATE))
    )
)
