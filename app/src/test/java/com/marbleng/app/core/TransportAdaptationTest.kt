package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.ProxyProfile
import com.marbleng.app.model.TransportProfileMode
import com.marbleng.app.model.transportProfileModeEnum
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue

/**
 * MARBLE_TRANSPORT_ADAPTATION_V203 — the learner behind Fragment & Mux.
 *
 * The requirement is not "have profiles": it is that the product *notices* when an operator
 * changes what it does and moves with it. So the tests that matter here are the three that
 * pin the drift detector, the exploration that makes drift findable at all, and the bound on
 * the table — everything else is arithmetic that a wrong constant would break loudly anyway.
 */
class TransportAdaptationTest {

    private val dayPart = DayPart.EVENING
    private val carrier = "mci"

    private fun observe(
        memory: TransportMemoryRecord?,
        pair: TransportPair,
        success: Boolean = true,
        latencyMs: Double = 120.0,
        jitterMs: Double = 20.0,
        throughput: Double = 3_000_000.0,
        nowMs: Long = 1_000_000L,
        severity: FilterSeverity = FilterSeverity.HEAVY
    ) = TransportAdaptation.observe(
        memory = memory,
        carrierId = carrier,
        dayPart = dayPart,
        pair = pair,
        success = success,
        latencyMs = latencyMs,
        jitterMs = jitterMs,
        throughputBytesPerSecond = throughput,
        nowMs = nowMs,
        severity = severity
    )

    // ─── The key ──────────────────────────────────────────────────────────────────────

    @Test
    fun `mcc-mnc wins over the isp name because it is what the sim actually is`() {
        assertEquals("43211", TransportAdaptation.carrierKeyOf("43211", "mci", "MCI"))
        assertEquals("mci", TransportAdaptation.carrierKeyOf("", "MCI", "Hamrah Aval"))
        assertEquals("hamrah aval", TransportAdaptation.carrierKeyOf("", "", "Hamrah Aval"))
        assertEquals(TransportAdaptation.UNKNOWN_CARRIER, TransportAdaptation.carrierKeyOf("", "", ""))
    }

    @Test
    fun `a short or non numeric operator code is not an operator code`() {
        // A four-digit truncated read must not become an operator identity.
        assertEquals("mci", TransportAdaptation.carrierKeyOf("4321", "mci", ""))
        assertEquals("mci", TransportAdaptation.carrierKeyOf("43a11", "mci", ""))
        assertEquals("mci", TransportAdaptation.carrierKeyOf("", "mci", ""))
    }

    @Test
    fun `day parts cover twenty four hours with no gap`() {
        for (hour in -24..48) {
            assertNotEquals(null, dayPartOf(hour))
        }
        assertEquals(dayPartOf(0), dayPartOf(24))
        assertEquals(dayPartOf(3), dayPartOf(27))
    }

    // ─── Quality of one connection ────────────────────────────────────────────────────

    @Test
    fun `a pair that cannot complete is worth nothing however fast it was`() {
        assertEquals(0.0, TransportAdaptation.quality(false, 20.0, 5.0, 40_000_000.0))
        assertEquals(0.0, TransportAdaptation.quality(true, 0.0, 0.0, 0.0))
        assertEquals(0.0, TransportAdaptation.quality(true, Double.NaN, 0.0, 0.0))
    }

    @Test
    fun `quality rises with speed and falls with latency and jitter`() {
        val fast = TransportAdaptation.quality(true, 40.0, 10.0, 4_000_000.0)
        val slow = TransportAdaptation.quality(true, 400.0, 10.0, 4_000_000.0)
        val shaky = TransportAdaptation.quality(true, 40.0, 200.0, 4_000_000.0)
        val thin = TransportAdaptation.quality(true, 40.0, 10.0, 100_000.0)
        assertTrue(fast > slow && fast > shaky && fast > thin)
        for (value in listOf(fast, slow, shaky, thin)) {
            assertTrue("quality must stay in 0..1, got $value", value in 0.0..1.0)
        }
    }

    // ─── The prior ────────────────────────────────────────────────────────────────────

