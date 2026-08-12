package com.marbleng.app.core

import java.net.InetSocketAddress
import java.net.Socket

/**
 * Measures *real* egress latency through the local SOCKS proxy.
 *
 * The previous approach timed only the SOCKS5 CONNECT reply — but Xray answers
 * that locally before it dials the upstream, so it reported ~2-4 ms (localhost
 * round-trip) instead of the true path RTT.
 *
 * Here we complete the tunnel: after the SOCKS handshake we send a real HTTP
 * request to a tiny "204 No Content" endpoint over plain HTTP (port 80, no TLS
 * to skew the number) and time until the first response byte arrives. That
 * interval includes the actual outbound dial + server round-trip, which is what
 * a "ping" should reflect.
 */
object LatencyProbe {
    private const val HOST = "www.gstatic.com"
    private const val PATH = "/generate_204"

    /** Returns egress latency in ms through 127.0.0.1:[port], or -1 on failure. */
    fun through(port: Int, timeoutMs: Int = 5000): Int = runCatching {
        Socket().use { s ->
            s.tcpNoDelay = true
            s.soTimeout = timeoutMs
            s.connect(InetSocketAddress("127.0.0.1", port), timeoutMs)
            val o = s.getOutputStream()
            val i = s.getInputStream()

            // SOCKS5 greeting (no auth)
            o.write(byteArrayOf(5, 1, 0)); o.flush()
            if (i.read() != 5 || i.read() != 0) return@runCatching -1

            // CONNECT to HOST:80 by domain name
            val host = HOST.toByteArray()
            o.write(byteArrayOf(5, 1, 0, 3, host.size.toByte())); o.write(host)
            o.write(byteArrayOf(0, 80)); o.flush()
            val rep = ByteArray(4); readFully(i, rep)
            if (rep[1].toInt() != 0) return@runCatching -1
            // consume the bound address so the stream is positioned at payload
            when (rep[3].toInt()) { 1 -> skip(i, 4); 3 -> skip(i, i.read()); 4 -> skip(i, 16) }
            skip(i, 2)

            // Time a real HTTP round-trip: send request, wait for first response byte.
            val req = "HEAD $PATH HTTP/1.1\r\nHost: $HOST\r\nConnection: close\r\nUser-Agent: MarbleNG/1\r\n\r\n"
            val start = System.nanoTime()
            o.write(req.toByteArray()); o.flush()
            if (i.read() < 0) return@runCatching -1
            ((System.nanoTime() - start) / 1_000_000L).toInt()
        }
    }.getOrDefault(-1)

    private fun readFully(i: java.io.InputStream, b: ByteArray) {
        var p = 0; while (p < b.size) { val n = i.read(b, p, b.size - p); if (n <= 0) throw java.io.EOFException(); p += n }
    }
    private fun skip(i: java.io.InputStream, n: Int) {
        var left = n; val buf = ByteArray(32); while (left > 0) { val z = i.read(buf, 0, minOf(left, buf.size)); if (z <= 0) break; left -= z }
    }
}
