package com.v2ray.ang.core

import com.v2ray.ang.AppConfig

/** Keeps routing usable when the optional compact CN/private GeoIP database is unavailable. */
object GeoIpRuleResolver {
    fun resolve(ipRules: List<String>, compactDatabaseAvailable: Boolean): List<String> {
        if (!compactDatabaseAvailable) return ipRules

        return ipRules.map { rule ->
            when (rule) {
                AppConfig.GEOIP_CN -> "ext:${AppConfig.GEOIP_ONLY_CN_PRIVATE_DAT}:cn"
                AppConfig.GEOIP_PRIVATE ->
                    "ext:${AppConfig.GEOIP_ONLY_CN_PRIVATE_DAT}:private"
                else -> rule
            }
        }
    }
}
