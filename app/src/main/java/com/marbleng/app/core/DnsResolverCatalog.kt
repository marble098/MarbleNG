package com.marbleng.app.core

/** One source of truth for stock IP-literal encrypted DNS fallbacks. */
object DnsResolverCatalog {
    val STOCK_DOH: List<String> = listOf(
        "https://1.1.1.1/dns-query",
        "https://8.8.8.8/dns-query",
        "https://9.9.9.9/dns-query",
        "https://149.112.112.112/dns-query",
        "https://1.0.0.1/dns-query"
    )

    fun candidates(primary: String, secondary: String): List<String> =
        (listOf(primary, secondary) + STOCK_DOH)
            .map(String::trim)
            .filter { it.startsWith("https://", ignoreCase = true) }
            .distinctBy(ResolverEvidencePolicy::normalize)

    /** Xray resolves a DoH endpoint marked SkipDNSResolve using the system dialer. */
    fun isXrayBootstrapSafe(endpoint: String): Boolean {
        val uri = runCatching { java.net.URI(endpoint.trim()) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.userInfo != null || uri.fragment != null) {
            return false
        }
        val host = uri.host?.removePrefix("[")?.removeSuffix("]")?.takeIf(String::isNotBlank) ?: return false
        if (!AddressFamilyPolicy.isLiteralIp(host)) return false
        return if (host.contains(':')) {
            runCatching { java.net.InetAddress.getByName(host) is java.net.Inet6Address }.getOrDefault(false)
        } else {
            true
        }
    }
}
