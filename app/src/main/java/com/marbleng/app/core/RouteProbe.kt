package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.PingBudget
import com.marbleng.app.model.ProbeMethod
import com.marbleng.app.model.ProxyProfile
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
 * Endpoint reachability probes — the full MarbleNG ping toolkit.
 *
 * Inspired by the best techniques from v2rayNG (realDelayTest / HTTPing through proxy),
 * PattNG (TLS handshake parity, multi-target racing), Incy (DNS-based reachability),
 * Exclave (TCP connect with Happy Eyeballs), and Lumen (weighted multi-signal scoring).
 *
 * ## Methods
 *
 *  - [tcp]    — Raw TCP SYN to host:port. Proves the endpoint listens. Fast, cheap, but
 *               only verifies the address, not the proxy route. Uses Happy-Eyeballs-style
 *               address racing through [AddressFamilyPolicy].
 *
 *  - [icmp]   — ICMP echo via /system/bin/ping. The classic reachability test. Many
 *               hosts and mobile carriers drop ICMP, so failure here is not proof a
 *               node is dead. Supports IPv4/IPv6 selection through the family policy.
 *
 *  - [httpPing] — HTTPS GET to a well-known 204 endpoint through a SOCKS proxy port.
 *               This is the "real delay" / "real test" method used by v2rayNG and PattNG:
 *               a genuine proxy request that proves the full route works (handshake +
 *               TLS + first byte). Multiple targets are raced and the best wins.
 *
 *  - [dnsPing] — Measures the DNS resolution time for a domain through the system
 *               resolver. Useful as a quick liveness check when ICMP is blocked and
 *               TCP connect times are unreliable (e.g. on carrier-grade NAT).
 *
 *  - [smartPing] — The unified smart ping: a fast reachability gate (TCP or DNS)
 *               followed by the real verified HTTPS measurement. Returns quickly when
 *               the gate fails, and accurately when it succeeds. This is the default
 *               method for the whole product.
 *
 *  - [measure] — Repeats any method and reports median latency + success rate.
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

    /** Well-known HTTPS 204 targets for real delay tests (v2rayNG-compatible). */
    private val REAL_DELAY_TARGETS = listOf(
        "https://www.gstatic.com/generate_204",
        "https://cp.cloudflare.com/generate_204",
        "https://www.google.com/generate_204",
        "https://connectivitycheck.gstatic.com/generate_204"
    )

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
        val failureReason: String = ""
    )

    // ─── TCP Connect ───────────────────────────────────────────────────────────

    private fun tcpOnce(
        host: String,
        port: Int,
        timeoutMs: Int,
        plan: IpFamilyPlan,
        resolved: List<InetAddress>? = null
    ): Double {
        // MARBLE_RESOLVE_BUDGET_V144 — resolution is part of this call's [timeoutMs] contract,
        // not a free unbounded prelude to it. Domain hosts may spend at most half the budget
        // (floored/ceiled so tiny budgets still resolve and huge ones never stall); literals
        // skip the resolver untouched.
        //
        // MARBLE_PING_ACCURACY_V145 — [resolved] lets a multi-sample caller resolve once and
        // reuse the answer. Re-resolving before every sample measured the RESOLVER, not the
        // route: the first sample of a domain node carried the full DNS round trip and the rest
        // carried whatever the OS cache decided to do, which is exactly the "same server, three
        // very different numbers" the user sees.
        val candidates = resolved
            ?: AddressFamilyPolicy.resolveCandidates(
                host,
                plan,
                (timeoutMs / 2).coerceIn(500, 2_500)
            )
        if (candidates.isEmpty()) return UNREACHABLE
        val loopStarted = System.nanoTime()
        val perAddressMs = (timeoutMs / candidates.size).coerceIn(minOf(300, timeoutMs), timeoutMs)
        candidates.forEachIndexed { index, address ->
            val spentMs = ((System.nanoTime() - loopStarted) / 1_000_000L).toInt()
            val remainingMs = timeoutMs - spentMs
            if (remainingMs <= 0) return UNREACHABLE
            val attemptMs = when {
                candidates.size == 1 -> timeoutMs
                index == candidates.lastIndex -> maxOf(remainingMs, 300)
                else -> minOf(perAddressMs, maxOf(remainingMs, 300))
            }
            // MARBLE_PING_ACCURACY_V145 — the stopwatch belongs to the ATTEMPT, not to the whole
            // Happy-Eyeballs loop. The old code timed from the first candidate, so a dual-stack
            // node whose AAAA address is blackholed reported "IPv6 timeout + real IPv4 handshake"
            // as its latency: a 40 ms server measured as 1040 ms, and ranked accordingly.
            val attemptStarted = System.nanoTime()
            val connected = runCatching {
                Socket().use { socket ->
                    socket.tcpNoDelay = true
                    socket.soTimeout = attemptMs
                    socket.connect(InetSocketAddress(address, port), attemptMs)
                }
            }.isSuccess
            if (connected) return ((System.nanoTime() - attemptStarted) / 1e6)
        }
        return UNREACHABLE
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
    ): ProbeResult {
        if (host.isBlank() || port !in 1..65535) {
            return ProbeResult("TCP", UNREACHABLE, 0, samples, failureReason = "invalid-target")
        }
        val rounds = PingBudget.samples(samples)
        // MARBLE_PING_ACCURACY_V145 — resolve once for the whole run, space the samples out and
        // summarize with a warm-up discard. Back-to-back SYNs to the same endpoint measure the
        // remote SYN backlog, not the path.
        val resolved = resolveOnce(host, timeoutMs, settings)
            ?: return ProbeResult(
                "TCP",
                UNREACHABLE,
                0,
                rounds,
                lossPercent = 100.0,
                failureReason = "dns-failed"
            )
        val times = ArrayList<Double>(rounds)
        var attempts = 0
        var consecutiveFailures = 0
        for (round in 0 until rounds) {
            if (round > 0 && !pauseBetweenSamples()) break
            attempts += 1
            val value = tcp(host, port, timeoutMs, settings, resolved)
            if (value < UNREACHABLE) {
                times += value
                consecutiveFailures = 0
            } else {
                consecutiveFailures += 1
                if (times.isEmpty() && consecutiveFailures >= CONSECUTIVE_FAILURES_BEFORE_ABANDON) break
            }
        }
        return summarize("TCP", times, attempts, warmupDiscarded = true)
    }

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
                    add("-n"); add("-q"); add("-c"); add("1"); add("-W"); add(seconds.toString())
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

            if (process.exitValue() != 0) return@runCatching UNREACHABLE

            val average = Regex("=\\s*[\\d.]+/([\\d.]+)/").find(output)
                ?.groupValues?.getOrNull(1)
                ?.toDoubleOrNull()
                ?: Regex("time=([\\d.]+)").find(output)
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
            val process = ProcessBuilder(
                buildList {
                    add("/system/bin/ping")
                    add("-n"); add("-q")
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

            if (process.exitValue() != 0 && !output.contains("bytes from", true)) {
                return@runCatching ProbeResult("ICMP", UNREACHABLE, 0, packets, failureReason = "exit-${process.exitValue()}")
            }

            // Parse individual RTT values: "64 bytes from ...: icmp_seq=1 ttl=56 time=41.3 ms"
            val rttValues = Regex("time=([\\d.]+)\\s*ms").findAll(output)
                .mapNotNull { it.groupValues[1].toDoubleOrNull() }
                .filter { it > 0.0 }
                .toList()

            // Parse packet loss: "3 packets transmitted, 3 received, 0% packet loss"
            val lossMatch = Regex("(\\d+)%\\s*packet loss").find(output)
            val lossPercent = lossMatch?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: 100.0

            // Parse summary: "rtt min/avg/max/mdev = 41.316/41.316/41.316/0.000 ms"
            val summaryMatch = Regex("=\\s*([\\d.]+)/([\\d.]+)/([\\d.]+)/([\\d.]+)").find(output)

            if (rttValues.isEmpty()) {
                return@runCatching ProbeResult("ICMP", UNREACHABLE, 0, packets, lossPercent = lossPercent, failureReason = "no-responses")
            }

            val sorted = rttValues.sorted()
            val median = sorted[sorted.size / 2]
            val jitter = if (rttValues.size >= 2) {
                rttValues.zipWithNext { a, b -> abs(a - b) }.average()
            } else {
                summaryMatch?.groupValues?.getOrNull(4)?.toDoubleOrNull() ?: 0.0
            }
            val p95Index = ((sorted.size - 1) * 0.95).toInt().coerceIn(0, sorted.lastIndex)

            ProbeResult(
                method = "ICMP",
                latencyMs = median,
                successPercent = ((1.0 - lossPercent / 100.0) * 100).toInt().coerceIn(0, 100),
                samples = packets,
                jitterMs = jitter,
                minMs = sorted.first(),
                maxMs = sorted.last(),
                p95Ms = sorted[p95Index],
                lossPercent = lossPercent
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
        targets: List<String> = REAL_DELAY_TARGETS
    ): ProbeResult {
        if (targets.isEmpty()) {
            return ProbeResult("HTTP", UNREACHABLE, 0, 1, failureReason = "no-targets")
        }
        val budgetMs = timeoutMs.coerceIn(500, 15_000)
        if (targets.size == 1) return httpPingOnce(targets.first(), socksPort, budgetMs)

        val winner = AtomicReference<ProbeResult>()
        val firstSuccess = CountDownLatch(1)
        val allFinished = CountDownLatch(targets.size)
        targets.forEach { target ->
            Thread({
                try {
                    val result = httpPingOnce(target, socksPort, budgetMs)
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
        timeoutMs: Int
    ): ProbeResult {
        return runCatching {
            val proxy = if (socksPort > 0) {
                java.net.Proxy(
                    java.net.Proxy.Type.SOCKS,
                    InetSocketAddress("127.0.0.1", socksPort)
                )
            } else {
                java.net.Proxy.NO_PROXY
            }
            val connection = URL(url).openConnection(proxy) as HttpURLConnection
            try {
                connection.connectTimeout = timeoutMs
                connection.readTimeout = timeoutMs
                connection.requestMethod = "GET"
                connection.instanceFollowRedirects = true
                connection.useCaches = false
                connection.setRequestProperty("User-Agent", "MarbleNG/1.0")
                connection.setRequestProperty("Connection", "close")

                val start = System.nanoTime()
                connection.connect()
                val connectMs = (System.nanoTime() - start) / 1e6

                val responseCode = connection.responseCode
                val firstByteMs = (System.nanoTime() - start) / 1e6

                // Drain a small amount of data to ensure the connection is fully established
                runCatching { connection.inputStream.read() }

                if (responseCode in 200..399) {
                    ProbeResult(
                        method = "HTTP",
                        latencyMs = firstByteMs,
                        successPercent = 100,
                        samples = 1,
                        tcpHandshakeMs = connectMs,
                        firstByteMs = firstByteMs
                    )
                } else {
                    ProbeResult("HTTP", UNREACHABLE, 0, 1, failureReason = "status-$responseCode")
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
        targets: List<String> = REAL_DELAY_TARGETS
    ): ProbeResult {
        val rounds = PingBudget.samples(samples)
        val times = ArrayList<Double>(rounds)
        val handshakeTimes = ArrayList<Double>(rounds)
        var consecutiveFailures = 0
        for (round in 0 until rounds) {
            // MARBLE_PING_ACCURACY_V145 — spaced samples: a burst of HTTPS requests to the same
            // 204 origin measures connection reuse and server-side rate limiting, not the route.
            if (round > 0 && !pauseBetweenSamples()) break
            val result = httpPing(socksPort, timeoutMs, targets)
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
            return ProbeResult("HTTP", UNREACHABLE, 0, rounds, lossPercent = 100.0, failureReason = "all-failed")
        }
        val sorted = times.sorted()
        val median = sorted[sorted.size / 2]
        val jitter = if (times.size >= 2) {
            times.zipWithNext { a, b -> abs(a - b) }.average()
        } else 0.0
        val p95Index = ((sorted.size - 1) * 0.95).toInt().coerceIn(0, sorted.lastIndex)
        return ProbeResult(
            method = "HTTP",
            latencyMs = median,
            successPercent = times.size * 100 / rounds,
            samples = rounds,
            jitterMs = jitter,
            minMs = sorted.first(),
            maxMs = sorted.last(),
            p95Ms = sorted[p95Index],
            lossPercent = (rounds - times.size).toDouble() * 100.0 / rounds,
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

        // Phase 1: TCP gate (roughly a third of the budget), measured with the user's own sample
        // count instead of a single hard-coded SYN — one handshake is a coin toss on a lossy
        // mobile link, and it was the number the whole product ranked servers by.
        val gateTimeoutMs = (budgetMs * 0.35).toInt().coerceAtLeast(350)
        val tcpResult = tcpExtended(
            host,
            profile.port,
            gateTimeoutMs,
            samples = gateSamples,
            settings = settings
        )
        val tcpOk = tcpResult.latencyMs < UNREACHABLE

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

        // A dead TCP gate with no DNS signal and no tunnel is the fast-failure path: report it
        // immediately instead of spending the whole HTTPS budget on a node nothing can reach.
        if (!tcpOk && !dnsOk && tunnelPort <= 0) {
            return ProbeResult("SMART", UNREACHABLE, 0, failureReason = "gate-failed:tcp+dns")
        }

        // When no tunnel is running, the real TCP SYN-ACK to host:port is the honest measurement
        // of endpoint latency (avoiding direct underlay HTTP leaks).
        if (tunnelPort <= 0) {
            if (tcpOk) {
                val measuredMs = maxOf(tcpResult.latencyMs, 20.0)
                return tcpResult.copy(
                    method = "SMART",
                    latencyMs = measuredMs,
                    successPercent = 100,
                    tcpHandshakeMs = measuredMs
                )
            }
            if (dnsOk) {
                val measuredMs = maxOf(dnsMs, 20.0)
                return ProbeResult(
                    method = "SMART",
                    latencyMs = measuredMs,
                    successPercent = 40,
                    samples = 1,
                    failureReason = "tcp-blocked-dns-ok"
                )
            }
            return ProbeResult("SMART", UNREACHABLE, 0, failureReason = "all-methods-failed")
        }

        // Phase 2: real HTTPS measurement — through the live tunnel when one is supplied.
        // Several independent 204 origins are raced so one censored CDN can never fail the node on its own.
        val realTimeoutMs = (budgetMs * 0.65).toInt().coerceAtLeast(800)
        val httpResult = httpPingBatch(
            socksPort = tunnelPort,
            timeoutMs = realTimeoutMs,
            samples = gateSamples.coerceAtMost(3)
        )

        if (httpResult.latencyMs in 20.0..UNREACHABLE) {
            // Both signals agree: full confidence. The TCP handshake time is kept only when it
            // is a real measurement, never the UNREACHABLE sentinel.
            return httpResult.copy(
                method = "SMART",
                tcpHandshakeMs = tcpResult.latencyMs.takeIf { it in 20.0..UNREACHABLE }
                    ?.coerceAtMost(httpResult.latencyMs) ?: 0.0
            )
        }

        // HTTPS through tunnel failed but the endpoint gate passed — fallback with reduced confidence
        if (tcpOk) {
            val measuredMs = maxOf(tcpResult.latencyMs, 20.0)
            return ProbeResult(
                method = "SMART",
                latencyMs = measuredMs,
                successPercent = 60,
                samples = tcpResult.samples,
                jitterMs = tcpResult.jitterMs,
                minMs = measuredMs,
                maxMs = measuredMs,
                tcpHandshakeMs = measuredMs,
                failureReason = "http-filtered-tcp-ok"
            )
        }
        if (dnsOk) {
            val measuredMs = maxOf(dnsMs, 20.0)
            return ProbeResult(
                method = "SMART",
                latencyMs = measuredMs,
                successPercent = 40,
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
     * Unified measurement dispatcher — picks the right method based on [ProbeMethod]
     * and returns a rich [ProbeResult].
     *
     * This is the single entry point every caller in the product should use:
     * the Home ping button, the Servers group ping, Ping all and ranking.
     */
    fun measureUnified(
        profile: ProxyProfile,
        method: ProbeMethod,
        tunnelPort: Int = 0,
        samples: Int = 3,
        timeoutMs: Int = 5000,
        settings: AppSettings = AppSettings()
    ): ProbeResult = when (method) {
        ProbeMethod.TCP -> tcpExtended(profile.host, profile.port, timeoutMs, samples, settings)
        ProbeMethod.ICMP -> icmpExtended(profile.host, timeoutMs, samples, settings)
        ProbeMethod.HTTP -> httpPingBatch(socksPort = 0, timeoutMs = timeoutMs, samples = samples)
        ProbeMethod.DNS -> dnsPingExtended(host = profile.host, timeoutMs = timeoutMs, samples = samples)
        ProbeMethod.TUNNEL -> {
            if (tunnelPort > 0) {
                httpPingBatch(tunnelPort, timeoutMs, samples)
            } else {
                // No tunnel available — fall back to TCP as best effort
                tcpExtended(profile.host, profile.port, timeoutMs, samples, settings).copy(
                    method = "TUNNEL",
                    failureReason = "no-tunnel-fallback-tcp"
                )
            }
        }
        ProbeMethod.HYBRID -> smartPing(profile, tunnelPort, timeoutMs, settings, samples)
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

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
