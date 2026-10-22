package com.v2ray.ang.handler

import com.google.gson.Gson
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.dto.entities.SubscriptionUsageInfo
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MobileTinaSubscriptionMetadataTest {
    @Test fun `legacy records gain upstream metadata without losing custom fields`() {
        val gson = Gson()
        val item = gson.fromJson(
            """{"remarks":"protected brand","url":"https://example.invalid/sub","commentExpireEpochSeconds":2000000000,"requestHeaders":"{}","trafficTotalBytes":4096}""",
            SubscriptionItem::class.java
        )
        val info = SubscriptionUsageInfo(upload = 100, download = 200, total = 1000,
            expire = 1900000000, title = "panel title", supportUrl = "https://t.me/example")

        assertTrue(MobileTinaSubscriptionMetadata.apply(item, info))
        val restored = gson.fromJson(gson.toJson(item), SubscriptionItem::class.java)
        assertEquals(info, restored.usageInfo)
        assertEquals(100L, restored.trafficUploadBytes)
        assertEquals(200L, restored.trafficDownloadBytes)
        assertEquals(1000L, restored.trafficTotalBytes)
        assertEquals(1900000000L, restored.expireEpochSeconds)
        assertEquals(2000000000L, restored.commentExpireEpochSeconds)
        assertEquals("protected brand", restored.remarks)
        assertEquals("{}", restored.requestHeaders)
        assertEquals("https://example.invalid/sub", restored.url)
        assertFalse(MobileTinaSubscriptionMetadata.apply(item, info))
    }

    @Test fun `successful response without headers clears stale panel data but preserves custom expiry`() {
        val item = SubscriptionItem(commentExpireEpochSeconds = 2000000000)
        MobileTinaSubscriptionMetadata.apply(item, SubscriptionUsageInfo(total = 4096, expire = 1900000000))

        assertTrue(MobileTinaSubscriptionMetadata.apply(item, null))
        assertNull(item.usageInfo)
        assertNull(item.trafficUploadBytes)
        assertNull(item.trafficDownloadBytes)
        assertNull(item.trafficTotalBytes)
        assertNull(item.expireEpochSeconds)
        assertEquals(2000000000L, item.commentExpireEpochSeconds)
        assertFalse(MobileTinaSubscriptionMetadata.apply(item, null))
    }

    @Test fun `unlimited panel plan resets a previous quota`() {
        val item = SubscriptionItem(trafficTotalBytes = 4096, expireEpochSeconds = 1900000000)
        val info = SubscriptionUsageInfo(download = 500)
        MobileTinaSubscriptionMetadata.apply(item, info)
        assertNull(item.trafficTotalBytes)
        assertNull(item.expireEpochSeconds)
        assertEquals(500L, item.trafficDownloadBytes)
        assertEquals(info, item.usageInfo)
    }
}
