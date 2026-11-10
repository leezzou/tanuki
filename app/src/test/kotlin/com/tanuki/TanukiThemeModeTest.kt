package com.tanuki

import com.tanuki.ui.TanukiThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TanukiThemeModeTest {
    @Test fun parseRecognizesStoredValues() {
        assertEquals(TanukiThemeMode.SYSTEM, TanukiThemeMode.parse("system"))
        assertEquals(TanukiThemeMode.LIGHT, TanukiThemeMode.parse("light"))
        assertEquals(TanukiThemeMode.DARK, TanukiThemeMode.parse("dark"))
    }

    @Test fun unknownOrMissingPreferenceFallsBackToSystem() {
        assertEquals(TanukiThemeMode.SYSTEM, TanukiThemeMode.parse(null))
        assertEquals(TanukiThemeMode.SYSTEM, TanukiThemeMode.parse(""))
        assertEquals(TanukiThemeMode.SYSTEM, TanukiThemeMode.parse("sepia"))
    }

    @Test fun resolveUsesSystemOnlyForSystemMode() {
        assertTrue(TanukiThemeMode.SYSTEM.isDark(systemDark = true))
        assertFalse(TanukiThemeMode.SYSTEM.isDark(systemDark = false))
        assertFalse(TanukiThemeMode.LIGHT.isDark(systemDark = true))
        assertTrue(TanukiThemeMode.DARK.isDark(systemDark = false))
    }

    @Test fun storageValuesRemainStable() {
        assertEquals("system", TanukiThemeMode.SYSTEM.storageValue)
        assertEquals("light", TanukiThemeMode.LIGHT.storageValue)
        assertEquals("dark", TanukiThemeMode.DARK.storageValue)
    }
}
