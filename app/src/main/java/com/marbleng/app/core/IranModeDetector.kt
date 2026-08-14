package com.marbleng.app.core

import android.content.Context
import android.net.Network
import android.telephony.TelephonyManager
import com.marbleng.app.model.IranModePolicy
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URL
import java.util.TimeZone
import javax.net.ssl.SNIHostName
import javax.net.ssl.SNIServerName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/** Filtering mechanisms MarbleNG can observe from the client side. */
enum class CensorTechnique {
    DNS_POISONING,
    SNI_FILTERING,
    TCP_RESET,
    PROTOCOL_ALLOWLIST,
    UDP_BLOCKED,
    NATIONAL_INTRANET,
    THROTTLING;

    val label: String
        get() = when (this) {
            DNS_POISONING -> "DNS poisoning"
            SNI_FILTERING -> "TLS/SNI filtering"
            TCP_RESET -> "TCP reset injection"
            PROTOCOL_ALLOWLIST -> "Port/protocol allowlist"
            UDP_BLOCKED -> "UDP transit blocked"
            NATIONAL_INTRANET -> "National-intranet only"
            THROTTLING -> "International throttling"
        }
}

/** One observation that contributed to the Iran Mode decision. */
data class IranSignal(
    val id: String,
    val label: String,
    val weight: Int,
    val detail: String = ""
)

data class IranModeState(
    val active: Boolean = false,
    val policy: IranModePolicy = IranModePolicy.AUTO,
    val confidence: Int = 0,
    val isp: IranIsp? = null,
    val carrierName: String = "",
    val asnOrganisation: String = "",
    val publicIp: String = "",
    val signals: List<IranSignal> = emptyList(),
    val techniques: Set<CensorTechnique> = emptySet(),
    val countermeasures: List<String> = emptyList(),
    val networkKey: String = "",
    val scanning: Boolean = false,
    val lastScanAt: Long = 0L,
    val summary: String = "Iran Mode standby"
) {
    val ispName: String get() = isp?.name.orEmpty()
    val ispShortName: String get() = isp?.shortName.orEmpty()
    val ispPersianName: String get() = isp?.persianName.orEmpty()
    val severity: FilterSeverity get() = isp?.severity ?: FilterSeverity.HEAVY

    /** Short one-line ISP identity for banners and notifications. */
    val ispLine: String
        get() = when {
            isp == null -> "Unidentified network"
            isp.asn > 0 -> "${isp.shortName} • AS${isp.asn}"
            else -> isp.shortName
        }
}

/**
 * Live detection of "am I on an Iranian ISP, and how is this link being filtered right now".
 *
 * Every probe is bound to the physical underlay when one is known, so a running tunnel can never
 * make the detector observe the exit country instead of the user's real ISP. Detection is scored:
 * soft evidence (timezone, IP-prefix table) can support a decision but never make one alone.
 */
