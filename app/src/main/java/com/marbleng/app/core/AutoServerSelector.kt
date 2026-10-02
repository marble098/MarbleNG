package com.marbleng.app.core

import com.marbleng.app.model.AutoServerScope
import com.marbleng.app.model.AutoServerStrategy
import com.marbleng.app.model.BenchmarkResult
import com.marbleng.app.model.ProxyProfile
import kotlin.math.abs
import kotlin.math.max

// =============================================================================
// MARBLE_AUTO_SERVER_SELECTOR_V202
//
// What the product had before this file: `autoConnectBestAfterScan`, one boolean, whose
// entire intelligence was `expanded.filter { it.success > 0 }.minByOrNull { it.latencyMs }`
// — pick whichever server answered fastest in the sweep that just finished.
//
// That is one heuristic wearing the label "auto", and it is wrong in the ways that matter:
//
//  - **It is blind to everything but latency.** A node with 30 ms and 90 % loss beats a node
//    with 55 ms and no loss, because one lucky packet sets the number. Jitter, throughput,
//    congestion under load and the recent failure record were all invisible to it.
//  - **It is blind to load.** "Least ping" and "least load" are not the same question. On the
//    links this product is built for, a node carrying three hundred users at 60 ms is a worse
//    route than an idle node at 80 ms, and only one of the two questions was askable.
//  - **It has one strategy for every user.** Some users want the fastest route, some want to
//    spread the load, some do not want any single exit to learn their traffic pattern. The
//    product picked for them and never said so.
//  - **It thrashes.** `minByOrNull` over one sample set with no margin means a 2 ms difference
//    moves the route, and it moves it again on the next sweep.
//
// What follows is the replacement: five named strategies, one scoring model shared by the two
// measurement-driven ones, and a switch margin so a working route is not abandoned for noise.
// =============================================================================

/** Everything the selector is allowed to know about one candidate route. */
data class ServerCandidate(
    val profile: ProxyProfile,
    val benchmark: BenchmarkResult? = null,
    /** Consecutive recent failures for this node, if the repository tracks them. */
    val failureStreak: Int = 0,
    /** True when this node is the route currently carrying traffic. */
    val current: Boolean = false
) {
    val id: String get() = profile.id
}

/** One scored candidate, with the reason the score is what it is. */
data class ServerScore(
    val candidate: ServerCandidate,
    val score: Double,
    val reason: String
) {
    val profile: ProxyProfile get() = candidate.profile
}

/** The outcome of one selection round. */
data class AutoServerChoice(
    val profile: ProxyProfile?,
    val strategy: AutoServerStrategy,
    val reason: String,
    /** True when the round left the current route in place on purpose. */
    val held: Boolean = false,
    /** Scores of every candidate, best first, so the UI and the log can explain the pick. */
    val ranking: List<ServerScore> = emptyList()
)

/**
 * MARBLE_AUTO_SERVER_SELECTOR_V202 — the selector.
 *
 * Pure, and deliberately so: the repository owns the library, the measurements and the
 * consequences; this object only answers "given these candidates and this strategy, which one
 * wins". That is what makes the five strategies testable without a phone, a network or a
 * subscription.
 */
object AutoServerSelector {

    /** Latency at which a route stops being "fast" and starts being "acceptable". */
    const val REFERENCE_LATENCY_MS = 160.0

    /** Jitter past which interactive traffic visibly stutters. */
    const val REFERENCE_JITTER_MS = 45.0

    /** Throughput at which a route counts as uncongested for a phone. */
    const val REFERENCE_THROUGHPUT_BPS = 2_500_000.0

    /** A measurement older than this is a memory, not evidence. */
    const val EVIDENCE_MAX_AGE_MS = 45L * 60L * 1000L

    /** Fraction a challenger must beat the incumbent by before the route moves. */
    const val SWITCH_MARGIN = 0.15

    /** A node with a failure streak at or past this is quarantined, not ranked. */
    const val QUARANTINE_STREAK = 4

    // ─── The shared scoring model ──────────────────────────────────────────────────────

    /**
     * Latency contribution, 0..1. 1 / (1 + r) is the honest shape: the first 50 ms of a route
     * matters enormously, the difference between 300 and 350 ms matters barely at all.
     */
    fun latencyScore(latencyMs: Double): Double =
        if (!latencyMs.isFinite() || latencyMs <= 0.0) 0.0
        else (1.0 / (1.0 + latencyMs / REFERENCE_LATENCY_MS)).coerceIn(0.0, 1.0)

    fun jitterScore(jitterMs: Double): Double =
        if (!jitterMs.isFinite() || jitterMs < 0.0) 0.5
        else (1.0 / (1.0 + jitterMs / REFERENCE_JITTER_MS)).coerceIn(0.0, 1.0)

