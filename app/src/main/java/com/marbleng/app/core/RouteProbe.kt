package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.DelayTest
import com.marbleng.app.model.PingBudget
import com.marbleng.app.model.ProbeMethod
import com.marbleng.app.model.ProxyProfile
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Endpoint reachability probes — MarbleNG's measurement plane.
 *
 * ## The three product methods
 *
 * MARBLE_PROBE_METHODS_V151 cut the seven-method ladder down to the three measurements that
 * answer different questions. [measureUnified] is the single entry point the app calls, and it
 * dispatches on `ProbeMethod` alone:
 *
 *  - [realDelay] — PattNG's real ping. A cheap TCP gate first, so a dead port never spends a
 *    core, then one real HTTPS round trip through the live tunnel to the configured delay URL.
 *    This is the number a browser would feel, which makes it the honest comparator.
 *
 *  - [tcpPing] — PattNG's tcping. One timed `Socket.connect` to the node's own `host:port`,
 *    with no tunnel involved. Fastest liveness check in the product, and the only method whose
 *    verdict belongs to the endpoint rather than to a route.
 *
 *  - [urlTest] — sing-box extended's native delay endpoint, reached through [urlTestHook].
 *    The running core measures its own live outbound and Marble reports what it measured.
 *
 * ## Measurement primitives
 *
 * The functions below the dispatch ([tcpConnect], [tcpConnectExtended], [tcp], [tcpExtended],
 * [icmp], [icmpExtended], [httpPing], [httpPingBatch], [dnsPing], [dnsPingExtended]) are the
 * instrument set the three methods and the diagnostics are built from, and the ones the ping
 * truth tests pin. They are not user-selectable: nothing in the product reaches them except
 * through [measureUnified].
 *
 *  - [measure] / [measureUnified] — repeat a method and report median latency plus the success
 *    rate; [measureUnified] is the single entry point used by the app.
 */
object RouteProbe {
    // MARBLE_PROBE_TOOLKIT_V130 — full rewrite inspired by v2rayNG, PattNG, Incy, Exclave, Lumen

    const val UNREACHABLE = 99_999.0

    private const val FAST_FAILURE_RETRY_WINDOW_MS = 300L
    private const val FAST_FAILURE_RETRY_DELAY_MS = 120L

    /** Scheduling grace added to the HTTP origin race deadline (see [httpPing]). */
    private const val HTTP_RACE_GRACE_MS = 750L

    /**
     * MARBLE_ICMP_BUDGET_V144 — ping(8) `-W` speaks whole seconds: 1 s is the smallest wait the
     * binary can express, 10 s the largest any probe should ever grant one packet batch.
     */
    internal const val ICMP_MIN_WAIT_SEC = 1
    private const val ICMP_MAX_WAIT_SEC = 10

    /**
     * MARBLE_IRAN_AWARE_PING_L0_TARGETS — the well-known set is GONE.
     *
     * The old list (`gstatic`/`cloudflare`/`google`) was the reference RTT for the whole product,
     * so on a window where the national filter targets those exact SNIs, "the route is slow"
     * really meant "the firewall is fighting *.google.com". Targets now come from the rotating
     * [ProbeTargetPool] (CDN diversity + literal proxy ends) in a deterministic 10-minute order,
     * with parallel probes staggered 50–400 ms so an adaptive filter cannot learn our pattern.
     */
    private fun realDelayTargets(): List<String> {
        val pool = ProbeTargetPool.ordered(ProbeTargetPool.CDN_TARGETS)
        val literals = ProbeTargetPool.ordered(ProbeTargetPool.LITERAL_TARGETS)
        return pool.map { "https://${it.host}${it.path}" } +
            literals.take(2).map { "https://${it.host}${it.path}" }
    }

    /** Lightweight domains for DNS reachability checks. */
    private val DNS_TARGETS = listOf(
        "one.one.one.one",
        "dns.google",
        "dns.cloudflare.com"
    )

    /**
     * MARBLE_PROBE_DETERMINISM_V144 — the blank-host DNS target used to be `DNS_TARGETS.random()`:
     * consecutive sweeps measured different resolvers, so two runs of Ping-all produced numbers
     * that could not be compared and a flaky provider poisoned exactly every third row at random.
     * Targets now rotate deterministically; the sequence is identical on every run and every
     * device, which is also what makes the rotation unit-testable.
     */
    private val dnsTargetCursor = AtomicInteger(0)

    internal fun nextDnsTarget(): String =
        DNS_TARGETS[Math.floorMod(dnsTargetCursor.getAndIncrement(), DNS_TARGETS.size)]

    data class Sample(val successPercent: Int, val latencyMs: Double)

    /**
     * Extended measurement result with rich diagnostics for smart scoring.
     */
    data class ProbeResult(
        val method: String,
        val latencyMs: Double,
        val successPercent: Int,
        val samples: Int = 1,
        val jitterMs: Double = 0.0,
        val minMs: Double = 0.0,
        val maxMs: Double = 0.0,
        val p95Ms: Double = 0.0,
        val lossPercent: Double = 0.0,
        val tcpHandshakeMs: Double = 0.0,
        val tlsHandshakeMs: Double = 0.0,
        val firstByteMs: Double = 0.0,
        val failureReason: String = "",
        // MARBLE_IRAN_AWARE_PING_L0 — Layer 0 flags surface through the result so the caller
        // (ranking / Home / intelligence) can persist and cross-validate them.
        val injectedResetSuspected: Boolean = false,
        val silentTimeoutSuspected: Boolean = false
    )

    // ─── TCP Connect (Layer 0 multi-vector) ────────────────────────────────────

    /**
     * MARBLE_IRAN_AWARE_PING_L0 — the SYN-only `tcpOnce` is REPLACED.
     *
     * A three-way handshake proves the port is open, which is precisely what a stateful national
     * firewall preserves: it accepts the handshake, reads the ClientHello, then injects an RST.
     * The old probe classified such an endpoint "reachable" and fed it to ranking at full trust.
     * The replacement ([MultiVectorReachability.probe]) requires TCP + TLS ServerHello/Alert and
     * applies the time-to-RST < half-baseline rule; only a verified signal is a measurement.
     */
    private fun tcpOnce(
        host: String,
        port: Int,
        timeoutMs: Int,
        plan: IpFamilyPlan,
        resolved: List<InetAddress>? = null
    ): Double {
        // `plan` is no longer consulted here: the Layer-0 engine derives its own
        // [AddressFamilyPolicy.plan] from [settings]. The parameter is kept on the call path
        // so resolveOnce callers continue to share one resolution per run.
        val signal = MultiVectorReachability.probe(
            host = host,
            port = port,
            timeoutMs = timeoutMs,
            settings = AppSettings(),
            referenceRttMs = 0.0,
            resolved = resolved
        )
        return if (signal.verified && signal.timeToFirstByteMs != null) {
            signal.timeToFirstByteMs!!.coerceAtLeast(20.0)
        } else {
            UNREACHABLE
        }
    }

