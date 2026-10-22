package com.v2ray.ang.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.v2ray.ang.AppConfig
import com.v2ray.ang.contracts.ServiceControl
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.handler.AppLocaleManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.NotificationManager
import com.v2ray.ang.root.RootProxyManager
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.lang.ref.SoftReference

/**
 * Foreground service for the root (system-wide) run modes. Unlike [CoreVpnService] it
 * does not use Android VpnService — traffic is routed by iptables instead
 * (see [RootProxyManager]).
 *
 * The in-process core is started first (so its listener is up and the foreground
 * notification is posted promptly), then the root routing rules are installed off the
 * main thread. On teardown the rules are removed before the core stops.
 */
class CoreRootService : Service(), ServiceControl {

    private val setupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var setupJob: Job? = null
    private lateinit var smartConnect: SmartConnectServiceDelegate
    @Volatile private var stopping = false

    override fun onCreate() {
        super.onCreate()
        LogUtil.i(AppConfig.TAG, "StartCore-Root: Service created")
        CoreServiceManager.serviceControl = SoftReference(this)
        smartConnect = SmartConnectServiceDelegate(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        NotificationManager.ensureForeground()
        LogUtil.i(AppConfig.TAG, "StartCore-Root: command received")

        if (stopping) return START_NOT_STICKY
        if (smartConnect.handleCommand(intent, setupJob?.isActive == true, ::stopService)) return START_NOT_STICKY
        if (MmkvManager.decodeSettingsBool(AppConfig.CACHE_SERVICE_RUNNING) && CoreServiceManager.isRunning()) return START_STICKY
        if (setupJob?.isActive == true) return START_STICKY
        setupJob = setupScope.launch {
            if (!isActive || stopping) return@launch
            if (!CoreServiceManager.startCoreLoop(null)) {
                stopService()
                return@launch
            }
            if (isActive && !stopping && !RootProxyManager.start(this@CoreRootService)) stopService()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        smartConnect.close()
        super.onDestroy()
        // Wait for any in-flight async setup to finish before tearing down. The rules are
        // installed off the main thread and can take seconds (the setup script waits for the
        // tun to appear); if a stop arrives during that window, teardown would run first and
        // the setup would then re-install the rules + tun pointing at a now-dead core,
        // blackholing all traffic until the next start/stop cycle clears it.
        stopping = true
        // Android root teardown must await rule setup to prevent late routes to a dead listener.
        // Bound the callback wait; the service scope is cancelled before any rules are removed.
        setupScope.cancel()
        runBlocking { withTimeoutOrNull(5_000L) { setupJob?.cancelAndJoin() } }
        // Remove routing rules BEFORE stopping the core so traffic is never redirected
        // to a dead listener. Synchronous on purpose — leaving rules behind breaks the net.
        RootProxyManager.stop(this)
        CoreServiceManager.stopCoreLoop()
        CoreServiceManager.acknowledgeStopRequest()
    }

    override fun getService(): Service = this

    override fun startService() {
        // do nothing
    }

    override fun stopService() {
        stopSelf()
    }

    override fun vpnProtect(socket: Int): Boolean = true

    override fun onBind(intent: Intent?): IBinder? = null

    override fun attachBaseContext(newBase: Context?) {
        val context = newBase?.let(AppLocaleManager::localizedContext)
        super.attachBaseContext(context)
    }
}
