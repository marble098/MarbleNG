package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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

    @Test fun concurrentDualFamilyScansCannotStarveTheirDohProviderWorkers() {
        val providerWorkers = Executors.newFixedThreadPool(4)
        val callers = Executors.newFixedThreadPool(6)
        val fakePool = DohResolverPool(object : DohTransport {
            override fun query(endpoint: String, wire: ByteArray, timeoutMs: Long): DohTransportResult {
                val address = if (qtype(wire) == 28) "2001:db8::88" else "192.0.2.88"
                return DohTransportResult(body = response(wire, address), success = true)
            }
        }, providerWorkers, overallDeadlineMs = 700L)
        EncryptedEndpointResolver.setPoolOverrideForTests(fakePool)
        try {
            val scans = (0 until 6).map { index ->
                callers.submit<List<InetAddress>> {
                    EncryptedEndpointResolver.resolveAll("edge-$index.example.org", 1_800L).toList()
                }
            }
            scans.forEachIndexed { index, future ->
                val addresses = future.get(3, TimeUnit.SECONDS)
                assertTrue("family scan $index returned no answers", addresses.isNotEmpty())
                assertTrue("AAAA disappeared under load: $addresses", addresses.any { it is Inet6Address })
                assertTrue("A disappeared under load: $addresses", addresses.any { it is Inet4Address })
            }
        } finally {
            EncryptedEndpointResolver.setPoolOverrideForTests(null)
            callers.shutdownNow()
            providerWorkers.shutdownNow()
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
