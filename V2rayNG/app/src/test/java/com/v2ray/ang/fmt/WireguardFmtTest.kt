package com.v2ray.ang.fmt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WireguardFmtTest {

    private val amneziaConfig = """
        [Interface]
        PrivateKey = AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=
        Address = 10.8.0.2/32
        MTU = 1380
        Jc = 5
        Jmin = 30
        Jmax = 60
        S1 = 64
        S2 = 80
        S3 = 96
        S4 = 112
        H1 = 100-110
        H2 = 200
        H3 = 300
        H4 = 400
        I1 = <b 0x0102><r 8>
        ContentPaddingAddition = 4-12
        RekeyAfterTime = 90-120
        RandomizePacketTrailers = true
        DisableCookieReplies = true

        [Peer]
        PublicKey = BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=
        PresharedKey = CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC=
        Endpoint = [2001:db8::1]:51820
        AllowedIPs = 0.0.0.0/0, ::/0
        PersistentKeepalive = 25
    """.trimIndent()

    @Test
    fun parsesAmneziaIniWithoutChangingProfileType() {
        val profile = WireguardFmt.parseWireguardConfFile(amneziaConfig)

        assertTrue(profile.isAmneziaWG)
        assertEquals("2001:db8::1", profile.server)
        assertEquals("51820", profile.serverPort)
        assertEquals(5, profile.awgJc)
        assertEquals("100-110", profile.awgH1)
        assertEquals("<b 0x0102><r 8>", profile.awgI1)
        assertEquals("4-12", profile.awgContentPaddingAddition)
        assertEquals(25, profile.keepAlive)
        assertEquals("0.0.0.0/0, ::/0", profile.allowedIPs)
    }

    @Test
    fun amneziaUriRoundTripsAllImportantOptions() {
        val source = WireguardFmt.parseWireguardConfFile(
            amneziaConfig,
            profileName = "AWG test",
        )

        val uri = WireguardFmt.exportUri(source)
        val parsed = requireNotNull(WireguardFmt.parseAmneziaWG(uri))

        assertTrue(uri.startsWith("amneziawg://"))
        assertTrue(parsed.isAmneziaWG)
        assertEquals("AWG test", parsed.remarks)
        assertEquals(source.server, parsed.server)
        assertEquals(source.awgJmin, parsed.awgJmin)
        assertEquals(source.awgS4, parsed.awgS4)
        assertEquals(source.awgH4, parsed.awgH4)
        assertEquals(source.awgI1, parsed.awgI1)
        assertEquals(source.awgRekeyAfterTime, parsed.awgRekeyAfterTime)
        assertEquals(source.awgRandomizePacketTrailers, parsed.awgRandomizePacketTrailers)
        assertEquals(source.awgDisableCookieReplies, parsed.awgDisableCookieReplies)
    }

    @Test
    fun standardWireGuardIniRemainsStandard() {
        val standard = """
            [Interface]
            PrivateKey = AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=
            Address = 10.8.0.2/32

            [Peer]
            PublicKey = BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=
            Endpoint = example.com:51820
            AllowedIPs = 0.0.0.0/0
        """.trimIndent()

        val profile = WireguardFmt.parseWireguardConfFile(standard)

        assertTrue(!profile.isAmneziaWG)
        assertTrue(WireguardFmt.exportUri(profile).startsWith("wireguard://"))
    }

    @Test
    fun preservesRepeatedIpv4AndIpv6Addresses() {
        val dualStack = """
            [Interface]
            PrivateKey = AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=
            Address = 172.16.0.2/32
            Address = 2606:4700:110:8e34:c6d7:9cc9:ce5:b41/128
            Jc = 4
            Jmin = 40
            Jmax = 70

            [Peer]
            PublicKey = BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=
            Endpoint = 162.159.192.1:2408
            AllowedIPs = 0.0.0.0/0, ::/0
        """.trimIndent()

        val profile = WireguardFmt.parseWireguardConfFile(dualStack)

        assertEquals(
            "172.16.0.2/32\n2606:4700:110:8e34:c6d7:9cc9:ce5:b41/128",
            profile.localAddress,
        )
        val exported = WireguardFmt.toConf(profile)
        assertTrue(exported.contains("Address = 172.16.0.2/32\n"))
        assertTrue(
            exported.contains(
                "Address = 2606:4700:110:8e34:c6d7:9cc9:ce5:b41/128\n"
            )
        )
        assertEquals(
            profile.localAddress,
            WireguardFmt.parseWireguardConfFile(exported).localAddress,
        )
    }
}
