package com.v2ray.ang.handler

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Process
import com.v2ray.ang.dto.entities.AppTrafficLogWindow
import com.v2ray.ang.dto.entities.saturatedTrafficSum

internal object AppTrafficStatsReader {
    @Suppress("DEPRECATION")
    fun hasAccess(context: Context): Boolean = runCatching {
        context.getSystemService(AppOpsManager::class.java)
            ?.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    /** Two aggregated queries, never one expensive query per installed application. */
    @Suppress("DEPRECATION")
    fun read(context: Context, window: AppTrafficLogWindow): Map<Int, Long> {
        val manager = context.getSystemService(NetworkStatsManager::class.java) ?: return emptyMap()
        val types = mutableListOf(ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_MOBILE)
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        if (connectivity?.getNetworkCapabilities(connectivity.activeNetwork)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true) {
            types += ConnectivityManager.TYPE_ETHERNET
        }
        val totals = mutableMapOf<Int, Long>()
        types.forEach { type ->
            try {
                val stats = manager.querySummary(type, null, window.startMillis, window.endMillis) ?: return@forEach
                try {
                    val bucket = NetworkStats.Bucket()
                    while (stats.hasNextBucket()) {
                        if (!stats.getNextBucket(bucket)) break
                        val uid = bucket.uid
                        if (uid < 0 || uid == Process.myUid() || bucket.tag != NetworkStats.Bucket.TAG_NONE) continue
                        totals[uid] = saturatedTrafficSum(totals[uid] ?: 0L, saturatedTrafficSum(bucket.rxBytes, bucket.txBytes))
                    }
                } finally { stats.close() }
            } catch (_: Exception) {
                // Revoked access, an unsupported transport or transient system-service failure
                // must never affect VPN startup/teardown or erase a previous valid snapshot.
            }
        }
        return totals
    }
}
