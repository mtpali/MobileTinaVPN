package com.v2ray.ang.handler

import com.google.gson.Gson
import com.v2ray.ang.dto.entities.AppTrafficLogEntry
import com.v2ray.ang.dto.entities.AppTrafficLogSession
import com.v2ray.ang.dto.entities.AppTrafficLogState
import com.v2ray.ang.dto.entities.saturatedTrafficSum
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AppTrafficLogStateTest {
    private val telegram = AppTrafficLogEntry(10123, listOf("org.telegram.messenger"), "Telegram")
    private val weather = AppTrafficLogEntry(10124, listOf("example.weather"), "آب و هوا")
    private val identities = mapOf(telegram.uid to telegram, weather.uid to weather)

    private fun started() = AppTrafficLogState().apply { begin(AppTrafficLogSession("vpn", 100L), emptySet()) }

    @Test
    fun repeatedAndTemporarilySmallerSnapshotsDoNotDoubleCount() {
        val state = started()
        for (counter in listOf(100L, 100L, 80L, 140L)) {
            state.record(state.window("vpn", 200L)!!, mapOf(telegram.uid to counter), identities)
        }
        assertEquals(140L, state.entries[telegram.key]!!.bytes)
    }

    @Test
    fun historyAccumulatesAcrossConnectionsWithoutReusingTheDisconnectedWindow() {
        val state = started()
        val stale = state.window("vpn", 200L)!!
        state.record(stale, mapOf(telegram.uid to 100L), identities)
        state.begin(AppTrafficLogSession("next", 1000L), emptySet())
        assertFalse(state.record(stale, mapOf(telegram.uid to 8000L), identities))
        assertEquals(1000L, state.window("next", 1100L)!!.startMillis)
        state.record(state.window("next", 1100L)!!, mapOf(telegram.uid to 50L), identities)
        assertEquals(150L, state.entries[telegram.key]!!.bytes)
    }

    @Test
    fun clearingHistoryRejectsInFlightSamplesAndContinuesTheActiveConnection() {
        val state = started()
        val old = state.window("vpn", 200L)!!
        state.record(old, mapOf(telegram.uid to 100L), identities)
        state.clear(300L, endSessions = false)
        assertFalse(state.record(old, mapOf(telegram.uid to 5000L), identities))
        assertTrue(state.entries.isEmpty())
        val fresh = state.window("vpn", 400L)!!
        assertEquals(300L, fresh.startMillis)
        state.record(fresh, mapOf(telegram.uid to 20L), identities)
        assertEquals(20L, state.entries[telegram.key]!!.bytes)
    }

    @Test
    fun resetRetiresActiveAndClosingSessionsSoFinalSamplesCannotResurrectHistory() {
        val state = started()
        val old = state.window("vpn", 200L)!!
        state.sessions["vpn"]!!.endMillis = 250L
        state.clear(300L, endSessions = true)
        assertFalse(state.record(old, mapOf(telegram.uid to 100L), identities))
        assertNull(state.window("vpn", 500L))
        assertTrue(state.entries.isEmpty())
        assertTrue(state.sessions.isEmpty())
    }

    @Test
    fun aQuickReconnectKeepsTheBoundedFinalSampleOfThePreviousSession() {
        val state = started()
        state.sessions["vpn"]!!.endMillis = 250L
        state.begin(AppTrafficLogSession("next", 300L), setOf("vpn"))
        val final = state.window("vpn", 900L)!!
        assertEquals(250L, final.endMillis)
        state.record(final, mapOf(telegram.uid to 100L), identities)
        state.record(state.window("next", 500L)!!, mapOf(telegram.uid to 40L), identities)
        assertEquals(140L, state.entries[telegram.key]!!.bytes)
    }

    @Test
    fun perAppAllowlistAndBypassUsePackageMembershipWithoutChangingTheirDefaults() {
        for (bypass in listOf(false, true)) {
            val state = AppTrafficLogState()
            state.begin(AppTrafficLogSession("vpn", 100L, selectedPackages = setOf("org.telegram.messenger"), bypass = bypass), emptySet())
            state.record(state.window("vpn", 200L)!!, mapOf(telegram.uid to 100L, weather.uid to 20L), identities)
            assertEquals(if (bypass) setOf(weather.key) else setOf(telegram.key), state.entries.keys)
        }
        assertTrue(AppTrafficLogSession().includes(telegram.packages))
        assertTrue(AppTrafficLogSession(bypass = true).includes(weather.packages))
    }

    @Test
    fun sharedUidsHaveOneStableGroupAndReassignedUidsDoNotRenameOldHistory() {
        val shared = telegram.copy(packages = listOf("example.shared", "org.telegram.messenger"), label = "Shared / Telegram")
        assertEquals(shared.key, shared.copy(packages = shared.packages.reversed()).key)
        val state = started()
        state.record(state.window("vpn", 200L)!!, mapOf(shared.uid to 100L), mapOf(shared.uid to shared))
        state.begin(AppTrafficLogSession("next", 300L), emptySet())
        val reassigned = telegram.copy(packages = listOf("example.other"), label = "Other")
        state.record(state.window("next", 400L)!!, mapOf(reassigned.uid to 20L), mapOf(reassigned.uid to reassigned))
        assertEquals(2, state.entries.size)
        assertEquals(100L, state.entries[shared.key]!!.bytes)
        assertEquals(20L, state.entries[reassigned.key]!!.bytes)
    }

    @Test
    fun persistedAggregateAndActiveBaselineSurviveJsonRoundTrip() {
        val state = started()
        state.record(state.window("vpn", 200L)!!, mapOf(telegram.uid to 100L), identities)
        val gson = Gson()
        val restored = gson.fromJson(gson.toJson(state), AppTrafficLogState::class.java)
        restored.record(restored.window("vpn", 300L)!!, mapOf(telegram.uid to 120L), identities)
        assertEquals(120L, restored.entries[telegram.key]!!.bytes)
        assertEquals("Telegram", restored.entries[telegram.key]!!.label)
    }

    @Test
    fun invalidAndVeryLargeCountersCannotProduceNegativeUsage() {
        assertEquals(5L, saturatedTrafficSum(-1L, 5L))
        assertEquals(Long.MAX_VALUE, saturatedTrafficSum(Long.MAX_VALUE - 1, 10L))
        val state = started()
        state.record(state.window("vpn", 200L)!!, mapOf(telegram.uid to -1L), identities)
        assertTrue(state.entries.isEmpty())
    }
}
