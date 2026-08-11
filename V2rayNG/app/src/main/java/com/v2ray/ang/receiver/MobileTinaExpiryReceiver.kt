package com.v2ray.ang.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.v2ray.ang.handler.MobileTinaExpiryManager

class MobileTinaExpiryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            MobileTinaExpiryManager.ACTION_EXPIRE -> MobileTinaExpiryManager.executeIfDue(context.applicationContext)
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> MobileTinaExpiryManager.recoverPending(context.applicationContext)
        }
    }
}
