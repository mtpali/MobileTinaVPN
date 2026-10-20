package com.v2ray.ang

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import androidx.work.WorkManager
import com.v2ray.ang.AppConfig.ANG_PACKAGE
import com.v2ray.ang.handler.AppLocaleManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.compose.ThemeManager
import com.v2ray.ang.handler.MobileTinaGeoAssetManager
import com.v2ray.ang.util.MobileTinaIntegrityGuard

class AngApplication : Application() {
    companion object {
        lateinit var application: AngApplication
    }

    /**
     * Attaches the base context to the application.
     * @param base The base context.
     */
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base?.let(AppLocaleManager::localizedContext))
        application = this
    }

    private val workManagerConfiguration: Configuration = Configuration.Builder()
        .setDefaultProcessName("${ANG_PACKAGE}:bg")
        .build()

    /**
     * Initializes the application.
     */
    override fun onCreate() {
        super.onCreate()

        MobileTinaIntegrityGuard.verify(this)
        MmkvManager.initialize(this)

        AppLocaleManager.initialize(this)

        // Prepare bundled geo assets synchronously in every Android process before CoreNativeManager
        // can initialize Xray. This removes the first-run race that could leave geosite.dat partially
        // written and then permanently skipped on subsequent launches.
        MobileTinaGeoAssetManager.ensureReady(this, assets)

        // MobileTina no longer exposes or supports the two root-only Settings options.
        // Clear stale values from upgrades so an invisible root mode can never remain active.
        MmkvManager.encodeSettings(AppConfig.PREF_ROOT_MODE_ENABLE, false)
        MmkvManager.encodeSettings(AppConfig.PREF_ROOT_LAN_SHARING, false)
        MmkvManager.encodeSettings(AppConfig.PREF_DOUBLE_COLUMN_DISPLAY, false)

        // Initialize WorkManager with the custom configuration
        WorkManager.initialize(this, workManagerConfiguration)

        // Ensure critical preference defaults are present in MMKV early
        SettingsManager.initApp(this)

        // Initialize theme state from MMKV
        ThemeManager.refresh()
    }
}
