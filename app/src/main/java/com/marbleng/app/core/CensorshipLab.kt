package com.marbleng.app.core

import com.marbleng.app.model.AddressFamilyMode
import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.FragmentStrategy
import com.marbleng.app.model.parseFragmentStrategy
import java.net.InetAddress
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * MARBLE_CENSORSHIP_LAB_V221 — a measurement, then a prescription.
 *
 * The product already knew a lot about censorship: `IranModeDetector` recognises an ISP and its
 * techniques, `DpiEvasionPolicy` heals MTU and budgets, `HighJitterShield` survives a lossy path.
 * What it did not have is the thing a user actually needs when a page will not open — *what is
 * blocking me right now, and what is the one switch that gets around it?*
 *
 * This file is that answer, split in the two halves the question has:
 *
 *  * [diagnose] is **pure**: it takes observations that were already collected (see
 *    [CensorshipProbe]) and names the techniques the evidence supports. No clocks, no sockets, no
 *    settings — a unit test can pin every rule.
 *  * [prescribe] is also pure: it maps a diagnosis onto the knobs the pinned cores actually read,
 *    and it refuses to spend a protection it does not need. A clean path prescribes nothing.
 *
 * The evidence model is deliberately the *path*, not the server: a probe that dies at TCP is a
 * different block from one that completes TCP and then dies inside the TLS handshake, and a
 * resolver that fails while the literal IP answers is a different block again. Those three
 * distinctions are the whole value of the lab, so they are the three the model keeps.
 */
object CensorshipLab {
    const val MARKER: String = "MARBLE_CENSORSHIP_LAB_V221"

    /**
     * MARBLE_PLAIN_VOICE_V246 — `ALT_PORT` was printed into the evidence line as a constant. The
     * three protocol probes keep their real names (a person asked about filtering knows DNS, TCP
     * and TLS); the fourth one is just "another port", and now says so.
     */
    enum class ProbeKind(val label: String) {
        DNS("DNS"),
        TCP("TCP"),
        TLS("TLS"),
        ALT_PORT("Other port")
    }

    enum class Technique(val label: String) {
        DNS_TAMPER("DNS interference"),
        SNI_FILTER("TLS name filtering"),
        TLS_RESET("Injected TLS reset"),
        IP_BLOCK("Address blocking"),
        PORT_BLOCK("Port filtering"),
        THROTTLING("Traffic shaping")
    }

    enum class Severity { INFO, WARNING, CRITICAL }

    /**
     * One measured fact about one target. [detail] is free text for the evidence line and never
     * participates in classification, so a translation can change it without changing a verdict.
     */
    data class Observation(
        val target: String,
        val kind: ProbeKind,
        val reachable: Boolean,
        val latencyMs: Int = 0,
        val tcpConnected: Boolean = reachable,
        val tlsCompleted: Boolean = reachable && kind == ProbeKind.TLS,
        val resetSuspected: Boolean = false,
        val detail: String = ""
    )

    data class Finding(
        val technique: Technique,
        val severity: Severity,
        val evidence: String
    )

    data class Diagnosis(
        val findings: List<Finding>,
        val observations: List<Observation>
    ) {
        val techniques: Set<Technique> get() = findings.map { it.technique }.toSet()

        val decisive: Boolean get() = findings.any { it.severity == Severity.CRITICAL }

        val clean: Boolean get() = findings.isEmpty()

        val severity: Severity
            get() = when {
                findings.any { it.severity == Severity.CRITICAL } -> Severity.CRITICAL
                findings.any { it.severity == Severity.WARNING } -> Severity.WARNING
                findings.isNotEmpty() -> Severity.INFO
                else -> Severity.INFO
            }

        val summary: String
            get() {
                if (clean) return "No interference was measured on this path."
                val names = findings
                    .sortedByDescending { it.severity.ordinal }
                    .map { it.technique.label }
                    .distinct()
                return names.joinToString(" • ")
            }
    }

