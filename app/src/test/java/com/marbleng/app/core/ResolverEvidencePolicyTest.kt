package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_RESOLVER_EVIDENCE_V134 regressions.
 *
 * The session that motivated this policy was `state=CONNECTED`, stable for two and a half hours, and
 * answered four or five of five ping probes — and still accumulated 29 `DoH deadline` events. V133
 * had already made every deadline a function of the measured link, so a correctly sized budget was
 * not the missing piece. The missing piece was that nothing in the stack ever *acted* on the
 * attributed failures: the resolver list was emitted from a fixed order forever, the count was
 * reported and discarded, and Bug Finder compared raw totals across sessions of different lengths so
 * the healthy long session read worse than the broken short one.
 *
 * Every test below pins one rule of the loop that replaced it.
 */
class ResolverEvidencePolicyTest {

    /** A realistic wall clock: `0` is the policy's "never observed" sentinel, not a test time. */
    private val t0 = 1_700_000_000_000L

    /** The exact shape Xray prints: the failing resolver's transport URL, quoted, then the cause. */
    private fun deadlineLine(endpoint: String) =
        "2026-09-04 17:16:36.412113 [Error] app/dns: failed to retrieve response for " +
            "www.google.com. > Post \"$endpoint\": context deadline exceeded"

    private val cloudflare = "https://1.1.1.1/dns-query"
    private val google = "https://8.8.8.8/dns-query"
    private val quad9 = "https://9.9.9.9/dns-query"
    private val pool = listOf(cloudflare, google, quad9)

    private fun observe(
        lines: List<String>,
        existing: List<ResolverEvidencePolicy.EndpointEvidence> = emptyList(),
        nowMs: Long = t0
    ) = ResolverEvidencePolicy.observe(lines.asSequence(), existing, nowMs)

    // ------------------------------------------------------------------
    // Attribution
    // ------------------------------------------------------------------

    @Test
    fun `a resolver failure is attributed to the endpoint the core named`() {
        assertEquals(cloudflare, ResolverEvidencePolicy.endpointOf(deadlineLine(cloudflare)))
        assertEquals(google, ResolverEvidencePolicy.endpointOf(deadlineLine(google)))
    }

    @Test
    fun `a line that names no endpoint is counted but attributed to nobody`() {
        // The classic plaintext EOF line carries no transport URL, so it must not be charged to a
        // resolver that happens to be mentioned elsewhere in the same session.
        assertNull(
            ResolverEvidencePolicy.endpointOf(
                "[Error] app/dns: failed to read response length > EOF"
            )
        )
        assertNull(ResolverEvidencePolicy.endpointOf(""))
        assertNull(ResolverEvidencePolicy.endpointOf("not a log line at all"))
    }

    @Test
    fun `a destination url inside a dns line is never charged to a resolver`() {
        // Being DNS-related is necessary but not sufficient: the quoted URL has to be a resolver
        // transport, otherwise an ordinary HTTPS destination would demote whoever it half-mentions.
        assertNull(
            ResolverEvidencePolicy.endpointOf(
                "[Error] app/dns: lookup path for dns.google failed > " +
                    "Get \"https://www.gstatic.com/generate_204\": context deadline exceeded"
            )
        )
    }

    @Test
    fun `a resolver-specific scheme is attributed without a dns path`() {
        assertEquals(
            "https+local://1.0.0.1/dns-query",
            ResolverEvidencePolicy.endpointOf(
                "[Error] app/dns: query failed > " +
                    "Post \"https+local://1.0.0.1/dns-query\": i/o timeout"
            )
        )
    }

    @Test
    fun `shutdown-safe lines never build evidence against a resolver`() {
        val teardown = observe(
            listOf(
                "[Error] app/dns: failed to retrieve response for example.com. > " +
                    "Post \"$cloudflare\": context canceled",
                "[Error] app/dns: failed to retrieve response for example.com. > " +
                    "Post \"$cloudflare\": read/write on closed pipe"
            )
        )
        assertTrue("teardown is not a resolver outage, got $teardown", teardown.isEmpty())
    }

    @Test
    fun `an expired certificate is decisive on the first event`() {
        val evidence = observe(
            listOf(
                "[Error] app/dns: failed to retrieve response for example.com. > " +
                    "Post \"$google\": x509: certificate has expired"
            )
        )
        assertEquals(1, evidence.size)
        assertEquals(1, evidence.first().certExpired)
        assertTrue(
            "an endpoint that cannot present a valid certificate must not keep its rank",
            ResolverEvidencePolicy.isDemoted(google, evidence, t0)
        )
    }

    // ------------------------------------------------------------------
    // Demotion, decay, TTL, recovery
    // ------------------------------------------------------------------

