package com.marbleng.app.jitter

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * MARBLE_SMART_JITTER_V238 — the module's own probe channel: a **warm, keep-alive** HTTPS
 * connection through the local SOCKS inbound.
 *
 * The legacy route probe pays a full TCP+TLS handshake per sample, which is exactly the
 * "cold sample" false positive the Bug Finder report (v11.2.3, P0-2) documented — a 2.2 s first
 * sample next to a 140 ms warm one reads as 2 seconds of jitter that never happened on the
 * path. This channel keeps the tunnel's TLS session open and measures request→first-byte on the
 * *same* connection, so every sample after the first is an in-path RTT.
 *
 * Contract:
 *  - `probe()` returns `(rttMs, cold)`. `cold = true` on the first sample of a (re)connect — the
 *    caller feeds it to [JitterWindow.add] which keeps it out of the statistics.
 *  - Any failure returns `Pair(0.0, false)` with `cold` irrelevant; the caller records a loss.
 *  - Everything is bounded by [timeoutMs]; a single `close()` from the owning coroutine is
 *    cancel-safe (the socket's `soTimeout` bounds every blocking read).
 *  - No user data travels: one GET of a fixed path, one small read, no body retention.
 *
 * The channel is deliberately Android-free (java.net + JSSE only) so it is unit-testable on the
 * host JVM against a local socket if ever needed.
 */
class KeepaliveHttpsProbe(
    val socksPort: Int,
    val host: String,
    private val path: String = DEFAULT_PATH,
    private val targetPort: Int = 443,
    private var timeoutMs: Int = DEFAULT_TIMEOUT_MS,
    private val tlsHost: String = host
) : AutoCloseable {

    private var socket: Socket? = null
    private var output: BufferedOutputStream? = null
    private var input: BufferedInputStream? = null
    private var everConnected = false

    /** True when this channel serves the given SOCKS port and probe host. */
    fun matches(port: Int, probeHost: String): Boolean =
        port == socksPort && probeHost == host

    val isWarm: Boolean get() = everConnected && socket?.isClosed == false

    // MARBLE_SMART_JITTER_V238 — the measurement window rides the channel: a rebuilt channel
    // (port or target change) starts cold by construction, and the controller's loop feeds the
    // sample in and reads the verdict without a second lock.
    private val window = JitterWindow()

    fun add(rttMs: Double, ok: Boolean = true, cold: Boolean = false) =
        window.add(rttMs = rttMs, ok = ok, cold = cold)

    fun summarize(minSamples: Int = JitterWindow.DEFAULT_MIN_SAMPLES): JitterWindow.Snapshot? =
        window.summarize(minSamples)

    @Synchronized
    fun probe(): Pair<Double, Boolean> {
        // MARBLE_SMART_JITTER_V238 — the cold flag is decided *before* the attempt: when the
        // channel is not warm (first sample ever, or the previous connection died), this sample
        // carries a full TLS handshake and must stay out of the statistics. The first draft only
        // flagged failures, which let every fresh session's handshake cost into the mean.
        val cold = !isWarm
        val started = System.nanoTime()
        return try {
            ensureConnected()
            val request = buildString {
                append("GET ").append(path).append(" HTTP/1.1\r\n")
                append("Host: ").append(tlsHost).append("\r\n")
                append("User-Agent: MarbleNG/1\r\n")
                append("Accept-Encoding: identity\r\n")
                append("Connection: keep-alive\r\n")
                append("\r\n")
            }
            output!!.write(request.toByteArray(Charsets.ISO_8859_1))
            output!!.flush()
            val statusLine = readStatusLine()
            if (!statusLine.startsWith("HTTP/")) error("not an HTTP reply")
            consumeHeaders()
            val rttMs = (System.nanoTime() - started) / 1_000_000.0
            everConnected = true
            rttMs to cold
        } catch (_: Throwable) {
            closeQuietly()
            0.0 to false
        }
    }

    private fun ensureConnected() {
        if (socket != null && socket!!.isClosed.not() && input != null && output != null) return
        closeQuietly()

        val tcp = Socket()
        tcp.soTimeout = timeoutMs
        tcp.tcpNoDelay = true
        tcp.connect(InetSocketAddress("127.0.0.1", socksPort), timeoutMs)

        val out = BufferedOutputStream(tcp.getOutputStream())
        val inp = BufferedInputStream(tcp.getInputStream())

        out.write(byteArrayOf(5, 1, 0))
        out.flush()
        if (inp.read() != 5 || inp.read() != 0) error("SOCKS auth negotiation failed")

        val target = socksTarget(host)
        out.write(buildSocks5Request(target.first, target.second, targetPort))
        out.flush()
        val reply = ByteArray(4)
        readFully(inp, reply)
        if (reply[0].toInt() != 5 || reply[1].toInt() != 0) {
            error("SOCKS connect failed: ${reply[1].toInt() and 0xff}")
        }
        when (reply[3].toInt() and 0xff) {
            1 -> skip(inp, 4)
            3 -> skip(inp, inp.read().coerceAtLeast(0))
            4 -> skip(inp, 16)
            else -> error("Invalid SOCKS address type")
        }
        skip(inp, 2)

        val context = SSLContext.getInstance("TLS")
        context.init(null, null, SecureRandom())
        val factory = context.socketFactory
        val ssl = factory.createSocket(tcp, tlsHost, targetPort, true) as SSLSocket
        ssl.soTimeout = timeoutMs
        ssl.tcpNoDelay = true
        ssl.startHandshake()

        socket = ssl
        output = BufferedOutputStream(ssl.getOutputStream())
        input = BufferedInputStream(ssl.getInputStream())
    }

    private fun readStatusLine(): String {
        val line = StringBuilder(32)
        while (true) {
            val c = input!!.read()
            if (c < 0 || c == '\n'.code) break
            if (c != '\r'.code) line.append(c.toChar())
            if (line.length > 128) break
        }
        return line.toString()
    }

    private fun consumeHeaders() {
        var blank = false
        while (!blank) {
            val line = StringBuilder(64)
            while (true) {
                val c = input!!.read()
                if (c < 0 || c == '\n'.code) break
                if (c != '\r'.code) line.append(c.toChar())
                if (line.length > 512) break
            }
            if (line.isEmpty()) blank = true
        }
    }

    private fun socksTarget(host: String): Pair<String, Int> {
        val trimmed = host.trim().trim('[', ']').removePrefix("http://").removePrefix("https://")
            .substringBefore('/').substringBefore(':')
        require(trimmed.isNotBlank()) { "probe host is blank" }
        if (trimmed.contains(':')) {
            // A literal IPv6 address (already bracket-stripped): SOCKS type 4 with the literal.
            return trimmed to SOCKS_ATYP_IPV6
        }
        return trimmed to (if (trimmed.all { it.isDigit() || it == '.' }) SOCKS_ATYP_IPV4 else SOCKS_ATYP_DOMAIN)
    }

    private fun buildSocks5Request(host: String, atyp: Int, port: Int): ByteArray = when (atyp) {
        SOCKS_ATYP_IPV4 -> {
            val parts = host.split('.').map { it.toIntOrNull() ?: 0 }
            ByteArray(4 + 6).apply {
                this[0] = 5; this[1] = 1; this[2] = 0; this[3] = 4.toByte()
                this[4] = (parts.getOrElse(0) { 0 }).toByte()
                this[5] = (parts.getOrElse(1) { 0 }).toByte()
                this[6] = (parts.getOrElse(2) { 0 }).toByte()
                this[7] = (parts.getOrElse(3) { 0 }).toByte()
                this[8] = (port shr 8).toByte(); this[9] = port.toByte()
            }
        }
        SOCKS_ATYP_IPV6 -> {
            val bytes = ipv6Bytes(host)
            ByteArray(4 + 18).apply {
                this[0] = 5; this[1] = 1; this[2] = 0; this[3] = 4.toByte()
                bytes.copyInto(this, 4)
                this[20] = (port shr 8).toByte(); this[21] = port.toByte()
            }
        }
        else -> {
            val name = host.toByteArray(Charsets.US_ASCII)
            ByteArray(4 + 1 + name.size + 2).apply {
                this[0] = 5; this[1] = 1; this[2] = 0; this[3] = 3
                this[4] = name.size.toByte()
                name.copyInto(this, 5)
                this[5 + name.size] = (port shr 8).toByte()
                this[6 + name.size] = port.toByte()
            }
        }
    }

    private fun ipv6Bytes(host: String): ByteArray {
        val expanded = expandIpv6(host)
        val bytes = ByteArray(16)
        expanded.forEachIndexed { index, group ->
            bytes[index * 2] = (group shr 8).toByte()
            bytes[index * 2 + 1] = group.toByte()
        }
        return bytes
    }

    private fun expandIpv6(raw: String): IntArray {
        val text = raw.substringBefore('%')
        val pieces = if (text.contains("::")) {
            val head = text.substringBefore("::").split(':').filter { it.isNotEmpty() }
            val tail = text.substringAfter("::").split(':').filter { it.isNotEmpty() }
            val missing = 8 - head.size - tail.size
            head.map { it.toInt(16) } + IntArray(missing.coerceAtLeast(0)) { 0 }.toList() + tail.map { it.toInt(16) }
        } else {
            text.split(':').filter { it.isNotEmpty() }.map { it.toInt(16) }
        }
        require(pieces.size == 8) { "bad IPv6 literal: $raw" }
        return pieces.toIntArray()
    }

    private fun readFully(input: BufferedInputStream, buffer: ByteArray) {
        var read = 0
        while (read < buffer.size) {
            val n = input.read(buffer, read, buffer.size - read)
            if (n < 0) throw IOException("SOCKS reply truncated")
            read += n
        }
    }

    private fun skip(input: BufferedInputStream, count: Int) {
        var remaining = count
        while (remaining > 0) {
            val n = input.read()
            if (n < 0) throw IOException("SOCKS reply truncated")
            remaining--
        }
    }

    private fun closeQuietly() {
        runCatching { input?.close() }
        runCatching { output?.close() }
        runCatching { socket?.close() }
        input = null
        output = null
        socket = null
    }

    @Synchronized
    override fun close() {
        closeQuietly()
        everConnected = false
    }

    companion object {
        const val DEFAULT_PATH = "/cdn-cgi/trace"
        const val DEFAULT_TIMEOUT_MS = 2_500
        private const val SOCKS_ATYP_IPV4 = 1
        private const val SOCKS_ATYP_DOMAIN = 3
        private const val SOCKS_ATYP_IPV6 = 4
    }
}
