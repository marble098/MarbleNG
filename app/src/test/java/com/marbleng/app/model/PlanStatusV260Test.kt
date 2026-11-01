package com.marbleng.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * MARBLE_SERVER_PLAN_CARDS_V260 — the plan arithmetic, on the host JVM.
 *
 * Every assertion here is a screenshot the readers sent about the Servers page: a bar that ran
 * backwards on a provider reporting 101 %, a byte pair with no ceiling, an expiry nobody printed,
 * and "towards the end" being a colour that disagreed with the number beside it.
 */
class PlanStatusV260Test {

    private val zone = TimeZone.getTimeZone("UTC")

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12): Long {
        val calendar = Calendar.getInstance(zone).apply {
            clear()
            set(year, month - 1, day, hour, 0, 0)
        }
        return calendar.timeInMillis
    }

    private fun sub(
        upload: Long = 0,
        download: Long = 0,
        total: Long = 0,
        expireAt: Long = 0,
        updatedAt: Long = 0
    ) = Subscription(
        id = "s1",
        name = "Provider",
        url = "https://example.test/sub",
        updatedAt = updatedAt,
        uploadBytes = upload,
        downloadBytes = download,
        totalBytes = total,
        expireAt = expireAt
    )

    // ── quota ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun aProviderThatReportsMoreTrafficThanTheCeilingNeverDrawsABarBackwards() {
        val status = PlanStatus.of(
            sub(upload = 40L * GIB, download = 60L * GIB, total = 50L * GIB),
            nowMs = at(2026, 11, 1),
            zone = zone
        )
        assertEquals(1f, status.usageFraction, 0.0001f)
        assertEquals(0L, status.remainingBytes)
        assertEquals(PlanStatus.Tone.DANGER, status.tone)
        assertEquals("100%", PlanStatus.usagePercentLabel(status.usageFraction, status.usedBytes))
    }

    @Test
    fun aPlanWithoutACeilingReportsNoShareRatherThanZero() {
        val status = PlanStatus.of(
            sub(upload = 2L * MIB, download = 3L * MIB, total = 0, expireAt = at(2027, 1, 1)),
            nowMs = at(2026, 11, 1),
            zone = zone
        )
        // No ceiling: there is no fraction to print, and the card must not draw a 0 % bar.
        assertFalse(status.hasQuota)
        assertEquals(0f, status.usageFraction, 0.0001f)
        assertTrue(status.hasExpiry)
        // …and the tone is driven by the expiry it *does* have, not by a fabricated quota.
        assertEquals(PlanStatus.Tone.CALM, status.tone)
    }

    @Test
    fun onePercentIsTheFloorOnceAnythingAtAllHasBeenUsed() {
        val used = 1L
        val total = 100_000L
        val fraction = PlanStatus.fractionOf(used, total)
        // 0.001 % rounds to 0, and a 0 % label beside a visible sliver of fill is a disagreement.
        assertEquals("1%", PlanStatus.usagePercentLabel(fraction, used))
        assertEquals("0%", PlanStatus.usagePercentLabel(0f, 0L))
    }

    @Test
    fun theQuotaPairPrintsTheProductUnitsOnBothSides() {
        assertEquals("2.00 GB / 5.00 GB", PlanStatus.quotaPairLabel(2L * GIB, 5L * GIB))
        // The model's formatter and the UI's must print the same unit ladder.
        assertEquals(PlanStatus.formatBytes(1_500_000L), formatBytes(1_500_000L))
        assertEquals(PlanStatus.formatBytes(0L), formatBytes(0L))
        assertEquals(PlanStatus.formatBytes(3L * GIB), formatBytes(3L * GIB))
    }

    // ── expiry ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun expiryIsCountedInCalendarDaysNotInHours() {
        val now = at(2026, 11, 1, hour = 23)
        // Eleven hours later is still *tomorrow*: a calendar day, not 0.45 of one.
        assertEquals(1, PlanStatus.daysBetween(now, at(2026, 11, 2, hour = 10), zone))
        assertEquals(0, PlanStatus.daysBetween(now, at(2026, 11, 1, hour = 23), zone))
        assertEquals(6, PlanStatus.daysBetween(now, at(2026, 11, 7), zone))
        // A past instant is negative, which the card prints as "3 days ago".
        assertEquals(-3, PlanStatus.daysBetween(now, at(2026, 10, 29), zone))
    }

    @Test
    fun theExpiryReadsAsDistanceInWords() {
        assertEquals("in 6 days", PlanStatus.expiryLabel(6))
        assertEquals("Tomorrow", PlanStatus.expiryLabel(1))
        assertEquals("Today", PlanStatus.expiryLabel(0))
        assertEquals("Yesterday", PlanStatus.expiryLabel(-1))
        assertEquals("3 days ago", PlanStatus.expiryLabel(-3))
        assertEquals("No expiry", PlanStatus.expiryLabel(Int.MIN_VALUE))
    }

    @Test
    fun theExpiryDayIsPrintedInTheCalendarTheReaderThinksIn() {
        // 1 November 2026 is 10 Aban 1405, and Nowruz 1405 was 21 March 2026.
        val november = at(2026, 11, 1)
        assertEquals("2026-11-01", PlanStatus.expiryDate(november, persian = false, zone = zone))
        assertEquals("1405-08-10", PlanStatus.expiryDate(november, persian = true, zone = zone))
        assertEquals(JalaliCalendar.Date(1405, 1, 1), JalaliCalendar.of(at(2026, 3, 21)))
        assertEquals(JalaliCalendar.Date(1404, 1, 1), JalaliCalendar.of(at(2025, 3, 21)))
        assertEquals(JalaliCalendar.Date(1403, 1, 1), JalaliCalendar.of(at(2024, 3, 20)))
        // A plan with no expiry prints nothing at all rather than 1970.
        assertEquals("", PlanStatus.expiryDate(0L, persian = true, zone = zone))
    }

    // ── tones ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun aPlanIsAsLoudAsItsLoudestProblem() {
        // Full quota, a year of expiry: still dangerous, because the quota already ran out.
        assertEquals(
            PlanStatus.Tone.DANGER,
            PlanStatus.toneOf(0.97f, 365, hasQuota = true, hasExpiry = true)
        )
        // Empty quota, expiring tomorrow: dangerous for the other reason.
        assertEquals(
            PlanStatus.Tone.DANGER,
            PlanStatus.toneOf(0.10f, 2, hasQuota = true, hasExpiry = true)
        )
        // Three quarters used: a warning, and so is a week left.
        assertEquals(
            PlanStatus.Tone.WARN,
            PlanStatus.toneOf(PlanStatus.WARN_FRACTION, 90, hasQuota = true, hasExpiry = false)
        )
        assertEquals(
            PlanStatus.Tone.WARN,
            PlanStatus.toneOf(0f, PlanStatus.WARN_DAYS, hasQuota = false, hasExpiry = true)
        )
        // Plenty of both: calm.
        assertEquals(
            PlanStatus.Tone.CALM,
            PlanStatus.toneOf(0.20f, 90, hasQuota = true, hasExpiry = true)
        )
        // Nothing known: unknown, never a false calm.
        assertEquals(PlanStatus.Tone.UNKNOWN, PlanStatus.toneOf(0f, 0, false, false))
    }

    @Test
    fun anExpiredPlanIsDangerousAndSaysSo() {
        val status = PlanStatus.of(
            sub(download = 1L * GIB, total = 10L * GIB, expireAt = at(2026, 10, 20)),
            nowMs = at(2026, 11, 1),
            zone = zone
        )
        assertTrue(status.expired)
        assertTrue(status.remainingDays < 0)
        assertEquals(PlanStatus.Tone.DANGER, status.tone)
    }

    @Test
    fun aCardWithNothingToSayDrawsNoPlanRow() {
        assertTrue(PlanStatus.of(sub(), nowMs = at(2026, 11, 1), zone = zone).isEmpty)
        assertFalse(
            PlanStatus.of(
                sub(total = 1L * GIB, updatedAt = at(2026, 10, 1)),
                nowMs = at(2026, 11, 1),
                zone = zone
            ).isEmpty
        )
    }

    private companion object {
        const val MIB = 1_024L * 1_024L
        const val GIB = 1_024L * MIB
    }
}
