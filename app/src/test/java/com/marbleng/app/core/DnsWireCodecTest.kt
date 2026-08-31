package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the RFC 8484 DNS wire codec (MARBLE_SMART_RANK_V90).
 *
 * The DoH resolver pool must send real DNS queries (never a bare GET /dns-query) and parse the
 * actual A/AAAA answers, so a resolver that answers but cannot resolve is treated as a failure.
 */
class DnsWireCodecTest {

    @Test
    fun buildQueryProducesValidHeaderAndQuestion() {
        val query = DnsWireCodec.buildQuery("example.com")
        assertTrue(query.size >= 17)
        assertEquals(0x01, query[2].toInt()) // RD flag set in the high flags byte
        assertEquals(0x00, query[3].toInt())
        assertEquals(0x00, query[4].toInt())
        assertEquals(0x01, query[5].toInt()) // QDCOUNT = 1
    }

    @Test
    fun parseAnswersReturnsIPv4Address() {
        val response = aRecordResponse(rcode = 0)
        val addresses = DnsWireCodec.parseAnswers(response)
        assertEquals(1, addresses.size)
        assertEquals("1.2.3.4", addresses.single().hostAddress)
    }

    @Test
    fun servfailRcodeReturnsNoAddresses() {
        val response = aRecordResponse(rcode = 2) // SERVFAIL
        assertTrue(DnsWireCodec.parseAnswers(response).isEmpty())
    }

    @Test
    fun tooShortMessageReturnsNoAddresses() {
        assertTrue(DnsWireCodec.parseAnswers(byteArrayOf(0x00, 0x01, 0x02)).isEmpty())
    }

    private fun aRecordResponse(rcode: Int): ByteArray =
        java.io.ByteArrayOutputStream().use { out ->
            out.write(byteArrayOf(0x00, 0x01))                                 // transaction id
            out.write(byteArrayOf(0x81.toByte(), (0x80 or rcode).toByte()))    // QR|RD|RA + rcode
            out.write(byteArrayOf(0x00, 0x01))                                 // QDCOUNT = 1
            out.write(byteArrayOf(0x00, 0x01))                                 // ANCOUNT = 1
            out.write(byteArrayOf(0x00, 0x00))                                 // NSCOUNT
            out.write(byteArrayOf(0x00, 0x00))                                 // ARCOUNT
            out.write(7)
            out.write("example".toByteArray(Charsets.US_ASCII))
            out.write(3)
            out.write("com".toByteArray(Charsets.US_ASCII))
            out.write(0)
            out.write(byteArrayOf(0x00, 0x01))                                 // QTYPE A
            out.write(byteArrayOf(0x00, 0x01))                                 // QCLASS IN
            out.write(byteArrayOf(0xC0.toByte(), 0x0C))                        // name ptr -> offset 12
            out.write(byteArrayOf(0x00, 0x01))                                 // TYPE A
            out.write(byteArrayOf(0x00, 0x01))                                 // CLASS IN
            out.write(byteArrayOf(0x00, 0x00, 0x00, 0x3C))                     // TTL 60
            out.write(byteArrayOf(0x00, 0x04))                                 // RDLENGTH 4
            out.write(byteArrayOf(0x01, 0x02, 0x03, 0x04))                     // 1.2.3.4
            out.toByteArray()
        }
}
