package com.marbleng.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * MARBLE_PERSIAN_CALENDAR_TRUTH_V275 — the calendar, pinned to the truth.
 *
 * The complaint this file answers was two sentences, and both are tested literally:
 *
 *  1. «تعطیل رسمی اعلام کردن اشتباه ۱۳ آبان که واقعا تعطیل رسمی نیست» — 13 Aban was wrongly
 *     declared an official holiday. It is Student Day, a commemoration, and no table this
 *     build carries — nor the solar-fixed rules, nor the Friday weekend — makes it a holiday.
 *  2. «نبود همچنان برخی از مناسبت ها» — occasions were missing. The non-holiday list now
 *     carries the national days a Persian reader expects to find, and the holidays come from
 *     a recorded official-calendar table instead of an invented rule.
 *
 * The third truth pinned here is arithmetic: the Jalali converter used to fold Esfand 30 of
 * every leap year back onto 12-29, so even a correct table could not have been displayed.
 */
class PersianCalendarV275Test {

    private fun utcOf(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month - 1, day, 12, 0, 0)
        }.timeInMillis

    // ── 13 Aban: a commemoration, never a holiday ────────────────────────────────────────────

    @Test
    fun thirteenAbanIsNotAnOfficialHolidayInAnyCoveredYear() {
        // The exact day the product lied about, in every year the table covers.
        for (year in PersianCalendar.TABLE_FIRST_YEAR..PersianCalendar.TABLE_LAST_YEAR) {
            assertFalse(
                "13 Aban $year must not be an official holiday",
                PersianCalendar.isOfficialHoliday(year, 8, 13)
            )
        }
    }

    @Test
    fun thirteenAbanIsStillStudentDaySoTheDayIsNotSilentEither() {
        val occasions = PersianCalendar.occasionsOn(1405, 8, 13)
        assertEquals(1, occasions.size)
        assertEquals("STUDENT_DAY", occasions.first().key)
        assertFalse(occasions.first().official)
    }

    @Test
    fun thirteenAbanFallsOnTheDayItFallsOn() {
        // 13 Aban 1405 = 4 November 2026, a Wednesday.
        assertEquals("2026-11-04", PersianCalendar.gregorianLabel(1405, 8, 13))
        assertEquals(4, PersianCalendar.weekIndexOf(1405, 8, 13))
        assertEquals("چهارشنبه", PersianCalendar.weekdayOf(1405, 8, 13, persian = true))
    }

    // ── Official holidays: the table, the solar rules, and Friday ─────────────────────────────

    @Test
    fun nowruzIsAnOfficialHoliday() {
        for (year in 1404..1412) {
            assertTrue("1 Farvardin $year", PersianCalendar.isOfficialHoliday(year, 1, 1))
        }
    }

    @Test
    fun theSolarFixedHolidaysHoldOutsideTheTableToo() {
        // 1413 is beyond the lunar table: the solar-fixed holidays still answer, and nothing
        // else pretends to know the moon.
        assertTrue(PersianCalendar.isOfficialHoliday(1413, 1, 12))
        assertTrue(PersianCalendar.isOfficialHoliday(1413, 11, 22))
        assertTrue(PersianCalendar.isOfficialHoliday(1413, 12, 29))
        assertFalse(PersianCalendar.holidayCoverage(1413))
        // A mid-Aban working day stays a working day.
        assertFalse(PersianCalendar.isOfficialHoliday(1413, 8, 13))
        // 29 Esfand is solar-fixed, so it is a holiday even beyond the table.
        assertTrue(PersianCalendar.occasionsOn(1413, 12, 29).any { it.key == "OIL" })
    }

    @Test
    fun theLunarTableIsTheMoonSighting() {
        // Spot checks transcribed from the recorded official calendar:
        // Eid al-Fitr 1404 = 11 Farvardin; Ashura 1404 = 15 Tir; Eid al-Adha 1405 = 7 Khordad;
        // Tasua/Ashura 1407 fell on 14/15 Khordad — beside the fixed 14/15 Khordad holidays.
        assertTrue(PersianCalendar.occasionsOn(1404, 1, 11).any { it.key == "EID_FITR" })
        assertTrue(PersianCalendar.occasionsOn(1404, 4, 15).any { it.key == "ASHURA" })
        assertTrue(PersianCalendar.occasionsOn(1405, 3, 7).any { it.key == "EID_ADHA" })
        assertEquals(2, PersianCalendar.occasionsOn(1407, 3, 14).size)
        assertEquals(2, PersianCalendar.occasionsOn(1407, 3, 15).size)
    }

    @Test
    fun aDayTwoHolidaysSharePrintsBoth() {
        // 1 Farvardin 1405 was Nowruz and Eid al-Fitr together.
        val occasions = PersianCalendar.occasionsOn(1405, 1, 1)
        assertEquals(listOf("NOWRUZ", "EID_FITR"), occasions.map { it.key })
    }

    @Test
    fun fridayIsTheWeekendAndThursdayIsNot() {
        // 13 Aban 1405 is a Wednesday, so that week's Thursday is the 14th and its Friday the
        // 15th (6 November 2026). Friday is the weekend; Thursday is a working day.
        assertTrue(PersianCalendar.isFriday(1405, 8, 15))
        assertFalse(PersianCalendar.isFriday(1405, 8, 14))
        assertTrue(PersianCalendar.isOfficialHoliday(1405, 8, 15))
        // Thursday the 14th carries no occasion and is not Friday → a working day.
        assertFalse(PersianCalendar.isOfficialHoliday(1405, 8, 14))
    }

    @Test
    fun everyTableDateIsARealDate() {
        for (year in PersianCalendar.TABLE_FIRST_YEAR..PersianCalendar.TABLE_LAST_YEAR) {
            for (month in 1..12) {
                val length = JalaliCalendar.daysInMonth(year, month)
                assertTrue("month $month/$year has $length days", length in 29..31)
            }
        }
        // The leap years the table's Esfand-30 rows depend on.
        assertTrue(JalaliCalendar.isLeapYear(1408))
        assertTrue(JalaliCalendar.isLeapYear(1412))
        assertFalse(JalaliCalendar.isLeapYear(1405))
    }

    // ── The Esfand-30 fix ────────────────────────────────────────────────────────────────────

    @Test
    fun esfandThirtyOfALeapYearRoundTripsInsteadOfFoldingOntoTwentyNine() {
        val epoch = utcOf(2030, 3, 20) // 30 Esfand 1408
        val date = JalaliCalendar.of(epoch, TimeZone.getTimeZone("UTC"))
        assertEquals(JalaliCalendar.Date(1408, 12, 30), date)
        assertEquals(30, JalaliCalendar.daysInMonth(1408, 12))
        assertEquals(29, JalaliCalendar.daysInMonth(1405, 12))
        assertEquals("2030-03-20", PersianCalendar.gregorianLabel(1408, 12, 30))
        assertEquals("2034-03-20", PersianCalendar.gregorianLabel(1412, 12, 30))
    }

    @Test
    fun theLastDayOfALeapYearCarriesItsOwnTableEntry() {
        assertTrue(PersianCalendar.occasionsOn(1408, 12, 30).any { it.key == "LAST_DAY" })
        assertTrue(PersianCalendar.occasionsOn(1412, 12, 30).any { it.key == "LAST_DAY" })
        // And a non-leap Esfand has no day 30 at all.
        assertTrue(PersianCalendar.occasionsOn(1405, 12, 30).isEmpty())
    }

    // ── Occasions the old calendar never had ────────────────────────────────────────────────

    @Test
    fun theCommemorationsAreOnTheCalendarAsNonHolidays() {
        val expected = listOf(
            Triple(1, 10, "PERSIAN_GULF_DAY"),
            Triple(1, 29, "ARMY_DAY"),
            Triple(2, 12, "TEACHERS_DAY"),
            Triple(7, 20, "HAFEZ_DAY"),
            Triple(8, 13, "STUDENT_DAY"),
            Triple(9, 30, "YALDA")
        )
        for ((month, day, key) in expected) {
            val occasions = PersianCalendar.occasionsOn(1405, month, day)
            val occasion = occasions.firstOrNull { it.key == key }
            assertTrue("$key on $month/$day", occasion != null)
            assertFalse("$key must never be an official holiday", occasion!!.official)
        }
    }

    @Test
    fun anOrdinaryDayHasNoOccasions() {
        assertTrue(PersianCalendar.occasionsOn(1405, 8, 16).isEmpty())
        assertFalse(PersianCalendar.isOfficialHoliday(1405, 8, 16))
    }

    // ── Today and what is coming ─────────────────────────────────────────────────────────────

    @Test
    fun todayReadsTheClockThroughTheJalaliConverter() {
        val nowMs = utcOf(2026, 11, 4)
        val today = PersianCalendar.today(nowMs, TimeZone.getTimeZone("UTC"))
        assertEquals(1405, today.year)
        assertEquals(8, today.month)
        assertEquals(13, today.day)
        assertEquals(4, today.weekIndex)
        assertFalse(today.holiday)
        assertTrue(today.lunarCoverage)
        assertEquals(listOf("STUDENT_DAY"), today.occasions.map { it.key })
    }

    @Test
    fun upcomingFindsTheNearestOccasionWithItsTrueDistance() {
        // From 16 Aban 1405 the nearest occasion is the martyrdom of Fatima on the 23rd — a
        // recorded lunar holiday seven days away, not a guess.
        val upcoming = PersianCalendar.upcoming(1405, 8, 16, limit = 1)
        assertEquals(1, upcoming.size)
        assertEquals(8, upcoming.first().month)
        assertEquals(23, upcoming.first().day)
        assertEquals(7, upcoming.first().daysAway)
        assertEquals("FATIMA", upcoming.first().occasion.key)
        assertTrue(upcoming.first().holiday)
    }

    @Test
    fun upcomingCrossesTheMonthEdgeCountingEveryDay() {
        // From 24 Aban 1405 nothing else lands in Aban; Azar carries Yalda on its 30th. Aban
        // has 30 days, so 08-24 → 08-30 is 6 days and 09-30 is 30 more: 36 days away.
        val upcoming = PersianCalendar.upcoming(1405, 8, 24, limit = 1)
        assertEquals(1, upcoming.size)
        assertEquals(9, upcoming.first().month)
        assertEquals(30, upcoming.first().day)
        assertEquals(36, upcoming.first().daysAway)
        assertEquals("YALDA", upcoming.first().occasion.key)
        assertFalse(upcoming.first().holiday)
    }

    @Test
    fun upcomingCountsTheDayDistanceTruthfully() {
        // From 9 Farvardin 1405 the nearest occasion is Persian Gulf Day on the 10th — one day
        // away, and a commemoration, not a holiday.
        val upcoming = PersianCalendar.upcoming(1405, 1, 9, limit = 1)
        assertEquals("PERSIAN_GULF_DAY", upcoming.first().occasion.key)
        assertEquals(1, upcoming.first().daysAway)
        assertFalse(upcoming.first().holiday)
    }

    @Test
    fun upcomingNeverLoopsForeverOnABadDate() {
        assertTrue(PersianCalendar.upcoming(1405, 8, 16, limit = 0).isEmpty())
    }

    // ── Names and formatting ─────────────────────────────────────────────────────────────────

    @Test
    fun theMonthsAndWeekdaysHaveOneSpellingEach() {
        assertEquals("آبان", PersianCalendar.monthName(8, persian = true))
        assertEquals("Aban", PersianCalendar.monthName(8, persian = false))
        assertEquals("اسفند", PersianCalendar.monthName(12, persian = true))
        assertEquals("جمعه", PersianCalendar.weekdayName(6, persian = true))
        assertEquals("Friday", PersianCalendar.weekdayName(6, persian = false))
        assertEquals("شنبه", PersianCalendar.weekdayName(0, persian = true))
    }

    @Test
    fun theFullSentenceReadsLikeOne() {
        assertEquals(
            "چهارشنبه 13 آبان 1405",
            PersianCalendar.formatFull(1405, 8, 13, persian = true)
        )
        assertEquals(
            "Wednesday, 13 Aban 1405",
            PersianCalendar.formatFull(1405, 8, 13, persian = false)
        )
        assertEquals("Aban 1405", PersianCalendar.formatMonthYear(1405, 8, persian = false))
        assertEquals("13 Aban", PersianCalendar.formatMonthDay(8, 13, persian = false))
    }

    @Test
    fun theMarkerIsTheOneTheSourceCarries() {
        assertEquals("MARBLE_PERSIAN_CALENDAR_TRUTH_V275", PersianCalendar.MARKER)
    }
}
