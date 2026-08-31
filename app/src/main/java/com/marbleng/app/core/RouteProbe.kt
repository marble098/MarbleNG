package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.ProxyProfile
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * Endpoint reachability probes that do not need an Xray process.
 *
 * These measure the physical path to the server. They are fast and cheap, but they only prove that
 * the host answers — not that the proxy account works or that the route survives filtering. Only
 * [ProbeMethod.TUNNEL] can prove that, which is why the tunnel test stays the default for the
 * routes MarbleNG is about to use.
 */
object RouteProbe {
    // MARBLE_DIRECT_PING_RETRY_V33

    const val UNREACHABLE = 99_999.0
    // MARBLE_PROBE_RELIABILITY_V78 — wider retry window catches more transient failures
    private const val FAST_FAILURE_RETRY_WINDOW_MS = 300L
    private const val FAST_FAILURE_RETRY_DELAY_MS = 120L

    data class Sample(val successPercent: Int, val latencyMs: Double)

    private fun tcpOnce(
        host: String,
        port: Int,
        timeoutMs: Int,
        plan: IpFamilyPlan
    ): Double {
        // MARBLE_PROBE_RELIABILITY_V78 — resolve once, try all candidates to avoid single-address
        // transient failures on flaky links; keep time budget tight so we don't stall a wave.
        val candidates = AddressFamilyPolicy.resolveCandidates(host, plan)
        if (candidates.isEmpty()) return UNREACHABLE
        val started = System.nanoTime()
        // Per-address budget: share the total budget across candidates, min 300ms each
        val perAddressMs = (timeoutMs / candidates.size).coerceIn(300, timeoutMs)
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
     * TCP connect time to host:port. A genuine timeout stays failed.
     * Only an abnormally fast local/resolver/link failure gets one confirmation retry.
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
     * ICMP echo through the system binary. Android cannot open raw sockets without root, so this
     * shells out to /system/bin/ping, which is present on every stock image. Many hosts and most
     * mobile carriers drop ICMP, so a failure here is not proof that a node is dead.
     */
    fun icmp(
        host: String,
        timeoutMs: Int,
        settings: AppSettings = AppSettings()
    ): Double {
        if (host.isBlank()) return UNREACHABLE
        val seconds = (timeoutMs / 1000).coerceIn(1, 10)
        // Pick the family explicitly. Without this, ping dials whatever getaddrinfo returned first,
        // so an IPv6-capable node was measured on IPv4 and reported as if IPv6 were broken.
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

            // rtt min/avg/max/mdev = 41.316/41.316/41.316/0.000 ms
            val average = Regex("=\\s*[\\d.]+/([\\d.]+)/").find(output)
                ?.groupValues?.getOrNull(1)
                ?.toDoubleOrNull()
                ?: Regex("time=([\\d.]+)").find(output)
                    ?.groupValues?.getOrNull(1)
                    ?.toDoubleOrNull()

            average?.takeIf { it > 0.0 } ?: UNREACHABLE
        }.getOrDefault(UNREACHABLE)
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
}
