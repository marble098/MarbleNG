package com.marbleng.app.core

import org.json.JSONArray
import org.json.JSONObject

object DnsDomainFaultPolicy {
    const val WINDOW_MS: Long = 10L * 60L * 1_000L

    const val DISTINCT_ENDPOINTS_FOR_FAULT: Int = 2

    const val MAX_DOMAINS: Int = 24

    data class DomainFault(
        val domain: String,
        val endpoints: Set<String> = emptySet(),
        val failures: Int = 0,
        val lastAtMs: Long = 0L
    ) {
        val namedEndpoints: Int get() = endpoints.count { it != UNATTRIBUTED }

        val decisive: Boolean get() = namedEndpoints >= DISTINCT_ENDPOINTS_FOR_FAULT

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

    private val CORE_CLIENT_TOKEN = Regex(
        """\b(dohl?|dotl?|udpl?|tcpl?|quicl?|h3l?)//([^\s,;>\"']{1,120})""",
        RegexOption.IGNORE_CASE
    )

    fun domainOf(line: String): String? {
        if (line.isBlank()) return null
        val match = FOR_DOMAIN.find(line)
            ?: EXCHANGE_FOR.find(line)
            ?: DOMAIN_FIELD.find(line)
            ?: return null
        val raw = match.groupValues.getOrNull(1)?.trim()?.trim('.', ',', ';', '"', '\'')
        return raw?.lowercase()?.takeIf(::isPlausibleDomain)
    }

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

    fun endpointOf(line: String): String {
        ResolverEvidencePolicy.endpointOf(line)
            ?.let { return ResolverEvidencePolicy.normalize(it) }
        val token = CORE_CLIENT_TOKEN.find(line) ?: return UNATTRIBUTED
        val scheme = token.groupValues.getOrNull(1).orEmpty().lowercase()
        val host = token.groupValues.getOrNull(2).orEmpty().trim().trim('.', ',', ';')
        if (host.isBlank()) return UNATTRIBUTED
        return "$scheme//${host.lowercase()}"
    }

    const val UNATTRIBUTED: String = "unattributed"

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
            val endpoint = endpointOf(line)
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

    fun faulted(faults: List<DomainFault>, nowMs: Long): List<DomainFault> =
        faults.filter { it.decisive && it.fresh(nowMs) }

    fun isFaulted(domain: String, faults: List<DomainFault>, nowMs: Long): Boolean {
        val key = domain.trim().lowercase()
        if (key.isBlank()) return false
        return faults.any { it.domain == key && it.decisive && it.fresh(nowMs) }
    }

    fun isDomainFaultLine(line: String, faults: List<DomainFault>, nowMs: Long): Boolean {
        val domain = domainOf(line) ?: return false
        return isFaulted(domain, faults, nowMs)
    }

    fun summary(faults: List<DomainFault>, nowMs: Long): String {
        val decisive = faulted(faults, nowMs)
        if (decisive.isEmpty()) return ""
        return decisive.take(4).joinToString(" • ") { fault ->
            "${fault.domain} (${fault.namedEndpoints} resolvers, ${fault.failures} failures)"
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
