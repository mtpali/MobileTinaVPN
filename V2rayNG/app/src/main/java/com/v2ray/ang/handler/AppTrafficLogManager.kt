package com.v2ray.ang.handler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.AppTrafficLogEntry
import com.v2ray.ang.dto.entities.AppTrafficLogSession
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Owned by the existing VPN daemon; no extra service, wake lock, native hook or packet scan. */
object AppTrafficLogManager {
    private const val SAMPLE_INTERVAL_MS = 60_000L
    private const val MIN_REFRESH_INTERVAL_MS = 15_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val queryMutex = Mutex()
    private val refresh = Channel<Unit>(Channel.CONFLATED)
    private val pending = ConcurrentHashMap<String, Long>()
    private var job: Job? = null
    private var activeId: String? = null
    private var receiverContext: Context? = null
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { refresh.trySend(Unit) }
    }

    private fun action(context: Context) = context.packageName + ".APP_TRAFFIC_LOG_REFRESH"

    fun hasAccess(context: Context): Boolean = AppTrafficStatsReader.hasAccess(context)

    fun requestRefresh(context: Context) {
        runCatching { context.sendBroadcast(Intent(action(context)).setPackage(context.packageName)) }
    }

    /** Called from the core's IO setup after an actual VPN start, also valid without the UI. */
    @Synchronized
    fun start(context: Context, stillRunning: () -> Boolean) {
        if (!stillRunning() || MmkvManager.dataResetInProgress() || activeId != null) return
        val app = context.applicationContext
        val id = UUID.randomUUID().toString()
        val selected = if (MmkvManager.decodeSettingsBool(AppConfig.PREF_PER_APP_PROXY, false)) {
            MmkvManager.decodeSettingsStringSet(AppConfig.PREF_PER_APP_PROXY_SET)?.toSet().orEmpty()
        } else emptySet()
        try {
            AppTrafficLogStore.begin(AppTrafficLogSession(
                id = id, startMillis = System.currentTimeMillis(), selectedPackages = selected,
                bypass = MmkvManager.decodeSettingsBool(AppConfig.PREF_BYPASS_APPS, false),
            ), pending.keys.toSet())
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "Traffic log could not start", e)
            return
        }
        activeId = id
        receiverContext = app
        runCatching {
            ContextCompat.registerReceiver(app, receiver, IntentFilter(action(app)), ContextCompat.RECEIVER_NOT_EXPORTED)
        }
        job = scope.launch {
            val identities = mutableMapOf<Int, AppTrafficLogEntry>()
            var lastQueryAt = Long.MIN_VALUE
            while (isActive) {
                try {
                    if (hasAccess(app) && (lastQueryAt == Long.MIN_VALUE || SystemClock.elapsedRealtime() - lastQueryAt >= MIN_REFRESH_INTERVAL_MS)) {
                        queryMutex.withLock { sample(app, id, System.currentTimeMillis(), identities) }
                        lastQueryAt = SystemClock.elapsedRealtime()
                    }
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (e: Exception) {
                    LogUtil.w(AppConfig.TAG, "Traffic log sample unavailable", e)
                }
                withTimeoutOrNull(SAMPLE_INTERVAL_MS) { refresh.receive() }
            }
        }
    }

    /** Never waits for NetworkStatsManager on the VPN stop/TUN acknowledgement path. */
    @Synchronized
    fun stop() {
        val id = activeId ?: return
        activeId = null
        val stoppedAt = System.currentTimeMillis()
        val app = receiverContext
        pending[id] = stoppedAt
        job?.cancel()
        job = null
        receiverContext = null
        app?.let { runCatching { it.unregisterReceiver(receiver) } }
        scope.launch {
            try {
                AppTrafficLogStore.end(id, stoppedAt)
                // Give the system's final accounting a chance to settle, without delaying TUN
                // closure, holding a wake lock or starting another background component.
                delay(2_500L)
                queryMutex.withLock {
                    if (app != null && hasAccess(app)) sample(app, id, stoppedAt, mutableMapOf())
                    AppTrafficLogStore.finish(id)
                }
            } catch (e: Exception) {
                LogUtil.w(AppConfig.TAG, "Traffic log final sample unavailable", e)
            } finally { pending.remove(id) }
        }
    }

    internal fun entries(): List<AppTrafficLogEntry> = AppTrafficLogStore.entries()

    fun clearHistory(context: Context, endSessions: Boolean = false) {
        AppTrafficLogStore.clear(System.currentTimeMillis(), endSessions)
        if (!endSessions) requestRefresh(context)
    }

    private fun sample(context: Context, id: String, endMillis: Long, identities: MutableMap<Int, AppTrafficLogEntry>) {
        if (MmkvManager.dataResetInProgress()) return
        val window = AppTrafficLogStore.window(id, endMillis) ?: return
        val totals = AppTrafficStatsReader.read(context, window)
        totals.filterValues { it > 0L }.keys.forEach { uid ->
            if (uid !in identities) resolve(context, uid)?.let { identities[uid] = it }
        }
        AppTrafficLogStore.record(window, totals, identities)
    }

    private fun resolve(context: Context, uid: Int): AppTrafficLogEntry? = runCatching {
        val pm = context.packageManager
        val packages = pm.getPackagesForUid(uid)?.sorted().orEmpty()
        if (packages.isEmpty()) return null
        val labels = packages.map { name ->
            runCatching { pm.getApplicationLabel(pm.getApplicationInfo(name, 0)).toString() }.getOrDefault(name)
        }.distinct()
        AppTrafficLogEntry(uid, packages, labels.joinToString(" / "))
    }.getOrNull()
}