    /** Loss is not a soft penalty: 12 % loss is a broken route, not a slightly slow one. */
    fun lossScore(lossPercent: Double): Double =
        if (!lossPercent.isFinite()) 0.5
        else (1.0 - (lossPercent.coerceIn(0.0, 100.0) / 100.0) * 3.0).coerceIn(0.0, 1.0)

    fun throughputScore(bytesPerSecond: Double): Double =
        if (!bytesPerSecond.isFinite() || bytesPerSecond <= 0.0) 0.0
        else (bytesPerSecond / REFERENCE_THROUGHPUT_BPS).coerceAtMost(1.0)

    /**
     * Congestion, from the two numbers that actually show it.
     *
     * `loadedLatencyMs` is the round trip measured while a bounded transfer was in flight;
     * compared with the idle latency it is bufferbloat, made visible. A route whose latency
     * triples under load is a loaded route however fast it looked while idle, which is exactly
     * the "least load" question the old one-line heuristic could not ask.
     */
    fun loadScore(idleLatencyMs: Double, loadedLatencyMs: Double, throughputBps: Double): Double {
        val idle = idleLatencyMs.takeIf { it.isFinite() && it > 0.0 }
        val loaded = loadedLatencyMs.takeIf { it.isFinite() && it > 0.0 }
        val congestion = if (idle != null && loaded != null) {
            val ratio = (loaded / idle).coerceIn(1.0, 8.0)
            // 1× = no congestion (1.0) • 2× = 0.72 • 4× = 0.43 • 8× = 0.0
            (1.0 - (ratio - 1.0) / 7.0).coerceIn(0.0, 1.0)
        } else {
            // No under-load measurement: fall back on throughput, which is the other half of
            // "how busy is this node", and say so by not pretending it is as good as a real one.
            throughputScore(throughputBps) * 0.8 + 0.1
        }
        return (congestion * 0.65 + throughputScore(throughputBps) * 0.35).coerceIn(0.0, 1.0)
    }

    /** How much a measurement this old may be trusted, 0.25..1. */
    fun freshnessScore(measuredAtMs: Long, nowMs: Long): Double {
        if (measuredAtMs <= 0L || nowMs <= 0L) return 0.5
        val age = (nowMs - measuredAtMs).coerceAtLeast(0L)
        if (age >= EVIDENCE_MAX_AGE_MS) return 0.25
        return (1.0 - 0.75 * (age.toDouble() / EVIDENCE_MAX_AGE_MS.toDouble())).coerceIn(0.25, 1.0)
    }

    /**
     * SMART: the weighted composite.
     *
     * Weights, and why: reachability dominates because a fast dead node is a dead node;
     * latency and load follow because those are the two things a user actually complains
     * about; stability (jitter + loss) is worth more than raw speed because a jittery 40 ms
     * feels worse than a steady 90 ms; throughput closes the gap.
     */
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

        // A node that keeps failing is not a route, it is a habit. The decay is multiplicative
        // and bounded so a streak that clears is genuinely forgiven.
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

    /** LEAST_LOAD: congestion and throughput, with latency only as a tie-breaker. */
    fun leastLoadScore(candidate: ServerCandidate): Pair<Double, String> {
        val b = candidate.benchmark
        if (b == null || b.success <= 0) return 0.0 to "never measured"
        val load = loadScore(b.latencyMs, b.loadedLatencyMs, b.bytesPerSecond)
        val latency = latencyScore(b.latencyMs)
        val score = (load * 0.75 + latency * 0.25).coerceIn(0.0, 1.0)
        return score to "load ${(load * 100).toInt()}% • ${b.latencyMs.toInt()} ms"
    }

    /** LEAST_PING: the classic, with the two guards the old one-liner lacked. */
    fun leastPingScore(candidate: ServerCandidate): Pair<Double, String> {
        val b = candidate.benchmark
        if (b == null || b.success <= 0) return 0.0 to "never measured"
        if (b.lossPercent >= 34.0) {
            // A node that lost a third of its packets did not "answer fast": one lucky probe
            // came back. Ranking it above a steady node is how auto-select picked dead routes.
            return 0.0 to "${b.latencyMs.toInt()} ms but ${b.lossPercent.toInt()}% loss"
        }
        val jitter = jitterScore(b.jitterMs)
        val score = (latencyScore(b.latencyMs) * 0.8 + jitter * 0.2).coerceIn(0.0, 1.0)
        return score to "${b.latencyMs.toInt()} ms"
    }

    // ─── Eligibility ───────────────────────────────────────────────────────────────────

