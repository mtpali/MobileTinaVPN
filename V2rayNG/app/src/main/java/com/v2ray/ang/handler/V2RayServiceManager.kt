package com.v2ray.ang.handler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.util.Utils
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Compatibility facade used by the MobileTina UI while the app runs on v2rayNG 2.2.6 Core.
 *
 * The VPN/Core service may run in a different Android process from MainActivity. Therefore
 * CoreServiceManager.isRunning() is not a valid UI-side source of truth: it reads the
 * CoreController instance of the caller's process. The multi-process MMKV running flag is
 * authoritative once startup completes, while a short local pending window covers the time
 * between requesting the service and receiving START_SUCCESS / START_FAILURE.
 */
object V2RayServiceManager {
    private const val START_PENDING_GRACE_MS = 15_000L

    private val receiverRegistered = AtomicBoolean(false)
    private val startPendingUntil = AtomicLong(0L)

    private fun ensureStateReceiver(context: Context) {
        if (!receiverRegistered.compareAndSet(false, true)) return

        val app = context.applicationContext
        ContextCompat.registerReceiver(
            app,
            object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    when (intent?.getIntExtra("key", 0)) {
                        AppConfig.MSG_STATE_RUNNING,
                        AppConfig.MSG_STATE_START_SUCCESS,
                        AppConfig.MSG_STATE_START_FAILURE,
                        AppConfig.MSG_STATE_STOP_SUCCESS -> startPendingUntil.set(0L)
                    }
                }
            },
            IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY),
            Utils.receiverFlags()
        )
    }

    fun startVService(context: Context) {
        ensureStateReceiver(context)
        startPendingUntil.set(SystemClock.elapsedRealtime() + START_PENDING_GRACE_MS)
        CoreServiceManager.startVService(context)
    }

    fun stopVService(context: Context) {
        ensureStateReceiver(context)
        // A stop click must always cancel any UI-side startup grace immediately.
        startPendingUntil.set(0L)
        CoreServiceManager.stopVService(context)
    }

    fun isRunning(): Boolean {
        if (MmkvManager.decodeSettingsBool(AppConfig.CACHE_SERVICE_RUNNING, false)) {
            return true
        }
        return SystemClock.elapsedRealtime() < startPendingUntil.get()
    }
}
