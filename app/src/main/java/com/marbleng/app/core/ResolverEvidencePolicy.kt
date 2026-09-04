package com.marbleng.app.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * MARBLE_RESOLVER_EVIDENCE_V134
 *
 * Why the `DoH deadline` count kept climbing while the tunnel stayed up.
 *
 * V133 sized every DNS deadline from the measured link, which removed the *systematic* expiry (a
 * 1350 ms budget on a 1126 ms route). What the following session proved is that a correctly sized
 * deadline does not make a filtered resolver usable: an operator that disrupts DNS-over-HTTPS on a
 * per-endpoint basis still burns that whole budget, on every cold lookup, for as long as the
 * filtering window lasts. The runtime evidence was `state=CONNECTED`, a stable 2.5 h tunnel, four or
 * five responders per ping race — and 29 accumulated `DoH deadline` events.
 *
 * The defect underneath that number is structural, not a threshold: **the resolver-health loop was
 * open.** Marble already had every piece of the loop except the wire between them —
 *
 *  - [ResolverFailureClassifier] categorises a raw core-log line (deadline / EOF / TLS / cert);
 *  - [ResolverEndpointQuarantine] implements a per-endpoint circuit breaker;
 *  - Bug Finder reads the core log and *reports* the counts;
 *  - [XrayConfigHardener] emits the resolver list.
 *
 * …and nothing connected the observation to the emission. The encrypted server list was a fixed
 * `[user primary, user secondary] + [Cloudflare, Google, Quad9]` truncated to three, in that order,
 * forever. An endpoint that the operator was actively disrupting therefore stayed in the emitted
 * config at the same priority for the whole life of the installation, and every cold lookup paid its
 * full deadline before failover reached a resolver that could answer. Reporting the count is what
 * made `16 → 29` look like a regression; nothing in the stack ever *acted* on it.
 *
 * This policy is that wire. It is pure and side-effect free (persistence lives in
 * [MarbleIntelligence], reporting in [BugFinder]) so every rule below is unit-testable:
 *
 *  1. **attribution** — a resolver failure is attributed to the endpoint the core actually named,
 *     parsed out of the quoted transport URL in the log line. A line that names no endpoint is
 *     counted but attributed to nobody, so an unattributable failure can never demote a resolver.
 *  2. **decay** — counters halve on a fixed half-life, exactly like [TurboBackoffPolicy]'s streak.
 *     A filtering window ends; the evidence about it must end too, or a demotion becomes permanent
 *     and the resolver is lost for the life of the app.
 *  3. **demotion, never deletion** — a decisively failing endpoint moves to the end of the emitted
 *     list. It is never removed: Marble cannot prove a resolver is dead from inside a tunnel, and
 *     an encrypted fallback that is merely last is still a fallback. If *every* candidate is
 *     failing, the configured order is kept — reordering cannot help and only churns the config.
 *  4. **recovery** — a single proven answer (the adaptive DNS audit measures real RFC 8484 wire
 *     responses) clears the pressure on that endpoint immediately.
 *  5. **racing only when it pays** — Xray's `enableParallelQuery` fans every lookup out to all
 *     matching servers. That is the documented remedy for serial failover behind a dead resolver,
 *     and it is also three times the DNS traffic through the tunnel, so it is armed by evidence of a
 *     failing endpoint and never by preference.
 */
object ResolverEvidencePolicy {

