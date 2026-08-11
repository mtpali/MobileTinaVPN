package com.v2ray.ang.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.TestServiceMessage
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.service.TProxyService
import com.v2ray.ang.util.MessageUtil
import com.v2ray.ang.util.Utils
import com.v2ray.ang.viewmodel.MainViewModel
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Thin MobileTina bridge over the native v2rayNG 2.2.6 MainViewModel/CoreTestService.
 * No 2.0.15 Core implementation is copied here.
 */
private object MobileTinaRealPingBridge {
    val finished = MutableLiveData<Long>()
    private val generation = AtomicLong(0L)
    private val registered = AtomicBoolean(false)

    fun generation(): Long = generation.get()

    fun ensureRegistered(context: Context) {
        if (!registered.compareAndSet(false, true)) return
        val app = context.applicationContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.getIntExtra("key", 0) != AppConfig.MSG_MEASURE_CONFIG_FINISH) return
                val status = intent.getSerializableExtra("content")?.toString().orEmpty()
                if (status == "0") {
                    val value = generation.incrementAndGet()
                    finished.postValue(value)
                }
            }
        }
        ContextCompat.registerReceiver(
            app,
            receiver,
            IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY),
            Utils.receiverFlags()
        )
    }
}

val MainViewModel.realPingFinishedAction: MutableLiveData<Long>
    get() {
        MobileTinaRealPingBridge.ensureRegistered(getApplication())
        return MobileTinaRealPingBridge.finished
    }

val MainViewModel.realPingGeneration: Long
    get() {
        MobileTinaRealPingBridge.ensureRegistered(getApplication())
        return MobileTinaRealPingBridge.generation()
    }

fun MainViewModel.currentServerGuids(): List<String> = serversCache.map { it.guid }

fun MainViewModel.updateEverySubscription() = AngConfigManager.updateConfigViaSubAll()

fun MainViewModel.testServerRealPing(guid: String) {
    if (guid.isBlank()) return
    MobileTinaRealPingBridge.ensureRegistered(getApplication())
    MessageUtil.sendMsg2TestService(
        getApplication(),
        TestServiceMessage(key = AppConfig.MSG_MEASURE_CONFIG_CANCEL)
    )
    MmkvManager.clearAllTestDelayResults(listOf(guid))
    MessageUtil.sendMsg2TestService(
        getApplication(),
        TestServiceMessage(
            key = AppConfig.MSG_MEASURE_CONFIG_START,
            subscriptionId = subscriptionId,
            serverGuids = listOf(guid)
        )
    )
}

fun MainViewModel.cancelRealPing() {
    MessageUtil.sendMsg2TestService(
        getApplication(),
        TestServiceMessage(key = AppConfig.MSG_MEASURE_CONFIG_CANCEL)
    )
}

/** Legacy hidden TCP action is intentionally mapped to the supported 2.2.6 Real Ping path. */
fun MainViewModel.testAllTcping() = testAllRealPing()

/** Old UI pre-warm hook; 2.2.6 loads the JNI library in TProxyService's companion initializer. */
fun TProxyService.Companion.preloadNative() = Unit
