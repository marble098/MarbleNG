package com.marbleng.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_REMEMBERED_PING_V160 — the last ping of every server has to survive a restart.
 *
 * A sweep over a big subscription is minutes of the device's radio, and until now the result
 * lived in the repository's memory only: leaving the app and coming back showed a Servers list
 * with no latency anywhere, because everything else the user did (the last route, the sources,
 * the settings) was written down and the measurement was not.
 *
 * The disk form is the whole contract, so it is pinned here: a result that cannot round-trip
 * cannot be remembered, and a remembered result that lost its stamp can never be pruned.
 */
class RememberedPingV160Test {

    private fun sample(
        id: String = "node-1",
        latency: Double = 184.0,
        at: Long = 1_700_000_000_000L
    ) = BenchmarkResult(
        profileId = id,
        name = "Germany • 01",
        success = 100,
        latencyMs = latency,
        bytesPerSecond = 4_200_000.0,
        score = 73.5,
        udpSuccess = 100,
        probeKind = "TUNNEL",
        jitterMs = 12.0,
        warmupMs = 240.0,
        sampleCount = 3,
        p95LatencyMs = 210.0,
        lossPercent = 0.0,
        failureReason = "",
        tcpHandshakeSuccessRatio = 1.0,
        handshakeAttempts = 3,
        measuredAtMs = at
    )

    @Test
    fun aMeasurementSurvivesTheDiskFormUntouched() {
        val original = sample()
        val restored = BenchmarkResult.fromJson(original.toJson())

        assertEquals(original.profileId, restored.profileId)
        assertEquals(original.name, restored.name)
        assertEquals(original.success, restored.success)
        assertEquals(original.latencyMs, restored.latencyMs, 0.001)
        assertEquals(original.bytesPerSecond, restored.bytesPerSecond, 0.001)
        assertEquals(original.score, restored.score, 0.001)
        assertEquals(original.jitterMs, restored.jitterMs, 0.001)
        assertEquals(original.p95LatencyMs, restored.p95LatencyMs, 0.001)
        assertEquals(original.tcpHandshakeSuccessRatio, restored.tcpHandshakeSuccessRatio, 0.001)
        assertEquals(original.probeKind, restored.probeKind)
        // The stamp is what lets a remembered ping be pruned instead of living forever.
        assertEquals(original.measuredAtMs, restored.measuredAtMs)
    }

    @Test
    fun anUnstampedMeasurementNeverPretendsToBeFresh() {
        val restored = BenchmarkResult.fromJson(sample(at = 0L).toJson())
        assertEquals(0L, restored.measuredAtMs)
        // A row with no stamp is older than every cutoff the store applies, so it is dropped
        // rather than shown: it can only have come from a build that did not remember pings.
        assertTrue(restored.measuredAtMs < 1L)
    }

    @Test
    fun aLegacyRowWithoutTheNewFieldsStillOpensWithHonestDefaults() {
        val legacy = org.json.JSONObject()
            .put("profileId", "node-legacy")
            .put("success", 80)
            .put("latencyMs", 320.0)

        val restored = BenchmarkResult.fromJson(legacy)

        assertEquals("node-legacy", restored.profileId)
        assertEquals(80, restored.success)
        assertEquals(320.0, restored.latencyMs, 0.001)
        // probeKind is read by the Library row; an empty string there would render as no
        // evidence at all, so the model repairs it to the shipped default.
        assertEquals("TUNNEL", restored.probeKind)
    }
}
