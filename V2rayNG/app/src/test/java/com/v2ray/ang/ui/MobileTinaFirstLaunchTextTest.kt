package com.v2ray.ang.ui

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class MobileTinaFirstLaunchTextTest {
    @Test
    fun launchNoticeContainsOnlyTheTwoRetainedInstagramButtons() {
        assertArrayEquals(
            arrayOf("instagram 1 : mobile.tina", "instagram 2 : mobile.tina2"),
            MobileTinaFirstLaunchDialog.noticeLines()
        )
    }

    @Test
    fun thirdAboutButtonIsRestoredWhileTelegramContactIsRetained() {
        assertEquals("https://www.instagram.com/mobile.tinaa/", q.a(2))
        assertEquals("اینستاگرام سوم", q.a(6))
        assertEquals("https://t.me/mobile_tina", q.a(3))
        assertEquals("ارتباط در تلگرام", q.a(7))
    }
}
