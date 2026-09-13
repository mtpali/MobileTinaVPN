package com.v2ray.ang.util

import org.junit.Assert.assertEquals
import org.junit.Test

class MobileTinaImportNormalizerTest {

    @Test
    fun removesClipboardFormattingCharactersBeforeRawIni() {
        val raw = "\uFEFF\u200B  [Interface]\nAddress = 10.187.192.50/30\n\n[Peer]"

        assertEquals(
            "[Interface]\nAddress = 10.187.192.50/30\n\n[Peer]",
            MobileTinaImportNormalizer.normalize(raw),
        )
    }
}
