package com.marbleng.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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

        assertEquals(original.measuredAtMs, restored.measuredAtMs)
    }

    @Test
    fun anUnstampedMeasurementNeverPretendsToBeFresh() {
        val restored = BenchmarkResult.fromJson(sample(at = 0L).toJson())
        assertEquals(0L, restored.measuredAtMs)

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

        assertEquals("TUNNEL", restored.probeKind)
    }
}
