package com.v2ray.ang.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileTinaHiddenAccessPolicyTest {
    @Test
    fun rawGitHubSubscriptionsRequireMultiTap() {
        assertTrue(
            MobileTinaHiddenAccessPolicy.requiresMultiTap(
                "https://raw.githubusercontent.com/example/repository/main/subscription.txt"
            )
        )
        assertTrue(
            MobileTinaHiddenAccessPolicy.requiresMultiTap(
                "  HTTPS://RAW.GITHUBUSERCONTENT.COM/example/repository/main/config.json"
            )
        )
    }

    @Test
    fun onlyRawGitHubLinksMayRevealAtExactlyFifteenTaps() {
        assertFalse(MobileTinaHiddenAccessPolicy.requiresMultiTap("https://example.com/sub"))
        assertFalse(MobileTinaHiddenAccessPolicy.requiresMultiTap("https://github.com/example/repo"))
        assertFalse(MobileTinaHiddenAccessPolicy.requiresMultiTap(""))
        assertFalse(
            MobileTinaHiddenAccessPolicy.shouldRevealAfterTap(
                "https://raw.githubusercontent.com/example/repo/main/sub",
                14
            )
        )
        assertTrue(
            MobileTinaHiddenAccessPolicy.shouldRevealAfterTap(
                "https://raw.githubusercontent.com/example/repo/main/sub",
                15
            )
        )
        assertFalse(MobileTinaHiddenAccessPolicy.shouldRevealAfterTap("https://example.com/sub", 15))
    }
}
