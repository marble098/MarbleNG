package com.marbleng.app.model

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

/**
 * MARBLE_SERVER_PLAN_CARDS_V260 — what a subscription's *plan* is doing, as one pure value.
 *
 * The Servers page has always carried the two numbers a paid plan is judged by — the traffic it has
 * spent and the day it stops — but it printed them as an unformatted byte pair inside a one-line
 * subtitle, and it never printed the expiry at all. A reader with four libraries could not answer
 * "which of these is running out?" without opening each one.
 *
 * So the arithmetic lives here, on the JVM, and the card only draws it:
 *
 *  - [usedBytes] / [totalBytes] with a clamped fraction, so a provider that reports 101 % never
 *    draws a bar that runs backwards;
 *  - [remainingDays] from the expiry instant, rolled to *calendar days* rather than to a duration,
 *    because "expires tomorrow" is a calendar fact and 23 hours is not;
 *  - one [tone] that both the bar and the chip read, so the colour cannot argue with the number;
 *  - one [expiryLabel] that says the distance in words ("in 6 days") and one [expiryDate] that says
 *    the day itself, in the calendar the reader thinks in (Jalali for Persian, Gregorian for
 *    English) — the same dual-calendar rule the rest of the product follows.
 *
 * Nothing here is Android, and nothing here reads a clock: [of] takes `nowMs`, so every branch is
 * provable without a device.
 */
object PlanStatus {

    const val MARKER: String = "MARBLE_SERVER_PLAN_CARDS_V260"

    /** The used share at which a plan stops being comfortable and starts being a warning. */
    const val WARN_FRACTION: Float = 0.75f

    /** The used share at which a plan is effectively gone. */
    const val DANGER_FRACTION: Float = 0.92f

    /** The days-left threshold for the same two tones, in whole days. */
    const val WARN_DAYS: Int = 7
    const val DANGER_DAYS: Int = 2

    /** How a plan's health reads, from the calmest to the loudest. */
    enum class Tone { CALM, WARN, DANGER, UNKNOWN }

    /**
     * One subscription's plan, resolved.
     *
     * [hasQuota] and [hasExpiry] are separate because the two are independent: a plan can cap
     * traffic and never expire, or expire and never cap. A card must not draw a 0 % bar for a plan
     * that has no ceiling at all — that reads as "you have used nothing", which is a different (and
     * false) statement.
     */
    data class Status(
        val usedBytes: Long = 0L,
        val totalBytes: Long = 0L,
        val remainingBytes: Long = 0L,
        val usageFraction: Float = 0f,
        val expireAtMs: Long = 0L,
        val remainingDays: Int = Int.MIN_VALUE,
        val tone: Tone = Tone.UNKNOWN,
        val hasQuota: Boolean = false,
        val hasExpiry: Boolean = false,
        val expired: Boolean = false,
        val updatedAtMs: Long = 0L
    ) {
        /** True while there is nothing at all to print — the card then draws no plan row. */
        val isEmpty: Boolean get() = !hasQuota && !hasExpiry && updatedAtMs <= 0L
    }

    /**
     * The numbers [Subscription] carries, resolved against `nowMs`.
     *
     * `expireAtMs` is the epoch instant the provider reported; a provider that reports `0` means
     * "no expiry", not "expired in 1970", so a non-positive instant is treated as absent.
     */
    fun of(
        subscription: Subscription,
        nowMs: Long,
        zone: TimeZone = TimeZone.getDefault()
    ): Status {
        val used = (subscription.uploadBytes + subscription.downloadBytes).coerceAtLeast(0L)
        val total = subscription.totalBytes.coerceAtLeast(0L)
        val expire = subscription.expireAt.coerceAtLeast(0L)
        val fraction = fractionOf(used, total)
        val days = if (expire > 0L) daysBetween(nowMs, expire, zone) else Int.MIN_VALUE
        val hasQuota = total > 0L
        val hasExpiry = expire > 0L
        val expired = hasExpiry && days <= 0
        return Status(
            usedBytes = used,
            totalBytes = total,
            remainingBytes = if (hasQuota) (total - used).coerceAtLeast(0L) else 0L,
            usageFraction = fraction,
            expireAtMs = expire,
            remainingDays = days,
            tone = toneOf(fraction, days, hasQuota, hasExpiry),
            hasQuota = hasQuota,
            hasExpiry = hasExpiry,
            expired = expired,
            updatedAtMs = subscription.updatedAt.coerceAtLeast(0L)
        )
    }

    /** A plan that is not a subscription (the "All sources" card): no quota, no expiry. */
    fun unknown(nowMs: Long = 0L): Status = Status()

