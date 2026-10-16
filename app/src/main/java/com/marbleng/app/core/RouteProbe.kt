package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.DelayTest
import com.marbleng.app.model.PingBudget
import com.marbleng.app.model.ProbeMethod
import com.marbleng.app.model.ProxyProfile
import com.marbleng.app.model.pingSampleSpacingMs
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

object RouteProbe {
    const val UNREACHABLE = 99_999.0

    private const val FAST_FAILURE_RETRY_WINDOW_MS = 300L
    private const val FAST_FAILURE_RETRY_DELAY_MS = 120L

    private const val HTTP_RACE_GRACE_MS = 750L

    internal const val ICMP_MIN_WAIT_SEC = 1
    private const val ICMP_MAX_WAIT_SEC = 10

    private fun realDelayTargets(): List<String> {
        val pool = ProbeTargetPool.ordered(ProbeTargetPool.CDN_TARGETS)
        val literals = ProbeTargetPool.ordered(ProbeTargetPool.LITERAL_TARGETS)
        return pool.map { "https://${it.host}${it.path}" } +
            literals.take(2).map { "https://${it.host}${it.path}" }
    }

    private val DNS_TARGETS = listOf(
        "one.one.one.one",
        "dns.google",
        "dns.cloudflare.com"
    )

    private val dnsTargetCursor = AtomicInteger(0)

    internal fun nextDnsTarget(): String =
        DNS_TARGETS[Math.floorMod(dnsTargetCursor.getAndIncrement(), DNS_TARGETS.size)]

    data class Sample(val successPercent: Int, val latencyMs: Double)

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

