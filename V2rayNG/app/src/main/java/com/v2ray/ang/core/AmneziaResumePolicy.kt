package com.v2ray.ang.core

/** Uses elapsed realtime (including deep sleep), never wall-clock time. */
internal class AmneziaResumePolicy {
    private var screenOffAt: Long? = null

    @Synchronized
    fun screenOff(now: Long) {
        if (screenOffAt == null) screenOffAt = now
    }

    @Synchronized
    fun screenOn(now: Long): Boolean {
        val start = screenOffAt ?: return false
        screenOffAt = null
        return now - start >= 60_000L
    }

    @Synchronized
    fun reset() { screenOffAt = null }
}
