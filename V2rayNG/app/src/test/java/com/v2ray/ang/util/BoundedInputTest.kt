package com.v2ray.ang.util

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class BoundedInputTest {
    @Test fun acceptsTheExactLimitWithoutCorruptingUtf8() {
        val bytes = "اشتراک\nvless://example".toByteArray()
        assertArrayEquals(bytes, ByteArrayInputStream(bytes).readBytesLimited(bytes.size))
    }
    @Test fun rejectsOversizedImportsBeforeTheyReachTheParser() {
        assertThrows(IllegalArgumentException::class.java) { ByteArrayInputStream(ByteArray(9000)).readBytesLimited(8192) }
    }
}
