package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.pingSpeedFactor
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicInteger

object MultiVectorReachability {
    enum class Verdict {
        REACHABLE,

        INJECTED_RESET,

        SILENT_TIMEOUT,

        REFUSED,

        UNREACHABLE,

        INVALID
    }

    data class ReachabilitySignal(
        val tcpConnected: Boolean = false,
        val tlsHandshakeCompleted: Boolean = false,
        val timeToFirstByteMs: Double? = null,
        val injectedResetSuspected: Boolean = false,
        val silentTimeoutSuspected: Boolean = false,
        val probeTargetUsed: String = "",
        val referenceRttDeviationRatio: Double = 0.0,
        val elapsedMs: Double = 0.0,
        val verdict: Verdict = Verdict.INVALID,
        val failureReason: String = "",
        val atMs: Long = System.currentTimeMillis()
    ) {
        val verified: Boolean
            get() = verdict == Verdict.REACHABLE

        val reachable: Boolean
            get() = tcpConnected && tlsHandshakeCompleted && !injectedResetSuspected
    }

    data class ReachabilitySummary(
        val signals: List<ReachabilitySignal>,
        val reachableSamples: Int,
        val injectedResetCount: Int,
        val silentTimeoutCount: Int,
        val medianTtfbMs: Double,
        val injectedResetRatePercent: Double
    ) {
        val verdict: Verdict
            get() {
                if (signals.isEmpty()) return Verdict.INVALID
                if (injectedResetCount >= reachableSamples && injectedResetCount > 0) return Verdict.INJECTED_RESET
                if (reachableSamples > 0) return Verdict.REACHABLE
                if (silentTimeoutCount > 0) return Verdict.SILENT_TIMEOUT
                if (signals.any { it.verdict == Verdict.REFUSED }) return Verdict.REFUSED
                return Verdict.UNREACHABLE
            }
    }

    fun probe(
        host: String,
        port: Int,
        timeoutMs: Int,
        settings: AppSettings = AppSettings(),
        referenceRttMs: Double = 0.0,
        resolved: List<InetAddress>? = null
    ): ReachabilitySignal {
        if (host.isBlank() || port !in 1..65535) {
            return ReachabilitySignal(probeTargetUsed = host, verdict = Verdict.INVALID, failureReason = "invalid-target")
        }
        val budgetMs = timeoutMs.coerceIn(250, 30_000)
        val plan = AddressFamilyPolicy.plan(settings = settings)
        val candidates = AddressFamilyPolicy.orderAddresses(
            resolved ?: AddressFamilyPolicy.resolveCandidates(
                host,
                plan,
                (budgetMs / 2).coerceIn(500, 2_500)
            ), plan
        )
        if (candidates.isEmpty()) {
            return ReachabilitySignal(
                probeTargetUsed = host,
                verdict = Verdict.INVALID,
                failureReason = "dns-failed"
            )
        }

        val loopStartNs = System.nanoTime()
        val perAddressMs = (budgetMs / candidates.size).coerceIn(300, budgetMs)
        candidates.forEachIndexed { index, address ->
            val spent = (System.nanoTime() - loopStartNs) / 1_000_000.0
            val remaining = budgetMs - spent
            if (remaining <= 0) return@forEachIndexed
            val attemptMs = when {
                candidates.size == 1 -> remaining.toInt()
                index == candidates.lastIndex -> maxOf(remaining.toInt(), 300)
                else -> perAddressMs.coerceAtMost(maxOf(remaining.toInt(), 300))
            }
            val outcome = attempt(address, port, attemptMs, referenceRttMs, host, "$host:$port")

            if (outcome.verdict == Verdict.REACHABLE ||
                outcome.verdict == Verdict.INJECTED_RESET ||
                outcome.verdict == Verdict.REFUSED
            ) {
                return outcome
            }
        }
        return ReachabilitySignal(
            probeTargetUsed = "$host:$port",
            verdict = Verdict.UNREACHABLE,
            failureReason = "all-addresses-failed"
        )
    }

