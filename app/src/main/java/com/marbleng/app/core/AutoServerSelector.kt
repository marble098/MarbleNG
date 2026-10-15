package com.marbleng.app.core

import com.marbleng.app.model.AutoServerScope
import com.marbleng.app.model.AutoServerStrategy
import com.marbleng.app.model.BenchmarkResult
import com.marbleng.app.model.ProxyProfile
import kotlin.math.abs
import kotlin.math.max

data class ServerCandidate(
    val profile: ProxyProfile,
    val benchmark: BenchmarkResult? = null,
    val failureStreak: Int = 0,
    val current: Boolean = false
) {
    val id: String get() = profile.id
}

data class ServerScore(
    val candidate: ServerCandidate,
    val score: Double,
    val reason: String
) {
    val profile: ProxyProfile get() = candidate.profile
}

data class AutoServerChoice(
    val profile: ProxyProfile?,
    val strategy: AutoServerStrategy,
    val reason: String,
    val held: Boolean = false,
    val ranking: List<ServerScore> = emptyList()
)

data class AutoServerWeights(
    val ping: Int = 40,
    val load: Int = 30,
    val stability: Int = 20,
    val freshness: Int = 10
) {
    fun clamped(): AutoServerWeights = copy(
        ping = ping.coerceIn(0, 100),
        load = load.coerceIn(0, 100),
        stability = stability.coerceIn(0, 100),
        freshness = freshness.coerceIn(0, 100)
    )

    companion object {
        val BALANCED = AutoServerWeights()
        val PING_FIRST = AutoServerWeights(ping = 70, load = 10, stability = 10, freshness = 10)
        val LOAD_FIRST = AutoServerWeights(ping = 15, load = 60, stability = 15, freshness = 10)
        val STABILITY_FIRST = AutoServerWeights(ping = 20, load = 15, stability = 55, freshness = 10)
    }
}

object AutoServerSelector {
    const val REFERENCE_LATENCY_MS = 160.0

    const val REFERENCE_JITTER_MS = 45.0

    const val REFERENCE_THROUGHPUT_BPS = 2_500_000.0

    const val EVIDENCE_MAX_AGE_MS = 45L * 60L * 1000L

    const val SWITCH_MARGIN = 0.15

    const val QUARANTINE_STREAK = 4

    fun latencyScore(latencyMs: Double): Double =
        if (!latencyMs.isFinite() || latencyMs <= 0.0) 0.0
        else (1.0 / (1.0 + latencyMs / REFERENCE_LATENCY_MS)).coerceIn(0.0, 1.0)

    fun jitterScore(jitterMs: Double): Double =
        if (!jitterMs.isFinite() || jitterMs < 0.0) 0.5
        else (1.0 / (1.0 + jitterMs / REFERENCE_JITTER_MS)).coerceIn(0.0, 1.0)

    fun lossScore(lossPercent: Double): Double =
        if (!lossPercent.isFinite()) 0.5
        else (1.0 - (lossPercent.coerceIn(0.0, 100.0) / 100.0) * 3.0).coerceIn(0.0, 1.0)

    fun throughputScore(bytesPerSecond: Double): Double =
        if (!bytesPerSecond.isFinite() || bytesPerSecond <= 0.0) 0.0
        else (bytesPerSecond / REFERENCE_THROUGHPUT_BPS).coerceAtMost(1.0)

    fun loadScore(idleLatencyMs: Double, loadedLatencyMs: Double, throughputBps: Double): Double {
        val idle = idleLatencyMs.takeIf { it.isFinite() && it > 0.0 }
        val loaded = loadedLatencyMs.takeIf { it.isFinite() && it > 0.0 }
        val congestion = if (idle != null && loaded != null) {
            val ratio = (loaded / idle).coerceIn(1.0, 8.0)

            (1.0 - (ratio - 1.0) / 7.0).coerceIn(0.0, 1.0)
        } else {
            throughputScore(throughputBps) * 0.8 + 0.1
        }
        return (congestion * 0.65 + throughputScore(throughputBps) * 0.35).coerceIn(0.0, 1.0)
    }

    fun freshnessScore(measuredAtMs: Long, nowMs: Long): Double {
        if (measuredAtMs <= 0L || nowMs <= 0L) return 0.5
        val age = (nowMs - measuredAtMs).coerceAtLeast(0L)
        if (age >= EVIDENCE_MAX_AGE_MS) return 0.25
        return (1.0 - 0.75 * (age.toDouble() / EVIDENCE_MAX_AGE_MS.toDouble())).coerceIn(0.25, 1.0)
    }

