package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_IRAN_AWARE_PING — Layer 0–3 regressions.
 *
 * The redesign's rules are the acceptance criteria and must be pinned as tests:
 *  1. Layer 0 endpoint verification is TCP+TLS-ServerHello, not SYN-only; the rotating pool is
 *     deterministic per 10-minute epoch and never fixes on one SNI.
 *  2. Layer 1 sawtooth detection separates deliberate throttling from a flat slow transfer.
 *  3. Layer 2 requires three 20-minute-apart samples before an instability verdict, and
 *     reset-after-volume requires three reset-like observations.
 *  4. Layer 3 attributes a >70 % simultaneous multi-ASN drop as a national event and freezes
 *     ranking.
 */
class IranAwarePingTest {

    // ---------------------------------------------------------------- Layer 0

    @Test
    fun `probe target pool rotates deterministically per epoch`() {
        val seed = "profile-a"
        val first = ProbeTargetPool.ordered(ProbeTargetPool.CDN_TARGETS, seed)
        val second = ProbeTargetPool.ordered(ProbeTargetPool.CDN_TARGETS, seed)
        // Same seed + same epoch = same order (determinism for reproducibility).
        assertEquals(first, second)
        // The pool is a permutation: same members, no loss, no duplicates.
        assertEquals(first.toSet(), ProbeTargetPool.CDN_TARGETS.toSet())
        // Google/Cloudflare are members of the pool but by construction not the whole pool.
        assertTrue(ProbeTargetPool.CDN_TARGETS.size > 4)
    }

    @Test
    fun `anti-probing jitter stays within fifty to four hundred ms`() {
        repeat(64) {
            val jitter = ProbeTargetPool.nextJitterMs()
            assertTrue("jitter $jitter below floor", jitter >= ProbeTargetPool.MIN_JITTER_MS)
            assertTrue("jitter $jitter above ceiling", jitter <= ProbeTargetPool.MAX_JITTER_MS)
        }
    }

    @Test
    fun `reachability summary requires a verified signal before it is reachable`() {
        val injected = MultiVectorReachability.ReachabilitySignal(
            tcpConnected = true,
            tlsHandshakeCompleted = false,
            injectedResetSuspected = true,
            verdict = MultiVectorReachability.Verdict.INJECTED_RESET
        )
        assertFalse("injected reset must not read as reachable", injected.reachable)

        val verified = MultiVectorReachability.ReachabilitySignal(
            tcpConnected = true,
            tlsHandshakeCompleted = true,
            timeToFirstByteMs = 120.0,
            verdict = MultiVectorReachability.Verdict.REACHABLE
        )
        assertTrue(verified.reachable)
    }

    @Test
    fun `injection dominance decides a mixed summary verdict`() {
        val summary = MultiVectorReachability.ReachabilitySummary(
            signals = listOf(
                MultiVectorReachability.ReachabilitySignal(verdict = MultiVectorReachability.Verdict.INJECTED_RESET, injectedResetSuspected = true, tcpConnected = true),
                MultiVectorReachability.ReachabilitySignal(verdict = MultiVectorReachability.Verdict.INJECTED_RESET, injectedResetSuspected = true, tcpConnected = true)
            ),
            reachableSamples = 0,
            injectedResetCount = 2,
            silentTimeoutCount = 0,
            medianTtfbMs = 0.0,
            injectedResetRatePercent = 100.0
        )
        assertEquals(MultiVectorReachability.Verdict.INJECTED_RESET, summary.verdict)
    }

    // ---------------------------------------------------------------- Layer 1

    @Test
    fun `sawtooth detector calls a collapsing rate throttling`() {
        // 256 KiB over 1 s in 10 buckets; first buckets deliver fast, last buckets stall.
        val samples = (0L until 10L).map { bucket ->
            val delivered = when {
                bucket < 2 -> (bucket + 1) * 60_000L
                bucket < 6 -> 120_000L + (bucket - 2) * 5_000L
                else -> 140_000L + (bucket - 6) * 500L
            }
            ProtocolFingerprintAwareVerifier.ThroughputSample(
                elapsedMs = (bucket + 1) * 250L,
                bytes = delivered
            )
        }
        val pattern = SawtoothDetector.evaluate(samples)
        assertTrue("peak/tail shape must be flagged (confidence=${pattern.confidence})",
            pattern.sawtoothSuspected)
    }

    @Test
    fun `sawtooth detector leaves a flat transfer alone`() {
        val samples = (1L..10L).map { bucket ->
            ProtocolFingerprintAwareVerifier.ThroughputSample(
                elapsedMs = bucket * 250L,
                bytes = bucket * 25_000L
            )
        }
        val pattern = SawtoothDetector.evaluate(samples)
        assertFalse("flat transfer must not be throttling", pattern.sawtoothSuspected)
    }

