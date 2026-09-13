package com.v2ray.ang.core

import org.junit.Assert.assertEquals
import org.junit.Test

class WireguardAddressPolicyTest {

    @Test
    fun ipv4OnlyTunnelForcesIpv4Destinations() {
        val addresses = WireguardAddressPolicy.activeAddresses(
            "10.187.192.50/30",
            ipv6Enabled = true,
        )

        assertEquals(listOf("10.187.192.50/30"), addresses)
        assertEquals("ForceIPv4", WireguardAddressPolicy.domainStrategy(addresses))
    }

    @Test
    fun dualStackTunnelKeepsBothFamiliesWhenEnabled() {
        val addresses = WireguardAddressPolicy.activeAddresses(
            "172.16.0.2/32\n2606:4700:110:8e34:c6d7:9cc9:ce5:b41/128",
            ipv6Enabled = true,
        )

        assertEquals(2, addresses.size)
        assertEquals("ForceIP", WireguardAddressPolicy.domainStrategy(addresses))
    }

    @Test
    fun disabledIpv6RemovesIpv6AddressAndForcesIpv4() {
        val addresses = WireguardAddressPolicy.activeAddresses(
            "172.16.0.2/32, 2606:4700:110::1/128",
            ipv6Enabled = false,
        )

        assertEquals(listOf("172.16.0.2/32"), addresses)
        assertEquals("ForceIPv4", WireguardAddressPolicy.domainStrategy(addresses))
    }
}
