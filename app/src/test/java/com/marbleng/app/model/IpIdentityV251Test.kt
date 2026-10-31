package com.marbleng.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_IP_IDENTITY_V251 — the IP page's arithmetic, proven without a device.
 *
 * Every case here is a literal that a real network hands out. The point of pinning them is that the
 * page's claims — "this is private", "this is IPv6 only", "the tunnel and the network show the same
 * address" — are decisions, not formatting, and a wrong decision on this page is the one a user
 * cannot check for themselves.
 */
class IpIdentityV251Test {

    @Test
    fun ipv4LiteralsAreParsedByArithmeticAndNonsenseIsRefused() {
        assertEquals(listOf(192, 168, 1, 12), IpAddressBook.parseIpv4("192.168.1.12"))
        assertEquals(listOf(0, 0, 0, 0), IpAddressBook.parseIpv4("0.0.0.0"))
        listOf("192.168.1", "192.168.1.256", "192.168.1.1.1", "a.b.c.d", "", "fe80::1")
            .forEach { raw ->
                assertEquals("'$raw' must not parse as IPv4", null, IpAddressBook.parseIpv4(raw))
            }
    }

    @Test
    fun ipv6LiteralsExpandToEightGroupsIncludingTheCompressedAndMappedForms() {
        assertEquals(8, IpAddressBook.parseIpv6("2001:db8::1")?.size)
        assertEquals(8, IpAddressBook.parseIpv6("::1")?.size)
        assertEquals(8, IpAddressBook.parseIpv6("::")?.size)
        assertEquals(8, IpAddressBook.parseIpv6("fe80::1%wlan0")?.size)
        assertEquals(8, IpAddressBook.parseIpv6("[2606:4700:4700::1111]")?.size)
        // The dotted-quad tail of a mapped address is two groups, not four.
        val mapped = IpAddressBook.parseIpv6("::ffff:192.168.1.1")
        assertEquals(8, mapped?.size)
        assertEquals(0xFFFF, mapped?.get(5))
        assertEquals(0xC0A8, mapped?.get(6))
        assertEquals(0x0101, mapped?.get(7))
        listOf("2001:db8:::1", "gggg::1", "1:2:3:4:5:6:7:8:9", "1::2::3").forEach { raw ->
            assertEquals("'$raw' must not parse as IPv6", null, IpAddressBook.parseIpv6(raw))
        }
    }

    @Test
    fun everyNonPublicIpv4RangeIsNamedInsteadOfBeingCalledAUserAddress() {
        assertEquals(IpScope.PRIVATE, IpAddressBook.scopeOf("10.1.2.3"))
        assertEquals(IpScope.PRIVATE, IpAddressBook.scopeOf("172.16.0.1"))
        assertEquals(IpScope.PRIVATE, IpAddressBook.scopeOf("172.31.255.254"))
        assertEquals(IpScope.PUBLIC, IpAddressBook.scopeOf("172.32.0.1"))
        assertEquals(IpScope.PRIVATE, IpAddressBook.scopeOf("192.168.0.10"))
        assertEquals(IpScope.LOOPBACK, IpAddressBook.scopeOf("127.0.0.1"))
        assertEquals(IpScope.LINK_LOCAL, IpAddressBook.scopeOf("169.254.10.20"))
        assertEquals(IpScope.CGNAT, IpAddressBook.scopeOf("100.64.1.1"))
        assertEquals(IpScope.CGNAT, IpAddressBook.scopeOf("100.127.255.255"))
        assertEquals(IpScope.PUBLIC, IpAddressBook.scopeOf("100.128.0.1"))
        assertEquals(IpScope.DOCUMENTATION, IpAddressBook.scopeOf("198.51.100.7"))
        assertEquals(IpScope.MULTICAST, IpAddressBook.scopeOf("224.0.0.1"))
        assertEquals(IpScope.UNSPECIFIED, IpAddressBook.scopeOf("0.0.0.0"))
        assertEquals(IpScope.PUBLIC, IpAddressBook.scopeOf("1.1.1.1"))
    }

