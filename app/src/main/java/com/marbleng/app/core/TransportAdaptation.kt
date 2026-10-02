package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.FilterSeverity
import com.marbleng.app.model.ProxyProfile
import com.marbleng.app.model.TransportProfileMode
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

// =============================================================================
// MARBLE_TRANSPORT_ADAPTATION_V203
//
// A critique of what this product shipped before V203, because the new engine only makes
// sense next to what it replaces:
//
//  1. **Fragment was one recipe with eight names.** `DpiEvasionPolicy` carries nine
//     `FragmentRecipe` constants, and every one of them is a *static* answer to a question
//     that is not static. `HAMRAH_STEEL` was right for MCI in April 2026; nothing in the
//     product can tell you whether MCI still behaves that way in October, or whether it
//     behaves that way at 21:00 on a Thursday. A table of constants is a snapshot of one
//     week's fieldwork, shipped as if it were physics.
//
//  2. **The operator ladder was one-shot.** `DpiEvasionPolicy.connectionRecipe(state)`
//     escalates from live ping/jitter/loss and never de-escalates: a link that was briefly
//     bad carries the heavyweight recipe for the rest of the session, paying a 517-byte
//     shred on a connection that needed nothing. Escalation-only is not adaptation, it is
//     hysteresis with one direction.
//
//  3. **Mux was a checkbox with three numbers.** `muxEnabled` / `muxConcurrency` /
//     `muxXudpConcurrency` were user constants. Whether Mux helps is entirely a function of
//     what the filter in front of you does with many streams on one connection — which is
//     exactly the kind of thing that must be measured per operator, not configured once.
//
//  4. **Nothing was remembered.** Every connection started from the same prior. The product
//     re-learned "this operator resets fragmented handshakes" on every single connect, and
//     the user paid for the lesson in failed handshakes, forever.
//
// What replaces it is below: profiles for both knobs, a memory keyed by operator *and* time
// of day, a bandit that balances what is known against what has not been tried, and a drift
// detector that notices when the operator changed its mind.
// =============================================================================

/** Quarter of the day a measurement belongs to: filtering has a rush hour. */
enum class DayPart(val id: String, val startHour: Int) {
    NIGHT("night", 0),
    MORNING("morning", 6),
    AFTERNOON("afternoon", 12),
    EVENING("evening", 18);

    companion object {
        val ALL: List<DayPart> get() = entries
    }
}

fun dayPartOf(hour: Int): DayPart = when (((hour % 24) + 24) % 24) {
    in 0..5 -> DayPart.NIGHT
    in 6..11 -> DayPart.MORNING
    in 12..17 -> DayPart.AFTERNOON
    else -> DayPart.EVENING
}

/**
 * One Mux shape.
 *
 * Mux trades a smaller number of visible connections for a distinctive multiplexed pattern,
 * and different filters punish different parts of that trade: some throttle by connection
 * count (Mux helps enormously), some fingerprint the multiplexed stream (Mux hurts), some
 * ignore it entirely (Mux helps throughput and costs nothing). Which one is in front of you
 * is a measurement, not an opinion.
 */
enum class MuxProfile(
    val id: String,
    val label: String,
    val enabled: Boolean,
    val concurrency: Int,
    val xudpConcurrency: Int,
    val udp443: String,
    /** How much multiplexing this shape puts on the wire; the prior uses it. */
    val weight: Int
) {
    OFF("off", "Off", false, 8, 16, "skip", 0),
    LIGHT("light", "Light", true, 4, 8, "skip", 1),
    BALANCED("balanced", "Balanced", true, 8, 16, "skip", 2),
    THROUGHPUT("throughput", "Throughput", true, 16, 32, "skip", 3),
    UDP_HEAVY("udp_heavy", "UDP heavy", true, 8, 32, "allow", 3),
    STEALTH("stealth", "Stealth", true, 2, 4, "skip", 1);

    companion object {
        val DEFAULT: MuxProfile get() = OFF

        fun byId(raw: String): MuxProfile =
            entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) } ?: DEFAULT
    }
}

/**
 * One fragment shape.
 *
 * [strength] is the ladder the prior walks: 0 does nothing to the wire, 5 shreds every
 * write into single bytes. The point of the number is that strength is *expensive* — each
 * step costs throughput and each step is only worth paying when the filter in front of you
 * actually punishes the unfragmented shape.
 */