    /**
     * The knobs a diagnosis asks for. A prescription is data, not an edit, so the UI can show it
     * before it is applied and so `applyTo` stays a single, auditable projection.
     */
    data class Prescription(
        val fragmentStrategy: FragmentStrategy = FragmentStrategy.AUTO,
        val muxPadding: Boolean = false,
        val tcpFastOpen: Boolean = true,
        val adaptiveBuffer: Boolean = true,
        val fakeIpDns: Boolean = true,
        val addressFamilyMode: AddressFamilyMode? = null,
        val requireExtendedCore: Boolean = false,
        val notes: List<String> = emptyList()
    ) {
        /** True when this prescription asks for a handshake treatment at all. */
        val touchesHandshake: Boolean get() = fragmentStrategy != FragmentStrategy.OFF

        /** True when nothing at all would change if it were applied. */
        val empty: Boolean
            get() = !touchesHandshake && !muxPadding && !requireExtendedCore &&
                addressFamilyMode == null && notes.isEmpty()

        fun applyTo(base: AppSettings): AppSettings {
            var next = base.copy(
                fragmentStrategy = fragmentStrategy.id,
                singBoxMuxPadding = muxPadding,
                tcpFastOpenEnabled = tcpFastOpen,
                adaptiveBufferEnabled = adaptiveBuffer,
                dnsFakeIpEnabled = fakeIpDns,
                adaptiveDnsEnabled = true
            )
            if (addressFamilyMode != null) next = next.copy(addressFamilyMode = addressFamilyMode)
            return next
        }
    }

    // ---------------------------------------------------------------------------------------
    // Diagnosis
    // ---------------------------------------------------------------------------------------

    fun diagnose(observations: List<Observation>): Diagnosis {
        val findings = mutableListOf<Finding>()
        val dns = observations.filter { it.kind == ProbeKind.DNS }
        val tls = observations.filter { it.kind == ProbeKind.TLS }
        val tcp = observations.filter { it.kind == ProbeKind.TCP }
        val alt = observations.filter { it.kind == ProbeKind.ALT_PORT }

        // 1. Names fail while addresses answer: the resolver is the thing being interfered with.
        if (dns.any { !it.reachable } && (tcp.any { it.reachable } || tls.any { it.reachable })) {
            findings += Finding(
                Technique.DNS_TAMPER,
                Severity.CRITICAL,
                "Resolver failed while a literal address on the same path answered."
            )
        } else if (dns.isNotEmpty() && dns.none { it.reachable }) {
            findings += Finding(
                Technique.DNS_TAMPER,
                Severity.WARNING,
                "Every resolver probe failed; the path may be offline rather than filtered."
            )
        }

        // 2. TCP lands, TLS does not: a middlebox is reading the handshake (name or certificate).
        val tcpLanded = tls.any { it.tcpConnected } || tcp.any { it.reachable }
        val tlsDied = tls.filter { it.tcpConnected && !it.tlsCompleted }
        if (tcpLanded && tlsDied.isNotEmpty()) {
            val reset = tlsDied.any { it.resetSuspected }
            findings += Finding(
                if (reset) Technique.TLS_RESET else Technique.SNI_FILTER,
                Severity.CRITICAL,
                if (reset) {
                    "TCP completed, then the handshake was reset before a certificate arrived."
                } else {
                    "TCP completed and the TLS handshake never finished."
                }
            )
        }

        // 3. A literal address that cannot be dialled at all is an address block, not a name block.
        val literal = tcp.filter { looksLikeLiteral(it.target) }
        if (literal.isNotEmpty() && literal.none { it.tcpConnected }) {
            findings += Finding(
                Technique.IP_BLOCK,
                Severity.CRITICAL,
                "No literal address answered on port 443."
            )
        }

        // 4. 443 answers while an alternate port does not: the port, not the host, is filtered.
        if (alt.isNotEmpty() && alt.all { !it.reachable } && tcp.any { it.reachable }) {
            findings += Finding(
                Technique.PORT_BLOCK,
                Severity.WARNING,
                "Port 443 answered while the alternate port did not."
            )
        }

        // 5. Everything answers, but slowly and unevenly: shaping rather than blocking.
        val answered = observations.filter { it.reachable && it.latencyMs > 0 }
        if (answered.size >= 2) {
            val slowest = answered.maxOf { it.latencyMs }
            val fastest = answered.minOf { it.latencyMs }
            if (slowest >= 600 || (fastest > 0 && slowest >= fastest * 4 && slowest >= 250)) {
                findings += Finding(
                    Technique.THROTTLING,
                    Severity.WARNING,
                    "Reachable but uneven: ${fastest} ms best against ${slowest} ms worst."
                )
            }
        }

        return Diagnosis(findings.sortedByDescending { it.severity.ordinal }, observations)
    }

