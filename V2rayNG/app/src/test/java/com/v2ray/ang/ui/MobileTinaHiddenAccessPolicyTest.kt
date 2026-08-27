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
    fun otherSubscriptionLinksKeepLongHoldAccess() {
        assertFalse(MobileTinaHiddenAccessPolicy.requiresMultiTap("https://example.com/sub"))
        assertFalse(MobileTinaHiddenAccessPolicy.requiresMultiTap("https://github.com/example/repo"))
        assertFalse(MobileTinaHiddenAccessPolicy.requiresMultiTap(""))
    }
}
