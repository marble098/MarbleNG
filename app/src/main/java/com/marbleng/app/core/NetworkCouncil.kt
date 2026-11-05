package com.marbleng.app.core

import com.marbleng.app.model.AddressFamilyMode
import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.FragmentStrategy
import com.marbleng.app.model.PingBudget
import com.marbleng.app.model.PowerProfile
import com.marbleng.app.model.WorkloadProfile
import com.marbleng.app.model.parseFragmentStrategy
import com.marbleng.app.model.parsePowerProfile
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * MARBLE_NETWORK_COUNCIL_V236 — twelve specialists, one decision.
 *
 * The product already measures a great deal: [RouteProbe] measures a route, [CensorshipLab]
 * names interference, [IpFamilyScanner] decides a family, [CorePower] projects a device onto the
 * cores' knobs. What it never had is the *judgement* between them — one place that looks at the
 * network the phone is actually on (Wi-Fi, mobile data, whatever operator is behind it), measures
 * the path from every side, and then writes the best settings it can justify.
 *
 * This file is the judgement, and it is deliberately **pure**. It owns no socket, no clock and no
 * Android type: it takes [NetworkEvidence] that [NetworkScanner] collected, lets each of the
 * twelve seats speak, resolves their disagreements by measured confidence, and returns both the
 * findings and a ready [AppSettings] value. That is what makes it testable on a JVM — every rule
 * below is pinned by `NetworkCouncilV236Test`.
 *
 * The council's constitution, which no seat may suspend:
 *
 *  * **Evidence before instinct.** A seat that measured nothing speaks at low confidence and can
 *    lose to a seat that measured something; the operator table may advise, never overrule, a
 *    completed handshake probe.
 *  * **Never spend a protection you do not need.** A clean path is left on the defaults
 *    (`FragmentStrategy.AUTO`), never pushed to `FULL` "just in case".
 *  * **Never touch what speed may not buy.** Routing, split tunnel, identity, certificate
 *    verification and the DNS fail-closed posture are not on the table at all: no [TuningField]
 *    reaches them.
 *  * **Bounded numbers.** Every value is clamped to the same ranges the config builders enforce
 *    (`PingBudget`, 1280..1500 MTU, mux 1..128, xUDP 1..1024), so an applied plan can never be a
 *    config the pinned cores reject.
 *  * **Multi-core safety.** The caller passes `muxSafe`; when the current route cannot carry the
 *    multiplexer, the core seat's proposal is `false`, not a broken mux.
 */
object NetworkCouncil {
    const val MARKER: String = "MARBLE_NETWORK_COUNCIL_V236"

    const val MTU_MIN: Int = 1280
    const val MTU_MAX: Int = 1500
    const val VPN_HEADROOM: Int = 60
    const val MUX_MIN: Int = 1
    const val MUX_MAX: Int = 128
    const val XUDP_MIN: Int = 1
    const val XUDP_MAX: Int = 1024
    const val BUFFER_MIN_KB: Int = 0
    const val BUFFER_MAX_KB: Int = 1024
    const val HAPPY_EYEBALLS_MIN_MS: Int = 20
    const val HAPPY_EYEBALLS_MAX_MS: Int = 400

    // ------------------------------------------------------------------------------------------
    // The evidence model the scanner fills in
    // ------------------------------------------------------------------------------------------

    enum class LinkTransport(val label: String) {
        WIFI("Wi-Fi"),
        CELLULAR("Mobile data"),
        ETHERNET("Ethernet"),
        VPN("VPN"),
        OTHER("Other link"),
        NONE("No network")
    }

    /** One link-layer snapshot. [signalLevel] is the platform's 0..4 bar count, -1 when unknown. */
    data class RadioEvidence(
        val transport: LinkTransport = LinkTransport.NONE,
        val operatorName: String = "",
        val operatorCode: String = "",
        val networkType: String = "",
        val signalLevel: Int = -1,
        val signalDbm: Int = 0,
        val linkDownKbps: Int = 0,
        val linkUpKbps: Int = 0,
        val metered: Boolean = false,
        val validated: Boolean = false,
        val captivePortal: Boolean = false,
        val interfaceMtu: Int = 0,
        val vpnActive: Boolean = false
    )

    /** TCP handshake truth: [samplesMs] are completed connects, [attempts] every connect tried. */
    data class LatencyEvidence(
        val samplesMs: List<Int> = emptyList(),
        val attempts: Int = 0,
        val failures: Int = 0,
        val targets: String = ""
    )

    data class DnsEvidence(
        val server: String,
        val answered: Boolean,
        val latencyMs: Int = 0,
        val rcode: Int = -1,
        val answer: String = "",
        val blockPage: Boolean = false
    )

    enum class PortProtocol { TCP, UDP }

    data class PortEvidence(
        val host: String,
        val port: Int,
        val protocol: PortProtocol = PortProtocol.TCP,
        val open: Boolean = false,
        val latencyMs: Int = 0
    )

    /** One HTTPS reachability probe: did TLS over this host:port actually complete? */
    data class HttpsProbe(
        val host: String,
        val port: Int = 443,
        val reachable: Boolean = false,
        val latencyMs: Int = 0,
        val http2: Boolean = false
    )

    // ------------------------------------------------------------------------------------------
    // MARBLE_FILTER_LAB_V273 — what the path does to a real handshake
    //
    // V236 declared [HttpsProbe] and `sniSuspicion` here and then never filled either of them:
    // the scanner probed one host through the product's own reachability helper and wrote a
    // single boolean. A subscriber in a filtered country could therefore read "DPI & censorship
    // · the TLS handshake completed cleanly" while telegram.org and x.com were being reset
    // mid-ClientHello, because nothing ever asked them. The council could name interference but
    // could not *find* it, and the report was right to call the twelve checks weak: the loudest
    // seat in the product was measuring the quietest thing on the link.
    //
    // [FilterEvidence] is the measurement the seat needed. Three independent families of test —
    // a TLS matrix across reachable and routinely-filtered names, a resolver cross-check that
    // compares the same handful of names on several resolvers, and a block-page fetch — each
    // reported per target so the page can say *which* name died and *how*, not "censorship
    // suspected".
    // ------------------------------------------------------------------------------------------

    /** How one TLS handshake ended. [RESET] is the fingerprint of an active middlebox. */
    enum class TlsOutcome(val label: String) {
        COMPLETED("TLS completed"),
        RESET("Connection reset during the handshake"),
        TIMEOUT("No answer before the deadline"),
        REFUSED("Connection refused"),
        DNS_FAILED("The name did not resolve"),
        HANDSHAKE_FAILED("The handshake failed")
    }

    /** One host, dialled for real, with the SNI name the middlebox would have read. */
    data class TlsProbe(
        val host: String,
        val port: Int = 443,
        val sni: String = host,
        val outcome: TlsOutcome = TlsOutcome.HANDSHAKE_FAILED,
        val latencyMs: Int = 0,
        val certificateOk: Boolean = false,
        /** The name is on the list a filter is expected to act on, in this product's catalogue. */
        val routinelyFiltered: Boolean = false
    ) {
        val reachable: Boolean get() = outcome == TlsOutcome.COMPLETED
    }

    /** How one name's plain answer compared with the honest answer for the same name. */
    enum class DnsVerdict(val label: String) {
        /** Two resolvers agree on an answer no policy intercepts. */
        AGREE("Resolvers agree"),

        /** One resolver returned a private/block-page address where another returned a real one. */
        BLOCKED("A resolver answered with a block-page address"),

        /** Two honest resolvers returned different addresses and neither is a block page. */
        DIFFERENT("Resolvers returned different answers"),

        /** The name did not answer on the plain path at all. */
        NO_ANSWER("The name did not resolve"),

        /** Nothing was measured for this name. */
        UNMEASURED("Not measured")
    }

    /**
     * One name, asked on every resolver the scan could reach, with the first two answers kept.
     *
     * `blockPageServer` names the resolver that produced the intercepted answer, so a finding can
     * say *who* rewrote the record instead of blaming the link as a whole.
     */
    data class DnsNameCheck(
        val name: String,
        val firstServer: String = "",
        val firstAnswer: String = "",
        val secondServer: String = "",
        val secondAnswer: String = "",
        val blockPageServer: String = "",
        val answeredServers: Int = 0,
        val verdict: DnsVerdict = DnsVerdict.UNMEASURED
    )

    /** A target that answered a plain HTTP request with a policy page instead of the site. */
    data class BlockPageProbe(
        val host: String,
        val statusCode: Int = 0,
        val redirectHost: String = "",
        val bodyMarker: String = "",
        val intercepted: Boolean = false,
        val latencyMs: Int = 0
    )

    /**
     * The filtration lab's evidence.
     *
     * Every counter here is derived, so the seats cannot disagree with the page about how many
     * targets failed: the list is the measurement, the numbers are a view of it.
     */
    data class FilterEvidence(
        val tls: List<TlsProbe> = emptyList(),
        val dnsNames: List<DnsNameCheck> = emptyList(),
        val blockPages: List<BlockPageProbe> = emptyList(),
        /** A TLS reset that arrived from a hop that is not the destination (measured elsewhere). */
        val rstInjected: Boolean = false
    ) {
        val tlsProbed: Int get() = tls.size
        val tlsCompleted: Int get() = tls.count { it.reachable }
        val tlsFailed: Int get() = tls.count { !it.reachable }
        val tlsReset: Int get() = tls.count { it.outcome == TlsOutcome.RESET }
        val dnsPoisoned: Int get() = dnsNames.count { it.verdict == DnsVerdict.BLOCKED }
        val dnsSplit: Int get() = dnsNames.count { it.verdict == DnsVerdict.DIFFERENT }
        val blockPagesFound: Int get() = blockPages.count { it.intercepted }

        /** The names a filter acted on, in scan order: what a reader wants to see first. */
        val filteredHosts: List<String>
            get() = tls.filter { !it.reachable && it.routinelyFiltered }.map { it.host } +
                blockPages.filter { it.intercepted }.map { it.host } +
                dnsNames.filter { it.verdict == DnsVerdict.BLOCKED }.map { it.name }

        /** The open names that also failed, which is the signature of a link-wide problem. */
        val openHostsFailed: Int
            get() = tls.count { !it.reachable && !it.routinelyFiltered }

        val measured: Boolean
            get() = tls.isNotEmpty() || dnsNames.any { it.verdict != DnsVerdict.UNMEASURED } ||
                blockPages.isNotEmpty()

        /**
         * SNI inspection: some names completed, the routinely-filtered ones did not, and at least
         * one of those deaths was a reset or a deadline rather than a refusal.
         *
         * The "some names completed" clause is what keeps a dead link from being reported as
         * censorship — a link where every handshake times out is unreachable, not filtered, and
         * the two justify different settings.
         */
        val sniFiltering: Boolean
            get() = tlsCompleted > 0 &&
                tls.any { !it.reachable && it.routinelyFiltered } &&
                tls.any {
                    it.routinelyFiltered &&
                        (it.outcome == TlsOutcome.RESET || it.outcome == TlsOutcome.TIMEOUT)
                }

        /** One word for the page's badge, derived from the evidence, never guessed. */
        val verdict: FilterVerdict
            get() = when {
                !measured -> FilterVerdict.UNMEASURED
                // `rstInjected` is the one flag measured outside the lab (the product's own
                // reachability probe); it is the same evidence and belongs to the same verdict.
                dnsPoisoned > 0 || blockPagesFound > 0 || sniFiltering || rstInjected ->
                    FilterVerdict.FILTERED
                tlsProbed > 0 && tlsCompleted == 0 -> FilterVerdict.UNREACHABLE
                tlsFailed > 0 || dnsSplit > 0 -> FilterVerdict.SUSPECT
                else -> FilterVerdict.CLEAN
            }

        /** The share of the TLS matrix that completed, for the score and the page's number. */
        val completionFraction: Double
            get() = if (tlsProbed == 0) 1.0 else tlsCompleted.toDouble() / tlsProbed.toDouble()
    }

