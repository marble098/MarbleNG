package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * MARBLE_IRAN_AWARE_PING_L0 — Layer 0 of the Iran-Aware Multi-Signal probing architecture.
 *
 * ## Why the old `tcpOnce` was structurally wrong for a state-driven firewall
 *
 * A three-way TCP handshake only proves that a port is *open*. A modern national firewall
 * (Iran, Turkey, Russia, …) very often accepts the full handshake and then kills the stream
 * **mid-flight** with an injected RST or a silent timeout once the first payload (the TLS
 * ClientHello) is on the wire. The old probe read "connect succeeded" as "the server is
 * healthy", so a fully filtered endpoint was reported as reachable and entered the first
 * ranking pass at full trust.
 *
 * ## The three-signal rule
 *
 * "Reachable" is no longer a Boolean produced by one SYN-ACK. The signal is only verified
 * when **all three** are true:
 *
 *  1. TCP connect completed;
 *  2. a real TLS ClientHello was sent **and** a ServerHello / Alert / certificate message
 *     came back (the far side answered at the transport layer, not only the SYN);
 *  3. the exchange did not complete suspiciously fast (time-to-RST below half the reference
 *     RTT is the classic injection signature, not a real server rejection).
 *
 * Every intermediate outcome is retained as a first-class flag ([ReachabilitySignal]) and
 * persisted by [MarbleIntelligence], so ranking can weight "endpoint answered" differently
 * from "endpoint survived a payload".
 *
 * ## Anti-probing stagger
 *
 * One host never receives five simultaneous handshakes any more. Every probe to the same
 * endpoint is armed with a random 50–400 ms delay ([ProbeTargetPool.nextJitterMs]) so a
 * parallel burst does not look like the deterministic "VPN probing" pattern that adaptive
 * DPI systems throttle on sight.
 */
object MultiVectorReachability {

    /** How one endpoint attempt ended, at the furthest layer we could reach. */
    enum class Verdict {
        /** TCP + TLS ServerHello/Alert verified. The only verdict that may seed ranking. */
        REACHABLE,

        /** TCP connected but the TLS exchange was torn down by a fast, injection-like RST. */
        INJECTED_RESET,

        /** TCP connected, no data answered, socket budget expired. */
        SILENT_TIMEOUT,

        /** The far side answered explicitly that it does not want this stream. */
        REFUSED,

        /** No listener / no route at the address level. */
        UNREACHABLE,

        /** Blank host, bad port or no resolvable address. */
        INVALID
    }

    /**
     * The rich Layer-0 result. This is the object that replaces `tcpOnce`'s `Double` verdict:
     * the number that used to be the whole result is now only one of its fields.
     */
    data class ReachabilitySignal(
        /** True when the TCP three-way handshake completed. */
        val tcpConnected: Boolean = false,
        /** True when a real TLS ClientHello produced a ServerHello/Alert (far side answered). */
        val tlsHandshakeCompleted: Boolean = false,
        /**
         * End-to-end time to the first verified application-layer byte of the probe exchange
         * (TLS ServerHello on the verified path). Null when [Verdict] is not REACHABLE.
         */
        val timeToFirstByteMs: Double? = null,
        /** RST arrived well before the reference round trip suggests it physically could. */
        val injectedResetSuspected: Boolean = false,
        /** Connection stayed open but nothing was answered before the budget expired. */
        val silentTimeoutSuspected: Boolean = false,
        /** The exact probe destination used (host:port or probe-target kind). */
        val probeTargetUsed: String = "",
        /** Measured RTT divided by the current network baseline; 1.0 = exactly the baseline. */
        val referenceRttDeviationRatio: Double = 0.0,
        /** Milliseconds the full attempt took; 0 when nothing could even be attempted. */
        val elapsedMs: Double = 0.0,
        val verdict: Verdict = Verdict.INVALID,
        val failureReason: String = "",
        val atMs: Long = System.currentTimeMillis()
    ) {
        val verified: Boolean
            get() = verdict == Verdict.REACHABLE

        /** Layer-0 shorthand: only a verified signal may stand for "reachable". */
        val reachable: Boolean
            get() = tcpConnected && tlsHandshakeCompleted && !injectedResetSuspected
    }

