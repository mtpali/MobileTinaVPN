package com.v2ray.ang.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.core.SmartConnectRunner
import com.v2ray.ang.dto.TestServiceMessage
import com.v2ray.ang.extension.toast
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.V2RayServiceManager
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/** Cross-process UI/widget status. Confirmed VPN running state remains a separate flag. */
object SmartConnectServiceState {
    const val ACTION_START = AppConfig.ANG_PACKAGE + ".action.smart.start"
    const val ACTION_TOGGLE = AppConfig.ANG_PACKAGE + ".action.smart.toggle"
    const val ACTION_SELECTED = AppConfig.ANG_PACKAGE + ".action.smart.selected"
    const val ACTION_CHANGED = AppConfig.ANG_PACKAGE + ".action.smart.changed"
    private const val CACHE_STATE = "smart_connect_service_state"

    enum class Phase { IDLE, SELECTING, STARTING, FAILED }

    fun phase(context: Context): Phase {
        val phase = raw().second
        if ((phase == Phase.SELECTING || phase == Phase.STARTING) &&
            V2RayServiceManager.coreServicePresent(context) == false
        ) {
            MmkvManager.encodeSettings(CACHE_STATE, "")
            return Phase.IDLE
        }
        return phase
    }

    fun isPending(context: Context): Boolean = phase(context).let { it == Phase.SELECTING || it == Phase.STARTING }

    private fun raw(): Pair<String, Phase> {
        val fields = MmkvManager.decodeSettingsString(CACHE_STATE).orEmpty().split(':', limit = 2)
        return fields.first() to (fields.getOrNull(1)?.let { value -> Phase.entries.find { it.name == value } } ?: Phase.IDLE)
    }

    internal fun publish(context: Context, owner: String, phase: Phase, begin: Boolean = false) {
        if (!begin && raw().first != owner) return
        MmkvManager.encodeSettings(CACHE_STATE, "$owner:${phase.name}")
        context.sendBroadcast(Intent(ACTION_CHANGED).apply { `package` = context.packageName })
    }
}

