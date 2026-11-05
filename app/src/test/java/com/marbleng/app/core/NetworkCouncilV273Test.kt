package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.FragmentStrategy
import com.marbleng.app.model.PowerProfile
import com.marbleng.app.model.WorkloadProfile
import com.marbleng.app.model.parseFragmentStrategy
import com.marbleng.app.model.parsePowerProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_FILTER_LAB_V273 — the filtration lab, on the host JVM.
 *
 * The report called the twelve checks weak, and this is the part of that report the tests answer
 * directly: before V273 the DPI seat reasoned about one boolean (`sniSuspicion`) that the scanner
 * never set, so "DPI & censorship" could only ever say that the one handshake it could not see had
 * completed. Every rule below is about *evidence the product now actually collects*: a TLS matrix
 * across open and routinely-filtered names, a resolver cross-check over named records, and a plain
 * page fetch that catches a policy answer.
 */
class NetworkCouncilV273Test {

    // ---------------------------------------------------------------------------------------
    // A healthy baseline: the same shape `NetworkCouncilV236Test` uses, with no lab evidence.
    // ---------------------------------------------------------------------------------------

    private fun base(): NetworkCouncil.NetworkEvidence = NetworkCouncil.NetworkEvidence(
        radio = NetworkCouncil.RadioEvidence(
            transport = NetworkCouncil.LinkTransport.WIFI,
            operatorName = "Marbelnet Fiber",
            networkType = "Wi-Fi 5 GHz",
            signalLevel = 3,
            linkDownKbps = 100_000,
            linkUpKbps = 20_000,
            validated = true,
            interfaceMtu = 1_500
        ),
        latency = NetworkCouncil.LatencyEvidence(
            samplesMs = listOf(38, 40, 42),
            attempts = 3,
            failures = 0,
            targets = "1.1.1.1"
        ),
        dns = listOf(
            NetworkCouncil.DnsEvidence(
                "1.1.1.1",
                answered = true,
                latencyMs = 20,
                rcode = 0,
                answer = "104.16.132.229"
            )
        ),
        ports = listOf(
            NetworkCouncil.PortEvidence("1.1.1.1", 443, NetworkCouncil.PortProtocol.TCP, open = true, latencyMs = 40),
            NetworkCouncil.PortEvidence("1.1.1.1", 80, NetworkCouncil.PortProtocol.TCP, open = true, latencyMs = 42)
        ),
        path = NetworkCouncil.PathEvidence(
            ipv4Ok = true,
            ipv4Ms = 40,
            ipv6Ok = true,
            ipv6Ms = 44,
            tlsCompleted = true,
            quicOk = true,
            udpDnsOk = true,
            pathMtu = 1_472,
            mtuConfidence = 0.9
        ),
        device = NetworkCouncil.DeviceEvidence(
            cores = 8,
            memoryMb = 8_000,
            batteryPercent = 80,
            charging = false,
            thermalStatus = 0
        ),
        throughputDownKbps = 100_000
    )

    private fun tls(
        host: String,
        outcome: NetworkCouncil.TlsOutcome,
        filtered: Boolean,
        latencyMs: Int = 60
    ) = NetworkCouncil.TlsProbe(
        host = host,
        sni = host,
        outcome = outcome,
        latencyMs = latencyMs,
        certificateOk = outcome == NetworkCouncil.TlsOutcome.COMPLETED,
        routinelyFiltered = filtered
    )

    /** The fingerprint of a filtered path: the open names answer and the flagged ones are reset. */
    private fun filteredLab(): NetworkCouncil.FilterEvidence = NetworkCouncil.FilterEvidence(
        tls = listOf(
            tls("www.gstatic.com", NetworkCouncil.TlsOutcome.COMPLETED, filtered = false),
            tls("cloudflare.com", NetworkCouncil.TlsOutcome.COMPLETED, filtered = false),
            tls("github.com", NetworkCouncil.TlsOutcome.COMPLETED, filtered = false),
            tls("telegram.org", NetworkCouncil.TlsOutcome.RESET, filtered = true, latencyMs = 12),
            tls("www.youtube.com", NetworkCouncil.TlsOutcome.RESET, filtered = true, latencyMs = 10),
            tls("x.com", NetworkCouncil.TlsOutcome.TIMEOUT, filtered = true, latencyMs = 2_000)
        )
    )