    /**
     * TCP connect time to host:port with retry for transient failures.
     *
     * Uses Happy-Eyeballs address racing: resolves both A and AAAA records and tries
     * them in parallel-ish order, so a dual-stack node is measured over whichever
     * family responds first. A genuine timeout stays failed; only an abnormally fast
     * local/resolver/link failure gets one confirmation retry.
     */
    fun tcp(
        host: String,
        port: Int,
        timeoutMs: Int,
        settings: AppSettings = AppSettings(),
        resolved: List<InetAddress>? = null
    ): Double {
        if (host.isBlank() || port !in 1..65535) return UNREACHABLE

        val plan = AddressFamilyPolicy.plan(settings = settings)
        val firstStarted = System.nanoTime()
        val first = tcpOnce(host, port, timeoutMs, plan, resolved)
        if (first < UNREACHABLE) return first

        val firstFailureElapsedMs =
            ((System.nanoTime() - firstStarted) / 1_000_000L).coerceAtLeast(0L)
        if (firstFailureElapsedMs > FAST_FAILURE_RETRY_WINDOW_MS) {
            return UNREACHABLE
        }

        try {
            Thread.sleep(FAST_FAILURE_RETRY_DELAY_MS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return UNREACHABLE
        }

        return tcpOnce(host, port, timeoutMs, plan, resolved)
    }

    // ─── Raw TCP Connect (TCP_CONNECT) ─────────────────────────────────────────

    /**
     * MARBLE_PING_METHODS_V148 — the raw TCP Connect method.
     *
     * Unlike the verified Layer-0 gate ([reachabilityExtended]), this only proves that the port
     * accepts a TCP three-way handshake. It is the fastest liveness signal available and the one
     * Marble uses when the user explicitly asks for "TCP Connect" — it never demands TLS, so a
     * server that answers on a non-TLS port (plain HTTP, Shadowsocks, a fronted endpoint) is still
     * reported healthy instead of being convicted by a handshake it was never designed to serve.
     *
     * Address family racing is preserved: the host is resolved once and each family is tried in
     * order, so a dual-stack node is measured over whichever family responds first.
     */
    fun tcpConnect(
        host: String,
        port: Int,
        timeoutMs: Int,
        settings: AppSettings = AppSettings(),
        resolved: List<InetAddress>? = null
    ): Double {
        if (host.isBlank() || port !in 1..65535) return UNREACHABLE
        val budgetMs = timeoutMs.coerceIn(250, 30_000)
        val candidates = resolved
            ?: resolveOnce(host, budgetMs, settings)
            ?: return UNREACHABLE
        val deadlineNs = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(budgetMs.toLong())
        candidates.forEach { address ->
            val leftMs = TimeUnit.NANOSECONDS.toMillis(deadlineNs - System.nanoTime())
            if (leftMs <= 0) return UNREACHABLE
            val value = rawTcpConnectOnce(address, port, leftMs.toInt())
            if (value < UNREACHABLE) return value
        }
        return UNREACHABLE
    }

    /** One raw TCP connect attempt against one already-resolved address. */
    private fun rawTcpConnectOnce(address: InetAddress, port: Int, timeoutMs: Int): Double {
        val socket = Socket()
        return try {
            socket.tcpNoDelay = true
            val started = System.nanoTime()
            socket.connect(InetSocketAddress(address, port), timeoutMs)
            ((System.nanoTime() - started) / 1e6).coerceAtLeast(1.0)
        } catch (_: Throwable) {
            UNREACHABLE
        } finally {
            runCatching { socket.close() }
        }
    }

    /**
     * MARBLE_PING_METHODS_V148 — multi-sample raw TCP Connect with the same statistics contract
     * as every other method: median, jitter, p95 and loss, warm-up discarded when >= 3 samples.
     */
    fun tcpConnectExtended(
        host: String,
        port: Int,
        timeoutMs: Int,
        samples: Int = 3,
        settings: AppSettings = AppSettings()
    ): ProbeResult {
        if (host.isBlank() || port !in 1..65535) {
            return ProbeResult("TCP", UNREACHABLE, 0, samples, failureReason = "invalid-target")
        }
        val rounds = PingBudget.samples(samples)
        val resolved = resolveOnce(host, timeoutMs, settings)
            ?: return ProbeResult(
                "TCP", UNREACHABLE, 0, rounds,
                lossPercent = 100.0, failureReason = "dns-failed"
            )
        val times = ArrayList<Double>(rounds)
        var attempts = 0
        var consecutiveFailures = 0
        for (round in 0 until rounds) {
            if (round > 0 && !pauseBetweenSamples()) break
            attempts += 1
            val value = tcpConnect(host, port, timeoutMs, settings, resolved)
            if (value < UNREACHABLE) {
                times += value
                consecutiveFailures = 0
            } else {
                consecutiveFailures += 1
                if (times.isEmpty() && consecutiveFailures >= CONSECUTIVE_FAILURES_BEFORE_ABANDON) break
            }
        }
        if (times.isEmpty()) {
            return ProbeResult(
                "TCP", UNREACHABLE, 0, attempts,
                lossPercent = 100.0, failureReason = "connect-failed"
            )
        }
        return summarize("TCP", times, attempts, warmupDiscarded = true)
    }

    /**
     * MARBLE_PING_ACCURACY_V145 — resolve the endpoint once for a whole multi-sample run.
     *
     * Returns `null` when nothing resolved, which the callers treat as "unreachable before the
     * first packet" instead of paying the resolver cost again for every sample.
     */
    private fun resolveOnce(
        host: String,
        timeoutMs: Int,
        settings: AppSettings
    ): List<InetAddress>? {
        val plan = AddressFamilyPolicy.plan(settings = settings)
        val candidates = AddressFamilyPolicy.resolveCandidates(
            host,
            plan,
            (timeoutMs / 2).coerceIn(500, 2_500)
        )
        return candidates.ifEmpty { null }
    }

    /**
     * MARBLE_PING_ACCURACY_V145 — the statistics of a multi-sample run, in one place.
     *
     * `samples >= 3` discards the first measurement: the first handshake of a run carries cold
     * ARP/NDP entries, a cold conntrack row on the carrier NAT and (for domain nodes) whatever
     * the resolver just did, so keeping it in the median made the published latency depend on
     * how recently the same server had been probed.
     */
    private fun summarize(
        method: String,
        times: List<Double>,
        rounds: Int,
        warmupDiscarded: Boolean
    ): ProbeResult {
        if (times.isEmpty()) {
            return ProbeResult(
                method = method,
                latencyMs = UNREACHABLE,
                successPercent = 0,
                samples = rounds,
                lossPercent = 100.0,
                failureReason = "all-failed"
            )
        }
        val considered = if (warmupDiscarded && times.size >= 3) times.drop(1) else times
        val sorted = considered.sorted()
        val median = sorted[sorted.size / 2]
        val jitter = if (considered.size >= 2) {
            considered.zipWithNext { a, b -> abs(a - b) }.average()
        } else {
            0.0
        }
        val p95Index = ((sorted.size - 1) * 0.95).toInt().coerceIn(0, sorted.lastIndex)
        return ProbeResult(
            method = method,
            latencyMs = median,
            successPercent = times.size * 100 / rounds,
            samples = rounds,
            jitterMs = jitter,
            minMs = sorted.first(),
            maxMs = sorted.last(),
            p95Ms = sorted[p95Index],
            lossPercent = (rounds - times.size).toDouble() * 100.0 / rounds,
            tcpHandshakeMs = if (method == "TCP") median else 0.0
        )
    }

    /** Quiet gap between two samples of the same endpoint; interruption ends the run. */
    private fun pauseBetweenSamples(): Boolean = try {
        Thread.sleep(PingBudget.SAMPLE_SPACING_MS)
        true
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        false
    }

    /**
     * MARBLE_PING_ACCURACY_V145 — stop paying for a target that has already proven it is silent.
     *
     * A median needs samples that exist. Once two consecutive attempts have produced nothing at
     * all, further attempts cannot change the verdict (still unreachable) — they only multiply
     * the honest per-server timeout by the sample count, which is what would turn a sweep over a
     * subscription of dead nodes into minutes of waiting. Any single success disarms this and the
     * full sample budget is spent, so accuracy for reachable servers is untouched. Loss is
     * reported against the attempts actually made, which is the honest denominator.
     */
    private const val CONSECUTIVE_FAILURES_BEFORE_ABANDON = 2

    /**
     * MARBLE_IRAN_AWARE_PING_L0_EX — the multi-vector reachability run in [ProbeResult] shape.
     *
     * This is what replaces `tcpExtended`'s SYN loop. It retains every Layer-0 flag
     * ([ProbeResult.injectedResetSuspected]) so callers can store the signal, and it applies
     * the 50–400 ms anti-probing stagger between samples — the old back-to-back SYNs were
     * exactly the burst signature adaptive DPI learns and throttles.
     */
    fun reachabilityExtended(
        host: String,
        port: Int,
        timeoutMs: Int,
        samples: Int = 3,
        settings: AppSettings = AppSettings(),
        referenceRttMs: Double = 0.0
    ): ProbeResult {
        if (host.isBlank() || port !in 1..65535) {
            return ProbeResult("TCP", UNREACHABLE, 0, samples, failureReason = "invalid-target")
        }
        val resolved = resolveOnce(host, timeoutMs, settings)
            ?: return ProbeResult(
                "TCP",
                UNREACHABLE,
                0,
                PingBudget.samples(samples),
                lossPercent = 100.0,
                failureReason = "dns-failed"
            )
        val summary = MultiVectorReachability.probeAll(
            host = host,
            port = port,
            samples = PingBudget.samples(samples),
            timeoutMs = timeoutMs,
            settings = settings,
            referenceRttMs = referenceRttMs,
            resolved = resolved
        )
        val times = summary.signals.mapNotNull { it.timeToFirstByteMs?.coerceAtLeast(20.0) }
        val flagged = summary.signals.any { it.injectedResetSuspected }
        val silent = summary.signals.any { it.silentTimeoutSuspected }
        if (times.isEmpty()) {
            // MARBLE_PING_TRUTH_V149 — a silent TLS phase is NOT proof the server is dead.
            //
            // The gate answers "did anything come back after a ClientHello". A server whose port
            // completed a TCP handshake but never spoke TLS is either filtered (the case this gate
            // exists to catch) or simply not a TLS speaker on that port — a plain Shadowsocks
            // listener, a raw VLESS endpoint with `security=none`, an obfuscated transport that
            // waits for its own protocol preamble. Convicting the second group is exactly the
            // "shows failed while the server is 100 % working" report: a whole class of healthy
            // nodes was published as dead by a protocol they never agreed to speak.
            //
            // An injected reset stays a hard failure — that is real evidence of interference. For
            // plain silence, fall back to the raw TCP connect and, when the port does answer,
            // report the measured handshake with the reason recorded so ranking can still weight
            // it below a fully verified gate.
            if (!flagged) {
                val rawConnect = tcpConnectExtended(host, port, timeoutMs, samples, settings)
                if (rawConnect.latencyMs < UNREACHABLE) {
                    return rawConnect.copy(
                        method = "TCP",
                        tcpHandshakeMs = rawConnect.latencyMs,
                        silentTimeoutSuspected = silent,
                        failureReason = "tcp-connect-only"
                    )
                }
            }
            return ProbeResult(
                "TCP",
                UNREACHABLE,
                0,
                summary.signals.size,
                lossPercent = 100.0,
                failureReason = when {
                    flagged -> "injected-reset-suspected"
                    silent -> "silent-timeout"
                    else -> "all-failed"
                },
                injectedResetSuspected = flagged,
                silentTimeoutSuspected = silent
            )
        }
        // MARBLE_PING_TRUTH_V147 — the warm-up sample is discarded here too. The same cold
        // ARP/NDP/conntrack cost that [summarize] removes from `measure` is present in the gate:
        // keeping it made Smart report a lower latency the first time a node was touched and a
        // higher one on every later run depending only on probe history, not on the path.
        val summarized = summarize("TCP", times, summary.signals.size.coerceAtLeast(1), warmupDiscarded = true)
        return summarized.copy(
            tlsHandshakeMs = summarized.latencyMs,
            injectedResetSuspected = flagged,
            silentTimeoutSuspected = silent,
            failureReason = if (flagged) "injected-reset-partial" else ""
        )
    }

    /**
     * Extended TCP measurement: multiple samples, statistics, handshake timing.
     *
     * Returns a [ProbeResult] with median, jitter, p95 and loss rate for smart scoring.
     */
    fun tcpExtended(
        host: String,
        port: Int,
        timeoutMs: Int,
        samples: Int = 3,
        settings: AppSettings = AppSettings()
    ): ProbeResult =
        reachabilityExtended(host, port, timeoutMs, samples, settings)

    // ─── ICMP Echo ─────────────────────────────────────────────────────────────

    /**
     * ICMP echo through the system binary. Android cannot open raw sockets without root,
     * so this shells out to /system/bin/ping, which is present on every stock image.
     * Many hosts and most mobile carriers drop ICMP, so a failure here is not proof
     * that a node is dead.
     */
    fun icmp(
        host: String,
        timeoutMs: Int,
        settings: AppSettings = AppSettings()
    ): Double {
        if (host.isBlank()) return UNREACHABLE
        // MARBLE_ICMP_BUDGET_V144 — two honest bounds in one place. (1) The address is resolved
        // under [AddressFamilyPolicy.resolveWithBudget]: the old inline lookup blocked the probe
        // thread for the whole OS resolver timeout before ping(8) even started. (2) ping's `-W`
        // flag only speaks whole seconds, so a sub-second [timeoutMs] CANNOT be honoured by the
        // binary — the 1 s floor below is that hardware truth made explicit, not a silent
        // inflation: callers that need sub-second endpoint answers must use TCP, never ICMP.
        val seconds = (timeoutMs / 1000).coerceIn(ICMP_MIN_WAIT_SEC, ICMP_MAX_WAIT_SEC)
        val target = AddressFamilyPolicy
            .resolveCandidates(
                host,
                AddressFamilyPolicy.plan(settings = settings),
                timeoutMs.coerceIn(500, 5_000)
            )
            .firstOrNull()
            ?.hostAddress
            ?.takeIf { it.isNotBlank() }
            ?: host
        return runCatching {
            val process = ProcessBuilder(
                buildList {
                    add("/system/bin/ping")
                    // MARBLE_PING_TRUTH_V149 — no `-q`: quiet mode hides the per-packet `time=`
                    // line this function's own regex depends on. See [icmpExtended].
                    add("-n"); add("-c"); add("1"); add("-W"); add(seconds.toString())
                    if (target.contains(':')) add("-6")
                    add(target)
                }
            ).redirectErrorStream(true).start()

            val output = try {
                if (!process.waitFor((seconds + 1).toLong(), TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    return@runCatching UNREACHABLE
                }
                process.inputStream.bufferedReader().use { it.readText() }
            } finally {
                runCatching { process.destroy() }
            }

            // MARBLE_PING_TRUTH_V149 — a measured RTT outranks the exit code. Some ping builds
            // exit non-zero even after receiving a reply (e.g. when the trailing statistics write
            // fails); throwing away a real measurement because of that reported healthy hosts as
            // unreachable. The exit code is only decisive when nothing was measured at all.
            val average = Regex("time[=<]\\s*([\\d.]+)").find(output)
                ?.groupValues?.getOrNull(1)
                ?.toDoubleOrNull()
                ?: Regex("=\\s*[\\d.]+/([\\d.]+)/").find(output)
                    ?.groupValues?.getOrNull(1)
                    ?.toDoubleOrNull()

            average?.takeIf { it > 0.0 } ?: UNREACHABLE
        }.getOrDefault(UNREACHABLE)
    }

    /**
     * Extended ICMP: multiple packets, statistics.
     *
     * Sends multiple ICMP echoes and computes median, jitter, p95 and packet loss,
     * giving a much richer signal than a single echo.
     */
    fun icmpExtended(
        host: String,
        timeoutMs: Int,
        count: Int = 3,
        settings: AppSettings = AppSettings()
    ): ProbeResult {
        if (host.isBlank()) {
            return ProbeResult("ICMP", UNREACHABLE, 0, count, failureReason = "blank-host")
        }
        // MARBLE_ICMP_BUDGET_V144 — same two bounds as [icmp]: budgeted resolution, explicit
        // 1 s ping-binary floor. The process wait below is additionally capped by the caller's
        // own per-packet budget times the packet count, so a large `-c` can never smuggle a
        // 30 s wait past a 2 s caller.
        val seconds = (timeoutMs / 1000).coerceIn(ICMP_MIN_WAIT_SEC, ICMP_MAX_WAIT_SEC)
        val packets = PingBudget.samples(count)
        val target = AddressFamilyPolicy
            .resolveCandidates(
                host,
                AddressFamilyPolicy.plan(settings = settings),
                timeoutMs.coerceIn(500, 5_000)
            )
            .firstOrNull()
            ?.hostAddress
            ?.takeIf { it.isNotBlank() }
            ?: host

        return runCatching {
            // MARBLE_PING_TRUTH_V149 — `-q` is REMOVED, and this is the whole ICMP bug.
            //
            // ping(8)'s quiet mode prints ONLY the trailing statistics block; it deliberately
            // suppresses every per-packet "64 bytes from …: icmp_seq=1 ttl=56 time=41.3 ms" line.
            // The parser below extracts RTTs with `time=([\d.]+) ms` — per-packet lines that quiet
            // mode had already thrown away — so `rttValues` came back EMPTY on every single run.
            // The `rttValues.isEmpty()` branch then returned UNREACHABLE with "no-responses",
            // even when the very same output said "0% packet loss". ICMP Ping therefore reported
            // FAILED for 100 % healthy servers, unconditionally, on every device. Dropping `-q`
            // restores the per-packet lines; the summary line is still parsed as a fallback below
            // so a stripped-down busybox ping that omits them is handled too.
            val process = ProcessBuilder(
                buildList {
                    add("/system/bin/ping")
                    add("-n")
                    add("-c"); add(packets.toString())
                    add("-W"); add(seconds.toString())
                    // Interval between packets: 200ms to keep the batch fast
                    add("-i"); add("0.2")
                    if (target.contains(':')) add("-6")
                    add(target)
                }
            ).redirectErrorStream(true).start()

            val output = try {
                val callerCapSec = timeoutMs.toLong() * packets / 1_000 + 5
                val waitSec = (seconds.toLong() * packets / 5 + 3)
                    .coerceAtMost(callerCapSec)
                    .coerceIn(3, 30)
                if (!process.waitFor(waitSec, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    return@runCatching ProbeResult("ICMP", UNREACHABLE, 0, packets, failureReason = "timeout")
                }
                process.inputStream.bufferedReader().use { it.readText() }
            } finally {
                runCatching { process.destroy() }
            }

            // Parse packet loss first: "3 packets transmitted, 3 received, 0% packet loss".
            // MARBLE_PING_TRUTH_V149 — the *statistics* are the verdict, not the exit code. ping
            // exits non-zero for partial loss on some images, and the old guard only forgave that
            // when the output contained "bytes from" — text that `-q` had removed. Loss is read
            // before any early return so a partially-answering host is never thrown away.
            val lossMatch = Regex("(\\d+)%\\s*packet loss").find(output)
            val lossPercent = lossMatch?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: 100.0
            val received = Regex("(\\d+)\\s+(?:packets\\s+)?received").find(output)
                ?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0

            // Parse summary: "rtt min/avg/max/mdev = 41.316/41.316/41.316/0.000 ms"
            val summaryMatch = Regex("=\\s*([\\d.]+)/([\\d.]+)/([\\d.]+)/([\\d.]+)").find(output)

            // Parse individual RTT values: "64 bytes from ...: icmp_seq=1 ttl=56 time=41.3 ms"
            val perPacket = Regex("time[=<]\\s*([\\d.]+)\\s*ms").findAll(output)
                .mapNotNull { it.groupValues[1].toDoubleOrNull() }
                .filter { it > 0.0 }
                .toList()

            // Fallback for a ping build that prints only the summary (or when the caller's own
            // options suppress per-packet lines): min/avg/max ARE real measurements and must be
            // used rather than discarded as "no responses".
            val rttValues = when {
                perPacket.isNotEmpty() -> perPacket
                summaryMatch != null -> listOfNotNull(
                    summaryMatch.groupValues.getOrNull(1)?.toDoubleOrNull(),
                    summaryMatch.groupValues.getOrNull(2)?.toDoubleOrNull(),
                    summaryMatch.groupValues.getOrNull(3)?.toDoubleOrNull()
                ).filter { it > 0.0 }

                else -> emptyList()
            }

            if (rttValues.isEmpty()) {
                val reason = when {
                    received > 0 -> "unparsable-output"
                    lossPercent >= 100.0 -> "no-responses"
                    else -> "exit-${process.exitValue()}"
                }
                return@runCatching ProbeResult(
                    "ICMP", UNREACHABLE, 0, packets,
                    lossPercent = lossPercent, failureReason = reason
                )
            }

            val sorted = rttValues.sorted()
            val median = sorted[sorted.size / 2]
            val jitter = if (rttValues.size >= 2) {
                rttValues.zipWithNext { a, b -> abs(a - b) }.average()
            } else {
                summaryMatch?.groupValues?.getOrNull(4)?.toDoubleOrNull() ?: 0.0
            }
            val p95Index = ((sorted.size - 1) * 0.95).toInt().coerceIn(0, sorted.lastIndex)

            // MARBLE_PING_TRUTH_V149 — real RTTs and a 0 % success rate cannot both be true.
            // `lossPercent` defaults to 100 when the statistics line cannot be parsed, so a build
            // whose summary wording differs published "latency 41 ms, success 0 %" — which every
            // consumer (ranking, the UI badge) reads as FAILED. When measurements exist, the
            // honest denominator is the packets we sent versus the RTTs we actually received.
            val measuredLoss = if (lossMatch != null) {
                lossPercent
            } else {
                ((packets - perPacket.size).coerceAtLeast(0)).toDouble() * 100.0 / packets
            }
            ProbeResult(
                method = "ICMP",
                latencyMs = median,
                successPercent = ((1.0 - measuredLoss / 100.0) * 100).toInt().coerceIn(1, 100),
                samples = packets,
                jitterMs = jitter,
                minMs = sorted.first(),
                maxMs = sorted.last(),
                p95Ms = sorted[p95Index],
                lossPercent = measuredLoss
            )
        }.getOrDefault(ProbeResult("ICMP", UNREACHABLE, 0, packets, failureReason = "exception"))
    }

    // ─── HTTP/HTTPS Ping (Real Delay / Real Test) ──────────────────────────────

    /**
     * HTTPS GET to a well-known 204 endpoint, measuring the full round-trip time
     * including DNS, TCP connect, TLS handshake and first byte. This is the "real
     * delay" method used by v2rayNG's measureOutboundDelay and PattNG's tunnel test.
     *
     * When [socksPort] > 0, the request is routed through a local SOCKS5 proxy
     * (the Xray process), proving the full tunnel route works. When [socksPort] is 0,
     * the request goes directly, measuring the underlay network path.
     *
     * MARBLE_HTTP_RACE_V144 — the origins are raced in parallel and the first successful
     * measurement wins. This prevents a single blocked CDN from classifying an otherwise
     * working node as dead. Critique of the sequential failover this replaces, so it is never
     * rebuilt: the old loop dialled the four 204 origins ONE AFTER ANOTHER, each with a full
     * connect+read timeout, so the worst case for one "ping" was ~8× the configured timeout and
     * the common censored-link case (first two origins filtered, third alive) always paid two
     * full timeouts before measuring anything. Origins are redundant failovers, not samples —
     * racing them changes no measurement semantics (the winner is still one real HTTPS
     * round-trip with its own TLS handshake) and bounds the worst case by ONE timeout instead
     * of four. Sample rounds in [httpPingBatch] deliberately stay sequential: parallel samples
     * would share the radio and destroy the time diversity the jitter math needs.
     *
     * Implementation notes: one daemon thread per origin (at most four, each self-bounded by
     * its own socket timeouts — no pool to saturate, no lifecycle to manage). The first success
     * wins immediately; when every racer reports failure the call returns without waiting out
     * the deadline; on deadline expiry the losers are abandoned to finish bounded on their own.
     */
    fun httpPing(
        socksPort: Int = 0,
        timeoutMs: Int = 5000,
        targets: List<String> = realDelayTargets(),
        httpMethod: String = "GET"
    ): ProbeResult {
        if (targets.isEmpty()) {
            return ProbeResult("HTTP", UNREACHABLE, 0, 1, failureReason = "no-targets")
        }
        val budgetMs = timeoutMs.coerceIn(500, 15_000)
        if (targets.size == 1) return httpPingOnce(targets.first(), socksPort, budgetMs, httpMethod)

        val winner = AtomicReference<ProbeResult>()
        val firstSuccess = CountDownLatch(1)
        val allFinished = CountDownLatch(targets.size)
        targets.forEach { target ->
            Thread({
                try {
                    // MARBLE_IRAN_AWARE_PING_L0 — anti-probing stagger: five simultaneous
                    // ClientHellos from one device are the signature an adaptive filter learns
                    // and then throttles. Each probe waits its own random 50–400 ms delay.
                    val jitter = ProbeTargetPool.staggerProbe()
                    if (jitter > 0L) {
                        // staggerProbe already slept; nothing else to do here.
                    }
                    val result = httpPingOnce(target, socksPort, budgetMs, httpMethod)
                    if (result.latencyMs < UNREACHABLE && winner.compareAndSet(null, result)) {
                        firstSuccess.countDown()
                    }
                } finally {
                    allFinished.countDown()
                }
            }, "marble-http-race").apply { isDaemon = true; start() }
        }

        // One HTTPS round trip costs a connect timeout plus a read timeout; the race as a whole
        // is granted both, plus a small grace for thread scheduling.
        val deadlineNs = System.nanoTime() +
            TimeUnit.MILLISECONDS.toNanos(budgetMs.toLong() * 2 + HTTP_RACE_GRACE_MS)
        while (true) {
            winner.get()?.let { return it }
            if (allFinished.count == 0L) {
                return ProbeResult("HTTP", UNREACHABLE, 0, 1, failureReason = "all-targets-failed")
            }
            val leftMs = TimeUnit.NANOSECONDS.toMillis(deadlineNs - System.nanoTime())
            if (leftMs <= 0) {
                return winner.get()
                    ?: ProbeResult("HTTP", UNREACHABLE, 0, 1, failureReason = "race-timeout")
            }
            runCatching { firstSuccess.await(minOf(leftMs, 100L), TimeUnit.MILLISECONDS) }
        }
    }

    private fun httpPingOnce(
        url: String,
        socksPort: Int,
        timeoutMs: Int,
        httpMethod: String = "GET"
    ): ProbeResult {
        if (socksPort > 0) return httpPingOnceThroughTunnel(url, socksPort, timeoutMs, httpMethod)
        return httpPingOnceDirect(url, timeoutMs, httpMethod)
    }

    /**
     * MARBLE_TUNNEL_PING_DNS_SAFE_V146 — the tunnel measurement must never ask the system
     * resolver for the target hostname.
     *
     * [HttpURLConnection] with a SOCKS proxy resolves the destination host through Android's own
     * resolver *before* opening the proxy connection, so a "real delay" ping through the tunnel
     * still leaked the 204 origin's name to the underlay resolver. On a censored link that poisons
     * or drops Google/Cloudflare names, the tunnel itself was perfectly healthy and the
     * measurement still reported unreachable — convicted by a DNS lookup the tunnel was built to
     * bypass.
     *
     * The rewrite uses [SocksHttpClient.request], which sends ATYP=domain to the SOCKS5 inbound so
     * Xray resolves the name inside the tunnel, and completes certificate-verified TLS against the
     * real hostname. The only name the system resolver ever sees is "127.0.0.1".
     */
    private fun httpPingOnceThroughTunnel(
        url: String,
        socksPort: Int,
        timeoutMs: Int,
        httpMethod: String = "GET"
    ): ProbeResult =
        runCatching {
            val target = URL(url)
            if (!target.protocol.equals("https", ignoreCase = true)) {
                return@runCatching ProbeResult(
                    "HTTP", UNREACHABLE, 0, 1, failureReason = "non-https-target"
                )
            }
            val targetPort = target.port.takeIf { it > 0 } ?: 443
            val path = target.file.takeIf { it.isNotBlank() } ?: "/"
            val probe = SocksHttpClient.request(
                port = socksPort,
                host = target.host,
                targetPort = targetPort,
                method = httpMethod,
                path = path,
                timeoutMs = timeoutMs,
                maxBytes = 4_096,
                headers = mapOf("User-Agent" to "MarbleNG/1.0", "Connection" to "close")
            )
            // MARBLE_PING_TRUTH_V149 — ANY complete HTTP response proves the route works.
            //
            // The old `200..399` gate is a content check masquerading as a reachability check. A
            // 403 from a CDN edge that dislikes the probe's User-Agent, a 404 because the origin
            // moved `/generate_204`, a 429 rate-limit, a 503 from one unhealthy PoP — every one of
            // these is a full round trip through the tunnel: SOCKS negotiated, DNS resolved inside
            // the tunnel, TLS completed, request sent, response parsed. That is exactly what the
            // method claims to measure, and it was being published as FAILED. A status line only
            // fails the probe when it is absent (status <= 0), which means no response at all.
            if (probe.status > 0) {
                val rtt = probe.elapsedMs.coerceAtLeast(1.0)
                ProbeResult(
                    method = "HTTP",
                    latencyMs = rtt,
                    successPercent = 100,
                    samples = 1,
                    firstByteMs = rtt,
                    failureReason = if (probe.status in 200..399) "" else "status-${probe.status}"
                )
            } else {
                ProbeResult("HTTP", UNREACHABLE, 0, 1, failureReason = "no-response")
            }
        }.getOrDefault(ProbeResult("HTTP", UNREACHABLE, 0, 1, failureReason = "exception"))

    /**
     * Direct HTTPS GET to the 204 origin over the underlay — no SOCKS hop, so the system resolver
     * is the right resolver here (the HTTP method is explicitly measuring the underlay path).
     */
    private fun httpPingOnceDirect(
        url: String,
        timeoutMs: Int,
        httpMethod: String = "GET"
    ): ProbeResult {
        return runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = timeoutMs
                connection.readTimeout = timeoutMs
                connection.requestMethod = httpMethod
                connection.instanceFollowRedirects = true
                connection.useCaches = false
                connection.setRequestProperty("User-Agent", "MarbleNG/1.0")
                connection.setRequestProperty("Connection", "close")

                val start = System.nanoTime()
                connection.connect()
                val connectMs = (System.nanoTime() - start) / 1e6

                // MARBLE_PING_TRUTH_V149 — `responseCode` is what actually performs the round
                // trip; it must be read inside its own guard. On a 4xx/5xx, HttpURLConnection
                // throws from `inputStream` and only exposes the body through `errorStream`, and
                // the old unconditional `inputStream.read()` also forced the HEAD method to wait
                // for a body that a HEAD response is defined never to have — the exact reason
                // "HTTP HEAD" timed out against servers that answered instantly.
                val responseCode = runCatching { connection.responseCode }.getOrDefault(-1)
                val firstByteMs = (System.nanoTime() - start) / 1e6

                // Drain one byte only for GET; a HEAD response has no body by definition.
                if (!httpMethod.equals("HEAD", ignoreCase = true)) {
                    runCatching {
                        (if (responseCode in 200..399) connection.inputStream else connection.errorStream)
                            ?.read()
                    }
                }

                if (responseCode > 0) {
                    ProbeResult(
                        method = "HTTP",
                        latencyMs = firstByteMs,
                        successPercent = 100,
                        samples = 1,
                        tcpHandshakeMs = connectMs,
                        firstByteMs = firstByteMs,
                        failureReason = if (responseCode in 200..399) "" else "status-$responseCode"
                    )
                } else {
                    ProbeResult("HTTP", UNREACHABLE, 0, 1, failureReason = "no-response")
                }
            } finally {
                runCatching { connection.disconnect() }
            }
        }.getOrDefault(ProbeResult("HTTP", UNREACHABLE, 0, 1, failureReason = "exception"))
    }

    /**
     * Multiple-sample HTTP ping with full statistics.
     *
     * Fires [samples] sequential HTTPS requests and returns median, jitter, p95
     * and loss rate. This gives the most accurate "real user experience" measurement
     * since it includes TLS negotiation and server response time.
     */
    fun httpPingBatch(
        socksPort: Int = 0,
        timeoutMs: Int = 5000,
        samples: Int = 3,
        targets: List<String> = realDelayTargets(),
        httpMethod: String = "GET"
    ): ProbeResult {
        val rounds = PingBudget.samples(samples)
        val times = ArrayList<Double>(rounds)
        val handshakeTimes = ArrayList<Double>(rounds)
        var consecutiveFailures = 0
        // MARBLE_PING_TRUTH_V149 — the honest denominator is the attempts actually made, exactly
        // as [summarize] already does for every other method. The batch divided successes by the
        // configured `rounds` even when the early-abandon rule stopped the loop after two, so a
        // route that answered 2 of 2 attempts under an 8-sample budget published 25 % success —
        // low enough for the UI badge and the ranker to read it as a failing server.
        var attempts = 0
        for (round in 0 until rounds) {
            // MARBLE_PING_ACCURACY_V145 — spaced samples: a burst of HTTPS requests to the same
            // 204 origin measures connection reuse and server-side rate limiting, not the route.
            if (round > 0 && !pauseBetweenSamples()) break
            attempts += 1
            val result = httpPing(socksPort, timeoutMs, targets, httpMethod)
            if (result.latencyMs < UNREACHABLE) {
                times += result.latencyMs
                if (result.tcpHandshakeMs > 0) handshakeTimes += result.tcpHandshakeMs
                consecutiveFailures = 0
            } else {
                consecutiveFailures += 1
                if (times.isEmpty() && consecutiveFailures >= CONSECUTIVE_FAILURES_BEFORE_ABANDON) break
            }
        }
        if (times.isEmpty()) {
            return ProbeResult(
                "HTTP", UNREACHABLE, 0, attempts.coerceAtLeast(1),
                lossPercent = 100.0, failureReason = "all-failed"
            )
        }
        val denominator = attempts.coerceAtLeast(times.size).coerceAtLeast(1)
        // MARBLE_PING_TRUTH_V147 — the Settings page promises "the warm-up sample is discarded",
        // but the batch kept it in the median. The first HTTPS request of a run carries the same
        // cold resolver/ARP/TLS-session state as a TCP gate, so a 3-sample "median" was really a
        // mean of [warmup + 2 real deltas]. Success and loss rates still count every attempt;
        // only the latency distribution drops the first measured value.
        val considered = if (times.size >= 3) times.drop(1) else times
        val sorted = considered.sorted()
        val median = sorted[sorted.size / 2]
        val jitter = if (considered.size >= 2) {
            considered.zipWithNext { a, b -> abs(a - b) }.average()
        } else 0.0
        val p95Index = ((sorted.size - 1) * 0.95).toInt().coerceIn(0, sorted.lastIndex)
        return ProbeResult(
            method = "HTTP",
            latencyMs = median,
            successPercent = (times.size * 100 / denominator).coerceIn(1, 100),
            samples = denominator,
            jitterMs = jitter,
            minMs = sorted.first(),
            maxMs = sorted.last(),
            p95Ms = sorted[p95Index],
            lossPercent = (denominator - times.size).toDouble() * 100.0 / denominator,
            tcpHandshakeMs = handshakeTimes.average().takeIf { it.isFinite() } ?: 0.0
        )
    }

    // ─── DNS Ping ──────────────────────────────────────────────────────────────

    /**
     * DNS resolution time measurement.
     *
     * Resolves a well-known domain and measures how long the system resolver takes.
     * This is useful as a quick liveness check when ICMP is blocked and TCP connect
     * times are unreliable (carrier-grade NAT, transparent proxies). Inspired by
     * Incy's DNS-based reachability detection.
     *
     * Not a proxy test — only proves the local network's DNS path works.
     *
     * MARBLE_DNS_BUDGET_V144 — critique of the old body, which is why [timeoutMs] was a lie:
     * `InetAddress.getAllByName` is a blocking syscall with NO timeout parameter, and the old
     * code called it inline with the parameter sitting unused next to it. On a dead link one
     * DNS "ping" blocked its worker for the full OS resolver timeout (10–20 s); `dnsPingExtended`
     * repeated that up to 8× SEQUENTIALLY, so a single domain-hosted node could pin a benchmark
     * worker for over a minute while every budget on the settings screen claimed seconds.
     *
     * The rewrite measures the same thing (system-resolver round trip) through
     * [AddressFamilyPolicy.resolveWithBudget]: numeric literals resolve locally with no
     * syscalls, domain names resolve on the shared daemon pool, and the wait never exceeds
     * [timeoutMs] — expiry reports unreachable instead of hanging the batch. [resolver] is an
     * injection seam for unit tests only; production always passes the system resolver.
     */
    fun dnsPing(
        host: String = "",
        timeoutMs: Int = 3000,
        resolver: (String) -> Array<InetAddress> = InetAddress::getAllByName
    ): Double {
        val target = host.ifBlank { nextDnsTarget() }
        if (target.isBlank()) return UNREACHABLE
        val budgetMs = timeoutMs.coerceIn(250, 30_000)
        val start = System.nanoTime()
        val addresses = AddressFamilyPolicy.resolveWithBudget(target, budgetMs, resolver)
        val elapsed = (System.nanoTime() - start) / 1e6
        return if (addresses.isNotEmpty()) elapsed else UNREACHABLE
    }

    /**
     * Extended DNS measurement with multiple samples and statistics.
     */
    fun dnsPingExtended(
        host: String = "",
        timeoutMs: Int = 3000,
        samples: Int = 3
    ): ProbeResult {
        val rounds = PingBudget.samples(samples)
        val budgetMs = timeoutMs.coerceIn(250, 30_000)
        // MARBLE_DNS_BUDGET_V144 — each round is individually bounded by [dnsPing], and the whole
        // batch additionally never outlives rounds × budget plus scheduling grace, so a caller
        // that asked for "8 samples, 10 s each" still gets a predictable wall clock.
        val deadlineNs = System.nanoTime() +
            TimeUnit.MILLISECONDS.toNanos(budgetMs.toLong() * rounds + 1_000L)
        val times = ArrayList<Double>(rounds)
        repeat(rounds) {
            val leftMs = TimeUnit.NANOSECONDS.toMillis(deadlineNs - System.nanoTime())
            if (leftMs <= 0) return@repeat
            val target = if (host.isNotBlank()) host else DNS_TARGETS[it % DNS_TARGETS.size]
            val value = dnsPing(target, minOf(budgetMs, leftMs.toInt()))
            if (value < UNREACHABLE) times += value
        }
        if (times.isEmpty()) {
            return ProbeResult("DNS", UNREACHABLE, 0, rounds, failureReason = "all-failed")
        }
        val sorted = times.sorted()
        val median = sorted[sorted.size / 2]
        val jitter = if (times.size >= 2) {
            times.zipWithNext { a, b -> abs(a - b) }.average()
        } else 0.0
        return ProbeResult(
            method = "DNS",
            latencyMs = median,
            successPercent = times.size * 100 / rounds,
            samples = rounds,
            jitterMs = jitter,
            minMs = sorted.first(),
            maxMs = sorted.last()
        )
    }

    // ─── Smart Ping (HYBRID) ──────────────────────────────────────────────────

    /**
     * Smart ping — the unified HYBRID method that combines fast reachability gating
     * with accurate real-delay measurement.
     *
     * ## Algorithm (inspired by PattNG, Lumen and v2rayNG)
     *
     * 1. **Fast gate** (TCP or DNS): A quick reachability check. If the endpoint
     *    is unreachable, return immediately — no point spending time on HTTPS.
     *    This is the "fast failure" path: a dead node is reported in <500ms.
     *
     * 2. **Real measurement** (HTTP through tunnel if available): If the gate passes,
     *    fire a real HTTPS request through the tunnel (or directly if no tunnel) to
     *    measure the actual latency the user would experience.
     *
     * 3. **Confidence scoring**: The result carries a confidence weight based on
     *    how many methods agreed. A node that passes TCP + HTTP is more trustworthy
     *    than one that only passes TCP.
     *
     * @param profile The proxy profile to test
     * @param tunnelPort SOCKS port of the running Xray tunnel (0 = direct test only)
     * @param timeoutMs Per-method timeout budget
     * @param settings Current app settings for family policy
     */
    fun smartPing(
        profile: ProxyProfile,
        tunnelPort: Int = 0,
        timeoutMs: Int = 5000,
        settings: AppSettings = AppSettings(),
        samples: Int = 1
    ): ProbeResult {
        // MARBLE_SMART_PING_V122 — the default ping method must never blanket-fail healthy
        // servers. The old logic chained hard gates (TCP, then DNS, then HTTPS) so that one
        // filtered phase on a censored underlay (direct SYN blocked, direct Google unreachable)
        // convicted the whole node even when other signals proved it alive. The new logic is
        // evidence-accumulating: every phase that passes contributes reachability, confidence
        // only degrades, and FAILED (0%) is reserved for "every signal failed".
        val host = profile.host.trim()
        if (host.isBlank() || profile.port !in 1..65535) {
            return ProbeResult("SMART", UNREACHABLE, 0, failureReason = "invalid-target")
        }
        // MARBLE_PING_ACCURACY_V145 — the caller's budget is the budget. The old
        // `coerceIn(1_200, 6_000)` silently discarded the user's Settings choice, so asking for
        // 10 s per server still failed every route that needed 7 s to answer.
        val budgetMs = timeoutMs.coerceIn(
            PingBudget.TIMEOUT_MIN_SEC * 1_000,
            PingBudget.TIMEOUT_MAX_SEC * 1_000
        )
        val gateSamples = PingBudget.samples(samples)

        // Phase 1: Layer-0 gate (roughly a third of the budget). The old SYN-only gate accepted
        // ports the firewall opens and then kills — a filtered endpoint entered ranking as
        // "reachable". The gate now requires TCP + TLS ServerHello/Alert with the time-to-RST
        // rule, and the injection signature travels with the result.
        val gateTimeoutMs = (budgetMs * 0.35).toInt().coerceAtLeast(350)
        val tcpResult = reachabilityExtended(
            host,
            profile.port,
            gateTimeoutMs,
            samples = gateSamples,
            settings = settings
        )
        val tcpOk = tcpResult.latencyMs < UNREACHABLE
        val injectedGate = tcpResult.injectedResetSuspected

        // Phase 1b: DNS gate — but only for domain hosts. Resolving a literal IP always
        // "succeeds" instantly without touching the network, so counting it would fake
        // reachability for dead IP endpoints and fake latency for live ones.
        val hostNeedsDns = host.any { it.isLetter() }
        val dnsMs = if (!tcpOk && hostNeedsDns) {
            dnsPing(host, gateTimeoutMs)
        } else {
            UNREACHABLE
        }
        val dnsOk = dnsMs < UNREACHABLE

        // MARBLE_PING_METHODS_V148 — Smart must never mark a server "failed" purely because the
        // TLS half of the verified gate could not complete. Many real protocols expose a plain TCP
        // listener (HTTP, Shadowsocks, a fronted endpoint); their handshake is healthy even though
        // no certificate is ever served. A raw TCP Connect is the honest fallback signal: it is
        // measured only when the stronger gate could not provide one, and it keeps the latency and
        // success rate of the sample the endpoint actually answered.
        val rawTcpResult = if (!tcpOk) {
            tcpConnectExtended(
                host,
                profile.port,
                gateTimeoutMs,
                samples = gateSamples,
                settings = settings
            )
        } else {
            ProbeResult("TCP", UNREACHABLE, 0, samples = gateSamples)
        }
        val rawTcpOk = rawTcpResult.latencyMs < UNREACHABLE

        // A dead TCP gate with no raw connect, no DNS signal and no tunnel is the fast-failure
        // path: report it immediately instead of spending the whole HTTPS budget on a node
        // nothing can reach.
        if (!tcpOk && !rawTcpOk && !dnsOk && tunnelPort <= 0) {
            return ProbeResult("SMART", UNREACHABLE, 0, failureReason = "gate-failed:tcp+dns")
        }

        // When no tunnel is running, the verified TCP+TLS gate to host:port is the honest
        // measurement of endpoint latency (avoiding direct underlay HTTP leaks).
        //
        // MARBLE_PING_TRUTH_V147 — the old copy painted every passing gate as 100 % reachable.
        // A gate that answers 2 of 3 samples (or was partially injected) must carry that loss in
        // its own successPercent, not be promoted to a perfect result before ranking sees it. The
        // magic 60/40 fallback scores are gone for the same reason: the measured success rate is
        // the only honest confidence next to a measured latency.
        if (tunnelPort <= 0) {
            if (tcpOk) {
                val measuredMs = maxOf(tcpResult.latencyMs, 20.0)
                return tcpResult.copy(
                    method = "SMART",
                    latencyMs = measuredMs,
                    successPercent = tcpResult.successPercent,
                    tcpHandshakeMs = measuredMs,
                    injectedResetSuspected = injectedGate,
                    failureReason = if (tcpResult.successPercent < 100) "partial-tcp-gate" else ""
                )
            }
            if (rawTcpOk) {
                val measuredMs = maxOf(rawTcpResult.latencyMs, 20.0)
                return rawTcpResult.copy(
                    method = "SMART",
                    latencyMs = measuredMs,
                    tcpHandshakeMs = measuredMs,
                    injectedResetSuspected = injectedGate,
                    failureReason = "tcp-connect-only"
                )
            }
            if (dnsOk) {
                val measuredMs = maxOf(dnsMs, 20.0)
                return ProbeResult(
                    method = "SMART",
                    latencyMs = measuredMs,
                    successPercent = 20,
                    samples = 1,
                    failureReason = "tcp-blocked-dns-ok"
                )
            }
            return ProbeResult("SMART", UNREACHABLE, 0, failureReason = "all-methods-failed")
        }

        // Phase 2: real HTTPS measurement — through the live tunnel when one is supplied.
        // Several independent 204 origins are raced so one censored CDN can never fail the node
        // on its own. MARBLE_SMART_BUDGET_FULL_V146 — this phase is the measurement the user
        // asked for, so it owns the FULL per-sample budget instead of a 65% slice: a route that
        // answers in 7 s under a 10 s budget must be reported as 7 s, not as timed-out at 6.5 s.
        // The fast gate above is what keeps dead nodes cheap, not a budget carve-out here.
        // MARBLE_PING_TRUTH_V147 — no hidden 3-sample ceiling. When a live tunnel is supplied the
        // HTTPS phase is the measurement the user asked for; if they configured 8 samples they get
        // 8 spaced HTTPS round trips, exactly as the Settings page states. The gate remains the
        // thing that makes dead nodes cheap; it is no longer allowed to shrink the honest tail.
        val httpResult = httpPingBatch(
            socksPort = tunnelPort,
            timeoutMs = budgetMs,
            samples = PingBudget.samples(samples)
        )

        // MARBLE_PING_TRUTH_V147 — the old guard used `20.0..UNREACHABLE`, which includes the
        // all-failed sentinel and therefore treated an empty HTTPS batch as a successful
        // "both signals agree" verdict. That made the TCP fallback below unreachable: a healthy
        // endpoint whose tunnel HTTPS phase was blocked was published as failed.
        if (httpResult.successPercent > 0 && httpResult.latencyMs >= 20.0 && httpResult.latencyMs < UNREACHABLE) {
            // Both signals agree: full confidence. The TCP handshake time is kept only when it
            // is a real measurement, never the UNREACHABLE sentinel.
            return httpResult.copy(
                method = "SMART",
                tcpHandshakeMs = tcpResult.latencyMs.takeIf { it >= 20.0 && it < UNREACHABLE }
                    ?.coerceAtMost(httpResult.latencyMs) ?: 0.0
            )
        }

        // HTTPS through tunnel failed but the endpoint gate passed — fallback with the gate's own
        // measured confidence. A verified gate beats a filtered HTTPS phase, but it must never be
        // promoted to a magic 60 %; a 2/3 gate stays 66 %, a 1/3 gate stays 33 %.
        if (tcpOk && !injectedGate) {
            val measuredMs = maxOf(tcpResult.latencyMs, 20.0)
            return ProbeResult(
                method = "SMART",
                latencyMs = measuredMs,
                successPercent = tcpResult.successPercent,
                samples = tcpResult.samples,
                jitterMs = tcpResult.jitterMs,
                minMs = measuredMs,
                maxMs = measuredMs,
                tcpHandshakeMs = measuredMs,
                failureReason = "http-filtered-tcp-ok"
            )
        }
        if (rawTcpOk) {
            val measuredMs = maxOf(rawTcpResult.latencyMs, 20.0)
            return rawTcpResult.copy(
                method = "SMART",
                latencyMs = measuredMs,
                successPercent = rawTcpResult.successPercent,
                samples = rawTcpResult.samples,
                tcpHandshakeMs = measuredMs,
                injectedResetSuspected = injectedGate,
                failureReason = "http-filtered-tcp-connect-ok"
            )
        }
        if (injectedGate) {
            return ProbeResult(
                method = "SMART",
                latencyMs = UNREACHABLE,
                successPercent = 0,
                samples = tcpResult.samples,
                failureReason = "injected-reset-suspected",
                injectedResetSuspected = true
            )
        }
        if (dnsOk) {
            val measuredMs = maxOf(dnsMs, 20.0)
            return ProbeResult(
                method = "SMART",
                latencyMs = measuredMs,
                successPercent = 20,
                samples = 1,
                failureReason = "tcp-blocked-dns-ok"
            )
        }

        // Tunnel HTTPS failed and no gate signal either: genuinely unreachable.
        return ProbeResult("SMART", UNREACHABLE, 0, failureReason = "all-methods-failed")
    }

    // ─── Unified Measure ───────────────────────────────────────────────────────

    /** Repeats a direct probe and reports median latency plus the success rate. */
    fun measure(
        profile: ProxyProfile,
        icmpMode: Boolean,
        samples: Int,
        timeoutMs: Int,
        settings: AppSettings = AppSettings()
    ): Sample {
        val rounds = PingBudget.samples(samples)
        // MARBLE_PING_ACCURACY_V145 — one resolution per run, spaced samples, warm-up discarded.
        val resolved = if (icmpMode) null else resolveOnce(profile.host, timeoutMs, settings)
        if (!icmpMode && resolved == null) return Sample(0, UNREACHABLE)
        val times = ArrayList<Double>(rounds)
        var attempts = 0
        var consecutiveFailures = 0
        for (round in 0 until rounds) {
            if (round > 0 && !pauseBetweenSamples()) break
            attempts += 1
            val value = if (icmpMode) {
                icmp(profile.host, timeoutMs, settings)
            } else {
                tcp(profile.host, profile.port, timeoutMs, settings, resolved)
            }
            if (value < UNREACHABLE) {
                times += value
                consecutiveFailures = 0
            } else {
                consecutiveFailures += 1
                if (times.isEmpty() && consecutiveFailures >= CONSECUTIVE_FAILURES_BEFORE_ABANDON) break
            }
        }
        val summary = summarize(
            if (icmpMode) "ICMP" else "TCP",
            times,
            attempts,
            warmupDiscarded = true
        )
        if (summary.successPercent <= 0) return Sample(0, UNREACHABLE)
        return Sample(summary.successPercent, summary.latencyMs)
    }

    /**
     * MARBLE_PATTNG_PING_V151 — the one measurement dispatcher of the product.
     *
     * Three methods, three honest answers. Nothing in here estimates: every branch either measures
     * the thing it claims to measure, or reports why it could not.
     *
     *  - [ProbeMethod.REAL_DELAY] — PattNG's real ping.
     *  - [ProbeMethod.TCP_PING]   — PattNG's TCP ping.
     *  - [ProbeMethod.URL_TEST]   — sing-box extended's own delay endpoint.
     */
    fun measureUnified(
        profile: ProxyProfile,
        method: ProbeMethod,
        tunnelPort: Int = 0,
        samples: Int = 3,
        timeoutMs: Int = 5000,
        settings: AppSettings = AppSettings()
    ): ProbeResult = when (method) {
        ProbeMethod.REAL_DELAY -> realDelay(profile, tunnelPort, timeoutMs, samples, settings)
        ProbeMethod.TCP_PING -> tcpPing(profile, timeoutMs, samples, settings)
        ProbeMethod.URL_TEST -> urlTest(profile, timeoutMs, settings)
    }

    /**
     * MARBLE_PATTNG_PING_V151 — **Real delay**, ported from
     * `RealPingWorkerService.startRealPing`.
     *
     * Two stages, in PattNG's order:
     *
     *  1. **The gate.** One raw TCP connect to the node's own `server:port` with a one-second
     *     budget. PattNG skips it for the protocols where a bare handshake proves nothing —
     *     complex/custom configs, Hysteria2, WireGuard and HTTP/3-only endpoints — and so does
     *     this. A gate failure ends the measurement immediately, which is what keeps a sweep over
     *     a subscription of dead nodes fast: no core is spawned for a port that does not answer.
     *  2. **The delay.** A real HTTP round trip through the running tunnel to the delay-test URL
     *     ([DelayTest.url]). The number is the tunnel's, not a socket's.
     *
     * Without a live tunnel there is no honest delay to report. The gate measurement is still
     * published — labelled with its reason — because "the port answers in 84 ms" is true and
     * useful, and pretending it is a tunnel delay is not.
     */
    fun realDelay(
        profile: ProxyProfile,
        tunnelPort: Int,
        timeoutMs: Int,
        samples: Int,
        settings: AppSettings = AppSettings()
    ): ProbeResult {
        val gate = realDelayGate(profile, settings, timeoutMs)
        if (gate != null) {
            if (gate.latencyMs >= UNREACHABLE) {
                return ProbeResult(
                    METHOD_REAL_DELAY, UNREACHABLE, 0, 1,
                    lossPercent = 100.0, failureReason = "tcp-gate-failed"
                )
            }
            if (tunnelPort <= 0) {
                // MARBLE_AUTOPARSER_PING_TRUTH_V154 — the gate says the endpoint is alive, so a
                // no-tunnel state is NOT a failed server: measure the real delay through a
                // throwaway core (the hook the repository installs). Only when no hook exists
                // does the honest gate measurement get published instead.
                val measured = realDelayThroughHook(profile, settings, timeoutMs, samples)
                if (measured != null) return measured
                return gate.copy(
                    method = METHOD_REAL_DELAY,
                    failureReason = "no-tunnel-tcp-gate"
                )
            }
        }

        if (tunnelPort <= 0) {
            // MARBLE_AUTOPARSER_PING_TRUTH_V154 — the gate-exempt case (Hysteria2, WireGuard,
            // pasted Xray JSON): a bare TCP handshake proves nothing here, so "no live tunnel"
            // used to mean FAILED for every healthy server in the subscription. The hook spawns
            // the throwaway core and measures the same real HTTPS round trip the live session
            // reports. A FAILED is only published when there is no way to measure at all.
            val measured = realDelayThroughHook(profile, settings, timeoutMs, samples)
            if (measured != null) return measured
            return ProbeResult(
                METHOD_REAL_DELAY, UNREACHABLE, 0, PingBudget.samples(samples),
                lossPercent = 100.0, failureReason = "no-live-tunnel"
            )
        }

        return tunnelHttpsMeasure(
            socksPort = tunnelPort,
            timeoutMs = timeoutMs,
            samples = samples,
            url = DelayTest.url(settings.delayTestUrl)
        ).copy(method = METHOD_REAL_DELAY)
    }

    /**
     * MARBLE_AUTOPARSER_PING_TRUTH_V154 — the no-tunnel real delay, measured through a
     * throwaway core spawned by [realDelayHook] (installed by the repository, which owns the
     * core processes). `null` when no hook is installed or the hook could not produce a
     * verdict, so the caller falls back to its legacy honest answer.
     */
    private fun realDelayThroughHook(
        profile: ProxyProfile,
        settings: AppSettings,
        timeoutMs: Int,
        samples: Int
    ): ProbeResult? {
        val hook = realDelayHook ?: return null
        val measured = runCatching { hook(profile, settings, timeoutMs, samples) }.getOrNull()
            ?: return null
        return measured.copy(method = METHOD_REAL_DELAY)
    }

    /**
     * The PattNG liveness gate, or `null` when this profile is one of the types PattNG exempts.
     *
     * Exempt: UDP-first and custom protocols, where a TCP handshake to the endpoint is either
     * meaningless (Hysteria2, WireGuard) or not part of the protocol at all (HTTP/3-only).
     *
     * MARBLE_AUTOPARSER_PING_TRUTH_V154 — the gate's budget follows the caller's timeout
     * ([gateBudgetMs]) instead of the fixed one-second desktop constant: on a congested mobile
     * link an alive endpoint regularly needs 2–3 s for a bare handshake, and the old red line
     * convicted far-away nodes. It remains a cheap pre-flight for endpoints that are actually
     * dead — the throwaway core would dial the same destination.
     */
    private fun realDelayGate(
        profile: ProxyProfile,
        settings: AppSettings,
        callerTimeoutMs: Int
    ): ProbeResult? {
        if (!gateApplies(profile)) return null
        return tcpPing(profile, gateBudgetMs(callerTimeoutMs), samples = 1, settings = settings)
    }

    /**
     * MARBLE_AUTOPARSER_PING_TRUTH_V154 — the liveness gate budget, clamped to 1.5–4 s and
     * following the caller's timeout. A 1 s gate on mobile is a coin flip against distance;
     * a 4 s ceiling keeps a sweep over dead endpoints fast.
     */
    internal fun gateBudgetMs(callerTimeoutMs: Int): Int =
        callerTimeoutMs.coerceIn(1_500, 4_000)

    /**
     * MARBLE_PATTNG_PING_V151 — **TCP ping**, ported from
     * `RealPingWorkerService.startTcping` → `SpeedtestManager.socketConnectTime`.
     *
     * One `Socket.connect(host, port, timeout)` per sample and the wall-clock milliseconds it
     * took. It measures the node's own endpoint from this device; it never crosses the tunnel and
     * it never claims to. MarbleNG's [PingBudget] supplies the sample count and the timeout, so
     * the published number is the median of the samples the user asked for instead of a single
     * handshake, but the measurement itself is byte-for-byte PattNG's.
     */
    fun tcpPing(
        profile: ProxyProfile,
        timeoutMs: Int,
        samples: Int,
        settings: AppSettings = AppSettings()
    ): ProbeResult {
        if (!gateApplies(profile)) {
            return ProbeResult(
                METHOD_TCP_PING, UNREACHABLE, 0, 1,
                lossPercent = 100.0, failureReason = "tcp-ping-not-applicable"
            )
        }
        return tcpConnectExtended(
            host = profile.host,
            port = profile.port,
            timeoutMs = timeoutMs,
            samples = samples,
            settings = settings
        ).copy(method = METHOD_TCP_PING)
    }

    /**
     * MARBLE_PATTNG_PING_V151 — **URL test** through sing-box extended.
     *
     * The core measures the round trip itself via its Clash-compatible delay endpoint, so the
     * number accounts for the real transport (unified delay, mux, reality) rather than for a
     * Kotlin socket that never saw the tunnel. [urlTestHook] is installed by the repository,
     * which owns the core process; without it the method reports that plainly instead of
     * substituting a different measurement.
     */
    fun urlTest(
        profile: ProxyProfile,
        timeoutMs: Int,
        settings: AppSettings = AppSettings()
    ): ProbeResult {
        val hook = urlTestHook
            ?: return ProbeResult(
                METHOD_URL_TEST, UNREACHABLE, 0, 1,
                lossPercent = 100.0, failureReason = "singbox-unavailable"
            )
        return runCatching { hook(profile, settings, timeoutMs) }.getOrElse {
            ProbeResult(
                METHOD_URL_TEST, UNREACHABLE, 0, 1,
                lossPercent = 100.0,
                failureReason = (it.message ?: it::class.java.simpleName).take(120)
            )
        }
    }

    /** Method labels stored in [ProbeResult.method]; kept as constants so nothing drifts. */
    const val METHOD_REAL_DELAY = "REAL_DELAY"
    const val METHOD_TCP_PING = "TCP_PING"
    const val METHOD_URL_TEST = "URL_TEST"

    /**
     * Installs the sing-box extended URL-test implementation.
     *
     * [RouteProbe] is a process-wide object with no Android context, and the URL test needs the
     * core process the repository owns. Rather than thread a manager through every call site of
     * [measureUnified], the repository publishes one closure at startup. `null` (unit tests, or a
     * build without the core) makes [urlTest] report unavailability instead of guessing.
     */
    @Volatile
    var urlTestHook: ((ProxyProfile, AppSettings, Int) -> ProbeResult)? = null

    /**
     * MARBLE_AUTOPARSER_PING_TRUTH_V154 — the no-tunnel **real delay** implementation.
     *
     * "A server that is alive never reports FAILED." Before this hook existed, a disconnected
     * real-delay measurement of a gate-exempt profile (Hysteria2, WireGuard, pasted Xray JSON)
     * had no way to prove the config and answered `no-live-tunnel` → FAILED — even though one
     * tap connected the very same server. The repository installs one closure that spawns a
     * throwaway core for the candidate (Xray's temporary tunnel for Xray-runnable profiles, the
     * sing-box extended autoparser for the protocols Xray cannot run at all: Hysteria v1 and
     * TUIC/AnyTLS link-only nodes) and times real HTTPS round trips to the delay URL through
     * that tunnel — the same measurement the live session reports, owned by the probe.
     *
     * Signature: `(profile, settings, timeoutMs, samples) → ProbeResult`. `null` (unit tests)
     * makes [realDelay] fall back to its legacy honest answers (gate publication /
     * `no-live-tunnel`) instead of guessing.
     */
    @Volatile
    var realDelayHook: ((ProxyProfile, AppSettings, Int, Int) -> ProbeResult)? = null

    /**
     * True when a raw TCP handshake to the endpoint is meaningful for this profile — PattNG's own
     * exemption list, expressed on MarbleNG's model.
     */
    private fun gateApplies(profile: ProxyProfile): Boolean {
        val scheme = profile.scheme.lowercase()
        if (scheme in setOf("hysteria2", "hy2", "hysteria", "wireguard", "wg", "json")) return false
        if (profile.host.isBlank() || profile.port !in 1..65535) return false
        // An HTTP/3-only endpoint never completes a TCP handshake; PattNG exempts `alpn=h3`.
        val alpn = alpnOf(profile)
        return alpn.isEmpty() || alpn.any { !it.startsWith("h3") }
    }

    private fun alpnOf(profile: ProxyProfile): List<String> = runCatching {
        val outbounds = JSONObject(profile.configJson).optJSONArray("outbounds") ?: return@runCatching emptyList<String>()
        for (i in 0 until outbounds.length()) {
            val outbound = outbounds.optJSONObject(i) ?: continue
            val alpn = outbound.optJSONObject("streamSettings")
                ?.optJSONObject("tlsSettings")
                ?.optJSONArray("alpn")
                ?: continue
            return@runCatching (0 until alpn.length()).mapNotNull { alpn.optString(it).takeIf(String::isNotBlank) }
        }
        emptyList()
    }.getOrDefault(emptyList())

    /**
     * MARBLE_PING_TRUTH_V149 — the HTTP GET / HEAD product methods.
     *
     * ## The bug
     *
     * Both methods used to call [httpPingBatch] with the caller's `tunnelPort` and nothing else.
     * When no tunnel is running, `socksPort` is 0 and [httpPingBatch] measures a DIRECT request to
     * a public 204 origin over the underlay. That measurement has two fatal properties:
     *
     *  1. it is **identical for every server in the list** — it never touches `profile.host` at
     *     all, so "ping this server with HTTP GET" was really "ping Cloudflare"; and
     *  2. on precisely the censored links this app exists for, those origins are filtered, so the
     *     probe failed for *every* server at once, which is exactly the reported symptom of
     *     "HTTP GET says failed while the servers are all working".
     *
     * ## The fix
     *
     * When a live tunnel is supplied the behaviour is unchanged and correct: a real HTTPS request
     * through the selected route. Without one, the request is aimed at the server's own endpoint
     * over TLS instead of at a third-party origin, so the method measures the thing the user
     * selected. If the endpoint is not an HTTP speaker (the normal case for a proxy port), the
     * verified TCP+TLS gate provides the answer — the same evidence, one layer down, reported
     * with its reason rather than as a failure.
     */
    private fun httpMeasure(
        profile: ProxyProfile,
        tunnelPort: Int,
        samples: Int,
        timeoutMs: Int,
        settings: AppSettings,
        httpMethod: String
    ): ProbeResult {
        val label = if (httpMethod.equals("HEAD", ignoreCase = true)) "HTTP_HEAD" else "HTTP_GET"
        if (tunnelPort > 0) {
            val viaTunnel = httpPingBatch(
                socksPort = tunnelPort,
                timeoutMs = timeoutMs,
                samples = samples,
                httpMethod = httpMethod
            )
            if (viaTunnel.latencyMs < UNREACHABLE) return viaTunnel.copy(method = label)
        }

        // No tunnel (or the tunnel phase was filtered): measure the SELECTED endpoint.
        //
        // Only an endpoint that genuinely speaks HTTPS is worth a full HTTP batch. A VLESS/Trojan/
        // VMess port answers a certificate the device cannot validate (that is the entire premise
        // of the pinning options), so aiming HttpURLConnection at it would spend the whole budget
        // proving something already known before reaching the gate below. Restricting the attempt
        // to HTTP-family profiles keeps this path fast for everything else.
        if (profile.scheme.lowercase() in setOf("http", "https")) {
            val direct = httpPingBatch(
                socksPort = 0,
                timeoutMs = timeoutMs,
                samples = samples,
                targets = listOf(endpointHttpsUrl(profile)),
                httpMethod = httpMethod
            )
            if (direct.latencyMs < UNREACHABLE) {
                return direct.copy(method = label, failureReason = "endpoint-direct")
            }
        }

        val gate = tcpExtended(profile.host, profile.port, timeoutMs, samples, settings)
        if (gate.latencyMs < UNREACHABLE) {
            return gate.copy(
                method = label,
                failureReason = if (gate.failureReason.isBlank()) "http-unavailable-gate-ok" else gate.failureReason
            )
        }
        return gate.copy(method = label)
    }

    /** `https://host:port/` for the profile endpoint, with IPv6 literals bracketed. */
    private fun endpointHttpsUrl(profile: ProxyProfile): String {
        val host = profile.host.trim()
        val literal = if (host.contains(':') && !host.startsWith("[")) "[$host]" else host
        return "https://$literal:${profile.port}/"
    }

    /**
     * MARBLE_IRAN_AWARE_PING_L1_RTT — latency stage of the Layer-1 verification, measured through
     * an already-running tunnel against the rotated pool with jittered parallel probes.
     */
    fun tunnelHttpsMeasure(
        socksPort: Int,
        timeoutMs: Int,
        samples: Int = 2,
        url: String = DelayTest.URL
    ): ProbeResult {
        // MARBLE_PING_TRUTH_V147 — configured samples are the budget. The old 3-sample ceiling
        // made "samples per server" a suggestion for the real-tunnel path, and warm-up was kept
        // in the median as well; both are now the same as every other measurement.
        //
        // MARBLE_PATTNG_PING_V151 — the target is the user's delay-test URL, the same one the
        // core would fetch, instead of a rotating CDN pool. A measurement whose target changes
        // between two runs cannot be compared, and PattNG's real delay has always been one fixed
        // `generate_204` endpoint for exactly that reason.
        val target = delayTarget(url)
        val rounds = PingBudget.samples(samples)
        val times = ArrayList<Double>(rounds)
        var injected = false
        var silent = false
        var consecutiveFailures = 0
        for (round in 0 until rounds) {
            if (round > 0 && !pauseBetweenSamples()) break
            val result = runCatching {
                SocksHttpClient.tunnelRttBatch(
                    port = socksPort,
                    host = target.first,
                    path = target.second,
                    samples = 1,
                    timeoutMs = timeoutMs.coerceIn(1_000, 12_000)
                )
            }.getOrNull()
            if (result != null && result.samplesMs.isNotEmpty()) {
                val value = result.samplesMs.first()
                if (value.isFinite() && value > 0.0) {
                    times += value
                    consecutiveFailures = 0
                }
            } else {
                consecutiveFailures += 1
                if (times.isEmpty() && consecutiveFailures >= CONSECUTIVE_FAILURES_BEFORE_ABANDON) break
            }
        }
        if (times.isEmpty()) {
            return ProbeResult(
                "TUNNEL",
                UNREACHABLE,
                0,
                rounds,
                lossPercent = 100.0,
                failureReason = "delay-url-failed"
            )
        }
        val summary = summarize("TUNNEL", times, rounds, warmupDiscarded = true)
        return summary.copy(
            firstByteMs = summary.latencyMs,
            failureReason = if (injected) "injected-reset-suspected" else ""
        )
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Splits a delay-test URL into the `host` / `path` pair the tunnel RTT primitive needs.
     *
     * The primitive performs a TLS request, so a plaintext `http://` override cannot be honoured
     * here and falls back to [DelayTest.URL]; the sing-box URL test accepts either scheme because
     * the core performs that fetch itself.
     */
    private fun delayTarget(url: String): Pair<String, String> {
        val candidate = url.trim()
        val normalized = if (candidate.startsWith("https://")) {
            candidate
        } else {
            DelayTest.URL
        }
        return runCatching {
            val parsed = URL(normalized)
            val path = parsed.path.ifBlank { "/" }
            parsed.host to path
        }.getOrElse { "www.gstatic.com" to "/generate_204" }
    }

    /**
     * Compute the standard deviation of a list of values.
     * Used for jitter and stability scoring.
     */
    private fun standardDeviation(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        val variance = values.map { (it - mean) * (it - mean) }.average()
        return sqrt(variance)
    }

    /**
     * Compute a confidence score (0.0 to 1.0) for a measurement based on
     * how consistent the samples were. Inspired by Lumen's multi-signal scoring.
     */
    fun measurementConfidence(result: ProbeResult): Double {
        if (result.successPercent <= 0) return 0.0
        val successFactor = result.successPercent / 100.0
        val jitterFactor = if (result.latencyMs > 0 && result.jitterMs > 0) {
            (1.0 - (result.jitterMs / result.latencyMs).coerceAtMost(1.0))
        } else 1.0
        val lossFactor = 1.0 - (result.lossPercent / 100.0)
        val sampleFactor = (result.samples.coerceAtMost(5) / 5.0)
        return (successFactor * 0.35 + jitterFactor * 0.25 + lossFactor * 0.25 + sampleFactor * 0.15)
            .coerceIn(0.0, 1.0)
    }
}
