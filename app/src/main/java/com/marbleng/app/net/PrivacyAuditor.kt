package com.marbleng.app.net

import android.net.Network
import com.marbleng.app.core.LeakGuard
import com.marbleng.app.core.SocksHttpClient
import java.io.ByteArrayOutputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import org.json.JSONArray
import kotlin.math.roundToInt

data class PrivacyReport(
    val proxyIp: String,
    val underlayIp: String,
    val cloudflareLocation: String,
    val dnsServers: String,
    val ipLeakScore: Int,
    val dnsLeakScore: Int,
    val overallScore: Int,
    val healthy: Boolean,
    val note: String,
    /** Independent IPv6-only egress observations, empty means unverified (NOT proof of no leak). */
    val proxyIpv6: String = "",
    val underlayIpv6: String = "",
    /** MARBLE_LEAK_GUARD_V80: additional leak assessment from continuous monitoring */
    val leakAssessment: LeakGuard.LeakAssessment? = null
)

object PrivacyAuditor {
    private const val TRACE_URL = "https://www.cloudflare.com/cdn-cgi/trace"

    /**
     * User-triggered audit with two intentionally independent views:
     *  - proxy view travels through the active core's local SOCKS inbound;
     *  - underlay view is explicitly bound to Android's physical Network.
     *
     * The direct view is never scheduled in the background and is used only to prove that an
     * international destination sees a different address through the selected proxy.
     */
    fun audit(
        port: Int,
        underlay: Network?,
        leakGuard: LeakGuard? = null,
        requireIpv6: Boolean = false,
        underlayHasIpv6: Boolean = false
    ): PrivacyReport {
        val proxyTrace = runCatching {
            val response = SocksHttpClient.get(port, "www.cloudflare.com", "/cdn-cgi/trace", 6_000, 8_192)
            if (response.status in 200..399) String(response.body) else ""
        }.getOrDefault("")
        val proxyIp = traceValue(proxyTrace, "ip")
        val location = traceValue(proxyTrace, "loc")

        val underlayTrace = underlay?.let(::readUnderlayTrace).orEmpty()
        val underlayIp = traceValue(underlayTrace, "ip")
        // A dual-stack Cloudflare trace may choose IPv4 on BOTH paths and miss an IPv6 bypass.
        // Use an IPv6-only diagnostic origin via SOCKS domain ATYP, so the proxy's exit must
        // actually carry AAAA traffic. The physical comparison is opt-in with this manual audit.
        val proxyIpv6 = if (requireIpv6) runCatching {
            val response = SocksHttpClient.get(port, "ipv6.icanhazip.com", "/", 6_000, 256)
            if (response.status in 200..299) ipv6Literal(String(response.body)) else ""
        }.getOrDefault("") else ""
        val underlayIpv6 = if (requireIpv6 && underlayHasIpv6 && underlay != null) runCatching {
            readUnderlayIpv6(underlay)
        }.getOrDefault("") else ""

        val id = runCatching {
            String(SocksHttpClient.get(port, "bash.ws", "/id", 6_000, 4_096).body)
                .filter(Char::isDigit)
                .take(16)
        }.getOrDefault("")

        if (id.isNotBlank()) {
            // Each hostname is sent as SOCKS ATYP=domain. Android/system DNS never resolves it.
            for (index in 1..3) {
                runCatching {
                    SocksHttpClient.get(port, "$index.$id.bash.ws", "/", 3_500, 4_096)
                }
            }
        }

        val dnsPayload = if (id.isBlank()) "" else runCatching {
            String(SocksHttpClient.get(port, "bash.ws", "/dnsleak/test/$id?json", 8_000, 256_000).body)
        }.getOrDefault("")

        val dnsRows = parseDnsRows(dnsPayload)
        val dnsObservation = if (dnsRows.isEmpty()) "inconclusive" else dnsRows.joinToString(" • ")
        val knownEncryptedProvider = dnsRows.any(::isKnownEncryptedResolver)

        val baseIpScore = when {
            proxyIp.isBlank() -> 0
            underlayIp.isBlank() -> 85
            proxyIp == underlayIp -> 0
            else -> 100
        }
        val ipv6Score = when {
            !requireIpv6 -> 100
            proxyIpv6.isBlank() -> 40 // unverified IPv6 exit is NOT a passing audit
            underlayIpv6.isBlank() -> 85
            proxyIpv6 == underlayIpv6 -> 0
            else -> 100
        }
        val ipScore = minOf(baseIpScore, ipv6Score)
        val dnsScore = when {
            proxyIp.isBlank() -> 0
            dnsRows.isEmpty() -> 60
            knownEncryptedProvider -> 100
            else -> 85
        }
        val overall = (ipScore * 0.60 + dnsScore * 0.40).roundToInt().coerceIn(0, 100)
        val healthy = ipScore >= 85 && dnsScore >= 85 && proxyIp != underlayIp

        val note = buildString {
            append("IP score is based on a proxy-vs-physical egress comparison. ")
            append("DNS triggers used SOCKS domain addressing and the active core's encrypted resolver graph. ")
            when {
                proxyIp.isBlank() -> append("Proxy egress could not be verified.")
                underlayIp.isBlank() -> append("Physical comparison was unavailable; proxy egress alone was verified.")
                proxyIp == underlayIp -> append("Proxy and physical egress matched; treat this as a possible IP leak.")
                dnsRows.isEmpty() -> append("Egress separation passed, but the external DNS observation was inconclusive.")
                else -> append("Proxy egress separation and external DNS observation both completed.")
            }
            if (requireIpv6) append(when {
                proxyIpv6.isBlank() -> " IPv6 proxy egress could not be verified; the proxy exit may lack IPv6. This is not proof of a leak."
                underlayIpv6.isBlank() -> " IPv6 proxy egress worked, but the physical IPv6 comparison was unavailable."
                proxyIpv6 == underlayIpv6 -> " IPv6 proxy and physical egress matched: possible IPv6 bypass."
                else -> " IPv6 proxy egress differs from the physical IPv6 egress."
            })
        }

        // MARBLE_LEAK_GUARD_V80: Feed the report into the continuous LeakGuard
        val leakAssessment = leakGuard?.let { guard ->
            val report = PrivacyReport(
                proxyIp = proxyIp,
                underlayIp = underlayIp,
                cloudflareLocation = location,
                dnsServers = dnsObservation,
                ipLeakScore = ipScore,
                dnsLeakScore = dnsScore,
                overallScore = overall,
                healthy = healthy,
                note = note,
                proxyIpv6 = proxyIpv6,
                underlayIpv6 = underlayIpv6
            )
            guard.setKnownProxyIp(proxyIp)
            guard.setKnownUnderlayIp(underlayIp)
            guard.processReport(report)
        }

        return PrivacyReport(
            proxyIp = proxyIp,
            underlayIp = underlayIp,
            cloudflareLocation = location,
            dnsServers = dnsObservation,
            ipLeakScore = ipScore,
            dnsLeakScore = dnsScore,
            overallScore = overall,
            healthy = healthy,
            note = note,
            proxyIpv6 = proxyIpv6,
            underlayIpv6 = underlayIpv6,
            leakAssessment = leakAssessment
        )
    }