    fun smartScore(candidate: ServerCandidate, nowMs: Long): Pair<Double, String> {
        val b = candidate.benchmark
        if (b == null || b.success <= 0) {
            return 0.0 to "never measured"
        }
        val fresh = freshnessScore(b.measuredAtMs, nowMs)
        val latency = latencyScore(b.latencyMs)
        val jitter = jitterScore(b.jitterMs)
        val loss = lossScore(b.lossPercent)
        val load = loadScore(b.latencyMs, b.loadedLatencyMs, b.bytesPerSecond)
        val throughput = throughputScore(b.bytesPerSecond)
        val handshake = b.tcpHandshakeSuccessRatio.takeIf { it > 0.0 } ?: 1.0

        val composite =
            latency * 0.26 +
                load * 0.24 +
                jitter * 0.14 +
                loss * 0.14 +
                throughput * 0.12 +
                handshake * 0.10

        val penalty = when {
            candidate.failureStreak >= QUARANTINE_STREAK -> 0.0
            candidate.failureStreak > 0 -> (1.0 - candidate.failureStreak * 0.22).coerceIn(0.05, 1.0)
            else -> 1.0
        }
        val score = (composite * fresh * penalty).coerceIn(0.0, 1.0)
        val reason = buildString {
            append("${b.latencyMs.toInt()} ms")
            if (b.jitterMs > 0.0) append(" • ${b.jitterMs.toInt()} ms jitter")
            if (b.lossPercent > 0.0) append(" • ${b.lossPercent.toInt()}% loss")
            if (b.loadedLatencyMs > 0.0) append(" • ${b.loadedLatencyMs.toInt()} ms loaded")
            if (candidate.failureStreak > 0) append(" • ${candidate.failureStreak} failures")
        }
        return score to reason
    }

    fun customScore(
        candidate: ServerCandidate,
        nowMs: Long,
        weights: AutoServerWeights
    ): Pair<Double, String> {
        val b = candidate.benchmark
        if (b == null || b.success <= 0) return 0.0 to "measure first"

        val safe = weights.clamped()
        val total = safe.ping + safe.load + safe.stability + safe.freshness
        val effective = if (total == 0) AutoServerWeights.BALANCED else safe
        val effectiveTotal = effective.ping + effective.load + effective.stability + effective.freshness
        val ping = latencyScore(b.latencyMs)
        val load = loadScore(b.latencyMs, b.loadedLatencyMs, b.bytesPerSecond)
        val handshake = b.tcpHandshakeSuccessRatio.takeIf { it > 0.0 } ?: 1.0
        val stability = (
            jitterScore(b.jitterMs) * 0.45 +
                lossScore(b.lossPercent) * 0.40 +
                handshake * 0.15
            ).coerceIn(0.0, 1.0)
        val freshness = freshnessScore(b.measuredAtMs, nowMs)
        val weighted = (
            ping * effective.ping +
                load * effective.load +
                stability * effective.stability +
                freshness * effective.freshness
            ) / effectiveTotal.toDouble()
        val penalty = when {
            candidate.failureStreak >= QUARANTINE_STREAK -> 0.0
            candidate.failureStreak > 0 -> (1.0 - candidate.failureStreak * 0.22).coerceIn(0.05, 1.0)
            else -> 1.0
        }
        val score = (weighted * penalty).coerceIn(0.0, 1.0)
        val reason = buildString {
            append("${b.latencyMs.toInt()} ms ping")
            append(" • load ${(load * 100).toInt()}%")
            append(" • stability ${(stability * 100).toInt()}%")
            if (candidate.failureStreak > 0) append(" • ${candidate.failureStreak} recent failures")
        }
        return score to reason
    }

    fun leastLoadScore(candidate: ServerCandidate): Pair<Double, String> {
        val b = candidate.benchmark
        if (b == null || b.success <= 0) return 0.0 to "never measured"
        val load = loadScore(b.latencyMs, b.loadedLatencyMs, b.bytesPerSecond)
        val latency = latencyScore(b.latencyMs)
        val score = (load * 0.75 + latency * 0.25).coerceIn(0.0, 1.0)
        return score to "load ${(load * 100).toInt()}% • ${b.latencyMs.toInt()} ms"
    }

