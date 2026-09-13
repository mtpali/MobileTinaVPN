package com.v2ray.ang.core

import org.junit.Assert.*
import org.junit.Test

class AmneziaResumePolicyTest {
    @Test fun longSleepRecoversOnce() {
        val policy = AmneziaResumePolicy()
        policy.screenOff(1_000)
        policy.screenOff(250_000)
        assertTrue(policy.screenOn(301_000))
        assertFalse(policy.screenOn(302_000))
    }
    @Test fun shortSleepAndUnmatchedWakeDoNotDisruptTraffic() {
        val policy = AmneziaResumePolicy()
        assertFalse(policy.screenOn(1_000))
        policy.screenOff(2_000)
        assertFalse(policy.screenOn(3_000))
    }
    @Test fun stopDiscardsPendingResume() {
        val policy = AmneziaResumePolicy()
        policy.screenOff(0)
        policy.reset()
        assertFalse(policy.screenOn(300_000))
    }
}