    /**
     * One encrypted resolver endpoint and the decisive failures observed against it.
     *
     * Shutdown-safe kinds (cancellation, closed pipe) are deliberately not represented: they are
     * teardown artefacts, and counting them is how a clean reconnect used to look like a resolver
     * outage.
     */
    data class EndpointEvidence(
        val endpoint: String,
        val deadlines: Int = 0,
        val eof: Int = 0,
        val tls: Int = 0,
        val certExpired: Int = 0,
        val poisoned: Int = 0,
        val other: Int = 0,
        /** Wall clock of the most recent decisive failure; drives the demotion TTL. */
        val lastFailureAtMs: Long = 0L,
        /** Wall clock of the most recent proven answer; a success clears the pressure. */
        val lastSuccessAtMs: Long = 0L,
        /**
         * The instant the counters were last decayed from. 0 means "same as [lastFailureAtMs]".
         *
         * Decay has to be idempotent in absolute time. Halving repeatedly against the *original*
         * failure timestamp would compound on every observation pass: with a 15-minute half-life read
         * every 30 s, a storm of eight would collapse to zero two minutes after the first half-life
         * and the evidence would vanish while the filtering window was still open. Advancing the
         * anchor by the half-lives actually consumed makes the counter a pure function of elapsed
         * time, however often it is evaluated.
         */
        val decayAnchorAtMs: Long = 0L
    ) {
        /** Every failure that says something about the endpoint itself. */
        val decisiveFailures: Int
            get() = deadlines + eof + tls + certExpired + poisoned + other

        /**
         * An expired certificate is decisive on its own: that endpoint cannot answer until it is
         * renewed, so it must not wait for a streak to accumulate.
         */
        val certBroken: Boolean
            get() = certExpired > 0

        fun withFailure(kind: ResolverFailureKind, atMs: Long): EndpointEvidence = when (kind) {
            ResolverFailureKind.DEADLINE -> copy(
                deadlines = deadlines + 1, lastFailureAtMs = atMs, decayAnchorAtMs = atMs
            )
            ResolverFailureKind.EOF -> copy(
                eof = eof + 1, lastFailureAtMs = atMs, decayAnchorAtMs = atMs
            )
            ResolverFailureKind.TLS -> copy(
                tls = tls + 1, lastFailureAtMs = atMs, decayAnchorAtMs = atMs
            )
            ResolverFailureKind.CERT_EXPIRED -> copy(
                certExpired = certExpired + 1, lastFailureAtMs = atMs, decayAnchorAtMs = atMs
            )
            ResolverFailureKind.POISON -> copy(
                poisoned = poisoned + 1, lastFailureAtMs = atMs, decayAnchorAtMs = atMs
            )
            ResolverFailureKind.OTHER -> copy(
                other = other + 1, lastFailureAtMs = atMs, decayAnchorAtMs = atMs
            )
            ResolverFailureKind.CANCELLED, ResolverFailureKind.CLOSED_PIPE -> this
        }

        /** A proven answer resets the pressure; the endpoint re-enters the list at its old rank. */
        fun withSuccess(atMs: Long): EndpointEvidence =
            EndpointEvidence(endpoint = endpoint, lastSuccessAtMs = atMs)

        /**
         * Halve every counter for each full half-life since the last failure.
         *
         * Mirrors [TurboBackoffPolicy.decayedStreak]: a suppression that cannot decay outlives the
         * condition that caused it, which is precisely how a two-minute filtering window turned
         * into a resolver that never came back.
         */
        fun decayed(nowMs: Long, halfLifeMs: Long = DECAY_HALF_LIFE_MS): EndpointEvidence {
            if (decisiveFailures == 0) return this
            val anchor = if (decayAnchorAtMs > 0L) decayAnchorAtMs else lastFailureAtMs
            if (anchor <= 0L || nowMs <= anchor) return this
            val life = halfLifeMs.coerceAtLeast(1L)
            val halvings = ((nowMs - anchor) / life).toInt()
            if (halvings <= 0) return this
            if (halvings >= 31) return EndpointEvidence(endpoint, lastSuccessAtMs = lastSuccessAtMs)
            return copy(
                deadlines = deadlines shr halvings,
                eof = eof shr halvings,
                tls = tls shr halvings,
                certExpired = certExpired shr halvings,
                poisoned = poisoned shr halvings,
                other = other shr halvings,
                decayAnchorAtMs = anchor + halvings * life
            )
        }

        fun toJson(): JSONObject = JSONObject()
            .put("endpoint", endpoint)
            .put("deadlines", deadlines)
            .put("eof", eof)
            .put("tls", tls)
            .put("certExpired", certExpired)
            .put("poisoned", poisoned)
            .put("other", other)
            .put("lastFailureAtMs", lastFailureAtMs)
            .put("lastSuccessAtMs", lastSuccessAtMs)
            .put("decayAnchorAtMs", decayAnchorAtMs)

        companion object {
            fun fromJson(o: JSONObject): EndpointEvidence? {
                val endpoint = o.optString("endpoint").trim()
                if (endpoint.isBlank()) return null
                return EndpointEvidence(
                    endpoint = endpoint,
                    deadlines = o.optInt("deadlines").coerceAtLeast(0),
                    eof = o.optInt("eof").coerceAtLeast(0),
                    tls = o.optInt("tls").coerceAtLeast(0),
                    certExpired = o.optInt("certExpired").coerceAtLeast(0),
                    poisoned = o.optInt("poisoned").coerceAtLeast(0),
                    other = o.optInt("other").coerceAtLeast(0),
                    lastFailureAtMs = o.optLong("lastFailureAtMs"),
                    lastSuccessAtMs = o.optLong("lastSuccessAtMs"),
                    decayAnchorAtMs = o.optLong("decayAnchorAtMs")
                )
            }
        }
    }

