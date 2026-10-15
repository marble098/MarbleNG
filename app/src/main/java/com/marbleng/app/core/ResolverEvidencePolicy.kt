package com.marbleng.app.core

import org.json.JSONArray
import org.json.JSONObject

object ResolverEvidencePolicy {
    data class EndpointEvidence(
        val endpoint: String,
        val deadlines: Int = 0,
        val eof: Int = 0,
        val tls: Int = 0,
        val certExpired: Int = 0,
        val poisoned: Int = 0,
        val other: Int = 0,
        val lastFailureAtMs: Long = 0L,
        val lastSuccessAtMs: Long = 0L,
        val decayAnchorAtMs: Long = 0L
    ) {
        val decisiveFailures: Int
            get() = deadlines + eof + tls + certExpired + poisoned + other

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

        fun withSuccess(atMs: Long): EndpointEvidence =
            EndpointEvidence(endpoint = endpoint, lastSuccessAtMs = atMs)

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

    enum class Severity { CONTAINED, ELEVATED, SEVERE }

    data class Window(
        val events: Int,
        val windowMs: Long,
        val perMinute: Double,
        val severity: Severity,
        val rateUnknown: Boolean
    )

    const val DEMOTE_FAILURES = 4

    const val DEMOTE_TTL_MS = 30L * 60_000L

    const val DECAY_HALF_LIFE_MS = 15L * 60_000L

    const val MAX_ENDPOINTS = 8

    const val MIN_RATE_WINDOW_MS = 60_000L

    const val ABSOLUTE_ELEVATED_EVENTS = 8

    const val ELEVATED_PER_MINUTE = 0.75
    const val SEVERE_PER_MINUTE = 3.0

    private val QUOTED_ENDPOINT = Regex("""(?:"|'|\b)((?:[A-Za-z][A-Za-z0-9+\-.]*://)[^\s"'<>]{3,200})""")

    private val RESOLVER_SCHEMES = setOf(
        "https+local", "dot", "dot+local", "quic", "quic+local", "h2c", "h3"
    )

    fun normalize(endpoint: String): String = endpoint.trim().lowercase()

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

    @Deprecated("Superseded by the rotating overload; use order(..., seed = )")
    fun orderLegacy(
        candidates: List<String>,
        evidence: List<EndpointEvidence>,
        nowMs: Long
    ): List<String> =
        order(candidates, evidence = evidence, nowMs = nowMs, seed = "")

    fun excluded(
        candidates: List<String>,
        evidence: List<EndpointEvidence>,
        nowMs: Long
    ): List<String> = candidates
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { normalize(it) }
        .filter { candidate ->
            isDomesticResolver(candidate) || run {
                val record = evidenceFor(candidate, evidence) ?: return@run false
                record.certBroken &&
                    record.lastFailureAtMs > 0L &&
                    nowMs - record.lastFailureAtMs <= CERT_BROKEN_TTL_MS &&
                    !(record.lastSuccessAtMs > 0L && record.lastSuccessAtMs >= record.lastFailureAtMs)
            }
        }

    fun withoutExcluded(
        candidates: List<String>,
        evidence: List<EndpointEvidence>,
        nowMs: Long
    ): List<String> {
        val out = excluded(candidates, evidence, nowMs).map { normalize(it) }.toSet()
        if (out.isEmpty()) return candidates
        val kept = candidates.filter { normalize(it) !in out }

        if (kept.isNotEmpty()) return kept
        return candidates.filter { !isDomesticResolver(it) }
    }

    fun isDomesticResolver(endpoint: String): Boolean {
        val host = endpoint.trim()
            .substringAfter("://", endpoint.trim())
            .substringBefore('/')
            .substringBefore('?')
            .removePrefix("[").substringBefore(']')
            .substringBefore(':')
            .trim()
            .lowercase()
            .trimEnd('.')
        if (host.isBlank()) return false
        if (host in IranNetworkRegistry.DOMESTIC_RESOLVERS) return true
        if (host == "ir" || host.endsWith(".ir")) return true
        return DOMESTIC_RESOLVER_HOSTS.any { host == it || host.endsWith(".$it") }
    }

    private val DOMESTIC_RESOLVER_HOSTS = listOf(
        "shecan.ir", "electrotm.org", "begzar.ir", "403.online", "radar.game", "pishgaman.net"
    )

    const val CERT_BROKEN_TTL_MS = 6L * 60L * 60_000L

    fun demoted(
        candidates: List<String>,
        evidence: List<EndpointEvidence>,
        nowMs: Long
    ): List<String> = candidates
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { normalize(it) }
        .filter { isDemoted(it, evidence, nowMs) }

    const val MIN_DIVERSE_PROVIDERS = 4

    const val OPERATOR_BLACKLIST_TTL_MS = 30L * 60_000L

    const val OPERATOR_RETEST_MS = 20L * 60_000L

    const val NOT_BLACKLISTED = -1L

    private const val ROTATION_EPOCH_MS = 10L * 60_000L

    private val OPERATOR_BY_SUFFIX = listOf(
        "cloudflare-dns.com" to "cloudflare",
        "1.1.1.1" to "cloudflare",
        "1.0.0.1" to "cloudflare",
        "dns.google" to "google",
        "8.8.8.8" to "google",
        "8.8.4.4" to "google",
        "dns.quad9.net" to "quad9",
        "9.9.9.9" to "quad9",
        "149.112.112.112" to "quad9",
        "dns.adguard-dns.com" to "adguard",
        "94.140.14.14" to "adguard",
        "dns.nextdns.io" to "nextdns",
        "dns.mullvad.net" to "mullvad"
    )

    fun providerFamily(endpoint: String): String {
        val host = endpoint
            .substringAfter("://", endpoint)
            .substringBefore('/')
            .substringBefore(':')
            .trim()
            .lowercase()
        return OPERATOR_BY_SUFFIX.firstOrNull { host == it.first || host.endsWith("." + it.first) }
            ?.second
            ?: host
    }

    fun diversity(candidates: List<String>): Int =
        candidates.map { providerFamily(it) }.filter { it.isNotBlank() }.distinct().size

    fun order(
        candidates: List<String>,
        evidence: List<EndpointEvidence>,
        nowMs: Long,
        seed: String = ""
    ): List<String> {
        val distinct = candidates
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { normalize(it) }
        if (distinct.size < 2) return distinct
        val (healthy, failing) = distinct.partition { !isDemoted(it, evidence, nowMs) }
        if (healthy.isEmpty()) return distinct
        if (seed.isBlank()) return healthy + failing
        val epoch = (nowMs / ROTATION_EPOCH_MS).toInt()
        val shift = Math.floorMod(seed.hashCode() xor epoch, healthy.size)
        val rotated = healthy.drop(shift) + healthy.take(shift)
        return rotated + failing
    }

    fun orderForEmission(
        candidates: List<String>,
        evidence: List<EndpointEvidence>,
        nowMs: Long,
        seed: String
    ): List<String> {
        val distinct = candidates.map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy(::normalize)
        if (distinct.size < 2 || seed.isBlank()) return order(distinct, evidence, nowMs, seed)
        val (healthy, failing) = distinct.partition { !isDemoted(it, evidence, nowMs) }
        val epoch = (nowMs / ROTATION_EPOCH_MS).toInt()
        val group = healthy.ifEmpty { distinct }
        val shift = Math.floorMod(seed.hashCode() xor epoch, group.size)
        val rotated = group.drop(shift) + group.take(shift)
        return if (healthy.isEmpty()) rotated else rotated + failing
    }

    fun selectForEmission(
        candidates: List<String>,
        evidence: List<EndpointEvidence>,
        nowMs: Long,
        seed: String,
        limit: Int
    ): List<String> = orderForEmission(candidates, evidence, nowMs, seed)
        .take(limit.coerceAtLeast(0))

    fun blacklistedOperators(
        candidates: List<String>,
        evidence: List<EndpointEvidence>,
        nowMs: Long
    ): Map<String, Long> {
        val byOperator = candidates
            .filter { it.isNotBlank() }
            .groupBy { providerFamily(it) }
        val out = HashMap<String, Long>()
        byOperator.forEach { (operator, endpoints) ->
            if (operator.isBlank()) return@forEach
            if (endpoints.size < 2) return@forEach
            val onlyDemoted = endpoints.all { isDemoted(it, evidence, nowMs) }
            if (!onlyDemoted) return@forEach
            val newestFailure = endpoints
                .mapNotNull { evidenceFor(it, evidence) }
                .maxOfOrNull { it.lastFailureAtMs }
                ?: 0L
            if (newestFailure > 0L) out[operator] = newestFailure
        }
        return out.filterValues { nowMs - it <= OPERATOR_BLACKLIST_TTL_MS }
    }

    fun retestDue(
        operator: String,
        blacklistedAtMs: Long,
        nowMs: Long,
        retestMs: Long = OPERATOR_RETEST_MS
    ): Boolean = blacklistedAtMs > 0L &&
        nowMs - blacklistedAtMs >= retestMs

    fun diversified(
        candidates: List<String>,
        fallbacks: List<String>,
        nowMs: Long = System.currentTimeMillis()
    ): List<String> {
        val merged = LinkedHashSet<String>()
        merged += candidates.map { it.trim() }.filter { it.isNotBlank() }
        if (diversity(merged.toList()) < MIN_DIVERSE_PROVIDERS) {
            fallbacks.forEach { candidate ->
                val trimmed = candidate.trim()
                if (trimmed.isNotBlank()) merged += trimmed
            }
        }
        return merged.toList()
    }

    fun parallelQueryJustified(
        candidates: List<String>,
        evidence: List<EndpointEvidence>,
        nowMs: Long
    ): Boolean = demoted(candidates, evidence, nowMs).isNotEmpty()

    fun window(events: Int, windowMs: Long): Window {
        val count = events.coerceAtLeast(0)
        if (count == 0) {
            return Window(0, windowMs.coerceAtLeast(0L), 0.0, Severity.CONTAINED, rateUnknown = false)
        }
        if (windowMs < MIN_RATE_WINDOW_MS) {
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
