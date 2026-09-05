package com.v2ray.ang.ui

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class MobileTinaFirstLaunchTextTest {
    @Test
    fun developerNoticeVaultDecryptsExactRequestedText() {
        assertArrayEquals(
            arrayOf(
                "Developed By ALIMTP",
                "@VPN963"
            ),
            arrayOf(q.a(9), q.a(10))
        )
    }
}
