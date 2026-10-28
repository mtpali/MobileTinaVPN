package com.v2ray.ang.core

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.v2ray.ang.dto.ConfigResult
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class IranDomainBypassTest {
    private fun json(text: String) = JsonParser.parseString(text).asJsonObject
    private fun rules(config: JsonObject) = config.getAsJsonObject("routing").getAsJsonArray("rules")

    @Test
    fun exactSuffixRulePrecedesCatchAllWithoutMovingTheDefaultProxy() {
        val config = json("""{"outbounds":[{"tag":"proxy","protocol":"vless","settings":{"opaque":42}}],"routing":{"domainStrategy":"AsIs","rules":[{"network":"tcp,udp","outboundTag":"proxy"}],"balancers":[{"tag":"pool"}]}}""")
        val proxy = config.getAsJsonArray("outbounds")[0].deepCopy()
        val catchAll = rules(config)[0].deepCopy()
        IranDomainBypass.apply(config, false)
        assertEquals(proxy, config.getAsJsonArray("outbounds")[0])
        assertEquals(catchAll, rules(config)[1])
        assertEquals("domain:ir", rules(config)[0].asJsonObject.getAsJsonArray("domain").single().asString)
        assertEquals("freedom", config.getAsJsonArray("outbounds")[1].asJsonObject["protocol"].asString)
        assertEquals("AsIs", config.getAsJsonObject("routing")["domainStrategy"].asString)
        assertEquals("pool", config.getAsJsonObject("routing").getAsJsonArray("balancers")[0].asJsonObject["tag"].asString)
    }

    @Test
    fun reusesAnUnchainedCustomFreedomOutboundAndIsIdempotent() {
        val config = json("""{"outbounds":[{"tag":"proxy","protocol":"vless"},{"tag":"my-direct","protocol":"freedom","streamSettings":null}],"routing":{"rules":[]}}""")
        IranDomainBypass.apply(config, false)
        val once = config.deepCopy()
        IranDomainBypass.apply(config, false)
        assertEquals(once, config)
        assertEquals(2, config.getAsJsonArray("outbounds").size())
        assertEquals("my-direct", rules(config)[0].asJsonObject["outboundTag"].asString)
    }

    @Test
    fun freedomChainedThroughAProxyIsNotReusedAndTagCollisionsAreSafe() {
        val config = json("""{"outbounds":[{"tag":"mobiletina-ir-direct","protocol":"blackhole"},{"tag":"chained","protocol":"freedom","proxySettings":{"tag":"proxy"}},{"tag":"dialer","protocol":"freedom","streamSettings":{"sockopt":{"dialerProxy":"proxy"}}},{"tag":"redirected","protocol":"freedom","settings":{"redirect":"example.com:443"}}]}""")
        IranDomainBypass.apply(config, false)
        val tag = rules(config)[0].asJsonObject["outboundTag"].asString
        assertEquals("mobiletina-ir-direct-direct", tag)
        assertEquals("freedom", config.getAsJsonArray("outbounds").last().asJsonObject["protocol"].asString)
        assertEquals("proxy", config.getAsJsonArray("outbounds")[1].asJsonObject.getAsJsonObject("proxySettings")["tag"].asString)
        assertEquals("example.com:443", config.getAsJsonArray("outbounds")[3].asJsonObject.getAsJsonObject("settings")["redirect"].asString)
    }

    @Test
    fun ipOnlyTrafficGetsRoutingOnlyDomainDetectionAndDnsInboundIsUntouched() {
        val config = json("""{"outbounds":[],"inbounds":[{"protocol":"socks","port":1080,"sniffing":{"enabled":false,"destOverride":["fakedns"],"domainsExcluded":["example.com"]}},{"protocol":"tun"},{"protocol":"dokodemo-door","port":53},{"protocol":"http"}]}""")
        IranDomainBypass.apply(config, false)
        for (index in listOf(0, 1, 3)) {
            val sniff = config.getAsJsonArray("inbounds")[index].asJsonObject.getAsJsonObject("sniffing")
            assertTrue(sniff["enabled"].asBoolean)
            assertTrue(sniff["routeOnly"].asBoolean)
            assertFalse(sniff["metadataOnly"].asBoolean)
            assertTrue(sniff.getAsJsonArray("destOverride").map { it.asString }.containsAll(listOf("http", "tls", "quic")))
        }
        val original = config.getAsJsonArray("inbounds")[0].asJsonObject.getAsJsonObject("sniffing")
        assertEquals("fakedns", original.getAsJsonArray("destOverride")[0].asString)
        assertEquals("example.com", original.getAsJsonArray("domainsExcluded")[0].asString)
        assertFalse(config.getAsJsonArray("inbounds")[2].asJsonObject.has("sniffing"))
    }

    @Test
    fun explicitDestinationRewriteChoiceAndUnknownCustomFieldsArePreserved() {
        val config = json("""{"outbounds":[],"inbounds":[{"protocol":"socks","sniffing":{"enabled":true,"routeOnly":false,"destOverride":["fakedns"]}}],"custom-extension":{"answer":42},"dns":{"servers":["1.1.1.1"]}}""")
        val dns = config["dns"].deepCopy()
        IranDomainBypass.apply(config, true)
        assertFalse(config.getAsJsonArray("inbounds")[0].asJsonObject.getAsJsonObject("sniffing")["routeOnly"].asBoolean)
        assertEquals(42, config.getAsJsonObject("custom-extension")["answer"].asInt)
        assertEquals(dns, config["dns"])
    }

    @Test
    fun disabledAndFailedConfigsAreReturnedWithoutParsingOrModification() {
        val invalid = ConfigResult(true, "one", "not json")
        assertSame(invalid, IranDomainBypass.applyToRuntimeConfig(invalid, false, false))
        val failed = ConfigResult(false, "two", "not json", "original failure")
        assertSame(failed, IranDomainBypass.applyToRuntimeConfig(failed, true, false))
    }

    @Test
    fun runtimeTransformationKeepsProfileIdentityAndDoesNotModifyTheInput() {
        val original = ConfigResult(true, "profile-id", """{"outbounds":[{"tag":"proxy","protocol":"wireguard"}],"inbounds":null}""")
        val content = original.content
        val result = IranDomainBypass.applyToRuntimeConfig(original, true, false)
        assertTrue(result.status)
        assertEquals("profile-id", result.guid)
        assertEquals(content, original.content)
        assertEquals("domain:ir", rules(json(result.content))[0].asJsonObject.getAsJsonArray("domain")[0].asString)
    }
}
