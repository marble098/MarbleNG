package com.marbleng.app.core

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import kotlin.math.min

/** End-to-end UDP capability probe through Xray's SOCKS5 UDP ASSOCIATE path using STUN. */
object SocksUdpProbe {
    private val random = SecureRandom()

    fun stun(
        socksPort: Int,
        host: String = "stun.l.google.com",
        targetPort: Int = 19302,
        timeoutMs: Int = 3000
    ): Int = runCatching {
        val started = System.nanoTime()
        Socket().use { control ->
            control.soTimeout = timeoutMs
            control.connect(InetSocketAddress("127.0.0.1", socksPort), timeoutMs)
            val out = BufferedOutputStream(control.getOutputStream())
            val input = BufferedInputStream(control.getInputStream())
            out.write(byteArrayOf(5, 1, 0))
            out.flush()
            require(input.read() == 5 && input.read() == 0) { "SOCKS auth failed" }

            // UDP ASSOCIATE, letting Xray choose the relay endpoint.
            out.write(byteArrayOf(5, 3, 0, 1, 0, 0, 0, 0, 0, 0))
            out.flush()
            val head = ByteArray(4)
            readFully(input, head)
            require(head[0].toInt() == 5 && head[1].toInt() == 0) { "UDP associate rejected" }
            val relayHost = when (head[3].toInt() and 0xff) {
                1 -> ByteArray(4).also { readFully(input, it) }.let(InetAddress::getByAddress).hostAddress
                3 -> {
                    val n = input.read()
                    require(n in 1..255)
                    ByteArray(n).also { readFully(input, it) }.toString(Charsets.UTF_8)
                }
                4 -> ByteArray(16).also { readFully(input, it) }.let(InetAddress::getByAddress).hostAddress
                else -> error("Invalid UDP relay address")
            }
            val p1 = input.read(); val p2 = input.read()
            require(p1 >= 0 && p2 >= 0)
            val relayPort = (p1 shl 8) or p2
            val relay = InetSocketAddress(
                if (relayHost == "0.0.0.0" || relayHost == "::") "127.0.0.1" else relayHost,
                relayPort
            )

            val tx = ByteArray(12).also(random::nextBytes)
            val stun = ByteArray(20)
            stun[0] = 0x00; stun[1] = 0x01 // Binding request
            stun[2] = 0; stun[3] = 0
            stun[4] = 0x21; stun[5] = 0x12; stun[6] = 0xA4.toByte(); stun[7] = 0x42
            System.arraycopy(tx, 0, stun, 8, tx.size)

            val hostBytes = host.toByteArray(Charsets.UTF_8)
            require(hostBytes.size in 1..255)
            val payload = ByteArray(3 + 1 + 1 + hostBytes.size + 2 + stun.size)
            var pos = 0
            payload[pos++] = 0; payload[pos++] = 0; payload[pos++] = 0 // RSV + FRAG
            payload[pos++] = 3
            payload[pos++] = hostBytes.size.toByte()
            System.arraycopy(hostBytes, 0, payload, pos, hostBytes.size); pos += hostBytes.size
            payload[pos++] = (targetPort ushr 8).toByte(); payload[pos++] = targetPort.toByte()
            System.arraycopy(stun, 0, payload, pos, stun.size)

            DatagramSocket().use { udp ->
                udp.soTimeout = timeoutMs
                udp.send(DatagramPacket(payload, payload.size, relay))
                val response = ByteArray(2048)
                val packet = DatagramPacket(response, response.size)
                udp.receive(packet)
                val bodyOffset = socksUdpBodyOffset(response, packet.length)
                require(packet.length - bodyOffset >= 20) { "Short STUN response" }
                require(response[bodyOffset] == 0x01.toByte() && response[bodyOffset + 1] == 0x01.toByte()) {
                    "Not a STUN binding response"
                }
                require(
                    response[bodyOffset + 4] == 0x21.toByte() &&
                        response[bodyOffset + 5] == 0x12.toByte() &&
                        response[bodyOffset + 6] == 0xA4.toByte() &&
                        response[bodyOffset + 7] == 0x42.toByte()
                ) { "Bad STUN magic" }
                for (i in tx.indices) require(response[bodyOffset + 8 + i] == tx[i]) { "STUN transaction mismatch" }
            }
        }
        ((System.nanoTime() - started) / 1_000_000L).toInt().coerceAtLeast(1)
    }.getOrDefault(-1)

    private fun socksUdpBodyOffset(data: ByteArray, length: Int): Int {
        require(length >= 4 && data[0] == 0.toByte() && data[1] == 0.toByte() && data[2] == 0.toByte())
        var pos = 3
        when (data[pos++].toInt() and 0xff) {
            1 -> pos += 4
            3 -> {
                require(pos < length)
                pos += 1 + (data[pos].toInt() and 0xff)
            }
            4 -> pos += 16
            else -> error("Invalid SOCKS UDP ATYP")
        }
        pos += 2
        require(pos <= length)
        return pos
    }

    private fun readFully(input: BufferedInputStream, buffer: ByteArray) {
        var p = 0
        while (p < buffer.size) {
            val n = input.read(buffer, p, min(buffer.size - p, 64))
            require(n > 0) { "Unexpected EOF" }
            p += n
        }
    }
}

