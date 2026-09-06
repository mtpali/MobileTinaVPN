package com.v2ray.ang.core

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.v2ray.ang.AppConfig

/** Keeps routing usable when the optional compact CN/private GeoIP database is unavailable. */
object GeoIpRuleResolver {
    fun resolve(ipRules: List<String>, compactDatabaseAvailable: Boolean): List<String> {
        return ipRules.map { resolveRule(it, compactDatabaseAvailable) }
    }

    /**
     * Normalizes GeoIP entries embedded in an imported full custom JSON config.
     *
     * Custom profiles bypass the normal RulesetItem builder, so an `ext:` rule copied from
     * another client would otherwise reach Xray unchanged even when its optional database is
     * not installed. Only the two lists provided by the compact database are rewritten; all
     * other custom rules and JSON fields remain untouched.
     *
     * @return true when at least one routing entry was changed.
     */
    fun normalizeCustomRouting(
        config: JsonObject,
        compactDatabaseAvailable: Boolean,
    ): Boolean {
        val rules = config.get("routing")
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?.get("rules")
            ?.takeIf { it.isJsonArray }
            ?.asJsonArray
            ?: return false

        var changed = false
        for (ruleElement in rules) {
            val ipRules = ruleElement
                .takeIf { it.isJsonObject }
                ?.asJsonObject
                ?.get("ip")
                ?.takeIf { it.isJsonArray }
                ?.asJsonArray
                ?: continue

            for (index in 0 until ipRules.size()) {
                val element = ipRules[index]
                if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) continue

                val source = element.asString
                val compactList = compactList(source)
                val resolved = if (!compactDatabaseAvailable && compactList != null) {
                    "geoip:$compactList"
                } else {
                    source
                }
                if (source != resolved) {
                    ipRules.set(index, JsonPrimitive(resolved))
                    changed = true
                }
            }
        }
        return changed
    }

    private fun resolveRule(rule: String, compactDatabaseAvailable: Boolean): String {
        val compactPrefix = "ext:${AppConfig.GEOIP_ONLY_CN_PRIVATE_DAT}:"
        val compactList = compactList(rule)

        if (compactList != null) {
            return if (compactDatabaseAvailable) {
                "$compactPrefix$compactList"
            } else {
                "geoip:$compactList"
            }
        }

        return when {
            rule.equals(AppConfig.GEOIP_CN, ignoreCase = true) ->
                if (compactDatabaseAvailable) "${compactPrefix}cn" else AppConfig.GEOIP_CN
            rule.equals(AppConfig.GEOIP_PRIVATE, ignoreCase = true) ->
                if (compactDatabaseAvailable) "${compactPrefix}private" else AppConfig.GEOIP_PRIVATE
            else -> rule
        }
    }

    private fun compactList(rule: String): String? {
        val compactPrefix = "ext:${AppConfig.GEOIP_ONLY_CN_PRIVATE_DAT}:"
        return rule
            .takeIf { it.startsWith(compactPrefix, ignoreCase = true) }
            ?.substring(compactPrefix.length)
            ?.lowercase()
            ?.takeIf { it == "cn" || it == "private" }
    }
}
