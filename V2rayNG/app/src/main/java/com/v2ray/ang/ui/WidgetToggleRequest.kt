package com.v2ray.ang.ui

import com.v2ray.ang.AppConfig

/** Keeps a widget click pending until onboarding/permissions finish, without replaying it on resume. */
internal class WidgetToggleRequest(pending: Boolean = false) {
    var isPending = pending
        private set

    fun receive(action: String?): Boolean {
        if (action != AppConfig.BROADCAST_ACTION_WIDGET_CLICK) return false
        // A second click before startup completes cancels the first request.
        isPending = !isPending
        return true
    }

    fun consume(ready: Boolean): Boolean {
        if (!ready || !isPending) return false
        isPending = false
        return true
    }

    fun cancel() {
        isPending = false
    }
}
