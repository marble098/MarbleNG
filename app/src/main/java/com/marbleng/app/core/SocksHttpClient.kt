package com.marbleng.app.core

import android.os.SystemClock
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.Locale
import java.util.zip.GZIPInputStream
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlin.math.min

data class TunnelRttBatch(
    val samplesMs: List<Double>,
    val warmupMs: Double
)

data class HttpProbe(
    val status: Int,
    val body: ByteArray,
    val elapsedMs: Double,
    val bytesPerSecond: Double,
    val headers: Map<String, String> = emptyMap()
)

object SocksHttpClient {
    // MARBLE_LITERAL_SOCKS_V13
    // MARBLE_LOW_NOISE_PROBE_V18
    // MARBLE_VERIFIED_RTT_V19
    // MARBLE_WARM_TUNNEL_RANK_V42
    // MARBLE_RANK_RECOVERY_CARD_UX_V43
    // MARBLE_REPEATABLE_RANK_V44
    // MARBLE_V2RAYNG_SMART_RANK_V45
    // MARBLE_SNI_RTT_V47
    // MARBLE_IPV6_HOST_HEADER_V65

    /**
     * The HTTP Host header for a target.
     *
     * An IPv6 literal has to be bracketed — `Host: 2606:4700::1:443` is ambiguous and strict servers
     * reject it — while a hostname or an IPv4 literal is left exactly as written. The TLS server name
     * keeps the unbracketed form, because that is what certificate verification matches against.
     */
    private fun httpHostHeader(host: String, port: Int, defaultPort: Int = 443): String {
        val literal = if (host.contains(':') && !host.startsWith("[")) "[$host]" else host
        return if (port == defaultPort) literal else "$literal:$port"
    }

    fun get(
        port: Int,
        host: String,
        path: String = "/",
        timeoutMs: Int = 8000,
        maxBytes: Int = 1024 * 1024
    ): HttpProbe = request(port, host, 443, "GET", path, null, timeoutMs, maxBytes)

    /**
     * HTTPS GET through SOCKS5 using ATYP=domain, so the Android resolver never sees the target
     * hostname. Redirects remain inside the same SOCKS path. Cleartext HTTP is intentionally
     * rejected for management traffic.
     */
    fun getTextUrl(
        port: Int,
        url: String,
        timeoutMs: Int = 30_000,
        maxBytes: Int = 8 * 1024 * 1024,
        redirectsLeft: Int = 5
    ): String {
        require(redirectsLeft >= 0) { "Too many redirects" }

        val target = URL(url)
        require(target.protocol.equals("https", ignoreCase = true)) {
            "Only HTTPS management requests are allowed while a tunnel is active"
        }

        val targetPort = target.port.takeIf { it > 0 } ?: 443
        val path = target.file.takeIf { it.isNotBlank() } ?: "/"
        val probe = request(
            port = port,
            host = target.host,
            targetPort = targetPort,
            method = "GET",
            path = path,
            body = null,
            timeoutMs = timeoutMs,
            maxBytes = maxBytes,
            headers = mapOf("Accept-Encoding" to "identity")
        )

        if (probe.status in 300..399) {
            val location = probe.headers["location"] ?: error("HTTPS redirect without Location")
            require(redirectsLeft > 0) { "Too many redirects" }
            return getTextUrl(
                port,
                URL(target, location).toString(),
                timeoutMs,
                maxBytes,
                redirectsLeft - 1
            )
        }

        require(probe.status in 200..299) { "HTTPS ${probe.status} from ${target.host}" }
        return probe.body.toString(Charsets.UTF_8)
    }

