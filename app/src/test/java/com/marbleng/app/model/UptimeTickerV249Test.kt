package com.marbleng.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_LIVE_TICKER_V249 — the arithmetic of a clock that is watched while it runs, on the host JVM.
 *
 * The bug this pins the answer to: Home's second line was one string (`Protected · Uptime 00:10:01`)
 * and the pill animated the whole string, so every second the state word and all eight clock
 * characters slid out and back in around the one digit that had changed. The fix is that only the
 * digit that moved moves — and "which digit moved" is a rule about strings, so it is a rule with a
 * test rather than a rule inside a composable.
 */
class UptimeTickerV249Test {

    @Test
    fun `only the digit that changed rolls`() {
        val rolling = UptimeTicker.rolling("00:10:01", "00:10:02")
        assertEquals(listOf(false, false, false, false, false, false, false, true), rolling)
        assertEquals(1, UptimeTicker.rollingCount("00:10:01", "00:10:02"))
    }

    @Test
    fun `a minute change moves the minute and the seconds and leaves the hours still`() {
        val rolling = UptimeTicker.rolling("00:10:59", "00:11:00")
        // 0 0 : 1 0 : 5 9  ->  0 0 : 1 1 : 0 0
        assertEquals(listOf(false, false, false, false, true, false, true, true), rolling)
        assertEquals(3, UptimeTicker.rollingCount("00:10:59", "00:11:00"))
        assertFalse("the hour cells did not move", rolling[0] || rolling[1] || rolling[3])
    }

    @Test
    fun `the separators never roll because their value never changes`() {
        assertEquals(listOf(2, 5), UptimeTicker.separators("00:10:01"))
        assertTrue(UptimeTicker.isSeparator(':'))
        assertFalse(UptimeTicker.isSeparator('0'))
        // The rolling flags agree with the separator list: a colon is never a changed cell.
        val rolling = UptimeTicker.rolling("00:10:01", "00:10:02")
        UptimeTicker.separators("00:10:01").forEach { index -> assertFalse(rolling[index]) }
    }

    @Test
    fun `a reading that changes shape marks every cell`() {
        // The first hour of a session appearing: a positional diff across two shapes would be a lie.
        val rolling = UptimeTicker.rolling("10:01", "00:10:01")
        assertTrue(UptimeTicker.reshaped("10:01", "00:10:01"))
        assertEquals(8, rolling.size)
        assertTrue(rolling.all { it })
        assertFalse(UptimeTicker.reshaped("00:10:01", "00:10:02"))
    }

    @Test
    fun `the pulse is derived from the seconds, not driven by a timer`() {
        assertTrue(UptimeTicker.pulseLit("00:10:02"))
        assertFalse(UptimeTicker.pulseLit("00:10:03"))
        assertEquals(2, UptimeTicker.secondsOf("00:10:02"))
        assertEquals(59, UptimeTicker.secondsOf("00:10:59"))
    }

    @Test
    fun `a Persian reading is read as digits, not as unparseable text`() {
        // `toInt` reads ASCII only: a ticker that parsed its own digits with it would work in
        // English and silently stop pulsing in Persian, which is half the product.
        assertEquals(45, UptimeTicker.secondsOf("۰۰:۱۰:۴۵"))
        assertTrue(UptimeTicker.pulseLit("۰۰:۱۰:۴۶"))
        assertFalse(UptimeTicker.pulseLit("۰۰:۱۰:۴۷"))
        assertEquals(7, UptimeTicker.digitValue('۷'))
        assertEquals(7, UptimeTicker.digitValue('٧'))
        assertEquals(-1, UptimeTicker.digitValue('•'))
    }

    @Test
    fun `a reading that cannot be parsed keeps the separators lit instead of guessing`() {
        assertEquals(-1, UptimeTicker.secondsOf("—"))
        assertEquals(-1, UptimeTicker.secondsOf("00:10:0x"))
        assertEquals(-1, UptimeTicker.secondsOf(""))
        // A clock that cannot be read is not a clock that is blinking: the dim half of the pulse is
        // a statement about the seconds, and there are none.
        assertTrue(UptimeTicker.pulseLit("—"))
    }

    @Test
    fun `an unchanged reading is not a tick even with padding around it`() {
        assertFalse(UptimeTicker.ticked("00:10:01", "00:10:01"))
        assertFalse(UptimeTicker.ticked("00:10:01", "  00:10:01  "))
        assertTrue(UptimeTicker.ticked("00:10:01", "00:10:02"))
        assertEquals(UptimeTicker.cells("00:10:01"), UptimeTicker.cells(" 00:10:01 "))
    }

    @Test
    fun `the roll stays inside one cell and the clock stays left to right`() {
        assertTrue(UptimeTicker.ROLL_TRAVEL_FRACTION > 0f)
        assertTrue(UptimeTicker.ROLL_TRAVEL_FRACTION < 1f)
        // A Persian paragraph is right-to-left; a clock inside it is not. The container forces LTR so
        // `۰۰:۱۰:۰۱` is never drawn as `۰۱:۱۰:۰۰`.
        assertTrue(UptimeTicker.CLOCK_IS_LEFT_TO_RIGHT)
        assertTrue(UptimeTicker.SEPARATOR_DIM_ALPHA > 0f)
        assertTrue(UptimeTicker.SEPARATOR_DIM_ALPHA < 1f)
    }

    @Test
    fun `an empty clock draws nothing and its first reading arrives as one transition`() {
        assertEquals(emptyList<Char>(), UptimeTicker.cells(""))
        assertEquals(emptyList<Boolean>(), UptimeTicker.rolling("", ""))
        assertFalse(UptimeTicker.ticked("", ""))
        // Nothing to something is a change of shape, so the eight cells cross-fade together instead
        // of eight independent rolls firing on the first frame of a session.
        assertTrue(UptimeTicker.reshaped("", "00:10:01"))
        assertEquals(8, UptimeTicker.rollingCount("", "00:10:01"))
    }
}