    @Test
    fun `a single transient deadline does not demote anything`() {
        val evidence = observe(listOf(deadlineLine(cloudflare)))
        assertEquals(1, evidence.first().decisiveFailures)
        assertFalse(ResolverEvidencePolicy.isDemoted(cloudflare, evidence, t0))
        assertEquals(pool, ResolverEvidencePolicy.order(pool, evidence, t0))
    }

    @Test
    fun `decisive failures demote the endpoint to the end of the emitted list`() {
        val evidence = observe(List(4) { deadlineLine(cloudflare) })
        assertEquals(ResolverEvidencePolicy.DEMOTE_FAILURES, evidence.first().decisiveFailures)
        assertTrue(ResolverEvidencePolicy.isDemoted(cloudflare, evidence, t0))
        assertEquals(
            "the disrupted resolver must lose its rank to endpoints with no evidence against them",
            listOf(google, quad9, cloudflare),
            ResolverEvidencePolicy.order(pool, evidence, t0)
        )
        assertEquals(listOf(cloudflare), ResolverEvidencePolicy.demoted(pool, evidence, t0))
    }

    @Test
    fun `demotion is an ordering, never a deletion`() {
        val evidence = observe(List(6) { deadlineLine(cloudflare) })
        val ordered = ResolverEvidencePolicy.order(pool, evidence, t0)
        assertEquals(pool.size, ordered.size)
        assertTrue(ordered.containsAll(pool))
    }

    @Test
    fun `a demotion decays away instead of lasting for the life of the app`() {
        val evidence = observe(List(8) { deadlineLine(cloudflare) })
        assertTrue(ResolverEvidencePolicy.isDemoted(cloudflare, evidence, t0))

        // One half-life halves the pressure, but a storm of eight is still above the threshold.
        val oneHalfLife = t0 + ResolverEvidencePolicy.DECAY_HALF_LIFE_MS
        val decayedOnce = observe(emptyList(), evidence, oneHalfLife)
        assertEquals(4, decayedOnce.first().decisiveFailures)
        assertTrue(ResolverEvidencePolicy.isDemoted(cloudflare, decayedOnce, oneHalfLife))

        // Two half-lives: the evidence about a window that has passed has faded below the threshold.
        val twoHalfLives = t0 + ResolverEvidencePolicy.DECAY_HALF_LIFE_MS * 2
        val decayedTwice = observe(emptyList(), decayedOnce, twoHalfLives)
        assertEquals(2, decayedTwice.first().decisiveFailures)
        assertFalse(
            "a filtering window ends, and the evidence about it must end too",
            ResolverEvidencePolicy.isDemoted(cloudflare, decayedTwice, twoHalfLives)
        )
        assertEquals(pool, ResolverEvidencePolicy.order(pool, decayedTwice, twoHalfLives))
    }

    @Test
    fun `a demotion expires with its ttl even when the counters have not decayed`() {
        val evidence = observe(List(4) { deadlineLine(cloudflare) })
        val afterTtl = t0 + ResolverEvidencePolicy.DEMOTE_TTL_MS + 1L
        assertFalse(
            "a demotion that outlives its TTL becomes a permanent deletion",
            ResolverEvidencePolicy.isDemoted(cloudflare, evidence, afterTtl)
        )
    }

    @Test
    fun `a proven answer restores the rank immediately`() {
        val evidence = observe(List(5) { deadlineLine(cloudflare) })
        assertTrue(ResolverEvidencePolicy.isDemoted(cloudflare, evidence, t0))

        val recoveredAt = t0 + 1_000L
        val recovered = ResolverEvidencePolicy.recordSuccess(cloudflare, evidence, recoveredAt)
        assertFalse(
            "the recovery half of the loop: an endpoint that answered is not failing",
            ResolverEvidencePolicy.isDemoted(cloudflare, recovered, recoveredAt)
        )
        assertEquals(pool, ResolverEvidencePolicy.order(pool, recovered, recoveredAt))
    }

    @Test
    fun `an answer recorded before the failures does not excuse them`() {
        val stale = ResolverEvidencePolicy.recordSuccess(cloudflare, emptyList(), t0)
        val failedAfter = t0 + 60_000L
        val evidence = observe(List(4) { deadlineLine(cloudflare) }, stale, failedAfter)
        assertTrue(ResolverEvidencePolicy.isDemoted(cloudflare, evidence, failedAfter))
    }

    @Test
    fun `when every candidate is failing the configured order is kept`() {
        val evidence = observe(
            List(4) { deadlineLine(cloudflare) } +
                List(4) { deadlineLine(google) } +
                List(4) { deadlineLine(quad9) }
        )
        assertEquals(3, ResolverEvidencePolicy.demoted(pool, evidence, t0).size)
        assertEquals(
            "reordering cannot help when nothing in the pool is healthy, and it would only churn " +
                "the emitted config",
            pool,
            ResolverEvidencePolicy.order(pool, evidence, t0)
        )
    }

