package com.v2ray.ang.receiver

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.ui.WidgetPermissionActivity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class WidgetProviderTest {
    @Test
    fun clickUsesAnImmutableBroadcastRatherThanLaunchingTheApp() {
        val context = mock<Context>()
        val pendingIntent = mock<PendingIntent>()
        mockConstruction(Intent::class.java) { _, construction ->
            assertEquals(listOf(context, WidgetProvider::class.java), construction.arguments())
        }.use { intents ->
            mockStatic(PendingIntent::class.java).use { pendingIntents ->
                pendingIntents.`when`<PendingIntent> {
                    PendingIntent.getBroadcast(any(), eq(R.id.layout_switch), any(),
                        eq(PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
                }.thenReturn(pendingIntent)
                assertSame(pendingIntent, WidgetProvider.createTogglePendingIntent(context))
                val intent = intents.constructed().single()
                verify(intent).action = AppConfig.BROADCAST_ACTION_WIDGET_CLICK
                pendingIntents.verify {
                    PendingIntent.getBroadcast(context, R.id.layout_switch, intent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                }
                verify(context, never()).startActivity(any())
            }
        }
    }

    @Test
    fun authorizedDisconnectedClickStartsSmartConnectWithoutAnyActivity() {
        val context = mock<Context>()
        val control = mock<WidgetConnectionControl>()
        WidgetProvider.toggle(context, control)
        verify(control).startInBackground()
        verify(control, never()).stop()
        verify(context, never()).startActivity(any())
    }

    @Test
    fun connectedOrSelectingClickStopsWithoutRequestingConsentOrOpeningTheApp() {
        val context = mock<Context>()
        val control = mock<WidgetConnectionControl>()
        whenever(control.isRunningOrPending()).thenReturn(true)
        WidgetProvider.toggle(context, control)
        verify(control).stop()
        verify(control, never()).startInBackground()
        verify(control, never()).needsConsent()
        verify(context, never()).startActivity(any())
    }

    @Test
    fun missingPermissionUsesOnlyTheTransparentConsentActivity() {
        val context = mock<Context>()
        val control = mock<WidgetConnectionControl>()
        whenever(control.needsConsent()).thenReturn(true)
        mockConstruction(Intent::class.java) { _, construction ->
            assertEquals(listOf(context, WidgetPermissionActivity::class.java), construction.arguments())
        }.use { intents ->
            WidgetProvider.toggle(context, control)
            val intent = intents.constructed().single()
            verify(context).startActivity(intent)
            verify(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            verify(control, never()).startInBackground()
        }
    }
}