    @Test
    fun `transport type is fingerprint derived not averaged`() {
        val vless = com.marbleng.app.model.ProxyProfile(
            id = "a", name = "a", scheme = "vless", raw = "", configJson = "",
            transport = "tcp", security = "reality"
        )
        val hy2 = com.marbleng.app.model.ProxyProfile(
            id = "b", name = "b", scheme = "hysteria2", raw = "", configJson = "",
            transport = "quic", security = "none"
        )
        assertEquals("vless/tcp/reality", ProtocolFingerprintAwareVerifier.transportTypeOf(vless))
        assertEquals("hysteria2/quic/none", ProtocolFingerprintAwareVerifier.transportTypeOf(hy2))
    }

    // ---------------------------------------------------------------- Layer 2

    @Test
    fun `three well-spaced stable samples are stable`() {
        val t0 = System.currentTimeMillis() - 61L * 60_000L
        val samples = listOf(
            BehavioralConsistency.ConsistencySample(t0, 120.0),
            BehavioralConsistency.ConsistencySample(t0 + 20L * 60_000L, 122.0),
            BehavioralConsistency.ConsistencySample(t0 + 40L * 60_000L, 121.0)
        )
        val report = BehavioralConsistency.evaluate(samples)
        assertEquals(BehavioralConsistency.StabilityClass.STABLE, report.stabilityClass)
    }

    @Test
    fun `three spaced samples with high stddev are unstable under observation`() {
        val t0 = System.currentTimeMillis() - 61L * 60_000L
        val samples = listOf(
            BehavioralConsistency.ConsistencySample(t0, 70.0),
            BehavioralConsistency.ConsistencySample(t0 + 20L * 60_000L, 250.0),
            BehavioralConsistency.ConsistencySample(t0 + 40L * 60_000L, 190.0)
        )
        val report = BehavioralConsistency.evaluate(samples)
        assertEquals(BehavioralConsistency.StabilityClass.UNSTABLE_UNDER_OBSERVATION, report.stabilityClass)
        assertTrue(report.stddevOverMean > BehavioralConsistency.UNSTABLE_STDDEV_OVER_MEAN)
    }

    @Test
    fun `back to back samples do not fake a twenty minute observation`() {
        val t0 = System.currentTimeMillis()
        val samples = listOf(
            BehavioralConsistency.ConsistencySample(t0, 80.0),
            BehavioralConsistency.ConsistencySample(t0 + 1_000L, 220.0),
            BehavioralConsistency.ConsistencySample(t0 + 2_000L, 190.0)
        )
        val report = BehavioralConsistency.evaluate(samples)
        assertEquals(BehavioralConsistency.StabilityClass.OBSERVING, report.stabilityClass)
    }

    @Test
    fun `four six hour day buckets map the whole day`() {
        assertEquals(0, BehavioralConsistency.timeOfDayBucket(0L))
        assertEquals(0, BehavioralConsistency.timeOfDayBucket(5L * 3_600_000L))
        assertEquals(1, BehavioralConsistency.timeOfDayBucket(6L * 3_600_000L))
        assertEquals(2, BehavioralConsistency.timeOfDayBucket(13L * 3_600_000L))
        assertEquals(3, BehavioralConsistency.timeOfDayBucket(23L * 3_600_000L))
    }

    @Test
    fun `reset after volume needs three reset like observations`() {
        val sample = BehavioralConsistency.ResetPatternSample(
            atMs = System.currentTimeMillis(),
            transportType = "vless/tcp/reality",
            bytesBeforeReset = 64L * 1024L,
            survivedMs = 2_000L,
            resetLike = true
        )
        var state = BehavioralConsistency.ResetPatternState()
        state = BehavioralConsistency.resetAfterVolumePattern(sample, state)
        assertEquals(0.0, state.confidence, 0.001)
        state = BehavioralConsistency.resetAfterVolumePattern(sample, state)
        state = BehavioralConsistency.resetAfterVolumePattern(sample, state)
        assertTrue("three volumetric resets must form the pattern", state.confidence >= 0.66)
    }

    // ---------------------------------------------------------------- Layer 3

    private fun observation(
        id: String,
        ip: String,
        dropped: Boolean,
        transport: String = "vless/tcp/reality",
        carrier: String = "cellular",
        prior: Boolean = true
    ) = CausalAttribution.ServerObservation(
        profileId = id,
        ip = ip,
        asn = "AS-$ip",
        carrier = carrier,
        transportType = transport,
        dropped = dropped,
        injectedResetSuspected = false,
        sawtoothConfidence = 0.0,
        priorEvidence = prior,
        windowStartMs = 1_000L,
        windowEndMs = 2_000L
    )

