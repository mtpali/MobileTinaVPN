package com.v2ray.ang.core

import com.google.gson.Gson
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.SubscriptionItem
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class UpstreamProfileCompatibilityTest {
    @Test fun `previous AWG profile survives decode edit and encode`() {
        val gson = Gson()
        val oldJson = """{"configVersion":3,"subscriptionId":"","addedTime":0,"remarks":"legacy","configType":"WIREGUARD","isAmneziaWG":true,"localAddress":"10.0.0.2/32","allowedIPs":"0.0.0.0/0","keepAlive":1500,"reserved":"0,0,0","awgJc":4,"awgH1":"1-4","awgI1":"<b 0x01020304>"}"""
        val profile = gson.fromJson(oldJson, ProfileItem::class.java)
        val restored = gson.fromJson(gson.toJson(profile.copy(remarks = "updated", remoteDNS = "1.1.1.1")), ProfileItem::class.java)
        assertTrue(restored.isAmneziaWG)
        assertEquals(4, restored.awgJc)
        assertEquals("1-4", restored.awgH1)
        assertEquals("<b 0x01020304>", restored.awgI1)
        assertEquals(1500, restored.keepAlive)
        assertEquals("0.0.0.0/0", restored.allowedIPs)
        assertEquals("1.1.1.1", restored.remoteDNS)
        assertNull(WireguardReservedPolicy.outboundBytes(restored.reserved))
    }

    @Test fun `old subscription metadata and new headers coexist`() {
        val gson = Gson()
        val oldJson = """{"remarks":"group","url":"https://example.invalid/sub","trafficTotalBytes":4096,"commentExpireEpochSeconds":2000000000}"""
        val item = gson.fromJson(oldJson, SubscriptionItem::class.java)
        val withHeaders = item.copy(requestHeaders = """{"X-Test":"migration"}""")
        val restored = gson.fromJson(gson.toJson(withHeaders), SubscriptionItem::class.java)
        assertEquals(4096L, restored.trafficTotalBytes)
        assertEquals(2000000000L, restored.commentExpireEpochSeconds)
        assertEquals(withHeaders.requestHeaders, restored.requestHeaders)
    }
}