        val injectedResetSuspected: Boolean = false,
        val silentTimeoutSuspected: Boolean = false,
        val survivalVerified: Boolean = false,
        val bytesTransferred: Long = 0L
    )

    fun verifyRouteSurvival(
        tunnelPort: Int,
        host: String = "1.1.1.1",
        timeoutMs: Int = 8_000,
        minBytes: Long = 20_480L
    ): SurvivalVerdict {
        if (tunnelPort <= 0) {
            return SurvivalVerdict(
                survived = false,
                handshakeOk = false,
                ttfbMs = 0.0,
                bytesTransferred = 0L,
                durationMs = 0L,
                injectedResetSuspected = false,
                silentTimeoutSuspected = false,
                reason = "no-live-tunnel"
            )
        }
        return runCatching {
            SocksHttpClient.verifySurvivalAndTransfer(
                port = tunnelPort,
                host = host,
                path = "/cdn-cgi/trace",
                timeoutMs = timeoutMs,
                minBytesRequired = minBytes
            )
        }.getOrElse {
            SurvivalVerdict(
                survived = false,
                handshakeOk = false,
                ttfbMs = 0.0,
                bytesTransferred = 0L,
                durationMs = 0L,
                injectedResetSuspected = it.message?.contains("reset", ignoreCase = true) == true,
                silentTimeoutSuspected = it is java.net.SocketTimeoutException,
                reason = it.message ?: it.javaClass.simpleName
            )
        }
    }

    private fun tcpOnce(
        host: String,
        port: Int,
        timeoutMs: Int,
        settings: AppSettings,
        resolved: List<InetAddress>? = null
    ): Double {
        val signal = MultiVectorReachability.probe(
            host = host,
            port = port,
            timeoutMs = timeoutMs,
            settings = settings,
            referenceRttMs = 0.0,
            resolved = resolved
        )
        return if (signal.verified && signal.timeToFirstByteMs != null) {
            signal.timeToFirstByteMs!!.coerceAtLeast(20.0)
        } else {
            UNREACHABLE
        }
    }

    fun tcp(
        host: String,
        port: Int,
        timeoutMs: Int,
        settings: AppSettings = AppSettings(),
        resolved: List<InetAddress>? = null
    ): Double {
        if (host.isBlank() || port !in 1..65535) return UNREACHABLE

        val firstStarted = System.nanoTime()
        val first = tcpOnce(host, port, timeoutMs, settings, resolved)
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

        return tcpOnce(host, port, timeoutMs, settings, resolved)
    }

    fun tcpConnect(
        host: String,
        port: Int,
        timeoutMs: Int,
        settings: AppSettings = AppSettings(),
        resolved: List<InetAddress>? = null
    ): Double {
        if (host.isBlank() || port !in 1..65535) return UNREACHABLE
        val budgetMs = timeoutMs.coerceIn(250, 30_000)
        val candidates = AddressFamilyPolicy.orderAddresses(
            resolved ?: resolveOnce(host, budgetMs, settings) ?: return UNREACHABLE,
            AddressFamilyPolicy.plan(settings = settings)
        )
        if (candidates.isEmpty()) return UNREACHABLE
        if (candidates.size == 1) {
            return rawTcpConnectOnce(candidates[0], port, budgetMs)
        }

        val pool = java.util.concurrent.Executors.newFixedThreadPool(minOf(candidates.size, 4))
        val completion = java.util.concurrent.ExecutorCompletionService<Double>(pool)
        val activeFutures = mutableListOf<java.util.concurrent.Future<Double>>()
        val deadlineNs = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(budgetMs.toLong())
        var fastest = UNREACHABLE

        fun dial(address: InetAddress, windowMs: Int) {
            activeFutures += completion.submit(java.util.concurrent.Callable {
                rawTcpConnectOnce(address, port, windowMs)
            })
        }

        try {
            dial(candidates[0], budgetMs)
            val happyEyeballsDelayMs = 200L
            var dialedCount = 1

            while (dialedCount < candidates.size && fastest >= UNREACHABLE) {
                val leftMs = TimeUnit.NANOSECONDS.toMillis(deadlineNs - System.nanoTime()).toInt()
                if (leftMs <= 0) break
                val completed = completion.poll(minOf(happyEyeballsDelayMs, leftMs.toLong()), TimeUnit.MILLISECONDS)
                if (completed != null) {
                    val res = runCatching { completed.get() }.getOrDefault(UNREACHABLE)
                    if (res < UNREACHABLE) {
                        fastest = res
                        break
                    }
                    if (dialedCount < candidates.size) {
                        dial(candidates[dialedCount], leftMs)
                        dialedCount++
                    }
                } else {
                    if (dialedCount < candidates.size) {
                        dial(candidates[dialedCount], leftMs)
                        dialedCount++
                    }
                }
            }

            while (fastest >= UNREACHABLE && dialedCount > 0) {
                val leftMs = TimeUnit.NANOSECONDS.toMillis(deadlineNs - System.nanoTime()).toInt()
                if (leftMs <= 0) break
                val completed = completion.poll(minOf(leftMs.toLong(), 200L), TimeUnit.MILLISECONDS) ?: continue
                dialedCount--
                val res = runCatching { completed.get() }.getOrDefault(UNREACHABLE)
                if (res < UNREACHABLE) {
                    fastest = res
                    break
                }
            }
        } finally {
            activeFutures.forEach { if (!it.isDone) it.cancel(true) }
            pool.shutdownNow()
        }
        return fastest
    }

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

    fun tcpConnectExtended(
        host: String,
        port: Int,
        timeoutMs: Int,
        samples: Int = 3,
        settings: AppSettings = AppSettings(),
        onSample: ((Double) -> Unit)? = null
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
            if (round > 0 && !pauseBetweenSamples(settings.pingSampleSpacingMs())) break
            attempts += 1

            val attemptTimeoutMs = if (times.isEmpty() && consecutiveFailures > 0) {
                minOf(timeoutMs, (timeoutMs / 2).coerceAtLeast(350))
            } else {
                timeoutMs
            }
            val value = tcpConnect(host, port, attemptTimeoutMs, settings, resolved)
            if (value < UNREACHABLE) {
                times += value
                runCatching { onSample?.invoke(value) }
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

    private fun pauseBetweenSamples(sampleSpacingMs: Long): Boolean = try {
        Thread.sleep(sampleSpacingMs)
        true
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        false
    }

    private const val CONSECUTIVE_FAILURES_BEFORE_ABANDON = 2

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

        val summarized = summarize("TCP", times, summary.signals.size.coerceAtLeast(1), warmupDiscarded = true)
        return summarized.copy(
            tlsHandshakeMs = summarized.latencyMs,
            injectedResetSuspected = flagged,
            silentTimeoutSuspected = silent,
            failureReason = if (flagged) "injected-reset-partial" else ""
        )
    }

    fun tcpExtended(
        host: String,
        port: Int,
        timeoutMs: Int,
        samples: Int = 3,
        settings: AppSettings = AppSettings()
    ): ProbeResult =
        reachabilityExtended(host, port, timeoutMs, samples, settings)

    fun icmp(
        host: String,
        timeoutMs: Int,
        settings: AppSettings = AppSettings()
    ): Double {
        if (host.isBlank()) return UNREACHABLE

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
            ?: return UNREACHABLE
        return runCatching {
            val process = ProcessBuilder(
                buildList {
                    add("/system/bin/ping")

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

            val average = Regex("time[=<]\\s*([\\d.]+)").find(output)
                ?.groupValues?.getOrNull(1)
                ?.toDoubleOrNull()
                ?: Regex("=\\s*[\\d.]+/([\\d.]+)/").find(output)
                    ?.groupValues?.getOrNull(1)
                    ?.toDoubleOrNull()

            average?.takeIf { it > 0.0 } ?: UNREACHABLE
        }.getOrDefault(UNREACHABLE)
    }

    fun icmpExtended(
        host: String,
        timeoutMs: Int,
        count: Int = 3,
        settings: AppSettings = AppSettings()
    ): ProbeResult {
        if (host.isBlank()) {
            return ProbeResult("ICMP", UNREACHABLE, 0, count, failureReason = "blank-host")
        }

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
            ?: return ProbeResult("ICMP", UNREACHABLE, 0, packets, failureReason = "dns-failed")

        return runCatching {
            val process = ProcessBuilder(
                buildList {
                    add("/system/bin/ping")
                    add("-n")
                    add("-c"); add(packets.toString())
                    add("-W"); add(seconds.toString())

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

            val lossMatch = Regex("(\\d+)%\\s*packet loss").find(output)
            val lossPercent = lossMatch?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: 100.0
            val received = Regex("(\\d+)\\s+(?:packets\\s+)?received").find(output)
                ?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0

            val summaryMatch = Regex("=\\s*([\\d.]+)/([\\d.]+)/([\\d.]+)/([\\d.]+)").find(output)

            val perPacket = Regex("time[=<]\\s*([\\d.]+)\\s*ms").findAll(output)
                .mapNotNull { it.groupValues[1].toDoubleOrNull() }
                .filter { it > 0.0 }
                .toList()

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
                    val jitter = ProbeTargetPool.staggerProbe()
                    if (jitter > 0L) {
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

                val responseCode = runCatching { connection.responseCode }.getOrDefault(-1)
                val firstByteMs = (System.nanoTime() - start) / 1e6

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

    fun httpPingBatch(
        socksPort: Int = 0,
        timeoutMs: Int = 5000,
        samples: Int = 3,
        targets: List<String> = realDelayTargets(),
        httpMethod: String = "GET",

        sampleSpacingMs: Long = PingBudget.SAMPLE_SPACING_MS
    ): ProbeResult {
        val rounds = PingBudget.samples(samples)
        val times = ArrayList<Double>(rounds)
        val handshakeTimes = ArrayList<Double>(rounds)
        var consecutiveFailures = 0

        var attempts = 0
        for (round in 0 until rounds) {
            if (round > 0 && !pauseBetweenSamples(sampleSpacingMs)) break
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

    fun dnsPing(
        host: String = "",
        timeoutMs: Int = 3000,
        resolver: (String) -> Array<InetAddress> = EncryptedEndpointResolver::resolve
    ): Double {
        val target = host.ifBlank { nextDnsTarget() }
        if (target.isBlank()) return UNREACHABLE
        val budgetMs = timeoutMs.coerceIn(250, 30_000)
        val start = System.nanoTime()
        val addresses = AddressFamilyPolicy.resolveWithBudget(target, budgetMs, resolver)
        val elapsed = (System.nanoTime() - start) / 1e6
        return if (addresses.isNotEmpty()) elapsed else UNREACHABLE
    }

    fun dnsPingExtended(
        host: String = "",
        timeoutMs: Int = 3000,
        samples: Int = 3
    ): ProbeResult {
        val rounds = PingBudget.samples(samples)
        val budgetMs = timeoutMs.coerceIn(250, 30_000)

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

    fun smartPing(
        profile: ProxyProfile,
        tunnelPort: Int = 0,
        timeoutMs: Int = 5000,
        settings: AppSettings = AppSettings(),
        samples: Int = 1
    ): ProbeResult {
        val host = profile.host.trim()
        if (host.isBlank() || profile.port !in 1..65535) {
            return ProbeResult("SMART", UNREACHABLE, 0, failureReason = "invalid-target")
        }

        val budgetMs = timeoutMs.coerceIn(
            PingBudget.TIMEOUT_MIN_SEC * 1_000,
            PingBudget.TIMEOUT_MAX_SEC * 1_000
        )
        val gateSamples = PingBudget.samples(samples)

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

        val hostNeedsDns = !AddressFamilyPolicy.isLiteralIp(host)
        val dnsMs = if (!tcpOk && hostNeedsDns) {
            dnsPing(host, gateTimeoutMs)
        } else {
            UNREACHABLE
        }
        val dnsOk = dnsMs < UNREACHABLE

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

        if (!tcpOk && !rawTcpOk && !dnsOk && tunnelPort <= 0) {
            return ProbeResult("SMART", UNREACHABLE, 0, failureReason = "gate-failed:tcp+dns")
        }

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

        val httpResult = httpPingBatch(
            socksPort = tunnelPort,
            timeoutMs = budgetMs,
            samples = PingBudget.samples(samples)
        )

        if (httpResult.successPercent > 0 && httpResult.latencyMs >= 20.0 && httpResult.latencyMs < UNREACHABLE) {
            return httpResult.copy(
                method = "SMART",
                tcpHandshakeMs = tcpResult.latencyMs.takeIf { it >= 20.0 && it < UNREACHABLE }
                    ?.coerceAtMost(httpResult.latencyMs) ?: 0.0
            )
        }

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

        return ProbeResult("SMART", UNREACHABLE, 0, failureReason = "all-methods-failed")
    }

    fun measure(
        profile: ProxyProfile,
        icmpMode: Boolean,
        samples: Int,
        timeoutMs: Int,
        settings: AppSettings = AppSettings()
    ): Sample {
        val rounds = PingBudget.samples(samples)

        val resolved = if (icmpMode) null else resolveOnce(profile.host, timeoutMs, settings)
        if (!icmpMode && resolved == null) return Sample(0, UNREACHABLE)
        val times = ArrayList<Double>(rounds)
        var attempts = 0
        var consecutiveFailures = 0
        for (round in 0 until rounds) {
            if (round > 0 && !pauseBetweenSamples(settings.pingSampleSpacingMs())) break
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

    fun measureUnified(
        profile: ProxyProfile,
        method: ProbeMethod,
        tunnelPort: Int = 0,
        samples: Int = 3,
        timeoutMs: Int = 5000,
        settings: AppSettings = AppSettings(),
        onSample: ((Double) -> Unit)? = null
    ): ProbeResult {
        if (AddressFamilyPolicy.excludedIpv6Endpoint(profile.host, settings)) {
            val label = when (method) {
                ProbeMethod.REAL_DELAY -> METHOD_REAL_DELAY
                ProbeMethod.TCP_PING -> METHOD_TCP_PING
                ProbeMethod.URL_TEST -> METHOD_URL_TEST
            }
            return ProbeResult(label, UNREACHABLE, 0, PingBudget.samples(samples),
                lossPercent = 100.0, failureReason = "ipv6-disabled")
        }
        return when (method) {
            ProbeMethod.REAL_DELAY -> realDelay(profile, tunnelPort, timeoutMs, samples, settings, onSample)
            ProbeMethod.TCP_PING -> tcpPing(profile, timeoutMs, samples, settings, onSample)
            ProbeMethod.URL_TEST -> urlTest(profile, timeoutMs, settings)
        }
    }

    fun realDelay(
        profile: ProxyProfile,
        tunnelPort: Int,
        timeoutMs: Int,
        samples: Int,
        settings: AppSettings = AppSettings(),
        onSample: ((Double) -> Unit)? = null
    ): ProbeResult {
        if (tunnelPort <= 0) {
            val hook = realDelayHook
                ?: return ProbeResult(
                    METHOD_REAL_DELAY, UNREACHABLE, 0, PingBudget.samples(samples),
                    lossPercent = 100.0, failureReason = "no-live-tunnel"
                )
            return runCatching { hook(profile, timeoutMs, samples, settings, onSample) }.getOrElse {
                if (it is InterruptedException) throw it
                ProbeResult(
                    METHOD_REAL_DELAY, UNREACHABLE, 0, PingBudget.samples(samples),
                    lossPercent = 100.0,
                    failureReason = (it.message ?: it::class.java.simpleName).take(120)
                )
            }
        }

        return tunnelHttpsMeasureTargets(
            socksPort = tunnelPort,
            timeoutMs = timeoutMs,
            samples = samples,
            urls = DelayTest.candidates(settings.delayTestUrl),
            sampleSpacingMs = settings.pingSampleSpacingMs(),
            onSample = onSample
        )
    }

    fun tcpPing(
        profile: ProxyProfile,
        timeoutMs: Int,
        samples: Int,
        settings: AppSettings = AppSettings(),
        onSample: ((Double) -> Unit)? = null
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
            settings = settings,
            onSample = onSample
        ).copy(method = METHOD_TCP_PING)
    }

    fun urlTest(
        profile: ProxyProfile,
        timeoutMs: Int,
        settings: AppSettings = AppSettings()
    ): ProbeResult {
        if (!ProbeMethod.URL_TEST.availableOn(settings.coreEngine())) {
            return ProbeResult(
                METHOD_URL_TEST, UNREACHABLE, 0, 1,
                lossPercent = 100.0, failureReason = URL_TEST_ENGINE_GATE
            )
        }
        val hook = urlTestHook
            ?: return ProbeResult(
                METHOD_URL_TEST, UNREACHABLE, 0, 1,
                lossPercent = 100.0, failureReason = "singbox-unavailable"
            )
        return runCatching { hook(profile, settings, timeoutMs) }.getOrElse {
            if (it is InterruptedException) throw it
            ProbeResult(
                METHOD_URL_TEST, UNREACHABLE, 0, 1,
                lossPercent = 100.0,
                failureReason = (it.message ?: it::class.java.simpleName).take(120)
            )
        }
    }

    const val URL_TEST_ENGINE_GATE = "urltest-requires-singbox"

    const val METHOD_REAL_DELAY = "REAL_DELAY"
    const val METHOD_TCP_PING = "TCP_PING"
    const val METHOD_URL_TEST = "URL_TEST"

    @Volatile
    var urlTestHook: ((ProxyProfile, AppSettings, Int) -> ProbeResult)? = null

    @Volatile
    var realDelayHook: ((ProxyProfile, Int, Int, AppSettings, ((Double) -> Unit)?) -> ProbeResult)? = null

    private fun gateApplies(profile: ProxyProfile): Boolean {
        val scheme = profile.scheme.lowercase()
        if (scheme in setOf("hysteria2", "hy2", "hysteria", "wireguard", "wg", "json")) return false
        if (profile.host.isBlank() || profile.port !in 1..65535) return false

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

    private fun endpointHttpsUrl(profile: ProxyProfile): String {
        val host = profile.host.trim()
        val literal = if (host.contains(':') && !host.startsWith("[")) "[$host]" else host
        return "https://$literal:${profile.port}/"
    }

    fun tunnelHttpsMeasureTargets(
        socksPort: Int,
        timeoutMs: Int,
        samples: Int,
        urls: List<String>,

        sampleSpacingMs: Long = PingBudget.SAMPLE_SPACING_MS,
        onSample: ((Double) -> Unit)? = null
    ): ProbeResult =
        ProbeTargetWalk.realDelay(urls) { url ->
            tunnelHttpsMeasure(
                socksPort = socksPort,
                timeoutMs = timeoutMs,
                samples = samples,
                url = url,
                sampleSpacingMs = sampleSpacingMs,
                onSample = onSample
            )
        }.copy(method = METHOD_REAL_DELAY)

    fun tunnelHttpsMeasure(
        socksPort: Int,
        timeoutMs: Int,
        samples: Int = 2,
        url: String = DelayTest.URL,

        sampleSpacingMs: Long = PingBudget.SAMPLE_SPACING_MS,
        onSample: ((Double) -> Unit)? = null
    ): ProbeResult {
        val rounds = PingBudget.samples(samples)
        val times = ArrayList<Double>(rounds)
        val budget = timeoutMs.coerceIn(500, 30_000)

        val attempts = if (rounds > 1) CONSECUTIVE_FAILURES_BEFORE_ABANDON else 1

        val published = ArrayList<Double>(rounds)
        var result: TunnelRttBatch? = null
        repeat(attempts) { attempt ->
            if (attempt > 0 && !pauseBetweenSamples(sampleSpacingMs)) return@repeat
            result = runCatching {
                SocksHttpClient.tunnelRttBatchUrl(
                    port = socksPort,
                    url = url,
                    samples = rounds,
                    timeoutMs = budget,
                    spacingMs = sampleSpacingMs,
                    onSample = { ms ->
                        if (ms.isFinite() && ms > 0.0 && published.size < rounds && ms !in published) {
                            published += ms
                        }
                        runCatching { onSample?.invoke(ms) }
                    }
                )
            }.getOrNull()
            if (result != null) return@repeat
        }

        val measured = result
        val landed = measured?.samplesMs.orEmpty().filter { it.isFinite() && it > 0.0 }
        times += if (landed.isNotEmpty()) landed else published
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
            failureReason = ""
        )
    }

    private fun standardDeviation(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        val variance = values.map { (it - mean) * (it - mean) }.average()
        return sqrt(variance)
    }

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
