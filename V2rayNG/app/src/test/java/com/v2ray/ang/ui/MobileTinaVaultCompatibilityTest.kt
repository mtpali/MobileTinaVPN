package com.v2ray.ang.ui

import java.security.MessageDigest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MobileTinaVaultCompatibilityTest {
    @Test
    fun `subscription brand decrypts after an app version upgrade`() {
        // The original encrypted record must keep its exact value on 2.3.12 and later.
        // A release VERSION_NAME must never silently change the record's AES-GCM key.
        val expected = "0c22a1a1888d3a1e50747658df605176404ff9fdf10d302562139af2682d8b75"
        repeat(2) {
            assertEquals(expected, digest(listOf(w.a())))
        }
    }

    @Test
    fun `social records preserve the restored Instagram and remaining Telegram contact`() {
        assertEquals(
            "5c03cc24a1aba6bec03d50087195fd7ffeec75de09ae1562a4478979e5ff19a7",
            digest((0..10).map(q::a))
        )
    }

    @Test
    fun `all hidden subscription records retain their contents`() {
        assertEquals(
            "1efe67fec9ea0124f28f03fcc67738cec4d6970ef1951151dcb20cd18263fbd7",
            digest((0..7).map(r::a))
        )
    }

    // Keep protected text out of fixtures and failure output. These digests were taken
    // from the preceding working release, including a NUL separator after each record.
    private fun digest(values: List<String>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        values.forEach {
            digest.update(it.toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
