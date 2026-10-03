package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.FragmentChoice
import com.marbleng.app.model.MuxChoice
import com.marbleng.app.model.ProxyProfile
import com.marbleng.app.model.TransportProfileMode
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
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

// ─────────────────────────────────────────────────────────────────────────────
// MARBLE_FRAGMENT_PROFILES_V208 — one sentence per recipe.
//
// The chooser in Settings prints these, and the copy rule for the whole product is one option,
// one sentence (see `MarbleCopy`). A recipe the user cannot picture from its row is a recipe
// they will never pick, which is how eight working recipes ended up unusable behind four raw
// numeric fields.
// ─────────────────────────────────────────────────────────────────────────────

/** What turning this recipe on does to the bytes, in the one sentence a settings row allows. */
val FragmentProfile.summary: String
    get() = when (this) {
        FragmentProfile.OFF -> "Packets go out exactly as the app wrote them."
        FragmentProfile.TLSHELLO -> "Splits the TLS ClientHello across two packets."
        FragmentProfile.RECORD_SPLIT -> "Splits the handshake into small TLS records."
        FragmentProfile.GFW_KNOCKER -> "Shreds the first packets into one-to-three byte pieces."
        FragmentProfile.OFFICIAL_SKIP_CHAIN -> "Splits on the first hop and shreds at 517 on the second."
        FragmentProfile.FULL_FRAGMENT -> "Shreds every write to a single byte, capped at 517."
        FragmentProfile.STEEL_CASCADE -> "Splits first, then shreds again on the inner hop."
        FragmentProfile.EXTREME -> "One byte every two milliseconds, on both hops."
    }

/** What this multiplexing shape trades, in the one sentence a settings row allows. */
val MuxProfile.summary: String
    get() = when (this) {
        MuxProfile.OFF -> "Every stream opens its own connection."
        MuxProfile.LIGHT -> "Four streams share one connection."
        MuxProfile.BALANCED -> "Eight streams share one connection."
        MuxProfile.THROUGHPUT -> "Sixteen streams share one connection."
        MuxProfile.UDP_HEAVY -> "Eight streams, and UDP on 443 is allowed through."
        MuxProfile.STEALTH -> "Two streams, the smallest multiplexing footprint."
    }

/**
 * Monotonically ordered fragment ladder from mildest to most aggressive.
 * Under DPI, heavier fragmentation costs throughput, latency, and jitter.
 * The ladder allows binary search or progression to find the minimal effective setting.
 */
val FragmentLadder: List<FragmentProfile> = listOf(
    FragmentProfile.OFF,
    FragmentProfile.TLSHELLO,
    FragmentProfile.RECORD_SPLIT,
    FragmentProfile.GFW_KNOCKER,
    FragmentProfile.OFFICIAL_SKIP_CHAIN,
    FragmentProfile.FULL_FRAGMENT,
    FragmentProfile.STEEL_CASCADE,
    FragmentProfile.EXTREME
)

