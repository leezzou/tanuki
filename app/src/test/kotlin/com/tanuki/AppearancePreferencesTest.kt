package com.tanuki

import com.tanuki.ui.AppearanceMode
import com.tanuki.ui.AppearancePreferenceController
import com.tanuki.ui.AppearancePreferenceStorage
import com.tanuki.ui.AppearanceSaveError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AppearancePreferencesTest {
    private class Storage(var value: String? = null) : AppearancePreferenceStorage {
        var failRead = false
        var failWrite = false
        var writes = 0
        override fun read(): String? {
            if (failRead) throw IOException("synthetic")
            return value
        }
        override fun write(value: String) {
            writes++
            if (failWrite) throw IOException("synthetic")
            this.value = value
        }
    }

    @Test fun absentOrUnknownPreferenceFollowsSystem() {
        for (raw in listOf(null, "", "future-mode")) assertEquals(AppearanceMode.SYSTEM, AppearanceMode.fromStoredValue(raw))
        assertFalse(AppearanceMode.SYSTEM.isDark(false))
        assertTrue(AppearanceMode.SYSTEM.isDark(true))
        for (systemDark in listOf(false, true)) {
            assertFalse(AppearanceMode.LIGHT.isDark(systemDark))
            assertTrue(AppearanceMode.DARK.isDark(systemDark))
        }
    }

    @Test fun savedChoiceSurvivesControllerRecreation() = runBlocking {
        val storage = Storage()
        val first = AppearancePreferenceController(storage)
        first.load()
        first.select(AppearanceMode.DARK)
        assertEquals(AppearanceMode.DARK, first.state.value.mode)
        assertFalse(first.state.value.saving)
        val second = AppearancePreferenceController(storage)
        second.load()
        assertEquals(AppearanceMode.DARK, second.state.value.mode)
        second.select(AppearanceMode.SYSTEM)
        val third = AppearancePreferenceController(storage)
        third.load()
        assertEquals(AppearanceMode.SYSTEM, third.state.value.mode)
    }

    @Test fun failedWriteKeepsEffectiveChoiceAndCanRetry() = runBlocking {
        val storage = Storage(AppearanceMode.LIGHT.storedValue)
        val controller = AppearancePreferenceController(storage)
        controller.load()
        storage.failWrite = true
        controller.select(AppearanceMode.DARK)
        assertEquals(AppearanceMode.LIGHT, controller.state.value.mode)
        assertEquals(AppearanceSaveError.SAVE, controller.state.value.error)
        assertFalse(controller.state.value.saving)
        storage.failWrite = false
        controller.select(AppearanceMode.DARK)
        assertEquals(AppearanceMode.DARK, controller.state.value.mode)
        assertNull(controller.state.value.error)
    }

    @Test fun readFailureDoesNotOverwriteStoredPreferenceAndRetryRecovers() = runBlocking {
        val storage = Storage(AppearanceMode.DARK.storedValue).also { it.failRead = true }
        val controller = AppearancePreferenceController(storage)
        controller.load()
        assertEquals(AppearanceSaveError.LOAD, controller.state.value.error)
        controller.select(AppearanceMode.LIGHT)
        assertEquals(0, storage.writes)
        storage.failRead = false
        controller.load()
        assertEquals(AppearanceMode.DARK, controller.state.value.mode)
        assertNull(controller.state.value.error)
    }

    @Test fun consecutiveSelectionsPreserveRequestedOrderAndLastChoice() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val writes = mutableListOf<String>()
        var stored = AppearanceMode.SYSTEM.storedValue
        val storage = object : AppearancePreferenceStorage {
            override fun read() = stored
            override fun write(value: String) {
                if (writes.isEmpty()) {
                    entered.complete(Unit)
                    check(release.await(30, TimeUnit.SECONDS))
                }
                writes += value
                stored = value
            }
        }
        val controller = AppearancePreferenceController(storage)
        controller.load()
        val first = async(start = CoroutineStart.UNDISPATCHED) { controller.select(AppearanceMode.DARK) }
        val second = try {
            withTimeout(30_000) { entered.await() }
            async(start = CoroutineStart.UNDISPATCHED) { controller.select(AppearanceMode.LIGHT) }
        } finally {
            release.countDown()
        }
        first.await()
        second.await()
        assertEquals(listOf(AppearanceMode.DARK.storedValue, AppearanceMode.LIGHT.storedValue), writes)
        assertEquals(AppearanceMode.LIGHT.storedValue, stored)
        assertEquals(AppearanceMode.LIGHT, controller.state.value.mode)
    }

    @Test fun effectiveThemeChangesOnlyAfterWriteCompletesEvenIfCallerLeaves() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        var stored = AppearanceMode.SYSTEM.storedValue
        val storage = object : AppearancePreferenceStorage {
            override fun read() = stored
            override fun write(value: String) {
                entered.complete(Unit)
                check(release.await(30, TimeUnit.SECONDS))
                stored = value
            }
        }
        val controller = AppearancePreferenceController(storage)
        controller.load()
        val save = async(Dispatchers.Default) { controller.select(AppearanceMode.DARK) }
        try {
            withTimeout(30_000) { entered.await() }
            assertTrue(controller.state.value.saving)
            assertEquals(AppearanceMode.SYSTEM, controller.state.value.mode)
            save.cancel()
        } finally {
            release.countDown()
        }
        save.join()
        assertEquals(AppearanceMode.DARK.storedValue, stored)
        assertEquals(AppearanceMode.DARK, controller.state.value.mode)
        assertFalse(controller.state.value.saving)
    }
}
