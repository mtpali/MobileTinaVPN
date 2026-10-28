package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.entities.SubscriptionUsageInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MainSubscriptionInfoTest {
    private val now = 1_000_000_000_000L // ms

    @Test
    fun limitedTrafficComputesFraction() {
        val model = buildSubscriptionInfoUiModel(SubscriptionUsageInfo(upload = 100, download = 300, total = 1000), now)
        assertEquals(400, model.usedBytes)
        assertEquals(0.4f, model.trafficFraction!!, 0.0001f)
        assertFalse(model.trafficLow)
        assertFalse(model.trafficExhausted)
    }

    @Test
    fun unlimitedTrafficHasNoFraction() {
        val model = buildSubscriptionInfoUiModel(SubscriptionUsageInfo(download = 5), now)
        assertEquals(0L, model.totalBytes)
        assertNull(model.trafficFraction)
        assertFalse(model.trafficLow)
        assertFalse(model.trafficExhausted)
    }

    @Test
    fun overUsageIsClampedAndFlaggedLow() {
        val model = buildSubscriptionInfoUiModel(SubscriptionUsageInfo(download = 2000, total = 1000), now)
        assertEquals(1f, model.trafficFraction!!, 0.0001f)
        assertTrue(model.trafficLow)
        assertTrue(model.trafficExhausted)
    }

    @Test
    fun quotaExhaustionTakesEffectAtExactLimitBeforeTimeExpiry() {
        val model = buildSubscriptionInfoUiModel(
            SubscriptionUsageInfo(upload = 400, download = 600, total = 1000, expire = now / 1000 + 86_400),
            now,
        )
        assertTrue(model.trafficExhausted)
        assertFalse(model.expired)
        assertEquals(1f, model.trafficFraction)
    }

    @Test
    fun exhaustedQuotaIsVisibleWithoutAnExpiryDateButUnknownQuotaIsNotExhausted() {
        val exhausted = buildSubscriptionInfoUiModel(SubscriptionUsageInfo(download = 1000, total = 1000), now)
        assertTrue(exhausted.trafficExhausted)
        assertNull(exhausted.expireMillis)

        for (total in listOf(0L, -1L)) {
            val unlimited = buildSubscriptionInfoUiModel(SubscriptionUsageInfo(download = 2000, total = total), now)
            assertFalse(unlimited.trafficExhausted)
            assertNull(unlimited.trafficFraction)
        }
    }

    @Test
    fun expiryIsConvertedToMillisAndFlaggedWhenPast() {
        val future = buildSubscriptionInfoUiModel(SubscriptionUsageInfo(expire = now / 1000 + 60), now)
        assertEquals(now + 60_000, future.expireMillis)
        assertFalse(future.expired)
        assertTrue(buildSubscriptionInfoUiModel(SubscriptionUsageInfo(expire = now / 1000 - 1), now).expired)
    }

    @Test
    fun zeroExpireMeansNeverExpires() {
        val model = buildSubscriptionInfoUiModel(SubscriptionUsageInfo(expire = 0), now)
        assertNull(model.expireMillis)
        assertFalse(model.expired)
    }
    @Test
    fun expiryLabelChangesAtTheExactTimestampWhileQuotaRemains() {
        val expiry = now / 1000 + 10
        val info = SubscriptionUsageInfo(total = 1000, download = 10, expire = expiry)
        val before = buildSubscriptionInfoUiModel(info, expiry * 1000 - 1)
        assertEquals(SubscriptionValidityLabel.REMAINING, before.validityLabel)
        assertEquals(1L, before.remainingDays)
        assertEquals(1L, before.nextRefreshDelayMillis)
        for (time in listOf(expiry * 1000, expiry * 1000 + 1)) {
            val model = buildSubscriptionInfoUiModel(info, time)
            assertEquals(SubscriptionValidityLabel.EXPIRED, model.validityLabel)
            assertTrue(model.expired)
            assertEquals(0L, model.remainingDays)
            assertNull(model.nextRefreshDelayMillis)
        }
    }

    @Test
    fun exhaustedQuotaRemainsUntilTimeEndsThenExpiryHasPriority() {
        val info = SubscriptionUsageInfo(total = 1000, download = 1000, expire = now / 1000 + 60)
        val before = buildSubscriptionInfoUiModel(info, now)
        assertEquals(SubscriptionValidityLabel.TRAFFIC_EXHAUSTED, before.validityLabel)
        assertEquals(60_000L, before.nextRefreshDelayMillis)
        assertEquals(SubscriptionValidityLabel.EXPIRED, buildSubscriptionInfoUiModel(info, now + 60_000L).validityLabel)
        assertEquals(SubscriptionValidityLabel.TRAFFIC_EXHAUSTED,
            buildSubscriptionInfoUiModel(info.copy(expire = 0L), now).validityLabel)
    }

    @Test
    fun unknownOrUnlimitedExpiryDoesNotScheduleOrDisplayAnEndedSubscription() {
        for (expiry in listOf(0L, -1L)) {
            val model = buildSubscriptionInfoUiModel(SubscriptionUsageInfo(expire = expiry), now)
            assertEquals(SubscriptionValidityLabel.HIDDEN, model.validityLabel)
            assertFalse(model.expired)
            assertNull(model.remainingDays)
            assertNull(model.nextRefreshDelayMillis)
        }
    }

    @Test
    fun daysAndNextRefreshFollowTheExactDayBoundaryWithoutPolling() {
        val expiry = now / 1000 + 86_400
        val info = SubscriptionUsageInfo(expire = expiry)
        val dayPlusOne = buildSubscriptionInfoUiModel(info, now - 1)
        assertEquals(2L, dayPlusOne.remainingDays)
        assertEquals(1L, dayPlusOne.nextRefreshDelayMillis)
        val exactDay = buildSubscriptionInfoUiModel(info, now)
        assertEquals(1L, exactDay.remainingDays)
        assertEquals(86_400_000L, exactDay.nextRefreshDelayMillis)
        assertEquals(SubscriptionValidityLabel.EXPIRED, buildSubscriptionInfoUiModel(info, now + 86_400_000L).validityLabel)
    }

    @Test
    fun veryLargeExpiryCannotOverflowIntoAnExpiredSubscription() {
        val model = buildSubscriptionInfoUiModel(SubscriptionUsageInfo(expire = Long.MAX_VALUE), now)
        assertFalse(model.expired)
        assertTrue(model.expireMillis!! > now)
        assertTrue(model.remainingDays!! > 0L)
        assertTrue(model.nextRefreshDelayMillis!! in 1L..86_400_000L)
    }

}