/** Lives inside the existing foreground VPN/proxy service, never inside an activity. */
internal class SmartConnectServiceDelegate(private val service: Service) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var owner: String? = null
    private var probeRequestId = ""
    private var startedBatchId = 0L
    private var stop: () -> Unit = {}
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AppConfig.BROADCAST_ACTION_SERVICE) {
                if (intent.getIntExtra("key", 0) == AppConfig.MSG_STATE_STOP && owner != null) {
                    cancel()
                    stop()
                }
                return
            }
            if (intent?.getStringExtra(MessageHelper.EXTRA_REQUEST_ID) != probeRequestId) return
            val text = intent.getSerializableExtra("content")?.toString().orEmpty()
            if (intent.getIntExtra("key", 0) == AppConfig.MSG_MEASURE_CONFIG_NOTIFY &&
                text.startsWith(TestServiceMessage.SMART_BATCH_STARTED_PREFIX)
            ) startedBatchId = text.removePrefix(TestServiceMessage.SMART_BATCH_STARTED_PREFIX).toLongOrNull() ?: 0L
        }
    }

    init {
        ContextCompat.registerReceiver(service, receiver,
            IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY).apply { addAction(AppConfig.BROADCAST_ACTION_SERVICE) },
            ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    fun handleCommand(intent: Intent?, busy: Boolean, stopService: () -> Unit): Boolean {
        stop = stopService
        return when (intent?.action) {
            SmartConnectServiceState.ACTION_TOGGLE -> {
                if (owner != null || busy || CoreServiceManager.isRunning()) {
                    cancel()
                    stopService()
                } else begin()
                true
            }
            SmartConnectServiceState.ACTION_START -> {
                if (owner == null && !busy && !CoreServiceManager.isRunning()) begin()
                true
            }
            SmartConnectServiceState.ACTION_SELECTED -> {
                // A queued handoff from a cancelled/previous attempt must never start a core.
                val obsolete = intent.getStringExtra(MessageHelper.EXTRA_REQUEST_ID) != owner || owner == null
                if (obsolete && owner == null && !busy &&
                    !(CoreServiceManager.isRunning() && V2RayServiceManager.isRunning())
                ) stopService()
                obsolete
            }
            else -> {
                cancel()
                SmartConnectServiceState.publish(service, "", SmartConnectServiceState.Phase.IDLE, begin = true)
                false
            }
        }
    }

    private fun begin() {
        val id = UUID.randomUUID().toString()
        owner = id
        SmartConnectServiceState.publish(service, id, SmartConnectServiceState.Phase.SELECTING, begin = true)
        job = scope.launch {
            try {
                val result = SmartConnectRunner(runtime(id)).run(batchIds.addAndGet(10L))
                if (owner != id) return@launch
                owner = null
                job = null
                SmartConnectServiceState.publish(service, id,
                    if (result == SmartConnectRunner.Result.CONNECTED) SmartConnectServiceState.Phase.IDLE
                    else SmartConnectServiceState.Phase.FAILED)
                when (result) {
                    SmartConnectRunner.Result.NO_SERVERS -> service.toast(R.string.title_file_chooser)
                    SmartConnectRunner.Result.NO_WORKING_SERVER -> service.toast(R.string.mobiletina_no_working_server)
                    else -> Unit
                }
                if (result != SmartConnectRunner.Result.CONNECTED) stop()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Smart Connect could not start", e)
                if (owner == id) {
                    owner = null
                    job = null
                    SmartConnectServiceState.publish(service, id, SmartConnectServiceState.Phase.FAILED)
                    stop()
                }
            }
        }
    }

    private fun runtime(id: String) = object : SmartConnectRunner.Runtime {
        private var subscriptionId = ""
        private var testedMultipleServers = false
        override suspend fun servers(): List<String> = withContext(Dispatchers.IO) {
            val subscriptions = MmkvManager.decodeSubscriptions().filter { it.guid != AppConfig.DEFAULT_SUBSCRIPTION_ID }
            val stored = MmkvManager.decodeSettingsString(AppConfig.CACHE_SUBSCRIPTION_ID).orEmpty()
            val subscription = stored.takeIf { key -> subscriptions.any { it.guid == key } }
                ?: subscriptions.firstOrNull()?.guid.orEmpty()
            subscriptionId = subscription
            MmkvManager.encodeSettings(AppConfig.CACHE_SUBSCRIPTION_ID, subscription)
            if (subscription.isBlank()) MmkvManager.decodeAllServerList() else MmkvManager.decodeServerList(subscription)
        }

        override fun startProbe(batchId: Long, servers: List<String>) {
            testedMultipleServers = true
            startedBatchId = 0L
            probeRequestId = "$id:$batchId"
            MessageHelper.sendMsg2TestService(service, TestServiceMessage(
                key = AppConfig.MSG_MEASURE_CONFIG_START, serverGuids = servers, batchId = batchId
            ), probeRequestId)
        }

        override fun probeStarted(batchId: Long) = startedBatchId == batchId
        override suspend fun results(servers: List<String>): Map<String, Long> = withContext(Dispatchers.IO) {
            servers.associateWith { MmkvManager.decodeServerAffiliationInfo(it)?.testDelayMillis ?: 0L }
        }

        override fun cancelProbe() {
            if (!probeRequestId.startsWith("$id:")) return
            probeRequestId = ""
            MessageHelper.sendMsg2TestService(service, TestServiceMessage(AppConfig.MSG_MEASURE_CONFIG_CANCEL))
        }

        override suspend fun startSelected(guid: String) {
            // Validate the same profile/root/integrity/socks-port rules as a normal start.
            withContext(Dispatchers.IO) {
                check(LauncherManager.prepareServiceStart(service, guid).component?.className == service.javaClass.name)
            }
            currentCoroutineContext().ensureActive()
            check(owner == id)
            MmkvManager.setSelectServer(guid)
            SmartConnectServiceState.publish(service, id, SmartConnectServiceState.Phase.STARTING)
            // This is the SAME already-foreground service. No second foreground service is
            // launched after the widget's user-interaction exemption has expired.
            service.startService(Intent(service, service.javaClass).apply {
                action = SmartConnectServiceState.ACTION_SELECTED
                putExtra(MessageHelper.EXTRA_REQUEST_ID, id)
            })
            if (testedMultipleServers) scope.launch(Dispatchers.IO) {
                val subscriptions = if (subscriptionId.isBlank()) MmkvManager.decodeSubsList() else listOf(subscriptionId)
                subscriptions.forEach(AngConfigManager::sortByTestResultsForSub)
                service.sendBroadcast(Intent(SmartConnectServiceState.ACTION_CHANGED).apply { `package` = service.packageName })
            }
        }

        override fun isRunning() = CoreServiceManager.isRunning() && V2RayServiceManager.isRunning()
        override fun now() = SystemClock.elapsedRealtime()
        override suspend fun pause(millis: Long) = delay(millis)
    }

    fun cancel() {
        val id = owner ?: return
        owner = null
        job?.cancel()
        job = null
        if (probeRequestId.isNotEmpty()) {
            probeRequestId = ""
            MessageHelper.sendMsg2TestService(service, TestServiceMessage(AppConfig.MSG_MEASURE_CONFIG_CANCEL))
        }
        SmartConnectServiceState.publish(service, id, SmartConnectServiceState.Phase.IDLE)
    }

    fun close() {
        cancel()
        scope.cancel()
        service.unregisterReceiver(receiver)
    }

    companion object { private val batchIds = AtomicLong(SystemClock.elapsedRealtime()) }
}
