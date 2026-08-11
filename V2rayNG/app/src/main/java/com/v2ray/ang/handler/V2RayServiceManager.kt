package com.v2ray.ang.handler

import android.content.Context
import com.v2ray.ang.core.CoreServiceManager

/** Compatibility facade used by the MobileTina UI while the app runs on v2rayNG 2.2.6 Core. */
object V2RayServiceManager {
    fun startVService(context: Context) = CoreServiceManager.startVService(context)
    fun stopVService(context: Context) = CoreServiceManager.stopVService(context)
    fun isRunning(): Boolean = CoreServiceManager.isRunning()
}