    @Test
    fun `endpoint matching is case and whitespace insensitive like the config writer`() {
        val evidence = observe(List(4) { deadlineLine("HTTPS://1.1.1.1/DNS-QUERY") })
        assertTrue(
            ResolverEvidencePolicy.isDemoted("  https://1.1.1.1/dns-query  ", evidence, t0)
        )
    }

    @Test
    fun `persisted evidence is bounded and survives a round trip`() {
        val many = (1..20).map { index -> deadlineLine("https://10.0.0.$index/dns-query") }
        val evidence = observe(many)
        assertTrue(
            "a resolver ledger must not grow without bound, got ${evidence.size}",
            evidence.size <= ResolverEvidencePolicy.MAX_ENDPOINTS
        )
        val restored = ResolverEvidencePolicy.deserialize(ResolverEvidencePolicy.serialize(evidence))
        assertEquals(evidence, restored)
        assertTrue(ResolverEvidencePolicy.deserialize("").isEmpty())
        assertTrue(ResolverEvidencePolicy.deserialize("not json").isEmpty())
    }

    // ------------------------------------------------------------------
    // Racing
    // ------------------------------------------------------------------

    @Test
    fun `racing the resolvers is armed only by attributed failure evidence`() {
        assertFalse(
            "serial failover is the default: one query, one answer, no fan-out",
            ResolverEvidencePolicy.parallelQueryJustified(pool, emptyList(), t0)
        )
        val transientOnly = observe(listOf(deadlineLine(cloudflare)))
        assertFalse(
            "one transient deadline is not a reason to triple the DNS traffic",
            ResolverEvidencePolicy.parallelQueryJustified(pool, transientOnly, t0)
        )
        val decisive = observe(List(4) { deadlineLine(cloudflare) })
        assertTrue(
            "a decisively failing endpoint in the emitted list is exactly what racing is for",
            ResolverEvidencePolicy.parallelQueryJustified(pool, decisive, t0)
        )
        assertFalse(
            "evidence about an endpoint that is not a candidate must not arm the fan-out",
            ResolverEvidencePolicy.parallelQueryJustified(listOf(google, quad9), decisive, t0)
        )
    }

    // ------------------------------------------------------------------
    // Honest reporting
    // ------------------------------------------------------------------

    @Test
    fun `the same count reads differently inside a different window`() {
        // The two sessions from the attached runtime logs: 16 events inside a short broken session
        // and 29 events across a long stable one. The absolute comparison says the healthy session is
        // worse; the rate says the opposite, which is what actually happened.
        val brokenShort = ResolverEvidencePolicy.window(16, 3L * 60_000L)
        val healthyLong = ResolverEvidencePolicy.window(29, 158L * 60_000L)
        assertEquals(ResolverEvidencePolicy.Severity.SEVERE, brokenShort.severity)
        assertEquals(ResolverEvidencePolicy.Severity.CONTAINED, healthyLong.severity)
        assertTrue(brokenShort.perMinute > healthyLong.perMinute)
        assertFalse(brokenShort.rateUnknown)
        assertFalse(healthyLong.rateUnknown)
    }

    @Test
    fun `a window too short to divide by falls back to the absolute reading`() {
        val unknown = ResolverEvidencePolicy.window(12, 0L)
        assertTrue(unknown.rateUnknown)
        assertEquals(ResolverEvidencePolicy.Severity.ELEVATED, unknown.severity)
        val small = ResolverEvidencePolicy.window(3, 0L)
        assertTrue(small.rateUnknown)
        assertEquals(ResolverEvidencePolicy.Severity.CONTAINED, small.severity)
    }

    @Test
    fun `no failures is contained whatever the window`() {
        assertEquals(
            ResolverEvidencePolicy.Severity.CONTAINED,
            ResolverEvidencePolicy.window(0, 0L).severity
        )
        assertFalse(ResolverEvidencePolicy.window(0, 600_000L).rateUnknown)
    }

    @Test
    fun `the description names the endpoints and their state`() {
        val evidence = observe(
            List(4) { deadlineLine(cloudflare) } + listOf(deadlineLine(google))
        )
        val description = ResolverEvidencePolicy.describe(evidence, t0)
        assertTrue(description.contains(cloudflare))
        assertTrue(description.contains("demoted"))
        assertTrue(description.contains(google))
        assertEquals(
            "no resolver endpoint failures attributed",
            ResolverEvidencePolicy.describe(emptyList(), t0)
        )
    }
}