    @Test
    fun everyIpv6ScopeIsNamedIncludingNat64AndTheDocumentationRange() {
        assertEquals(IpScope.LOOPBACK, IpAddressBook.scopeOf("::1"))
        assertEquals(IpScope.UNSPECIFIED, IpAddressBook.scopeOf("::"))
        assertEquals(IpScope.LINK_LOCAL, IpAddressBook.scopeOf("fe80::1"))
        assertEquals(IpScope.UNIQUE_LOCAL, IpAddressBook.scopeOf("fd00::1"))
        assertEquals(IpScope.MULTICAST, IpAddressBook.scopeOf("ff02::1"))
        assertEquals(IpScope.NAT64, IpAddressBook.scopeOf("64:ff9b::1.2.3.4"))
        assertEquals(IpScope.DOCUMENTATION, IpAddressBook.scopeOf("2001:db8::5"))
        assertEquals(IpScope.PUBLIC, IpAddressBook.scopeOf("2606:4700:4700::1111"))
        // A zone id must not change the verdict, and neither must the brackets.
        assertEquals(IpScope.LINK_LOCAL, IpAddressBook.scopeOf("[fe80::1%wlan0]"))
        assertEquals(IpScope.OTHER, IpAddressBook.scopeOf("not-an-address"))
    }

    @Test
    fun theTunnelIsRecognisedByItsInterfaceNameNotByItsAddress() {
        listOf("tun0", "tun1", "utun3", "ppp0", "tap0", "wg0", "ipsec0", "clat4").forEach { name ->
            assertTrue("$name must be a tunnel", IpAddressBook.isTunnelInterface(name))
        }
        listOf("wlan0", "rmnet_data0", "eth0", "", "  ").forEach { name ->
            assertFalse("$name must not be a tunnel", IpAddressBook.isTunnelInterface(name))
        }
    }

    @Test
    fun theNetworkLineSaysWhichFamiliesTheDeviceActuallyHolds() {
        val dual = IpStackReport(
            addresses = listOf(
                entry("192.168.1.12"),
                entry("fe80::1", prefix = 64),
                entry("2606:4700::1", prefix = 64)
            ),
            transport = "wifi",
            mtu = 1500
        )
        assertTrue(dual.hasIpv4)
        assertTrue(dual.hasIpv6)
        assertTrue(dual.networkLine.contains("WIFI"))
        assertTrue(dual.networkLine.contains("dual stack"))
        assertTrue(dual.networkLine.contains("MTU 1500"))

        val v4Only = IpStackReport(addresses = listOf(entry("10.0.0.5")), transport = "mobile")
        assertTrue(v4Only.hasIpv4)
        assertFalse(v4Only.hasIpv6)
        assertTrue(v4Only.networkLine.contains("IPv4 only"))

        val bare = IpStackReport()
        assertTrue(bare.networkLine.contains("no address yet"))
    }

    @Test
    fun theTunnelAddressesAreKeptApartFromTheOnesTheDeviceOwns() {
        val report = IpStackReport(
            addresses = listOf(
                entry("192.168.1.12", iface = "wlan0", tunnel = false),
                entry("10.8.0.2", iface = "tun0", tunnel = true),
                entry("2606:4700::1", prefix = 64, iface = "wlan0", tunnel = false)
            )
        )
        assertEquals(2, report.underlayAddresses.size)
        assertEquals(1, report.tunnelAddresses.size)
        assertEquals("10.8.0.2", report.tunnelAddresses.single().address)
        assertEquals("192.168.1.12", report.primaryDeviceAddress?.address)
        assertTrue(report.addressLine.startsWith("2 IPv4"))
        assertTrue(report.addressLine.contains("in the tunnel"))
    }

    @Test
    fun aLocalAddressOnTheTunnelIsNotReportedAsThePublicExit() {
        val local = IpEgress(
            family = IpKind.IPV4,
            address = "10.8.0.2",
            viaTunnel = true,
            global = false
        )
        val public = IpEgress(
            family = IpKind.IPV4,
            address = "93.184.216.34",
            viaTunnel = true,
            global = true
        )
        val report = IpStackReport(egress = listOf(local, public), tunnelActive = true)
        assertEquals("93.184.216.34", report.publicV4?.address)
        assertTrue(report.exitIsGlobal)
        assertEquals("93.184.216.34", report.exit?.address)

        val onlyLocal = IpStackReport(egress = listOf(local), tunnelActive = true)
        assertEquals(null, onlyLocal.publicV4)
        assertFalse(onlyLocal.exitIsGlobal)
    }

