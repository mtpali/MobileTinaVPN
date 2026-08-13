package com.v2ray.ang.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.core.MobileTinaSessionLimiter

class MobileTinaSessionLimitReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == MobileTinaSessionLimiter.ACTION_SESSION_LIMIT) {
            CoreServiceManager.stopVService(context.applicationContext)
        }
    }
}