    /** Consumed share, clamped; a plan without a ceiling has no share at all. */
    fun fractionOf(usedBytes: Long, totalBytes: Long): Float {
        if (totalBytes <= 0L) return 0f
        val used = usedBytes.coerceAtLeast(0L)
        return (used.toDouble() / totalBytes.toDouble()).coerceIn(0.0, 1.0).toFloat()
    }

    /**
     * Whole calendar days from `fromMs` to `toMs` in [zone].
     *
     * Calendar days rather than a duration: a plan that stops at 23:59 tomorrow is "tomorrow" at
     * noon, not "1.5 days", and the card is a calendar object, not a stopwatch. Rounded up, so a
     * plan with eleven hours left says `today` and not `0 days`; a past instant is negative, and the
     * caller prints it as expired.
     */
    fun daysBetween(fromMs: Long, toMs: Long, zone: TimeZone = TimeZone.getDefault()): Int {
        val start = startOfDay(fromMs, zone)
        val end = startOfDay(toMs, zone)
        return Math.floorDiv(end - start, DAY_MS.toLong()).toInt()
    }

    private fun startOfDay(ms: Long, zone: TimeZone): Long {
        val calendar = Calendar.getInstance(zone).apply {
            timeInMillis = ms
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    /**
     * The one tone both the bar and the chip read.
     *
     * A plan is as loud as its loudest problem: full quota or almost-expired both mean "act now",
     * and an unused plan expiring in a year stays calm. When neither axis is known the card says so
     * instead of guessing [Tone.CALM].
     */
    fun toneOf(
        usageFraction: Float,
        remainingDays: Int,
        hasQuota: Boolean,
        hasExpiry: Boolean
    ): Tone {
        if (!hasQuota && !hasExpiry) return Tone.UNKNOWN
        if (hasExpiry && remainingDays <= 0) return Tone.DANGER
        if (hasQuota && usageFraction >= DANGER_FRACTION) return Tone.DANGER
        if (hasExpiry && remainingDays <= DANGER_DAYS) return Tone.DANGER
        if (hasQuota && usageFraction >= WARN_FRACTION) return Tone.WARN
        if (hasExpiry && remainingDays <= WARN_DAYS) return Tone.WARN
        return Tone.CALM
    }

    /**
     * "62 %" — the share of the ceiling a plan has spent.
     *
     * Whole percent, and floored at 1 % once anything at all has been used: a bar that reads `0 %`
     * beside a visible sliver of fill is the kind of disagreement a reader notices.
     */
    fun usagePercentLabel(fraction: Float, usedBytes: Long): String {
        if (fraction <= 0f && usedBytes <= 0L) return "0%"
        val percent = (fraction * 100f).roundToInt().coerceAtLeast(if (usedBytes > 0L) 1 else 0)
        return "${percent.coerceAtMost(100)}%"
    }

    /**
     * "in 6 days" / "tomorrow" / "today" / "3 days ago" / "no expiry" — the distance in words.
     *
     * These are English keys `trx` translates; the caller shapes the digits for Persian.
     */
    fun expiryLabel(days: Int): String = when {
        days == Int.MIN_VALUE -> "No expiry"
        days < -1 -> "${-days} days ago"
        days == -1 -> "Yesterday"
        days == 0 -> "Today"
        days == 1 -> "Tomorrow"
        else -> "in $days days"
    }

    /**
     * The calendar the reader thinks in: Jalali for Persian, Gregorian for English.
     *
     * `2026-11-01` / `1405-08-10`, with the year printed in full so two libraries expiring years
     * apart cannot be confused for each other.
     */
    fun expiryDate(epochMs: Long, persian: Boolean, zone: TimeZone = TimeZone.getDefault()): String {
        if (epochMs <= 0L) return ""
        return if (persian) {
            val j = JalaliCalendar.of(epochMs, zone)
            String.format(Locale.US, "%04d-%02d-%02d", j.year, j.month, j.day)
        } else {
            val calendar = Calendar.getInstance(zone).apply { timeInMillis = epochMs }
            String.format(
                Locale.US,
                "%04d-%02d-%02d",
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH)
            )
        }
    }

    /** "14.20 GB of 50.00 GB" — the pair the bar is drawn from. */
    fun quotaPairLabel(usedBytes: Long, totalBytes: Long): String {
        val used = formatBytes(usedBytes)
        return if (totalBytes > 0L) "$used / ${formatBytes(totalBytes)}" else used
    }

    /**
     * Bytes in the product's own units, one decimal from the megabyte up.
     *
     * Deliberately a local copy of the shape `UiFormatters.formatBytes` prints, kept in the model so
     * `PlanStatusTest` can pin it on the host JVM — the two produce the same string, and a test
     * asserts it so they cannot drift.
     */
    fun formatBytes(bytes: Long): String {
        val safe = bytes.coerceAtLeast(0L)
        return when {
            safe < 1_024L -> "$safe B"
            safe < 1_048_576L -> String.format(Locale.US, "%.1f KB", safe / 1_024.0)
            safe < 1_073_741_824L -> String.format(Locale.US, "%.1f MB", safe / 1_048_576.0)
            else -> String.format(Locale.US, "%.2f GB", safe / 1_073_741_824.0)
        }
    }

    const val DAY_MS: Int = 86_400_000
}

/**
 * MARBLE_SERVER_PLAN_CARDS_V260 — Jalali (Solar Hijri) date arithmetic, without a dependency.
 *
 * The Servers card prints a subscription's expiry day, and a Persian reader reads that day in the
 * Jalali calendar. The product has no date library and does not want one for twelve lines of
 * arithmetic, so this is the well-known Borkowski/Khayyam algorithm — the same one the Persian
 * calendar's own libraries implement — written out with the leap-year breaks it needs.
 *
 * It is a `object` of pure functions: no clock, no locale, no Android. `PlanStatusTest` proves it
 * against the Nowruz of four years and against dates that must round-trip.
 */
object JalaliCalendar {