enum class FragmentProfile(
    val id: String,
    val label: String,
    val enabled: Boolean,
    val packets: String,
    val length: String,
    val interval: String,
    val maxSplit: String,
    val innerEnabled: Boolean,
    val innerPackets: String,
    val innerLength: String,
    val innerInterval: String,
    val innerMaxSplit: String,
    val strength: Int
) {
    OFF(
        "off", "Off", false,
        "tlshello", "100-200", "10-20", "",
        false, "1-1", "1", "4", "517", 0
    ),
    TLSHELLO(
        "tlshello", "ClientHello split", true,
        "tlshello", "100-200", "10-20", "",
        false, "1-1", "1", "4", "517", 1
    ),
    RECORD_SPLIT(
        "record_split", "Fine record split", true,
        "tlshello", "40-80", "5-10", "",
        false, "1-1", "1", "4", "517", 2
    ),
    GFW_KNOCKER(
        "gfw_knocker", "Packet split (GFW-knocker)", true,
        "1-3", "1-3", "5-10", "",
        false, "1-1", "1", "4", "517", 3
    ),
    OFFICIAL_SKIP_CHAIN(
        "skip_chain", "Official skip-fragment chain", true,
        "1-1", "130", "560", "4",
        true, "2-4", "1", "4", "130", 4
    ),
    FULL_FRAGMENT(
        "full_fragment", "Full fragment (517)", true,
        "1-1", "1", "4", "517",
        false, "1-1", "1", "4", "517", 5
    ),
    STEEL_CASCADE(
        "steel_cascade", "Steel cascade (split → shred)", true,
        "1-3", "1-3", "5-10", "",
        true, "1-1", "1", "4", "517", 5
    ),
    EXTREME(
        "extreme", "Extreme micro-fragment", true,
        "1-1", "1", "2", "517",
        true, "1-1", "1", "2", "517", 6
    );

    companion object {
        val DEFAULT: FragmentProfile get() = OFF

        fun byId(raw: String): FragmentProfile =
            entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) } ?: DEFAULT
    }
}

/** One (fragment, Mux) pair the bandit can pick, and its stable identity on disk. */
data class TransportPair(
    val fragment: FragmentProfile = FragmentProfile.OFF,
    val mux: MuxProfile = MuxProfile.OFF
) {
    val id: String get() = "${fragment.id}+${mux.id}"

    companion object {
        fun fromId(raw: String): TransportPair {
            val parts = raw.split('+', limit = 2)
            return TransportPair(
                fragment = FragmentProfile.byId(parts.getOrElse(0) { "" }),
                mux = MuxProfile.byId(parts.getOrElse(1) { "" })
            )
        }
    }
}

/**
 * What the wire in front of one profile looks like, as far as fragmentation is concerned.
 *
 * Fragmentation is a TCP/TLS trick: it works by splitting writes on a stream the filter
 * reassembles. A QUIC datagram transport (Hysteria2, TUIC) has no such stream to split, and
 * a hop with no TLS has no ClientHello to split — so offering those profiles there would be
 * measuring noise and charging the user for it.
 */
enum class TransportShape { TCP_TLS, TCP_PLAIN, QUIC, UNKNOWN }

fun transportShapeOf(profile: ProxyProfile): TransportShape {
    val scheme = profile.scheme.lowercase()
    val transport = profile.transport.lowercase()
    val security = profile.security.lowercase()
    if (scheme in setOf("hysteria2", "hy2", "hysteria", "tuic") ||
        transport == "quic" ||
        scheme == "wireguard" ||
        scheme == "wg"
    ) return TransportShape.QUIC
    if (scheme in setOf("vless", "vmess", "trojan", "shadowsocks", "ss")) {
        return if (security in setOf("", "none", "plain")) TransportShape.TCP_PLAIN else TransportShape.TCP_TLS
    }
    return TransportShape.UNKNOWN
}