    private fun attempt(
        address: InetAddress,
        port: Int,
        timeoutMs: Int,
        referenceRttMs: Double,
        displayHost: String,
        probeTarget: String
    ): ReachabilitySignal {
        val tcp = Socket()
        val startNs = System.nanoTime()
        var tcpConnected = false
        var injected = false
        var silent = false
        var tlsCompleted = false
        var ttfb: Double? = null
        var failure = ""
        var verdict = Verdict.UNREACHABLE
        try {
            tcp.tcpNoDelay = true
            tcp.soTimeout = timeoutMs
            tcp.connect(InetSocketAddress(address, port), timeoutMs)
            tcpConnected = true

            val handshakeStartNs = System.nanoTime()
            try {
                val hello = clientHello(displayHost)
                val out = tcp.getOutputStream()
                out.write(hello)
                out.flush()

                val header = ByteArray(TLS_RECORD_HEADER_BYTES)
                var read = 0
                val input = tcp.getInputStream()
                while (read < header.size) {
                    val n = input.read(header, read, header.size - read)
                    if (n < 0) break
                    read += n
                }

                val elapsed = (System.nanoTime() - handshakeStartNs) / 1e6
                when {
                    read >= TLS_RECORD_HEADER_BYTES && isTlsRecord(header) -> {
                        tlsCompleted = true
                        ttfb = elapsed.coerceAtLeast(1.0)
                        verdict = Verdict.REACHABLE
                    }

                    read > 0 -> {
                        tlsCompleted = true
                        ttfb = elapsed.coerceAtLeast(1.0)
                        verdict = Verdict.REACHABLE
                        failure = "non-tls-listener"
                    }

                    else -> {
                        val rttRef = referenceRttMs.takeIf { it in 1.0..10_000.0 } ?: 300.0
                        injected = elapsed < rttRef * 0.5
                        verdict = if (injected) Verdict.INJECTED_RESET else Verdict.SILENT_TIMEOUT
                        silent = !injected
                        failure = if (injected) "injected-eof" else "no-tls-answer"
                    }
                }
            } catch (timeout: SocketTimeoutException) {
                silent = true
                verdict = Verdict.SILENT_TIMEOUT
                failure = "tls-silent-timeout"
            } catch (e: Throwable) {
                val elapsedToFailure = (System.nanoTime() - handshakeStartNs) / 1e6
                val msg = (e.message ?: "").lowercase()
                val resetLike = msg.contains("reset") ||
                    msg.contains("connection aborted") ||
                    msg.contains("broken pipe") ||
                    e is SocketException
                if (resetLike) {
                    val rttRef = referenceRttMs.takeIf { it in 1.0..10_000.0 } ?: 300.0
                    injected = elapsedToFailure < rttRef * 0.5
                    verdict = if (injected) Verdict.INJECTED_RESET else Verdict.REFUSED
                    failure = if (injected) "injected-reset-time-${elapsedToFailure.toInt()}ms" else "remote-reset"
                } else if (e is ConnectException || e is NoRouteToHostException) {
                    verdict = if (!tcpConnected) Verdict.UNREACHABLE else Verdict.REFUSED
                    failure = e::class.java.simpleName.lowercase()
                } else {
                    verdict = if (tcpConnected) Verdict.SILENT_TIMEOUT else Verdict.UNREACHABLE
                    failure = e::class.java.simpleName.lowercase()
                }
            }
        } catch (timeout: SocketTimeoutException) {
            silent = !tcpConnected
            verdict = if (tcpConnected) Verdict.SILENT_TIMEOUT else Verdict.UNREACHABLE
            failure = "connect-timeout"
        } catch (e: Throwable) {
            val msg = (e.message ?: "").lowercase()
            if (msg.contains("reset")) {
                val elapsed = (System.nanoTime() - startNs) / 1e6
                val rttRef = referenceRttMs.takeIf { it in 1.0..10_000.0 } ?: 300.0
                injected = elapsed < rttRef * 0.5
                verdict = Verdict.REFUSED
                failure = "connect-reset"
            } else if (e is ConnectException || e is NoRouteToHostException) {
                verdict = Verdict.UNREACHABLE
                failure = e::class.java.simpleName.lowercase()
            } else {
                verdict = Verdict.UNREACHABLE
                failure = e::class.java.simpleName.lowercase()
            }
        } finally {
            runCatching { tcp.close() }
        }

        val elapsedMs = (System.nanoTime() - startNs) / 1e6
        val measuredRtt = (ttfb ?: elapsedMs).takeIf { it > 0.0 }
        val deviation = if (measuredRtt != null && referenceRttMs in 1.0..10_000.0) {
            measuredRtt / referenceRttMs
        } else {
            0.0
        }
        return ReachabilitySignal(
            tcpConnected = tcpConnected,
            tlsHandshakeCompleted = tlsCompleted,
            timeToFirstByteMs = ttfb,
            injectedResetSuspected = injected || verdict == Verdict.INJECTED_RESET,
            silentTimeoutSuspected = silent,
            probeTargetUsed = probeTarget,
            referenceRttDeviationRatio = deviation,
            elapsedMs = elapsedMs,
            verdict = verdict,
            failureReason = failure,
            atMs = System.currentTimeMillis()
        )
    }

    internal const val TLS_RECORD_HEADER_BYTES = 5

    private val random = SecureRandom()