    enum class FilterVerdict(val label: String) {
        UNMEASURED("Not measured"),
        CLEAN("No interference measured"),
        SUSPECT("Partial interference"),
        FILTERED("Filtering measured"),
        UNREACHABLE("The public internet is unreachable")
    }

    data class PathEvidence(
        val ipv4Ok: Boolean = false,
        val ipv4Ms: Int = 0,
        val ipv6Ok: Boolean = false,
        val ipv6Ms: Int = 0,
        val tlsCompleted: Boolean = false,
        val injectedReset: Boolean = false,
        val quicOk: Boolean = false,
        val udpDnsOk: Boolean = false,
        val pathMtu: Int = 0,
        val mtuConfidence: Double = 0.0
    )

    data class DeviceEvidence(
        val cores: Int = 0,
        val memoryMb: Long = 0,
        val batteryPercent: Int = -1,
        val charging: Boolean = false,
        val thermalStatus: Int = 0
    )

    data class NetworkEvidence(
        val radio: RadioEvidence = RadioEvidence(),
        val latency: LatencyEvidence = LatencyEvidence(),
        val dns: List<DnsEvidence> = emptyList(),
        val ports: List<PortEvidence> = emptyList(),
        val path: PathEvidence = PathEvidence(),
        val device: DeviceEvidence = DeviceEvidence(),
        /**
         * HTTPS reachability measured against real targets: a TCP handshake to 443 is not the
         * same thing as TLS completing — a middlebox can answer the handshake and drop the
         * ClientHello, which is exactly what filtering looks like.
         */
        val httpsTargets: List<HttpsProbe> = emptyList(),
        /**
         * TCP 443 answers but TLS never completes, repeatedly, across targets — the classic
         * SNI inspection signature. Set by the scanner; the council never guesses it.
         */
        val sniSuspicion: Boolean = false,
        /**
         * MARBLE_FILTER_LAB_V273 — the filtration lab. Empty in a scan that predates the lab or
         * that could not measure it, which the council reports as "not measured" rather than as
         * "clean".
         */
        val filter: FilterEvidence = FilterEvidence(),
        val throughputDownKbps: Int = 0,
        val capturedAtMs: Long = 0L
    )

    // ------------------------------------------------------------------------------------------
    // What the council says
    // ------------------------------------------------------------------------------------------

    enum class CouncilSeat(val label: String, val focus: String) {
        RADIO("Radio & link", "signal, band and the link's own ceiling"),
        OPERATOR("Operator & carrier", "the operator behind the link and its posture"),
        LATENCY("Latency", "how far the public internet is, in milliseconds"),
        JITTER("Jitter & stability", "whether that distance holds still"),
        LOSS("Loss & retransmit", "what never arrives at all"),
        DNS("DNS integrity", "whether names are answered honestly"),
        PORTS("Ports & firewall", "which destinations survive the middleboxes"),
        UDP("UDP & QUIC", "whether datagrams cross this path"),
        MTU("MTU & fragmentation", "the largest packet the path carries"),
        FAMILY("Path & address family", "IPv4 and IPv6, measured not assumed"),
        DPI("DPI & censorship", "interference inside the handshake"),
        CORE("Core & device", "the knobs the pinned cores actually read")
    }

    enum class CouncilSeverity(val rank: Int) {
        GOOD(0),
        NOTICE(1),
        WARNING(2),
        CRITICAL(3)
    }

    data class CouncilFinding(
        val seat: CouncilSeat,
        val severity: CouncilSeverity,
        val headline: String,
        val detail: String = ""
    )

    /**
     * Every knob the council is allowed to write. This list is the constitution in code: nothing
     * about routing, split tunnelling, identity or trust lives here, so no future seat can reach
     * them by accident.
     */
    enum class FieldKind { TEXT, INT, BOOL }

    enum class TuningField(val label: String, val kind: FieldKind) {
        POWER_PROFILE("Power profile", FieldKind.TEXT),
        WORKLOAD_PROFILE("Workload profile", FieldKind.TEXT),
        MTU_MIN("TUN MTU floor", FieldKind.INT),
        MTU_MAX("TUN MTU ceiling", FieldKind.INT),
        TUN_MTU("Tunnel datapath MTU", FieldKind.INT),
        UDP_TIMEOUT_SEC("UDP idle timeout", FieldKind.INT),
        TCP_KEEPALIVE_SEC("TCP keep-alive", FieldKind.INT),
        CONNECT_TIMEOUT_SEC("Core connect timeout", FieldKind.INT),
        FALLBACK_COUNT("Fallback candidates", FieldKind.INT),
        DNS_PRIMARY("Primary resolver", FieldKind.TEXT),
        DNS_SECONDARY("Secondary resolver", FieldKind.TEXT),
        DNS_PRIMARY_DOH("Primary DoH endpoint", FieldKind.TEXT),
        DNS_SECONDARY_DOH("Secondary DoH endpoint", FieldKind.TEXT),
        DNS_STRATEGY("DNS query strategy", FieldKind.TEXT),
        DNS_HIJACK("DNS hijack guard", FieldKind.BOOL),
        DNS_FAKE_IP("Fake-IP pool", FieldKind.BOOL),
        TCP_FAST_OPEN("TCP fast open", FieldKind.BOOL),
        UDP_PROBE("UDP probing", FieldKind.BOOL),
        MUX("Multiplexing", FieldKind.BOOL),
        MUX_CONCURRENCY("Mux concurrency", FieldKind.INT),
        MUX_XUDP_CONCURRENCY("Mux xUDP concurrency", FieldKind.INT),
        MUX_UDP443("UDP/443 policy", FieldKind.TEXT),
        MUX_PADDING("Mux padding", FieldKind.BOOL),
        ADAPTIVE_BUFFER("Adaptive buffers", FieldKind.BOOL),
        POLICY_BUFFER_KB("Policy buffer size", FieldKind.INT),
        PING_TIMEOUT_SEC("Ping timeout", FieldKind.INT),
        PING_SAMPLES("Ping samples", FieldKind.INT),
        PING_CONCURRENCY("Ping concurrency", FieldKind.INT),
        ADDRESS_FAMILY("Address family", FieldKind.TEXT),
        IPV6("IPv6 enabled", FieldKind.BOOL),
        HAPPY_EYEBALLS_MS("Happy Eyeballs delay", FieldKind.INT),
        FRAGMENT("TLS fragmentation", FieldKind.TEXT)
    }

    /** A seat's recorded opinion. Losers are kept as [TuningChange.dissent], not thrown away. */
    data class Proposal(
        val field: TuningField,
        val value: String,
        val seat: CouncilSeat,
        val confidence: Int,
        val reason: String
    )

    data class TuningChange(
        val field: TuningField,
        val value: String,
        val previous: String,
        val seat: CouncilSeat,
        val confidence: Int,
        val reason: String,
        val dissent: String = ""
    )

    data class Metrics(
        val medianRttMs: Double = 0.0,
        val p95RttMs: Double = 0.0,
        val jitterMs: Double = 0.0,
        val lossPercent: Double = 0.0,
        val dnsOkPercent: Double = 0.0,
        val dnsMedianMs: Double = 0.0,
        val openPorts: Int = 0,
        val probedPorts: Int = 0,
        val tlsOk: Boolean = false,
        val injectedReset: Boolean = false,
        val quicOk: Boolean = false,
        val ipv4Ok: Boolean = false,
        val ipv6Ok: Boolean = false,
        val pathMtu: Int = 0,
        val mtuConfidence: Double = 0.0,
        /** How many HTTPS targets were probed and how many answered honestly. */
        val httpsProbed: Int = 0,
        val httpsOk: Int = 0,
        /** TCP 443 answers but TLS keeps failing across targets — SNI inspection territory. */
        val sniSuspicion: Boolean = false,
        /** MARBLE_FILTER_LAB_V273 — the lab's own summary, so the page never re-derives it. */
        val filterVerdict: FilterVerdict = FilterVerdict.UNMEASURED,
        val tlsProbed: Int = 0,
        val tlsCompleted: Int = 0,
        val filteredHosts: List<String> = emptyList(),
        val dnsPoisoned: Int = 0,
        val blockPages: Int = 0,
        val score: Int = 0,
        val grade: String = "C"
    )

    data class Plan(
        val metrics: Metrics,
        val findings: List<CouncilFinding>,
        val changes: List<TuningChange>,
        val settings: AppSettings,
        val proposals: List<Proposal> = emptyList()
    ) {
        val seatVerdicts: Map<CouncilSeat, CouncilSeverity>
            get() = CouncilSeat.entries.associateWith { seat ->
                findings.filter { it.seat == seat }
                    .maxByOrNull { it.severity.rank }?.severity ?: CouncilSeverity.GOOD
            }

        val critical: Boolean
            get() = findings.any { it.severity == CouncilSeverity.CRITICAL }
    }

    // ------------------------------------------------------------------------------------------
    // Deliberation
    // ------------------------------------------------------------------------------------------