    /** How a raw resolver-error count reads once it is divided by the window it happened in. */
    enum class Severity { CONTAINED, ELEVATED, SEVERE }

    data class Window(
        val events: Int,
        val windowMs: Long,
        val perMinute: Double,
        val severity: Severity,
        /** True when the window was too short/unknown to compute a meaningful rate. */
        val rateUnknown: Boolean
    )

    /** Decisive failures before an endpoint may be demoted (a broken certificate needs only one). */
    const val DEMOTE_FAILURES = 4

    /** A demotion expires after this even without a proven answer: filtering windows end. */
    const val DEMOTE_TTL_MS = 30L * 60_000L

    /** Failure counters halve every half-life. */
    const val DECAY_HALF_LIFE_MS = 15L * 60_000L

    /** Bounded persistence: a resolver list is a handful of endpoints, not a growing ledger. */
    const val MAX_ENDPOINTS = 8

    /** Below this observation window a rate is not meaningful, so the absolute count is used. */
    const val MIN_RATE_WINDOW_MS = 60_000L

    /** Legacy absolute threshold, kept only for windows too short to produce a rate. */
    const val ABSOLUTE_ELEVATED_EVENTS = 8

    const val ELEVATED_PER_MINUTE = 0.75
    const val SEVERE_PER_MINUTE = 3.0

    /**
     * Xray prints the transport URL of a failing resolver inside quotes:
     *
     * ```
     * [Error] app/dns: failed to retrieve response for www.google.com. >
     *   Post "https://1.1.1.1/dns-query": context deadline exceeded
     * ```
     *
     * Only a quoted, scheme-qualified URL is accepted. The bare-address forms a plaintext resolver
     * would print (`dial tcp 1.1.1.1:53`) are deliberately not parsed: the neighbouring text
     * (`failed to lookup ipv4 for www.google.com`) names a *domain*, and guessing an endpoint from
     * an ambiguous line is how a healthy resolver gets demoted for somebody else's failure.
     */
    private val QUOTED_ENDPOINT = Regex("\"([A-Za-z][A-Za-z0-9+\\-.]*://[^\"]{3,200})\"")

    /** Schemes that are unambiguously a resolver transport rather than an ordinary HTTP fetch. */
    private val RESOLVER_SCHEMES = setOf(
        "https+local", "dot", "dot+local", "quic", "quic+local", "h2c", "h3"
    )

    /** Comparison key: resolver URLs are matched case-insensitively, as the hardener dedupes them. */
    fun normalize(endpoint: String): String = endpoint.trim().lowercase()

    /**
     * The resolver endpoint a raw core-log line names, or null when it names none.
     *
     * Requires the line to be DNS-related *and* the URL to look like a resolver, so an outbound
     * HTTPS failure that happens to quote a destination URL is never charged to a resolver.
     */
    fun endpointOf(line: String): String? {
        if (line.isBlank()) return null
        if (!ResolverFailureClassifier.isDnsRelated(line)) return null
        val url = QUOTED_ENDPOINT.find(line)?.groupValues?.get(1)?.trim().orEmpty()
        if (url.isBlank()) return null
        return if (isResolverUrl(url)) url else null
    }