    /** A clean path: every name answered, including the ones a filter would have taken. */
    private fun cleanLab(): NetworkCouncil.FilterEvidence = NetworkCouncil.FilterEvidence(
        tls = listOf(
            tls("www.gstatic.com", NetworkCouncil.TlsOutcome.COMPLETED, filtered = false),
            tls("cloudflare.com", NetworkCouncil.TlsOutcome.COMPLETED, filtered = false),
            tls("github.com", NetworkCouncil.TlsOutcome.COMPLETED, filtered = false),
            tls("telegram.org", NetworkCouncil.TlsOutcome.COMPLETED, filtered = true),
            tls("www.youtube.com", NetworkCouncil.TlsOutcome.COMPLETED, filtered = true),
            tls("x.com", NetworkCouncil.TlsOutcome.COMPLETED, filtered = true)
        )
    )

    private fun finding(
        plan: NetworkCouncil.Plan,
        seat: NetworkCouncil.CouncilSeat,
        severity: NetworkCouncil.CouncilSeverity
    ) = plan.findings.firstOrNull { it.seat == seat && it.severity == severity }

    private fun fragmentOf(plan: NetworkCouncil.Plan) =
        plan.proposals.first { it.field == NetworkCouncil.TuningField.FRAGMENT }.value

    // ---------------------------------------------------------------------------------------
    // The verdict, which is the lab's own arithmetic
    // ---------------------------------------------------------------------------------------

    @Test
    fun `an unmeasured lab never claims the path is clean`() {
        val empty = NetworkCouncil.FilterEvidence()
        assertFalse(empty.measured)
        assertEquals(NetworkCouncil.FilterVerdict.UNMEASURED, empty.verdict)
        // The council's own summary must agree with the object it summarises.
        assertEquals(
            NetworkCouncil.FilterVerdict.UNMEASURED,
            NetworkCouncil.measure(base()).filterVerdict
        )
    }

    @Test
    fun `a filtered path is filtered, and a clean one is not`() {
        assertEquals(NetworkCouncil.FilterVerdict.FILTERED, filteredLab().verdict)
        assertEquals(NetworkCouncil.FilterVerdict.CLEAN, cleanLab().verdict)
        assertTrue(filteredLab().sniFiltering)
        assertFalse(cleanLab().sniFiltering)
        // The names a filter acted on are named, not counted: that is what the page prints.
        assertEquals(
            listOf("telegram.org", "www.youtube.com", "x.com"),
            filteredLab().filteredHosts
        )
    }

    @Test
    fun `a dead link is unreachable, not censored`() {
        val dead = NetworkCouncil.FilterEvidence(
            tls = listOf(
                tls("www.gstatic.com", NetworkCouncil.TlsOutcome.TIMEOUT, filtered = false),
                tls("cloudflare.com", NetworkCouncil.TlsOutcome.TIMEOUT, filtered = false),
                tls("telegram.org", NetworkCouncil.TlsOutcome.TIMEOUT, filtered = true)
            )
        )
        assertEquals(NetworkCouncil.FilterVerdict.UNREACHABLE, dead.verdict)
        assertFalse("silence everywhere is not SNI filtering", dead.sniFiltering)
        // …and the council must not spend the heavy treatment on it.
        val plan = NetworkCouncil.deliberate(base().copy(filter = dead), AppSettings())
        assertNotEquals(FragmentStrategy.FULL.id, fragmentOf(plan))
    }

    @Test
    fun `partial failure is a suspicion, and a policy page is proof`() {
        val partial = NetworkCouncil.FilterEvidence(
            tls = cleanLab().tls.dropLast(1) + tls("x.com", NetworkCouncil.TlsOutcome.HANDSHAKE_FAILED, true)
        )
        assertEquals(NetworkCouncil.FilterVerdict.SUSPECT, partial.verdict)

        val page = NetworkCouncil.FilterEvidence(
            tls = cleanLab().tls,
            blockPages = listOf(
                NetworkCouncil.BlockPageProbe(
                    host = "www.youtube.com",
                    statusCode = 451,
                    bodyMarker = "internet.ir",
                    intercepted = true
                )
            )
        )
        assertEquals(NetworkCouncil.FilterVerdict.FILTERED, page.verdict)
    }

