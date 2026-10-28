package com.v2ray.ang.core

import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SmartConnectRunnerTest {
    private class FakeRuntime(val profiles: List<String> = listOf("slow", "fast")) : SmartConnectRunner.Runtime {
        var time = 0L
        var round = 0
        var acknowledged = true
        var connected = true
        var resultsRead = 0
        var cancellations = 0
        val batches = mutableListOf<Long>()
        val starts = mutableListOf<String>()
        var snapshot: (Int, Long) -> Map<String, Long> = { _, _ -> mapOf("slow" to 120L, "fast" to 20L) }
        var onPause: suspend () -> Unit = {}
        override suspend fun servers() = profiles
        override fun startProbe(batchId: Long, servers: List<String>) { round++; batches += batchId }
        override fun probeStarted(batchId: Long) = acknowledged
        override suspend fun results(servers: List<String>): Map<String, Long> { resultsRead++; return snapshot(round, time) }
        override fun cancelProbe() { cancellations++ }
        override suspend fun startSelected(guid: String) { currentCoroutineContext().ensureActive(); starts += guid }
        override fun isRunning() = connected
        override fun now() = time
        override suspend fun pause(millis: Long) { time += millis; onPause() }
    }

    @Test
    fun oneServerStartsImmediatelyWithoutAProbe() {
        runBlocking {
            val runtime = FakeRuntime(listOf("only"))
            assertEquals(SmartConnectRunner.Result.CONNECTED, SmartConnectRunner(runtime).run(10L))
            assertEquals(listOf("only"), runtime.starts)
            assertTrue(runtime.batches.isEmpty())
            assertEquals(0L, runtime.time)
        }
    }

    @Test
    fun multipleServersSelectTheLowestPositiveRealPing() {
        runBlocking {
            val runtime = FakeRuntime(listOf("slow", "fast", "failed"))
            runtime.snapshot = { _, _ -> mapOf("slow" to 120L, "fast" to 20L, "failed" to -1L) }
            assertEquals(SmartConnectRunner.Result.CONNECTED, SmartConnectRunner(runtime).run(10L))
            assertEquals(listOf("fast"), runtime.starts)
            assertEquals(listOf(10L), runtime.batches)
            assertEquals(1, runtime.cancellations)
        }
    }

    @Test
    fun unavailableBatchAcknowledgementNeverUsesStalePositiveResults() {
        runBlocking {
            val runtime = FakeRuntime()
            runtime.acknowledged = false
            assertEquals(SmartConnectRunner.Result.NO_WORKING_SERVER, SmartConnectRunner(runtime).run(40L))
            assertEquals(listOf(40L, 41L), runtime.batches)
            assertEquals(0, runtime.resultsRead)
            assertTrue(runtime.starts.isEmpty())
            assertEquals(2, runtime.cancellations)
        }
    }

    @Test
    fun aFailedFirstRoundRetriesInsideTheSameClickWithANewBatch() {
        runBlocking {
            val runtime = FakeRuntime()
            runtime.snapshot = { round, _ -> if (round == 1) mapOf("slow" to -1L, "fast" to -1L)
                else mapOf("slow" to 50L, "fast" to 10L) }
            assertEquals(SmartConnectRunner.Result.CONNECTED, SmartConnectRunner(runtime).run(60L))
            assertEquals(listOf(60L, 61L), runtime.batches)
            assertEquals(listOf("fast"), runtime.starts)
            assertEquals(SmartConnectRunner.RETRY_DELAY_MS, runtime.time)
        }
    }

    @Test
    fun aPositiveProbeSettlesWithoutWaitingForEveryUnresponsiveServer() {
        runBlocking {
            val runtime = FakeRuntime()
            runtime.snapshot = { _, _ -> mapOf("fast" to 12L, "slow" to 0L) }
            assertEquals(SmartConnectRunner.Result.CONNECTED, SmartConnectRunner(runtime).run(70L))
            assertTrue(runtime.time in 350L..410L)
            assertEquals(listOf("fast"), runtime.starts)
        }
    }

    @Test
    fun noProfilesOrNoPositiveResultsNeverStartThePreviousSelection() {
        runBlocking {
            val empty = FakeRuntime(emptyList())
            assertEquals(SmartConnectRunner.Result.NO_SERVERS, SmartConnectRunner(empty).run(80L))
            assertTrue(empty.starts.isEmpty())
            val failed = FakeRuntime()
            failed.snapshot = { _, _ -> mapOf("slow" to -1L, "fast" to -1L) }
            assertEquals(SmartConnectRunner.Result.NO_WORKING_SERVER, SmartConnectRunner(failed).run(90L))
            assertTrue(failed.starts.isEmpty())
        }
    }

    @Test
    fun cancellationDuringTestingClosesTheProbeAndCannotStartLater() {
        runBlocking {
            val runtime = FakeRuntime()
            runtime.snapshot = { _, _ -> emptyMap() }
            runtime.onPause = { currentCoroutineContext().cancel() }
            val job = launch { SmartConnectRunner(runtime).run(100L) }
            job.join()
            assertTrue(job.isCancelled)
            assertEquals(1, runtime.cancellations)
            assertTrue(runtime.starts.isEmpty())
        }
    }

    @Test
    fun coreStartupMustBeConfirmedWithinItsOwnFifteenSecondWindow() {
        runBlocking {
            val runtime = FakeRuntime(listOf("only"))
            runtime.connected = false
            assertEquals(SmartConnectRunner.Result.START_TIMEOUT, SmartConnectRunner(runtime).run(110L))
            assertEquals(SmartConnectRunner.START_CONFIRM_TIMEOUT_MS, runtime.time)
            assertEquals(listOf("only"), runtime.starts)
            assertFalse(runtime.isRunning())
        }
    }
}
