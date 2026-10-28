package com.v2ray.ang.dto.entities

/** Local aggregates only: no domains, destinations, packets or browsing history. */
internal data class AppTrafficLogEntry(
    val uid: Int = 0,
    val packages: List<String> = emptyList(),
    val label: String = "",
    var bytes: Long = 0L,
) {
    val key: String get() = "$uid:${packages.sorted().joinToString("|")}"
}

internal data class AppTrafficLogSession(
    val id: String = "",
    var startMillis: Long = 0L,
    var endMillis: Long = 0L,
    val selectedPackages: Set<String> = emptySet(),
    val bypass: Boolean = false,
    val sampled: MutableMap<Int, Long> = mutableMapOf(),
    var resetRequired: Boolean = false,
) {
    fun includes(packages: List<String>): Boolean = selectedPackages.isEmpty() ||
        if (bypass) packages.none { it in selectedPackages } else packages.any { it in selectedPackages }
}

internal data class AppTrafficLogWindow(
    val sessionId: String,
    val generation: Long,
    val startMillis: Long,
    val endMillis: Long,
    val resetRequired: Boolean = false,
)

internal data class AppTrafficLogState(
    var generation: Long = 0L,
    val entries: MutableMap<String, AppTrafficLogEntry> = mutableMapOf(),
    val sessions: MutableMap<String, AppTrafficLogSession> = mutableMapOf(),
) {
    fun begin(session: AppTrafficLogSession, pendingFinalSamples: Set<String>) {
        // After process death, do not charge the disconnected interval to a stale session.
        sessions.keys.retainAll(pendingFinalSamples)
        sessions[session.id] = session
    }

    fun window(id: String, nowMillis: Long): AppTrafficLogWindow? {
        val session = sessions[id] ?: return null
        val end = maxOf(session.startMillis, if (session.endMillis > 0L) minOf(nowMillis, session.endMillis) else nowMillis)
        return AppTrafficLogWindow(id, generation, session.startMillis, end, session.resetRequired)
    }

    fun acknowledgeReset(window: AppTrafficLogWindow): Boolean {
        val session = sessions[window.sessionId] ?: return false
        if (window.generation != generation || window.startMillis != session.startMillis) return false
        session.resetRequired = false
        return true
    }

    fun record(window: AppTrafficLogWindow, totals: Map<Int, Long>, identities: Map<Int, AppTrafficLogEntry>): Boolean {
        val session = sessions[window.sessionId] ?: return false
        if (session.resetRequired || window.generation != generation || window.startMillis != session.startMillis) return false
        var changed = false
        totals.forEach { (uid, value) ->
            val counter = value.coerceAtLeast(0L)
            val previous = session.sampled[uid] ?: 0L
            // Cumulative counters belong to one native core. Reject repeated/older snapshots.
            if (counter <= previous) return@forEach
            val identity = identities[uid] ?: return@forEach
            session.sampled[uid] = counter
            changed = true
            if (!session.includes(identity.packages)) return@forEach
            val entry = entries.getOrPut(identity.key) { identity.copy(bytes = 0L) }
            entry.bytes = saturatedTrafficSum(entry.bytes, counter - previous)
        }
        return changed
    }

    fun clear(nowMillis: Long, endSessions: Boolean) {
        generation++
        entries.clear()
        if (endSessions) {
            sessions.clear()
        } else {
            sessions.values.removeAll { it.endMillis > 0L }
            sessions.values.forEach {
                it.startMillis = nowMillis
                it.sampled.clear()
                it.resetRequired = true
            }
        }
    }
}

internal fun saturatedTrafficSum(first: Long, second: Long): Long {
    val a = first.coerceAtLeast(0L)
    val b = second.coerceAtLeast(0L)
    return if (Long.MAX_VALUE - a < b) Long.MAX_VALUE else a + b
}
