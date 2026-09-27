package com.marbleng.app.core

import java.net.InetAddress

/**
 * RFC 8484 DNS wire-message codec, shared by the DoH resolver pool, the censorship-aware resolver
 * and the resolver-pool unit tests.
 *
 * MARBLE_SMART_RANK_V90: extracting this out of [CensorshipAwareDnsResolver] lets the new
 * [DohResolverPool] ship real DNS queries (never a bare GET /dns-query, which resolvers can answer
 * with HTTP 400 while still being unable to resolve anything) and lets tests assert on the actual
 * wire answers returned by a fake transport.
 *
 * Pure and dependency-free (JVM + Android), so it can be unit tested on the JVM.
 */
object DnsWireCodec {

    /** One question per RFC 8484 message; call separately for A (1) and AAAA (28). */
    fun buildQuery(hostname: String, type: Int = 1): ByteArray {
        require(type == 1 || type == 28) { "Only A and AAAA queries are supported" }
        val labels = java.net.IDN.toASCII(hostname.trimEnd('.')).split('.')
            .filter { it.isNotBlank() }
        require(labels.isNotEmpty() && labels.all { it.length in 1..63 } && hostname.length <= 253) {
            "Invalid DNS hostname"
        }
        return java.io.ByteArrayOutputStream().use { out ->
            out.write(byteArrayOf(0x00, 0x01)) // transaction id
            out.write(byteArrayOf(0x01, 0x00)) // flags: RD
            out.write(byteArrayOf(0x00, 0x01)) // QDCOUNT
            out.write(byteArrayOf(0x00, 0x00)) // ANCOUNT
            out.write(byteArrayOf(0x00, 0x00)) // NSCOUNT
            out.write(byteArrayOf(0x00, 0x00)) // ARCOUNT
            labels.forEach { label ->
                val bytes = label.toByteArray(Charsets.US_ASCII)
                out.write(bytes.size)
                out.write(bytes)
            }
            out.write(0)
            out.write(byteArrayOf(0x00, type.toByte())) // QTYPE A or AAAA
            out.write(byteArrayOf(0x00, 0x01)) // QCLASS IN
            out.toByteArray()
        }
    }

    /**
     * Parse A (type 1) and AAAA (type 28) answers from a DNS wire message.
     * Returns an empty list when no usable answer was returned (caller treats it as a failure).
     */
    fun parseAnswers(message: ByteArray): List<InetAddress> {
        if (message.size < 12) return emptyList()
        val rcode = message[3].toInt() and 0x0F
        if (rcode != 0) return emptyList() // NXDOMAIN/SERVFAIL -> empty (treated as failure)

        val answers = ((message[6].toInt() and 0xFF) shl 8) or (message[7].toInt() and 0xFF)
        if (answers == 0) return emptyList()

        val out = mutableListOf<InetAddress>()
        var offset = 12
        // Skip question section (one question).
        var qdCount = ((message[4].toInt() and 0xFF) shl 8) or (message[5].toInt() and 0xFF)
        while (qdCount > 0 && offset < message.size) {
            offset = skipName(message, offset)
            if (offset + 4 > message.size) return out
            offset += 4
            qdCount--
        }
        var remaining = answers
        while (remaining > 0 && offset + 11 < message.size) {
            offset = skipName(message, offset)
            if (offset + 10 > message.size) break
            val type = ((message[offset].toInt() and 0xFF) shl 8) or (message[offset + 1].toInt() and 0xFF)
            val dataLen = ((message[offset + 8].toInt() and 0xFF) shl 8) or (message[offset + 9].toInt() and 0xFF)
            offset += 10
            if (offset + dataLen > message.size) break
            val data = message.copyOfRange(offset, offset + dataLen)
            when {
                type == 1 && dataLen == 4 -> runCatching { out += InetAddress.getByAddress(data) }
                type == 28 && dataLen == 16 -> runCatching { out += InetAddress.getByAddress(data) }
            }
            offset += dataLen
            remaining--
        }
        return out
    }

    /** Advance past a possibly-compressed DNS name at [start], returning the next byte offset. */
    private fun skipName(message: ByteArray, start: Int): Int {
        var offset = start
        var jumped = false
        var firstJump = start
        var guard = 0
        while (offset < message.size && guard < 64) {
            guard++
            val len = message[offset].toInt() and 0xFF
            if (len == 0) return if (jumped) firstJump + 2 else offset + 1
            if ((len and 0xC0) == 0xC0) {
                val pointer = ((len and 0x3F) shl 8) or (message[offset + 1].toInt() and 0xFF)
                if (!jumped) {
                    firstJump = offset
                    jumped = true
                }
                offset = pointer
            } else {
                offset += 1 + len
            }
        }
        return if (jumped) firstJump + 2 else offset
    }
}
