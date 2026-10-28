package com.v2ray.ang.ui

import java.util.concurrent.atomic.AtomicBoolean

/** Owns frame release and exactly-once delivery, independent of CameraX/Activity timing. */
internal class ScanResultDispatcher(
    private val dispatch: (() -> Unit) -> Unit,
    private val active: () -> Boolean,
    private val onResult: (String) -> Unit,
) {
    private val disposed = AtomicBoolean(false)
    private val claimed = AtomicBoolean(false)

    fun analyze(decode: () -> String?, close: () -> Unit) {
        if (disposed.get() || claimed.get()) { close(); return }
        val result = try { decode() } catch (_: Exception) { null } finally { close() }
        if (result.isNullOrBlank() || !claimed.compareAndSet(false, true)) return
        dispatch {
            if (!disposed.get() && active()) onResult(result)
            else if (!disposed.get()) claimed.set(false)
        }
    }

    fun dispose() { disposed.set(true) }
}
