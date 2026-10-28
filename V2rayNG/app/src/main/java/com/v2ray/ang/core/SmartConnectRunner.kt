package com.v2ray.ang.core

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** The same selection/start policy for Auto and the widget, owned by the core service. */
internal class SmartConnectRunner(private val runtime: Runtime) {
    interface Runtime {
        suspend fun servers(): List<String>
        fun startProbe(batchId: Long, servers: List<String>)
        fun probeStarted(batchId: Long): Boolean
        suspend fun results(servers: List<String>): Map<String, Long>
        fun cancelProbe()
        suspend fun startSelected(guid: String)
        fun isRunning(): Boolean
        fun now(): Long
        suspend fun pause(millis: Long)
    }

    enum class Result { CONNECTED, NO_SERVERS, NO_WORKING_SERVER, START_TIMEOUT }

    suspend fun run(firstBatchId: Long): Result {
        val servers = runtime.servers().distinct()
        currentCoroutineContext().ensureActive()
        if (servers.isEmpty()) return Result.NO_SERVERS
        val selected = if (servers.size == 1) servers.single() else {
            probe(firstBatchId, servers, FIRST_PING_TIMEOUT_MS) ?: run {
                runtime.pause(RETRY_DELAY_MS)
                probe(firstBatchId + 1L, servers, RETRY_PING_TIMEOUT_MS)
            }
        } ?: return Result.NO_WORKING_SERVER

        currentCoroutineContext().ensureActive()
        runtime.startSelected(selected)
        val deadline = runtime.now() + START_CONFIRM_TIMEOUT_MS
        while (runtime.now() < deadline) {
            currentCoroutineContext().ensureActive()
            if (runtime.isRunning()) return Result.CONNECTED
            runtime.pause(RESULT_POLL_MS)
        }
        return if (runtime.isRunning()) Result.CONNECTED else Result.START_TIMEOUT
    }

    private suspend fun probe(batchId: Long, servers: List<String>, timeout: Long): String? {
        currentCoroutineContext().ensureActive()
        runtime.startProbe(batchId, servers)
        val deadline = runtime.now() + timeout
        try {
            // Never choose a previous test's persisted result before this batch acknowledges
            // clearing its results. Both request identity and batch identity are checked by IPC.
            while (!runtime.probeStarted(batchId)) {
                currentCoroutineContext().ensureActive()
                if (runtime.now() >= deadline) return null
                runtime.pause(BATCH_START_POLL_MS)
            }
            var firstPositiveAt: Long? = null
            var results = emptyMap<String, Long>()
            while (runtime.now() < deadline) {
                currentCoroutineContext().ensureActive()
                results = runtime.results(servers)
                if (results.values.any { it > 0L } && firstPositiveAt == null) firstPositiveAt = runtime.now()
                if (servers.all { (results[it] ?: 0L) != 0L } ||
                    firstPositiveAt?.let { runtime.now() - it >= FAST_SETTLE_MS } == true
                ) break
                runtime.pause(RESULT_POLL_MS)
            }
            return servers.mapNotNull { guid -> results[guid]?.takeIf { it > 0L }?.let { guid to it } }
                .minByOrNull { it.second }?.first
        } finally {
            runtime.cancelProbe()
        }
    }

    companion object {
        const val FIRST_PING_TIMEOUT_MS = 6_000L
        const val RETRY_PING_TIMEOUT_MS = 5_000L
        const val RETRY_DELAY_MS = 300L
        const val START_CONFIRM_TIMEOUT_MS = 15_000L
        private const val BATCH_START_POLL_MS = 20L
        private const val RESULT_POLL_MS = 60L
        private const val FAST_SETTLE_MS = 350L
    }
}