    @Test
    fun `the prior follows the severity of the operator being talked to`() {
        val heavy = TransportAdaptation.priorFor(
            TransportPair(FragmentProfile.FULL_FRAGMENT, MuxProfile.BALANCED),
            FilterSeverity.HEAVY
        )
        val extreme = TransportAdaptation.priorFor(
            TransportPair(FragmentProfile.FULL_FRAGMENT, MuxProfile.BALANCED),
            FilterSeverity.EXTREME
        )
        val light = TransportAdaptation.priorFor(
            TransportPair(FragmentProfile.FULL_FRAGMENT, MuxProfile.BALANCED),
            FilterSeverity.LIGHT
        )
        assertTrue("shredding is at least as plausible on an extreme link", extreme >= heavy)
        assertTrue("shredding a light link is paying for nothing", heavy > light)
    }

    @Test
    fun `an untested pair is never worth zero or it would never be explored`() {
        for (severity in FilterSeverity.entries) {
            for (fragment in FragmentProfile.entries) {
                for (mux in MuxProfile.entries) {
                    val prior = TransportAdaptation.priorFor(TransportPair(fragment, mux), severity)
                    assertTrue("$fragment+$mux on $severity has no chance of being tried", prior > 0.0)
                }
            }
        }
    }

    @Test
    fun `quic and plaintext hops are not offered fragments that do nothing there`() {
        val quic = TransportAdaptation.candidatesFor(TransportShape.QUIC, FilterSeverity.HEAVY)
        assertTrue("QUIC has no stream to split", quic.all { it.fragment == FragmentProfile.OFF })
        assertTrue("QUIC carries no Mux here", quic.all { it.mux == MuxProfile.OFF })

        val plain = TransportAdaptation.candidatesFor(TransportShape.TCP_PLAIN, FilterSeverity.HEAVY)
        assertTrue(
            "a plaintext hop has no ClientHello to chain",
            plain.none { it.fragment.innerEnabled }
        )
        assertTrue("Mux is transport-agnostic and still valid", plain.any { it.mux != MuxProfile.OFF })

        val tls = TransportAdaptation.candidatesFor(TransportShape.TCP_TLS, FilterSeverity.HEAVY)
        assertTrue("a TLS hop has the widest field", tls.size > plain.size)
    }

    // ─── Drift: the reason this feature exists ────────────────────────────────────────

    @Test
    fun `a cell is not called drifting before it is confident`() {
        var memory: TransportMemoryRecord? = null
        val pair = TransportPair(FragmentProfile.GFW_KNOCKER, MuxProfile.BALANCED)
        repeat(TransportAdaptation.CONFIDENT_OBSERVATIONS - 1) { index ->
            // Alternate wildly: a young cell must not report drift on two observations.
            memory = observe(memory, pair, success = index % 2 == 0, nowMs = 1_000L + index)
        }
        assertEquals("drift needs a baseline first", 0.0, memory!!.drift, 0.0001)
    }

    @Test
    fun `an operator that starts breaking a working profile is detected`() {
        val pair = TransportPair(FragmentProfile.GFW_KNOCKER, MuxProfile.BALANCED)
        var memory: TransportMemoryRecord? = null
        // A long, quiet, good relationship.
        repeat(12) { index ->
            memory = observe(memory, pair, success = true, latencyMs = 110.0, nowMs = 1_000L + index)
        }
        val before = memory!!.drift
        assertTrue("a stable operator must not drift", before < TransportAdaptation.DRIFT_THRESHOLD)
        assertEquals("and it must not drift at all on a constant signal", 0.0, before, 0.0001)

        // The filter changes: the same profile stops completing.
        repeat(6) { index ->
            memory = observe(memory, pair, success = false, latencyMs = 0.0, nowMs = 5_000L + index)
        }
        assertTrue(
            "a profile that stops working must read as the operator changing, drift=${memory!!.drift}",
            memory!!.drift >= TransportAdaptation.DRIFT_THRESHOLD
        )
    }

    @Test
    fun `the windows are seeded or every cell drifts on its fifth connection`() {
        // An EWMA started at zero needs ~19 connections before its initial value stops
        // dominating; the fast window needs ~3. Comparing unseeded windows therefore reports
        // "the operator changed" on the fifth connection of a perfectly stable operator.
        val pair = TransportPair(FragmentProfile.GFW_KNOCKER, MuxProfile.BALANCED)
        var memory: TransportMemoryRecord? = null
        repeat(12) { index ->
            memory = observe(memory, pair, success = true, latencyMs = 110.0, nowMs = 1_000L + index)
        }
        assertEquals("a stable operator must never read as drifting", 0.0, memory!!.drift, 0.0001)
        assertEquals("the observation count is a count, not a score", 12, memory!!.observations)
    }

