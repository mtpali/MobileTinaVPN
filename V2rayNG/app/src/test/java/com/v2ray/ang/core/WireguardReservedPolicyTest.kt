package com.v2ray.ang.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WireguardReservedPolicyTest {

    @Test
    fun missingOrZeroReservedIsNotSentToCore() {
        assertNull(WireguardReservedPolicy.outboundBytes(null))
        assertNull(WireguardReservedPolicy.outboundBytes(""))
        assertNull(WireguardReservedPolicy.outboundBytes("0,0,0"))
        assertNull(WireguardReservedPolicy.outboundBytes(" 0, 0, 0 "))
    }

    @Test
    fun nonZeroReservedTripletIsPreserved() {
        assertEquals(
            listOf(6, 7, 8),
            WireguardReservedPolicy.outboundBytes("6, 7, 8"),
        )
    }

    @Test
    fun malformedReservedIsNotSentToCore() {
        assertNull(WireguardReservedPolicy.outboundBytes("1,2"))
        assertNull(WireguardReservedPolicy.outboundBytes("1,2,256"))
        assertNull(WireguardReservedPolicy.outboundBytes("1,two,3"))
    }
}