    /** Aggregate for a multi-sample Layer-0 run. */
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

    /**
     * One triple-signal attempt against a single endpoint.
     *
     * @param referenceRttMs the current network baseline in milliseconds. When 0 (unmeasured),
     *   the injection heuristic degrades to "RST before any ClientHello could plausibly have
     *   been answered" (still an injection signature) instead of being skipped.
     */
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
        val candidates = resolved
            ?: AddressFamilyPolicy.resolveCandidates(
                host,
                plan,
                (budgetMs / 2).coerceIn(500, 2_500)
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
            // A definitive answer ends the address walk: verified, injected, or an explicit
            // remote refusal. Silence on one address (e.g. a blackholed AAAA) must not stop the
            // next family from being tried.
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

    /**
     * The three signals against one resolved address, on one socket.
     *
     * The attempt runs in a single wall-clock budget so the timing evidence (time-to-RST vs
     * reference RTT) is measured against the same calendar as the connect attempt itself.
     */
    private fun attempt(
        address: InetAddress,
        port: Int,
        timeoutMs: Int,
        referenceRttMs: Double,
        displayHost: String,
        probeTarget: String
    ): ReachabilitySignal {
        val tcp = Socket()
        var ssl: SSLSocket? = null
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
                ssl = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                    .createSocket(tcp, displayHost, port, true) as SSLSocket
                val params = ssl.sslParameters
                params.endpointIdentificationAlgorithm = "HTTPS"
                ssl.sslParameters = params
                ssl.soTimeout = timeoutMs
                ssl.startHandshake()
                tlsCompleted = true
                ttfb = (System.nanoTime() - handshakeStartNs) / 1e6
                verdict = Verdict.REACHABLE
            } catch (timeout: SocketTimeoutException) {
                silent = true
                verdict = Verdict.SILENT_TIMEOUT
                failure = "tls-silent-timeout"
            } catch (e: Throwable) {
                val elapsedToFailure = (System.nanoTime() - handshakeStartNs) / 1e6
                val msg = (e.message ?: "").lowercase()
                val resetLike = e is SocketException &&
                    (msg.contains("reset") || msg.contains("connection aborted")) ||
                    e is SSLException && msg.contains("reset")
                if (resetLike) {
                    // Time-to-RST rule: a reset that lands faster than roughly half a genuine
                    // round trip cannot be the far side finishing its network work — it is the
                    // fingerprint of an in-path resetter. With no baseline we still flag it,
                    // because a TLS ClientHello almost never gets an RST this fast from a
                    // listening endpoint.
                    val rttRef = referenceRttMs.takeIf { it in 1.0..10_000.0 } ?: 300.0
                    injected = elapsedToFailure < rttRef * 0.5
                    verdict = if (injected) Verdict.INJECTED_RESET else Verdict.REFUSED
                    failure = if (injected) "injected-reset-time-${(elapsedToFailure * 10).toInt()}ms" else "remote-reset"
                } else if (e is SSLHandshakeException) {
                    // The far side answered with a certificate/alert — that is a response, not
                    // a filter: handshake mismatch is REFUSED, never injected.
                    verdict = Verdict.REFUSED
                    failure = "tls-rejected"
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
            runCatching { ssl?.close() }
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

    /**
     * Multi-sample Layer 0 run with anti-probing stagger between samples, mirroring the same
     * discipline as [RouteProbe.tcpExtended] but retaining every rich signal instead of only
     * the latency median.
     */
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
        repeat(rounds) { index ->
            if (index > 0) ProbeTargetPool.staggerProbe()
            // One resolution per run: re-resolving per sample measured the resolver, not the route.
            val signal = probe(host, port, timeoutMs, settings, referenceRttMs, resolved)
            signals += signal
            // Two consecutive signal-less failures prove silence; nothing more can be learned.
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

/**
 * MARBLE_IRAN_AWARE_PING_L0_TARGETS — the rotating probe target pool.
 *
 * ## What this replaces
 *
 * Every measurement used to race the same four hardcoded origins (`www.gstatic.com`,
 * `cp.cloudflare.com`, `www.google.com`, `connectivitycheck.gstatic.com`). In a country that
 * filters per-SNI and per-hostname, those are exactly the names that carry DPI throttling or
 * injected resets during peak windows, so "the route is slow" was really "the firewall is
 * fighting *.google.com". The pool is the fix:
 *
 *  - it holds **large CDN + FOSS-infrastructure hosts** (which are so widely used that a
 *    complete block is rare), plus **raw destination IPs** of the proxy itself and the
 *    provider's literal anycast ends;
 *  - the order **rotates** every audit cycle and per run, so an adaptive filter cannot learn
 *    a stable five-probe signature from us;
 *  - `google.com` / `cloudflare.com` are *members*, never the *reference RTT*: the baseline
 *    used for the time-to-RST heuristic comes from the measured network itself.
 *
 * All rotation logic is pure and deterministic for a given [seed] + 10-minute epoch, which is
 * what makes the unit tests reproducible while the live sequence still changes over time.
 */
object ProbeTargetPool {

    /** One pool entry: SOCKS destination + TLS name + path, mirroring the literal/domain split. */
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

    /**
     * Large CDN / infrastructure hosts, deliberately provider-diverse and *not* limited to
     * Google + Cloudflare. These are measured through the tunnel with SOCKS ATYP=domain; the
     * Android resolver never sees them.
     */
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

    /** Literal-IP ends of the providers themselves, so a probe needs no DNS at all. */
    val LITERAL_TARGETS: List<ProbeTarget> = listOf(
        ProbeTarget("1.1.1.1", "cloudflare-dns.com", "/cdn-cgi/trace", "literals"),
        ProbeTarget("1.0.0.1", "cloudflare-dns.com", "/cdn-cgi/trace", "literals"),
        ProbeTarget("8.8.8.8", "dns.google", "/dns-query", "literals"),
        ProbeTarget("9.9.9.9", "dns.quad9.net", "/dns-query", "literals"),
        ProbeTarget("94.140.14.14", "dns.adguard-dns.com", "/dns-query", "literals")
    )

    /** Deterministic rotation: position shift from seed hash + 10-minute epoch. */
    fun ordered(
        pool: List<ProbeTarget> = CDN_TARGETS,
        seed: String = ""
    ): List<ProbeTarget> {
        val epochMinutes = (System.currentTimeMillis() / EPOCH_MS).toInt()
        val shift = Math.floorMod(seed.hashCode() xor (epochMinutes * 2_654_435_761), pool.size.coerceAtLeast(1))
        if (pool.size <= 1) return pool
        return pool.mapIndexed { index, target -> (index + shift) % pool.size }
            .map { pool[it] }
    }

    /** Next rotated target for a single probe (thread-safe, used by one-shot callers). */
    fun nextTarget(): ProbeTarget {
        val pool = CDN_TARGETS
        return pool[Math.floorMod(cursor.getAndIncrement(), pool.size)]
    }

    fun nextLiteralTarget(): ProbeTarget {
        val pool = LITERAL_TARGETS
        return pool[Math.floorMod(cursor.getAndIncrement(), pool.size)]
    }

    /** Random 50–400 ms anti-probing jitter. */
    fun nextJitterMs(): Long =
        MIN_JITTER_MS + ThreadLocalRandom.current().nextLong(MAX_JITTER_MS - MIN_JITTER_MS + 1)

    /** Sleep the anti-probing jitter; returns the actual delay, 0 on interrupt. */
    fun staggerProbe(): Long {
        val delay = nextJitterMs()
        return try {
            Thread.sleep(delay)
            delay
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            0L
        }
    }

    /** TLS name for a literal IP entry of [ProbeTargetPool.LITERAL_TARGETS]. */
    fun tlsHostFor(host: String): String =
        LITERAL_TARGETS.firstOrNull { it.host == host }?.tlsHost ?: host

    /** Label used in diagnostics; keeps log lines stable between pool reorders. */
    fun labelOf(target: ProbeTarget): String = "${target.kind}:${target.host}"
}
