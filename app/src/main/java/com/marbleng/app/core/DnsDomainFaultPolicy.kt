package com.marbleng.app.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * MARBLE_DNS_DOMAIN_FAULT_V196 — telling a broken resolver apart from a broken name.
 *
 * ## The confusion this removes
 *
 * [ResolverEvidencePolicy] attributes every decisive DNS failure to the *endpoint* the core named,
 * and [MarbleIntelligence]'s storm guard counts those same failures to decide when the resolver
 * pool should abandon serial failover and race. Both are correct as long as the failure really is
 * the resolver's fault. In the field it often is not:
 *
 * ```
 * app/dns: failed to lookup ip for domain noveo.ir at DOH//1.0.0.1 > context deadline exceeded
 * app/dns: failed to lookup ip for domain noveo.ir at DOH//149.112.112.112 > context deadline exceeded
 * ```
 *
 * Two different, independent, healthy providers missing their budget on the *same name* is not a
 * resolver outage — it is a fact about that name: a domestic ccTLD whose authoritative servers are
 * slow or unreachable from foreign anycast, a domain that is being interfered with, or simply a
 * host that no longer exists. Counting those two lines as endpoint evidence produced two wrong
 * reactions at once: two perfectly good resolvers were pushed down the emitted order, and the
 * storm detector armed parallel racing (tripling DNS traffic through the tunnel) because "the pool
 * is failing" — the `DNS storm guard` warning users saw with no resolver actually broken.
 *
 * ## The rule
 *
 * A failure is a **domain fault** once the same name has failed against at least
 * [DISTINCT_ENDPOINTS_FOR_FAULT] *distinct* endpoints inside [WINDOW_MS]. From that moment its
 * lines stop being evidence about any endpoint and stop feeding the storm detector: they are
 * recorded against the name instead, where they are actually actionable (Bug Finder names the
 * domain, and the answer is "that site's DNS is the problem", not "your resolvers are broken").
 *
 * Everything else keeps working exactly as before: a single endpoint failing many different names
 * is still endpoint evidence, still demoted, still able to arm the storm guard. Only the specific
 * shape "many endpoints, one name" is reclassified, which is the shape that can never be fixed by
 * changing resolvers.
 *
 * The policy is pure: parsing, windowing and serialisation only, so every rule is unit-testable
 * without a core, a socket or Android.
 */
object DnsDomainFaultPolicy {

    /** How long one observation about a name stays evidence. */
    const val WINDOW_MS: Long = 10L * 60L * 1_000L

    /** Distinct endpoints that must fail the same name before it is called a domain fault. */
    const val DISTINCT_ENDPOINTS_FOR_FAULT: Int = 2

    /** Bounded memory: the noisiest names win, the rest age out. */
    const val MAX_DOMAINS: Int = 24

    /** One name and the endpoints observed failing it. */
    data class DomainFault(
        val domain: String,
        val endpoints: Set<String> = emptySet(),
        val failures: Int = 0,
        val lastAtMs: Long = 0L
    ) {
        /** True when enough independent resolvers failed this name to blame the name. */
        val decisive: Boolean get() = endpoints.size >= DISTINCT_ENDPOINTS_FOR_FAULT

        fun fresh(nowMs: Long): Boolean = lastAtMs > 0L && nowMs - lastAtMs <= WINDOW_MS

        fun toJson(): JSONObject = JSONObject()
            .put("domain", domain)
            .put("endpoints", JSONArray(endpoints.toList()))
            .put("failures", failures)
            .put("at", lastAtMs)

        companion object {
            fun fromJson(o: JSONObject): DomainFault {
                val array = o.optJSONArray("endpoints") ?: JSONArray()
                val endpoints = (0 until array.length())
                    .mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
                    .toSet()
                return DomainFault(
                    domain = o.optString("domain"),
                    endpoints = endpoints,
                    failures = o.optInt("failures"),
                    lastAtMs = o.optLong("at")
                )
            }
        }
    }

    private val FOR_DOMAIN = Regex("""for\s+domain\s+([A-Za-z0-9._-]+)""", RegexOption.IGNORE_CASE)
    private val EXCHANGE_FOR = Regex(
        """(?:exchange|lookup|query)\s+(?:failed\s+)?for\s+([A-Za-z0-9._-]+)""",
        RegexOption.IGNORE_CASE
    )
    private val DOMAIN_FIELD = Regex("""domain[:=]\s*([A-Za-z0-9._-]+)""", RegexOption.IGNORE_CASE)

