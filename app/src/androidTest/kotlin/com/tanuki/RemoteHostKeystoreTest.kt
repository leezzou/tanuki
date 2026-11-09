package com.tanuki

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tanuki.runtime.AndroidKeystoreVault
import com.tanuki.runtime.SharedPreferencesKeyValueStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RemoteHostKeystoreTest {
    @Test
    fun androidKeystoreUsesProviderGeneratedIvAndRoundTrips() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".workspacecheck"))
        val prefs = context.getSharedPreferences("remote-keystore-${System.nanoTime()}", 0)
        val vault = AndroidKeystoreVault(SharedPreferencesKeyValueStore(prefs))
        val token = "synthetic-token-${System.nanoTime()}"
        try {
            // 生产密钥 setRandomizedEncryptionRequired(true)；显式传 IV 会在这里触发 Keystore 异常。
            vault.put("token", token)
            val stored = prefs.all.values.filterIsInstance<String>().single()
            assertFalse(stored.contains(token))
            assertEquals(token, vault.get("token"))
            prefs.edit().putString("token", "tampered").commit()
            assertNull(vault.get("token"))
        } finally {
            prefs.edit().clear().commit()
        }
    }
}