    private fun isResolverUrl(url: String): Boolean {
        val lower = url.lowercase()
        val scheme = lower.substringBefore("://", "")
        if (scheme in RESOLVER_SCHEMES) return true
        if (scheme != "https" && scheme != "http") return false
        val afterScheme = lower.substringAfter("://", "")
        val path = afterScheme.substringAfter('/', "")
        return path.contains("dns") || path.contains("query")
    }

    /**
     * Fold a batch of raw core-log lines into the persisted evidence set.
     *
     * Existing records are decayed first, so evidence about a filtering window that has passed
     * fades instead of accumulating for the life of the installation.
     */
    fun observe(
        lines: Sequence<String>,
        existing: List<EndpointEvidence>,
        nowMs: Long
    ): List<EndpointEvidence> {
        val byEndpoint = LinkedHashMap<String, EndpointEvidence>()
        existing.forEach { record ->
            val key = normalize(record.endpoint)
            if (key.isNotBlank()) byEndpoint[key] = record.decayed(nowMs)
        }
        lines.forEach { line ->
            val endpoint = endpointOf(line) ?: return@forEach
            val kind = ResolverFailureClassifier.classify(line) ?: return@forEach
            if (kind.isShutdownSafe) return@forEach
            val key = normalize(endpoint)
            val current = byEndpoint[key] ?: EndpointEvidence(endpoint = endpoint.trim())
            byEndpoint[key] = current.withFailure(kind, nowMs)
        }
        return byEndpoint.values
            .filter { it.decisiveFailures > 0 }
            .sortedByDescending { it.decisiveFailures }
            .take(MAX_ENDPOINTS)
    }

    /** Record one proven answer; the endpoint's pressure is cleared immediately. */
    fun recordSuccess(
        endpoint: String,
        existing: List<EndpointEvidence>,
        nowMs: Long
    ): List<EndpointEvidence> {
        val key = normalize(endpoint)
        if (key.isBlank()) return existing
        val cleared = EndpointEvidence(endpoint = endpoint.trim()).withSuccess(nowMs)
        val replaced = existing.map { if (normalize(it.endpoint) == key) cleared else it }
        val kept = if (replaced.any { normalize(it.endpoint) == key }) {
            replaced
        } else {
            replaced + cleared
        }
        return kept
            .filter { it.decisiveFailures > 0 || it.lastSuccessAtMs > 0L }
            .sortedByDescending { it.decisiveFailures }
            .take(MAX_ENDPOINTS)
    }

    private fun evidenceFor(
        endpoint: String,
        evidence: List<EndpointEvidence>
    ): EndpointEvidence? {
        val key = normalize(endpoint)
        return evidence.firstOrNull { normalize(it.endpoint) == key }
    }

    /**
     * True when this endpoint should move to the end of the emitted resolver list.
     *
     * All four conditions are required: decisive pressure, a recent failure (the demotion TTL), and
     * no proven answer since. A resolver that answered after its failures is not demoted, however
     * large the historical count is.
     */
    fun isDemoted(
        endpoint: String,
        evidence: List<EndpointEvidence>,
        nowMs: Long
    ): Boolean {
        val record = evidenceFor(endpoint, evidence) ?: return false
        if (record.decisiveFailures <= 0) return false
        if (!record.certBroken && record.decisiveFailures < DEMOTE_FAILURES) return false
        if (record.lastFailureAtMs <= 0L) return false
        if (nowMs - record.lastFailureAtMs > DEMOTE_TTL_MS) return false
        if (record.lastSuccessAtMs >= record.lastFailureAtMs && record.lastSuccessAtMs > 0L) {
            return false
        }
        return true
    }

    /**
     * Reorder a resolver candidate list so decisively failing endpoints go last.
     *
     * Stable within both groups, and a list where *every* candidate is failing is returned
     * unchanged: there is no better alternative, and churning the emitted config would cost a
     * reconnect for nothing.
     */
    fun order(
        candidates: List<String>,
        evidence: List<EndpointEvidence>,
        nowMs: Long
    ): List<String> {
        val distinct = candidates
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { normalize(it) }
        if (distinct.size < 2) return distinct
        val (healthy, failing) = distinct.partition { !isDemoted(it, evidence, nowMs) }
        return if (healthy.isEmpty()) distinct else healthy + failing
    }

