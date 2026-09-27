package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/** No network required: verifies that management/probe lookups never fall through to netd and
 * that an IPv6-only endpoint remains usable when an A question receives NOERROR/NODATA. */
class EncryptedEndpointResolverTest {
    private fun response(question: ByteArray, address: String): ByteArray {
        val data = InetAddress.getByName(address).address
        val type = if (data.size == 16) 28 else 1
        val header = question.clone().apply {
            this[2] = 0x81.toByte()
            this[3] = 0x80.toByte()
            this[6] = 0
            this[7] = 1
        }
        val answer = byteArrayOf(
            0xc0.toByte(), 0x0c, 0, type.toByte(), 0, 1,
            0, 0, 0, 60, 0, data.size.toByte()
        )
        return header + answer + data
    }

    private fun qtype(wire: ByteArray): Int =
        ((wire[wire.size - 4].toInt() and 0xff) shl 8) or (wire[wire.size - 3].toInt() and 0xff)

    @Test fun independentAAndAaaaQueriesReturnBothFamiliesWithNoSystemDns() {
        val seen = mutableListOf<Int>()
        val result = EncryptedEndpointResolver.resolveWith("edge.example.org") { wire ->
            seen += qtype(wire)
            when (qtype(wire)) {
                1 -> response(wire, "192.0.2.7")
                28 -> response(wire, "2001:db8::7")
                else -> error("unexpected DNS question")
            }
        }
        assertEquals(listOf(28, 1), seen)
        assertTrue(result[0] is Inet6Address)
        assertTrue(result[1] is Inet4Address)
    }

    @Test fun missingAOrUnreachableEncryptedDnsNeverTriggersSystemResolver() {
        val seen = mutableListOf<Int>()
        val onlyV6 = EncryptedEndpointResolver.resolveWith("ipv6.example.invalid") { wire ->
            seen += qtype(wire)
            if (qtype(wire) == 28) response(wire, "2001:db8::42")
            else wire.clone().apply { this[2] = 0x81.toByte(); this[3] = 0x80.toByte() }
        }
        assertEquals(listOf(28, 1), seen)
        assertEquals(1, onlyV6.size)
        assertTrue(onlyV6[0] is Inet6Address)
        assertTrue(EncryptedEndpointResolver.resolveWith("absent.example.invalid") { null }.isEmpty())
        assertEquals("2001:db8:0:0:0:0:0:42", onlyV6[0].hostAddress)
    }

    @Test fun directDohRejectsHostnameOrPlainHttpBeforeOpeningASocket() {
        val transport = HttpUrlConnectionDohTransport()
        val wire = DnsWireCodec.buildQuery("node.example.org")
        listOf("https://dns.example.org/dns-query", "http://1.1.1.1/dns-query")
            .forEach { url ->
                val result = transport.query(url, wire, 300)
                assertTrue(url, !result.success)
                assertEquals("unsafe-doh-bootstrap", result.detail)
            }
    }

    @Test fun numericLiteralBypassesEveryDnsProvider() {
        var called = false
        val result = EncryptedEndpointResolver.resolveWith("[2001:db8::1]") {
            called = true
            null
        }
        assertTrue(result.single() is Inet6Address)
        assertTrue(!called)
    }
}
