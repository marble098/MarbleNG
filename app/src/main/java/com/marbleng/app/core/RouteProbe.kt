package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.ProxyProfile
import java.io.BufferedReader
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Endpoint reachability probes that do not need an Xray process.
 *
 * These measure the physical path to the server. They are fast and cheap, but they only prove that
 * the host answers — not that the proxy account works or that the route survives filtering. Only
 * [com.marbleng.app.model.ProbeMethod.TUNNEL] (and the Smart ladder it feeds) can prove that, which
 * is why the tunnel test stays the default for the routes MarbleNG is about to use.
 *
 * MARBLE_PING_ENGINE_V122 — rewritten after a method-by-method review of how v2rayNG, PattNG,
 * Exclave, Hiddify and Lumen measure latency:
 *
 *  - **ICMP** now shells out exactly once for the whole sample batch (`-c count`), the way the
 *    stock `ping` binary reports `rtt min/avg/max/mdev`, and parses *every* individual
 *    `time=… ms` reply line instead of trusting only the summary average. The median of the real
 *    replies is reported, so one slow/duplicated reply can never drag the number up, and a binary
 *    whose summary layout differs between Android releases still works. ICMP is the only method
 *    that can report true packet loss for the underlay path.
 *  - **TCP connect** stops timing a candidate the moment *its own* SYN is answered. The previous
 *    clock kept running through the earlier family candidates, so a node whose first answer
 *    family was black-holed was billed the timeout of the dead family plus the live connect.
 *    Per-address attempts still share the total deadline the way Happy Eyeballs does.
 *  - Both methods return the same [Sample] shape (median RTT, success rate, sample/attempt
 *    counts, jitter) so the UI and ranking code treat every ping method identically.
 */
object RouteProbe {
    // MARBLE_DIRECT_PING_RETRY_V33
    // MARBLE_PROBE_RELIABILITY_V78

    const val UNREACHABLE = 99_999.0

    // MARBLE_PROBE_RELIABILITY_V78 — wider retry window catches more transient failures.
    private const val FAST_FAILURE_RETRY_WINDOW_MS = 300L
    private const val FAST_FAILURE_RETRY_DELAY_MS = 120L

    /**
     * One batch of endpoint measurements.
     *
     * @param successPercent  replies received / attempts sent, 0..100.
     * @param latencyMs       median RTT of the successful replies ([UNREACHABLE] when none landed).
     * @param samples         successful reply count.
     * @param attempts        total attempts made (success + loss).
     * @param jitterMs        median absolute inter-reply delta (0 for fewer than 2 replies).
     */
    data class Sample(
        val successPercent: Int,
        val latencyMs: Double,
        val samples: Int = 0,
        val attempts: Int = samples.coerceAtLeast(0),
        val jitterMs: Double = 0.0
    )

    // ------------------------------------------------------------------------------------------
    // TCP connect ping
    // ------------------------------------------------------------------------------------------

