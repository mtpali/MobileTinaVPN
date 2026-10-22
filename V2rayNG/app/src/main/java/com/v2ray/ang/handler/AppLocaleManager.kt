package com.v2ray.ang.handler

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/** Keep the original unqualified text and discard unsupported saved locale choices. */
object AppLocaleManager {
    private val defaultLocales = LocaleListCompat.forLanguageTags("en")

    fun initialize(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val manager = context.getSystemService(LocaleManager::class.java)
            if (manager.applicationLocales.toLanguageTags() != "en") {
                manager.applicationLocales = LocaleList.forLanguageTags("en")
            }
        }
        if (AppCompatDelegate.getApplicationLocales() != defaultLocales) {
            AppCompatDelegate.setApplicationLocales(defaultLocales)
        }
    }

    fun onActivityCreated(context: Context) = initialize(context)

    fun localizedContext(context: Context): Context {
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(Locale.ENGLISH)
        return context.createConfigurationContext(configuration)
    }
}