    private fun looksLikeLiteral(target: String): Boolean {
        val host = target.substringBefore(':').trim().removePrefix("[").removeSuffix("]")
        if (host.count { it == '.' } == 3) {
            return host.split('.').all { part -> part.toIntOrNull()?.let { it in 0..255 } == true }
        }
        return host.contains(':') && host.any { it.isLetterOrDigit() }
    }

    // ---------------------------------------------------------------------------------------
    // Prescription
    // ---------------------------------------------------------------------------------------

    fun prescribe(diagnosis: Diagnosis, base: AppSettings): Prescription {
        if (diagnosis.clean) return Prescription(fragmentStrategy = FragmentStrategy.OFF, notes = emptyList())

        val techniques = diagnosis.techniques
        val notes = mutableListOf<String>()
        var fragment = FragmentStrategy.OFF
        var padding = false
        var fakeIp = true
        var family: AddressFamilyMode? = null
        var extended = false

        val handshakeBlocked = Technique.SNI_FILTER in techniques || Technique.TLS_RESET in techniques
        if (handshakeBlocked) {
            // A reset is a middlebox that has already decided; a half-open handshake is a filter
            // that may still be talked past. The heavier treatment belongs to the reset.
            fragment = if (Technique.TLS_RESET in techniques) {
                FragmentStrategy.FULL
            } else {
                FragmentStrategy.RECORD
            }
            padding = true
            extended = true
            notes += if (fragment == FragmentStrategy.FULL) {
                "Split the whole TLS handshake across TCP segments; the extended core is required."
            } else {
                "Split the ClientHello across TLS records; the extended core is required."
            }
        }

        if (Technique.DNS_TAMPER in techniques) {
            fakeIp = true
            notes += "Keep name resolution inside encrypted DNS with a fake-IP pool."
        }

        if (Technique.IP_BLOCK in techniques) {
            family = when (base.addressFamilyMode) {
                AddressFamilyMode.FORCE_IPV6, AddressFamilyMode.PREFER_IPV6 -> AddressFamilyMode.PREFER_IPV4
                AddressFamilyMode.FORCE_IPV4, AddressFamilyMode.PREFER_IPV4 -> AddressFamilyMode.PREFER_IPV6
                else -> AddressFamilyMode.SMART
            }
            notes += "Retry over the other address family: a literal address that never answered is " +
                "usually a route, not a name."
        }

        if (Technique.PORT_BLOCK in techniques) {
            notes += "Alternate ports are filtered; keep the node on 443 or a CDN-fronted port."
        }

        if (Technique.THROTTLING in techniques) {
            notes += "The path is shaped rather than blocked: larger buffers and multiplexing help " +
                "more than fragmentation."
        }

        // Never hand back a strategy the user disabled by hand: OFF is a deliberate instruction.
        if (parseFragmentStrategy(base.fragmentStrategy) == FragmentStrategy.OFF) {
            fragment = FragmentStrategy.OFF
            notes += "Fragmentation stays off because it is switched off in Settings."
        }

        return Prescription(
            fragmentStrategy = fragment,
            muxPadding = padding,
            fakeIpDns = fakeIp,
            addressFamilyMode = family,
            requireExtendedCore = extended,
            notes = notes
        )
    }
}