class IranModeDetector(
    private val context: Context,
    private val intelligence: MarbleIntelligence
) {
    private val telephony: TelephonyManager? =
        runCatching { context.getSystemService(TelephonyManager::class.java) }.getOrNull()

    private data class Lookup(
        val ok: Boolean,
        val country: String = "",
        val asn: Int = 0,
        val organisation: String = "",
        val ip: String = ""
    )

    private enum class TlsOutcome { OK, RESET, TIMEOUT, BLOCKPAGE, ERROR }

    /**
     * Runs a detection sweep.
     *
     * @param tunnelActive when true and no physical underlay handle is available, remote lookups are
     *        skipped instead of being answered by the tunnel exit.
     * @param deepProbe runs the censorship-technique fingerprinting pass as well.
     */
    fun detect(
        policy: IranModePolicy,
        tunnelActive: Boolean,
        deepProbe: Boolean,
        previous: IranModeState = IranModeState()
    ): IranModeState {
        val networkKey = intelligence.currentSnapshot().key()
        if (policy == IranModePolicy.OFF) {
            return IranModeState(
                active = false,
                policy = policy,
                networkKey = networkKey,
                lastScanAt = System.currentTimeMillis(),
                summary = "Iran Mode disabled in settings"
            )
        }

        val underlay = runCatching { intelligence.currentUnderlyingNetwork() }.getOrNull()
        val remoteAllowed = underlay != null || !tunnelActive

        val signals = mutableListOf<IranSignal>()
        var score = 0
        var hardEvidence = false
        var foreignCountry = ""
        var isp: IranIsp? = null
        var organisation = ""
        var publicIp = ""

        // ---- Signal 1: the mobile network the device is actually registered on ----
        val networkOperator = runCatching { telephony?.networkOperator.orEmpty() }.getOrDefault("")
        val simOperator = runCatching { telephony?.simOperator.orEmpty() }.getOrDefault("")
        val carrierName = runCatching { telephony?.networkOperatorName.orEmpty() }.getOrDefault("")

        if (IranNetworkRegistry.isIranianOperatorCode(networkOperator)) {
            score += 60
            hardEvidence = true
            isp = IranNetworkRegistry.byOperatorCode(networkOperator)
            signals += IranSignal(
                "mcc",
                "Registered on an Iranian mobile network",
                60,
                listOfNotNull(
                    "MCC/MNC $networkOperator",
                    IranNetworkRegistry.operatorCodeName(networkOperator).takeIf { it.isNotBlank() }
                ).joinToString(" • ")
            )
        } else if (IranNetworkRegistry.isIranianOperatorCode(simOperator)) {
            score += 20
            isp = IranNetworkRegistry.byOperatorCode(simOperator)
            signals += IranSignal("sim", "Iranian SIM card present", 20, "SIM MCC/MNC $simOperator")
        }

        // ---- Signal 2: authoritative country/ASN of the physical uplink ----
        if (remoteAllowed) {
            val lookup = lookupPublicNetwork(underlay)
            if (lookup.ok && lookup.country.isNotBlank()) {
                publicIp = lookup.ip
                organisation = lookup.organisation
                if (lookup.country.equals("IR", true)) {
                    score += 70
                    hardEvidence = true
                    signals += IranSignal(
                        "geo",
                        "Uplink geolocates to Iran",
                        70,
                        listOfNotNull(
                            lookup.ip.takeIf { it.isNotBlank() },
                            lookup.organisation.takeIf { it.isNotBlank() }
                        ).joinToString(" • ")
                    )
                    val resolved = IranNetworkRegistry.resolve(lookup.asn, lookup.organisation, networkOperator)
                    if (isp == null || lookup.asn > 0) isp = resolved
                    if (IranNetworkRegistry.byAsn(lookup.asn) != null) {
                        score += 25
                        signals += IranSignal(
                            "asn",
                            "Known Iranian ISP autonomous system",
                            25,
                            "AS${lookup.asn} • ${resolved.name}"
                        )
                    }
                } else {
                    foreignCountry = lookup.country.uppercase()
                }
            }
        }

        // ---- Signal 3: offline IPv4 allocation table ----
        if (publicIp.isNotBlank() && IranNetworkRegistry.isKnownIranianIpv4(publicIp)) {
            score += 25
            signals += IranSignal("prefix", "Address inside a known Iranian allocation", 25, publicIp)
        }

        // ---- Signal 4: national block-page DNS injection ----
        val poisoned = probeDnsPoisoning(underlay)
        if (poisoned.isNotEmpty()) {
            score += 75
            hardEvidence = true
            signals += IranSignal(
                "dns-poison",
                "Filtering gateway is injecting block-page answers",
                75,
                poisoned.joinToString(" • ")
            )
        }

        // ---- Signal 5: domestic-only resolvers ----
        // Supporting evidence only: a foreign client can sometimes complete a TCP handshake with
        // these resolvers even though they refuse to answer, so this must never activate alone.
        val domestic = probeDomesticResolvers(underlay)
        if (domestic.publicReachable > 0) {
            val weight = if (domestic.publicReachable > 1) 40 else 30
            score += weight
            signals += IranSignal(
                "domestic-dns",
                "Iran-only resolvers are reachable",
                weight,
                "${domestic.publicReachable} of ${IranNetworkRegistry.PUBLIC_DOMESTIC_RESOLVERS.size} responded"
            )
        }
        if (domestic.privateReachable > 0) {
            score += 10
            signals += IranSignal(
                "domestic-private-dns",
                "Domestic private-space resolver reachable",
                10,
                "${domestic.privateReachable} responded"
            )
        }

        // ---- Signal 6: soft locale evidence ----
        if (runCatching { TimeZone.getDefault().id }.getOrDefault("") == "Asia/Tehran") {
            score += 12
            signals += IranSignal("timezone", "Device timezone is Asia/Tehran", 12)
        }
        val simCountry = runCatching { telephony?.simCountryIso.orEmpty() }.getOrDefault("")
        if (simCountry.equals("ir", true)) {
            score += 10
            signals += IranSignal("sim-country", "SIM country is Iran", 10)
        }

        // A confident non-Iranian geolocation always wins over soft evidence.
        if (foreignCountry.isNotBlank()) {
            score -= 120
            signals += IranSignal(
                "geo-foreign",
                "Uplink geolocates outside Iran",
                -120,
                foreignCountry
            )
        }

        val confidence = score.coerceIn(0, 100)
        val autoActive = hardEvidence && confidence >= 65 && foreignCountry.isBlank()
        val active = when (policy) {
            IranModePolicy.ALWAYS_ON -> true
            IranModePolicy.OFF -> false
            IranModePolicy.AUTO -> autoActive
        }

        var techniques: Set<CensorTechnique> = if (deepProbe) emptySet() else previous.techniques
        if (active && deepProbe) {
            techniques = fingerprintCensorship(underlay, remoteAllowed, domestic.publicReachable > 0, poisoned.isNotEmpty())
        } else if (active && poisoned.isNotEmpty()) {
            techniques = techniques + CensorTechnique.DNS_POISONING
        }

        val effectiveIsp = when {
            isp != null -> isp
            active -> IranNetworkRegistry.unknownIsp()
            else -> null
        }

        val state = IranModeState(
            active = active,
            policy = policy,
            confidence = confidence,
            isp = effectiveIsp,
            carrierName = carrierName,
            asnOrganisation = organisation,
            publicIp = publicIp,
            signals = signals.sortedByDescending { it.weight },
            techniques = techniques,
            networkKey = networkKey,
            scanning = false,
            lastScanAt = System.currentTimeMillis(),
            summary = summarise(policy, active, confidence, effectiveIsp, foreignCountry)
        )
        return state.copy(countermeasures = IranShield.countermeasures(state))
    }

    private fun summarise(
        policy: IranModePolicy,
        active: Boolean,
        confidence: Int,
        isp: IranIsp?,
        foreignCountry: String
    ): String = when {
        active && policy == IranModePolicy.ALWAYS_ON && confidence < 65 ->
            "Iran Mode forced on manually"
        active && isp != null ->
            "Iran Mode active on ${isp.shortName} • ${confidence}% confidence"
        active ->
            "Iran Mode active • ${confidence}% confidence"
        foreignCountry.isNotBlank() ->
            "Non-Iranian uplink detected ($foreignCountry) • Iran Mode idle"
        else ->
            "No Iranian ISP detected • Iran Mode idle"
    }

    // ------------------------------------------------------------------
    // Probes
    // ------------------------------------------------------------------

    private val lookupEndpoints = listOf(
        "https://speed.cloudflare.com/meta",
        "https://ipinfo.io/json",
        "https://ipapi.co/json/",
        "https://api.country.is/",
        "https://www.cloudflare.com/cdn-cgi/trace"
    )

    private fun lookupPublicNetwork(network: Network?): Lookup {
        for (endpoint in lookupEndpoints) {
            val result = runCatching { fetchLookup(network, endpoint) }.getOrNull()
            if (result != null && result.ok && result.country.isNotBlank()) return result
        }
        return Lookup(ok = false)
    }

    private fun fetchLookup(network: Network?, endpoint: String): Lookup {
        val url = URL(endpoint)
        val connection = (network?.openConnection(url) ?: url.openConnection()) as HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "MarbleNG/IranMode")
        connection.setRequestProperty("Accept", "application/json, text/plain")
        return try {
            if (connection.responseCode !in 200..299) return Lookup(ok = false)
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            parseLookup(body)
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    private fun parseLookup(body: String): Lookup {
        val trimmed = body.trim()
        if (trimmed.startsWith("{")) {
            val json = JSONObject(trimmed)
            val country = listOf("country", "country_code", "countryCode")
                .map { json.optString(it) }
                .firstOrNull { it.isNotBlank() }
                .orEmpty()
            val organisation = listOf("asOrganization", "org", "isp", "asn_org", "organisation")
                .map { json.optString(it) }
                .firstOrNull { it.isNotBlank() }
                .orEmpty()
            val asn = extractAsn(json)
            val ip = listOf("ip", "clientIp", "query")
                .map { json.optString(it) }
                .firstOrNull { it.isNotBlank() }
                .orEmpty()
            return Lookup(country.isNotBlank(), country, asn, organisation, ip)
        }

        // Cloudflare trace: key=value lines.
        val values = trimmed.lines()
            .mapNotNull { line ->
                val key = line.substringBefore('=', "")
                val value = line.substringAfter('=', "")
                if (key.isBlank() || value.isBlank()) null else key.trim() to value.trim()
            }
            .toMap()
        val country = values["loc"].orEmpty()
        return Lookup(country.isNotBlank(), country, 0, "", values["ip"].orEmpty())
    }

    private fun extractAsn(json: JSONObject): Int {
        json.optInt("asn", 0).takeIf { it > 0 }?.let { return it }
        val candidates = listOf(json.optString("asn"), json.optString("as"), json.optString("org"))
        for (candidate in candidates) {
            val match = Regex("AS(\\d+)", RegexOption.IGNORE_CASE).find(candidate)
            if (match != null) return match.groupValues[1].toIntOrNull() ?: 0
        }
        return 0
    }

    /** Returns the domains whose answers came back as national block-page addresses. */
    private fun probeDnsPoisoning(network: Network?): List<String> {
        val hits = mutableListOf<String>()
        for (domain in IranNetworkRegistry.FILTERED_PROBE_DOMAINS) {
            val addresses = runCatching {
                network?.getAllByName(domain) ?: InetAddress.getAllByName(domain)
            }.getOrNull() ?: continue
            val injected = addresses.any {
                IranNetworkRegistry.isBlockPageAddress(it.hostAddress.orEmpty())
            }
            if (injected) hits += domain
            if (hits.size >= 2) break
        }
        return hits
    }

    private data class DomesticProbe(val publicReachable: Int, val privateReachable: Int)

    private fun probeDomesticResolvers(network: Network?): DomesticProbe {
        var publicHits = 0
        var privateHits = 0
        for (resolver in IranNetworkRegistry.DOMESTIC_RESOLVERS) {
            val reachable = tcpReachable(network, resolver, 53, 1_600)
            if (!reachable) continue
            if (resolver.startsWith("10.")) privateHits++ else publicHits++
            if (publicHits >= 2) break
        }
        return DomesticProbe(publicHits, privateHits)
    }

    private fun fingerprintCensorship(
        network: Network?,
        remoteAllowed: Boolean,
        domesticReachable: Boolean,
        dnsPoisoned: Boolean
    ): Set<CensorTechnique> {
        val found = linkedSetOf<CensorTechnique>()
        if (dnsPoisoned) found += CensorTechnique.DNS_POISONING
        if (!remoteAllowed) return found

        val controlStart = System.currentTimeMillis()
        val control = tlsProbe(network, IranNetworkRegistry.CONTROL_DOMAIN)
        val controlMs = System.currentTimeMillis() - controlStart
        val controlOk = control == TlsOutcome.OK

        if (controlOk && controlMs > 3_500) found += CensorTechnique.THROTTLING

        if (controlOk) {
            for (domain in IranNetworkRegistry.FILTERED_PROBE_DOMAINS.take(2)) {
                when (tlsProbe(network, domain)) {
                    TlsOutcome.RESET -> {
                        found += CensorTechnique.SNI_FILTERING
                        found += CensorTechnique.TCP_RESET
                    }
                    TlsOutcome.TIMEOUT -> found += CensorTechnique.SNI_FILTERING
                    TlsOutcome.BLOCKPAGE -> found += CensorTechnique.DNS_POISONING
                    else -> Unit
                }
                if (CensorTechnique.SNI_FILTERING in found) break
            }

            // Port allowlist: 443 works while well-known non-allowlisted ports are dropped.
            val altPortsBlocked = !tcpReachable(network, "1.1.1.1", 853, 2_500) &&
                !tcpReachable(network, "8.8.8.8", 853, 2_500)
            if (altPortsBlocked) found += CensorTechnique.PROTOCOL_ALLOWLIST

            if (!udpDnsReachable(network, "8.8.8.8")) found += CensorTechnique.UDP_BLOCKED
        } else {
            val foreignTcp = tcpReachable(network, "1.1.1.1", 443, 2_500) ||
                tcpReachable(network, "8.8.8.8", 443, 2_500)
            if (!foreignTcp && domesticReachable) found += CensorTechnique.NATIONAL_INTRANET
        }
        return found
    }

    private fun tlsProbe(network: Network?, host: String): TlsOutcome {
        var plain: Socket? = null
        var tls: SSLSocket? = null
        return try {
            val addresses = runCatching {
                network?.getAllByName(host) ?: InetAddress.getAllByName(host)
            }.getOrNull() ?: return TlsOutcome.ERROR
            val target = addresses.firstOrNull() ?: return TlsOutcome.ERROR
            if (IranNetworkRegistry.isBlockPageAddress(target.hostAddress.orEmpty())) {
                return TlsOutcome.BLOCKPAGE
            }

            plain = network?.socketFactory?.createSocket() ?: Socket()
            plain.soTimeout = 5_000
            plain.connect(InetSocketAddress(target, 443), 5_000)

            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            tls = factory.createSocket(plain, host, 443, true) as SSLSocket
            tls.soTimeout = 5_000
            val params = tls.sslParameters
            params.serverNames = listOf<SNIServerName>(SNIHostName(host))
            tls.sslParameters = params
            tls.startHandshake()
            TlsOutcome.OK
        } catch (timeout: SocketTimeoutException) {
            TlsOutcome.TIMEOUT
        } catch (error: Throwable) {
            val text = (error.message ?: "").lowercase()
            if (text.contains("reset") || text.contains("closed by peer") || text.contains("broken pipe")) {
                TlsOutcome.RESET
            } else {
                TlsOutcome.ERROR
            }
        } finally {
            runCatching { tls?.close() }
            runCatching { plain?.close() }
        }
    }

    private fun tcpReachable(network: Network?, host: String, port: Int, timeoutMs: Int): Boolean {
        var socket: Socket? = null
        return try {
            socket = network?.socketFactory?.createSocket() ?: Socket()
            socket.connect(InetSocketAddress(InetAddress.getByName(host), port), timeoutMs)
            true
        } catch (error: Throwable) {
            false
        } finally {
            runCatching { socket?.close() }
        }
    }

    /**
     * Foreign UDP reachability. UDP/53 is the one datagram service Iranian networks keep open even
     * during clampdowns, so losing it means every UDP-based transport (QUIC, WireGuard, Hysteria)
     * is unusable on this link.
     */
    private fun udpDnsReachable(network: Network?, resolver: String): Boolean {
        var socket: DatagramSocket? = null
        return try {
            socket = DatagramSocket()
            network?.let { runCatching { it.bindSocket(socket) } }
            socket.soTimeout = 2_500
            val query = dnsQuery("example.com")
            socket.send(DatagramPacket(query, query.size, InetAddress.getByName(resolver), 53))
            val buffer = ByteArray(512)
            socket.receive(DatagramPacket(buffer, buffer.size))
            true
        } catch (error: Throwable) {
            false
        } finally {
            runCatching { socket?.close() }
        }
    }

    /** Minimal DNS A query packet. */
    private fun dnsQuery(domain: String): ByteArray {
        val out = ByteArrayOutputStream()
        // Header: id, standard query with recursion desired, one question, no records.
        val header = intArrayOf(0x4D, 0x42, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
        header.forEach { out.write(it) }
        domain.split('.').forEach { label ->
            val bytes = label.toByteArray(Charsets.US_ASCII)
            out.write(bytes.size)
            out.write(bytes)
        }
        out.write(0x00)
        // QTYPE = A, QCLASS = IN
        intArrayOf(0x00, 0x01, 0x00, 0x01).forEach { out.write(it) }
        return out.toByteArray()
    }
}