/**
 * MARBLE_TRANSPORT_ADAPTATION_V203 — what one operator, at one time of day, has taught us.
 *
 * The memory is per (operator, [DayPart]) because filtering is not one behaviour per ASN: it
 * is one behaviour per ASN *per shift*. The evening throttling on Iranian mobile links is a
 * documented, reproducible, time-of-day phenomenon, and a single per-operator average would
 * smear the 21:00 evidence into the 04:00 evidence until neither described either.
 *
 * `profileScores` / `profileTries` are the bandit's own state: an EWMA quality score and a
 * try count per pair, so what has not been tried yet can be preferred without pretending it
 * is known to be good.
 */
data class TransportMemoryRecord(
    val carrierId: String,
    val dayPart: DayPart,
    val pairId: String = TransportPair().id,
    val observations: Int = 0,
    /** EWMA of the quality score of [pairId]; 0..1. */
    val scoreEwma: Double = 0.0,
    /** Fast and slow EWMAs of the same series: their gap IS the drift signal. */
    val fastEwma: Double = 0.0,
    val slowEwma: Double = 0.0,
    val latencyEwma: Double = 0.0,
    val jitterEwma: Double = 0.0,
    val throughputEwma: Double = 0.0,
    val successEwma: Double = 0.0,
    val profileScores: Map<String, Double> = emptyMap(),
    val profileTries: Map<String, Int> = emptyMap(),
    val updatedAtMs: Long = 0L,
    /** When the preferred pair last actually changed — the anti-flap clock. */
    val changedAtMs: Long = 0L,
    /** 0..1: how far recent evidence has moved away from the long-run average. */
    val drift: Double = 0.0
) {
    val pair: TransportPair get() = TransportPair.fromId(pairId)

    /** True once the record has earned the right to overrule the static prior. */
    val confident: Boolean get() = observations >= TransportAdaptation.CONFIDENT_OBSERVATIONS

    fun toJson(): JSONObject {
        val scores = JSONObject()
        profileScores.forEach { (key, value) -> scores.put(key, value) }
        val tries = JSONObject()
        profileTries.forEach { (key, value) -> tries.put(key, value) }
        return JSONObject()
            .put("carrierId", carrierId)
            .put("dayPart", dayPart.id)
            .put("pairId", pairId)
            .put("observations", observations)
            .put("scoreEwma", scoreEwma)
            .put("fastEwma", fastEwma)
            .put("slowEwma", slowEwma)
            .put("latencyEwma", latencyEwma)
            .put("jitterEwma", jitterEwma)
            .put("throughputEwma", throughputEwma)
            .put("successEwma", successEwma)
            .put("profileScores", scores)
            .put("profileTries", tries)
            .put("updatedAtMs", updatedAtMs)
            .put("changedAtMs", changedAtMs)
            .put("drift", drift)
    }

    companion object {
        /** Stable key of one (operator, time-of-day) memory cell. */
        fun keyOf(carrierId: String, dayPart: DayPart): String =
            "${carrierId.trim().lowercase()}|${dayPart.id}"

        fun fromJson(o: JSONObject): TransportMemoryRecord {
            val scores = linkedMapOf<String, Double>()
            val rawScores = o.optJSONObject("profileScores")
            if (rawScores != null) {
                for (key in rawScores.keys()) {
                    scores[key] = rawScores.optDouble(key, 0.0)
                }
            }
            val tries = linkedMapOf<String, Int>()
            val rawTries = o.optJSONObject("profileTries")
            if (rawTries != null) {
                for (key in rawTries.keys()) {
                    tries[key] = rawTries.optInt(key, 0)
                }
            }
            val dayPart = DayPart.ALL.firstOrNull { it.id == o.optString("dayPart") } ?: DayPart.MORNING
            return TransportMemoryRecord(
                carrierId = o.optString("carrierId").ifBlank { TransportAdaptation.UNKNOWN_CARRIER },
                dayPart = dayPart,
                pairId = o.optString("pairId").ifBlank { TransportPair().id },
                observations = o.optInt("observations").coerceAtLeast(0),
                scoreEwma = o.optDouble("scoreEwma").coerceIn(0.0, 1.0),
                fastEwma = o.optDouble("fastEwma").coerceIn(0.0, 1.0),
                slowEwma = o.optDouble("slowEwma").coerceIn(0.0, 1.0),
                latencyEwma = o.optDouble("latencyEwma").coerceAtLeast(0.0),
                jitterEwma = o.optDouble("jitterEwma").coerceAtLeast(0.0),
                throughputEwma = o.optDouble("throughputEwma").coerceAtLeast(0.0),
                successEwma = o.optDouble("successEwma").coerceIn(0.0, 1.0),
                profileScores = scores,
                profileTries = tries,
                updatedAtMs = o.optLong("updatedAtMs").coerceAtLeast(0L),
                changedAtMs = o.optLong("changedAtMs").coerceAtLeast(0L),
                drift = o.optDouble("drift").coerceIn(0.0, 1.0)
            )
        }
    }
}