    fun deliberate(
        evidence: NetworkEvidence,
        base: AppSettings,
        muxSafe: Boolean = true
    ): Plan {
        val metrics = measure(evidence)
        val findings = mutableListOf<CouncilFinding>()
        val proposals = mutableListOf<Proposal>()

        seatRadio(evidence, metrics, findings, proposals)
        seatOperator(evidence, metrics, findings, proposals)
        seatLatency(evidence, metrics, findings, proposals)
        seatJitter(evidence, metrics, findings, proposals)
        seatLoss(evidence, metrics, findings, proposals)
        seatDns(evidence, metrics, findings, proposals)
        seatPorts(evidence, metrics, findings, proposals)
        seatUdp(evidence, metrics, findings, proposals)
        seatMtu(evidence, metrics, findings, proposals)
        seatFamily(evidence, metrics, findings, proposals)
        seatDpi(evidence, metrics, findings, proposals)
        seatCore(evidence, metrics, findings, proposals, muxSafe)

        val resolved = resolve(proposals)
        val changes = mutableListOf<TuningChange>()
        for (proposal in resolved) {
            val previous = read(base, proposal.field)
            if (previous.equals(proposal.value, ignoreCase = true)) continue
            changes += TuningChange(
                field = proposal.field,
                value = proposal.value,
                previous = previous,
                seat = proposal.seat,
                confidence = proposal.confidence,
                reason = proposal.reason,
                dissent = dissentFor(proposal, proposals)
            )
        }

        var settings = base
        for (change in changes) settings = write(settings, change.field, change.value)
        settings = normalize(settings)

        return Plan(
            metrics = metrics,
            findings = findings,
            changes = changes,
            settings = settings,
            proposals = resolved
        )
    }

    /** The council's own arithmetic, exposed so a test can pin the score instead of guessing it. */
    fun measure(evidence: NetworkEvidence): Metrics {
        val rtt = evidence.latency.samplesMs.filter { it > 0 }.sorted()
        val median = median(rtt)
        val p95 = percentile(rtt, 0.95)
        val jitter = if (rtt.size < 2) 0.0 else median(rtt.zipWithNext { a, b -> abs(b - a) })
        val attempts = evidence.latency.attempts.coerceAtLeast(rtt.size + evidence.latency.failures)
        val loss = if (attempts <= 0) 0.0 else {
            (evidence.latency.failures.toDouble() / attempts.toDouble() * 100.0)
        }

        val dnsAnswered = evidence.dns.count { it.answered }
        val dnsOkPercent = if (evidence.dns.isEmpty()) 0.0 else {
            dnsAnswered.toDouble() / evidence.dns.size.toDouble() * 100.0
        }
        val dnsMedian = median(evidence.dns.filter { it.answered }.map { it.latencyMs }.sorted())

        val tcpPorts = evidence.ports.filter { it.protocol == PortProtocol.TCP }
        val openPorts = tcpPorts.count { it.open }
        val httpsProbed = evidence.httpsTargets.size
        val httpsOk = evidence.httpsTargets.count { it.reachable }

        val latencyScore = when {
            median <= 0.0 -> 50
            median <= 60.0 -> 100
            median <= 120.0 -> 88
            median <= 200.0 -> 72
            median <= 350.0 -> 52
            else -> 30
        }
        val jitterScore = when {
            rtt.size < 2 -> 60
            jitter <= 8.0 -> 100
            jitter <= 20.0 -> 88
            jitter <= 45.0 -> 70
            jitter <= 90.0 -> 45
            else -> 25
        }
        val lossScore = when {
            // Nothing was attempted is not the same as nothing was lost: an unmeasured path
            // scores below a clean one and never above it.
            attempts <= 0 -> 40
            loss <= 0.0 -> 100
            loss <= 1.5 -> 90
            loss <= 4.0 -> 72
            loss <= 8.0 -> 48
            loss <= 15.0 -> 28
            else -> 10
        }
        val dnsScore = when {
            evidence.dns.isEmpty() -> 55
            else -> (
                dnsOkPercent * 0.7 +
                    (when {
                        dnsMedian <= 40.0 -> 30.0
                        dnsMedian <= 90.0 -> 24.0
                        dnsMedian <= 180.0 -> 14.0
                        else -> 6.0
                    })
                ).roundToInt().coerceIn(0, 100)
        }
        val portScore = when {
            tcpPorts.isEmpty() -> 55
            else -> (30 + (openPorts.toDouble() / tcpPorts.size.toDouble() * 70.0)).roundToInt()
        }
        val filter = evidence.filter
        // MARBLE_FILTER_LAB_V273 — the DPI share used to be a three-way guess about one boolean.
        // It is the one score the report called weak, and it was weak because it was blind: a
        // link that reset every filtered name still scored 100 as long as *one* handshake had
        // completed. With the lab's matrix the share is the measured completion rate, minus a
        // penalty for the two kinds of evidence that no healthy link produces at all (an
        // intercepted DNS answer and a policy page served in place of the site). A scan that did
        // not run the lab keeps the V236 arithmetic exactly.
        val dpiScore = when {
            !evidence.path.tlsCompleted -> 45
            evidence.path.injectedReset -> 30
            !filter.measured -> 100
            else -> {
                val completion = 100.0 * filter.completionFraction
                val intercepted = filter.dnsPoisoned * 12.0 + filter.blockPagesFound * 15.0
                (completion - intercepted.coerceAtMost(60.0)).roundToInt().coerceIn(10, 100)
            }
        }
        val udpScore = when {
            evidence.path.quicOk -> 100
            evidence.path.udpDnsOk -> 70
            else -> 35
        }
        val familyScore = when {
            evidence.path.ipv4Ok && evidence.path.ipv6Ok -> 100
            evidence.path.ipv4Ok -> 78
            evidence.path.ipv6Ok -> 60
            else -> 30
        }
        val radioScore = when {
            evidence.radio.transport == LinkTransport.NONE -> 20
            evidence.radio.signalLevel < 0 -> 62
            evidence.radio.signalLevel >= 3 -> 100
            evidence.radio.signalLevel == 2 -> 80
            evidence.radio.signalLevel == 1 -> 58
            else -> 35
        }

        val score = (
            latencyScore * 22.0 +
                jitterScore * 14.0 +
                lossScore * 14.0 +
                dnsScore * 16.0 +
                portScore * 8.0 +
                dpiScore * 12.0 +
                udpScore * 6.0 +
                familyScore * 4.0 +
                radioScore * 4.0
            ) / 100.0

        val rounded = score.roundToInt().coerceIn(0, 100)
        return Metrics(
            medianRttMs = median,
            p95RttMs = p95,
            jitterMs = jitter,
            lossPercent = loss,
            dnsOkPercent = dnsOkPercent,
            dnsMedianMs = dnsMedian,
            openPorts = openPorts,
            probedPorts = tcpPorts.size,
            httpsProbed = httpsProbed,
            httpsOk = httpsOk,
            sniSuspicion = evidence.sniSuspicion,
            filterVerdict = filter.verdict,
            tlsProbed = filter.tlsProbed,
            tlsCompleted = filter.tlsCompleted,
            filteredHosts = filter.filteredHosts,
            dnsPoisoned = filter.dnsPoisoned,
            blockPages = filter.blockPagesFound,
            tlsOk = evidence.path.tlsCompleted,
            injectedReset = evidence.path.injectedReset,
            quicOk = evidence.path.quicOk,
            ipv4Ok = evidence.path.ipv4Ok,
            ipv6Ok = evidence.path.ipv6Ok,
            pathMtu = evidence.path.pathMtu,
            mtuConfidence = evidence.path.mtuConfidence,
            score = rounded,
            grade = gradeFor(rounded)
        )
    }

    fun gradeFor(score: Int): String = when {
        score >= 85 -> "A"
        score >= 70 -> "B"
        score >= 55 -> "C"
        else -> "D"
    }

    // ------------------------------------------------------------------------------------------
    // The twelve seats
    // ------------------------------------------------------------------------------------------

    private fun seatRadio(
        evidence: NetworkEvidence,
        metrics: Metrics,
        findings: MutableList<CouncilFinding>,
        proposals: MutableList<Proposal>
    ) {
        val radio = evidence.radio
        when (radio.transport) {
            LinkTransport.NONE -> findings += CouncilFinding(
                CouncilSeat.RADIO,
                CouncilSeverity.CRITICAL,
                "No network is attached",
                "Wi-Fi and mobile data are both down; every measurement below is a local failure."
            )
            LinkTransport.WIFI -> findings += CouncilFinding(
                CouncilSeat.RADIO,
                if (radio.signalLevel in 0..1) CouncilSeverity.WARNING else CouncilSeverity.GOOD,
                if (radio.signalLevel in 0..1) "Weak Wi-Fi signal" else "Wi-Fi link is healthy",
                "Signal bars ${bars(radio.signalLevel)} · ${radio.networkType.ifBlank { "Wi-Fi" }}" +
                    if (radio.metered) " · metered hotspot" else ""
            )
            LinkTransport.CELLULAR -> findings += CouncilFinding(
                CouncilSeat.RADIO,
                if (radio.signalLevel in 0..1) CouncilSeverity.WARNING else CouncilSeverity.GOOD,
                "${radio.operatorName.ifBlank { "Mobile operator" }} · ${radio.networkType.ifBlank { "mobile data" }}",
                "Signal ${radio.signalDbm.takeIf { it != 0 }?.let { "$it dBm" } ?: bars(radio.signalLevel)}" +
                    " · downlink ${kbps(radio.linkDownKbps)} · uplink ${kbps(radio.linkUpKbps)}"
            )
            else -> findings += CouncilFinding(
                CouncilSeat.RADIO,
                CouncilSeverity.GOOD,
                "${radio.transport.label} link",
                if (radio.vpnActive) "Another VPN is already in front of this app." else ""
            )
        }

        if (radio.captivePortal) {
            findings += CouncilFinding(
                CouncilSeat.RADIO,
                CouncilSeverity.CRITICAL,
                "Captive portal controls the link",
                "A portal page owns this link and no request reaches the internet until it is opened in a browser and accepted. No tunnel knob can fix that, so the scan stays small on purpose and no protection is bought for a link the portal can still rewrite."
            )
        }

        if (!radio.validated && radio.transport != LinkTransport.NONE) {
            findings += CouncilFinding(
                CouncilSeat.RADIO,
                CouncilSeverity.WARNING,
                if (radio.captivePortal) "Captive portal in front of the link" else "Link is not validated",
                "Android has not confirmed internet on this link; a portal may be swallowing requests."
            )
        }

        val ceiling = radio.interfaceMtu.takeIf { it > 0 }?.coerceIn(MTU_MIN, MTU_MAX)
        if (ceiling != null) {
            proposals += Proposal(
                TuningField.MTU_MAX,
                ceiling.toString(),
                CouncilSeat.RADIO,
                82,
                "The link reports an interface MTU of $ceiling bytes; the TUN ceiling must not exceed it."
            )
            proposals += Proposal(
                TuningField.MTU_MIN,
                minOf(MTU_MIN, ceiling).toString(),
                CouncilSeat.RADIO,
                55,
                "Keep the floor at the IPv6 minimum so a shrinking path still has a legal packet."
            )
        }
        if (radio.metered) {
            findings += CouncilFinding(
                CouncilSeat.RADIO,
                CouncilSeverity.NOTICE,
                "Metered link: the scan stayed small",
                "No bulk download was performed; throughput is the link's own estimate."
            )
        }
    }

