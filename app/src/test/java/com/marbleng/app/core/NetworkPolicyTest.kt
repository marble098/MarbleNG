package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.marbleng.app.model.AppSettings
import java.net.InetAddress

class NetworkPolicyTest {
    @Test
    fun dataStallRequiresIndependentConfirmation() {
        val guard = DataStallGuard(
            confirmationWindowMs = 10_000,
            confirmationSignals = 2,
            cooldownMs = 30_000
        )
        assertEquals(DataStallGuard.Decision.PROBE, guard.onSignal(1_000, false))
        assertEquals(DataStallGuard.Decision.CONFIRM, guard.onSignal(2_000, false))
        assertEquals(DataStallGuard.Decision.IGNORE_COOLDOWN, guard.onSignal(3_000, false))
        assertEquals(
            DataStallGuard.Decision.IGNORE_RECENT_TRAFFIC,
            guard.onSignal(4_000, true)
        )
    }

    @Test
    fun cellularAndDatagramMtuNeverExceedPhysicalCeiling() {
        val value = AdaptiveMtuPolicy.recommend(
            AdaptiveMtuPolicy.Input(
                physicalMtu = 1500,
                configuredMin = 1280,
                configuredMax = 1500,
                networkTransport = "cellular",
                proxyScheme = "hysteria2",
                proxyTransport = "udp"
            )
        )
        assertEquals(1380, value.mtu)
        assertEquals(1500, value.ceiling)
        assertTrue("cellular-headroom" in value.reason)
        assertTrue("udp-tunnel-overhead" in value.reason)
    }

    @Test
    fun rankTargetOrderIsEqualForEveryNodeInOneEpoch() {
        val targets = listOf("gstatic", "cloudflare", "google")
        val first = RankTargetScheduler.ordered(targets, "wifi|v4|v6", nowMs = 1_000)
        val second = RankTargetScheduler.ordered(targets, "wifi|v4|v6", nowMs = 2_000)
        assertEquals(first, second)
        assertEquals(targets.toSet(), first.toSet())
    }

    @Test
    fun rankTargetOrderRotatesAcrossEpochs() {
        val targets = listOf("a", "b", "c", "d")
        val a = RankTargetScheduler.ordered(targets, "cellular", nowMs = 0, epochMs = 100)
        val b = RankTargetScheduler.ordered(targets, "cellular", nowMs = 100, epochMs = 100)
        assertNotEquals(a, b)
    }

    @Test
    fun chainedSocketPolicyIsMorePatient() {
        val direct = SocketLivenessPolicy.forTransport("xhttp", chained = false)
        val chain = SocketLivenessPolicy.forTransport("xhttp", chained = true)
        assertTrue(chain.userTimeoutMs > direct.userTimeoutMs)
        assertTrue(chain.keepAliveIdleSeconds > direct.keepAliveIdleSeconds)
    }

    @Test
    fun dnsHedgePlanUsesReliableResolverAndBoundedDelay() {
        val plan = requireNotNull(
            DnsHedgePolicy.plan(
                listOf(
                    DnsHedgePolicy.Resolver("slow-perfect", 120, 500, 100),
                    DnsHedgePolicy.Resolver("fast-flaky", 30, 80, 80),
                    DnsHedgePolicy.Resolver("backup", 90, 180, 99)
                )
            )
        )
        assertEquals("slow-perfect", plan.primaryId)
        assertEquals("backup", plan.secondaryId)
        assertTrue(plan.hedgeDelayMs in 150..350)
        assertNull(DnsHedgePolicy.plan(emptyList()))
    }

    @Test
    fun identityGuardBlocksMakeBeforeBreak() {
        val handover = HandoverCoordinator()
        assertEquals(
            HandoverCoordinator.Action.NONE,
            handover.begin("route-a", "route-b", identityGuard = true)
        )
        assertEquals(HandoverCoordinator.State.IDLE, handover.snapshot().state)
    }