/** The result of one decision: what to put on the wire, and why. */
data class TransportDecision(
    val pair: TransportPair = TransportPair(),
    /** True when this round is deliberately spending a connection on an unproven pair. */
    val exploring: Boolean = false,
    /** True when the drift detector invalidated the old confidence this round. */
    val drifted: Boolean = false,
    val reason: String = "no memory yet"
) {
    val fragment: FragmentProfile get() = pair.fragment
    val mux: MuxProfile get() = pair.mux
}

/**
 * MARBLE_TRANSPORT_ADAPTATION_V203 — the learner.
 *
 * Every function here is pure: memory in, memory (or a decision) out. Nothing touches Android,
 * nothing touches the network, and nothing is hidden in a field, so the whole policy is pinned
 * by unit tests instead of by hope.
 *
 * The loop is a Boltzmann-free, UCB-flavoured bandit over at most `fragment × mux` pairs:
 *
 *  - **prior** — what the severity table says about this operator before we measured anything;
 *  - **evidence** — the EWMA quality of each pair we have actually tried on this operator at
 *    this time of day;
 *  - **exploration** — an uncertainty bonus that shrinks as a pair is tried, so an operator
 *    that changed its filtering gets found without the user filing a bug report;
 *  - **hysteresis** — an incumbent that is healthy keeps the connection unless a challenger
 *    beats it by [SWITCH_MARGIN], because a protocol that flip-flops every round is worse
 *    than one that is 5 % off optimal;
 *  - **drift** — fast vs slow EWMA of the same series. When they separate, the operator
 *    changed; confidence is cut and exploration is forced, which is the "remember it, and
 *    notice when it changes" half of the requirement.
 */
object TransportAdaptation {

    const val UNKNOWN_CARRIER = "unknown"

    /** Observations before a record may overrule the static severity prior. */
    const val CONFIDENT_OBSERVATIONS = 4

    /** Fast and slow EWMAs. The slow one is the operator's character; the fast one is today. */
    const val ALPHA_FAST = 0.35
    const val ALPHA_SLOW = 0.08
    const val ALPHA_SCORE = 0.30

    /** Gap between fast and slow (relative) that counts as "the operator changed". */
    const val DRIFT_THRESHOLD = 0.22

    /** A challenger must beat a healthy incumbent by this much before we move. */
    const val SWITCH_MARGIN = 0.05

    /** Weight of the uncertainty bonus. Higher explores more; this is deliberately cheap. */
    const val EXPLORATION = 0.35

    /** A memory cell older than this is evidence about a network that no longer exists. */
    const val MEMORY_MAX_AGE_MS = 45L * 24L * 60L * 60L * 1000L

    /** How many pairs one cell may remember: the table is written on every observation. */
    const val MAX_RECORDED_PAIRS = 24

    /** A cell untouched for this long is dropped on load. */
    const val MAX_CELLS = 96

    // ─── The static prior ──────────────────────────────────────────────────────────────

    /** The fragment strength a severity calls for, before anything is measured. */
    fun preferredStrength(severity: FilterSeverity): Int = when (severity) {
        FilterSeverity.LIGHT -> 1
        FilterSeverity.MODERATE -> 2
        FilterSeverity.HEAVY -> 4
        FilterSeverity.EXTREME -> 5
    }

