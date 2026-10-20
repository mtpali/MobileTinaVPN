package com.v2ray.ang.helper

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.TestServiceMessage
import com.v2ray.ang.service.CoreTestService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockConstruction
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class TestServiceDispatchTest {
    @Test
    fun smartConnectStartsTheRegularServiceWithItsBatchAndRequestIdentity() {
        val context = mock<Context>()
        val message = TestServiceMessage(
            key = AppConfig.MSG_MEASURE_CONFIG_START,
            serverGuids = listOf("one", "two"),
            onlyTcp = true,
            batchId = 42L,
        )
        mockConstruction(ComponentName::class.java) { _, construction ->
            assertEquals(listOf(context, CoreTestService::class.java), construction.arguments())
        }.use {
            mockConstruction(Intent::class.java).use { intents ->
                MessageHelper.sendMsg2TestService(context, message, "smart-42")

                val intent = intents.constructed().single()
                verify(intent).putExtra("content", message)
                verify(intent).putExtra(MessageHelper.EXTRA_REQUEST_ID, "smart-42")
                verify(context).startService(intent)
                verify(context, never()).startForegroundService(any())
            }
        }
    }

    @Test
    fun cancellationStopsTheExistingTestWithoutStartingAnotherService() {
        val context = mock<Context>()
        mockConstruction(ComponentName::class.java).use {
            mockConstruction(Intent::class.java).use { intents ->
                MessageHelper.sendMsg2TestService(
                    context,
                    TestServiceMessage(AppConfig.MSG_MEASURE_CONFIG_CANCEL),
                    "cancelled-request",
                )

                verify(context).stopService(intents.constructed().single())
                verify(context, never()).startService(any())
                verify(context, never()).startForegroundService(any())
            }
        }
    }
}