    private fun seatOperator(
        evidence: NetworkEvidence,
        metrics: Metrics,
        findings: MutableList<CouncilFinding>,
        proposals: MutableList<Proposal>
    ) {
        val radio = evidence.radio
        val isp = IranNetworkRegistry.byOperatorCode(radio.operatorCode)
            ?: IranNetworkRegistry.byOrganisation(radio.operatorName)
        val name = isp?.shortName ?: radio.operatorName.ifBlank { "Unknown operator" }

        if (isp == null) {
            findings += CouncilFinding(
                CouncilSeat.OPERATOR,
                CouncilSeverity.NOTICE,
                name,
                "Operator is not in the offline registry, so only measured evidence is used."
            )
            return
        }

        val severity = when (isp.severity) {
            FilterSeverity.EXTREME -> CouncilSeverity.WARNING
            FilterSeverity.HEAVY -> CouncilSeverity.WARNING
            FilterSeverity.MODERATE -> CouncilSeverity.NOTICE
            FilterSeverity.LIGHT -> CouncilSeverity.GOOD
        }
        findings += CouncilFinding(
            CouncilSeat.OPERATOR,
            severity,
            "${isp.label} · ${isp.severity.name.lowercase()} filtering profile",
            isp.note
        )

        // The registry is a prior, not a measurement: it may suggest the cheaper treatment while
        // the handshake probe is inconclusive, and it loses to a completed probe every time.
        val inconclusive = !evidence.path.tlsCompleted
        if (isp.severity == FilterSeverity.EXTREME || isp.severity == FilterSeverity.HEAVY) {
            val strategy = if (isp.severity == FilterSeverity.EXTREME) {
                FragmentStrategy.RECORD
            } else {
                FragmentStrategy.AUTO
            }
            proposals += Proposal(
                TuningField.FRAGMENT,
                strategy.id,
                CouncilSeat.OPERATOR,
                if (inconclusive) 58 else 34,
                if (inconclusive) {
                    "The operator is documented as ${isp.severity.name.lowercase()} filtering and the handshake probe was inconclusive."
                } else {
                    "The operator's profile suggests this treatment, but the handshake probe completed."
                }
            )
        }
        if (isp.kind == IranIspKind.MOBILE) {
            proposals += Proposal(
                TuningField.UDP_TIMEOUT_SEC,
                "45",
                CouncilSeat.OPERATOR,
                30,
                "Mobile carriers tear down idle UDP mappings sooner than fixed lines."
            )
        }
    }

    private fun seatLatency(
        evidence: NetworkEvidence,
        metrics: Metrics,
        findings: MutableList<CouncilFinding>,
        proposals: MutableList<Proposal>
    ) {
        val median = metrics.medianRttMs
        if (evidence.latency.samplesMs.isEmpty()) {
            findings += CouncilFinding(
                CouncilSeat.LATENCY,
                CouncilSeverity.WARNING,
                "No TCP handshake completed",
                "The latency ladder could not reach a single anchor; budgets stay conservative."
            )
        } else {
            val severity = when {
                median <= 80.0 -> CouncilSeverity.GOOD
                median <= 180.0 -> CouncilSeverity.NOTICE
                else -> CouncilSeverity.WARNING
            }
            findings += CouncilFinding(
                CouncilSeat.LATENCY,
                severity,
                "Median ${ms(median)} · p95 ${ms(metrics.p95RttMs)}",
                "${evidence.latency.samplesMs.size} handshakes to ${evidence.latency.targets.ifBlank { "public anchors" }}"
            )
        }

        val (timeout, samples) = when {
            median <= 0.0 -> 8 to 3
            median <= 80.0 -> 3 to 5
            median <= 150.0 -> 4 to 4
            median <= 300.0 -> 6 to 3
            else -> 8 to 3
        }
        proposals += Proposal(
            TuningField.PING_TIMEOUT_SEC,
            timeout.toString(),
            CouncilSeat.LATENCY,
            90,
            if (median <= 0.0) {
                "Nothing answered; a longer timeout keeps a slow server from looking dead."
            } else {
                "A ${ms(median)} path needs ${timeout}s per sample to separate slow from dead."
            }
        )
        proposals += Proposal(
            TuningField.PING_SAMPLES,
            samples.toString(),
            CouncilSeat.LATENCY,
            74,
            "Samples are sized to the path's own variance: more when it is fast, fewer when it is slow."
        )
    }

    private fun seatJitter(
        evidence: NetworkEvidence,
        metrics: Metrics,
        findings: MutableList<CouncilFinding>,
        proposals: MutableList<Proposal>
    ) {
        val jitter = metrics.jitterMs
        val severity = when {
            evidence.latency.samplesMs.size < 2 -> CouncilSeverity.NOTICE
            jitter <= 20.0 -> CouncilSeverity.GOOD
            jitter <= 60.0 -> CouncilSeverity.NOTICE
            else -> CouncilSeverity.WARNING
        }
        findings += CouncilFinding(
            CouncilSeat.JITTER,
            severity,
            if (evidence.latency.samplesMs.size < 2) "Not enough samples to judge jitter" else "Jitter ${ms(jitter)}",
            "Jitter is the median absolute step between consecutive handshakes, not an average that hides spikes."
        )

        val connectTimeout = if (jitter > 45.0 || metrics.medianRttMs > 180.0) 12 else 6
        proposals += Proposal(
            TuningField.CONNECT_TIMEOUT_SEC,
            connectTimeout.toString(),
            CouncilSeat.JITTER,
            78,
            if (connectTimeout > 6) {
                "A ${ms(jitter)} jittery path spends a short connect budget on retransmits alone."
            } else {
                "A steady path does not need a long connect budget; failing fast is better than hanging."
            }
        )
        val keepAlive = when {
            jitter > 45.0 -> 15
            metrics.medianRttMs > 200.0 -> 30
            else -> 0
        }
        proposals += Proposal(
            TuningField.TCP_KEEPALIVE_SEC,
            keepAlive.toString(),
            CouncilSeat.JITTER,
            60,
            if (keepAlive == 0) {
                "The path is steady enough to leave keep-alive probing off and save wakeups."
            } else {
                "A NAT on this path reaps idle sockets; keep-alive every ${keepAlive}s holds the mapping."
            }
        )
    }

    private fun seatLoss(
        evidence: NetworkEvidence,
        metrics: Metrics,
        findings: MutableList<CouncilFinding>,
        proposals: MutableList<Proposal>
    ) {
        val loss = metrics.lossPercent
        val severity = when {
            loss <= 0.0 -> CouncilSeverity.GOOD
            loss <= 2.0 -> CouncilSeverity.NOTICE
            loss <= 8.0 -> CouncilSeverity.WARNING
            else -> CouncilSeverity.CRITICAL
        }
        findings += CouncilFinding(
            CouncilSeat.LOSS,
            severity,
            "${loss.roundToInt()}% of handshakes were lost",
            "Measured from ${evidence.latency.attempts.coerceAtLeast(evidence.latency.samplesMs.size + evidence.latency.failures)} attempts."
        )

        val udpTimeout = if (loss >= 2.0) 45 else 60
        proposals += Proposal(
            TuningField.UDP_TIMEOUT_SEC,
            udpTimeout.toString(),
            CouncilSeat.LOSS,
            72,
            if (loss >= 2.0) {
                "A lossy path should recycle stale UDP mappings sooner than a clean one."
            } else {
                "A clean path keeps UDP mappings for the default minute; nothing is gained by shortening it."
            }
        )
        proposals += Proposal(
            TuningField.FALLBACK_COUNT,
            if (loss >= 2.0) "4" else "3",
            CouncilSeat.LOSS,
            66,
            if (loss >= 2.0) {
                "With ${loss.roundToInt()}% loss, a wider fallback ladder is worth the extra probes."
            } else {
                "Three fallbacks already cover a path that loses nothing."
            }
        )
    }