    /** Prior quality of one pair on an operator of this severity, in 0..1. */
    fun priorFor(pair: TransportPair, severity: FilterSeverity): Double {
        val wanted = preferredStrength(severity).toDouble()
        val distance = abs(pair.fragment.strength - wanted)
        // Each step away from the severity's own strength costs 15 %, floored so an untested
        // pair is never worth exactly zero (it would never be explored).
        val fragmentPrior = (1.0 - distance * 0.15).coerceIn(0.10, 1.0)
        // Mux: mild multiplexing is nearly always a throughput win; a heavy one is a
        // fingerprint on a severe link and a gift on a light one.
        val muxPrior = when (pair.mux) {
            MuxProfile.OFF -> 0.40
            MuxProfile.LIGHT -> 0.58
            MuxProfile.BALANCED -> 0.62
            MuxProfile.THROUGHPUT -> if (severity == FilterSeverity.EXTREME) 0.42 else 0.60
            MuxProfile.UDP_HEAVY -> 0.52
            MuxProfile.STEALTH -> if (severity == FilterSeverity.LIGHT) 0.40 else 0.56
        }
        return (fragmentPrior * 0.65 + muxPrior * 0.35).coerceIn(0.05, 1.0)
    }

    /**
     * The pairs worth considering for one route.
     *
     * A QUIC hop or a plaintext hop gets no fragment candidates at all — offering them would
     * spend the user's connections measuring a knob that does nothing on that wire — and a
     * plaintext hop keeps Mux, which is transport-agnostic inside Xray.
     */
    fun candidatesFor(shape: TransportShape, severity: FilterSeverity): List<TransportPair> {
        val fragments = when (shape) {
            TransportShape.QUIC -> listOf(FragmentProfile.OFF)
            TransportShape.TCP_PLAIN -> listOf(FragmentProfile.OFF, FragmentProfile.GFW_KNOCKER)
            TransportShape.TCP_TLS, TransportShape.UNKNOWN -> FragmentProfile.entries.toList()
        }
        val muxes = when (shape) {
            TransportShape.QUIC -> listOf(MuxProfile.OFF)
            else -> MuxProfile.entries.toList()
        }
        return fragments.flatMap { fragment -> muxes.map { mux -> TransportPair(fragment, mux) } }
            .filter { priorFor(it, severity) > 0.0 }
    }

    // ─── Scoring one observation ───────────────────────────────────────────────────────

    /**
     * Quality of one measured connection, in 0..1.
     *
     * Success is a hard gate: a pair that cannot complete is worth nothing no matter how fast
     * it was. The rest is the three things a user actually feels, on scales that match how
     * they feel them — latency around a 120 ms reference, jitter around 40 ms (past that,
     * interactive traffic stutters) and throughput topping out at 4 MB/s (past that, nothing
     * a phone does gets better).
     */
    fun quality(
        success: Boolean,
        latencyMs: Double,
        jitterMs: Double,
        throughputBytesPerSecond: Double
    ): Double {
        if (!success || !latencyMs.isFinite() || latencyMs <= 0.0) return 0.0
        val latency = 1.0 / (1.0 + (latencyMs.coerceAtLeast(1.0) / 120.0))
        val jitter = 1.0 / (1.0 + (jitterMs.coerceAtLeast(0.0) / 40.0))
        val throughput = (throughputBytesPerSecond.coerceAtLeast(0.0) / 4_000_000.0).coerceAtMost(1.0)
        return (latency * 0.40 + jitter * 0.20 + throughput * 0.40).coerceIn(0.0, 1.0)
    }

    // ─── The decision ──────────────────────────────────────────────────────────────────