    @Test
    fun disablingIpv6BlocksTheWholeFamilyEndToEnd() {
        val plan = AddressFamilyPolicy.plan(
            settings = AppSettings(ipv6Enabled = false),
            underlayHasIpv6 = true
        )
        assertEquals(IpFamilyPreference.IPV4_ONLY, plan.preference)
        assertEquals("ForceIPv4", plan.endpointStrategy)
        assertEquals("UseIPv4", plan.dnsQueryStrategy)
        assertTrue("::/0 must be blackholed so Android cannot bypass the tunnel", plan.blockIpv6Traffic)
        assertFalse("there is nothing to race on a single-family plan", plan.raceEnabled)
    }

    @Test
    fun enablingIpv6OnADualStackLinkActuallyPrefersIt() {
        // The default profile used to write ForceIP with no happyEyeballs block, which Xray reads as
        // "resolve both families and pick one at random" - the reported "IPv6 never activates".
        val plan = AddressFamilyPolicy.plan(
            settings = AppSettings(ipv6Enabled = true),
            underlayHasIpv6 = true
        )
        assertEquals(IpFamilyPreference.IPV6_FIRST, plan.preference)
        assertTrue(plan.prioritizeIpv6)
        assertTrue(plan.raceEnabled)
        assertTrue("a zero delay disables Xray's race", plan.tryDelayMs >= AddressFamilyPolicy.MIN_TRY_DELAY_MS)
        assertTrue(plan.maxConcurrentTry >= AddressFamilyPolicy.MIN_CONCURRENT_TRY)
        assertEquals("ForceIP", plan.endpointStrategy)
        assertEquals("UseIP", plan.dnsQueryStrategy)
        assertFalse(plan.blockIpv6Traffic)
    }

    @Test
    fun aPathThatCannotRacesStillGetsADeterministicOrder() {
        // Fragment and chained hops set dialerProxy, and UDP transports never reach Xray's TCP race.
        listOf(
            AddressFamilyPolicy.plan(
                settings = AppSettings(ipv6Enabled = true),
                underlayHasIpv6 = true,
                dialerProxy = "fragment-direct"
            ),
            AddressFamilyPolicy.plan(
                settings = AppSettings(ipv6Enabled = true),
                underlayHasIpv6 = true,
                tcpTransport = false
            ),
            AddressFamilyPolicy.plan(
                settings = AppSettings(ipv6Enabled = true, adaptiveDualStackEnabled = false),
                underlayHasIpv6 = true
            )
        ).forEach { plan ->
            assertFalse("racing must be off", plan.raceEnabled)
            assertEquals(0, plan.tryDelayMs)
            assertEquals("ForceIPv6v4", plan.endpointStrategy)
        }
    }

    @Test
    fun strictIpv6ModeRemovesTheIpv4Fallback() {
        val plan = AddressFamilyPolicy.plan(
            settings = AppSettings(ipv6Enabled = true, preferIpv6 = true, dnsQueryStrategy = "UseIPv6"),
            underlayHasIpv6 = true,
            tcpTransport = true
        )
        assertEquals(IpFamilyPreference.IPV6_ONLY, plan.preference)
        assertEquals("ForceIPv6", plan.endpointStrategy)
        assertEquals("UseIPv6", plan.dnsQueryStrategy)
        assertFalse("strict v6 has no fallback to race against", plan.raceEnabled)
    }

    @Test
    fun aMeasuredIpv6PathologyDemotesTheAutomaticPreference() {
        val demoted = AddressFamilyPolicy.plan(
            settings = AppSettings(ipv6Enabled = true),
            underlayHasIpv6 = true,
            measuredV6Healthy = false
        )
        // The automatic v6-first order is dropped, but the race stays armed so a dead family is
        // discovered in parallel instead of being dialled serially and timed out.
        assertEquals(IpFamilyPreference.DUAL, demoted.preference)
        assertFalse(demoted.prioritizeIpv6)
        assertEquals("ForceIP", demoted.endpointStrategy)
        assertTrue(demoted.raceEnabled)

        val explicit = AddressFamilyPolicy.plan(
            settings = AppSettings(ipv6Enabled = true, preferIpv6 = true),
            underlayHasIpv6 = true,
            measuredV6Healthy = false
        )
        // An explicit request is never overridden by a guess: the race is kept, and the head start is
        // all a bad measurement can cost.
        assertEquals(IpFamilyPreference.IPV6_FIRST, explicit.preference)
        assertTrue(explicit.prioritizeIpv6)
        assertEquals("ForceIP", explicit.endpointStrategy)
        assertTrue(explicit.raceEnabled)

        val explicitNoRace = AddressFamilyPolicy.plan(
            settings = AppSettings(ipv6Enabled = true, preferIpv6 = true, adaptiveDualStackEnabled = false),
            underlayHasIpv6 = true,
            measuredV6Healthy = false
        )
        assertFalse(explicitNoRace.raceEnabled)
        assertEquals("ForceIPv6v4", explicitNoRace.endpointStrategy)
    }