    // ---------------------------------------------------------------------------------------
    // The seats
    // ---------------------------------------------------------------------------------------

    @Test
    fun `a measured filtration raises the DPI seat and spends the full fragment`() {
        val plan = NetworkCouncil.deliberate(base().copy(filter = filteredLab()), AppSettings())

        assertEquals(
            NetworkCouncil.CouncilSeverity.CRITICAL,
            plan.seatVerdicts.getValue(NetworkCouncil.CouncilSeat.DPI)
        )
        assertEquals(FragmentStrategy.FULL.id, fragmentOf(plan))
        assertTrue(
            "the reset fingerprints are named as a finding of their own",
            plan.findings.any {
                it.seat == NetworkCouncil.CouncilSeat.DPI && it.headline.contains("were reset")
            }
        )
        // Mux padding is spent for the same reason the fragment is: the middlebox was observed.
        assertTrue(
            plan.proposals.any {
                it.field == NetworkCouncil.TuningField.MUX_PADDING &&
                    it.seat == NetworkCouncil.CouncilSeat.DPI &&
                    it.value == "true"
            }
        )
        // …and the cores are not asked to trust a filtered path with a multiplexer.
        assertEquals(WorkloadProfile.STEALTH, plan.settings.workloadProfile)
        assertEquals(
            "false",
            plan.proposals.first { it.field == NetworkCouncil.TuningField.MUX }.value
        )
    }

    @Test
    fun `a clean lab keeps the automatic treatment and the fast profile`() {
        val plan = NetworkCouncil.deliberate(base().copy(filter = cleanLab()), AppSettings())

        assertEquals(
            NetworkCouncil.CouncilSeverity.GOOD,
            plan.seatVerdicts.getValue(NetworkCouncil.CouncilSeat.DPI)
        )
        assertEquals(FragmentStrategy.AUTO.id, fragmentOf(plan))
        assertEquals(WorkloadProfile.STREAMING, plan.settings.workloadProfile)
        assertTrue("a clean lab keeps a healthy score", plan.metrics.score >= 85)
        assertEquals(
            "nothing was intercepted, so the whole matrix completed",
            plan.metrics.tlsProbed,
            plan.metrics.tlsCompleted
        )
        assertTrue(
            plan.findings.any {
                it.seat == NetworkCouncil.CouncilSeat.DPI &&
                    it.headline == "The TLS handshake completed cleanly"
            }
        )
    }

    @Test
    fun `a poisoned name is critical on the DNS seat and names the resolver that did it`() {
        val lab = NetworkCouncil.FilterEvidence(
            tls = cleanLab().tls,
            dnsNames = listOf(
                NetworkCouncil.DnsNameCheck(
                    name = "telegram.org",
                    firstServer = "10.10.34.36",
                    firstAnswer = "10.10.34.36",
                    secondServer = "1.1.1.1",
                    secondAnswer = "149.154.167.99",
                    blockPageServer = "10.10.34.36",
                    answeredServers = 2,
                    verdict = NetworkCouncil.DnsVerdict.BLOCKED
                )
            )
        )
        val plan = NetworkCouncil.deliberate(base().copy(filter = lab), AppSettings())

        assertEquals(
            NetworkCouncil.CouncilSeverity.CRITICAL,
            plan.seatVerdicts.getValue(NetworkCouncil.CouncilSeat.DNS)
        )
        val finding = finding(plan, NetworkCouncil.CouncilSeat.DNS, NetworkCouncil.CouncilSeverity.CRITICAL)
        assertTrue(finding != null && finding.detail.contains("telegram.org"))
        assertTrue(finding!!.detail.contains("10.10.34.36"))
        assertTrue(
            plan.proposals.any {
                it.field == NetworkCouncil.TuningField.DNS_HIJACK &&
                    it.seat == NetworkCouncil.CouncilSeat.DNS
            }
        )
    }