    /**
     * Pick the pair for the next connection.
     *
     * [memory] is the cell for this (operator, day part); `null` means we have never been here
     * before, which is a normal state and not an error. [shape] narrows the field to knobs that
     * exist on this wire.
     */
    fun decide(
        memory: TransportMemoryRecord?,
        shape: TransportShape,
        severity: FilterSeverity,
        explore: Boolean = true,
        nowMs: Long = 0L
    ): TransportDecision {
        val candidates = candidatesFor(shape, severity)
        if (candidates.isEmpty()) {
            return TransportDecision(reason = "nothing applies to this transport")
        }
        if (memory == null || memory.observations == 0) {
            // No evidence: the severity table is the only honest guide, and it picks the
            // cheapest pair that matches rather than the strongest one available.
            val best = candidates.maxByOrNull { priorFor(it, severity) } ?: TransportPair()
            return TransportDecision(
                pair = best,
                exploring = true,
                reason = "first contact on this operator • using the ${severity.name.lowercase()} baseline"
            )
        }

        val totalTries = memory.profileTries.values.sum().coerceAtLeast(1)
        val incumbent = memory.pair
        val incumbentKnown = memory.profileTries.containsKey(incumbent.id)

        fun evidenceOf(pair: TransportPair): Double =
            memory.profileScores[pair.id] ?: priorFor(pair, severity)

        fun triesOf(pair: TransportPair): Int = memory.profileTries[pair.id] ?: 0

        // UCB1: value + bonus, where the bonus shrinks with what we already know. The
        // logarithm keeps exploration sub-linear, so a stable operator stops being probed
        // while a changed one starts again immediately.
        fun upperBound(pair: TransportPair): Double {
            val bonus = if (!explore) 0.0 else {
                EXPLORATION * sqrt(ln(totalTries.toDouble() + 1.0) / (triesOf(pair) + 1.0))
            }
            return evidenceOf(pair) + bonus
        }

        // With exploration switched off the challenger must come from the pairs this cell has
        // actually measured. A prior is a starting point, not evidence: letting an untried
        // pair win on a guess is how a learner abandons a profile the user's own connections
        // proved works, because the severity table happens to like the other one more.
        val measured = candidates.filter { memory.profileTries.containsKey(it.id) }
        val field = if (explore || measured.isEmpty()) candidates else measured

        val challenger = field.maxByOrNull { upperBound(it) } ?: incumbent
        val drifted = memory.drift >= DRIFT_THRESHOLD

        // Hysteresis: keep a healthy, recently-confirmed incumbent unless the challenger is
        // materially better, or the operator's behaviour moved and the incumbent's evidence
        // describes a network that is no longer there.
        val incumbentHealthy = memory.successEwma >= 0.5 && memory.scoreEwma >= 0.35
        val settledRecently = nowMs <= 0L || memory.changedAtMs <= 0L ||
            (nowMs - memory.changedAtMs) < MIN_SWITCH_INTERVAL_MS
        val margin = evidenceOf(challenger) - evidenceOf(incumbent)

        val chosen = when {
            !incumbentKnown -> challenger
            drifted -> challenger
            !incumbentHealthy -> challenger
            challenger.id == incumbent.id -> incumbent
            margin < SWITCH_MARGIN && settledRecently -> incumbent
            margin < SWITCH_MARGIN -> challenger
            else -> challenger
        }

        return TransportDecision(
            pair = chosen,
            exploring = explore && triesOf(chosen) < EXPLORATION_MIN_TRIES,
            drifted = drifted,
            reason = when {
                drifted -> "operator behaviour moved • re-learning ${memory.carrierId}"
                !incumbentKnown -> "testing ${chosen.id}"
                chosen.id != incumbent.id -> "${chosen.id} beats ${incumbent.id} by ${(margin * 100).toInt()}%"
                else -> "holding ${chosen.id}"
            }
        )
    }

    /** A pair stops counting as "unproven" once it has been measured this many times. */
    const val EXPLORATION_MIN_TRIES = 3

    /** Minimum time between two profile switches, so a route cannot thrash inside a session. */
    const val MIN_SWITCH_INTERVAL_MS = 45L * 1000L

    // ─── Learning ──────────────────────────────────────────────────────────────────────