    private fun seatDns(
        evidence: NetworkEvidence,
        metrics: Metrics,
        findings: MutableList<CouncilFinding>,
        proposals: MutableList<Proposal>
    ) {
        if (evidence.dns.isEmpty()) {
            findings += CouncilFinding(
                CouncilSeat.DNS,
                CouncilSeverity.NOTICE,
                "No resolver was probed",
                "Names were not measured this round; the DNS settings are left alone."
            )
            return
        }

        val tampered = evidence.dns.filter { it.blockPage }
        val dead = evidence.dns.filter { !it.answered }
        val healthy = evidence.dns.filter { it.answered && !it.blockPage }.sortedBy { it.latencyMs }

        val answers = healthy.map { it.answer }.filter { it.isNotBlank() }.distinct()
        if (answers.size > 1 && evidence.dns.size > 1) {
            findings += CouncilFinding(
                CouncilSeat.DNS,
                CouncilSeverity.WARNING,
                "Resolvers disagree on the same names",
                evidence.dns.joinToString(" · ") { entry ->
                    "${entry.server} said ${entry.answer.ifBlank { "nothing" }}"
                } + " — two honest resolvers do not give different answers for one name; something is rewriting responses."
            )
            proposals += Proposal(
                TuningField.DNS_HIJACK,
                "true",
                CouncilSeat.DNS,
                60,
                "Different resolvers returned different answers for the same name: the hijack guard must treat every answer as suspect."
            )
            proposals += Proposal(
                TuningField.DNS_FAKE_IP,
                "true",
                CouncilSeat.DNS,
                56,
                "A fake-IP pool keeps a rewritten answer from ever reaching an application."
            )
        }

        val severity = when {
            tampered.isNotEmpty() -> CouncilSeverity.CRITICAL
            dead.size == evidence.dns.size -> CouncilSeverity.CRITICAL
            dead.isNotEmpty() -> CouncilSeverity.WARNING
            metrics.dnsMedianMs > 150.0 -> CouncilSeverity.NOTICE
            else -> CouncilSeverity.GOOD
        }
        findings += CouncilFinding(
            CouncilSeat.DNS,
            severity,
            when {
                tampered.isNotEmpty() -> "Resolver answered with a block page"
                dead.isNotEmpty() -> "${dead.size} of ${evidence.dns.size} resolvers stayed silent"
                else -> "Names resolve honestly · ${ms(metrics.dnsMedianMs)}"
            },
            evidence.dns.joinToString(" · ") { entry ->
                "${entry.server}: ${if (entry.answered) "${entry.latencyMs}ms rcode=${entry.rcode}" else "no answer"}"
            }
        )

        val best = healthy.firstOrNull()
        if (best != null) {
            proposals += Proposal(
                TuningField.DNS_PRIMARY,
                best.server,
                CouncilSeat.DNS,
                86,
                "Fastest honest resolver measured on this path (${best.latencyMs}ms)."
            )
            val second = healthy.firstOrNull { it.server != best.server }
            if (second != null) {
                proposals += Proposal(
                    TuningField.DNS_SECONDARY,
                    second.server,
                    CouncilSeat.DNS,
                    78,
                    "Second-fastest honest resolver (${second.latencyMs}ms) as the hedge."
                )
            }
        } else {
            proposals += Proposal(
                TuningField.DNS_PRIMARY,
                "1.1.1.1",
                CouncilSeat.DNS,
                70,
                "Every configured resolver failed; fall back to the encrypted-by-policy public pair."
            )
            proposals += Proposal(
                TuningField.DNS_SECONDARY,
                "8.8.8.8",
                CouncilSeat.DNS,
                66,
                "A second public resolver keeps names alive if the first is filtered."
            )
        }

        val primary = healthy.firstOrNull()?.server ?: "1.1.1.1"
        dohFor(primary)?.let { doh ->
            proposals += Proposal(
                TuningField.DNS_PRIMARY_DOH,
                doh,
                CouncilSeat.DNS,
                62,
                "The DoH endpoint follows the measured resolver so names and their encryption never disagree."
            )
        }
        val secondary = healthy.firstOrNull { it.server != primary }?.server ?: "8.8.8.8"
        dohFor(secondary)?.let { doh ->
            proposals += Proposal(
                TuningField.DNS_SECONDARY_DOH,
                doh,
                CouncilSeat.DNS,
                58,
                "Second DoH endpoint, matching the measured hedge resolver."
            )
        }

        proposals += Proposal(
            TuningField.DNS_STRATEGY,
            if (evidence.path.ipv4Ok && !evidence.path.ipv6Ok) "UseIPv4" else "UseIP",
            CouncilSeat.DNS,
            70,
            if (evidence.path.ipv4Ok && !evidence.path.ipv6Ok) {
                "IPv6 did not answer; asking for AAAA first costs a full timeout per name."
            } else {
                "Both families answered, so the resolver may return whatever the destination actually has."
            }
        )

        if (tampered.isNotEmpty()) {
            proposals += Proposal(
                TuningField.DNS_HIJACK,
                "true",
                CouncilSeat.DNS,
                80,
                "A block-page address was measured; the hijack guard must capture that answer."
            )
            proposals += Proposal(
                TuningField.DNS_FAKE_IP,
                "true",
                CouncilSeat.DNS,
                76,
                "A fake-IP pool keeps a tampered answer from ever reaching an application."
            )
        }

        // MARBLE_FILTER_LAB_V273 — the cross-check the V236 seat could not make.
        //
        // V236 asked one name on several resolvers and compared the answers *for that one name*,
        // which is both too little and too much: too little because one name is not a policy, and
        // too much because two honest resolvers may legitimately return different A records for a
        // CDN name. The lab asks a fixed handful of names, several of which a filter is expected
        // to act on, and compares the block-page verdicts rather than raw addresses — so a name
        // that two resolvers answer differently but honestly is a suspicion, while a name that one
        // resolver answers with a block-page address is proof, and it names the resolver.
        val lab = evidence.filter
        val poisoned = lab.dnsNames.filter { it.verdict == DnsVerdict.BLOCKED }
        val split = lab.dnsNames.filter { it.verdict == DnsVerdict.DIFFERENT }
        if (poisoned.isNotEmpty()) {
            findings += CouncilFinding(
                CouncilSeat.DNS,
                CouncilSeverity.CRITICAL,
                "${poisoned.size} name(s) were answered with a block-page address",
                poisoned.joinToString(" · ") { check ->
                    "${check.name}: ${check.blockPageServer.ifBlank { "a resolver" }} said " +
                        "${check.firstAnswer.ifBlank { "nothing" }}" +
                        (if (check.secondAnswer.isNotBlank() && check.secondAnswer != check.firstAnswer) {
                            " while ${check.secondServer.ifBlank { "another resolver" }} said ${check.secondAnswer}"
                        } else {
                            ""
                        })
                }
            )
            proposals += Proposal(
                TuningField.DNS_HIJACK,
                "true",
                CouncilSeat.DNS,
                92,
                "A block-page address was measured for ${poisoned.size} name(s): the hijack guard must treat every plain answer as suspect."
            )
            proposals += Proposal(
                TuningField.DNS_FAKE_IP,
                "true",
                CouncilSeat.DNS,
                88,
                "A fake-IP pool keeps a rewritten answer from ever reaching an application, so a tampered record costs one failed lookup instead of a broken page."
            )
            proposals += Proposal(
                TuningField.DNS_STRATEGY,
                "UseIP",
                CouncilSeat.DNS,
                74,
                "Plain answers are being rewritten; the resolver strategy is kept deterministic so the encrypted path is the only one that is trusted."
            )
        }
        if (split.isNotEmpty()) {
            findings += CouncilFinding(
                CouncilSeat.DNS,
                CouncilSeverity.WARNING,
                "${split.size} name(s) resolved differently on different resolvers",
                split.joinToString(" · ") { check ->
                    "${check.name}: ${check.firstServer.ifBlank { "one resolver" }} said " +
                        "${check.firstAnswer.ifBlank { "nothing" }}, " +
                        "${check.secondServer.ifBlank { "another" }} said ${check.secondAnswer.ifBlank { "nothing" }}"
                } + " — a CDN can answer differently and honestly, so this is a suspicion the encrypted path resolves, not a verdict."
            )
        }
        if (lab.dnsNames.isNotEmpty() && poisoned.isEmpty() && split.isEmpty()) {
            findings += CouncilFinding(
                CouncilSeat.DNS,
                CouncilSeverity.GOOD,
                "Every checked name resolved honestly on every resolver",
                lab.dnsNames.joinToString(" · ") { check ->
                    "${check.name}: ${check.firstAnswer.ifBlank { "no answer" }}"
                }
            )
        }
    }

    private fun seatPorts(
        evidence: NetworkEvidence,
        metrics: Metrics,
        findings: MutableList<CouncilFinding>,
        proposals: MutableList<Proposal>
    ) {
        val tcp = evidence.ports.filter { it.protocol == PortProtocol.TCP }
        if (tcp.isEmpty()) {
            findings += CouncilFinding(
                CouncilSeat.PORTS,
                CouncilSeverity.NOTICE,
                "No port matrix this round",
                "The port ladder did not run; nothing about reachability is claimed."
            )
            return
        }
        val open = tcp.filter { it.open }.map { it.port }.sorted()
        val closed = tcp.filterNot { it.open }.map { it.port }.sorted()
        val port443 = tcp.firstOrNull { it.port == 443 }

        val severity = when {
            open.isEmpty() -> CouncilSeverity.CRITICAL
            port443 != null && !port443.open -> CouncilSeverity.CRITICAL
            closed.isNotEmpty() -> CouncilSeverity.NOTICE
            else -> CouncilSeverity.GOOD
        }
        findings += CouncilFinding(
            CouncilSeat.PORTS,
            severity,
            when {
                open.isEmpty() -> "No probed port answered"
                port443 != null && !port443.open -> "TCP 443 is filtered while other ports answer"
                closed.isNotEmpty() -> "${open.size} of ${tcp.size} probed ports answer"
                else -> "Every probed port answered"
            },
            "Open: ${open.joinToString(", ") { it.toString() }.ifBlank { "none" }}" +
                (if (closed.isNotEmpty()) " · filtered: ${closed.joinToString(", ")}" else "")
        )

        val https = evidence.httpsTargets
        if (https.isNotEmpty()) {
            val httpsOpen = https.count { it.reachable }
            val httpsDead = https.filterNot { it.reachable }
            findings += CouncilFinding(
                CouncilSeat.PORTS,
                when {
                    httpsOpen == https.size -> CouncilSeverity.GOOD
                    httpsOpen > 0 -> CouncilSeverity.WARNING
                    else -> CouncilSeverity.CRITICAL
                },
                when {
                    httpsOpen == https.size -> "Every HTTPS target answered"
                    httpsOpen > 0 -> "${httpsOpen} of ${https.size} HTTPS targets answered"
                    else -> "No HTTPS target answered"
                },
                https.joinToString(" · ") { probe ->
                    "${probe.host}:${probe.port} · ${if (probe.reachable) "TLS completed in ${probe.latencyMs}ms" else "no TLS"}" +
                        if (probe.http2) " · h2" else ""
                }
            )
            // TCP 443 answering while TLS over 443 dies is the signature of a middlebox that is
            // inspecting the handshake: it lets the TCP handshake through and kills the ClientHello.
            if (port443?.open == true && httpsDead.any { it.port == 443 }) {
                findings += CouncilFinding(
                    CouncilSeat.PORTS,
                    CouncilSeverity.WARNING,
                    "TCP 443 answers but TLS over it does not",
                    "The port is open and the handshake to it is not: something in the middle is reading SNI and resetting the connection."
                )
                proposals += Proposal(
                    TuningField.FRAGMENT,
                    FragmentStrategy.RECORD.id,
                    CouncilSeat.PORTS,
                    66,
                    "TCP carries but TLS does not: splitting the ClientHello across TLS records is the first thing to try before the heavier treatments."
                )
            }
        }

        val fastOpen = port443?.open == true && metrics.medianRttMs in 1.0..150.0
        proposals += Proposal(
            TuningField.TCP_FAST_OPEN,
            fastOpen.toString(),
            CouncilSeat.PORTS,
            68,
            if (fastOpen) {
                "443 answers in ${ms(metrics.medianRttMs)}; fast open saves one round trip per connection."
            } else {
                "Either 443 did not answer or the path is too slow for fast open to be worth the risk."
            }
        )
    }