    @Test
    fun `drift clears by itself once the operator settles at its new behaviour`() {
        val pair = TransportPair(FragmentProfile.GFW_KNOCKER, MuxProfile.BALANCED)
        var memory: TransportMemoryRecord? = null
        repeat(12) { index ->
            memory = observe(memory, pair, success = true, latencyMs = 110.0, nowMs = 1_000L + index)
        }
        repeat(6) { index ->
            memory = observe(memory, pair, success = false, latencyMs = 0.0, nowMs = 5_000L + index)
        }
        assertTrue(memory!!.drift >= TransportAdaptation.DRIFT_THRESHOLD)

        // The filter settles into a new, worse but stable shape. Both windows converge onto
        // it, so the reading falls without any counter being reset — a detector that needed
        // bookkeeping to disarm would disarm one connection after it fired.
        repeat(40) { index ->
            memory = observe(
                memory,
                pair,
                success = true,
                latencyMs = 300.0,
                jitterMs = 60.0,
                throughput = 500_000.0,
                nowMs = 9_000L + index
            )
        }
        assertTrue(
            "a re-learned operator must stop reading as drifting: ${memory!!.drift}",
            memory!!.drift < TransportAdaptation.DRIFT_THRESHOLD
        )
    }

    @Test
    fun `a link that collapsed outright keeps reading as drift`() {
        // Both windows pinned at zero is not "settled at a new normal", it is a profile that
        // stopped working — and that must keep the challengers in play.
        val pair = TransportPair(FragmentProfile.GFW_KNOCKER, MuxProfile.BALANCED)
        var memory: TransportMemoryRecord? = null
        repeat(12) { index ->
            memory = observe(memory, pair, success = true, latencyMs = 110.0, nowMs = 1_000L + index)
        }
        repeat(45) { index ->
            memory = observe(memory, pair, success = false, latencyMs = 0.0, nowMs = 5_000L + index)
        }
        assertTrue("${memory!!.drift}", memory!!.drift >= TransportAdaptation.DRIFT_THRESHOLD)
    }

    @Test
    fun `after drift the selector re-learns instead of holding the broken profile`() {
        val broken = TransportPair(FragmentProfile.GFW_KNOCKER, MuxProfile.BALANCED)
        var memory: TransportMemoryRecord? = null
        repeat(12) { index ->
            memory = observe(memory, broken, success = true, latencyMs = 110.0, nowMs = 1_000L + index)
        }
        val heldBefore = TransportAdaptation.decide(
            memory = memory,
            shape = TransportShape.TCP_TLS,
            severity = FilterSeverity.HEAVY,
            nowMs = 9_000_000L
        )
        repeat(6) { index ->
            memory = observe(memory, broken, success = false, latencyMs = 0.0, nowMs = 5_000L + index)
        }
        val after = TransportAdaptation.decide(
            memory = memory,
            shape = TransportShape.TCP_TLS,
            severity = FilterSeverity.HEAVY,
            nowMs = 9_000_000L
        )
        assertTrue("the decision must say the operator moved", after.drifted)
        assertNotEquals(
            "holding a profile that just stopped working is the failure this feature exists to prevent",
            broken.id,
            after.pair.id
        )
    }

    // ─── Exploration and hysteresis ───────────────────────────────────────────────────

    @Test
    fun `an unproven pair is explored before the incumbent is trusted`() {
        var memory: TransportMemoryRecord? = null
        val first = TransportPair(FragmentProfile.TLSHELLO, MuxProfile.BALANCED)
        repeat(2) { index ->
            memory = observe(memory, first, success = true, latencyMs = 100.0, nowMs = 1_000L + index)
        }
        val decision = TransportAdaptation.decide(
            memory = memory,
            shape = TransportShape.TCP_TLS,
            severity = FilterSeverity.HEAVY,
            explore = true,
            nowMs = 9_000_000L
        )
        assertTrue("two observations is not proof", decision.exploring)
    }

    @Test
    fun `exploration can be switched off and then the evidence alone decides`() {
        var memory: TransportMemoryRecord? = null
        val good = TransportPair(FragmentProfile.OFFICIAL_SKIP_CHAIN, MuxProfile.BALANCED)
        repeat(8) { index ->
            memory = observe(memory, good, success = true, latencyMs = 90.0, nowMs = 1_000L + index)
        }
        val decision = TransportAdaptation.decide(
            memory = memory,
            shape = TransportShape.TCP_TLS,
            severity = FilterSeverity.LIGHT,
            explore = false,
            nowMs = 9_000_000L
        )
        assertEquals("with exploration off the best evidence wins outright", good.id, decision.pair.id)
    }

