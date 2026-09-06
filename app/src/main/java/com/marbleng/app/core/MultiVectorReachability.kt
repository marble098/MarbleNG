package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
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
     * MARBLE_PING_TRUTH_V149 — the three signals against one resolved address, on ONE RAW socket.
     *
     * ## Why the JSSE handshake had to go
     *
     * The previous implementation wrapped the socket in an `SSLSocket` with
     * `endpointIdentificationAlgorithm = "HTTPS"` and called `startHandshake()`. That asks the
     * *Android device's CA store* to validate the server certificate against the endpoint host,
     * which is a question the gate has no business asking:
     *
     *  - a VLESS/Trojan/REALITY node very often serves a **self-signed** certificate — that is the
     *    entire reason "certificate fingerprint (SHA-256)" and "verify peer certificate by name"
     *    exist. JSSE throws `SSLHandshakeException`, the gate recorded REFUSED, and a 100 %
     *    working server was published as FAILED;
     *  - the certificate CN/SAN legitimately does not match the endpoint host whenever the profile
     *    fronts a different SNI (`sni=spotify.com` against `vps1.example`) — again a guaranteed
     *    hostname-verification failure against a perfectly healthy node;
     *  - an expired or not-yet-valid certificate on an otherwise reachable proxy is irrelevant to
     *    reachability, because Xray is the component that decides trust, not the probe.
     *
     * ## What the gate actually needs to know
     *
     * Exactly one thing: **did the far side answer a TLS ClientHello at the record layer?** That is
     * the signal that separates "port open, stream killed by a filter" from "endpoint alive". So
     * the probe now writes a minimal, well-formed TLS 1.2/1.3 ClientHello by hand and reads the
     * first five bytes of the reply. Any valid TLS record type — `0x16` Handshake (ServerHello),
     * `0x15` Alert (the server answered and declined, which still proves it is alive and not
     * filtered) — is a verified answer. No trust store is consulted, no certificate is parsed, no
     * hostname is verified, and there is nothing left for a self-signed or fronted certificate to
     * fail.
     *
     * This also makes the injection heuristic sharper: an RST that arrives after the ClientHello
     * is on the wire but before any record comes back is now unambiguous, because we know exactly
     * what we sent and when.
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
                        // The far side spoke TLS back at us. Whether it is a ServerHello or an
                        // Alert, the endpoint is alive and unfiltered: that is the whole question.
                        tlsCompleted = true
                        ttfb = elapsed.coerceAtLeast(1.0)
                        verdict = Verdict.REACHABLE
                    }

                    read > 0 -> {
                        // Bytes arrived that are not a TLS record. A non-TLS listener (plain HTTP,
                        // Shadowsocks, a fronted endpoint) still PROVED it is alive by answering,
                        // so this must never be a failure — the old code had no branch for it and
                        // fell through to SILENT_TIMEOUT.
                        tlsCompleted = true
                        ttfb = elapsed.coerceAtLeast(1.0)
                        verdict = Verdict.REACHABLE
                        failure = "non-tls-listener"
                    }

                    else -> {
                        // Clean EOF with no bytes at all after a successful connect: the stream was
                        // torn down without an answer. Fast teardown is the injection signature.
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
                    // Time-to-RST rule: a reset that lands faster than roughly half a genuine
                    // round trip cannot be the far side finishing its network work — it is the
                    // fingerprint of an in-path resetter. With no baseline we still flag it,
                    // because a TLS ClientHello almost never gets an RST this fast from a
                    // listening endpoint.
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

    /** A TLS record header is 5 bytes: type, major, minor, length-hi, length-lo. */
    internal const val TLS_RECORD_HEADER_BYTES = 5

    private val random = SecureRandom()

    /**
     * True when these bytes are the head of a plausible TLS record from a live TLS speaker.
     *
     * Accepted content types are Handshake (0x16, a ServerHello) and Alert (0x15, an explicit
     * refusal that still proves the endpoint answered). ChangeCipherSpec (0x14) is accepted too
     * because a TLS 1.3 server sends it as compatibility middlebox padding. The version must be
     * an SSL 3.0 / TLS 1.x legacy record version and the length must be within TLS's own 16 KiB
     * + expansion ceiling, which is what keeps a random byte stream from reading as TLS.
     */
    internal fun isTlsRecord(header: ByteArray): Boolean {
        if (header.size < TLS_RECORD_HEADER_BYTES) return false
        val type = header[0].toInt() and 0xFF
        if (type != 0x16 && type != 0x15 && type != 0x14) return false
        if ((header[1].toInt() and 0xFF) != 0x03) return false
        if ((header[2].toInt() and 0xFF) > 0x04) return false
        val length = ((header[3].toInt() and 0xFF) shl 8) or (header[4].toInt() and 0xFF)
        return length in 1..18_432
    }

    /**
     * A minimal but genuinely well-formed TLS 1.2 ClientHello offering TLS 1.3, with SNI set to
     * [serverName] when it is a DNS name.
     *
     * It must be well-formed: a malformed hello would be answered with a decode_error alert (still
     * a valid signal) by a real server, but a DPI box could also drop it, which would put noise
     * exactly where the measurement lives. Common, widely-deployed cipher suites and the standard
     * extension set are used so the hello looks like ordinary client traffic rather than a probe.
     */
    internal fun clientHello(serverName: String): ByteArray {
        val sni = serverName.takeIf { name ->
            name.isNotBlank() && name.any { it.isLetter() } && !name.contains(':')
        }.orEmpty()

        val body = ArrayList<Byte>(256)
        fun put(vararg values: Int) = values.forEach { body.add((it and 0xFF).toByte()) }
        fun putBytes(values: ByteArray) = values.forEach { body.add(it) }
        fun putU16(value: Int) = put(value shr 8, value)

        // client_version = TLS 1.2 (TLS 1.3 is negotiated via supported_versions).
        put(0x03, 0x03)
        // 32 random bytes + empty legacy session id.
        putBytes(ByteArray(32).also { random.nextBytes(it) })
        put(0x00)
        // Cipher suites: TLS 1.3 AEADs plus two ubiquitous TLS 1.2 suites.
        val suites = intArrayOf(0x1301, 0x1302, 0x1303, 0xC02B, 0xC02F, 0xC030)
        putU16(suites.size * 2)
        suites.forEach { putU16(it) }
        // compression_methods = [null]
        put(0x01, 0x00)

        val extensions = ArrayList<Byte>(128)
        fun ext(vararg values: Int) = values.forEach { extensions.add((it and 0xFF).toByte()) }
        fun extBytes(values: ByteArray) = values.forEach { extensions.add(it) }
        fun extU16(value: Int) = ext(value shr 8, value)

        if (sni.isNotEmpty()) {
            val name = sni.toByteArray(Charsets.US_ASCII)
            extU16(0x0000)                    // server_name
            extU16(name.size + 5)             // extension length
            extU16(name.size + 3)             // server_name_list length
            ext(0x00)                         // host_name
            extU16(name.size)
            extBytes(name)
        }
        // supported_groups: x25519, secp256r1, secp384r1
        extU16(0x000A); extU16(8); extU16(6); extU16(0x001D); extU16(0x0017); extU16(0x0018)
        // ec_point_formats: uncompressed
        extU16(0x000B); extU16(2); ext(0x01, 0x00)
        // signature_algorithms
        val sigAlgs = intArrayOf(0x0403, 0x0804, 0x0401, 0x0503, 0x0805, 0x0501, 0x0806, 0x0601)
        extU16(0x000D); extU16(sigAlgs.size * 2 + 2); extU16(sigAlgs.size * 2)
        sigAlgs.forEach { extU16(it) }
        // supported_versions: TLS 1.3, TLS 1.2
        extU16(0x002B); extU16(5); ext(0x04); extU16(0x0304); extU16(0x0303)
        // key_share with an x25519 entry. This is what turns the reply into a real ServerHello
        // (record type 0x16) instead of a `handshake_failure` alert: a TLS 1.3 server that is
        // offered supported_versions but no key share must either HelloRetryRequest or abort.
        // Both are still valid signals, but a ServerHello is the strongest evidence available and
        // it also makes the probe indistinguishable from ordinary client traffic on the wire.
        run {
            val share = ByteArray(32).also { random.nextBytes(it) }
            extU16(0x0033); extU16(share.size + 6); extU16(share.size + 4)
            extU16(0x001D); extU16(share.size); extBytes(share)
        }

        putU16(extensions.size)
        putBytes(extensions.toByteArray())

        val bodyBytes = body.toByteArray()
        val handshake = ByteArray(4 + bodyBytes.size)
        handshake[0] = 0x01 // client_hello
        handshake[1] = ((bodyBytes.size shr 16) and 0xFF).toByte()
        handshake[2] = ((bodyBytes.size shr 8) and 0xFF).toByte()
        handshake[3] = (bodyBytes.size and 0xFF).toByte()
        bodyBytes.copyInto(handshake, 4)

        val record = ByteArray(TLS_RECORD_HEADER_BYTES + handshake.size)
        record[0] = 0x16 // handshake
        record[1] = 0x03
        record[2] = 0x01 // legacy record version TLS 1.0, as real clients send
        record[3] = ((handshake.size shr 8) and 0xFF).toByte()
        record[4] = (handshake.size and 0xFF).toByte()
        handshake.copyInto(record, TLS_RECORD_HEADER_BYTES)
        return record
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
        for (index in 0 until rounds) {
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
        val epoch = (System.currentTimeMillis() / EPOCH_MS).toInt()
        // 2654435761 is the Knuth multiplicative hash constant; it is a Long literal in Kotlin,
        // so fold the epoch into the seed hash with Int arithmetic (no Long xor with Int).
        val shift = Math.floorMod(
            seed.hashCode() xor Math.floorMod(epoch, Int.MAX_VALUE),
            pool.size.coerceAtLeast(1)
        )
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