    @Test
    fun nodePinnedIpv4EndpointsAreNeverReorderedOntoIpv6() {
        val plan = AddressFamilyPolicy.plan(
            settings = AppSettings(),
            underlayHasIpv6 = true,
            importedStrategy = "ForceIPv4"
        )
        assertEquals(IpFamilyPreference.IPV4_ONLY, plan.preference)
        assertEquals("ForceIPv4", plan.endpointStrategy)
    }

    @Test
    fun addressOrderFollowsRfc8305AndDropsTheExcludedFamily() {
        val v4 = InetAddress.getByName("203.0.113.7")
        val v6 = InetAddress.getByName("2001:db8::7")
        val mixed = listOf(v4, v6)

        assertEquals(listOf(v6, v4), AddressFamilyPolicy.orderAddresses(mixed, IpFamilyPreference.IPV6_FIRST))
        assertEquals(listOf(v4, v6), AddressFamilyPolicy.orderAddresses(mixed, IpFamilyPreference.DUAL, prioritizeIpv6 = false))
        assertEquals(listOf(v4), AddressFamilyPolicy.orderAddresses(mixed, IpFamilyPreference.IPV4_ONLY))
        assertEquals(listOf(v6), AddressFamilyPolicy.orderAddresses(mixed, IpFamilyPreference.IPV6_ONLY))
        // A family the user excluded is dropped rather than dialled and timed out: a v6-only demand
        // against an A-record-only node must read as unreachable, not as a silent IPv4 success.
        assertTrue(
            AddressFamilyPolicy.orderAddresses(listOf(v4), IpFamilyPreference.IPV6_ONLY).isEmpty()
        )
        assertEquals(listOf(v4), AddressFamilyPolicy.orderAddresses(listOf(v4), IpFamilyPreference.IPV4_ONLY))
    }

    @Test
    fun endpointStrategiesStayInsideWhatTheEngineAccepts() {
        IpFamilyPreference.values().forEach { preference ->
            listOf(true, false).forEach { race ->
                listOf(true, false).forEach { v6 ->
                    val strategy = AddressFamilyPolicy.endpointStrategy(preference, race, v6)
                    assertTrue(
                        "Xray rejects $strategy",
                        strategy in setOf(
                            "AsIs", "UseIP", "UseIPv4", "UseIPv6", "UseIPv4v6", "UseIPv6v4",
                            "ForceIP", "ForceIPv4", "ForceIPv6", "ForceIPv4v6", "ForceIPv6v4"
                        )
                    )
                }
            }
        }
    }

    @Test
    fun handoverNeverBreaksBeforeChallengerVerification() {
        val handover = HandoverCoordinator()
        assertEquals(
            HandoverCoordinator.Action.START_CHALLENGER,
            handover.begin("route-a", "route-b", identityGuard = false)
        )
        assertEquals(
            HandoverCoordinator.Action.SWITCH_NEW_FLOWS,
            handover.verified("route-b", healthy = true)
        )
        assertEquals(
            HandoverCoordinator.Action.DRAIN_OLD,
            handover.switched("route-b")
        )
        handover.drained()
        assertEquals(HandoverCoordinator.State.IDLE, handover.snapshot().state)
        assertEquals("route-b", handover.snapshot().activeRoute)
    }
}
