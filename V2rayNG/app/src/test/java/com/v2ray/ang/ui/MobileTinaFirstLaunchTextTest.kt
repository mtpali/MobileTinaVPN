package com.v2ray.ang.ui

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class MobileTinaFirstLaunchTextTest {
    @Test
    fun socialNoticeVaultDecryptsExactRequestedText() {
        assertArrayEquals(
            arrayOf(
                "instagram 1 : mobile.tina",
                "instagram 2 : mobile.tina2",
                "Telegram : mobile_tina"
            ),
            arrayOf(q.a(9), q.a(10), q.a(11))
        )
    }
    @Test
    fun telegramButtonsResolveToTheRequestedAccount() {
        assertEquals("https://t.me/mobile_tina", q.a(2))
        assertEquals("https://t.me/mobile_tina", q.a(3))
        assertEquals("ارتباط در تلگرام", q.a(7))
    }
}