    /**
     * Low-noise route timing for live jitter control.
     *
     * Measures only: local SOCKS negotiation -> remote TCP connect. It deliberately avoids DNS,
     * TLS and HTTP so the live "jitter" number is not polluted by resolver timeouts, certificate
     * work, CDN origin differences or response-body scheduling.
     *
     * Pass a literal IP (Marble currently pins one per session) to keep the sample DNS-free.
     */
    fun connectLatency(
        port: Int,
        host: String,
        targetPort: Int = 443,
        timeoutMs: Int = 2_000
    ): Double {
        require(port in 1..65535)
        require(targetPort in 1..65535)
        require(host.isNotBlank())
        require(timeoutMs in 250..30_000)

        val started = System.nanoTime()
        val tcp = Socket()
        try {
            tcp.soTimeout = timeoutMs
            tcp.tcpNoDelay = true
            tcp.connect(InetSocketAddress("127.0.0.1", port), timeoutMs)

            val output = BufferedOutputStream(tcp.getOutputStream())
            val input = BufferedInputStream(tcp.getInputStream())

            output.write(byteArrayOf(5, 1, 0))
            output.flush()
            require(input.read() == 5 && input.read() == 0) {
                "SOCKS auth negotiation failed"
            }

            val target = socksTarget(host)
            output.write(byteArrayOf(5, 1, 0, target.first.toByte()))
            output.write(target.second)
            output.write(byteArrayOf((targetPort ushr 8).toByte(), targetPort.toByte()))
            output.flush()

            val reply = ByteArray(4)
            readFully(input, reply)
            require(reply[0].toInt() == 5 && reply[1].toInt() == 0) {
                "SOCKS connect failed: ${reply[1].toInt() and 0xff}"
            }
            when (reply[3].toInt() and 0xff) {
                1 -> skip(input, 4)
                3 -> {
                    val length = input.read()
                    require(length >= 0)
                    skip(input, length)
                }
                4 -> skip(input, 16)
                else -> error("Invalid SOCKS address type")
            }
            skip(input, 2)

            return (System.nanoTime() - started) / 1e6
        } finally {
            runCatching { tcp.close() }
        }
    }


    /**
     * Verified live application RTT through the already-running Xray route.
     *
     * connectLatency() remains available for callers that explicitly need SOCKS CONNECT setup
     * timing, but setup timing must not be displayed as Internet ping: some transports can accept
     * CONNECT before a complete remote application round trip has actually happened.
     *
     * This probe:
     *  1. opens SOCKS5 to a literal IP (DNS-free),
     *  2. completes certificate-verified TLS,
     *  3. starts the latency clock only after TLS is ready,
     *  4. sends a tiny HTTPS request,
     *  5. stops only after the first remote response byte arrives.
     *
     * Thus the displayed RTT requires genuine remote response traffic while excluding DNS,
     * TCP/TLS cold-start setup and body-download time.
     */
    fun httpsFirstByteLatency(
        port: Int,
        host: String,
        path: String = "/cdn-cgi/trace",
        targetPort: Int = 443,
        timeoutMs: Int = 2_500,
        tlsHost: String = host
    ): Double {
        require(port in 1..65535)
        require(targetPort in 1..65535)
        require(host.isNotBlank())
        require(tlsHost.isNotBlank())
        require(path.startsWith('/'))
        require(timeoutMs in 500..30_000)

        val tcp = Socket()
        var ssl: SSLSocket? = null
        try {
            tcp.soTimeout = timeoutMs
            tcp.tcpNoDelay = true
            tcp.connect(InetSocketAddress("127.0.0.1", port), timeoutMs)

            val output = BufferedOutputStream(tcp.getOutputStream())
            val input = BufferedInputStream(tcp.getInputStream())

            output.write(byteArrayOf(5, 1, 0))
            output.flush()
            require(input.read() == 5 && input.read() == 0) {
                "SOCKS auth negotiation failed"
            }

            val target = socksTarget(host)
            output.write(byteArrayOf(5, 1, 0, target.first.toByte()))
            output.write(target.second)
            output.write(
                byteArrayOf(
                    (targetPort ushr 8).toByte(),
                    targetPort.toByte()
                )
            )
            output.flush()

            val reply = ByteArray(4)
            readFully(input, reply)
            require(reply[0].toInt() == 5 && reply[1].toInt() == 0) {
                "SOCKS connect failed: ${reply[1].toInt() and 0xff}"
            }
            when (reply[3].toInt() and 0xff) {
                1 -> skip(input, 4)
                3 -> {
                    val length = input.read()
                    require(length >= 0)
                    skip(input, length)
                }
                4 -> skip(input, 16)
                else -> error("Invalid SOCKS address type")
            }
            skip(input, 2)

            val secure = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                .createSocket(tcp, tlsHost, targetPort, true) as SSLSocket
            ssl = secure
            secure.soTimeout = timeoutMs
            secure.tcpNoDelay = true

            val parameters = secure.sslParameters
            parameters.endpointIdentificationAlgorithm = "HTTPS"
            secure.sslParameters = parameters
            secure.startHandshake()

            val sslOut = BufferedOutputStream(secure.getOutputStream())
            val sslIn = BufferedInputStream(secure.getInputStream())
            val hostHeader = httpHostHeader(tlsHost, targetPort)
            val request = buildString {
                append("GET $path HTTP/1.1\r\n")
                append("Host: $hostHeader\r\n")
                append("User-Agent: MarbleNG/1\r\n")
                append("Accept-Encoding: identity\r\n")
                append("Connection: close\r\n")
                append("\r\n")
            }.toByteArray(Charsets.ISO_8859_1)

            val started = SystemClock.elapsedRealtimeNanos()
            sslOut.write(request)
            sslOut.flush()

            require(sslIn.read() >= 0) {
                "HTTPS peer closed before response"
            }
            return (SystemClock.elapsedRealtimeNanos() - started) / 1e6
        } finally {
            runCatching { ssl?.close() }
            runCatching { tcp.close() }
        }
    }

