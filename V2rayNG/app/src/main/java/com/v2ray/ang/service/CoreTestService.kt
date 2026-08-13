package com.v2ray.ang.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.dto.RealPingEvent
import com.v2ray.ang.dto.TestServiceMessage
import com.v2ray.ang.extension.serializable
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.MessageUtil
import java.util.Collections
import java.util.concurrent.atomic.AtomicLong

class CoreTestService : Service() {

    // Manage active batch workers so each batch is independent and cancellable.
    private val activeWorkers = Collections.synchronizedList(mutableListOf<RealPingWorkerService>())

    // Every START/CANCEL invalidates callbacks from all older workers. Native delay tests are
    // not guaranteed to stop synchronously, so a cancelled worker may still return later.
    private val batchGeneration = AtomicLong(0L)

    /**
     * Initializes the V2Ray environment.
     */
    override fun onCreate() {
        super.onCreate()
        CoreNativeManager.initCoreEnv(this)
    }

    /**
     * Binds the service.
     * @param intent The intent.
     * @return The binder.
     */
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    /**
     * Cleans up resources when the service is destroyed.
     */
    override fun onDestroy() {
        batchGeneration.incrementAndGet()
        LogUtil.i(AppConfig.TAG, "CoreTestService is being destroyed, cancelling ${activeWorkers.size} active workers")
        val snapshot = ArrayList(activeWorkers)
        snapshot.forEach { it.cancel() }
        activeWorkers.clear()
        super.onDestroy()
    }

    /**
     * Handles the start command for the service.
     * @param intent The intent.
     * @param flags The flags.
     * @param startId The start ID.
     * @return The start mode.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val message = intent?.serializable<TestServiceMessage>("content")
        if (message == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        when (message.key) {
            AppConfig.MSG_MEASURE_CONFIG_START -> handleMeasureStart(message, startId)
            AppConfig.MSG_MEASURE_CONFIG_CANCEL -> handleMeasureCancel()
            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun handleMeasureStart(message: TestServiceMessage, startId: Int) {
        LogUtil.i(AppConfig.TAG, "CoreTestService starting worker subscription ${message.subscriptionId}")

        // START is authoritative even if a caller's preceding CANCEL is delayed or omitted.
        // Invalidate and cancel anything older before accepting the new batch.
        val generation = batchGeneration.incrementAndGet()
        val staleWorkers = ArrayList(activeWorkers)
        staleWorkers.forEach { it.cancel() }
        activeWorkers.clear()

        val guidsList = when {
            message.serverGuids.isNotEmpty() -> message.serverGuids
            message.subscriptionId.isNotEmpty() -> MmkvManager.decodeServerList(message.subscriptionId)
            else -> MmkvManager.decodeAllServerList()
        }

        if (guidsList.isNotEmpty()) {
            lateinit var worker: RealPingWorkerService
            worker = RealPingWorkerService(
                context = this,
                guids = guidsList,
                onEvent = { event -> handleWorkerEvent(generation, worker, event) }
            )
            activeWorkers.add(worker)
            worker.start()
        } else {
            stopSelf(startId)
        }
    }

    private fun handleWorkerEvent(
        generation: Long,
        worker: RealPingWorkerService,
        event: RealPingEvent
    ) {
        val isCurrentBatch = generation == batchGeneration.get()

        when (event) {
            is RealPingEvent.Progress -> {
                if (isCurrentBatch) {
                    MessageUtil.sendMsg2UI(this, AppConfig.MSG_MEASURE_CONFIG_NOTIFY, event.text)
                }
            }

            is RealPingEvent.Result -> {
                if (isCurrentBatch) {
                    MmkvManager.encodeServerTestDelayMillis(event.guid, event.delayMillis)
                    MessageUtil.sendMsg2UI(this, AppConfig.MSG_MEASURE_CONFIG_SUCCESS, event.guid)
                }
            }

            is RealPingEvent.Finish -> {
                activeWorkers.remove(worker)
                if (isCurrentBatch) {
                    MessageUtil.sendMsg2UI(this, AppConfig.MSG_MEASURE_CONFIG_FINISH, event.status)
                }
                if (activeWorkers.isEmpty()) {
                    stopSelf()
                }
            }
        }
    }

    private fun handleMeasureCancel() {
        // Invalidate first, before asking workers to cancel, so any late native callback is ignored.
        batchGeneration.incrementAndGet()
        LogUtil.i(AppConfig.TAG, "CoreTestService received cancel message, cancelling ${activeWorkers.size} active workers")
        val snapshot = ArrayList(activeWorkers)
        snapshot.forEach { it.cancel() }
        activeWorkers.clear()
        stopSelf()
    }
}
