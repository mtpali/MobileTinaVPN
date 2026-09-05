package com.v2ray.ang.core

import com.v2ray.ang.AppConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class GeoIpRuleResolverTest {
    private val source = listOf(AppConfig.GEOIP_PRIVATE, AppConfig.GEOIP_CN, "1.1.1.1")

    @Test
    fun keepsBuiltInRulesWhenCompactDatabaseIsMissing() {
        assertEquals(source, GeoIpRuleResolver.resolve(source, compactDatabaseAvailable = false))
    }

    @Test
    fun usesExternalRulesWhenCompactDatabaseIsAvailable() {
        assertEquals(
            listOf(
                "ext:${AppConfig.GEOIP_ONLY_CN_PRIVATE_DAT}:private",
                "ext:${AppConfig.GEOIP_ONLY_CN_PRIVATE_DAT}:cn",
                "1.1.1.1",
            ),
            GeoIpRuleResolver.resolve(source, compactDatabaseAvailable = true),
        )
    }
}