    private fun seatUdp(
        evidence: NetworkEvidence,
        metrics: Metrics,
        findings: MutableList<CouncilFinding>,
        proposals: MutableList<Proposal>
    ) {
        val udpAlive = evidence.path.quicOk || evidence.path.udpDnsOk
        val severity = when {
            evidence.path.quicOk -> CouncilSeverity.GOOD
            evidence.path.udpDnsOk -> CouncilSeverity.NOTICE
            else -> CouncilSeverity.WARNING
        }
        findings += CouncilFinding(
            CouncilSeat.UDP,
            severity,
            when {
                evidence.path.quicOk -> "QUIC/443 answered"
                evidence.path.udpDnsOk -> "Plain UDP answers; QUIC/443 did not"
                else -> "No UDP answer on either probe"
            },
            "QUIC and UDP-53 are measured separately: a carrier that drops one rarely drops both."
        )

        proposals += Proposal(
            TuningField.UDP_PROBE,
            udpAlive.toString(),
            CouncilSeat.UDP,
            74,
            if (udpAlive) {
                "UDP crosses this path, so measuring a node's UDP success is worth the probes."
            } else {
                "No UDP answer at all: UDP probes would only spend budget to confirm the same block."
            }
        )
        proposals += Proposal(
            TuningField.MUX_UDP443,
            if (evidence.path.quicOk) "skip" else "reject",
            CouncilSeat.UDP,
            64,
            if (evidence.path.quicOk) {
                "QUIC works here, so UDP/443 through mux is skipped rather than rejected."
            } else {
                "QUIC does not work here; carrying UDP/443 over mux only adds latency to a dead flow."
            }
        )
        proposals += Proposal(
            TuningField.MUX_XUDP_CONCURRENCY,
            if (evidence.path.quicOk) "32" else "8",
            CouncilSeat.UDP,
            52,
            "xUDP concurrency is sized to whether this path actually carries datagrams."
        )
        if (!udpAlive && metrics.score < 100) {
            findings += CouncilFinding(
                CouncilSeat.UDP,
                CouncilSeverity.NOTICE,
                "UDP-dependent features are demoted",
                "Calls, QUIC sites and UDP ping will fall back to TCP on this link."
            )
        }
    }

    private fun seatMtu(
        evidence: NetworkEvidence,
        metrics: Metrics,
        findings: MutableList<CouncilFinding>,
        proposals: MutableList<Proposal>
    ) {
        val measured = evidence.path.pathMtu
        val confidence = evidence.path.mtuConfidence.coerceIn(0.0, 1.0)
        val link = evidence.radio.interfaceMtu
        val ceiling = minOf(
            MTU_MAX,
            when {
                measured > 0 -> measured
                link > 0 -> link
                else -> MTU_MAX
            }
        ).coerceAtLeast(MTU_MIN)

        findings += CouncilFinding(
            CouncilSeat.MTU,
            when {
                measured <= 0 -> CouncilSeverity.NOTICE
                measured < 1400 -> CouncilSeverity.WARNING
                else -> CouncilSeverity.GOOD
            },
            if (measured > 0) "Path carries $measured bytes" else "Path MTU not measured",
            if (measured > 0) {
                "Padded datagrams up to $measured bytes were answered (confidence ${(confidence * 100).roundToInt()}%)."
            } else {
                "The link reports ${link.takeIf { it > 0 } ?: 0} bytes; the tunnel MTU stays on its safe default."
            }
        )

        val tunMtu = when {
            measured <= 0 && evidence.radio.transport != LinkTransport.CELLULAR -> 0
            measured <= 0 -> 1400
            else -> (measured - VPN_HEADROOM).coerceIn(MTU_MIN, MTU_MAX)
        }
        proposals += Proposal(
            TuningField.TUN_MTU,
            tunMtu.toString(),
            CouncilSeat.MTU,
            (55 + (confidence * 35.0)).roundToInt().coerceIn(55, 90),
            if (tunMtu == 0) {
                "A ${ceiling}-byte link with no measured ceiling keeps the datapath's automatic MTU."
            } else {
                "Leave ${VPN_HEADROOM} bytes of headroom under the measured $measured-byte path for the tunnel."
            }
        )
    }

    private fun seatFamily(
        evidence: NetworkEvidence,
        metrics: Metrics,
        findings: MutableList<CouncilFinding>,
        proposals: MutableList<Proposal>
    ) {
        val path = evidence.path
        val severity = when {
            !path.ipv4Ok && !path.ipv6Ok -> CouncilSeverity.CRITICAL
            path.ipv4Ok && path.ipv6Ok -> CouncilSeverity.GOOD
            else -> CouncilSeverity.WARNING
        }
        findings += CouncilFinding(
            CouncilSeat.FAMILY,
            severity,
            when {
                path.ipv4Ok && path.ipv6Ok -> "IPv4 ${ms(path.ipv4Ms.toDouble())} · IPv6 ${ms(path.ipv6Ms.toDouble())}"
                path.ipv4Ok -> "IPv4 answers, IPv6 does not"
                path.ipv6Ok -> "IPv6 answers, IPv4 does not"
                else -> "Neither family answered"
            },
            "Both families were dialled directly, so a dead AAAA record cannot be mistaken for a slow server."
        )

        val family = when {
            path.ipv4Ok && !path.ipv6Ok -> AddressFamilyMode.PREFER_IPV4
            path.ipv6Ok && !path.ipv4Ok -> AddressFamilyMode.PREFER_IPV6
            path.ipv4Ok && path.ipv6Ok -> AddressFamilyMode.SMART
            else -> AddressFamilyMode.SMART
        }
        proposals += Proposal(
            TuningField.ADDRESS_FAMILY,
            family.name,
            CouncilSeat.FAMILY,
            84,
            when (family) {
                AddressFamilyMode.PREFER_IPV4 -> "IPv6 is unreachable on this link; prefer IPv4 but keep IPv6 as the hedge."
                AddressFamilyMode.PREFER_IPV6 -> "Only IPv6 answered; prefer it, with IPv4 as the hedge."
                else -> "Both families answer; the smart race is the cheapest correct choice."
            }
        )
        proposals += Proposal(
            TuningField.IPV6,
            "true",
            CouncilSeat.FAMILY,
            40,
            "The family stays enabled even when it is unhealthy: turning it off is how leaks begin, and the preference above already handles it."
        )
        proposals += Proposal(
            TuningField.HAPPY_EYEBALLS_MS,
            when {
                !path.ipv6Ok && path.ipv4Ok -> 120
                path.ipv6Ok && !path.ipv4Ok -> 120
                metrics.medianRttMs > 180.0 -> 100
                else -> 60
            }.toString(),
            CouncilSeat.FAMILY,
            70,
            "The race delay is stretched when one family is dead, so the healthy one is tried first without waiting for a full timeout."
        )
    }

    private fun seatDpi(
        evidence: NetworkEvidence,
        metrics: Metrics,
        findings: MutableList<CouncilFinding>,
        proposals: MutableList<Proposal>
    ) {
        val path = evidence.path
        val filter = evidence.filter
        val labFiltered = filter.verdict == FilterVerdict.FILTERED
        val labSuspect = filter.verdict == FilterVerdict.SUSPECT
        val labUnreachable = filter.verdict == FilterVerdict.UNREACHABLE
        val severity = when {
            path.injectedReset -> CouncilSeverity.CRITICAL
            labFiltered -> CouncilSeverity.CRITICAL
            // A link where not one TLS target answered is not "suspicious": it is a dead path,
            // and calling it a warning let the rest of the plan treat it as a slow network.
            labUnreachable -> CouncilSeverity.CRITICAL
            !path.tlsCompleted -> CouncilSeverity.WARNING
            labSuspect -> CouncilSeverity.WARNING
            else -> CouncilSeverity.GOOD
        }
        findings += CouncilFinding(
            CouncilSeat.DPI,
            severity,
            when {
                path.injectedReset -> "An injected TLS reset was measured"
                labFiltered -> "Filtering measured on ${filter.tlsFailed} of ${filter.tlsProbed} TLS targets"
                !path.tlsCompleted -> "The TLS handshake did not complete"
                labSuspect -> "Part of the handshake matrix did not complete"
                labUnreachable -> "No TLS target answered at all"
                else -> "The TLS handshake completed cleanly"
            },
            "A completed handshake and a reset are different verdicts: only the second justifies the heavy treatment."
        )

        // TCP answers but TLS dies, again and again, across targets: a middlebox reading SNI.
        if (evidence.sniSuspicion && !path.injectedReset) {
            findings += CouncilFinding(
                CouncilSeat.DPI,
                CouncilSeverity.WARNING,
                "SNI interference suspected",
                "TCP 443 answers but the TLS handshake does not complete: something in the path is inspecting the ClientHello and resetting what it does not like."
            )
        }

        // MARBLE_FILTER_LAB_V273 — the lab's per-target truth. One finding per family so the page
        // can print *which* name died instead of one aggregate adjective, and so a reader can tell
        // "the whole link is down" (every target, open names included) from "this country filters
        // these names" (the routinely filtered ones only).
        if (filter.tls.isNotEmpty()) {
            val dead = filter.tls.filter { !it.reachable }
            if (dead.isNotEmpty()) {
                findings += CouncilFinding(
                    CouncilSeat.DPI,
                    when {
                        filter.tlsCompleted == 0 -> CouncilSeverity.CRITICAL
                        filter.openHostsFailed > 0 -> CouncilSeverity.WARNING
                        else -> CouncilSeverity.WARNING
                    },
                    when {
                        filter.tlsCompleted == 0 ->
                            "Every TLS target failed; the public internet is not reachable from this link"
                        filter.openHostsFailed > 0 ->
                            "${filter.openHostsFailed} routinely open target(s) failed as well — this is a path problem, not only a policy"
                        else ->
                            "Exactly the routinely filtered names failed — SG/SNI policy on this path"
                    },
                    filter.tls.joinToString(" · ") { probe ->
                        "${probe.sni}: ${probe.outcome.label.lowercase()}" +
                            (if (probe.latencyMs > 0) " in ${probe.latencyMs}ms" else "")
                    }
                )
            }
            if (filter.tlsReset > 0) {
                findings += CouncilFinding(
                    CouncilSeat.DPI,
                    CouncilSeverity.CRITICAL,
                    "${filter.tlsReset} handshake(s) were reset before they could finish",
                    "A reset that arrives during the ClientHello is injected by a middlebox: the destination never answered, and its own server never saw the request."
                )
            }
        }
        if (filter.blockPagesFound > 0) {
            findings += CouncilFinding(
                CouncilSeat.DPI,
                CouncilSeverity.CRITICAL,
                "A policy page was served in place of ${filter.blockPagesFound} site(s)",
                filter.blockPages.filter { it.intercepted }
                    .joinToString(" · ") { probe ->
                        "${probe.host}: HTTP ${probe.statusCode}" +
                            (if (probe.redirectHost.isNotBlank()) " → ${probe.redirectHost}" else "")
                    }
            )
        }

        val strategy = when {
            path.injectedReset || labFiltered -> FragmentStrategy.FULL
            !path.tlsCompleted -> FragmentStrategy.RECORD
            labSuspect -> FragmentStrategy.RECORD
            else -> FragmentStrategy.AUTO
        }
        proposals += Proposal(
            TuningField.FRAGMENT,
            strategy.id,
            CouncilSeat.DPI,
            if (labFiltered) 98 else 96,
            when (strategy) {
                FragmentStrategy.FULL ->
                    "A reset inside the handshake is a middlebox that has already decided; split the whole ClientHello across segments."
                FragmentStrategy.RECORD ->
                    "The handshake never completed; splitting the ClientHello across TLS records is the cheap first attempt."
                else ->
                    "Nothing interfered with a completed handshake, so the default automatic treatment is kept — a protection is never spent without evidence."
            }
        )
        if (path.injectedReset || labFiltered) {
            proposals += Proposal(
                TuningField.MUX_PADDING,
                "true",
                CouncilSeat.DPI,
                88,
                "Mux padding hides the stream's first bytes from the same middlebox that reset the handshake."
            )
        }
    }

