package com.v2ray.ang.core

import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.JsonUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun replacesExternalCustomRulesWhenCompactDatabaseIsMissing() {
        val config = JsonUtil.parseString(
            """
            {
              "routing": {
                "rules": [
                  {
                    "type": "field",
                    "ip": [
                      "ext:geoip-only-cn-private.dat:private",
                      "EXT:GEOIP-ONLY-CN-PRIVATE.DAT:CN",
                      "1.1.1.1"
                    ],
                    "outboundTag": "direct"
                  }
                ]
              }
            }
            """.trimIndent()
        )!!.asJsonObject

        assertTrue(
            GeoIpRuleResolver.normalizeCustomRouting(
                config,
                compactDatabaseAvailable = false,
            )
        )
        val ipRules = config.getAsJsonObject("routing")
            .getAsJsonArray("rules")[0].asJsonObject
            .getAsJsonArray("ip")
            .map { it.asString }
        assertEquals(listOf("geoip:private", "geoip:cn", "1.1.1.1"), ipRules)
    }

    @Test
    fun keepsExternalCustomRulesWhenCompactDatabaseIsAvailable() {
        val config = JsonUtil.parseString(
            """
            {
              "routing": {
                "rules": [
                  {
                    "type": "field",
                    "ip": [
                      "ext:geoip-only-cn-private.dat:private",
                      "geoip:cn"
                    ]
                  }
                ]
              }
            }
            """.trimIndent()
        )!!.asJsonObject

        assertFalse(
            GeoIpRuleResolver.normalizeCustomRouting(
                config,
                compactDatabaseAvailable = true,
            )
        )
        assertEquals(
            listOf("ext:geoip-only-cn-private.dat:private", "geoip:cn"),
            config.getAsJsonObject("routing")
                .getAsJsonArray("rules")[0].asJsonObject
                .getAsJsonArray("ip")
                .map { it.asString },
        )
    }
}