    /**
     * Xray real-delay semantics aligned with v2rayNG 2.3.5:
     * two complete HTTPS GET attempts, only 200/204 is healthy, and the caller keeps the minimum.
     * Attempt one includes SOCKS/outbound/TLS setup; attempt two reuses the same verified session
     * when the origin permits keep-alive. A later failure never erases an earlier valid response.
     */
    fun tunnelRttBatch(
        port: Int,
        host: String,
        path: String = "/generate_204",
        samples: Int = 2,
        timeoutMs: Int = 8_000
    ): TunnelRttBatch {
        require(port in 1..65535)
        require(host.isNotBlank())
        require(path.startsWith('/'))
        require(samples in 1..8)
        require(timeoutMs in 1_000..12_000)

        val sessionStarted = SystemClock.elapsedRealtimeNanos()
        val tcp = Socket()
        var ssl: SSLSocket? = null
        try {
            tcp.soTimeout = timeoutMs
            tcp.tcpNoDelay = true
            tcp.connect(InetSocketAddress("127.0.0.1", port), timeoutMs)

            val output = BufferedOutputStream(tcp.getOutputStream())
            val input = BufferedInputStream(tcp.getInputStream())
            output.write(byteArrayOf(5, 1, 0))
            output.flush()
            require(input.read() == 5 && input.read() == 0) { "SOCKS auth negotiation failed" }

            val target = socksTarget(host)
            output.write(byteArrayOf(5, 1, 0, target.first.toByte()))
            output.write(target.second)
            output.write(byteArrayOf(0x01, 0xbb.toByte()))
            output.flush()

            val reply = ByteArray(4)
            readFully(input, reply)
            require(reply[0].toInt() == 5 && reply[1].toInt() == 0) {
                "SOCKS connect failed: ${reply[1].toInt() and 0xff}"
            }
            when (reply[3].toInt() and 0xff) {
                1 -> skip(input, 4)
                3 -> { val length = input.read(); require(length >= 0); skip(input, length) }
                4 -> skip(input, 16)
                else -> error("Invalid SOCKS address type")
            }
            skip(input, 2)

            val secure = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                .createSocket(tcp, host, 443, true) as SSLSocket
            ssl = secure
            secure.soTimeout = timeoutMs
            secure.tcpNoDelay = true
            val parameters = secure.sslParameters
            parameters.endpointIdentificationAlgorithm = "HTTPS"
            secure.sslParameters = parameters
            secure.startHandshake()

            val sslOut = BufferedOutputStream(secure.getOutputStream())
            val sslIn = BufferedInputStream(secure.getInputStream())
            val measured = ArrayList<Double>(samples)

            fun readLine(limit: Int = 16 * 1024): String {
                val bytes = ByteArrayOutputStream()
                var previous = -1
                while (bytes.size() < limit) {
                    val next = sslIn.read()
                    require(next >= 0) { "HTTPS peer closed mid-response" }
                    if (previous == '\r'.code && next == '\n'.code) {
                        val raw = bytes.toByteArray()
                        return String(raw, 0, (raw.size - 1).coerceAtLeast(0), Charsets.ISO_8859_1)
                    }
                    bytes.write(next)
                    previous = next
                }
                error("HTTPS line exceeds $limit bytes")
            }

            fun consumeBody(status: Int, headers: Map<String, String>) {
                if (status in 100..199 || status == 204 || status == 304) return
                val chunked = headers["transfer-encoding"]?.contains("chunked", true) == true
                val contentLength = headers["content-length"]?.toLongOrNull()
                var consumed = 0L
                if (chunked) {
                    while (true) {
                        val size = readLine().substringBefore(';').trim().toLongOrNull(16)
                            ?: error("Malformed chunk size")
                        if (size == 0L) {
                            while (readLine().isNotEmpty()) Unit
                            break
                        }
                        require(consumed + size <= 64L * 1024L) { "Delay response body too large" }
                        var left = size
                        val buffer = ByteArray(4096)
                        while (left > 0L) {
                            val read = sslIn.read(buffer, 0, min(buffer.size.toLong(), left).toInt())
                            require(read > 0) { "Truncated chunked response" }
                            left -= read
                            consumed += read
                        }
                        require(sslIn.read() == '\r'.code && sslIn.read() == '\n'.code) {
                            "Malformed chunk terminator"
                        }
                    }
                } else if (contentLength != null) {
                    require(contentLength in 0..(64L * 1024L)) { "Delay response body too large" }
                    var left = contentLength
                    val buffer = ByteArray(4096)
                    while (left > 0L) {
                        val read = sslIn.read(buffer, 0, min(buffer.size.toLong(), left).toInt())
                        require(read > 0) { "Truncated HTTPS body" }
                        left -= read
                    }
                } else {
                    error("Lengthless 200 response cannot be safely reused")
                }
            }

            for (index in 0 until samples) {
                try {
                    val separator = if ('?' in path) '&' else '?'
                    val requestPath = "$path${separator}marble=${SystemClock.elapsedRealtimeNanos()}-$index"
                    val request = buildString {
                        append("GET $requestPath HTTP/1.1\r\n")
                        append("Host: ${httpHostHeader(host, targetPort)}\r\n")
                        append("User-Agent: MarbleNG/1\r\n")
                        append("Accept: */*\r\n")
                        append("Accept-Encoding: identity\r\n")
                        append("Cache-Control: no-cache\r\n")
                        append("Connection: keep-alive\r\n\r\n")
                    }.toByteArray(Charsets.ISO_8859_1)

                    val started = if (index == 0) sessionStarted else SystemClock.elapsedRealtimeNanos()
                    sslOut.write(request)
                    sslOut.flush()

                    val status = readLine().split(' ').getOrNull(1)?.toIntOrNull() ?: 0
                    val headers = linkedMapOf<String, String>()
                    while (true) {
                        val line = readLine()
                        if (line.isEmpty()) break
                        val colon = line.indexOf(':')
                        if (colon > 0) headers[line.substring(0, colon).trim().lowercase(Locale.US)] =
                            line.substring(colon + 1).trim()
                    }
                    consumeBody(status, headers)
                    require(status == 200 || status == 204) { "Delay endpoint returned HTTP $status" }

                    val elapsed = (SystemClock.elapsedRealtimeNanos() - started) / 1e6
                    if (elapsed.isFinite() && elapsed > 0.0) measured += elapsed
                    if (headers["connection"]?.equals("close", true) == true) break
                } catch (error: Throwable) {
                    if (measured.isEmpty()) throw error
                    break
                }
            }

            require(measured.isNotEmpty()) { "No valid 200/204 real-delay response" }
            return TunnelRttBatch(measured, measured.first())
        } finally {
            runCatching { ssl?.close() }
            runCatching { tcp.close() }
        }
    }

