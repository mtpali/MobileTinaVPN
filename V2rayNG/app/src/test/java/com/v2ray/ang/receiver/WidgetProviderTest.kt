package com.v2ray.ang.receiver

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.ui.MainActivity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class WidgetProviderTest {
    @Test
    fun clickLaunchesTheProductActivityDirectlyWithAnImmutableToggleIntent() {
        val context = mock<Context>()
        val pendingIntent = mock<PendingIntent>()
        mockConstruction(Intent::class.java) { _, construction ->
            assertEquals(listOf(context, MainActivity::class.java), construction.arguments())
        }.use { intents ->
            mockStatic(PendingIntent::class.java).use { pendingIntents ->
                pendingIntents.`when`<PendingIntent> {
                    PendingIntent.getActivity(
                        any(), eq(R.id.layout_switch), any(),
                        eq(PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                    )
                }.thenReturn(pendingIntent)

                assertSame(pendingIntent, WidgetProvider.createTogglePendingIntent(context))
                val intent = intents.constructed().single()
                verify(intent).action = AppConfig.BROADCAST_ACTION_WIDGET_CLICK
                verify(intent).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
                pendingIntents.verify {
                    PendingIntent.getActivity(
                        context, R.id.layout_switch, intent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                }
            }
        }
    }
}