/**
 * The live half of the lab: a small, bounded probe set, run in parallel.
 *
 * Six targets is not a survey — it is the smallest set that can separate the four verdicts the
 * diagnosis knows. Each probe is one existing measurement primitive (`MultiVectorReachability`),
 * so the lab inherits the product's own timeouts, address-family discipline and reset detection
 * instead of inventing a second network stack.
 */
object CensorshipProbe {
    data class Target(val label: String, val host: String, val port: Int, val kind: CensorshipLab.ProbeKind)

    val TARGETS: List<Target> = listOf(
        Target("gstatic", "www.gstatic.com", 443, CensorshipLab.ProbeKind.TLS),
        Target("cloudflare", "cp.cloudflare.com", 443, CensorshipLab.ProbeKind.TLS),
        Target("cloudflare-ip", "1.1.1.1", 443, CensorshipLab.ProbeKind.TCP),
        Target("dns-alt-port", "1.1.1.1", 853, CensorshipLab.ProbeKind.ALT_PORT),
        Target("gstatic-direct", "www.gstatic.com", 80, CensorshipLab.ProbeKind.ALT_PORT)
    )

    fun scan(
        settings: AppSettings,
        timeoutMs: Int = 2_500,
        dnsLookup: (String, Int) -> Boolean = ::systemResolves
    ): CensorshipLab.Diagnosis {
        val budget = timeoutMs.coerceIn(500, 10_000)
        val observations = mutableListOf<CensorshipLab.Observation>()

        // One resolver question first: a name that will not resolve makes every TLS probe below a
        // DNS verdict, not a handshake verdict.
        val dnsOk = runCatching { dnsLookup("www.gstatic.com", budget) }.getOrDefault(false)
        observations += CensorshipLab.Observation(
            target = "www.gstatic.com:53",
            kind = CensorshipLab.ProbeKind.DNS,
            reachable = dnsOk,
            detail = if (dnsOk) "Resolver answered." else "Resolver did not answer."
        )

        val pool = Executors.newFixedThreadPool(4) { runnable ->
            Thread(runnable, "marble-censorship-probe").apply { isDaemon = true }
        }
        try {
            val tasks = TARGETS.map { target ->
                Callable {
                    runCatching { probe(target, settings, budget) }.getOrElse { error ->
                        CensorshipLab.Observation(
                            target = "${target.host}:${target.port}",
                            kind = target.kind,
                            reachable = false,
                            detail = error.message ?: error.javaClass.simpleName
                        )
                    }
                }
            }
            val futures = pool.invokeAll(tasks, (budget + 750).toLong(), TimeUnit.MILLISECONDS)
            futures.forEach { future ->
                if (future.isCancelled) return@forEach
                runCatching { future.get() }.getOrNull()?.let { observations += it }
            }
        } finally {
            pool.shutdownNow()
        }
        return CensorshipLab.diagnose(observations)
    }

    private fun probe(
        target: Target,
        settings: AppSettings,
        timeoutMs: Int
    ): CensorshipLab.Observation {
        val signal = MultiVectorReachability.probe(
            host = target.host,
            port = target.port,
            timeoutMs = timeoutMs,
            settings = settings
        )
        val latency = signal.timeToFirstByteMs?.toInt() ?: signal.elapsedMs.toInt()
        return CensorshipLab.Observation(
            target = "${target.host}:${target.port}",
            kind = target.kind,
            reachable = signal.reachable,
            latencyMs = latency.coerceAtLeast(0),
            tcpConnected = signal.tcpConnected,
            tlsCompleted = signal.tlsHandshakeCompleted,
            resetSuspected = signal.injectedResetSuspected,
            detail = signal.failureReason.ifBlank { signal.verdict.name.lowercase() }
        )
    }

    private fun systemResolves(host: String, timeoutMs: Int): Boolean {
        val pool = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "marble-censorship-dns").apply { isDaemon = true }
        }
        return try {
            val future = pool.submit(Callable { InetAddress.getAllByName(host).isNotEmpty() })
            future.get(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
            false
        } finally {
            pool.shutdownNow()
        }
    }
}