    /**
     * The queried name inside one core log line, or null when the line names none.
     *
     * Both cores are covered: Xray writes `failed to lookup ip for domain <name> at <server>`,
     * sing-box writes `exchange failed for <name> IN A`. A value that is an IP literal, has no dot
     * or has no alphabetic TLD is rejected — an address is not a name, and a truncated token must
     * never become a permanent entry in the table.
     */
    fun domainOf(line: String): String? {
        if (line.isBlank()) return null
        val match = FOR_DOMAIN.find(line)
            ?: EXCHANGE_FOR.find(line)
            ?: DOMAIN_FIELD.find(line)
            ?: return null
        val raw = match.groupValues.getOrNull(1)?.trim()?.trim('.', ',', ';', '"', '\'')
        return raw?.lowercase()?.takeIf(::isPlausibleDomain)
    }

    /** A name Marble is willing to remember: dotted, alphabetic TLD, not an address. */
    fun isPlausibleDomain(candidate: String): Boolean {
        if (candidate.length !in 4..253) return false
        if (candidate.contains(':')) return false
        if (AddressFamilyPolicy.isLiteralIp(candidate)) return false
        val labels = candidate.split('.')
        if (labels.size < 2) return false
        if (labels.any { it.isBlank() || it.length > 63 }) return false
        val tld = labels.last()
        return tld.length >= 2 && tld.all { it.isLetter() }
    }

    /**
     * Fold raw core-log lines into the per-name table.
     *
     * Only decisive failures are counted, using the same classifier the endpoint evidence uses, so
     * a teardown artefact can no more blame a name than it can blame a resolver.
     */
    fun observe(
        lines: Sequence<String>,
        existing: List<DomainFault>,
        nowMs: Long
    ): List<DomainFault> {
        val byDomain = LinkedHashMap<String, DomainFault>()
        existing.filter { it.fresh(nowMs) }.forEach { byDomain[it.domain] = it }
        lines.forEach { line ->
            val kind = ResolverFailureClassifier.classify(line) ?: return@forEach
            if (kind.isShutdownSafe) return@forEach
            val domain = domainOf(line) ?: return@forEach
            val endpoint = ResolverEvidencePolicy.endpointOf(line)
                ?.let { ResolverEvidencePolicy.normalize(it) }
                ?.takeIf { it.isNotBlank() }
                ?: "unattributed"
            val current = byDomain[domain] ?: DomainFault(domain = domain)
            byDomain[domain] = current.copy(
                endpoints = current.endpoints + endpoint,
                failures = current.failures + 1,
                lastAtMs = nowMs
            )
        }
        return byDomain.values
            .sortedWith(compareByDescending<DomainFault> { it.endpoints.size }.thenByDescending { it.failures })
            .take(MAX_DOMAINS)
    }

    /** Names that currently carry a decisive, fresh fault. */
    fun faulted(faults: List<DomainFault>, nowMs: Long): List<DomainFault> =
        faults.filter { it.decisive && it.fresh(nowMs) }

    /** True when this exact name is currently a domain fault. */
    fun isFaulted(domain: String, faults: List<DomainFault>, nowMs: Long): Boolean {
        val key = domain.trim().lowercase()
        if (key.isBlank()) return false
        return faults.any { it.domain == key && it.decisive && it.fresh(nowMs) }
    }

    /**
     * True when this log line must not be counted as endpoint evidence.
     *
     * The caller passes the table *after* [observe], so the second failure of a name is already
     * enough: the line that made the fault decisive is itself part of the fault, and counting it
     * against the endpoint is the mis-attribution this policy exists to stop.
     */
    fun isDomainFaultLine(line: String, faults: List<DomainFault>, nowMs: Long): Boolean {
        val domain = domainOf(line) ?: return false
        return isFaulted(domain, faults, nowMs)
    }

    /** One line for Bug Finder: `noveo.ir (2 resolvers, 2 failures)`. */
    fun summary(faults: List<DomainFault>, nowMs: Long): String {
        val decisive = faulted(faults, nowMs)
        if (decisive.isEmpty()) return ""
        return decisive.take(4).joinToString(" • ") { fault ->
            "${fault.domain} (${fault.endpoints.size} resolvers, ${fault.failures} failures)"
        }
    }

    fun serialize(faults: List<DomainFault>): String {
        if (faults.isEmpty()) return ""
        val array = JSONArray()
        faults.take(MAX_DOMAINS).forEach { array.put(it.toJson()) }
        return array.toString()
    }

    fun deserialize(raw: String): List<DomainFault> {
        if (raw.isBlank()) return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until array.length())
            .mapNotNull { index -> array.optJSONObject(index) }
            .map { DomainFault.fromJson(it) }
            .filter { it.domain.isNotBlank() }
    }
}
