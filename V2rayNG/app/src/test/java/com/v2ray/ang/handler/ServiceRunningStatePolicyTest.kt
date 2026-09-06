package com.v2ray.ang.handler

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceRunningStatePolicyTest {

    @Test
    fun cachedStoppedStateRemainsStoppedEvenIfSnapshotContainsService() {
        assertFalse(ServiceRunningStatePolicy.resolve(cachedRunning = false, servicePresent = true))
    }

    @Test
    fun cachedRunningStateIsKeptWhenCoreServiceExists() {
        assertTrue(ServiceRunningStatePolicy.resolve(cachedRunning = true, servicePresent = true))
    }

    @Test
    fun cachedRunningStateIsClearedWhenNoCoreServiceExists() {
        assertFalse(ServiceRunningStatePolicy.resolve(cachedRunning = true, servicePresent = false))
    }

    @Test
    fun cachedRunningStateIsPreservedWhenSnapshotIsUnavailable() {
        assertTrue(ServiceRunningStatePolicy.resolve(cachedRunning = true, servicePresent = null))
    }
}
