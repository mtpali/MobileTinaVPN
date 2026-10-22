package com.v2ray.ang.core

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.root.RootManager
import com.v2ray.ang.service.CoreProxyOnlyService
import com.v2ray.ang.service.CoreRootService
import com.v2ray.ang.service.CoreVpnService
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils

object LauncherManager {
    private const val COMMAND_TOKEN_KEY = "MOBILETINA_SERVICE_COMMAND_TOKEN"

    internal fun currentCommandToken(): String = MmkvManager.decodeSettingsString(COMMAND_TOKEN_KEY).orEmpty()

    private fun invalidatePendingRestart() {
        MmkvManager.encodeSettings(COMMAND_TOKEN_KEY, java.util.UUID.randomUUID().toString())
    }


    fun startServiceFromToggle(context: Context): Boolean {
        if (MmkvManager.getSelectServer().isNullOrEmpty()) {
            context.toast(R.string.app_tile_first_use)
            return false
        }
        try {
            startContextService(context)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "LauncherManager: ${e.message}", e)
            context.toast(e.message ?: e.javaClass.simpleName)
            return false
        }
        return true
    }

    fun startService(context: Context, guid: String? = null) {
        LogUtil.i(AppConfig.TAG, "LauncherManager: startService from ${context::class.java.simpleName}")

        if (guid != null) {
            MmkvManager.setSelectServer(guid)
        }

        try {
            startContextService(context)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "LauncherManager: ${e.message}", e)
            context.toast(e.message ?: e.javaClass.simpleName)
        }
    }

    fun stopService(context: Context) {
        invalidatePendingRestart()
        //context.toast(R.string.toast_services_stop)
        MessageHelper.sendMsg2Service(context, AppConfig.MSG_STATE_STOP, "")
    }

    /** Restarts the active daemon without starting a stopped service. */
    fun restartService(context: Context) {
        MessageHelper.sendMsg2Service(context, AppConfig.MSG_STATE_RESTART, "")
    }

    /** Restarts the active daemon, or delegates to the caller's permission-aware start flow. */
    fun restartServiceOrStart(context: Context, startIfStopped: () -> Unit) {
        MessageHelper.sendMsg2ServiceForResult(context, AppConfig.MSG_STATE_RESTART, "") { handled ->
            if (!handled) startIfStopped()
        }
    }

    @Throws(Exception::class)
    internal fun prepareServiceStart(context: Context, selectedGuid: String? = MmkvManager.getSelectServer()): Intent {
        invalidatePendingRestart()
        com.v2ray.ang.util.MobileTinaIntegrityGuard.verify(context.applicationContext)
        // Note: isRunning check is removed here to avoid loading Native libraries in the UI process.
        // The check is performed in CoreServiceManager when the service starts in the daemon process.

        val guid = selectedGuid
            ?: run {
                LogUtil.e(AppConfig.TAG, "LauncherManager: No server selected")
                error(context.getString(R.string.app_tile_first_use))
            }

        val config = MmkvManager.decodeServerConfig(guid)
            ?: run {
                LogUtil.e(AppConfig.TAG, "LauncherManager: Failed to decode server config")
                error(context.getString(R.string.toast_config_file_invalid))
            }

        if (!config.configType.isComplexType()
            && !Utils.isValidUrl(config.server)
            && !Utils.isPureIpAddress(config.server.orEmpty())
        ) {
            LogUtil.e(AppConfig.TAG, "LauncherManager: Invalid server configuration")
            error(context.getString(R.string.toast_config_file_invalid))
        }

        SettingsManager.refreshRuntimeSocksPort()

        if (config.insecure == true && config.pinnedCA256.isNullOrEmpty()) {
            context.toastError(R.string.toast_allow_insecure_deprecated)
            Utils.setClipboard(context, context.getString(R.string.toast_allow_insecure_deprecated))
        }

        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_PROXY_SHARING)) {
            context.toast(R.string.toast_warning_pref_proxysharing_short)
        }

        val isRootMode = SettingsManager.isRootMode()
        if (isRootMode && !RootManager.isRootAvailable()) {
            LogUtil.e(AppConfig.TAG, "LauncherManager: root mode requires root but none available")
            error(context.getString(R.string.toast_root_required))
        }

        return coreServiceIntent(context)
    }

    internal fun coreServiceIntent(context: Context): Intent = when {
        SettingsManager.isRootMode() -> Intent(context.applicationContext, CoreRootService::class.java)
        SettingsManager.isVpnMode() -> Intent(context.applicationContext, CoreVpnService::class.java)
        else -> Intent(context.applicationContext, CoreProxyOnlyService::class.java)
    }

    /** Starts the existing core service promptly; selection happens inside that foreground service. */
    fun startSmartService(context: Context, toggle: Boolean = false): Boolean {
        return try {
            invalidatePendingRestart()
            com.v2ray.ang.util.MobileTinaIntegrityGuard.verify(context.applicationContext)
            val intent = coreServiceIntent(context).apply {
                action = if (toggle) com.v2ray.ang.service.SmartConnectServiceState.ACTION_TOGGLE
                    else com.v2ray.ang.service.SmartConnectServiceState.ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
            true
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Unable to start Smart Connect service", e)
            context.toast(e.message ?: e.javaClass.simpleName)
            false
        }
    }

    @Throws(Exception::class)
    private fun startContextService(context: Context) {
        val intent = prepareServiceStart(context)
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: SecurityException) {
            LogUtil.e(AppConfig.TAG, "LauncherManager: Missing permission to start foreground service", e)
            throw IllegalStateException(e.message ?: e.javaClass.simpleName, e)
        } catch (e: RuntimeException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                e.javaClass.name == "android.app.ForegroundServiceStartNotAllowedException"
            ) {
                LogUtil.e(AppConfig.TAG, "LauncherManager: Foreground service start not allowed", e)
                throw IllegalStateException(e.message ?: e.javaClass.simpleName, e)
            }
            throw e
        }
    }
}
