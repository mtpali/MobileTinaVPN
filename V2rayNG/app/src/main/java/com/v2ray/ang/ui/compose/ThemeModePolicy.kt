package com.v2ray.ang.ui.compose

import androidx.appcompat.app.AppCompatDelegate

/** One preference controls both the legacy Views and Compose screens. */
internal object ThemeModePolicy {
    fun appCompatMode(preference: String): Int = when (preference) {
        "1" -> AppCompatDelegate.MODE_NIGHT_NO
        "2" -> AppCompatDelegate.MODE_NIGHT_YES
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }

    fun isDark(preference: String, systemDark: Boolean): Boolean = when (appCompatMode(preference)) {
        AppCompatDelegate.MODE_NIGHT_NO -> false
        AppCompatDelegate.MODE_NIGHT_YES -> true
        else -> systemDark
    }
}
