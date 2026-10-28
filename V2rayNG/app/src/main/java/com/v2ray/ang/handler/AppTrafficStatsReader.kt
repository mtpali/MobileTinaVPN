package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.saturatedTrafficSum

/** Only this VPN core's cumulative payload counters, never Android network history. */
internal object AppTrafficStatsReader {
    fun parse(payload: String): Map<Int, Long> = buildMap {
        payload.split(';').forEach { item ->
            if (item.isEmpty()) return@forEach
            val fields = item.split(',')
            if (fields.size != 3) return@forEach
            val uid = fields[0].toIntOrNull()?.takeIf { it >= 0 } ?: return@forEach
            val up = fields[1].toLongOrNull()?.takeIf { it >= 0L } ?: return@forEach
            val down = fields[2].toLongOrNull()?.takeIf { it >= 0L } ?: return@forEach
            val total = saturatedTrafficSum(up, down)
            if (total > 0L) put(uid, saturatedTrafficSum(get(uid) ?: 0L, total))
        }
    }
}
