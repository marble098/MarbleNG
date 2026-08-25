package com.marbleng.app.net

import android.net.Network
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
    val note: String
)

object PrivacyAuditor {
    private const val TRACE_URL = "https://www.cloudflare.com/cdn-cgi/trace"

    /**
     * User-triggered audit with two intentionally independent views:
     *  - proxy view travels through Xray SOCKS;
     *  - underlay view is explicitly bound to Android's physical Network.
     *
     * The direct view is never scheduled in the background and is used only to prove that an
     * international destination sees a different address through the selected proxy.
     */
    fun audit(port: Int, underlay: Network?): PrivacyReport {
        val proxyTrace = runCatching {
            val response = SocksHttpClient.get(port, "www.cloudflare.com", "/cdn-cgi/trace", 6_000, 8_192)
            if (response.status in 200..399) String(response.body) else ""
        }.getOrDefault("")
        val proxyIp = traceValue(proxyTrace, "ip")
        val location = traceValue(proxyTrace, "loc")

        val underlayTrace = underlay?.let(::readUnderlayTrace).orEmpty()
        val underlayIp = traceValue(underlayTrace, "ip")

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

        val ipScore = when {
            proxyIp.isBlank() -> 0
            underlayIp.isBlank() -> 85
            proxyIp == underlayIp -> 0
            else -> 100
        }
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
            append("DNS triggers used SOCKS domain addressing and the Xray encrypted resolver graph. ")
            when {
                proxyIp.isBlank() -> append("Proxy egress could not be verified.")
                underlayIp.isBlank() -> append("Physical comparison was unavailable; proxy egress alone was verified.")
                proxyIp == underlayIp -> append("Proxy and physical egress matched; treat this as a possible IP leak.")
                dnsRows.isEmpty() -> append("Egress separation passed, but the external DNS observation was inconclusive.")
                else -> append("Proxy egress separation and external DNS observation both completed.")
            }
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
            note = note
        )
    }

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
