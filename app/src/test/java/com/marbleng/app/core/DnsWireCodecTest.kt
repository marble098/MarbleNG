package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsWireCodecTest {
    @Test
    fun buildQueryProducesValidHeaderAndQuestion() {
        val query = DnsWireCodec.buildQuery("example.com")
        assertTrue(query.size >= 17)
        assertEquals(0x01, query[2].toInt())
        assertEquals(0x00, query[3].toInt())
        assertEquals(0x00, query[4].toInt())
        assertEquals(0x01, query[5].toInt())
    }

    @Test
    fun buildQueryCanRequestAaaaWithoutAskingForAnARecord() {
        val question = DnsWireCodec.buildQuery("edge.example.com", type = 28)
        assertEquals(0, question[question.size - 4].toInt())
        assertEquals(28, question[question.size - 3].toInt())
        assertEquals(0, question[question.size - 2].toInt())
        assertEquals(1, question[question.size - 1].toInt())
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
        val response = aRecordResponse(rcode = 2)
        assertTrue(DnsWireCodec.parseAnswers(response).isEmpty())
    }

    @Test
    fun tooShortMessageReturnsNoAddresses() {
        assertTrue(DnsWireCodec.parseAnswers(byteArrayOf(0x00, 0x01, 0x02)).isEmpty())
    }

    private fun aRecordResponse(rcode: Int): ByteArray =
        java.io.ByteArrayOutputStream().use { out ->
            out.write(byteArrayOf(0x00, 0x01))
            out.write(byteArrayOf(0x81.toByte(), (0x80 or rcode).toByte()))
            out.write(byteArrayOf(0x00, 0x01))
            out.write(byteArrayOf(0x00, 0x01))
            out.write(byteArrayOf(0x00, 0x00))
            out.write(byteArrayOf(0x00, 0x00))
            out.write(7)
            out.write("example".toByteArray(Charsets.US_ASCII))
            out.write(3)
            out.write("com".toByteArray(Charsets.US_ASCII))
            out.write(0)
            out.write(byteArrayOf(0x00, 0x01))
            out.write(byteArrayOf(0x00, 0x01))
            out.write(byteArrayOf(0xC0.toByte(), 0x0C))
            out.write(byteArrayOf(0x00, 0x01))
            out.write(byteArrayOf(0x00, 0x01))
            out.write(byteArrayOf(0x00, 0x00, 0x00, 0x3C))
            out.write(byteArrayOf(0x00, 0x04))
            out.write(byteArrayOf(0x01, 0x02, 0x03, 0x04))
            out.toByteArray()
        }
}