    fun leastPingScore(candidate: ServerCandidate): Pair<Double, String> {
        val b = candidate.benchmark
        if (b == null || b.success <= 0) return 0.0 to "never measured"
        if (b.lossPercent >= 34.0) {
            return 0.0 to "${b.latencyMs.toInt()} ms but ${b.lossPercent.toInt()}% loss"
        }
        val jitter = jitterScore(b.jitterMs)
        val score = (latencyScore(b.latencyMs) * 0.8 + jitter * 0.2).coerceIn(0.0, 1.0)
        return score to "${b.latencyMs.toInt()} ms"
    }

    fun eligible(candidate: ServerCandidate): Boolean {
        if (candidate.failureStreak >= QUARANTINE_STREAK) return false
        val b = candidate.benchmark ?: return true

        return b.success > 0
    }

    fun choose(
        candidates: List<ServerCandidate>,
        strategy: AutoServerStrategy,
        nowMs: Long = System.currentTimeMillis(),
        roundRobinCursor: Int = 0,
        randomSeed: Long = 0L,
        switchMarginPercent: Int = 15,
        scope: AutoServerScope = AutoServerScope.SOURCE,
        weights: AutoServerWeights = AutoServerWeights()
    ): AutoServerChoice {
        if (candidates.isEmpty()) {
            return AutoServerChoice(
                profile = null,
                strategy = strategy,
                reason = "no ${scope.id} servers to choose from"
            )
        }

        val measurementDriven = strategy in setOf(
            AutoServerStrategy.SMART,
            AutoServerStrategy.LEAST_PING,
            AutoServerStrategy.LEAST_LOAD,
            AutoServerStrategy.CUSTOM
        )
        val measured = candidates.filter { candidate -> (candidate.benchmark?.success ?: 0) > 0 }
        val healthyMeasured = measured.filter { it.failureStreak < QUARANTINE_STREAK }
        val pool = when {
            measurementDriven && healthyMeasured.isNotEmpty() -> healthyMeasured

            measurementDriven && measured.isNotEmpty() -> measured
            measurementDriven -> return AutoServerChoice(
                profile = null,
                strategy = strategy,
                reason = "no measured servers • ping the pool first"
            )
            else -> candidates.filter(::eligible).ifEmpty { candidates }
        }

        when (strategy) {
            AutoServerStrategy.ROUND_ROBIN -> {
                val ordered = pool.sortedBy { it.id }
                val index = ((roundRobinCursor % ordered.size) + ordered.size) % ordered.size
                val picked = ordered[index]
                return AutoServerChoice(
                    profile = picked.profile,
                    strategy = strategy,
                    reason = "rotation slot ${index + 1} of ${ordered.size}",
                    held = picked.current,
                    ranking = ordered.map {
                        ServerScore(it, 0.0, "rotation slot ${ordered.indexOf(it) + 1}")
                    }
                )
            }

            AutoServerStrategy.RANDOM -> {
                val ordered = pool.sortedBy { it.id }
                if (ordered.size == 1) {
                    return AutoServerChoice(
                        profile = ordered.first().profile,
                        strategy = strategy,
                        reason = "only one server in this pool"
                    )
                }

                val draw = if (randomSeed == 0L) {
                    ordered.indices.random()
                } else {
                    val mixed = (randomSeed xor (randomSeed ushr 32)) * -7046029254386353131L
                    val positive = if (mixed == Long.MIN_VALUE) 0L else abs(mixed)
                    (positive % ordered.size.toLong()).toInt()
                }
                val picked = ordered[draw]
                return AutoServerChoice(
                    profile = picked.profile,
                    strategy = strategy,
                    reason = "random draw ${draw + 1} of ${ordered.size}",
                    held = picked.current
                )
            }

            AutoServerStrategy.LEAST_PING -> {
                val ranked = pool
                    .map { candidate ->
                        val (score, reason) = leastPingScore(candidate)
                        ServerScore(candidate, score, reason)
                    }
                    .sortedWith(compareByDescending<ServerScore> { it.score }
                        .thenBy { it.profile.name })
                return settle(ranked, strategy, switchMarginPercent, "fastest")
            }

            AutoServerStrategy.LEAST_LOAD -> {
                val ranked = pool
                    .map { candidate ->
                        val (score, reason) = leastLoadScore(candidate)
                        ServerScore(candidate, score, reason)
                    }
                    .sortedWith(compareByDescending<ServerScore> { it.score }
                        .thenBy { it.profile.name })
                return settle(ranked, strategy, switchMarginPercent, "least loaded")
            }

            AutoServerStrategy.CUSTOM -> {
                val ranked = pool
                    .map { candidate ->
                        val (score, reason) = customScore(candidate, nowMs, weights)
                        ServerScore(candidate, score, reason)
                    }
                    .sortedWith(compareByDescending<ServerScore> { it.score }
                        .thenBy { it.profile.name })
                return settle(ranked, strategy, switchMarginPercent, "custom weighted")
            }

            AutoServerStrategy.SMART -> {
                val ranked = pool
                    .map { candidate ->
                        val (score, reason) = smartScore(candidate, nowMs)
                        ServerScore(candidate, score, reason)
                    }
                    .sortedWith(compareByDescending<ServerScore> { it.score }
                        .thenBy { it.profile.name })
                return settle(ranked, strategy, switchMarginPercent, "best scored")
            }
        }
    }

