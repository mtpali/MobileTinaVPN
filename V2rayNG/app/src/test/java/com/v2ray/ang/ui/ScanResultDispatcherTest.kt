package com.v2ray.ang.ui

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ScanResultDispatcherTest {
    @Test fun releasesCameraFrameBeforeEvenImmediateResultCallback() {
        val events = mutableListOf<String>()
        val gate = ScanResultDispatcher({ it() }, { true }) { events += "finish:$it" }
        gate.analyze({ "qr" }, { events += "close" })
        assertEquals(listOf("close", "finish:qr"), events)
    }
    @Test fun consumesOneResultAndClosesAllFollowingFrames() {
        val tasks = mutableListOf<() -> Unit>()
        val results = mutableListOf<String>()
        var closed = 0
        var decoded = 0
        val gate = ScanResultDispatcher({ tasks += it }, { true }, results::add)
        repeat(5) { gate.analyze({ decoded++; "qr" }, { closed++ }) }
        assertEquals(5, closed)
        assertEquals(1, decoded)
        tasks.forEach { it() }
        assertEquals(listOf("qr"), results)
    }
    @Test fun closingScannerDiscardsQueuedCallbacksWithoutLeakingTheFrame() {
        val tasks = mutableListOf<() -> Unit>()
        var delivered = false
        var closed = false
        val gate = ScanResultDispatcher({ tasks += it }, { true }) { delivered = true }
        gate.analyze({ "qr" }, { closed = true })
        gate.dispose()
        tasks.forEach { it() }
        assertTrue(closed)
        assertFalse(delivered)
    }
    @Test fun resumingAfterBackgroundResultAllowsAnotherFrame() {
        var resumed = false
        val results = mutableListOf<String>()
        val gate = ScanResultDispatcher({ it() }, { resumed }, results::add)
        gate.analyze({ "background" }, {})
        resumed = true
        gate.analyze({ "foreground" }, {})
        assertEquals(listOf("foreground"), results)
    }
    @Test fun failedDecodeAlwaysClosesAndDoesNotDisableFutureScanning() {
        var closes = 0
        val results = mutableListOf<String>()
        val gate = ScanResultDispatcher({ it() }, { true }, results::add)
        gate.analyze({ error("bad frame") }, { closes++ })
        gate.analyze({ "retry" }, { closes++ })
        assertEquals(2, closes)
        assertEquals(listOf("retry"), results)
    }
}
