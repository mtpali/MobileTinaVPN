package com.v2ray.ang.ui.perappproxy

/** Initial selection only; this policy never enables per-app routing or bypass mode. */
internal object PerAppDefaults {
    val pinnedPackages = listOf(
        "org.telegram.messenger",
        "com.instagram.android",
        "com.whatsapp",
        "com.android.chrome",
        "com.google.android.googlequicksearchbox",
    )

    fun selectionFor(installedPackages: Collection<String>, saved: Set<String>?): Set<String> {
        if (saved != null) return saved.toSet()
        val installed = installedPackages.toHashSet()
        return pinnedPackages.filterTo(linkedSetOf()) { it in installed }
    }
}