    @Test
    fun `more than seventy percent simultaneous multi asn drop is a national event`() {
        val result = CausalAttribution.attribute(
            listOf(
                observation("a", "1.1.1.1", dropped = true),
                observation("b", "2.2.2.2", dropped = true),
                observation("c", "3.3.3.3", dropped = true),
                observation("d", "4.4.4.4", dropped = true),
                observation("e", "5.5.5.5", dropped = true),
                observation("f", "6.6.6.6", dropped = false),
                observation("g", "7.7.7.7", dropped = false)
            )
        )
        assertEquals(CausalAttribution.AttributedCause.NATIONAL_FILTERING_EVENT, result.cause)
        assertTrue(result.rankingFreeze)
        assertTrue(result.confidence >= CausalAttribution.MIN_CONFIDENCE)
    }

    @Test
    fun `single provider mass drop is not a national event`() {
        val result = CausalAttribution.attribute(
            listOf(
                observation("a", "1.1.1.1", dropped = true).copy(asn = "AS-1", carrier = "cell-a"),
                observation("b", "1.1.1.2", dropped = true).copy(asn = "AS-1", carrier = "cell-a"),
                observation("c", "1.1.1.3", dropped = true).copy(asn = "AS-1", carrier = "cell-a")
            )
        )
        assertFalse("one ASN failing is one provider", result.rankingFreeze)
    }

    @Test
    fun `protocol bias with healthy sibling transport is protocol targeted`() {
        val result = CausalAttribution.attribute(
            listOf(
                observation("a", "1.1.1.1", dropped = true, transport = "vless/tcp/reality"),
                observation("b", "2.2.2.2", dropped = true, transport = "vless/tcp/reality"),
                observation("c", "3.3.3.3", dropped = false, transport = "hysteria2/quic/none"),
                observation("d", "4.4.4.4", dropped = false, transport = "hysteria2/quic/none")
            ).map { if (it.dropped) it.copy(sawtoothConfidence = 0.8) else it }
        )
        assertEquals(CausalAttribution.AttributedCause.PROTOCOL_TARGETED_THROTTLE, result.cause)
    }

    @Test
    fun `shallow degradation with prior evidence is server side`() {
        val result = CausalAttribution.attribute(
            listOf(
                observation("a", "1.1.1.1", dropped = true),
                observation("b", "2.2.2.2", dropped = false),
                observation("c", "3.3.3.3", dropped = false),
                observation("d", "4.4.4.4", dropped = false)
            )
        )
        assertEquals(CausalAttribution.AttributedCause.SERVER_SIDE_DEGRADATION, result.cause)
    }

    @Test
    fun `injection evidence attributes injected reset`() {
        val result = CausalAttribution.attribute(
            listOf(
                observation("a", "1.1.1.1", dropped = true).copy(injectedResetSuspected = true),
                observation("b", "2.2.2.2", dropped = true).copy(injectedResetSuspected = true),
                observation("c", "3.3.3.3", dropped = false),
                observation("d", "4.4.4.4", dropped = false)
            )
        )
        assertEquals(CausalAttribution.AttributedCause.INJECTED_RESET, result.cause)
    }

    // ---------------------------------------------------------------- deadlines & policies

    @Test
    fun `censorship aware deadline adds injection margin and fails fast`() {
        val base = 2_000L
        val normal = CensorshipAwareDeadlinePolicy.decideProbe(base)
        assertEquals(base, normal.timeoutMs)
        assertFalse(normal.failFast)

        val filtered = CensorshipAwareDeadlinePolicy.decideProbe(base, injectedResetSuspected = true)
        assertTrue(filtered.timeoutMs > base)
        assertTrue(filtered.failFast)
        assertTrue(filtered.useFallbackTarget)
    }

    @Test
    fun `deadline policy never shrinks the measured budget`() {
        val evidence = LinkEvidence(rttMs = 400.0, tailRttMs = 520.0, jitterMs = 30.0, samples = 6)
        val budget = LinkDeadlinePolicy.httpsProbeTimeoutMs(evidence)
        // 4 hops × 520 + 3×30 headroom must stay above the legacy floor.
        assertTrue(budget > LinkDeadlinePolicy.PRIMARY_DNS_FLOOR_MS)
    }

    // ---------------------------------------------------------------- resolvers

