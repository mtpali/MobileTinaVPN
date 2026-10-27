package com.v2ray.ang.ui.perappproxy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PerAppDefaultsTest {
    @Test fun firstLoadChecksAllFiveInstalledPinnedAppsOnly() {
        val installed = listOf("com.example.notes", "com.whatsapp", "com.instagram.android", "org.telegram.messenger", "com.google.android.googlequicksearchbox", "com.android.chrome")
        assertEquals(linkedSetOf("org.telegram.messenger", "com.instagram.android", "com.whatsapp", "com.android.chrome", "com.google.android.googlequicksearchbox"), PerAppDefaults.selectionFor(installed, null))
    }

    @Test fun absentPinnedAppsAreNotAddedToInitialSelection() {
        assertEquals(setOf("org.telegram.messenger", "com.android.chrome"), PerAppDefaults.selectionFor(listOf("com.example.notes", "org.telegram.messenger", "com.android.chrome"), null))
    }

    @Test fun savedEmptySelectionStaysEmptyAfterReopening() {
        assertEquals(emptySet<String>(), PerAppDefaults.selectionFor(listOf("org.telegram.messenger", "com.whatsapp"), emptySet()))
    }

    @Test fun existingCustomSelectionIsPreserved() {
        assertEquals(setOf("com.example.notes"), PerAppDefaults.selectionFor(listOf("com.example.notes", "org.telegram.messenger"), setOf("com.example.notes")))
    }
}
