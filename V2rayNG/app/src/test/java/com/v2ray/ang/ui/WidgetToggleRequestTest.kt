package com.v2ray.ang.ui

import com.v2ray.ang.AppConfig
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WidgetToggleRequestTest {
    @Test
    fun ordinaryLaunchAndResumeNeverRequestAConnection() {
        val request = WidgetToggleRequest()
        assertFalse(request.receive(null))
        assertFalse(request.receive("android.intent.action.MAIN"))
        assertFalse(request.consume(ready = true))
    }

    @Test
    fun coldWidgetLaunchWaitsForPermissionsAndIsConsumedOnlyOnce() {
        val request = WidgetToggleRequest()
        assertTrue(request.receive(AppConfig.BROADCAST_ACTION_WIDGET_CLICK))
        assertFalse(request.consume(ready = false))
        assertTrue(request.isPending)
        assertTrue(request.consume(ready = true))
        assertFalse(request.consume(ready = true))
        // A later widget click must still reach Smart Connect's stop/cancel path.
        assertTrue(request.receive(AppConfig.BROADCAST_ACTION_WIDGET_CLICK))
        assertTrue(request.consume(ready = true))
    }

    @Test
    fun aSecondClickDuringOnboardingCancelsThePendingConnection() {
        val request = WidgetToggleRequest()
        request.receive(AppConfig.BROADCAST_ACTION_WIDGET_CLICK)
        request.receive(AppConfig.BROADCAST_ACTION_WIDGET_CLICK)
        assertFalse(request.consume(ready = true))
        request.receive(AppConfig.BROADCAST_ACTION_WIDGET_CLICK)
        assertTrue(request.consume(ready = true))
    }

    @Test
    fun recreationPreservesOnlyAnUnconsumedRequest() {
        val original = WidgetToggleRequest()
        original.receive(AppConfig.BROADCAST_ACTION_WIDGET_CLICK)
        val restored = WidgetToggleRequest(original.isPending)
        assertTrue(restored.consume(ready = true))
        assertFalse(WidgetToggleRequest(restored.isPending).consume(ready = true))
    }

    @Test
    fun denyingFirstRunVpnPermissionDiscardsTheWidgetRequest() {
        val request = WidgetToggleRequest()
        request.receive(AppConfig.BROADCAST_ACTION_WIDGET_CLICK)
        request.cancel()
        assertFalse(request.consume(ready = true))
    }
}
