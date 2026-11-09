package com.tanuki

import android.webkit.CookieManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tanuki.runtime.DshCookieIsolation
import com.tanuki.runtime.SharedPreferencesKeyValueStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class DshCookieIsolationTest {
    @Test
    fun twoSyntheticHostsNeverReceiveEachOthersAuthCookie() = runBlocking(Dispatchers.IO) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".workspacecheck"))
        val prefs = context.getSharedPreferences("dsh-cookie-isolation-${System.nanoTime()}", 0)
        val isolation = DshCookieIsolation(CookieManager.getInstance(), SharedPreferencesKeyValueStore(prefs))
        val manager = CookieManager.getInstance()
        try {
            isolation.switchTo("host-a")
            manager.setCookie("http://127.0.0.1:41001/", "authA=synthetic-a; Path=/; HttpOnly")
            manager.flush()

            isolation.switchTo("host-b")
            assertTrue(manager.getCookie("http://127.0.0.1:41002/").isNullOrEmpty())
            manager.setCookie("http://127.0.0.1:41002/", "authB=synthetic-b; Path=/; HttpOnly")
            manager.flush()

            isolation.switchTo("host-a")
            val a = manager.getCookie("http://127.0.0.1:41001/").orEmpty()
            assertTrue(a.contains("authA=synthetic-a"))
            assertFalse(a.contains("authB=synthetic-b"))

            isolation.switchTo("host-b")
            val b = manager.getCookie("http://127.0.0.1:41002/").orEmpty()
            assertTrue(b.contains("authB=synthetic-b"))
            assertFalse(b.contains("authA=synthetic-a"))
            assertEquals("host-b", isolation.currentScope())
        } finally {
            val latch = CountDownLatch(1)
            manager.removeAllCookies { latch.countDown() }
            latch.await(3, TimeUnit.SECONDS)
            manager.flush()
            prefs.edit().clear().commit()
        }
    }
}
