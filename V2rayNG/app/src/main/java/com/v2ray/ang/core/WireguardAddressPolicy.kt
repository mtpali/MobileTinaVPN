package com.v2ray.ang.core

import com.v2ray.ang.AppConfig

internal object WireguardAddressPolicy {

    fun activeAddresses(localAddress: String?, ipv6Enabled: Boolean): List<String> {
        val configured = localAddress
            ?.split(',', '\n')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.distinct()
            ?.ifEmpty { null }
            ?: listOf(AppConfig.WIREGUARD_LOCAL_ADDRESS_V4)

        if (ipv6Enabled) return configured

        return configured
            .filterNot(::isIpv6)
            .ifEmpty { listOf(AppConfig.WIREGUARD_LOCAL_ADDRESS_V4) }
    }

    fun domainStrategy(addresses: List<String>): String {
        val hasIpv4 = addresses.any { !isIpv6(it) }
        val hasIpv6 = addresses.any(::isIpv6)
        return when {
            hasIpv4 && !hasIpv6 -> "ForceIPv4"
            hasIpv6 && !hasIpv4 -> "ForceIPv6"
            else -> "ForceIP"
        }
    }

    private fun isIpv6(address: String): Boolean = address.substringBefore('/').contains(':')
}
