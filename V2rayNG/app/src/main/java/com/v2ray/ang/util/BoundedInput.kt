package com.v2ray.ang.util

import java.io.ByteArrayOutputStream
import java.io.InputStream

internal fun InputStream.readBytesLimited(limit: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var total = 0
    while (true) {
        val size = read(buffer)
        if (size < 0) break
        total += size
        require(total <= limit) { "Input too large" }
        output.write(buffer, 0, size)
    }
    return output.toByteArray()
}