    /** The 33-year leap breaks of the 2820-year cycle arithmetic used by the Persian calendar. */
    private val BREAKS = intArrayOf(
        -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181,
        1210, 1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178
    )

    data class Date(val year: Int, val month: Int, val day: Int)

    fun of(epochMs: Long, zone: TimeZone = TimeZone.getDefault()): Date = ofJdn(jdnOf(epochMs, zone))

    fun of(year: Int, month: Int, day: Int): Date {
        val jdn = jdnOfGregorian(year, month, day)
        return ofJdn(jdn)
    }

    private fun jdnOf(epochMs: Long, zone: TimeZone): Int {
        val calendar = Calendar.getInstance(zone).apply { timeInMillis = epochMs }
        return jdnOfGregorian(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    /** Julian day number of a Gregorian date (the standard integer form, no fractions). */
    private fun jdnOfGregorian(gy: Int, gm: Int, gd: Int): Int {
        val d = div((gy + div(gm - 8, 6) + 100_100) * 1461, 4) +
            div(153 * mod(gm + 9, 12) + 2, 5) + gd - 34_840_408
        return d - div(div(gy + 100_100 + div(gm - 8, 6), 100) * 3, 4) + 752
    }

    private fun ofJdn(jdn: Int): Date {
        val gy = gregorianOfJdn(jdn).first
        var jy = gy - 621
        val leap = jalCal(jy)
        val jdn1f = jdnOfGregorian(gy, 3, leap.march)
        var k = jdn - jdn1f
        if (k >= 0) {
            if (k <= 185) return Date(jy, 1 + div(k, 31), mod(k, 31) + 1)
            k -= 186
        } else {
            jy -= 1
            k += 179
            if (jalCal(jy).leap == 1) k += 1
        }
        return Date(jy, 7 + div(k, 30), mod(k, 30) + 1)
    }

    private fun gregorianOfJdn(jdn: Int): Triple<Int, Int, Int> {
        var j = 4 * jdn + 139_361_631
        j += div(div(4 * jdn + 183_187_720, 146_097) * 3, 4) * 4 - 3908
        val i = div(mod(j, 1461), 4) * 5 + 308
        val gd = div(mod(i, 153), 5) + 1
        val gm = mod(div(i, 153), 12) + 1
        val gy = div(j, 1461) - 100_100 + div(8 - gm, 6)
        return Triple(gy, gm, gd)
    }

    private data class Leap(val leap: Int, val march: Int)

    private fun jalCal(jy: Int): Leap {
        val gy = jy + 621
        var leapJ = -14
        var jp = BREAKS[0]
        var jump = 0
        for (index in 1 until BREAKS.size) {
            val jm = BREAKS[index]
            jump = jm - jp
            if (jy < jm) break
            leapJ += div(jump, 33) * 8 + div(mod(jump, 33), 4)
            jp = jm
        }
        var n = jy - jp
        leapJ += div(n, 33) * 8 + div(mod(n, 33) + 3, 4)
        if (mod(jump, 33) == 4 && jump - n == 4) leapJ += 1
        val leapG = div(gy, 4) - div((div(gy, 100) + 1) * 3, 4) - 150
        val march = 20 + leapJ - leapG
        if (jump - n < 6) n = n - jump + div(jump + 4, 33) * 33
        var leap = mod(mod(n + 1, 33) - 1, 4)
        if (leap == -1) leap = 4
        return Leap(leap, march)
    }

    /** Truncating integer division — Kotlin's own `/` — named so the calendar reads like the paper. */
    private fun div(a: Int, b: Int): Int = a / b

    private fun mod(a: Int, b: Int): Int = a - div(a, b) * b
}