    fun request(
        port: Int,
        host: String,
        targetPort: Int = 443,
        method: String = "GET",
        path: String = "/",
        body: ByteArray? = null,
        timeoutMs: Int = 10_000,
        maxBytes: Int = 1024 * 1024,
        headers: Map<String, String> = emptyMap()
    ): HttpProbe {
        require(port in 1..65535)
        require(targetPort in 1..65535)
        require(host.isNotBlank())
        require(maxBytes > 0)

        val start = System.nanoTime()
        val tcp = Socket()
        var ssl: SSLSocket? = null

        /*
         * Every failure below (SOCKS refusal, TLS handshake, truncated response, size limit) must
         * release the local socket. Benchmarks probe hundreds of dead nodes per run, so leaking one
         * file descriptor per failure exhausts the process FD table and breaks the whole engine.
         */
        try {
            tcp.soTimeout = timeoutMs
            tcp.connect(InetSocketAddress("127.0.0.1", port), timeoutMs)

            val output = BufferedOutputStream(tcp.getOutputStream())
            val input = BufferedInputStream(tcp.getInputStream())

            // SOCKS5 no-auth negotiation.
            output.write(byteArrayOf(5, 1, 0))
            output.flush()
            require(input.read() == 5 && input.read() == 0) { "SOCKS auth negotiation failed" }

            // Use a real literal address type when the caller supplied an IPv4 literal.
            // The previous implementation sent even "1.1.1.1" as ATYP=domain, so the so-called
            // DNS-independent transport probe could still enter Xray's domain-resolution path.
            val target = socksTarget(host)
            // ATYP=1/4 keeps a literal address literal and ATYP=domain keeps ordinary hostnames away
            // from Android/system DNS.
            output.write(byteArrayOf(5, 1, 0, target.first.toByte()))
            output.write(target.second)
            output.write(byteArrayOf((targetPort ushr 8).toByte(), targetPort.toByte()))
            output.flush()

            val reply = ByteArray(4)
            readFully(input, reply)
            require(reply[0].toInt() == 5 && reply[1].toInt() == 0) {
                "SOCKS connect failed: ${reply[1].toInt() and 0xff}"
            }
            when (reply[3].toInt() and 0xff) {
                1 -> skip(input, 4)
                3 -> {
                    val length = input.read()
                    require(length >= 0)
                    skip(input, length)
                }
                4 -> skip(input, 16)
                else -> error("Invalid SOCKS address type")
            }
            skip(input, 2)

            val secure = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                .createSocket(tcp, host, targetPort, true) as SSLSocket
            ssl = secure
            secure.soTimeout = timeoutMs

            /*
             * Raw SSLSocket does not automatically enable HTTPS endpoint identification on every
             * Android/JSSE path. Enforce hostname verification before the handshake.
             */
            val parameters = secure.sslParameters
            parameters.endpointIdentificationAlgorithm = "HTTPS"
            secure.sslParameters = parameters
            secure.startHandshake()

            val sslOut = BufferedOutputStream(secure.getOutputStream())
            val sslIn = BufferedInputStream(secure.getInputStream())

            val normalizedHeaders = LinkedHashMap<String, String>()
            normalizedHeaders["User-Agent"] = "MarbleNG/1"
            normalizedHeaders["Connection"] = "close"
            if (headers.keys.none { it.equals("Accept-Encoding", ignoreCase = true) }) {
                normalizedHeaders["Accept-Encoding"] = "identity"
            }
            headers.forEach { (key, value) -> normalizedHeaders[key] = value }

            val hostHeader = httpHostHeader(host, targetPort)
            val requestText = buildString {
                append("$method $path HTTP/1.1\r\n")
                append("Host: $hostHeader\r\n")
                normalizedHeaders.forEach { (key, value) -> append("$key: $value\r\n") }
                if (body != null) append("Content-Length: ${body.size}\r\n")
                append("\r\n")
            }

            sslOut.write(requestText.toByteArray(Charsets.ISO_8859_1))
            if (body != null) sslOut.write(body)
            sslOut.flush()

            val raw = readToLimit(sslIn, maxBytes + 64 * 1024)
            val separator = "\r\n\r\n".toByteArray(Charsets.ISO_8859_1)
            val headerEnd = indexOf(raw, separator, 0)
            require(headerEnd >= 0) { "Invalid HTTPS response" }

            val headerText = String(raw, 0, headerEnd, Charsets.ISO_8859_1)
            val headerLines = headerText.split("\r\n")
            val status = headerLines.firstOrNull()
                ?.split(' ')
                ?.getOrNull(1)
                ?.toIntOrNull()
                ?: 0

            val responseHeaders = linkedMapOf<String, String>()
            headerLines.drop(1).forEach { line ->
                val colon = line.indexOf(':')
                if (colon > 0) {
                    val key = line.substring(0, colon).trim().lowercase(Locale.US)
                    val value = line.substring(colon + 1).trim()
                    responseHeaders[key] = responseHeaders[key]
                        ?.let { "$it, $value" }
                        ?: value
                }
            }

            var payload = raw.copyOfRange(headerEnd + separator.size, raw.size)
            if (responseHeaders["transfer-encoding"]?.contains("chunked", ignoreCase = true) == true) {
                payload = decodeChunked(payload, maxBytes)
            }
            if (responseHeaders["content-encoding"]?.contains("gzip", ignoreCase = true) == true) {
                payload = GZIPInputStream(ByteArrayInputStream(payload)).use { gzip ->
                    readToLimit(gzip, maxBytes)
                }
            }

            require(payload.size <= maxBytes) { "HTTPS response exceeds $maxBytes bytes" }

            val elapsed = (System.nanoTime() - start) / 1e6

            return HttpProbe(
                status = status,
                body = payload,
                elapsedMs = elapsed,
                bytesPerSecond = if (elapsed > 0) payload.size / (elapsed / 1000.0) else 0.0,
                headers = responseHeaders
            )
        } finally {
            runCatching { ssl?.close() }
            runCatching { tcp.close() }
        }
    }


