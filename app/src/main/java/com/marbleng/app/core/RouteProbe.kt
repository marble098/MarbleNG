package com.marbleng.app.core

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

    const val UNREACHABLE = 99_999.0

    data class Sample(val successPercent: Int, val latencyMs: Double)

    /** TCP connect time to host:port, the classic "tcping". */
    fun tcp(host: String, port: Int, timeoutMs: Int): Double {
        if (host.isBlank() || port !in 1..65535) return UNREACHABLE
        val started = System.nanoTime()
        return try {
            Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) }
            (System.nanoTime() - started) / 1e6
        } catch (_: Throwable) {
            UNREACHABLE
        }
    }

    /**
     * ICMP echo through the system binary. Android cannot open raw sockets without root, so this
     * shells out to /system/bin/ping, which is present on every stock image. Many hosts and most
     * mobile carriers drop ICMP, so a failure here is not proof that a node is dead.
     */
    fun icmp(host: String, timeoutMs: Int): Double {
        if (host.isBlank()) return UNREACHABLE
        val seconds = (timeoutMs / 1000).coerceIn(1, 10)
        return runCatching {
            val process = ProcessBuilder(
                "/system/bin/ping", "-n", "-q", "-c", "1", "-W", seconds.toString(), host
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
        timeoutMs: Int
    ): Sample {
        val rounds = samples.coerceIn(1, 8)
        val times = ArrayList<Double>(rounds)
        repeat(rounds) {
            val value = if (icmpMode) {
                icmp(profile.host, timeoutMs)
            } else {
                tcp(profile.host, profile.port, timeoutMs)
            }
            if (value < UNREACHABLE) times += value
        }
        if (times.isEmpty()) return Sample(0, UNREACHABLE)
        val sorted = times.sorted()
        return Sample(times.size * 100 / rounds, sorted[sorted.size / 2])
    }
}
