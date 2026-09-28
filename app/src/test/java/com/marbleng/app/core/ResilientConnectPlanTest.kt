package com.marbleng.app.core

import org.junit.Assert.*
import org.junit.Test

class ResilientConnectPlanTest {
    @Test fun `healthy link uses bounded parallel probes and staggered family race`() {
        val plan = ResilientConnectPlan.create(ResilientConnectPlan.Evidence(rttMs = 40.0))
        assertEquals(4, plan.probeParallelism)
        assertEquals(250L, plan.ipv6RaceDelayMs)
        assertEquals(0L, plan.ipv4RaceDelayMs)
        assertFalse(plan.shouldFailOver)
    }

    @Test fun `poor link limits probe load and races families promptly`() {
        val plan = ResilientConnectPlan.create(
            ResilientConnectPlan.Evidence(rttMs = 1100.0, lossPercent = 9.0, ipv6Available = true)
        )
        assertEquals(1, plan.probeParallelism)
        assertEquals(0L, plan.ipv6RaceDelayMs)
        assertNull(plan.ipv4RaceDelayMs)
    }

    @Test fun `failover requires repeated failure and recovery requires three successes`() {
        assertFalse(ResilientConnectPlan.create(ResilientConnectPlan.Evidence(consecutiveFailures = 1)).shouldFailOver)
        assertTrue(ResilientConnectPlan.create(ResilientConnectPlan.Evidence(consecutiveFailures = 2)).shouldFailOver)
        assertFalse(ResilientConnectPlan.create(ResilientConnectPlan.Evidence(consecutiveSuccesses = 2)).shouldRecoverPrimary)
        assertTrue(ResilientConnectPlan.create(ResilientConnectPlan.Evidence(consecutiveSuccesses = 3)).shouldRecoverPrimary)
    }

    @Test fun `retry backoff is bounded and can be expedited on severe loss`() {
        assertEquals(4000L, ResilientConnectPlan.create(ResilientConnectPlan.Evidence(consecutiveFailures = 4)).retryDelayMs)
        assertEquals(250L, ResilientConnectPlan.create(ResilientConnectPlan.Evidence(consecutiveFailures = 5, lossPercent = 12.0)).retryDelayMs)
        assertEquals(6, ResilientConnectPlan.create(ResilientConnectPlan.Evidence(), maxParallelism = 99).probeParallelism)
    }
}