    /**
     * The SOCKS5 target for a literal IPv4 (ATYP 1), literal IPv6 (ATYP 4) or hostname (ATYP 3).
     *
     * Only ATYP 1 and 3 were supported, so an IPv6 node — bare or bracketed — went out as a
     * *hostname* called "2606:…". The proxy tried to resolve that, failed, and every measurement for
     * the node reported it unreachable: IPv6 endpoints looked broken instead of connectable.
     */
    private fun socksTarget(host: String): Pair<Int, ByteArray> {
        literalIpv4Bytes(host)?.let { return 1 to it }
        literalIpv6Bytes(host)?.let { return 4 to it }
        val name = host.trim().removePrefix("[").removeSuffix("]")
        val hostBytes = name.toByteArray(Charsets.UTF_8)
        require(hostBytes.size in 1..255) { "SOCKS hostname too long" }
        return 3 to hostBytes
    }

    /** A bracketed or bare IPv6 literal, resolved syntactically — never through DNS. */
    private fun literalIpv6Bytes(host: String): ByteArray? {
        val raw = host.trim().removePrefix("[").removeSuffix("]")
        if (!raw.contains(':')) return null
        val bytes = runCatching { java.net.InetAddress.getByName(raw).address }.getOrNull() ?: return null
        return if (bytes.size == 16) bytes else null
    }