    internal fun isTlsRecord(header: ByteArray): Boolean {
        if (header.size < TLS_RECORD_HEADER_BYTES) return false
        val type = header[0].toInt() and 0xFF
        if (type != 0x16 && type != 0x15 && type != 0x14) return false
        if ((header[1].toInt() and 0xFF) != 0x03) return false
        if ((header[2].toInt() and 0xFF) > 0x04) return false
        val length = ((header[3].toInt() and 0xFF) shl 8) or (header[4].toInt() and 0xFF)
        return length in 1..18_432
    }

    internal fun clientHello(serverName: String): ByteArray {
        val sni = serverName.takeIf { name ->
            name.isNotBlank() && name.any { it.isLetter() } && !name.contains(':')
        }.orEmpty()

        val body = ArrayList<Byte>(256)
        fun put(vararg values: Int) = values.forEach { body.add((it and 0xFF).toByte()) }
        fun putBytes(values: ByteArray) = values.forEach { body.add(it) }
        fun putU16(value: Int) = put(value shr 8, value)

        put(0x03, 0x03)

        putBytes(ByteArray(32).also { random.nextBytes(it) })
        put(0x00)

        val suites = intArrayOf(0x1301, 0x1302, 0x1303, 0xC02B, 0xC02F, 0xC030)
        putU16(suites.size * 2)
        suites.forEach { putU16(it) }

        put(0x01, 0x00)

        val extensions = ArrayList<Byte>(128)
        fun ext(vararg values: Int) = values.forEach { extensions.add((it and 0xFF).toByte()) }
        fun extBytes(values: ByteArray) = values.forEach { extensions.add(it) }
        fun extU16(value: Int) = ext(value shr 8, value)

        if (sni.isNotEmpty()) {
            val name = sni.toByteArray(Charsets.US_ASCII)
            extU16(0x0000)
            extU16(name.size + 5)
            extU16(name.size + 3)
            ext(0x00)
            extU16(name.size)
            extBytes(name)
        }

        extU16(0x000A); extU16(8); extU16(6); extU16(0x001D); extU16(0x0017); extU16(0x0018)

        extU16(0x000B); extU16(2); ext(0x01, 0x00)

        val sigAlgs = intArrayOf(0x0403, 0x0804, 0x0401, 0x0503, 0x0805, 0x0501, 0x0806, 0x0601)
        extU16(0x000D); extU16(sigAlgs.size * 2 + 2); extU16(sigAlgs.size * 2)
        sigAlgs.forEach { extU16(it) }

        extU16(0x002B); extU16(5); ext(0x04); extU16(0x0304); extU16(0x0303)

        run {
            val share = ByteArray(32).also { random.nextBytes(it) }
            extU16(0x0033); extU16(share.size + 6); extU16(share.size + 4)
            extU16(0x001D); extU16(share.size); extBytes(share)
        }

        putU16(extensions.size)
        putBytes(extensions.toByteArray())

        val bodyBytes = body.toByteArray()
        val handshake = ByteArray(4 + bodyBytes.size)
        handshake[0] = 0x01
        handshake[1] = ((bodyBytes.size shr 16) and 0xFF).toByte()
        handshake[2] = ((bodyBytes.size shr 8) and 0xFF).toByte()
        handshake[3] = (bodyBytes.size and 0xFF).toByte()
        bodyBytes.copyInto(handshake, 4)

        val record = ByteArray(TLS_RECORD_HEADER_BYTES + handshake.size)
        record[0] = 0x16
        record[1] = 0x03
        record[2] = 0x01
        record[3] = ((handshake.size shr 8) and 0xFF).toByte()
        record[4] = (handshake.size and 0xFF).toByte()
        handshake.copyInto(record, TLS_RECORD_HEADER_BYTES)
        return record
    }

    fun probeAll(
        host: String,
        port: Int,
        samples: Int,
        timeoutMs: Int,
        settings: AppSettings = AppSettings(),
        referenceRttMs: Double = 0.0,
        resolved: List<InetAddress>? = null
    ): ReachabilitySummary {
        val rounds = samples.coerceIn(1, 10)
        val signals = ArrayList<ReachabilitySignal>(rounds)

        val staggerScale = settings.pingSpeedFactor()
        for (index in 0 until rounds) {
            if (index > 0) ProbeTargetPool.staggerProbe(staggerScale)

            val signal = probe(host, port, timeoutMs, settings, referenceRttMs, resolved)
            signals += signal

            if (signals.size >= 2 &&
                signals.takeLast(2).all { it.verdict == Verdict.UNREACHABLE || it.verdict == Verdict.INVALID }
            ) {
                break
            }
        }
        val reachable = signals.count { it.verified }
        val injections = signals.count { it.injectedResetSuspected }
        val silent = signals.count { it.silentTimeoutSuspected }
        val ttfb = signals.mapNotNull { it.timeToFirstByteMs }.sorted()
        val median = if (ttfb.isEmpty()) 0.0 else ttfb[ttfb.size / 2]
        return ReachabilitySummary(
            signals = signals,
            reachableSamples = reachable,
            injectedResetCount = injections,
            silentTimeoutCount = silent,
            medianTtfbMs = median,
            injectedResetRatePercent = if (signals.isEmpty()) 0.0
            else injections * 100.0 / signals.size
        )
    }
}

