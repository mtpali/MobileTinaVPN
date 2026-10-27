package com.v2ray.ang.handler

import android.content.Context
import com.v2ray.ang.core.MobileTinaSessionLimiter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object MobileTinaResetManager {
    private val mutex = Mutex()

    suspend fun reset(context: Context, cancelPendingExpiry: Boolean = true): Boolean =
        withContext(NonCancellable + Dispatchers.IO) {
            mutex.withLock {
                val app = context.applicationContext
                MmkvManager.encodeSettings("DATA_RESET_UNTIL", android.os.SystemClock.elapsedRealtime() + 120_000L)
                try {
                    if (!V2RayServiceManager.stopAndAwait(app)) return@withLock false
                    if (cancelPendingExpiry) MobileTinaExpiryManager.cancel(app)
                    MobileTinaSessionLimiter.cancel(app)
                    MmkvManager.decodeSubsList().forEach { SubscriptionUpdater.cancelOne(app, it) }
                    // Reconcile marker work before taking the storage lock (opposite ordering can deadlock).
                    MobileTinaSubscriptionMarkerManager.clearExpiredState()
                    MmkvManager.clearProfilesAndSubscriptions()
                    true
                } catch (e: Exception) {
                    com.v2ray.ang.util.LogUtil.e(com.v2ray.ang.AppConfig.TAG, "VPN reset failed", e)
                    false
                } finally { MmkvManager.encodeSettings("DATA_RESET_UNTIL", 0L) }
            }
        }
}
