package com.v2ray.ang.core

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.v2ray.ang.dto.ConfigResult

/** Apply the Iran preset's exact suffix rule without replacing a user's other rules. */
internal object IranDomainBypass {
    const val DOMAIN_RULE = "domain:ir"
    private const val DIRECT_TAG = "mobiletina-ir-direct"

    fun applyToRuntimeConfig(result: ConfigResult, enabled: Boolean, allowDestinationOverride: Boolean): ConfigResult {
        if (!enabled || !result.status) return result
        val config = JsonParser.parseString(result.content).takeIf { it.isJsonObject }?.asJsonObject ?: return result
        apply(config, allowDestinationOverride)
        return result.copy(content = config.toString())
    }

    fun apply(config: JsonObject, allowDestinationOverride: Boolean) {
        val outbounds = config.array("outbounds")
        val direct = outbounds.firstOrNull { element ->
            if (!element.isJsonObject) return@firstOrNull false
            val outbound = element.asJsonObject
            outbound.string("protocol") == "freedom" && !outbound.string("tag").isNullOrBlank() &&
                outbound.child("settings")?.string("redirect").isNullOrBlank() &&
                outbound.get("proxySettings").let { it == null || it.isJsonNull } &&
                outbound.child("streamSettings")?.child("sockopt")?.string("dialerProxy").isNullOrBlank()
        }?.asJsonObject
        val directTag = direct?.string("tag") ?: run {
            val used = outbounds.filter { it.isJsonObject }.mapNotNull { it.asJsonObject.string("tag") }.toSet()
            var tag = DIRECT_TAG
            while (tag in used) tag += "-direct"
            outbounds.add(JsonObject().apply {
                addProperty("tag", tag)
                addProperty("protocol", "freedom")
                add("settings", JsonObject())
            })
            tag
        }
        val routing = config.obj("routing")
        val originalRules = routing.array("rules")
        val rule = JsonObject().apply {
            addProperty("type", "field")
            addProperty("outboundTag", directTag)
            add("domain", JsonArray().apply { add(DOMAIN_RULE) })
        }
        // Xray uses the first matching rule; existing catch-all rules stay after this rule.
        routing.add("rules", JsonArray().apply {
            add(rule)
            originalRules.forEach { if (it != rule) add(it) }
        })
        config.get("inbounds")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { element ->
            if (!element.isJsonObject) return@forEach
            val inbound = element.asJsonObject
            if (inbound.string("protocol") !in setOf("socks", "http", "tun", "dokodemo-door")) return@forEach
            if (inbound.string("port") == "53") return@forEach
            val sniffing = inbound.obj("sniffing")
            sniffing.addProperty("enabled", true)
            sniffing.addProperty("metadataOnly", false)
            // General destination rewriting stays off unless the user explicitly enabled it.
            if (!allowDestinationOverride) sniffing.addProperty("routeOnly", true)
            val protocols = sniffing.array("destOverride")
            for (protocol in listOf("http", "tls", "quic")) {
                if (protocols.none { it.isJsonPrimitive && it.asString == protocol }) protocols.add(protocol)
            }
        }
    }

    private fun JsonObject.string(name: String): String? = get(name)?.takeIf { it.isJsonPrimitive }?.asString
    private fun JsonObject.child(name: String): JsonObject? = get(name)?.takeIf { it.isJsonObject }?.asJsonObject
    private fun JsonObject.obj(name: String): JsonObject = child(name) ?: JsonObject().also { add(name, it) }
    private fun JsonObject.array(name: String): JsonArray = get(name)?.takeIf { it.isJsonArray }?.asJsonArray
        ?: JsonArray().also { add(name, it) }
}