    @Test
    fun `a healthy incumbent is not dropped for a hair of a margin`() {
        var memory: TransportMemoryRecord? = null
        val incumbent = TransportPair(FragmentProfile.RECORD_SPLIT, MuxProfile.BALANCED)
        repeat(10) { index ->
            memory = observe(memory, incumbent, success = true, latencyMs = 100.0, nowMs = 1_000L + index)
        }
        val decision = TransportAdaptation.decide(
            memory = memory,
            shape = TransportShape.TCP_TLS,
            severity = FilterSeverity.MODERATE,
            explore = false,
            // Just after a change: inside the switch interval, so hysteresis applies.
            nowMs = memory!!.changedAtMs + 1_000L
        )
        assertEquals("a settled route must not flap", incumbent.id, decision.pair.id)
    }

    @Test
    fun `first contact uses the severity baseline and says so`() {
        val decision = TransportAdaptation.decide(
            memory = null,
            shape = TransportShape.TCP_TLS,
            severity = FilterSeverity.EXTREME,
            nowMs = 1_000L
        )
        assertTrue("a cell with no history is being explored by definition", decision.exploring)
        assertTrue(decision.reason, decision.reason.contains("first contact"))
        assertTrue(
            "the baseline must be the pair the severity table believes in",
            TransportAdaptation.priorFor(decision.pair, FilterSeverity.EXTREME) > 0.5
        )
    }

    // ─── Observation bookkeeping ──────────────────────────────────────────────────────

    @Test
    fun `observations accumulate and the pair on record is the last one used`() {
        var memory: TransportMemoryRecord? = null
        val a = TransportPair(FragmentProfile.TLSHELLO, MuxProfile.OFF)
        val b = TransportPair(FragmentProfile.RECORD_SPLIT, MuxProfile.LIGHT)
        repeat(3) { index -> memory = observe(memory, a, nowMs = 1_000L + index) }
        memory = observe(memory, b, nowMs = 2_000L)
        assertEquals(4, memory!!.observations)
        assertEquals(b.id, memory!!.pairId)
        assertEquals(2, memory!!.profileTries.size)
        assertEquals(3, memory!!.profileTries[a.id])
        assertEquals(2_000L, memory!!.changedAtMs)
    }

    @Test
    fun `a good run raises the record and a bad one lowers it`() {
        var memory: TransportMemoryRecord? = null
        val pair = TransportPair(FragmentProfile.RECORD_SPLIT, MuxProfile.BALANCED)
        repeat(5) { index ->
            memory = observe(memory, pair, success = true, latencyMs = 80.0, nowMs = 1_000L + index)
        }
        val good = memory!!.scoreEwma
        repeat(5) { index ->
            memory = observe(memory, pair, success = false, latencyMs = 0.0, nowMs = 2_000L + index)
        }
        assertTrue("the record must fall when the operator breaks the profile", memory!!.scoreEwma < good)
        assertTrue(memory!!.successEwma < 1.0)
    }

    @Test
    fun `the pair table stays bounded however long the product runs`() {
        var memory: TransportMemoryRecord? = null
        var now = 1_000L
        val pairs = FragmentProfile.entries.flatMap { fragment ->
            MuxProfile.entries.map { mux -> TransportPair(fragment, mux) }
        }
        assertEquals("8 fragments × 6 mux profiles", 48, pairs.size)
        repeat(6) { round ->
            pairs.forEach { pair ->
                memory = observe(memory, pair, nowMs = now++)
            }
        }
        assertTrue(
            "the table grew to ${memory!!.profileScores.size}",
            memory!!.profileScores.size <= TransportAdaptation.MAX_RECORDED_PAIRS
        )
        assertTrue("the current pair is never evicted", memory!!.profileScores.containsKey(memory!!.pairId))
    }