object ProbeTargetPool {
    data class ProbeTarget(
        val host: String,
        val tlsHost: String = host,
        val path: String = "/generate_204",
        val kind: String = "cdn"
    )

    const val MIN_JITTER_MS = 50L
    const val MAX_JITTER_MS = 400L
    private const val EPOCH_MS = 10L * 60L * 1_000L

    private val cursor = AtomicInteger(0)

    val CDN_TARGETS: List<ProbeTarget> = listOf(
        ProbeTarget("cp.cloudflare.com", "cp.cloudflare.com", "/generate_204", "cloudflare"),
        ProbeTarget("www.cloudflare.com", "www.cloudflare.com", "/cdn-cgi/trace", "cloudflare"),
        ProbeTarget("speed.cloudflare.com", "speed.cloudflare.com", "/generate_204", "cloudflare"),
        ProbeTarget("cdn.jsdelivr.net", "cdn.jsdelivr.net", "/gh/marble098/MarbleNG@main/README.md", "jsdelivr"),
        ProbeTarget("raw.githubusercontent.com", "raw.githubusercontent.com", "/marble098/MarbleNG/main/README.md", "github"),
        ProbeTarget("www.gstatic.com", "www.gstatic.com", "/generate_204", "google"),
        ProbeTarget("connectivitycheck.gstatic.com", "connectivitycheck.gstatic.com", "/generate_204", "google"),
        ProbeTarget("dns.google", "dns.google", "/dns-query", "google"),
        ProbeTarget("one.one.one.one", "one.one.one.one", "/cdn-cgi/trace", "cloudflare")
    )

    val LITERAL_TARGETS: List<ProbeTarget> = listOf(
        ProbeTarget("1.1.1.1", "cloudflare-dns.com", "/cdn-cgi/trace", "literals"),
        ProbeTarget("1.0.0.1", "cloudflare-dns.com", "/cdn-cgi/trace", "literals"),
        ProbeTarget("8.8.8.8", "dns.google", "/dns-query", "literals"),
        ProbeTarget("9.9.9.9", "dns.quad9.net", "/dns-query", "literals"),
        ProbeTarget("94.140.14.14", "dns.adguard-dns.com", "/dns-query", "literals")
    )

    fun ordered(
        pool: List<ProbeTarget> = CDN_TARGETS,
        seed: String = ""
    ): List<ProbeTarget> {
        val epoch = (System.currentTimeMillis() / EPOCH_MS).toInt()

        val shift = Math.floorMod(
            seed.hashCode() xor Math.floorMod(epoch, Int.MAX_VALUE),
            pool.size.coerceAtLeast(1)
        )
        if (pool.size <= 1) return pool
        return pool.mapIndexed { index, target -> (index + shift) % pool.size }
            .map { pool[it] }
    }

    fun nextTarget(): ProbeTarget {
        val pool = CDN_TARGETS
        return pool[Math.floorMod(cursor.getAndIncrement(), pool.size)]
    }

    fun nextLiteralTarget(): ProbeTarget {
        val pool = LITERAL_TARGETS
        return pool[Math.floorMod(cursor.getAndIncrement(), pool.size)]
    }

    fun nextJitterMs(scale: Double = 1.0): Long {
        val safe = if (scale.isFinite() && scale > 0.0) scale else 1.0
        val min = (MIN_JITTER_MS / safe).toLong().coerceIn(20L, MAX_JITTER_MS)
        val max = (MAX_JITTER_MS / safe).toLong().coerceIn(min, MAX_JITTER_MS)
        return min + ThreadLocalRandom.current().nextLong(max - min + 1)
    }

    fun staggerProbe(scale: Double = 1.0): Long {
        val delay = nextJitterMs(scale)
        return try {
            Thread.sleep(delay)
            delay
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            0L
        }
    }

    fun tlsHostFor(host: String): String =
        LITERAL_TARGETS.firstOrNull { it.host == host }?.tlsHost ?: host

    fun labelOf(target: ProbeTarget): String = "${target.kind}:${target.host}"
}