    @Test
    fun theLeakSentenceComparesTheTwoObservationsAndSaysWhenItCannot() {
        val throughTunnel = IpEgress(IpKind.IPV4, "8.8.4.4", viaTunnel = true, global = true)
        val direct = IpEgress(IpKind.IPV4, "93.184.216.34", viaTunnel = false, global = true)
        val hidden = IpStackReport(egress = listOf(throughTunnel, direct), tunnelActive = true)
        assertTrue(IpAddressBook.leakLine(hidden).contains("the real address is hidden"))

        val same = IpStackReport(
            egress = listOf(throughTunnel.copy(address = "93.184.216.34"), direct),
            tunnelActive = true
        )
        assertTrue(IpAddressBook.leakLine(same).contains("not being replaced"))

        val offline = IpStackReport(tunnelActive = false)
        assertTrue(IpAddressBook.leakLine(offline).contains("No public address"))
    }

    @Test
    fun theFamilyVerdictSeparatesAMeasuredAnswerFromAnAdvertisedOne() {
        val measured = IpStackReport(
            reachability = listOf(
                IpReachability("Cloudflare", "1.1.1.1", IpKind.IPV4, ok = true, latencyMs = 24),
                IpReachability("Google", "2001:4860:4860::8888", IpKind.IPV6, ok = false, detail = "no answer within 2500 ms")
            )
        )
        val line = IpAddressBook.familyVerdict(measured)
        assertTrue(line.contains("IPv4 reached in 24 ms"))
        assertTrue(line.contains("IPv6 no answer within 2500 ms"))

        val unmeasured = IpStackReport()
        assertFalse(IpAddressBook.familyVerdict(unmeasured).contains("reached"))
    }

    @Test
    fun aV4CidrIsMaskedAndAnUnknownPrefixIsLeftAlone() {
        assertEquals("192.168.1.0/24", IpAddressBook.networkOf(entry("192.168.1.12", prefix = 24)))
        assertEquals("10.0.0.0/8", IpAddressBook.networkOf(entry("10.14.1.9", prefix = 8)))
        assertEquals("172.16.0.0/12", IpAddressBook.networkOf(entry("172.20.3.4", prefix = 12)))
        assertEquals("192.168.1.12", IpAddressBook.networkOf(entry("192.168.1.12", prefix = -1)))
        assertEquals(
            "2606:4700::1/64",
            IpAddressBook.networkOf(entry("2606:4700::1", prefix = 64))
        )
    }

    @Test
    fun theLedgerPutsTheDevicesOwnAddressesFirstAndTheTunnelsLast() {
        val ordered = IpAddressBook.ordered(
            listOf(
                entry("fd00::1", prefix = 64, iface = "tun0", tunnel = true),
                entry("10.0.0.2", iface = "tun0", tunnel = true),
                entry("2606:4700::1", prefix = 64, iface = "wlan0"),
                entry("192.168.1.12", iface = "wlan0")
            )
        )
        assertEquals("192.168.1.12", ordered.first().address)
        assertTrue(ordered.take(2).none { it.tunnel })
        assertTrue(ordered.takeLast(2).all { it.tunnel })
    }

    @Test
    fun theReportKeyChangesWithTheNetworkSoThePageKnowsWhenToSweepAgain() {
        val wifi = IpStackReport(transport = "wifi", mtu = 1500)
        val mobile = IpStackReport(transport = "mobile", mtu = 1400)
        assertFalse(wifi.networkKey() == mobile.networkKey())
        assertTrue(wifi.networkKey().contains("no4"))
        assertTrue(IpStackReport(transport = "wifi", mtu = 1500).networkKey() == wifi.networkKey())
    }

    private fun entry(
        address: String,
        prefix: Int = 24,
        iface: String = "wlan0",
        tunnel: Boolean = false
    ) = IpAddressEntry(
        address = address,
        prefixLength = prefix,
        kind = IpAddressBook.kindOf(address),
        scope = IpAddressBook.scopeOf(address),
        interfaceName = iface,
        tunnel = tunnel
    )
}