val FragmentProfile.overheadPenalty: Double
    get() = when (this) {
        FragmentProfile.OFF -> 0.00
        FragmentProfile.TLSHELLO -> 0.05
        FragmentProfile.RECORD_SPLIT -> 0.10
        FragmentProfile.GFW_KNOCKER -> 0.16
        FragmentProfile.OFFICIAL_SKIP_CHAIN -> 0.22
        FragmentProfile.FULL_FRAGMENT -> 0.30
        FragmentProfile.STEEL_CASCADE -> 0.38
        FragmentProfile.EXTREME -> 0.48
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

    /**
     * Pair the user's own settings currently describe.
     *
     * MARBLE_FRAGMENT_PROFILES_V208 — the recipe the settings *name* wins over the recipe the
     * fields happen to describe. The two can differ (a chosen profile whose fields an automatic
     * policy later rewrote, or a hand-typed set that sits between two ready recipes), and the
     * read-out that claims to say "what is on the wire" has to answer with the recipe the user
     * picked, not with the nearest match to four strings.
     */
    fun pairFromSettings(s: AppSettings): TransportPair =
        TransportPair(selectedFragment(s), selectedMux(s))

    /** The fragment recipe these settings name, falling back to what the fields describe. */
    fun selectedFragment(s: AppSettings): FragmentProfile {
        if (!s.fragmentEnabled) return FragmentProfile.OFF
        namedFragment(s.fragmentProfileId)?.let { return it }
        return fragmentFromFields(s)
    }

    /** The Mux recipe these settings name, falling back to what the fields describe. */
    fun selectedMux(s: AppSettings): MuxProfile {
        if (!s.muxEnabled) return MuxProfile.OFF
        namedMux(s.muxProfileId)?.let { return it }
        return muxFromFields(s)
    }

    /**
     * True when the user has actually chosen a recipe.
     *
     * A blank id is not "off": it is *no opinion*, which is what an install that never opened the
     * page has, and it is the only value under which the automatic policies (Iran Mode, the DPI
     * ladder, the intelligence engine) are allowed to shape packets on their own. Without that
     * distinction a shipped default of "off" would silently switch fragmentation off for every
     * Iran Mode user on their next connection.
     */
    fun fragmentIsUserOwned(s: AppSettings): Boolean = s.fragmentProfileId.isNotBlank()

    /** The Mux twin of [fragmentIsUserOwned]. */
    fun muxIsUserOwned(s: AppSettings): Boolean = s.muxProfileId.isNotBlank()

    /**
     * The user's own choice, written over whatever the automatic policies produced.
     *
     * This is the whole of the V208 fix for "fragmentation does not work": the recipe the user
     * picked in Settings is applied LAST, after Iran Mode, the DPI ladder and the intelligence
     * engine have had their say, instead of being a starting value that any of them could
     * overwrite on the way to the config builder. A choice that can be silently replaced by a
     * policy is not a control, and a control that appears to do nothing is worse than no control.
     *
     * Mux keeps one exception, and it is the only one: multiplexing on top of XTLS Vision or
     * REALITY is both slower and a stronger fingerprint, so [muxIsUnsafeFor] vetoes it — the
     * same rule [IranShield] already applies, now stated where the choice is honoured rather than
     * where it is silently undone.
     */
    fun applyUserChoice(base: AppSettings, user: AppSettings, profile: ProxyProfile?): AppSettings {
        var next = base
        // `custom` is checked first, and deliberately so: it is also non-blank, so letting the
        // recipe branch see it would snap the user's own numbers to the nearest named recipe —
        // a 1300-byte custom length silently becoming the 100-200 ClientHello split.
        when {
            FragmentChoice.isCustom(user.fragmentProfileId) -> next = withCustomFragment(next, user)
            fragmentIsUserOwned(user) -> next = withFragmentProfile(next, selectedFragment(user))
        }
        // The one veto that survives a user choice, for both a recipe and hand-typed values.
        val muxAllowed = profile == null || !muxIsUnsafeFor(profile)
        when {
            MuxChoice.isCustom(user.muxProfileId) -> next = withCustomMux(next, user, muxAllowed)
            muxIsUserOwned(user) -> {
                val mux = selectedMux(user)
                val safe = mux == MuxProfile.OFF || muxAllowed
                next = withMuxProfile(next, if (safe) mux else MuxProfile.OFF)
            }
        }
        return next
    }

    /**
     * The user's own numbers, copied onto the wire settings without being snapped to a recipe.
     *
     * This is the whole of the Custom option. Every other path in this file materialises a named
     * recipe into the fields; this one exists so that a value no recipe describes still reaches
     * the config builder as the user typed it, instead of being rounded to the nearest one on
     * the way.
     */
    fun withCustomFragment(base: AppSettings, user: AppSettings): AppSettings =
        base.copy(
            fragmentProfileId = FragmentChoice.CUSTOM,
            fragmentEnabled = user.fragmentEnabled,
            fragmentPackets = user.fragmentPackets,
            fragmentLength = user.fragmentLength,
            fragmentInterval = user.fragmentInterval,
            fragmentMaxSplit = user.fragmentMaxSplit,
            fragmentInnerEnabled = user.fragmentInnerEnabled,
            fragmentInnerPackets = user.fragmentInnerPackets,
            fragmentInnerLength = user.fragmentInnerLength,
            fragmentInnerInterval = user.fragmentInnerInterval,
            fragmentInnerMaxSplit = user.fragmentInnerMaxSplit
        )

    /** The Mux twin of [withCustomFragment]; [allowed] is the Vision/REALITY veto. */
    fun withCustomMux(base: AppSettings, user: AppSettings, allowed: Boolean): AppSettings =
        base.copy(
            muxProfileId = MuxChoice.CUSTOM,
            muxEnabled = allowed && user.muxEnabled,
            muxConcurrency = user.muxConcurrency,
            muxXudpConcurrency = user.muxXudpConcurrency,
            muxUdp443 = user.muxUdp443
        )

    /** A ready recipe by id, or null for the two values that are not recipes (blank, `custom`). */
    fun namedFragment(raw: String): FragmentProfile? {
        val id = raw.trim()
        if (id.isBlank() || FragmentChoice.isCustom(id)) return null
        return FragmentProfile.entries.firstOrNull { candidate ->
            candidate.enabled && candidate.id.equals(id, ignoreCase = true)
        }
    }

    /** The Mux twin of [namedFragment]. */
    fun namedMux(raw: String): MuxProfile? {
        val id = raw.trim()
        if (id.isBlank() || MuxChoice.isCustom(id)) return null
        return MuxProfile.entries.firstOrNull { candidate ->
            candidate.enabled && candidate.id.equals(id, ignoreCase = true)
        }
    }

    /**
     * Write one fragment recipe into the fields every consumer already reads.
     *
     * Both cores, the benchmark engine, the connection tuner and the wire read-out read
     * `fragmentPackets` / `fragmentLength` / … — never a recipe object. Materialising the recipe
     * here is therefore what makes one chooser reach every consumer at once, with no second code
     * path that could apply a different recipe to a different engine.
     */
    fun withFragmentProfile(base: AppSettings, profile: FragmentProfile): AppSettings =
        base.copy(
            fragmentProfileId = profile.id,
            fragmentEnabled = profile.enabled,
            fragmentPackets = profile.packets,
            fragmentLength = profile.length,
            fragmentInterval = profile.interval,
            fragmentMaxSplit = profile.maxSplit,
            fragmentInnerEnabled = profile.innerEnabled,
            fragmentInnerPackets = profile.innerPackets,
            fragmentInnerLength = profile.innerLength,
            fragmentInnerInterval = profile.innerInterval,
            fragmentInnerMaxSplit = profile.innerMaxSplit
        )

    /** The Mux twin of [withFragmentProfile]. */
    fun withMuxProfile(base: AppSettings, profile: MuxProfile): AppSettings =
        base.copy(
            muxProfileId = profile.id,
            muxEnabled = profile.enabled,
            muxConcurrency = profile.concurrency,
            muxXudpConcurrency = profile.xudpConcurrency,
            muxUdp443 = profile.udp443
        )

    /**
     * True for the two paths where multiplexing must never be turned on by a preference.
     *
     * XTLS Vision negotiates its own flow control and REALITY already carries a camouflage
     * handshake; smux on top of either is a slower connection with a louder fingerprint.
     */
    fun muxIsUnsafeFor(profile: ProxyProfile): Boolean =
        profile.security.lowercase().contains("reality") ||
            profile.raw.lowercase().contains("flow=xtls-rprx-vision")

    private fun fragmentFromFields(s: AppSettings): FragmentProfile =
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

    private fun muxFromFields(s: AppSettings): MuxProfile =
        MuxProfile.entries.firstOrNull { candidate ->
            candidate.enabled &&
                candidate.concurrency == s.muxConcurrency &&
                candidate.xudpConcurrency == s.muxXudpConcurrency &&
                candidate.udp443 == s.muxUdp443
        } ?: MuxProfile.entries
            .filter { it.enabled }
            .minByOrNull { abs(it.concurrency - s.muxConcurrency) }
            ?: MuxProfile.BALANCED

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

    // ─── Ladder & Hierarchical Thompson Sampling ────────────────────────────────────────

    /**
     * Finds the minimal effective ladder rung: the lowest rung that satisfies survival and
     * connectivity, ensuring minimal latency and jitter overhead.
     */
    fun minimalEffectiveRung(
        candidates: List<FragmentProfile>,
        scores: Map<String, Double>,
        minScoreThreshold: Double = 0.45
    ): FragmentProfile {
        val sorted = candidates.sortedBy { it.strength }
        for (rung in sorted) {
            val score = scores[rung.id] ?: 0.0
            if (score >= minScoreThreshold) {
                return rung
            }
        }
        return sorted.lastOrNull() ?: FragmentProfile.OFF
    }

    /**
     * Real reward: requires handshake + survival (>= 15s or >= 20KB transfer) + TTFB,
     * minus setting overhead (interval x packet count).
     */
    fun calculateRealReward(
        success: Boolean,
        survivalVerified: Boolean,
        bytesTransferred: Long,
        ttfbMs: Double,
        durationMs: Long,
        rung: FragmentProfile
    ): Double {
        if (!success || (!survivalVerified && bytesTransferred < 20_480L && durationMs < 15_000L)) {
            return 0.0
        }
        val ttfbScore = 1.0 / (1.0 + (ttfbMs.coerceAtLeast(1.0) / 160.0))
        val volumeScore = (bytesTransferred.coerceAtLeast(0L).toDouble() / 100_000.0).coerceIn(0.0, 1.0)
        val durationScore = (durationMs.coerceAtLeast(0L).toDouble() / 30_000.0).coerceIn(0.0, 1.0)
        val grossReward = ttfbScore * 0.40 + volumeScore * 0.35 + durationScore * 0.25
        val netReward = grossReward - rung.overheadPenalty
        return netReward.coerceIn(0.05, 1.0)
    }

    /**
     * High jitter / loss mitigation: single-connection TCP Mux creates severe HOL blocking
     * when packets drop or jitter spikes. Demote or turn Mux off to maintain responsive streams.
     */
    fun adaptForJitter(pair: TransportPair, jitterMs: Double, lossPercent: Double): TransportPair {
        if (jitterMs > 60.0 || lossPercent > 5.0) {
            val adjustedMux = if (pair.mux.enabled) {
                if (pair.mux.concurrency > 4) MuxProfile.STEALTH else pair.mux
            } else {
                MuxProfile.OFF
            }
            return pair.copy(mux = adjustedMux)
        }
        return pair
    }

    /**
     * Page-Hinkley change-point detector for rapid detection of DPI filtering rule shifts.
     */
    class PageHinkley(
        val threshold: Double = 0.25,
        val delta: Double = 0.05
    ) {
        private var mean = 0.0
        private var count = 0
        private var cumSum = 0.0
        private var minCumSum = 0.0

        fun update(sample: Double): Boolean {
            count++
            mean += (sample - mean) / count
            cumSum += (mean - sample - delta)
            if (cumSum < minCumSum) {
                minCumSum = cumSum
            }
            val phValue = cumSum - minCumSum
            return phValue > threshold
        }

        fun reset() {
            mean = 0.0
            count = 0
            cumSum = 0.0
            minCumSum = 0.0
        }
    }

    /**
     * Hierarchical Thompson Sampling with Beta distributions.
     * Backs off from cell -> operator -> global prior to resolve cold start and data sparsity.
     */
    object HierarchicalThompson {
        const val GLOBAL_PRIOR_ALPHA = 2.0
        const val GLOBAL_PRIOR_BETA = 1.0

        fun sampleBeta(alpha: Double, beta: Double, random: java.util.Random = java.util.Random()): Double {
            val a = max(0.1, alpha)
            val b = max(0.1, beta)
            val x = sampleGamma(a, random)
            val y = sampleGamma(b, random)
            return if (x + y <= 0.0) a / (a + b) else x / (x + y)
        }

        private fun sampleGamma(k: Double, random: java.util.Random): Double {
            if (k < 1.0) {
                val u = max(1e-9, random.nextDouble())
                return sampleGamma(k + 1.0, random) * u.pow(1.0 / k)
            }
            val d = k - 1.0 / 3.0
            val c = 1.0 / sqrt(9.0 * d)
            while (true) {
                val z = random.nextGaussian()
                val v = 1.0 + c * z
                if (v <= 0.0) continue
                val v3 = v * v * v
                val u = random.nextDouble()
                if (u < 1.0 - 0.0331 * (z * z) * (z * z)) return d * v3
                if (ln(max(1e-9, u)) < 0.5 * z * z + d * (1.0 - v3 + ln(max(1e-9, v3)))) return d * v3
            }
        }

        fun effectivePosterior(
            cellTries: Int,
            cellSuccess: Double,
            operatorTries: Int,
            operatorSuccess: Double,
            rung: FragmentProfile
        ): Pair<Double, Double> {
            val cellAlpha = cellTries * cellSuccess
            val cellBeta = cellTries * (1.0 - cellSuccess)

            val opAlpha = operatorTries * operatorSuccess * 0.5
            val opBeta = operatorTries * (1.0 - operatorSuccess) * 0.5

            val globalAlpha = GLOBAL_PRIOR_ALPHA * (1.0 - rung.overheadPenalty)
            val globalBeta = GLOBAL_PRIOR_BETA * (1.0 + rung.overheadPenalty)

            val totalAlpha = max(0.5, cellAlpha + opAlpha + globalAlpha)
            val totalBeta = max(0.5, cellBeta + opBeta + globalBeta)
            return Pair(totalAlpha, totalBeta)
        }
    }
}