    private fun seatCore(
        evidence: NetworkEvidence,
        metrics: Metrics,
        findings: MutableList<CouncilFinding>,
        proposals: MutableList<Proposal>,
        muxSafe: Boolean
    ) {
        val device = evidence.device
        val cores = device.cores.coerceIn(0, 32)
        val thermal = device.thermalStatus
        val lowBattery = device.batteryPercent in 0..20 && !device.charging
        val hot = thermal >= 3
        val downKbps = maxOf(evidence.throughputDownKbps, evidence.radio.linkDownKbps)

        // MARBLE_FILTER_LAB_V273 — a measured filtrations verdict is as loud as a captive portal:
        // the cores run the stealth workload when the lab actually found policy on the path.
        val censored = evidence.radio.captivePortal || evidence.sniSuspicion ||
            evidence.dns.any { it.blockPage } ||
            evidence.filter.verdict == FilterVerdict.FILTERED
        val power = when {
            evidence.radio.captivePortal -> PowerProfile.EFFICIENT
            hot || lowBattery -> PowerProfile.EFFICIENT
            device.memoryMb in 1..2_499L -> PowerProfile.BALANCED
            metrics.medianRttMs in 1.0..60.0 && metrics.jitterMs <= 15.0 &&
                metrics.lossPercent <= 1.0 && cores >= 8 && device.memoryMb >= 6_000 -> PowerProfile.EXTREME
            metrics.medianRttMs <= 130.0 && metrics.jitterMs <= 30.0 -> PowerProfile.TURBO
            else -> PowerProfile.BALANCED
        }
        val interferenceTail = if (censored) " · interference detected on this link: the cores run the stealth workload" else ""
        findings += CouncilFinding(
            CouncilSeat.CORE,
            when {
                hot -> CouncilSeverity.WARNING
                // MARBLE_FILTER_LAB_V273 — interference outranks "this device is fast enough":
                // a seat that measured a policy on the path may not answer GOOD because the phone
                // happens to have eight cores.
                censored -> CouncilSeverity.WARNING
                power == PowerProfile.EXTREME -> CouncilSeverity.GOOD
                else -> CouncilSeverity.NOTICE
            },
            "Device: $cores cores · ${if (device.memoryMb > 0) "${device.memoryMb} MB" else "memory unknown"} · " +
                "thermal $thermal$interferenceTail",
            "The core profile is projected from the device and the measured path, never from a fixed default."
        )
        proposals += Proposal(
            TuningField.POWER_PROFILE,
            power.id,
            CouncilSeat.CORE,
            82,
            when (power) {
                PowerProfile.EFFICIENT -> if (hot) {
                    "The device is thermally throttled; a lighter core profile keeps the tunnel alive instead of cooking it."
                } else {
                    "Battery is low and not charging; the efficient profile protects the session."
                }
                PowerProfile.EXTREME -> "A fast, steady, low-loss path on a strong device can actually use the extreme profile."
                PowerProfile.TURBO -> "The measured path is fast and stable enough for the turbo profile."
                PowerProfile.BALANCED -> "The path or the device does not justify a specialised profile."
            }
        )

        val dnsTampered = evidence.dns.any { it.blockPage }
        val workload = when {
            evidence.radio.captivePortal || evidence.sniSuspicion || dnsTampered -> WorkloadProfile.STEALTH
            // MARBLE_FILTER_LAB_V273 — a lab that *measured* filtering is the same evidence the
            // sniSuspicion flag used to be, except that it was actually collected.
            evidence.filter.verdict == FilterVerdict.FILTERED -> WorkloadProfile.STEALTH
            evidence.path.injectedReset -> WorkloadProfile.STEALTH
            metrics.jitterMs > 45.0 || metrics.lossPercent > 4.0 -> WorkloadProfile.STABILITY
            downKbps >= 20_000 -> WorkloadProfile.STREAMING
            else -> WorkloadProfile.INTERACTIVE
        }
        proposals += Proposal(
            TuningField.WORKLOAD_PROFILE,
            workload.name,
            CouncilSeat.CORE,
            70,
            when (workload) {
                WorkloadProfile.STEALTH -> "Interference was measured; the stealth workload shapes the cores' own behaviour."
                WorkloadProfile.STABILITY -> "Loss or jitter dominates, so stability outranks raw throughput."
                WorkloadProfile.STREAMING -> "The link is wide enough (${kbps(downKbps)}) for the streaming workload."
                else -> "Interactive use is the honest default for this path."
            }
        )

        val wantMux = muxSafe && metrics.medianRttMs > 120.0 && !evidence.path.injectedReset &&
            !evidence.sniSuspicion && evidence.filter.verdict != FilterVerdict.FILTERED
        proposals += Proposal(
            TuningField.MUX,
            wantMux.toString(),
            CouncilSeat.CORE,
            76,
            when {
                !muxSafe -> "This route's transport cannot carry the multiplexer; enabling it is the documented 'connects, then nothing loads' failure."
                wantMux -> "A ${ms(metrics.medianRttMs)} path pays for a new handshake on every stream; multiplexing amortises it."
                else -> "The path is quick enough that multiplexing would add a queue for no gain."
            }
        )
        proposals += Proposal(
            TuningField.MUX_CONCURRENCY,
            (if (cores >= 8) 8 else 4).coerceIn(MUX_MIN, MUX_MAX).toString(),
            CouncilSeat.CORE,
            60,
            "Mux concurrency is sized to the device's cores so the tunnel does not queue behind itself."
        )
        proposals += Proposal(
            TuningField.ADAPTIVE_BUFFER,
            "true",
            CouncilSeat.CORE,
            58,
            "Adaptive buffers let the cores grow the socket window only when the path proves it can carry it."
        )
        val bufferKb = if (downKbps >= 20_000 && device.memoryMb >= 4_000) 512 else 0
        proposals += Proposal(
            TuningField.POLICY_BUFFER_KB,
            bufferKb.toString(),
            CouncilSeat.CORE,
            54,
            if (bufferKb > 0) {
                "A ${kbps(downKbps)} link on ${device.memoryMb} MB of RAM can afford a 512 KiB policy buffer."
            } else {
                "An automatic buffer is the right size for this link and this device."
            }
        )
        proposals += Proposal(
            TuningField.PING_CONCURRENCY,
            when {
                cores >= 8 -> "24"
                cores >= 4 -> "16"
                else -> "8"
            },
            CouncilSeat.CORE,
            56,
            "Sweep concurrency follows the device's core count; oversubscribing it makes pings slower, not faster."
        )
    }

    // ------------------------------------------------------------------------------------------
    // Reading and writing the settings — the only place a [TuningField] becomes an AppSettings edit
    // ------------------------------------------------------------------------------------------

    fun read(settings: AppSettings, field: TuningField): String = when (field) {
        TuningField.POWER_PROFILE -> parsePowerProfile(settings.powerProfile).id
        TuningField.WORKLOAD_PROFILE -> settings.workloadProfile.name
        TuningField.MTU_MIN -> settings.mtuMin.toString()
        TuningField.MTU_MAX -> settings.mtuMax.toString()
        TuningField.TUN_MTU -> settings.tun2SocksMtu.toString()
        TuningField.UDP_TIMEOUT_SEC -> settings.tun2SocksUdpTimeoutSec.toString()
        TuningField.TCP_KEEPALIVE_SEC -> settings.xrayTcpKeepAliveIntervalSec.toString()
        TuningField.CONNECT_TIMEOUT_SEC -> settings.singBoxConnectTimeoutSec.toString()
        TuningField.FALLBACK_COUNT -> settings.fallbackCount.toString()
        TuningField.DNS_PRIMARY -> settings.dnsPrimaryIp
        TuningField.DNS_SECONDARY -> settings.dnsSecondaryIp
        TuningField.DNS_PRIMARY_DOH -> settings.dnsPrimaryDoH
        TuningField.DNS_SECONDARY_DOH -> settings.dnsSecondaryDoH
        TuningField.DNS_STRATEGY -> settings.dnsQueryStrategy
        TuningField.DNS_HIJACK -> settings.dnsHijackEnabled.toString()
        TuningField.DNS_FAKE_IP -> settings.dnsFakeIpEnabled.toString()
        TuningField.TCP_FAST_OPEN -> settings.tcpFastOpenEnabled.toString()
        TuningField.UDP_PROBE -> settings.udpProbeEnabled.toString()
        TuningField.MUX -> settings.muxEnabled.toString()
        TuningField.MUX_CONCURRENCY -> settings.muxConcurrency.toString()
        TuningField.MUX_XUDP_CONCURRENCY -> settings.muxXudpConcurrency.toString()
        TuningField.MUX_UDP443 -> settings.muxUdp443
        TuningField.MUX_PADDING -> settings.singBoxMuxPadding.toString()
        TuningField.ADAPTIVE_BUFFER -> settings.adaptiveBufferEnabled.toString()
        TuningField.POLICY_BUFFER_KB -> settings.xrayPolicyBufferSizeKb.toString()
        TuningField.PING_TIMEOUT_SEC -> settings.pingTimeoutSec.toString()
        TuningField.PING_SAMPLES -> settings.pingSamples.toString()
        TuningField.PING_CONCURRENCY -> settings.pingConcurrency.toString()
        TuningField.ADDRESS_FAMILY -> settings.addressFamilyMode.name
        TuningField.IPV6 -> settings.ipv6Enabled.toString()
        TuningField.HAPPY_EYEBALLS_MS -> settings.happyEyeballsTryDelayMs.toString()
        TuningField.FRAGMENT -> parseFragmentStrategy(settings.fragmentStrategy).id
    }

