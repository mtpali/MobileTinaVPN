package com.v2ray.ang.core

internal object WireguardReservedPolicy {

    /**
     * Xray applies a configured reserved triplet to every outbound packet. Passing
     * the WireGuard default (0,0,0) is therefore not equivalent to omitting it for
     * AmneziaWG: it overwrites bytes in custom I1-I5 signature packets.
     */
    fun outboundBytes(reserved: String?): List<Int>? {
        val values = reserved
            ?.split(',')
            ?.map { it.trim().toIntOrNull() ?: return null }
            ?.takeIf { it.size == 3 && it.all { value -> value in 0..255 } }
            ?: return null

        return values.takeUnless { it.all { value -> value == 0 } }
    }
}
