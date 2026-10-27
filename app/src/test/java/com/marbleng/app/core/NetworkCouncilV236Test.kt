package com.marbleng.app.core

import com.marbleng.app.model.AddressFamilyMode
import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.ConnectionMode
import com.marbleng.app.model.FragmentStrategy
import com.marbleng.app.model.PowerProfile
import com.marbleng.app.model.RoutingMode
import com.marbleng.app.model.SplitTunnelMode
import com.marbleng.app.model.WorkloadProfile
import com.marbleng.app.model.parseFragmentStrategy
import com.marbleng.app.model.parsePowerProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_NETWORK_COUNCIL_V236 — every rule the twelve seats claim is pinned here.
 *
 * The council is pure precisely so this file can exist: no socket, no Android type, no clock. Each
 * test states the finding the product's docs promise and then proves the arithmetic behind it.
 */
class NetworkCouncilV236Test {

    private fun healthyEvidence(): NetworkCouncil.NetworkEvidence = NetworkCouncil.NetworkEvidence(
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
            NetworkCouncil.DnsEvidence("1.1.1.1", answered = true, latencyMs = 20, rcode = 0, answer = "104.16.132.229")
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

    private fun deadEvidence(): NetworkCouncil.NetworkEvidence = NetworkCouncil.NetworkEvidence(
        radio = NetworkCouncil.RadioEvidence(transport = NetworkCouncil.LinkTransport.NONE),
        latency = NetworkCouncil.LatencyEvidence(samplesMs = emptyList(), attempts = 6, failures = 6),
        dns = emptyList(),
        ports = emptyList(),
        path = NetworkCouncil.PathEvidence(),
        device = NetworkCouncil.DeviceEvidence(cores = 4, memoryMb = 3_000, batteryPercent = 50)
    )

    private fun change(plan: NetworkCouncil.Plan, field: NetworkCouncil.TuningField): NetworkCouncil.TuningChange? =
        plan.changes.firstOrNull { it.field == field }

    // ---------------------------------------------------------------------------------------
    // The constitution
    // ---------------------------------------------------------------------------------------

    @Test
    fun aCleanPathNeverSpendsTheHeavyHandshakeProtection() {
        val plan = NetworkCouncil.deliberate(healthyEvidence(), AppSettings())

        assertEquals(
            "a completed handshake keeps the automatic treatment",
            FragmentStrategy.AUTO.id,
            parseFragmentStrategy(plan.settings.fragmentStrategy).id
        )
        assertNull("no padding is written for a path that reset nothing", change(plan, NetworkCouncil.TuningField.MUX_PADDING))
        assertFalse(plan.critical)
    }

    @Test
    fun anInjectedResetBuysFullFragmentationAndPadding() {
        val evidence = healthyEvidence().copy(
            path = healthyEvidence().path.copy(tlsCompleted = true, injectedReset = true)
        )
        val plan = NetworkCouncil.deliberate(evidence, AppSettings())

        assertEquals(FragmentStrategy.FULL.id, parseFragmentStrategy(plan.settings.fragmentStrategy).id)
        assertEquals("true", change(plan, NetworkCouncil.TuningField.MUX_PADDING)?.value)
        assertEquals(
            NetworkCouncil.CouncilSeat.DPI,
            change(plan, NetworkCouncil.TuningField.FRAGMENT)?.seat
        )
        assertTrue(plan.critical)
        assertEquals(
            NetworkCouncil.CouncilSeverity.CRITICAL,
            plan.seatVerdicts.getValue(NetworkCouncil.CouncilSeat.DPI)
        )
    }

    @Test
    fun theOperatorTableMayAdviseButNeverOverrulesACompletedHandshake() {
        // MTN Irancell is in the offline registry with the harshest profile, and the handshake
        // completed cleanly. The measured probe must win the disagreement.
        val evidence = healthyEvidence().copy(
            radio = healthyEvidence().radio.copy(
                transport = NetworkCouncil.LinkTransport.CELLULAR,
                operatorName = "MTN Irancell",
                operatorCode = "43235",
                networkType = "LTE"
            )
        )
        val plan = NetworkCouncil.deliberate(evidence, AppSettings())

        assertEquals(
            FragmentStrategy.AUTO.id,
            parseFragmentStrategy(plan.settings.fragmentStrategy).id
        )
        // Resolve the raw proposals, not the change list: the winner here agrees with the shipped
        // value, so there is nothing to write — but the disagreement still has to be recorded.
        val winner = NetworkCouncil.resolve(plan.proposals)
            .first { it.field == NetworkCouncil.TuningField.FRAGMENT }
        assertEquals(NetworkCouncil.CouncilSeat.DPI, winner.seat)
        assertEquals(FragmentStrategy.AUTO.id, winner.value)
        assertTrue(
            "the losing seat is quoted, not deleted: ${NetworkCouncil.dissentFor(winner, plan.proposals)}",
            NetworkCouncil.dissentFor(winner, plan.proposals).contains("Operator & carrier")
        )
        assertEquals(
            NetworkCouncil.CouncilSeverity.WARNING,
            plan.seatVerdicts.getValue(NetworkCouncil.CouncilSeat.OPERATOR)
        )
    }

    @Test
    fun anInconclusiveHandshakeLetsTheOperatorPriorSpeakLouder() {
        val evidence = healthyEvidence().copy(
            radio = healthyEvidence().radio.copy(
                operatorName = "MTN Irancell",
                operatorCode = "43235",
                transport = NetworkCouncil.LinkTransport.CELLULAR
            ),
            path = healthyEvidence().path.copy(tlsCompleted = false, injectedReset = false)
        )
        val plan = NetworkCouncil.deliberate(evidence, AppSettings())

        // The measured seat still wins, but with the cheaper record-level treatment its own
        // evidence supports; the carrier prior stays a dissent line.
        assertEquals(
            FragmentStrategy.RECORD.id,
            parseFragmentStrategy(plan.settings.fragmentStrategy).id
        )
        assertEquals(
            NetworkCouncil.CouncilSeat.DPI,
            change(plan, NetworkCouncil.TuningField.FRAGMENT)?.seat
        )
    }

    @Test
    fun theCouncilNeverTouchesRoutingIdentityOrSplitTunnelling() {
        val base = AppSettings(
            routingMode = RoutingMode.GEO_DIRECT,
            splitTunnelMode = SplitTunnelMode.ONLY_SELECTED,
            splitTunnelPackages = "com.example.a",
            identityGuardEnabled = true,
            routeBlockAds = true,
            allowUnencryptedPublicOutbound = false,
            connectionMode = ConnectionMode.FULL_TUN
        )
        val plan = NetworkCouncil.deliberate(healthyEvidence(), base)

        assertEquals(base.routingMode, plan.settings.routingMode)
        assertEquals(base.splitTunnelMode, plan.settings.splitTunnelMode)
        assertEquals(base.splitTunnelPackages, plan.settings.splitTunnelPackages)
        assertEquals(base.identityGuardEnabled, plan.settings.identityGuardEnabled)
        assertEquals(base.routeBlockAds, plan.settings.routeBlockAds)
        assertEquals(base.allowUnencryptedPublicOutbound, plan.settings.allowUnencryptedPublicOutbound)
        assertEquals(base.connectionMode, plan.settings.connectionMode)
        assertEquals(base.socksPort, plan.settings.socksPort)
        assertEquals(base.localProxyPort, plan.settings.localProxyPort)
    }

    // ---------------------------------------------------------------------------------------
    // DNS
    // ---------------------------------------------------------------------------------------

    @Test
    fun aBlockPageMovesTheResolverAndRaisesTheHijackGuard() {
        val evidence = healthyEvidence().copy(
            dns = listOf(
                NetworkCouncil.DnsEvidence("1.1.1.1", answered = true, latencyMs = 12, rcode = 0, answer = "10.10.34.36", blockPage = true),
                NetworkCouncil.DnsEvidence("8.8.8.8", answered = true, latencyMs = 24, rcode = 0, answer = "104.16.132.229"),
                NetworkCouncil.DnsEvidence("9.9.9.9", answered = true, latencyMs = 30, rcode = 0, answer = "104.16.133.229")
            )
        )
        val plan = NetworkCouncil.deliberate(evidence, AppSettings())

        assertEquals("8.8.8.8", plan.settings.dnsPrimaryIp)
        assertEquals("9.9.9.9", plan.settings.dnsSecondaryIp)
        assertEquals("https://8.8.8.8/dns-query", plan.settings.dnsPrimaryDoH)
        assertTrue(plan.settings.dnsHijackEnabled)
        assertTrue(plan.settings.dnsFakeIpEnabled)
        assertEquals(
            NetworkCouncil.CouncilSeverity.CRITICAL,
            plan.seatVerdicts.getValue(NetworkCouncil.CouncilSeat.DNS)
        )
    }

    @Test
    fun anHonestResolverIsKeptAndItsEncryptionFollowsIt() {
        val plan = NetworkCouncil.deliberate(healthyEvidence(), AppSettings())

        assertEquals("1.1.1.1", plan.settings.dnsPrimaryIp)
        assertEquals("https://1.1.1.1/dns-query", plan.settings.dnsPrimaryDoH)
        assertFalse("a clean resolver is not a reason to rewrite the hijack guard", change(plan, NetworkCouncil.TuningField.DNS_HIJACK) != null)
    }

    // ---------------------------------------------------------------------------------------
    // Families, MTU, loss, UDP
    // ---------------------------------------------------------------------------------------

    @Test
    fun aDeadIpv6PrefersIpv4WithoutEverDisablingIt() {
        val evidence = healthyEvidence().copy(
            path = healthyEvidence().path.copy(ipv4Ok = true, ipv6Ok = false)
        )
        val plan = NetworkCouncil.deliberate(evidence, AppSettings())

        assertEquals(AddressFamilyMode.PREFER_IPV4, plan.settings.addressFamilyMode)
        assertTrue("turning IPv6 off is how leaks begin", plan.settings.ipv6Enabled)
        assertEquals("UseIPv4", plan.settings.dnsQueryStrategy)
        assertEquals(120, plan.settings.happyEyeballsTryDelayMs)
    }

    @Test
    fun bothFamiliesHealthyKeepsTheSmartRaceAndTheDefaultDelay() {
        val plan = NetworkCouncil.deliberate(healthyEvidence(), AppSettings())

        assertEquals(AddressFamilyMode.SMART, plan.settings.addressFamilyMode)
        assertEquals(60, plan.settings.happyEyeballsTryDelayMs)
    }

    @Test
    fun aMeasuredPathLeavesSixtyBytesOfHeadroomForTheTunnel() {
        val plan = NetworkCouncil.deliberate(healthyEvidence(), AppSettings())

        assertEquals(1_412, plan.settings.tun2SocksMtu)
        assertEquals(NetworkCouncil.CouncilSeat.MTU, change(plan, NetworkCouncil.TuningField.TUN_MTU)?.seat)
    }

    @Test
    fun anUnmeasuredPathOnWifiKeepsTheAutomaticTunnelMtu() {
        val evidence = healthyEvidence().copy(
            path = healthyEvidence().path.copy(pathMtu = 0, mtuConfidence = 0.0)
        )
        val plan = NetworkCouncil.deliberate(evidence, AppSettings())

        assertEquals(0, plan.settings.tun2SocksMtu)
    }

    @Test
    fun aLossyPathWidensTheFallbacksAndReclaimsUdpMappingsSooner() {
        val evidence = healthyEvidence().copy(
            latency = NetworkCouncil.LatencyEvidence(
                samplesMs = listOf(100, 130, 160),
                attempts = 4,
                failures = 1
            )
        )
        val plan = NetworkCouncil.deliberate(evidence, AppSettings())

        assertEquals(25.0, plan.metrics.lossPercent, 0.001)
        assertEquals(45, plan.settings.tun2SocksUdpTimeoutSec)
        assertEquals(4, plan.settings.fallbackCount)
        assertEquals(
            NetworkCouncil.CouncilSeverity.CRITICAL,
            plan.seatVerdicts.getValue(NetworkCouncil.CouncilSeat.LOSS)
        )
    }

    @Test
    fun noUdpAtAllStopsSpendingBudgetOnUdpProbes() {
        val evidence = healthyEvidence().copy(
            path = healthyEvidence().path.copy(quicOk = false, udpDnsOk = false)
        )
        val plan = NetworkCouncil.deliberate(evidence, AppSettings())

        assertFalse(plan.settings.udpProbeEnabled)
        assertEquals("reject", plan.settings.muxUdp443)
        assertEquals(8, plan.settings.muxXudpConcurrency)
    }

    @Test
    fun aFiltered443IsCriticalEvenWhenOtherPortsAnswer() {
        val evidence = healthyEvidence().copy(
            ports = listOf(
                NetworkCouncil.PortEvidence("1.1.1.1", 443, NetworkCouncil.PortProtocol.TCP, open = false),
                NetworkCouncil.PortEvidence("1.1.1.1", 80, NetworkCouncil.PortProtocol.TCP, open = true, latencyMs = 40)
            )
        )
        val plan = NetworkCouncil.deliberate(evidence, AppSettings())

        assertEquals(
            NetworkCouncil.CouncilSeverity.CRITICAL,
            plan.seatVerdicts.getValue(NetworkCouncil.CouncilSeat.PORTS)
        )
        assertFalse("fast open without a working 443 is a liability", plan.settings.tcpFastOpenEnabled)
    }

    // ---------------------------------------------------------------------------------------
    // The cores and the device
    // ---------------------------------------------------------------------------------------

    @Test
    fun aFastSteadyPathOnAStrongPhoneAsksForTheExtremeProfile() {
        val plan = NetworkCouncil.deliberate(healthyEvidence(), AppSettings())

        assertEquals(PowerProfile.EXTREME, parsePowerProfile(plan.settings.powerProfile))
        assertEquals(WorkloadProfile.STREAMING, plan.settings.workloadProfile)
        assertEquals(512, plan.settings.xrayPolicyBufferSizeKb)
        assertEquals(24, plan.settings.pingConcurrency)
    }

    @Test
    fun aThrottledDeviceIsGivenTheEfficientProfileInsteadOfSpeed() {
        val evidence = healthyEvidence().copy(
            device = healthyEvidence().device.copy(thermalStatus = 3)
        )
        val plan = NetworkCouncil.deliberate(evidence, AppSettings())

        assertEquals(PowerProfile.EFFICIENT, parsePowerProfile(plan.settings.powerProfile))
        assertEquals(
            NetworkCouncil.CouncilSeverity.WARNING,
            plan.seatVerdicts.getValue(NetworkCouncil.CouncilSeat.CORE)
        )
    }

    @Test
    fun muxIsNeverEnabledOnARouteThatCannotCarryIt() {
        val evidence = healthyEvidence().copy(
            latency = NetworkCouncil.LatencyEvidence(samplesMs = listOf(200, 220, 240), attempts = 3)
        )
        val unsafe = NetworkCouncil.deliberate(evidence, AppSettings(), muxSafe = false)
        val safe = NetworkCouncil.deliberate(evidence, AppSettings(), muxSafe = true)

        assertFalse(unsafe.settings.muxEnabled)
        assertEquals(
            "false",
            unsafe.proposals.first { it.field == NetworkCouncil.TuningField.MUX }.value
        )
        assertFalse(safe.settings.muxEnabled.not())
        assertEquals(
            "true",
            safe.proposals.first { it.field == NetworkCouncil.TuningField.MUX }.value
        )
    }

    // ---------------------------------------------------------------------------------------
    // Mechanics, bounds and the score
    // ---------------------------------------------------------------------------------------

    @Test
    fun theHighestMeasuredConfidenceWinsAndTheLoserIsQuoted() {
        val proposals = listOf(
            NetworkCouncil.Proposal(
                field = NetworkCouncil.TuningField.FRAGMENT,
                value = FragmentStrategy.RECORD.id,
                seat = NetworkCouncil.CouncilSeat.OPERATOR,
                confidence = 58,
                reason = "carrier prior"
            ),
            NetworkCouncil.Proposal(
                field = NetworkCouncil.TuningField.FRAGMENT,
                value = FragmentStrategy.FULL.id,
                seat = NetworkCouncil.CouncilSeat.DPI,
                confidence = 96,
                reason = "measured reset"
            )
        )
        val resolved = NetworkCouncil.resolve(proposals)

        assertEquals(1, resolved.size)
        assertEquals(FragmentStrategy.FULL.id, resolved.first().value)
        assertEquals(NetworkCouncil.CouncilSeat.DPI, resolved.first().seat)
        assertTrue(
            NetworkCouncil.dissentFor(resolved.first(), proposals)
                .contains("Operator & carrier wanted record")
        )
    }

    @Test
    fun everyAppliedValueIsBoundedToWhatTheCoresAccept() {
        val base = AppSettings(
            mtuMin = 1_500,
            mtuMax = 1_280,
            tun2SocksMtu = 9_000,
            pingTimeoutSec = 99,
            pingSamples = 99,
            pingConcurrency = 999,
            muxConcurrency = 999,
            muxXudpConcurrency = 9_999,
            xrayPolicyBufferSizeKb = 9_999,
            happyEyeballsTryDelayMs = 5_000,
            dnsQueryStrategy = "nonsense"
        )
        val normal = NetworkCouncil.normalize(base)

        assertEquals(1_280, normal.mtuMin)
        assertEquals(1_500, normal.mtuMax)
        assertEquals(1_500, normal.tun2SocksMtu)
        assertEquals(30, normal.pingTimeoutSec)
        assertEquals(10, normal.pingSamples)
        assertEquals(64, normal.pingConcurrency)
        assertEquals(128, normal.muxConcurrency)
        assertEquals(1_024, normal.muxXudpConcurrency)
        assertEquals(1_024, normal.xrayPolicyBufferSizeKb)
        assertEquals(400, normal.happyEyeballsTryDelayMs)
        assertEquals("UseIP", normal.dnsQueryStrategy)
    }

    @Test
    fun anUnmeasuredLossIsNotScoredAsACleanPath() {
        val plan = NetworkCouncil.deliberate(deadEvidence(), AppSettings())

        assertEquals("D", plan.metrics.grade)
        assertTrue(plan.metrics.score < 55)
    }

    @Test
    fun aHealthyPathScoresAnAAndSaysSoPerSeat() {
        val plan = NetworkCouncil.deliberate(healthyEvidence(), AppSettings())

        assertTrue("score was ${plan.metrics.score}", plan.metrics.score >= 85)
        assertEquals("A", plan.metrics.grade)
        assertEquals(NetworkCouncil.CouncilSeat.entries.size, plan.seatVerdicts.size)
        assertEquals(
            NetworkCouncil.CouncilSeverity.GOOD,
            plan.seatVerdicts.getValue(NetworkCouncil.CouncilSeat.LATENCY)
        )
        assertEquals(
            NetworkCouncil.CouncilSeverity.GOOD,
            plan.seatVerdicts.getValue(NetworkCouncil.CouncilSeat.DNS)
        )
    }

    @Test
    fun readAgreesWithTheValueTheSeatWrites() {
        val settings = AppSettings()
        NetworkCouncil.TuningField.entries.forEach { field ->
            val current = NetworkCouncil.read(settings, field)
            assertTrue("$field has no readable value", current.isNotBlank())
        }
        assertEquals("balanced", NetworkCouncil.read(settings, NetworkCouncil.TuningField.POWER_PROFILE))
        assertEquals("auto", NetworkCouncil.read(settings, NetworkCouncil.TuningField.FRAGMENT))
        assertEquals("1280", NetworkCouncil.read(settings, NetworkCouncil.TuningField.MTU_MIN))
    }

    // ---------------------------------------------------------------------------------------
    // The saved verdict
    // ---------------------------------------------------------------------------------------

    @Test
    fun theSavedVerdictRoundTripsThroughItsJsonForm() {
        val plan = NetworkCouncil.deliberate(healthyEvidence(), AppSettings())
        val report = CouncilReport(
            atMs = 1_760_000_000_000L,
            transport = NetworkCouncil.LinkTransport.WIFI,
            operatorName = "Marbelnet Fiber",
            networkType = "Wi-Fi 5 GHz",
            score = plan.metrics.score,
            grade = plan.metrics.grade,
            applied = true,
            findings = plan.findings,
            changes = plan.changes
        )

        val restored = CouncilReport.fromJson(report.toJson())
        assertNotNull(restored)
        assertEquals(report.atMs, restored!!.atMs)
        assertEquals(report.score, restored.score)
        assertEquals(report.grade, restored.grade)
        assertEquals(report.findings.size, restored.findings.size)
        assertEquals(report.changes.size, restored.changes.size)
        assertEquals(report.changes.first().field, restored.changes.first().field)
        assertEquals(report.changes.first().value, restored.changes.first().value)
        assertTrue(restored.summary.contains("Score"))
    }

    @Test
    fun aForeignOrBrokenDocumentIsRefusedNotGuessed() {
        assertNull(CouncilReport.fromJson(""))
        assertNull(CouncilReport.fromJson("not json at all"))
        assertNull(CouncilReport.fromJson("{\"format\":\"something-else\",\"version\":1}"))
        assertNull(CouncilReport.fromJson("{\"format\":\"marbleng-network-council\",\"version\":9}"))
    }
}
