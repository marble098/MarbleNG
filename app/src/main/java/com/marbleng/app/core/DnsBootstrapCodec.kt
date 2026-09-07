package com.marbleng.app.core

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.InetAddress

/** Small defensive codec for the API 26-28 resolver bridge. API 29+ uses raw DNS messages. */
object DnsBootstrapCodec {
    data class Question(val host: String, val type: Int, val end: Int)

    fun question(query: ByteArray): Question? {
        if (query.size < 17 || query[2].toInt() and 0xF8 != 0 || u16(query, 4) != 1) return null
        var offset = 12
        val labels = mutableListOf<String>()
        while (offset < query.size) {
            val length = query[offset++].toInt() and 255
            if (length == 0) break
            // Queries from the core have a single uncompressed question. Refuse malformed or
            // compressed input rather than chasing pointers from other apps on loopback.
            if (length > 63 || offset + length >= query.size) return null
            val label = String(query, offset, length, Charsets.US_ASCII)
            if (label.any { it <= ' ' || it >= '\u007f' }) return null
            labels += label
            offset += length
        }
        if (labels.isEmpty() || offset + 4 > query.size || u16(query, offset + 2) != 1) return null
        val host = labels.joinToString(".")
        if (host.length > 253) return null
        return Question(host, u16(query, offset), offset + 4)
    }

    fun error(query: ByteArray, rcode: Int): ByteArray? = response(query, emptyList(), rcode)
    fun answer(query: ByteArray, addresses: List<InetAddress>): ByteArray? = response(query, addresses, 0)

    private fun response(query: ByteArray, addresses: List<InetAddress>, rcode: Int): ByteArray? {
        val question = question(query) ?: return null
        val usable = addresses.filter { it.address.size == if (question.type == 1) 4 else 16 }.take(32)
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.write(query, 0, 2)
            output.writeShort(0x8080 or ((query[2].toInt() and 1) shl 8) or (rcode and 15))
            output.writeShort(1)
            output.writeShort(usable.size)
            output.writeShort(0)
            output.writeShort(0)
            output.write(query, 12, question.end - 12)
            usable.forEach { address ->
                output.writeShort(0xc00c)
                output.writeShort(question.type)
                output.writeShort(1)
                output.writeInt(60)
                output.writeShort(address.address.size)
                output.write(address.address)
            }
        }
        return bytes.toByteArray()
    }

    private fun u16(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 255) shl 8) or (bytes[offset + 1].toInt() and 255)
}
