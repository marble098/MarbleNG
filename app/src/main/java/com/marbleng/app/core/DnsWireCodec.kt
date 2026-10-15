package com.marbleng.app.core

import java.net.InetAddress

object DnsWireCodec {
    fun buildQuery(hostname: String, type: Int = 1): ByteArray {
        require(type == 1 || type == 28) { "Only A and AAAA queries are supported" }
        val labels = java.net.IDN.toASCII(hostname.trimEnd('.')).split('.')
            .filter { it.isNotBlank() }
        require(labels.isNotEmpty() && labels.all { it.length in 1..63 } && hostname.length <= 253) {
            "Invalid DNS hostname"
        }
        return java.io.ByteArrayOutputStream().use { out ->
            out.write(byteArrayOf(0x00, 0x01))
            out.write(byteArrayOf(0x01, 0x00))
            out.write(byteArrayOf(0x00, 0x01))
            out.write(byteArrayOf(0x00, 0x00))
            out.write(byteArrayOf(0x00, 0x00))
            out.write(byteArrayOf(0x00, 0x00))
            labels.forEach { label ->
                val bytes = label.toByteArray(Charsets.US_ASCII)
                out.write(bytes.size)
                out.write(bytes)
            }
            out.write(0)
            out.write(byteArrayOf(0x00, type.toByte()))
            out.write(byteArrayOf(0x00, 0x01))
            out.toByteArray()
        }
    }

    fun extractRcode(message: ByteArray): Int {
        if (message.size < 4) return -1
        return message[3].toInt() and 0x0F
    }

    data class DetailedResult(
        val rcode: Int,
        val answerCount: Int,
        val addresses: List<InetAddress>
    )

    fun parseDetailed(message: ByteArray): DetailedResult {
        if (message.size < 12) return DetailedResult(rcode = -1, answerCount = 0, addresses = emptyList())
        val rcode = message[3].toInt() and 0x0F
        val answers = ((message[6].toInt() and 0xFF) shl 8) or (message[7].toInt() and 0xFF)
        if (rcode != 0 || answers == 0) return DetailedResult(rcode = rcode, answerCount = answers, addresses = emptyList())
        return DetailedResult(rcode = rcode, answerCount = answers, addresses = parseAnswersInternal(message))
    }

    private fun parseAnswersInternal(message: ByteArray): List<InetAddress> {
        val answers = ((message[6].toInt() and 0xFF) shl 8) or (message[7].toInt() and 0xFF)
        val out = mutableListOf<InetAddress>()
        var offset = 12
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

    fun parseAnswers(message: ByteArray): List<InetAddress> {
        if (message.size < 12) return emptyList()
        val rcode = message[3].toInt() and 0x0F
        if (rcode != 0) return emptyList()

        val answers = ((message[6].toInt() and 0xFF) shl 8) or (message[7].toInt() and 0xFF)
        if (answers == 0) return emptyList()

        val out = mutableListOf<InetAddress>()
        var offset = 12

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