    private fun settle(
        ranked: List<ServerScore>,
        strategy: AutoServerStrategy,
        switchMarginPercent: Int,
        label: String
    ): AutoServerChoice {
        val best = ranked.firstOrNull()
            ?: return AutoServerChoice(null, strategy, "nothing to choose from")
        val incumbent = ranked.firstOrNull { it.candidate.current }
        if (incumbent == null || incumbent.profile.id == best.profile.id) {
            return AutoServerChoice(
                profile = best.profile,
                strategy = strategy,
                reason = "$label • ${best.reason}",
                ranking = ranked
            )
        }
        val margin = (switchMarginPercent.coerceIn(0, 100) / 100.0)
        val challengerWins = when {
            incumbent.score <= 0.0 -> true
            best.score <= 0.0 -> false
            else -> (best.score - incumbent.score) / max(incumbent.score, 0.01) >= margin
        }
        return if (challengerWins) {
            AutoServerChoice(
                profile = best.profile,
                strategy = strategy,
                reason = "$label • ${best.reason} • beats the current route by " +
                    "${(((best.score - incumbent.score) / max(incumbent.score, 0.01)) * 100).toInt()}%",
                ranking = ranked
            )
        } else {
            AutoServerChoice(
                profile = incumbent.profile,
                strategy = strategy,
                reason = "holding the current route • ${incumbent.reason}",
                held = true,
                ranking = ranked
            )
        }
    }

    fun nextCursor(candidates: List<ServerCandidate>, cursor: Int): Int {
        val size = candidates.size
        if (size <= 0) return 0
        return ((cursor % size) + 1 + size) % size
    }

    fun scopeLabel(scope: AutoServerScope): String = when (scope) {
        AutoServerScope.SOURCE -> "this source"
        AutoServerScope.ALL -> "the whole library"
    }

    fun describe(strategy: AutoServerStrategy): String = when (strategy) {
        AutoServerStrategy.LEAST_PING ->
            "Picks the server with the lowest measured latency, ignoring any node losing a third of its packets."
        AutoServerStrategy.LEAST_LOAD ->
            "Picks the least congested server: latency-under-load and throughput decide, latency only breaks ties."
        AutoServerStrategy.ROUND_ROBIN ->
            "Walks the pool one server per selection, so every node is used and none is worn out."
        AutoServerStrategy.RANDOM ->
            "Picks a server at random, so no single exit ever learns your whole traffic pattern."
        AutoServerStrategy.SMART ->
            "Balances ping, congestion, stability, throughput and measurement age."
        AutoServerStrategy.CUSTOM ->
            "Ranks measured servers using your saved ping, load, stability and freshness priorities."
    }

    fun shortLabel(strategy: AutoServerStrategy): String = when (strategy) {
        AutoServerStrategy.LEAST_PING -> "Least ping"
        AutoServerStrategy.LEAST_LOAD -> "Least load"
        AutoServerStrategy.ROUND_ROBIN -> "Round robin"
        AutoServerStrategy.RANDOM -> "Random"
        AutoServerStrategy.SMART -> "Smart"
        AutoServerStrategy.CUSTOM -> "Custom"
    }

    fun confidenceOf(choice: AutoServerChoice, nowMs: Long): Double {
        val best = choice.ranking.firstOrNull() ?: return 0.0
        val fresh = freshnessScore(best.candidate.benchmark?.measuredAtMs ?: 0L, nowMs)
        return (best.score * 0.7 + fresh * 0.3).coerceIn(0.0, 1.0)
    }
}