    /** Endpoints of [candidates] that are currently demoted, for diagnostics and the config writer. */
    fun demoted(
        candidates: List<String>,
        evidence: List<EndpointEvidence>,
        nowMs: Long
    ): List<String> = candidates
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { normalize(it) }
        .filter { isDemoted(it, evidence, nowMs) }

    /**
     * Whether Xray should race its encrypted resolvers instead of failing over serially.
     *
     * Serial failover is the right default: one query, one answer, no fan-out. But when an endpoint
     * that is about to be emitted is decisively failing, serial failover pays that endpoint's whole
     * (now RTT-derived, therefore generous) deadline on *every* cold lookup before it reaches a
     * resolver that can answer. Xray's own documentation names this exact remedy for this exact
     * symptom. Racing is therefore armed by evidence about the emitted list, and disarmed as soon
     * as the evidence decays or a proven answer arrives.
     */
    fun parallelQueryJustified(
        candidates: List<String>,
        evidence: List<EndpointEvidence>,
        nowMs: Long
    ): Boolean = demoted(candidates, evidence, nowMs).isNotEmpty()

    /**
     * Read a raw resolver-error count honestly.
     *
     * `16` events in a five-minute disconnected session and `29` events in a two-and-a-half-hour
     * connected one are not the same observation, and the absolute comparison says the healthy
     * session is worse. The rate is the measurement; the count is only the numerator.
     */
    fun window(events: Int, windowMs: Long): Window {
        val count = events.coerceAtLeast(0)
        if (count == 0) {
            return Window(0, windowMs.coerceAtLeast(0L), 0.0, Severity.CONTAINED, rateUnknown = false)
        }
        if (windowMs < MIN_RATE_WINDOW_MS) {
            // Too short (or unknown) to divide by: fall back to the absolute reading rather than
            // inventing a rate out of a handful of seconds.
            val severity = if (count >= ABSOLUTE_ELEVATED_EVENTS) Severity.ELEVATED else Severity.CONTAINED
            return Window(count, windowMs.coerceAtLeast(0L), 0.0, severity, rateUnknown = true)
        }
        val perMinute = count / (windowMs / 60_000.0)
        val severity = when {
            perMinute >= SEVERE_PER_MINUTE -> Severity.SEVERE
            perMinute >= ELEVATED_PER_MINUTE -> Severity.ELEVATED
            else -> Severity.CONTAINED
        }
        return Window(count, windowMs, perMinute, severity, rateUnknown = false)
    }

    fun serialize(evidence: List<EndpointEvidence>): String =
        JSONArray().also { array ->
            evidence.take(MAX_ENDPOINTS).forEach { array.put(it.toJson()) }
        }.toString()

    fun deserialize(raw: String): List<EndpointEvidence> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length())
                .mapNotNull { array.optJSONObject(it) }
                .mapNotNull { EndpointEvidence.fromJson(it) }
                .filter { it.decisiveFailures > 0 || it.lastSuccessAtMs > 0L }
                .sortedByDescending { it.decisiveFailures }
                .take(MAX_ENDPOINTS)
        }.getOrDefault(emptyList())
    }

    /** One-line, endpoint-attributed description for diagnostics and Bug Finder. */
    fun describe(evidence: List<EndpointEvidence>, nowMs: Long): String {
        if (evidence.isEmpty()) return "no resolver endpoint failures attributed"
        return evidence
            .sortedByDescending { it.decisiveFailures }
            .joinToString(" • ") { record ->
                val parts = mutableListOf<String>()
                if (record.deadlines > 0) parts += "${record.deadlines} deadline"
                if (record.eof > 0) parts += "${record.eof} eof"
                if (record.tls > 0) parts += "${record.tls} tls"
                if (record.certExpired > 0) parts += "${record.certExpired} cert-expired"
                if (record.poisoned > 0) parts += "${record.poisoned} poisoned"
                if (record.other > 0) parts += "${record.other} other"
                val state = if (isDemoted(record.endpoint, evidence, nowMs)) "demoted" else "active"
                "${record.endpoint}: ${parts.joinToString("/").ifBlank { "0" }} ($state)"
            }
    }
}
