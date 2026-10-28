package com.v2ray.ang.handler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.AppTrafficLogEntry
import com.v2ray.ang.dto.entities.AppTrafficLogSession
import com.v2ray.ang.dto.entities.AppTrafficLogWindow
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import libv2ray.AppTrafficLog
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Own VPN proxy payload only. No Usage Access, network history queries or extra service. */
object AppTrafficLogManager {
    private const val SAMPLE_INTERVAL_MS = 60_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val queryMutex = Mutex()
    private val refresh = Channel<Unit>(Channel.CONFLATED)
    private val pending = ConcurrentHashMap.newKeySet<String>()
    private var job: Job? = null
    private var active: Active? = null
    private class Active(val id: String, val app: Context, val native: AppTrafficLog, var window: AppTrafficLogWindow) {
        var stopped = false
        val identities = mutableMapOf<Int, AppTrafficLogEntry>()
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { refresh.trySend(Unit) }
    }
    private fun action(context: Context) = context.packageName + ".APP_TRAFFIC_LOG_REFRESH"
    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    fun requestRefresh(context: Context) {
        runCatching { context.sendBroadcast(Intent(action(context)).setPackage(context.packageName)) }
    }
    @Synchronized
    fun start(context: Context, native: AppTrafficLog?, stillRunning: () -> Boolean) {
        if (native == null) return
        if (!isSupported() || !stillRunning() || MmkvManager.dataResetInProgress() || active != null) {
            native.stop()
            return
        }
        val app = context.applicationContext
        val id = UUID.randomUUID().toString()
        val window = try {
            AppTrafficLogStore.begin(AppTrafficLogSession(id = id, startMillis = System.currentTimeMillis()), pending.toSet())
        } catch (e: Exception) {
            native.stop()
            LogUtil.w(AppConfig.TAG, "Traffic log could not start", e)
            return
        }
        val session = Active(id, app, native, window)
        active = session
        runCatching {
            ContextCompat.registerReceiver(app, receiver, IntentFilter(action(app)), ContextCompat.RECEIVER_NOT_EXPORTED)
        }
        job = scope.launch {
            while (isActive) {
                try { queryMutex.withLock { sample(session) } }
                catch (cancel: CancellationException) { throw cancel }
                catch (e: Exception) { LogUtil.w(AppConfig.TAG, "Traffic log sample unavailable", e) }
                withTimeoutOrNull(SAMPLE_INTERVAL_MS) { refresh.receive() }
            }
        }
    }
    /** Only a small in-memory native freeze is synchronous; disk/labels stay on IO. */
    @Synchronized
    fun stop() {
        val session = active ?: return
        active = null
        pending.add(session.id)
        job?.cancel()
        job = null
        runCatching { session.app.unregisterReceiver(receiver) }
        val (window, payload) = synchronized(session) {
            session.stopped = true
            session.window.copy(endMillis = System.currentTimeMillis()) to session.native.stop()
        }
        scope.launch {
            try {
                queryMutex.withLock {
                    AppTrafficLogStore.end(session.id, window.endMillis)
                    persist(session, window, payload)
                    AppTrafficLogStore.finish(session.id)
                }
            } catch (e: Exception) { LogUtil.w(AppConfig.TAG, "Traffic log final sample unavailable", e) }
            finally { pending.remove(session.id) }
        }
    }
    internal fun entries(): List<AppTrafficLogEntry> = AppTrafficLogStore.entries()
    fun clearHistory(context: Context, endSessions: Boolean = false) {
        AppTrafficLogStore.clear(System.currentTimeMillis(), endSessions)
        if (!endSessions) requestRefresh(context)
    }
    private fun sample(session: Active) {
        if (MmkvManager.dataResetInProgress()) return
        val requested = AppTrafficLogStore.window(session.id, System.currentTimeMillis()) ?: return
        val snapshot = synchronized(session) {
            if (session.stopped) return
            if (requested.resetRequired) {
                session.native.reset()
                session.window = requested.copy(resetRequired = false)
            }
            session.window.copy(endMillis = requested.endMillis) to session.native.snapshot()
        }
        if (requested.resetRequired) AppTrafficLogStore.acknowledgeReset(requested)
        persist(session, snapshot.first, snapshot.second)
    }
    private fun persist(session: Active, window: AppTrafficLogWindow, payload: String) {
        if (MmkvManager.dataResetInProgress()) return
        val totals = AppTrafficStatsReader.parse(payload)
        totals.keys.forEach { uid ->
            if (uid !in session.identities) resolve(session.app, uid)?.let { session.identities[uid] = it }
        }
        AppTrafficLogStore.record(window, totals, session.identities)
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
