package com.v2ray.ang.ui

/** Ten consecutive taps on the same subscription; a hold never counts as more taps. */
internal class SubscriptionQrClickCounter {
    private var subscriptionId: String? = null
    private var lastClickAt: Long? = null
    private var clicks = 0

    fun registerClick(id: String, nowMillis: Long): Boolean {
        if (id.isBlank()) {
            reset()
            return false
        }
        val previous = lastClickAt
        if (subscriptionId != id || previous == null || nowMillis < previous || nowMillis - previous > 2_000L) {
            clicks = 0
        }
        subscriptionId = id
        lastClickAt = nowMillis
        clicks++
        if (clicks < 10) return false
        reset()
        return true
    }

    fun reset() {
        subscriptionId = null
        lastClickAt = null
        clicks = 0
    }
}
