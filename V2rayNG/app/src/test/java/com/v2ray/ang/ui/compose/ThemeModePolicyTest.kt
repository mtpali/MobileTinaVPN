package com.v2ray.ang.ui.compose

import androidx.appcompat.app.AppCompatDelegate
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ThemeModePolicyTest {
    @Test
    fun lightSelectionOverridesADarkSystemInBothUiEngines() {
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, ThemeModePolicy.appCompatMode("1"))
        assertFalse(ThemeModePolicy.isDark("1", true))
        assertFalse(ThemeModePolicy.isDark("1", false))
    }

    @Test
    fun darkSelectionOverridesALightSystemInBothUiEngines() {
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, ThemeModePolicy.appCompatMode("2"))
        assertTrue(ThemeModePolicy.isDark("2", false))
        assertTrue(ThemeModePolicy.isDark("2", true))
    }

    @Test
    fun autoAndUnknownSavedValuesFollowTheSystem() {
        for (preference in listOf("0", "", "unknown")) {
            assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, ThemeModePolicy.appCompatMode(preference))
            assertTrue(ThemeModePolicy.isDark(preference, true))
            assertFalse(ThemeModePolicy.isDark(preference, false))
        }
    }
}
