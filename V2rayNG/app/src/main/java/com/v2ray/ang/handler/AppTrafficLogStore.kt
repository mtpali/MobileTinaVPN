package com.v2ray.ang.handler

import com.google.gson.Gson
import com.tencent.mmkv.MMKV
import com.v2ray.ang.dto.entities.AppTrafficLogEntry
import com.v2ray.ang.dto.entities.AppTrafficLogSession
import com.v2ray.ang.dto.entities.AppTrafficLogState
import com.v2ray.ang.dto.entities.AppTrafficLogWindow

/** A separate multi-process store keeps log clearing independent from profile/index locks. */
internal object AppTrafficLogStore {
    private val storage by lazy { MMKV.mmkvWithID("APP_TRAFFIC_LOG_V1", MMKV.MULTI_PROCESS_MODE) }
    private val gson = Gson()

    private fun load(): AppTrafficLogState =
        runCatching { gson.fromJson(storage.decodeString("state"), AppTrafficLogState::class.java) }.getOrNull()
            ?: AppTrafficLogState()

    private fun save(state: AppTrafficLogState) {
        check(storage.encode("state", gson.toJson(state))) { "Traffic log storage write failed" }
    }

    private inline fun <T> locked(block: () -> T): T = synchronized(storage) {
        storage.lock()
        try { block() } finally { storage.unlock() }
    }

    fun begin(session: AppTrafficLogSession, pending: Set<String>) = locked {
        val state = load()
        state.begin(session, pending)
        save(state)
    }

    fun window(id: String, nowMillis: Long): AppTrafficLogWindow? = locked { load().window(id, nowMillis) }

    fun record(window: AppTrafficLogWindow, totals: Map<Int, Long>, identities: Map<Int, AppTrafficLogEntry>) = locked {
        val state = load()
        if (state.record(window, totals, identities)) save(state)
    }

    fun end(id: String, nowMillis: Long) = locked {
        val state = load()
        state.sessions[id]?.let {
            it.endMillis = nowMillis
            save(state)
        }
    }

    fun finish(id: String) = locked {
        val state = load()
        if (state.sessions.remove(id) != null) save(state)
    }

    fun entries(): List<AppTrafficLogEntry> = locked {
        load().entries.values.filter { it.bytes > 0L }
            .sortedWith(compareByDescending<AppTrafficLogEntry> { it.bytes }.thenBy { it.label })
    }

    fun clear(nowMillis: Long, endSessions: Boolean) = locked {
        val state = load()
        state.clear(nowMillis, endSessions)
        save(state)
        storage.sync()
    }
}