    /** A candidate the selector is allowed to pick at all. */
    fun eligible(candidate: ServerCandidate): Boolean {
        if (candidate.failureStreak >= QUARANTINE_STREAK) return false
        val b = candidate.benchmark ?: return true
        // An explicit failure is dispositive for the measurement-driven strategies; for the
        // rotation strategies a node with no successful measurement is still a node worth a turn.
        return b.success > 0
    }

    // ─── The five strategies ───────────────────────────────────────────────────────────

    /**
     * Pick one route.
     *
     * [roundRobinCursor] and [randomSeed] are inputs rather than internal state so the object
     * stays pure: the repository owns the cursor (it is a user-visible choice that must
     * survive a restart) and the tests can pin the rotation exactly.
     */
    fun choose(
        candidates: List<ServerCandidate>,
        strategy: AutoServerStrategy,
        nowMs: Long = System.currentTimeMillis(),
        roundRobinCursor: Int = 0,
        randomSeed: Long = 0L,
        switchMarginPercent: Int = 15,
        scope: AutoServerScope = AutoServerScope.SOURCE
    ): AutoServerChoice {
        if (candidates.isEmpty()) {
            return AutoServerChoice(
                profile = null,
                strategy = strategy,
                reason = "no ${scope.id} servers to choose from"
            )
        }

        val pool = candidates.filter(::eligible).ifEmpty { candidates }

        when (strategy) {
            AutoServerStrategy.ROUND_ROBIN -> {
                // Rotation is over the *whole* pool, not just over nodes that happened to be
                // measured: a node nobody has pinged is exactly the node rotation exists to try.
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
                // A seeded, deterministic draw: reproducible in a test, and always a fresh
                // value in production because the repository seeds with the wall clock.
                val draw = if (randomSeed == 0L) {
                    ordered.indices.random()
                } else {
                    val mixed = (randomSeed xor (randomSeed ushr 32)) * 0x9E3779B97F4A7C15L
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

    /**
     * Turn a ranking into a decision, applying the switch margin to the incumbent.
     *
     * The margin is the difference between a selector and a roulette wheel. Without it, every
     * sweep that reorders the top two by a millisecond moves the user's exit node — which
     * breaks sessions, breaks Identity Guard's promise of one exit, and looks like a bug. With
     * it, the route only moves when the challenger is materially better or the incumbent is
     * actually unhealthy.
     */
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
            // An unhealthy incumbent is replaced immediately: the margin exists to prevent
            // flapping between two good routes, not to protect a bad one.
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

    /**
     * The next round-robin cursor: one step forward from the slot just used.
     *
     * Returns 0 for an empty pool rather than carrying a stale index, so a pool that shrank
     * and grew again cannot resume in the middle of a rotation nobody remembers starting.
     */
    fun nextCursor(candidates: List<ServerCandidate>, cursor: Int): Int {
        val size = candidates.size
        if (size <= 0) return 0
        return ((cursor % size) + 1 + size) % size
    }

    /**
     * How wide the pool should be, given the scope.
     *
     * SOURCE keeps the selector inside the subscription the user is looking at, which is the
     * only scope in which "least load" means anything: comparing load across two providers with
     * different backbones is comparing different questions.
     */
    fun scopeLabel(scope: AutoServerScope): String = when (scope) {
        AutoServerScope.SOURCE -> "this source"
        AutoServerScope.ALL -> "the whole library"
    }

    /**
     * One-line explanation of a strategy, for the setting and for the diagnostics log.
     * Pure, so the UI and the log cannot drift apart.
     */
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
            "Weighs latency, congestion, jitter, loss, throughput, handshake reliability and the age of the evidence."
    }

    /** Short name for the chips and the status line. */
    fun shortLabel(strategy: AutoServerStrategy): String = when (strategy) {
        AutoServerStrategy.LEAST_PING -> "Least ping"
        AutoServerStrategy.LEAST_LOAD -> "Least load"
        AutoServerStrategy.ROUND_ROBIN -> "Round robin"
        AutoServerStrategy.RANDOM -> "Random"
        AutoServerStrategy.SMART -> "Smart"
    }

    /**
     * A confidence thumbs-up/thumbs-down for the chosen route, 0..1.
     *
     * Used by the UI to say "the selector moved you" versus "the selector is guessing": a
     * SMART pick off fresh measurements is worth telling the user about, a pick off stale ones
     * is not.
     */
    fun confidenceOf(choice: AutoServerChoice, nowMs: Long): Double {
        val best = choice.ranking.firstOrNull() ?: return 0.0
        val fresh = freshnessScore(best.candidate.benchmark?.measuredAtMs ?: 0L, nowMs)
        return (best.score * 0.7 + fresh * 0.3).coerceIn(0.0, 1.0)
    }
}