    private fun write(settings: AppSettings, field: TuningField, value: String): AppSettings = when (field) {
        TuningField.POWER_PROFILE -> settings.copy(powerProfile = parsePowerProfile(value).id)
        TuningField.WORKLOAD_PROFILE -> settings.copy(
            workloadProfile = runCatching { WorkloadProfile.valueOf(value) }.getOrDefault(WorkloadProfile.AUTO)
        )
        TuningField.MTU_MIN -> settings.copy(mtuMin = value.toIntOrNull()?.coerceIn(MTU_MIN, MTU_MAX) ?: settings.mtuMin)
        TuningField.MTU_MAX -> settings.copy(mtuMax = value.toIntOrNull()?.coerceIn(MTU_MIN, MTU_MAX) ?: settings.mtuMax)
        TuningField.TUN_MTU -> settings.copy(
            tun2SocksMtu = value.toIntOrNull()?.let { if (it <= 0) 0 else it.coerceIn(MTU_MIN, MTU_MAX) }
                ?: settings.tun2SocksMtu
        )
        TuningField.UDP_TIMEOUT_SEC -> settings.copy(
            tun2SocksUdpTimeoutSec = value.toIntOrNull()?.coerceIn(15, 300) ?: settings.tun2SocksUdpTimeoutSec
        )
        TuningField.TCP_KEEPALIVE_SEC -> settings.copy(
            xrayTcpKeepAliveIntervalSec = value.toIntOrNull()?.coerceIn(0, 300) ?: settings.xrayTcpKeepAliveIntervalSec
        )
        TuningField.CONNECT_TIMEOUT_SEC -> settings.copy(
            singBoxConnectTimeoutSec = value.toIntOrNull()?.coerceIn(3, 60) ?: settings.singBoxConnectTimeoutSec
        )
        TuningField.FALLBACK_COUNT -> settings.copy(
            fallbackCount = value.toIntOrNull()?.coerceIn(1, 8) ?: settings.fallbackCount
        )
        TuningField.DNS_PRIMARY -> settings.copy(dnsPrimaryIp = value.trim().ifBlank { settings.dnsPrimaryIp })
        TuningField.DNS_SECONDARY -> settings.copy(dnsSecondaryIp = value.trim().ifBlank { settings.dnsSecondaryIp })
        TuningField.DNS_PRIMARY_DOH -> settings.copy(dnsPrimaryDoH = value.trim().ifBlank { settings.dnsPrimaryDoH })
        TuningField.DNS_SECONDARY_DOH -> settings.copy(dnsSecondaryDoH = value.trim().ifBlank { settings.dnsSecondaryDoH })
        TuningField.DNS_STRATEGY -> settings.copy(dnsQueryStrategy = value.trim().ifBlank { settings.dnsQueryStrategy })
        TuningField.DNS_HIJACK -> settings.copy(dnsHijackEnabled = value.toBooleanStrictOrNull() ?: settings.dnsHijackEnabled)
        TuningField.DNS_FAKE_IP -> settings.copy(dnsFakeIpEnabled = value.toBooleanStrictOrNull() ?: settings.dnsFakeIpEnabled)
        TuningField.TCP_FAST_OPEN -> settings.copy(tcpFastOpenEnabled = value.toBooleanStrictOrNull() ?: settings.tcpFastOpenEnabled)
        TuningField.UDP_PROBE -> settings.copy(udpProbeEnabled = value.toBooleanStrictOrNull() ?: settings.udpProbeEnabled)
        TuningField.MUX -> settings.copy(muxEnabled = value.toBooleanStrictOrNull() ?: settings.muxEnabled)
        TuningField.MUX_CONCURRENCY -> settings.copy(
            muxConcurrency = value.toIntOrNull()?.coerceIn(MUX_MIN, MUX_MAX) ?: settings.muxConcurrency
        )
        TuningField.MUX_XUDP_CONCURRENCY -> settings.copy(
            muxXudpConcurrency = value.toIntOrNull()?.coerceIn(XUDP_MIN, XUDP_MAX) ?: settings.muxXudpConcurrency
        )
        TuningField.MUX_UDP443 -> settings.copy(
            muxUdp443 = value.trim().takeIf { it in setOf("reject", "allow", "skip") } ?: settings.muxUdp443
        )
        TuningField.MUX_PADDING -> settings.copy(
            singBoxMuxPadding = value.toBooleanStrictOrNull() ?: settings.singBoxMuxPadding
        )
        TuningField.ADAPTIVE_BUFFER -> settings.copy(
            adaptiveBufferEnabled = value.toBooleanStrictOrNull() ?: settings.adaptiveBufferEnabled
        )
        TuningField.POLICY_BUFFER_KB -> settings.copy(
            xrayPolicyBufferSizeKb = value.toIntOrNull()?.coerceIn(BUFFER_MIN_KB, BUFFER_MAX_KB)
                ?: settings.xrayPolicyBufferSizeKb
        )
        TuningField.PING_TIMEOUT_SEC -> settings.copy(
            pingTimeoutSec = value.toIntOrNull()?.let { PingBudget.timeoutSec(it) } ?: settings.pingTimeoutSec
        )
        TuningField.PING_SAMPLES -> settings.copy(
            pingSamples = value.toIntOrNull()?.let { PingBudget.samples(it) } ?: settings.pingSamples
        )
        TuningField.PING_CONCURRENCY -> settings.copy(
            pingConcurrency = value.toIntOrNull()?.let { PingBudget.concurrency(it) } ?: settings.pingConcurrency
        )
        TuningField.ADDRESS_FAMILY -> settings.copy(
            addressFamilyMode = runCatching { AddressFamilyMode.valueOf(value) }
                .getOrDefault(settings.addressFamilyMode)
        )
        TuningField.IPV6 -> settings.copy(ipv6Enabled = value.toBooleanStrictOrNull() ?: settings.ipv6Enabled)
        TuningField.HAPPY_EYEBALLS_MS -> settings.copy(
            happyEyeballsTryDelayMs = value.toIntOrNull()?.coerceIn(HAPPY_EYEBALLS_MIN_MS, HAPPY_EYEBALLS_MAX_MS)
                ?: settings.happyEyeballsTryDelayMs
        )
        TuningField.FRAGMENT -> settings.copy(fragmentStrategy = parseFragmentStrategy(value).id)
    }

    /** A last, cheap guarantee: the applied plan is a config the pinned cores will accept. */
    fun normalize(settings: AppSettings): AppSettings {
        val min = settings.mtuMin.coerceIn(MTU_MIN, MTU_MAX)
        val max = settings.mtuMax.coerceIn(MTU_MIN, MTU_MAX)
        return settings.copy(
            mtuMin = minOf(min, max),
            mtuMax = maxOf(min, max),
            tun2SocksMtu = if (settings.tun2SocksMtu <= 0) 0 else settings.tun2SocksMtu.coerceIn(MTU_MIN, MTU_MAX),
            pingTimeoutSec = PingBudget.timeoutSec(settings.pingTimeoutSec),
            pingSamples = PingBudget.samples(settings.pingSamples),
            pingConcurrency = PingBudget.concurrency(settings.pingConcurrency),
            muxConcurrency = settings.muxConcurrency.coerceIn(MUX_MIN, MUX_MAX),
            muxXudpConcurrency = settings.muxXudpConcurrency.coerceIn(XUDP_MIN, XUDP_MAX),
            xrayPolicyBufferSizeKb = settings.xrayPolicyBufferSizeKb.coerceIn(BUFFER_MIN_KB, BUFFER_MAX_KB),
            happyEyeballsTryDelayMs = settings.happyEyeballsTryDelayMs
                .coerceIn(HAPPY_EYEBALLS_MIN_MS, HAPPY_EYEBALLS_MAX_MS),
            dnsQueryStrategy = settings.dnsQueryStrategy
                .takeIf { it in setOf("UseIP", "UseIPv4", "UseIPv6") } ?: "UseIP"
        )
    }

    // ------------------------------------------------------------------------------------------
    // Council mechanics
    // ------------------------------------------------------------------------------------------

    /**
     * One winner per field: the highest measured confidence, and — when two seats measured equally
     * well — the earlier seat, because the seats are ordered from the wire inwards. Every loser is
     * kept as a dissent line, so the page can show that the council disagreed rather than pretend
     * it did not.
     */
    fun resolve(proposals: List<Proposal>): List<Proposal> {
        val byField = LinkedHashMap<TuningField, MutableList<Proposal>>()
        for (proposal in proposals) {
            byField.getOrPut(proposal.field) { mutableListOf() } += proposal
        }
        return byField.map { (_, group) ->
            group.sortedWith(compareByDescending<Proposal> { it.confidence }.thenBy { it.seat.ordinal })
                .first()
        }
    }

    fun dissentFor(proposal: Proposal, all: List<Proposal>): String {
        val others = all.filter {
            it.field == proposal.field && it.seat != proposal.seat && !it.value.equals(proposal.value, true)
        }
        if (others.isEmpty()) return ""
        return others.joinToString("; ") { "${it.seat.label} wanted ${it.value}" }
    }

    // ------------------------------------------------------------------------------------------
    // Small, honest arithmetic
    // ------------------------------------------------------------------------------------------

    fun median(values: List<Int>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[middle - 1].toDouble() + sorted[middle].toDouble()) / 2.0
        } else {
            sorted[middle].toDouble()
        }
    }

    fun percentile(values: List<Int>, fraction: Double): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val index = ((sorted.size - 1) * fraction.coerceIn(0.0, 1.0)).roundToInt()
        return sorted[index.coerceIn(0, sorted.lastIndex)].toDouble()
    }

    private fun bars(level: Int): String = when {
        level < 0 -> "unknown"
        else -> "$level/4"
    }

    private fun ms(value: Double): String = "${value.roundToInt()} ms"

    private fun kbps(value: Int): String = when {
        value <= 0 -> "unknown"
        value >= 1_000 -> "${(value / 1_000.0).roundToInt()} Mbps"
        else -> "$value kbps"
    }

    private fun dohFor(server: String): String? = when (server) {
        "1.1.1.1" -> "https://1.1.1.1/dns-query"
        "1.0.0.1" -> "https://1.0.0.1/dns-query"
        "8.8.8.8" -> "https://8.8.8.8/dns-query"
        "8.8.4.4" -> "https://8.8.4.4/dns-query"
        "9.9.9.9" -> "https://9.9.9.9/dns-query"
        "149.112.112.112" -> "https://149.112.112.112/dns-query"
        "94.140.14.14" -> "https://94.140.14.14/dns-query"
        else -> null
    }
}
