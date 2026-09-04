package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.ProbeMethod
import com.marbleng.app.model.ProxyProfile
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.TimeUnit
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
        plan: IpFamilyPlan
    ): Double {
        val candidates = AddressFamilyPolicy.resolveCandidates(host, plan)
        if (candidates.isEmpty()) return UNREACHABLE
        val started = System.nanoTime()
        val perAddressMs = (timeoutMs / candidates.size).coerceIn(minOf(300, timeoutMs), timeoutMs)
        candidates.forEachIndexed { index, address ->
            val spentMs = ((System.nanoTime() - started) / 1_000_000L).toInt()
            val remainingMs = timeoutMs - spentMs
            if (remainingMs <= 0) return UNREACHABLE
            val attemptMs = when {
                candidates.size == 1 -> timeoutMs
                index == candidates.lastIndex -> maxOf(remainingMs, 300)
                else -> minOf(perAddressMs, maxOf(remainingMs, 300))
            }
            val connected = runCatching {
                Socket().use { socket ->
                    socket.tcpNoDelay = true
                    socket.soTimeout = attemptMs
                    socket.connect(InetSocketAddress(address, port), attemptMs)
                }
            }.isSuccess
            if (connected) return ((System.nanoTime() - started) / 1e6)
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
        settings: AppSettings = AppSettings()
    ): Double {
        if (host.isBlank() || port !in 1..65535) return UNREACHABLE

        val plan = AddressFamilyPolicy.plan(settings = settings)
        val firstStarted = System.nanoTime()
        val first = tcpOnce(host, port, timeoutMs, plan)
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

        return tcpOnce(host, port, timeoutMs, plan)
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
    ): ProbeResult {
        if (host.isBlank() || port !in 1..65535) {
            return ProbeResult("TCP", UNREACHABLE, 0, samples, failureReason = "invalid-target")
        }
        val rounds = samples.coerceIn(1, 8)
        val times = ArrayList<Double>(rounds)
        repeat(rounds) {
            val value = tcp(host, port, timeoutMs, settings)
            if (value < UNREACHABLE) times += value
        }
        if (times.isEmpty()) {
            return ProbeResult("TCP", UNREACHABLE, 0, rounds, lossPercent = 100.0, failureReason = "all-failed")
        }
        val sorted = times.sorted()
        val median = sorted[sorted.size / 2]
        val jitter = if (times.size >= 2) {
            times.zipWithNext { a, b -> abs(a - b) }.average()
        } else 0.0
        val p95Index = ((times.size - 1) * 0.95).toInt().coerceIn(0, times.lastIndex)
        return ProbeResult(
            method = "TCP",
            latencyMs = median,
            successPercent = times.size * 100 / rounds,
            samples = rounds,
            jitterMs = jitter,
            minMs = sorted.first(),
            maxMs = sorted.last(),
            p95Ms = sorted[p95Index],
            lossPercent = (rounds - times.size).toDouble() * 100.0 / rounds,
            tcpHandshakeMs = median
        )
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
        val seconds = (timeoutMs / 1000).coerceIn(1, 10)
        val target = AddressFamilyPolicy
            .resolveCandidates(host, AddressFamilyPolicy.plan(settings = settings))
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
        val seconds = (timeoutMs / 1000).coerceIn(1, 10)
        val packets = count.coerceIn(1, 8)
        val target = AddressFamilyPolicy
            .resolveCandidates(host, AddressFamilyPolicy.plan(settings = settings))
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
                val waitSec = (seconds.toLong() * packets / 5 + 3).coerceAtMost(30)
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
     * Multiple targets are tried in sequence; the first successful measurement wins.
     * This prevents a single blocked CDN from classifying an otherwise working node as dead.
     */
    fun httpPing(
        socksPort: Int = 0,
        timeoutMs: Int = 5000,
        targets: List<String> = REAL_DELAY_TARGETS
    ): ProbeResult {
        val started = System.nanoTime()
        for (target in targets) {
            val result = httpPingOnce(target, socksPort, timeoutMs)
            if (result.latencyMs < UNREACHABLE) return result
            // Budget check: stop if we've spent too much time on failed targets
            val elapsed = (System.nanoTime() - started) / 1_000_000L
            if (elapsed > timeoutMs * 2L) break
        }
        return ProbeResult("HTTP", UNREACHABLE, 0, 1, failureReason = "all-targets-failed")
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
        val rounds = samples.coerceIn(1, 8)
        val times = ArrayList<Double>(rounds)
        val handshakeTimes = ArrayList<Double>(rounds)
        repeat(rounds) {
            val result = httpPing(socksPort, timeoutMs, targets)
            if (result.latencyMs < UNREACHABLE) {
                times += result.latencyMs
                if (result.tcpHandshakeMs > 0) handshakeTimes += result.tcpHandshakeMs
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
     */
    fun dnsPing(
        host: String = "",
        timeoutMs: Int = 3000
    ): Double {
        val target = host.ifBlank { DNS_TARGETS.random() }
        return runCatching {
            val start = System.nanoTime()
            val addresses = java.net.InetAddress.getAllByName(target)
            val elapsed = (System.nanoTime() - start) / 1e6
            if (addresses.isNotEmpty()) elapsed else UNREACHABLE
        }.getOrDefault(UNREACHABLE)
    }

    /**
     * Extended DNS measurement with multiple samples and statistics.
     */
    fun dnsPingExtended(
        host: String = "",
        timeoutMs: Int = 3000,
        samples: Int = 3
    ): ProbeResult {
        val rounds = samples.coerceIn(1, 8)
        val times = ArrayList<Double>(rounds)
        repeat(rounds) {
            val target = if (host.isNotBlank()) host else DNS_TARGETS[it % DNS_TARGETS.size]
            val value = dnsPing(target, timeoutMs)
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
        settings: AppSettings = AppSettings()
    ): ProbeResult {
        if (profile.host.isBlank()) {
            return ProbeResult("SMART", UNREACHABLE, 0, failureReason = "blank-host")
        }

        // Phase 1: Fast TCP gate (budget: 40% of timeout)
        val gateTimeoutMs = (timeoutMs * 0.4).toInt().coerceIn(300, 3000)
        val tcpResult = tcpExtended(profile.host, profile.port, gateTimeoutMs, samples = 1, settings = settings)

        if (tcpResult.latencyMs >= UNREACHABLE) {
            // Gate failed — try DNS as a secondary check before giving up
            val dnsResult = dnsPing(profile.host, gateTimeoutMs)
            if (dnsResult >= UNREACHABLE) {
                return ProbeResult("SMART", UNREACHABLE, 0, failureReason = "gate-failed:tcp+dns")
            }
            // DNS works but TCP doesn't — endpoint might be filtering SYN
            // Still try the real test if we have a tunnel
            if (tunnelPort <= 0) {
                return ProbeResult(
                    "SMART", dnsResult, 50,
                    failureReason = "tcp-blocked-dns-ok"
                )
            }
        }

        // Phase 2: Real HTTPS measurement through tunnel (or direct)
        val realTimeoutMs = (timeoutMs * 0.6).toInt().coerceIn(1000, 8000)
        val httpResult = if (tunnelPort > 0) {
            httpPingBatch(tunnelPort, realTimeoutMs, samples = 2)
        } else {
            // No tunnel: do a direct HTTP test as the best available measurement
            httpPingBatch(0, realTimeoutMs, samples = 2)
        }

        if (httpResult.latencyMs < UNREACHABLE) {
            // Combine TCP handshake time with HTTP result for a richer picture
            return httpResult.copy(
                method = "SMART",
                tcpHandshakeMs = tcpResult.latencyMs.coerceAtMost(httpResult.latencyMs)
            )
        }

        // HTTP failed but TCP gate passed — the endpoint listens but the proxy
        // route might not work. Return the TCP time with reduced confidence.
        if (tcpResult.latencyMs < UNREACHABLE) {
            return ProbeResult(
                method = "SMART",
                latencyMs = tcpResult.latencyMs,
                successPercent = (tcpResult.successPercent * 0.6).toInt(),
                samples = tcpResult.samples,
                jitterMs = tcpResult.jitterMs,
                tcpHandshakeMs = tcpResult.latencyMs,
                failureReason = "http-failed-tcp-ok"
            )
        }

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
        val rounds = samples.coerceIn(1, 8)
        val times = ArrayList<Double>(rounds)
        repeat(rounds) {
            val value = if (icmpMode) {
                icmp(profile.host, timeoutMs, settings)
            } else {
                tcp(profile.host, profile.port, timeoutMs, settings)
            }
            if (value < UNREACHABLE) times += value
        }
        if (times.isEmpty()) return Sample(0, UNREACHABLE)
        val sorted = times.sorted()
        return Sample(times.size * 100 / rounds, sorted[sorted.size / 2])
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
        ProbeMethod.HYBRID -> smartPing(profile, tunnelPort, timeoutMs, settings)
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
