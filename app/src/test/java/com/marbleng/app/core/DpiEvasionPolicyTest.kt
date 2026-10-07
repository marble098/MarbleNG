package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_CORE_OPTIONS_V211 — what [DpiEvasionPolicy] is allowed to decide now.
 *
 * The fragment ladder that used to live here is gone with the feature: the two cores' options are
 * the user's, written verbatim, and no policy may rewrite them. What this object still owns is the
 * two decisions that are *not* a core option — the TUN segment ceiling on a filtered carrier, and
 * the timing budgets a measured lossy link earns — and these tests pin exactly that boundary.
 */
class DpiEvasionPolicyTest {
    private fun cellularIsp(severity: FilterSeverity) = IranIsp(
        asn = 0,
        name = "Mobile operator",
        persianName = "اپراتور همراه",
        shortName = "MCI",
        kind = IranIspKind.MOBILE,
        severity = severity
    )

    @Test
    fun neverCleartextRejectsHttp() {
        assertTrue(DpiEvasionPolicy.neverCleartext("https://example.com/sub"))
        assertFalse(DpiEvasionPolicy.neverCleartext("http://example.com/sub"))
        assertFalse(DpiEvasionPolicy.neverCleartext("ftp://example.com/sub"))
    }

    @Test
    fun aHealthyMeasuredPathEarnsNothing() {
        val base = AppSettings(
            mtuMax = 1500,
            mtuMin = 1280,
            benchTimeoutSec = 9,
            tcpPrecheckTimeoutMs = 2_000
        )
        val healed = DpiEvasionPolicy.heal(
            base,
            DpiEvasionPolicy.PathEvidence(pingMs = 80, jitterMs = 4, successPercent = 100, samples = 6),
            IranModeState()
        )
        assertEquals("nothing may change on a healthy path", base, healed)
    }

    @Test
    fun plainHighPingWidensTimeoutsWithoutTouchingAnythingElse() {
        val base = AppSettings(benchTimeoutSec = 9, tcpPrecheckTimeoutMs = 2_000, mtuMax = 1500)
        val healed = DpiEvasionPolicy.heal(
            base,
            DpiEvasionPolicy.PathEvidence(pingMs = 320, jitterMs = 6, successPercent = 100, samples = 6),
            IranModeState()
        )
        assertTrue(healed.benchTimeoutSec > base.benchTimeoutSec)
        assertTrue(healed.tcpPrecheckTimeoutMs > base.tcpPrecheckTimeoutMs)
        assertEquals(
            "distance is not packet loss: the MTU clamp belongs to the carrier, not to a ping",
            base.mtuMax,
            healed.mtuMax
        )
    }

    @Test
    fun lossAndJitterClampTheTunSegmentUnderTheCarrierCeiling() {
        val cellular = IranModeState(
            active = true,
            isp = cellularIsp(FilterSeverity.MODERATE)
        )
        val lossy = DpiEvasionPolicy.heal(
            AppSettings(mtuMax = 1500, mtuMin = 1280),
            DpiEvasionPolicy.PathEvidence(pingMs = 120, jitterMs = 8, successPercent = 55, samples = 8),
            cellular
        )
        assertTrue("loss must clamp below the carrier ceiling", lossy.mtuMax <= 1280)
        assertTrue(lossy.mtuMax >= lossy.mtuMin)

        val jittery = DpiEvasionPolicy.heal(
            AppSettings(mtuMax = 1500, mtuMin = 1280),
            DpiEvasionPolicy.PathEvidence(pingMs = 120, jitterMs = 60, successPercent = 100, samples = 8),
            cellular
        )
        assertTrue(jittery.mtuMax <= 1380)
    }

    @Test
    fun throttlingTechniqueWidensTheTimingBudgets() {
        val healed = DpiEvasionPolicy.heal(
            AppSettings(benchTimeoutSec = 9, tcpPrecheckTimeoutMs = 2_000),
            DpiEvasionPolicy.PathEvidence(),
            IranModeState(active = true, techniques = setOf(CensorTechnique.THROTTLING))
        )
        assertTrue(healed.benchTimeoutSec >= 14)
        assertTrue(healed.tcpPrecheckTimeoutMs >= 3_000)
    }

    @Test
    fun mtuCeilingFollowsSeverityAndCarrier() {
        assertEquals(
            "an unfiltered link keeps the standard MTU",
            1500,
            DpiEvasionPolicy.mtuCeiling(IranModeState(), cellular = false)
        )
        val cellular = IranModeState(active = true, isp = cellularIsp(FilterSeverity.MODERATE))
        assertEquals(1380, DpiEvasionPolicy.mtuCeiling(cellular, cellular = true))
        assertEquals(1420, DpiEvasionPolicy.mtuCeiling(IranModeState(active = true), cellular = false))
        val clampdown = IranModeState(
            active = true,
            isp = cellularIsp(FilterSeverity.MODERATE),
            techniques = setOf(CensorTechnique.NATIONAL_INTRANET)
        )
        assertEquals("a national blackout is the strictest tier", 1280, DpiEvasionPolicy.mtuCeiling(clampdown, cellular = true))
    }
}
