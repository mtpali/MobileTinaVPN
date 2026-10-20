package com.v2ray.ang.ui

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SubscriptionQrClickCounterTest {
    @Test
    fun onlyTheTenthTapRevealsAndTheNextSeriesStartsFresh() {
        val counter = SubscriptionQrClickCounter()
        for (tap in 1..9) assertFalse(counter.registerClick("one", tap * 100L))
        assertTrue(counter.registerClick("one", 1_000L))
        assertFalse(counter.registerClick("one", 1_100L))
    }

    @Test
    fun tapsOnDifferentSubscriptionsAndLongPausesDoNotAccumulate() {
        val counter = SubscriptionQrClickCounter()
        for (tap in 1..9) assertFalse(counter.registerClick("one", tap * 100L))
        assertFalse(counter.registerClick("two", 1_000L))
        for (tap in 1..8) assertFalse(counter.registerClick("two", 1_000L + tap * 100))
        assertFalse(counter.registerClick("two", 4_000L))
        for (tap in 1..8) assertFalse(counter.registerClick("two", 4_000L + tap * 100))
        assertTrue(counter.registerClick("two", 4_900L))
    }

    @Test
    fun leavingTheModeInvalidIdsAndClockResetClearAnUnfinishedSeries() {
        val counter = SubscriptionQrClickCounter()
        for (tap in 1..9) counter.registerClick("one", tap * 100L)
        counter.reset()
        assertFalse(counter.registerClick("one", 1_000L))
        for (tap in 1..8) counter.registerClick("one", 1_000L + tap * 100)
        assertFalse(counter.registerClick("", 1_900L))
        assertFalse(counter.registerClick("one", 2_000L))
        for (tap in 1..8) counter.registerClick("one", 2_000L + tap * 100)
        assertFalse(counter.registerClick("one", 1L))
    }
}