    @Test
    fun `prune drops stale cells and keeps the freshest ones`() {
        val now = 10_000_000L
        val young = (0 until TransportAdaptation.MAX_CELLS + 20).associate { index ->
            val record = TransportMemoryRecord(
                carrierId = "carrier-$index",
                dayPart = DayPart.MORNING,
                updatedAtMs = now - index * 1_000L
            )
            TransportMemoryRecord.keyOf(record.carrierId, record.dayPart) to record
        }
        val pruned = TransportAdaptation.prune(young, now)
        assertTrue("pruned to ${pruned.size}", pruned.size <= TransportAdaptation.MAX_CELLS)
        assertTrue("the newest cell survives", pruned.containsKey("carrier-0|morning"))

        val ancient = mapOf(
            "old|morning" to TransportMemoryRecord(
                carrierId = "old",
                dayPart = DayPart.MORNING,
                updatedAtMs = now - TransportAdaptation.MEMORY_MAX_AGE_MS - 1
            )
        )
        assertTrue("a 45-day-old cell is a guess", TransportAdaptation.prune(ancient, now).isEmpty())
    }

    // ─── Applying a decision ──────────────────────────────────────────────────────────

    @Test
    fun `applyTo writes every field the config builders read`() {
        val pair = TransportPair(FragmentProfile.OFFICIAL_SKIP_CHAIN, MuxProfile.THROUGHPUT)
        val applied = TransportAdaptation.applyTo(AppSettings(), pair)
        assertEquals(pair.fragment.enabled, applied.fragmentEnabled)
        assertEquals(pair.fragment.packets, applied.fragmentPackets)
        assertEquals(pair.fragment.length, applied.fragmentLength)
        assertEquals(pair.fragment.interval, applied.fragmentInterval)
        assertEquals(pair.fragment.maxSplit, applied.fragmentMaxSplit)
        assertEquals(pair.fragment.innerEnabled, applied.fragmentInnerEnabled)
        assertEquals(pair.mux.enabled, applied.muxEnabled)
        assertEquals(pair.mux.concurrency, applied.muxConcurrency)
        assertEquals(pair.mux.xudpConcurrency, applied.muxXudpConcurrency)
        assertEquals(pair.mux.udp443, applied.muxUdp443)
    }

    @Test
    fun `a round trip through applyTo and pairFromSettings is stable`() {
        for (fragment in FragmentProfile.entries) {
            for (mux in MuxProfile.entries) {
                val pair = TransportPair(fragment, mux)
                val read = TransportAdaptation.pairFromSettings(TransportAdaptation.applyTo(AppSettings(), pair))
                assertEquals("$pair did not survive apply→read", fragment, read.fragment)
                assertEquals("$pair did not survive apply→read", mux, read.mux)
            }
        }
    }

    @Test
    fun `user settings that match no profile still read as a profile`() {
        // A user who typed their own numbers by hand must not crash the learner or get a
        // profile that silently rewrites their wire.
        val custom = AppSettings(
            fragmentEnabled = true,
            fragmentPackets = "1-9",
            fragmentLength = "7",
            fragmentInterval = "3",
            fragmentMaxSplit = "11",
            muxEnabled = true,
            muxConcurrency = 7,
            muxXudpConcurrency = 13,
            muxUdp443 = "allow"
        )
        val pair = TransportAdaptation.pairFromSettings(custom)
        assertTrue("custom values must not read as fragment-off", pair.fragment.enabled)
        assertTrue(pair.mux.enabled)
        assertEquals(
            "the shipped default is automatic, gated behind the separate enable switch",
            TransportProfileMode.AUTO,
            AppSettings().transportProfileModeEnum
        )
    }

    @Test
    fun `the shipped default is off`() {
        // Shipping a learner switched on would be a silent behaviour change: the first
        // connection on a HEAVY operator would turn fragmentation on before the user had been
        // asked. The switch is the ask; the mode underneath it is already the interesting one.
        val settings = AppSettings()
        assertEquals(false, settings.transportAdaptationEnabled)
        assertEquals(TransportProfileMode.AUTO, settings.transportProfileModeEnum)
        assertEquals(true, settings.transportAdaptationExplore)
    }

    // ─── Shape detection ──────────────────────────────────────────────────────────────

    @Test
    fun `quic schemes are recognised as datagram transports`() {
        assertEquals(
            TransportShape.QUIC,
            transportShapeOf(ProxyProfile("1", "h", "hysteria2", "", ""))
        )
        assertEquals(
            TransportShape.QUIC,
            transportShapeOf(ProxyProfile("2", "t", "tuic", "", ""))
        )
        assertEquals(
            TransportShape.TCP_PLAIN,
            transportShapeOf(ProxyProfile("3", "s", "shadowsocks", "", ""))
        )
        assertEquals(
            TransportShape.TCP_TLS,
            transportShapeOf(
                ProxyProfile("4", "v", "vless", "", "", security = "tls", transport = "tcp")
            )
        )
    }
}
