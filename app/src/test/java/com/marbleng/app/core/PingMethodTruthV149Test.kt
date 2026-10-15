package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PingMethodTruthV149Test {
    @Test
    fun `a handshake record from the server counts as a verified answer`() {
        val serverHello = byteArrayOf(0x16, 0x03, 0x03, 0x00, 0x5a)
        assertTrue(MultiVectorReachability.isTlsRecord(serverHello))
    }

    @Test
    fun `an alert record still proves the endpoint is alive`() {
        val alert = byteArrayOf(0x15, 0x03, 0x03, 0x00, 0x02)
        assertTrue(MultiVectorReachability.isTlsRecord(alert))
    }

    @Test
    fun `non-TLS bytes are not mistaken for a TLS record`() {
        val http = "HTTP/".toByteArray(Charsets.US_ASCII)
        assertFalse(MultiVectorReachability.isTlsRecord(http))

        assertFalse(MultiVectorReachability.isTlsRecord(byteArrayOf(0x16, 0x09, 0x09, 0x00, 0x05)))

        assertFalse(MultiVectorReachability.isTlsRecord(byteArrayOf(0x16, 0x03)))

        assertFalse(MultiVectorReachability.isTlsRecord(byteArrayOf(0x16, 0x03, 0x03, 0x00, 0x00)))
    }

    @Test
    fun `the generated ClientHello is a structurally valid TLS record`() {
        val hello = MultiVectorReachability.clientHello("vps1.maje.eu.org")

        assertEquals(0x16, hello[0].toInt() and 0xFF)
        assertEquals(0x03, hello[1].toInt() and 0xFF)
        assertEquals(0x01, hello[2].toInt() and 0xFF)
        val recordLength = ((hello[3].toInt() and 0xFF) shl 8) or (hello[4].toInt() and 0xFF)
        assertEquals(hello.size - MultiVectorReachability.TLS_RECORD_HEADER_BYTES, recordLength)

        val handshake = hello.copyOfRange(MultiVectorReachability.TLS_RECORD_HEADER_BYTES, hello.size)
        assertEquals(0x01, handshake[0].toInt() and 0xFF)
        val handshakeLength = ((handshake[1].toInt() and 0xFF) shl 16) or
            ((handshake[2].toInt() and 0xFF) shl 8) or
            (handshake[3].toInt() and 0xFF)
        assertEquals(handshake.size - 4, handshakeLength)

        var index = 4
        index += 2
        index += 32
        index += 1 + (handshake[index].toInt() and 0xFF)
        val cipherLength = ((handshake[index].toInt() and 0xFF) shl 8) or
            (handshake[index + 1].toInt() and 0xFF)
        index += 2 + cipherLength
        index += 1 + (handshake[index].toInt() and 0xFF)
        val extensionsLength = ((handshake[index].toInt() and 0xFF) shl 8) or
            (handshake[index + 1].toInt() and 0xFF)
        index += 2
        assertEquals(
            "extensions block length must match the remaining bytes exactly",
            handshake.size - index,
            extensionsLength
        )

        val end = index + extensionsLength
        val seen = mutableSetOf<Int>()
        while (index < end) {
            val type = ((handshake[index].toInt() and 0xFF) shl 8) or (handshake[index + 1].toInt() and 0xFF)
            val length = ((handshake[index + 2].toInt() and 0xFF) shl 8) or (handshake[index + 3].toInt() and 0xFF)
            seen += type
            index += 4 + length
        }
        assertEquals("extension walk must land exactly on the end of the record", end, index)
        assertTrue("server_name must be offered", 0x0000 in seen)
        assertTrue("supported_versions must be offered", 0x002B in seen)
        assertTrue("key_share is what elicits a real ServerHello", 0x0033 in seen)
    }

    @Test
    fun `an IP literal endpoint omits SNI instead of sending an invalid name`() {
        val hello = MultiVectorReachability.clientHello("203.0.113.10")

        val body = String(hello, Charsets.ISO_8859_1)
        assertFalse(body.contains("203.0.113.10"))
    }

    @Test
    fun `a hello for an IPv6 literal is still well formed`() {
        val hello = MultiVectorReachability.clientHello("2001:db8::1")
        val recordLength = ((hello[3].toInt() and 0xFF) shl 8) or (hello[4].toInt() and 0xFF)
        assertEquals(hello.size - MultiVectorReachability.TLS_RECORD_HEADER_BYTES, recordLength)
    }
}