    /**
     * Fold one measured connection into the memory of one (operator, day part) cell.
     *
     * Returns the new record together with the drift verdict, so the caller can log it and the
     * next [decide] can act on it.
     */
    fun observe(
        memory: TransportMemoryRecord?,
        carrierId: String,
        dayPart: DayPart,
        pair: TransportPair,
        success: Boolean,
        latencyMs: Double,
        jitterMs: Double,
        throughputBytesPerSecond: Double,
        nowMs: Long = System.currentTimeMillis(),
        severity: FilterSeverity = FilterSeverity.HEAVY
    ): TransportMemoryRecord {
        val score = quality(success, latencyMs, jitterMs, throughputBytesPerSecond)
        val base = memory ?: TransportMemoryRecord(
            carrierId = carrierId.trim().lowercase().ifBlank { UNKNOWN_CARRIER },
            dayPart = dayPart
        )

        val pairScores = base.profileScores.toMutableMap()
        val prior = pairScores[pair.id] ?: priorFor(pair, severity)
        pairScores[pair.id] = (prior * (1.0 - ALPHA_SCORE) + score * ALPHA_SCORE).coerceIn(0.0, 1.0)

        val pairTries = base.profileTries.toMutableMap()
        pairTries[pair.id] = (pairTries[pair.id] ?: 0) + 1

        // Keep the table bounded: drop the least-tried pair we are not currently using.
        if (pairScores.size > MAX_RECORDED_PAIRS) {
            val victims = pairScores.keys
                .filter { it != pair.id }
                .sortedWith(compareBy<String> { pairTries[it] ?: 0 }.thenByDescending { it })
            for (victim in victims) {
                if (pairScores.size <= MAX_RECORDED_PAIRS) break
                pairScores.remove(victim)
                pairTries.remove(victim)
            }
        }

        // ── The drift detector ──────────────────────────────────────────────────────────
        //
        // Two windows over the same signal: a fast one that follows the last few connections,
        // a slow one that remembers the season. |fast - slow| is how far the operator has
        // moved from the network the cell's evidence describes.
        //
        // Both windows are SEEDED from the first observation of a cell instead of blended up
        // from zero. This is not a polish detail, it is the whole detector: an EWMA started at
        // zero needs ~19 connections before its initial value stops dominating, and the fast
        // window needs ~3 — so for the first dozen connections |fast - slow| is enormous for
        // one reason only, that the cell is young. The first version of this code compared
        // unseeded windows and reported "the operator changed" on the fifth connection of a
        // perfectly stable operator, every time.
        //
        // Drift also has to be self-clearing by construction, not by bookkeeping. Halving a
        // confidence counter when drift fires (an earlier attempt) made the counter fall below
        // its own confidence floor, which zeroed the drift on the *next* observation — so the
        // detector disarmed itself one connection after it fired. Comparing the two windows
        // needs no such counter: once the operator settles at its new behaviour the fast
        // window converges back onto the slow one and the reading falls on its own.
        val firstObservation = base.observations <= 0
        val fast = if (firstObservation) {
            score
        } else {
            base.fastEwma * (1.0 - ALPHA_FAST) + score * ALPHA_FAST
        }
        val slow = if (firstObservation) {
            score
        } else {
            base.slowEwma * (1.0 - ALPHA_SLOW) + score * ALPHA_SLOW
        }
        // The confidence floor still earns its keep for a different reason: seeding removes
        // the warm-up false positive, but two or three noisy connections can still separate
        // the windows before either has seen enough to be an average rather than a sample.
        val drift = if (base.observations >= CONFIDENT_OBSERVATIONS) {
            val reference = max(slow, 0.05)
            (abs(fast - slow) / reference).coerceIn(0.0, 1.0)
        } else {
            0.0
        }
        val observations = base.observations + 1

        val switched = base.pairId != pair.id || base.observations == 0
        return base.copy(
            pairId = pair.id,
            observations = observations,
            scoreEwma = (base.scoreEwma * (1.0 - ALPHA_SCORE) + score * ALPHA_SCORE).coerceIn(0.0, 1.0),
            fastEwma = fast.coerceIn(0.0, 1.0),
            slowEwma = slow.coerceIn(0.0, 1.0),
            latencyEwma = ewma(base.latencyEwma, latencyMs.coerceAtLeast(0.0), base.observations),
            jitterEwma = ewma(base.jitterEwma, jitterMs.coerceAtLeast(0.0), base.observations),
            throughputEwma = ewma(
                base.throughputEwma,
                throughputBytesPerSecond.coerceAtLeast(0.0),
                base.observations
            ),
            successEwma = ewma(base.successEwma, if (success) 1.0 else 0.0, base.observations)
                .coerceIn(0.0, 1.0),
            profileScores = pairScores,
            profileTries = pairTries,
            updatedAtMs = nowMs,
            changedAtMs = if (switched) nowMs else base.changedAtMs,
            drift = drift
        )
    }