    @Test
    fun `resolver diversity counts operators not endpoints`() {
        val oneOperator = listOf("https://1.1.1.1/dns-query", "https://1.0.0.1/dns-query")
        assertEquals(1, ResolverEvidencePolicy.diversity(oneOperator))
        val diverse = listOf(
            "https://1.1.1.1/dns-query",
            "https://dns.google/dns-query",
            "https://dns.quad9.net/dns-query",
            "https://dns.adguard-dns.com/dns-query"
        )
        assertTrue(ResolverEvidencePolicy.diversity(diverse) >= ResolverEvidencePolicy.MIN_DIVERSE_PROVIDERS)
    }

    @Test
    fun `diversified pool appends independent providers when short`() {
        val short = listOf("https://1.1.1.1/dns-query", "https://1.0.0.1/dns-query")
        val fallback = listOf(
            "https://dns.google/dns-query",
            "https://dns.quad9.net/dns-query",
            "https://dns.adguard-dns.com/dns-query"
        )
        val merged = ResolverEvidencePolicy.diversified(short, fallback)
        assertTrue(ResolverEvidencePolicy.diversity(merged) >= ResolverEvidencePolicy.MIN_DIVERSE_PROVIDERS)
    }

    @Test
    fun `blacklisted operator is retrievable for periodic retest`() {
        val now = System.currentTimeMillis()
        val evidence = listOf(
            ResolverEvidencePolicy.EndpointEvidence(
                endpoint = "https://1.1.1.1/dns-query",
                deadlines = 9,
                lastFailureAtMs = now - 1_000L
            ),
            ResolverEvidencePolicy.EndpointEvidence(
                endpoint = "https://1.0.0.1/dns-query",
                deadlines = 9,
                lastFailureAtMs = now - 1_000L
            )
        )
        val blacklisted = ResolverEvidencePolicy.blacklistedOperators(
            listOf("https://1.1.1.1/dns-query", "https://1.0.0.1/dns-query"),
            evidence,
            now
        )
        assertTrue("cloudflare must be blacklisted when both its endpoints fail", blacklisted.containsKey("cloudflare"))
        assertTrue(ResolverEvidencePolicy.retestDue("cloudflare", blacklisted.getValue("cloudflare"), now + ResolverEvidencePolicy.OPERATOR_RETEST_MS))
    }

    @Test
    fun `rotation keeps demoted endpoints last`() {
        val now = System.currentTimeMillis()
        val evidence = listOf(
            ResolverEvidencePolicy.EndpointEvidence(
                endpoint = "https://1.1.1.1/dns-query",
                deadlines = 9,
                lastFailureAtMs = now - 1_000L
            )
        )
        val pool = listOf(
            "https://dns.google/dns-query",
            "https://dns.quad9.net/dns-query",
            "https://1.1.1.1/dns-query"
        )
        val ordered = ResolverEvidencePolicy.order(pool, evidence, now, seed = "x")
        assertEquals("https://1.1.1.1/dns-query", ordered.last())
    }

    // ---------------------------------------------------------------- Path MTU

    @Test
    fun `throttle synchronized mtu drop is reported to layer three`() {
        // Corroboration rule: a single differing sample is socket rotation; the second
        // identical reading is the learned signal.
        var state = PathMtuPolicy.State()
        state = PathMtuPolicy.observe(
            state = state,
            observedMtu = 1360,
            activeMtu = 1400,
            nowMs = 1_000L,
            sawtoothConfidence = 0.9
        ).state
        val decision = PathMtuPolicy.observe(
            state = state,
            observedMtu = 1360,
            activeMtu = 1400,
            nowMs = 2_000L,
            sawtoothConfidence = 0.9
        )
        assertTrue(decision.throttlingSynchronized)
        assertTrue(decision.reason.startsWith("throttle-synchronized"))
    }

    @Test
    fun `natural mtu drop stays a plain learn`() {
        var state = PathMtuPolicy.State()
        state = PathMtuPolicy.observe(
            state = state,
            observedMtu = 1360,
            activeMtu = 1400,
            nowMs = 1_000L
        ).state
        val decision = PathMtuPolicy.observe(
            state = state,
            observedMtu = 1360,
            activeMtu = 1400,
            nowMs = 2_000L
        )
        assertFalse(decision.throttlingSynchronized)
    }

    // ---------------------------------------------------------------- Turbo backoff

    @Test
    fun `national event never escalates the transport backoff`() {
        val state = TurboBackoffPolicy.State()
        val outcome = TurboBackoffPolicy.inconclusive(
            state = state,
            nowMs = 1_000L,
            cause = TurboBackoffPolicy.Cause.TRANSPORT_INCONCLUSIVE,
            baseMs = 30_000L,
            maxMs = 1_800_000L,
            nationalFilteringEvent = true
        )
        assertFalse(outcome.escalated)
        assertEquals(0, outcome.state.streak)
    }
}