    private fun ipv6Literal(raw: String): String {
        val candidate = raw.lineSequence().firstOrNull().orEmpty().trim()
        if (!candidate.contains(':') || candidate.length > 64) return ""
        return runCatching { java.net.InetAddress.getByName(candidate) }
            .getOrNull()?.takeIf { it is java.net.Inet6Address }?.hostAddress.orEmpty()
    }

    private fun readUnderlayIpv6(network: Network): String = runCatching {
        val connection = network.openConnection(URL("https://ipv6.icanhazip.com/")) as HttpsURLConnection
        try {
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            if (connection.responseCode !in 200..299) return@runCatching ""
            connection.inputStream.use { input ->
                val bytes = ByteArray(256)
                ipv6Literal(String(bytes, 0, input.read(bytes).coerceAtLeast(0), Charsets.UTF_8))
            }
        } finally {
            connection.disconnect()
        }
    }.getOrDefault("")

    private fun readUnderlayTrace(network: Network): String = runCatching {
        val connection = network.openConnection(URL(TRACE_URL)) as HttpsURLConnection
        try {
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("Accept-Encoding", "identity")
            val status = connection.responseCode
            if (status !in 200..399) return@runCatching ""
            connection.inputStream.use { input ->
                val out = ByteArrayOutputStream(2_048)
                val buffer = ByteArray(1_024)
                while (out.size() < 8_192) {
                    val read = input.read(buffer, 0, minOf(buffer.size, 8_192 - out.size()))
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                }
                out.toString(Charsets.UTF_8.name())
            }
        } finally {
            connection.disconnect()
        }
    }.getOrDefault("")

    private fun traceValue(trace: String, key: String): String =
        Regex("(?m)^${Regex.escape(key)}=([^\\r\\n]+)$")
            .find(trace)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            .orEmpty()

    private fun parseDnsRows(raw: String): List<String> = runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            val row = array.optJSONObject(index) ?: return@mapNotNull null
            if (!row.optString("type").equals("dns", ignoreCase = true)) return@mapNotNull null
            val ip = row.optString("ip", "?")
            val owner = sequenceOf("asn_org", "provider", "asn")
                .map { key -> row.optString(key) }
                .firstOrNull(String::isNotBlank)
                .orEmpty()
            val country = row.optString("country_name", "?")
            listOf(ip, owner, country).filter(String::isNotBlank).joinToString(" / ")
        }
    }.getOrDefault(emptyList())

    private fun isKnownEncryptedResolver(row: String): Boolean {
        val normalized = row.lowercase()
        return listOf(
            "cloudflare", "google", "quad9", "woody", "opendns", "cisco",
            "adguard", "nextdns", "mullvad", "control d"
        ).any(normalized::contains)
    }
}
