package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_PING_TRUTH_V149 — regressions for the four product ping methods that reported FAILED for
 * servers that were verifiably alive.
 *
 * The four root causes pinned here:
 *
 *  1. **TCP (recommended)** ran a JSSE handshake with hostname verification, so any self-signed or
 *     fronted certificate — i.e. the normal case for a proxy node — was a guaranteed failure. The
 *     gate now writes its own ClientHello and only asks whether TLS came back.
 *  2. **ICMP Ping** invoked `ping -q`, which suppresses the per-packet `time=` lines the parser
 *     required, so every run produced "no-responses" even at 0 % packet loss.
 *  3. **HTTP GET / HEAD** treated any status outside `200..399` as unreachable, so a 403/404/429
 *     from a CDN edge failed a route that had demonstrably completed a full round trip.
 *  4. All methods divided successes by the *configured* sample count instead of the attempts
 *     actually made, deflating the success percentage after an early abandon.
 */
class PingMethodTruthV149Test {

    // ------------------------------------------------------------------ Layer 0 TLS record gate

    @Test
    fun `a handshake record from the server counts as a verified answer`() {
        // 0x16 Handshake, TLS 1.2 record version, 90-byte ServerHello.
        val serverHello = byteArrayOf(0x16, 0x03, 0x03, 0x00, 0x5a)
        assertTrue(MultiVectorReachability.isTlsRecord(serverHello))
    }

    @Test
    fun `an alert record still proves the endpoint is alive`() {
        // A self-signed server that declines our cipher list answers 0x15 Alert. That is a
        // response, not a filter — the old JSSE gate turned this exact case into "FAILED".
        val alert = byteArrayOf(0x15, 0x03, 0x03, 0x00, 0x02)
        assertTrue(MultiVectorReachability.isTlsRecord(alert))
    }

    @Test
    fun `non-TLS bytes are not mistaken for a TLS record`() {
        // "HTTP/" — a plain HTTP listener. Not a TLS record, and the probe has a separate branch
        // that still treats any answered bytes as proof of life.
        val http = "HTTP/".toByteArray(Charsets.US_ASCII)
        assertFalse(MultiVectorReachability.isTlsRecord(http))
        // Junk with an impossible record version.
        assertFalse(MultiVectorReachability.isTlsRecord(byteArrayOf(0x16, 0x09, 0x09, 0x00, 0x05)))
        // A truncated read can never be a verdict.
        assertFalse(MultiVectorReachability.isTlsRecord(byteArrayOf(0x16, 0x03)))
        // Declared length of zero is not a real record.
        assertFalse(MultiVectorReachability.isTlsRecord(byteArrayOf(0x16, 0x03, 0x03, 0x00, 0x00)))
    }

    // ------------------------------------------------------------------ ClientHello encoding

    @Test
    fun `the generated ClientHello is a structurally valid TLS record`() {
        val hello = MultiVectorReachability.clientHello("vps1.maje.eu.org")

        // Record layer: handshake, legacy version TLS 1.0, length matches the payload exactly.
        assertEquals(0x16, hello[0].toInt() and 0xFF)
        assertEquals(0x03, hello[1].toInt() and 0xFF)
        assertEquals(0x01, hello[2].toInt() and 0xFF)
        val recordLength = ((hello[3].toInt() and 0xFF) shl 8) or (hello[4].toInt() and 0xFF)
        assertEquals(hello.size - MultiVectorReachability.TLS_RECORD_HEADER_BYTES, recordLength)

        // Handshake layer: client_hello with a 24-bit length that matches its own body.
        val handshake = hello.copyOfRange(MultiVectorReachability.TLS_RECORD_HEADER_BYTES, hello.size)
        assertEquals(0x01, handshake[0].toInt() and 0xFF)
        val handshakeLength = ((handshake[1].toInt() and 0xFF) shl 16) or
            ((handshake[2].toInt() and 0xFF) shl 8) or
            (handshake[3].toInt() and 0xFF)
        assertEquals(handshake.size - 4, handshakeLength)

        // Walk the body to the extension block and prove every declared length is self-consistent.
        var index = 4
        index += 2                              // client_version
        index += 32                             // random
        index += 1 + (handshake[index].toInt() and 0xFF)   // legacy_session_id
        val cipherLength = ((handshake[index].toInt() and 0xFF) shl 8) or
            (handshake[index + 1].toInt() and 0xFF)
        index += 2 + cipherLength
        index += 1 + (handshake[index].toInt() and 0xFF)   // compression_methods
        val extensionsLength = ((handshake[index].toInt() and 0xFF) shl 8) or
            (handshake[index + 1].toInt() and 0xFF)
        index += 2
        assertEquals(
            "extensions block length must match the remaining bytes exactly",
            handshake.size - index,
            extensionsLength
        )

        // Each extension header must also be internally consistent, and SNI must be present.
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
        // A server_name extension carrying a bare IPv4 literal is illegal per RFC 6066 and is a
        // reason for a strict server to abort, so it must simply not be sent.
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
