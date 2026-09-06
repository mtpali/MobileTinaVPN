package com.v2ray.ang.service

import org.junit.Assert.assertEquals
import org.junit.Test

class VpnTeardownCoordinatorTest {

    @Test
    fun `forced stop releases vpn before publishing and auxiliary cleanup`() {
        val events = mutableListOf<String>()

        VpnTeardownCoordinator().teardown(
            forceServiceStop = true,
            stopService = { events += "stop-service" },
            closeVpnInterface = { events += "close-interface" },
            publishStoppedState = { events += "publish-stopped" },
            acknowledgeInterfaceClosed = { events += "acknowledge" },
            cleanupAuxiliaries = { events += "cleanup" },
        )

        assertEquals(
            listOf("stop-service", "close-interface", "publish-stopped", "acknowledge", "cleanup"),
            events,
        )
    }

    @Test
    fun `repeated teardown keeps closure idempotent and does not repeat cleanup`() {
        val events = mutableListOf<String>()
        val coordinator = VpnTeardownCoordinator()
        val teardown = {
            coordinator.teardown(
                forceServiceStop = false,
                stopService = { events += "stop-service" },
                closeVpnInterface = { events += "close-interface" },
                publishStoppedState = { events += "publish-stopped" },
                acknowledgeInterfaceClosed = { events += "acknowledge" },
                cleanupAuxiliaries = { events += "cleanup" },
            )
        }

        teardown()
        teardown()

        assertEquals(
            listOf(
                "close-interface", "publish-stopped", "acknowledge", "cleanup",
                "close-interface", "acknowledge",
            ),
            events,
        )
    }
}