    private fun ewma(previous: Double, sample: Double, observations: Int): Double {
        if (observations <= 0) return sample
        val alpha = ALPHA_SCORE
        return (previous * (1.0 - alpha) + sample * alpha).coerceAtLeast(0.0)
    }

    // ─── Applying a decision to the settings that reach the core ────────────────────────

    /**
     * Write one pair into the settings the config builders read.
     *
     * [TransportProfileMode.MANUAL] never reaches here with a learned pair — the caller passes
     * the user's own values in that case — so this is the single place where a decision becomes
     * wire behaviour, and it is deliberately a pure `copy()`.
     */
    fun applyTo(base: AppSettings, pair: TransportPair): AppSettings =
        base.copy(
            fragmentEnabled = pair.fragment.enabled,
            fragmentPackets = pair.fragment.packets,
            fragmentLength = pair.fragment.length,
            fragmentInterval = pair.fragment.interval,
            fragmentMaxSplit = pair.fragment.maxSplit,
            fragmentInnerEnabled = pair.fragment.innerEnabled,
            fragmentInnerPackets = pair.fragment.innerPackets,
            fragmentInnerLength = pair.fragment.innerLength,
            fragmentInnerInterval = pair.fragment.innerInterval,
            fragmentInnerMaxSplit = pair.fragment.innerMaxSplit,
            muxEnabled = pair.mux.enabled,
            muxConcurrency = pair.mux.concurrency,
            muxXudpConcurrency = pair.mux.xudpConcurrency,
            muxUdp443 = pair.mux.udp443
        )

    /** Pair the user's own settings currently describe, for MANUAL mode bookkeeping. */
    fun pairFromSettings(s: AppSettings): TransportPair {
        val fragment = if (!s.fragmentEnabled) {
            FragmentProfile.OFF
        } else {
            FragmentProfile.entries.firstOrNull { candidate ->
                candidate.enabled &&
                    candidate.packets == s.fragmentPackets &&
                    candidate.length == s.fragmentLength &&
                    candidate.interval == s.fragmentInterval &&
                    candidate.maxSplit == s.fragmentMaxSplit &&
                    candidate.innerEnabled == s.fragmentInnerEnabled
            } ?: FragmentProfile.entries.firstOrNull { candidate ->
                candidate.enabled && candidate.packets == s.fragmentPackets
            } ?: FragmentProfile.TLSHELLO
        }
        val mux = if (!s.muxEnabled) {
            MuxProfile.OFF
        } else {
            MuxProfile.entries.firstOrNull { candidate ->
                candidate.enabled &&
                    candidate.concurrency == s.muxConcurrency &&
                    candidate.xudpConcurrency == s.muxXudpConcurrency &&
                    candidate.udp443 == s.muxUdp443
            } ?: MuxProfile.entries
                .filter { it.enabled }
                .minByOrNull { abs(it.concurrency - s.muxConcurrency) }
                ?: MuxProfile.BALANCED
        }
        return TransportPair(fragment, mux)
    }

    /**
     * Stable operator identity for the memory key.
     *
     * MCC/MNC is the strongest identity a phone can give (it is what the SIM and the serving
     * network actually are); the ISP short name is the fallback for Wi-Fi and for handsets
     * that hide the operator code. Blank collapses to [UNKNOWN_CARRIER] rather than to an
     * empty key, so "no identity" is one cell and not a new one per connection.
     */
    fun carrierKeyOf(operatorCode: String, ispShortName: String, carrierName: String): String {
        val code = operatorCode.trim()
        if (code.length >= 5 && code.all { it.isDigit() }) return code
        val isp = ispShortName.trim()
        if (isp.isNotBlank()) return isp.lowercase()
        val name = carrierName.trim()
        if (name.isNotBlank()) return name.lowercase()
        return UNKNOWN_CARRIER
    }

    /** Drop cells that have aged out or that no longer fit the bound. */
    fun prune(cells: Map<String, TransportMemoryRecord>, nowMs: Long): Map<String, TransportMemoryRecord> =
        cells.values
            .filter { nowMs - it.updatedAtMs < MEMORY_MAX_AGE_MS }
            .sortedByDescending { it.updatedAtMs }
            .take(MAX_CELLS)
            .associateBy { TransportMemoryRecord.keyOf(it.carrierId, it.dayPart) }
}