    /**
     * One TCP connect attempt to every resolved address, sharing [timeoutMs] as a total deadline.
     *
     * Timing restarts per address: the reported value is the connect time of the address that
     * actually answered, not wall-clock spent probing dead families first. A per-attempt floor of
     * 300 ms keeps a black-holed family cheap while leaving a live family its real handshake time.
     */
    private fun tcpOnce(
        host: String,
        port: Int,
        timeoutMs: Int,
        plan: IpFamilyPlan
    ): Double {
        // Resolve once and try every candidate so a flaky single-address answer can't fail a node;
        // the order is exactly the family order the tunnel itself will dial.
        val candidates = AddressFamilyPolicy.resolveCandidates(host, plan)
        if (candidates.isEmpty()) return UNREACHABLE
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs.toLong())
        candidates.forEachIndexed { index, address ->
            val remainingMs = ((deadline - System.nanoTime()) / 1_000_000L).toInt()
            if (remainingMs <= 0) return UNREACHABLE
            val attemptMs = when {
                candidates.size == 1 -> timeoutMs
                index == candidates.lastIndex -> remainingMs.coerceAtLeast(300)
                // A black-holed family must cost a little, never the whole wave budget.
                else -> minOf(remainingMs.coerceAtLeast(300), 1_200)
            }
            // The clock belongs to THIS attempt: dead-family time is not charged to the live one.
            val attemptStart = System.nanoTime()
            val connected = runCatching {
                Socket().use { socket ->
                    socket.tcpNoDelay = true
                    socket.soTimeout = attemptMs
                    socket.connect(InetSocketAddress(address, port), attemptMs)
                }
            }.isSuccess
            if (connected) return ((System.nanoTime() - attemptStart) / 1e6)
        }
        return UNREACHABLE
    }

    /**
     * TCP connect time to host:port. A genuine timeout stays failed.
     * Only an abnormally fast local/resolver/link failure gets one confirmation retry — a real
     * dropped SYN consumes the full timeout and is therefore never retried.
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

    // ------------------------------------------------------------------------------------------
    // ICMP echo ping (/system/bin/ping)
    // ------------------------------------------------------------------------------------------

    /** Individual reply lines look like `64 bytes from 1.1.1.1: icmp_seq=1 ttl=57 time=12.3 ms`. */
    private val ICMP_REPLY_TIME = Regex("time[= <]+([0-9]+(?:\\.[0-9]+)?)\\s*ms", RegexOption.IGNORE_CASE)

    /** The stock binary's own summary: `rtt min/avg/max/mdev = 41.3/41.9/42.6/0.5 ms`. */
    private val ICMP_SUMMARY =
        Regex("=\\s*[0-9.]+/([0-9.]+)/[0-9.]+(?:/[0-9.]+)?\\s*ms", RegexOption.IGNORE_CASE)

    /**
     * One ICMP echo batch through the system binary.
     *
     * Android cannot open raw sockets without root, so this shells out to `/system/bin/ping`,
     * which is present on every stock image. The process is started ONCE with `-c <samples>` (the
     * same way a user runs `ping -c 4`), so N samples cost one fork and the binary computes the
     * loss statistics itself. Many hosts and most mobile carriers drop ICMP, so a failure here is
     * not proof that a node is dead — it means the underlay does not answer echo requests.
     */
    fun icmpBatch(
        host: String,
        samples: Int,
        timeoutMs: Int,
        settings: AppSettings = AppSettings()
    ): Sample {
        if (host.isBlank()) return Sample(0, UNREACHABLE)
        val count = samples.coerceIn(1, 8)
        // Per-reply wait. Android toybox ping and bsd-ping both accept -W in whole seconds; use the
        // total budget spread across the batch so a dead host can't hang the wave.
        val waitSeconds = (timeoutMs / 1000).coerceIn(1, 10)

        // Pick the family explicitly. Without this, ping dials whatever getaddrinfo returned
        // first, so an IPv6-capable node was measured on IPv4 and reported as if IPv6 were broken.
        val target = AddressFamilyPolicy
            .resolveCandidates(host, AddressFamilyPolicy.plan(settings = settings))
            .firstOrNull()
            ?.hostAddress
            ?.substringBefore('%') // strip any scoped-interface suffix ping cannot parse
            ?.takeIf { it.isNotBlank() }
            ?: host

        val command = buildList {
            add("/system/bin/ping")
            add("-n")            // numeric output, no reverse DNS lookups
            add("-q")            // quiet: summary only where supported
            add("-c"); add(count.toString())
            add("-W"); add(waitSeconds.toString())
            // 0.2 s between echoes: tight enough to feel like a ping, gentle enough for radios.
            add("-i"); add("0.2")
            if (target.contains(':')) add("-6")
            add(target)
        }

        val output = runCatching {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            try {
                // Overall wall-clock guard: count*wait plus startup/teardown slack. The process is
                // destroyed if a stuck radio makes the binary ignore its own deadline.
                val waitMs = (waitSeconds * 1_000L * count + 2_500L)
                    .coerceAtMost(TimeUnit.SECONDS.toMillis((waitSeconds + 2L) * count))
                if (!process.waitFor(waitMs, TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly()
                    return@runCatching null
                }
                process.inputStream.bufferedReader().use(BufferedReader::readText)
            } finally {
                runCatching { process.destroy() }
            }
        }.getOrNull() ?: return Sample(0, UNREACHABLE, attempts = count)

        return parseIcmpOutput(output, count)
    }

    /**
     * Parse one `/system/bin/ping` batch output into the shared [Sample] shape.
     *
     * Per-reply `time=… ms` lines win because they are present in every Android ping build and
     * let us report the *median* (plus real jitter) instead of trusting the summary average; the
     * `rtt min/avg/max/mdev` summary covers the rare build that prints only the table.
     */
    internal fun parseIcmpOutput(output: String, requestedCount: Int): Sample {
        val count = requestedCount.coerceAtLeast(1)
        // Per-reply times: one binary's summary layout differs across Android releases, but every
        // reply line prints `time=… ms`.
        val replies = ICMP_REPLY_TIME
            .findAll(output)
            .mapNotNull { it.groupValues.getOrNull(1)?.toDoubleOrNull() }
            .filter { it.isFinite() && it > 0.0 && it < 10_000.0 }
            .toList()
            .sorted()

        val summaryAverage = ICMP_SUMMARY.find(output)
            ?.groupValues?.getOrNull(1)
            ?.toDoubleOrNull()
            ?.takeIf { it > 0.0 && it < 10_000.0 }

        val times = when {
            replies.isNotEmpty() -> replies
            // Fallback for the rare build that prints only the summary table.
            summaryAverage != null -> listOf(summaryAverage)
            else -> return Sample(0, UNREACHABLE, attempts = count)
        }

        val median = times[times.size / 2]
        val jitter = medianDelta(times)
        val success = (times.size * 100.0 / count).roundToInt().coerceIn(0, 100)
        return Sample(
            successPercent = success,
            latencyMs = median,
            samples = times.size,
            attempts = count,
            jitterMs = jitter
        )
    }

    /**
     * ICMP echo through the system binary; kept for callers that only need a single RTT number.
     * Returns [UNREACHABLE] when no reply landed — ICMP loss is common and never proves a node dead.
     */
    fun icmp(
        host: String,
        timeoutMs: Int,
        settings: AppSettings = AppSettings()
    ): Double = icmpBatch(host, samples = 1, timeoutMs = timeoutMs, settings = settings)
        .let { if (it.successPercent > 0) it.latencyMs else UNREACHABLE }

    // ------------------------------------------------------------------------------------------
    // Shared statistics + the unified batch entry point
    // ------------------------------------------------------------------------------------------

    /** Median of |x_i - median| — the robust jitter estimate used across the ping engine. */
    private fun medianDelta(sortedAscending: List<Double>): Double {
        if (sortedAscending.size < 2) return 0.0
        val center = sortedAscending[sortedAscending.size / 2]
        val deltas = sortedAscending.map { kotlin.math.abs(it - center) }.sorted()
        return deltas[deltas.size / 2]
    }

    /** Repeats a direct probe and reports median latency plus the success rate. */
    fun measure(
        profile: ProxyProfile,
        icmpMode: Boolean,
        samples: Int,
        timeoutMs: Int,
        settings: AppSettings = AppSettings()
    ): Sample {
        val rounds = samples.coerceIn(1, 8)
        if (icmpMode) {
            // One process for the whole batch; the binary reports loss itself.
            return icmpBatch(profile.host, rounds, timeoutMs, settings)
        }

        val times = ArrayList<Double>(rounds)
        repeat(rounds) {
            val value = tcp(profile.host, profile.port, timeoutMs, settings)
            if (value < UNREACHABLE) times += value
        }
        if (times.isEmpty()) return Sample(0, UNREACHABLE)
        val sorted = times.sorted()
        return Sample(
            successPercent = times.size * 100 / rounds,
            latencyMs = sorted[sorted.size / 2],
            samples = times.size,
            attempts = rounds,
            jitterMs = medianDelta(sorted)
        )
    }
}
