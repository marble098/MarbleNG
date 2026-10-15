package com.marbleng.app.core

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

object EncryptedEndpointResolver {
    private val familyWorkers = ThreadPoolExecutor(
        4, 4, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue<Runnable>(8),
        { task -> Thread(task, "marble-endpoint-family").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy()
    )
    private val dohWorkers = ThreadPoolExecutor(
        6, 6, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue<Runnable>(24),
        { task -> Thread(task, "marble-endpoint-doh").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy()
    )
    private val productionPool = DohResolverPool(HttpUrlConnectionDohTransport(), dohWorkers, 3_000)
    @Volatile private var poolOverride: DohResolverPool? = null

    private fun resolverPool(): DohResolverPool = poolOverride ?: productionPool

    internal fun setPoolOverrideForTests(pool: DohResolverPool?) {
        poolOverride = pool
    }

    private fun providers(): List<DohResolverPool.Provider> {
        val v4 = listOf(
            DohResolverPool.Provider("cloudflare-v4", "https://1.1.1.1/dns-query"),
            DohResolverPool.Provider("google-v4", "https://8.8.8.8/dns-query"),
            DohResolverPool.Provider("quad9-v4", "https://9.9.9.9/dns-query")
        )
        val v6 = listOf(
            DohResolverPool.Provider("cloudflare-v6", "https://[2606:4700:4700::1111]/dns-query"),
            DohResolverPool.Provider("google-v6", "https://[2001:4860:4860::8888]/dns-query")
        )
        return if (AddressFamilyPolicy.underlayHasIpv6()) v6 + v4 else v4
    }

    fun resolve(host: String): Array<InetAddress> = resolveWith(host) { wire ->
        runCatching { resolverPool().raceResolve(wire, providers(), 2_500) }
            .getOrNull()?.takeIf { it.success }?.body
    }

    fun resolveAll(host: String, timeoutMs: Long = FAMILY_BUDGET_MS): Array<InetAddress> {
        val clean = host.trim().removeSurrounding("[", "]")
        if (clean.isBlank()) return emptyArray()
        if (AddressFamilyPolicy.isLiteralIp(clean)) {
            return runCatching { arrayOf(InetAddress.getByName(clean)) }.getOrDefault(emptyArray())
        }
        val budget = timeoutMs.coerceIn(500L, 15_000L)
        val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(budget)
        val answers = linkedSetOf<InetAddress>()
        val completion = ExecutorCompletionService<Pair<Int, List<InetAddress>>>(familyWorkers)
        val pending = linkedSetOf<java.util.concurrent.Future<Pair<Int, List<InetAddress>>>>()
        try {
            for (type in listOf(28, 1)) {
                try {
                    pending += completion.submit {
                        type to queryFamily(clean, type, deadlineNanos)
                    }
                } catch (_: RejectedExecutionException) {
                }
            }
            while (pending.isNotEmpty()) {
                val leftNanos = deadlineNanos - System.nanoTime()
                if (leftNanos <= 0L) break
                val finished = try {
                    completion.poll(leftNanos, TimeUnit.NANOSECONDS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                } ?: break
                pending.remove(finished)
                val pair = runCatching { finished.get() }.getOrNull() ?: continue
                val (type, found) = pair
                answers.addAll(found.filter { if (type == 28) it is Inet6Address else it is Inet4Address })
            }
        } finally {
            pending.forEach { it.cancel(true) }
        }
        return answers.toTypedArray()
    }

    private fun queryFamily(clean: String, type: Int, deadlineNanos: Long): List<InetAddress> {
        val wire = runCatching { DnsWireCodec.buildQuery(clean, type) }.getOrNull() ?: return emptyList()
        val found = linkedSetOf<InetAddress>()
        var witnesses = 0
        for (provider in providers().take(MAX_WITNESS_PROVIDERS)) {
            if (witnesses >= MIN_WITNESS_PROVIDERS && found.size >= MIN_WITNESS_ADDRESSES) break
            val leftNanos = deadlineNanos - System.nanoTime()
            if (leftNanos <= 0L) break
            if (Thread.currentThread().isInterrupted) break
            val leftMs = TimeUnit.NANOSECONDS.toMillis(leftNanos).coerceAtLeast(1L)
            val body = runCatching {
                resolverPool().raceResolve(
                    wire,
                    listOf(provider),
                    minOf(leftMs, PER_WITNESS_TIMEOUT_MS)
                )
            }.getOrNull()?.takeIf { it.success }?.body ?: continue
            val parsed = DnsWireCodec.parseAnswers(body)
                .filter { if (type == 28) it is Inet6Address else it is Inet4Address }
            if (parsed.isNotEmpty()) witnesses += 1
            found.addAll(parsed)
        }
        return found.toList()
    }

    const val PER_WITNESS_TIMEOUT_MS: Long = 900L

    const val MAX_WITNESS_PROVIDERS = 3
    const val MIN_WITNESS_PROVIDERS = 2
    const val MIN_WITNESS_ADDRESSES = 2

    const val FAMILY_BUDGET_MS: Long = 3_000L

    internal fun resolveWith(host: String, query: (ByteArray) -> ByteArray?): Array<InetAddress> {
        val clean = host.trim().removeSurrounding("[", "]")
        if (clean.isBlank()) return emptyArray()
        if (AddressFamilyPolicy.isLiteralIp(clean)) {
            return runCatching { arrayOf(InetAddress.getByName(clean)) }.getOrDefault(emptyArray())
        }
        val answers = linkedSetOf<InetAddress>()

        for (type in listOf(28, 1)) {
            if (Thread.currentThread().isInterrupted) break
            val response = runCatching { query(DnsWireCodec.buildQuery(clean, type)) }.getOrNull()
                ?: continue
            DnsWireCodec.parseAnswers(response).filterTo(answers) { address ->
                if (type == 28) address is Inet6Address else address is Inet4Address
            }
        }
        return answers.toTypedArray()
    }
}