    private fun literalIpv4Bytes(host: String): ByteArray? {
        val parts = host.trim().split('.')
        if (parts.size != 4) return null

        val values = IntArray(4)
        for (i in parts.indices) {
            val part = parts[i]
            if (part.isBlank() || (part.length > 1 && part.startsWith('0'))) return null
            val value = part.toIntOrNull() ?: return null
            if (value !in 0..255) return null
            values[i] = value
        }

        return byteArrayOf(
            values[0].toByte(),
            values[1].toByte(),
            values[2].toByte(),
            values[3].toByte()
        )
    }

    private fun readToLimit(input: java.io.InputStream, limit: Int): ByteArray {
        val out = ByteArrayOutputStream(min(limit, 64 * 1024))
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val read = runCatching { input.read(buffer) }.getOrDefault(-1)
            if (read <= 0) break
            total += read
            require(total <= limit) { "Response exceeds $limit bytes" }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun decodeChunked(data: ByteArray, maxBytes: Int): ByteArray {
        val crlf = "\r\n".toByteArray(Charsets.ISO_8859_1)
        val out = ByteArrayOutputStream(min(data.size, maxBytes))
        var position = 0

        while (true) {
            val lineEnd = indexOf(data, crlf, position)
            require(lineEnd >= 0) { "Malformed chunked response" }
            val sizeText = String(data, position, lineEnd - position, Charsets.ISO_8859_1)
                .substringBefore(';')
                .trim()
            val chunkSize = sizeText.toIntOrNull(16) ?: error("Malformed chunk size")
            position = lineEnd + 2

            if (chunkSize == 0) break
            require(position + chunkSize <= data.size) { "Truncated chunked response" }
            require(out.size() + chunkSize <= maxBytes) { "HTTPS response exceeds $maxBytes bytes" }

            out.write(data, position, chunkSize)
            position += chunkSize

            require(position + 1 < data.size && data[position] == 13.toByte() && data[position + 1] == 10.toByte()) {
                "Malformed chunk terminator"
            }
            position += 2
        }

        return out.toByteArray()
    }

    private fun readFully(input: BufferedInputStream, buffer: ByteArray) {
        var position = 0
        while (position < buffer.size) {
            val read = input.read(buffer, position, buffer.size - position)
            require(read > 0) { "Unexpected EOF" }
            position += read
        }
    }

    private fun skip(input: BufferedInputStream, count: Int) {
        var left = count
        val buffer = ByteArray(32)
        while (left > 0) {
            val read = input.read(buffer, 0, min(left, buffer.size))
            require(read > 0) { "Unexpected EOF" }
            left -= read
        }
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray, start: Int): Int {
        if (needle.isEmpty()) return start.coerceAtMost(haystack.size)
        val last = haystack.size - needle.size
        if (last < start) return -1

        outer@ for (i in start..last) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }
}
