package com.v2ray.ang.service

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Keeps VPN teardown idempotent while preserving the safety-critical order.
 *
 * The Android VPN component and its TUN descriptor must be released before any native or
 * auxiliary cleanup that may block. Once the descriptor is closed, publishing the stopped state
 * is truthful even if a complex Xray configuration takes longer to release its own resources.
 */
internal class VpnTeardownCoordinator {
    private val cleanupClaimed = AtomicBoolean(false)

    fun teardown(
        forceServiceStop: Boolean,
        stopService: () -> Unit,
        closeVpnInterface: () -> Unit,
        publishStoppedState: () -> Unit,
        acknowledgeInterfaceClosed: () -> Unit,
        cleanupAuxiliaries: () -> Unit,
    ) {
        if (forceServiceStop) {
            stopService()
        }

        closeVpnInterface()

        if (!cleanupClaimed.compareAndSet(false, true)) {
            acknowledgeInterfaceClosed()
            return
        }

        publishStoppedState()
        acknowledgeInterfaceClosed()
        cleanupAuxiliaries()
    }
}
