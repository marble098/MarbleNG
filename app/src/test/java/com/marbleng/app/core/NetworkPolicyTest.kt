package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