    @Test
    fun `two honest answers to one name are a suspicion, not a hijack`() {
        val lab = NetworkCouncil.FilterEvidence(
            tls = cleanLab().tls,
            dnsNames = listOf(
                NetworkCouncil.DnsNameCheck(
                    name = "www.youtube.com",
                    firstServer = "1.1.1.1",
                    firstAnswer = "142.250.185.78",
                    secondServer = "8.8.8.8",
                    secondAnswer = "142.250.186.14",
                    answeredServers = 2,
                    verdict = NetworkCouncil.DnsVerdict.DIFFERENT
                )
            )
        )
        val plan = NetworkCouncil.deliberate(base().copy(filter = lab), AppSettings())

        assertEquals(
            "a CDN is a warning, never a critical",
            NetworkCouncil.CouncilSeverity.WARNING,
            plan.seatVerdicts.getValue(NetworkCouncil.CouncilSeat.DNS)
        )
        assertTrue(
            plan.findings.any {
                it.seat == NetworkCouncil.CouncilSeat.DNS &&
                    it.headline.contains("resolved differently")
            }
        )
        // Two honest answers to one name are a CDN, not a hijack: no guard is spent on them.
        assertTrue(
            plan.proposals.none { it.field == NetworkCouncil.TuningField.DNS_HIJACK }
        )
    }

    // ---------------------------------------------------------------------------------------
    // The score, and the promises the older tests made about it
    // ---------------------------------------------------------------------------------------

    @Test
    fun `the DPI share follows the measured completion rate, and a scan without the lab keeps the V236 arithmetic`() {
        val withoutLab = NetworkCouncil.measure(base())
        val clean = NetworkCouncil.measure(base().copy(filter = cleanLab()))
        val filtered = NetworkCouncil.measure(base().copy(filter = filteredLab()))
        val dead = NetworkCouncil.measure(
            base().copy(
                filter = NetworkCouncil.FilterEvidence(
                    tls = listOf(
                        tls("www.gstatic.com", NetworkCouncil.TlsOutcome.COMPLETED, filtered = false),
                        tls("cloudflare.com", NetworkCouncil.TlsOutcome.RESET, filtered = false),
                        tls("github.com", NetworkCouncil.TlsOutcome.RESET, filtered = false),
                        tls("telegram.org", NetworkCouncil.TlsOutcome.RESET, filtered = true)
                    )
                )
            )
        )

        assertEquals("no lab evidence changes nothing", withoutLab.score, clean.score)
        assertTrue("a filtered path scores lower than a clean one", filtered.score < clean.score)
        assertTrue("a half-dead matrix scores lower still", dead.score < filtered.score)
        assertEquals(NetworkCouncil.FilterVerdict.UNMEASURED, withoutLab.filterVerdict)
        assertEquals(6, filtered.tlsProbed)
        assertEquals(3, filtered.tlsCompleted)
        assertEquals(
            listOf("telegram.org", "www.youtube.com", "x.com"),
            filtered.filteredHosts
        )
    }

    @Test
    fun `a scan that measured the lab reports its verdict and never the old silence`() {
        val clean = NetworkCouncil.measure(base().copy(filter = cleanLab()))
        assertEquals(NetworkCouncil.FilterVerdict.CLEAN, clean.filterVerdict)
        assertEquals(6, clean.tlsProbed)
        assertEquals(6, clean.tlsCompleted)
        assertEquals(0, clean.dnsPoisoned)
        assertEquals(0, clean.blockPages)

        val tapped = NetworkCouncil.measure(
            base().copy(
                filter = cleanLab().copy(
                    blockPages = listOf(
                        NetworkCouncil.BlockPageProbe("www.youtube.com", 403, intercepted = true)
                    )
                )
            )
        )
        assertEquals(NetworkCouncil.FilterVerdict.FILTERED, tapped.filterVerdict)
        assertEquals(1, tapped.blockPages)
        assertEquals(1, tapped.filteredHosts.size)
    }

    @Test
    fun `a filtered path changes the tunnel treatment, not the device power profile`() {
        val plan = NetworkCouncil.deliberate(
            base().copy(filter = filteredLab()),
            AppSettings(powerProfile = PowerProfile.EXTREME.id)
        )
        // The response to censorship is the workload and the fragment…
        assertEquals(WorkloadProfile.STEALTH, plan.settings.workloadProfile)
        assertEquals(FragmentStrategy.FULL, parseFragmentStrategy(plan.settings.fragmentStrategy))
        // …while the power profile stays a projection of the device and the path, which is what
        // keeps the council's constitution: no seat spends a protection it does not need.
        assertEquals(PowerProfile.EXTREME, parsePowerProfile(plan.settings.powerProfile))
    }
}
