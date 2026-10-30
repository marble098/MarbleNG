package com.marbleng.app.model

/**
 * MARBLE_LIVE_TICKER_V249 — the arithmetic of a clock that is watched while it runs.
 *
 * The Home pill's second line used to be one string: `Protected · Uptime 00:10:01`. The whole line
 * was the target of one `AnimatedContent`, so every second the *entire* subtitle — the state word,
 * the separator, the label and all eight clock characters — slid out and back in again. A reading
 * that changes once a second is not a headline; it is an instrument, and an instrument moves only
 * the part that moved.
 *
 * This object is the pure half of that decision:
 *
 *  - [cells] is the clock as the reader sees it, one character per cell, in reading order;
 *  - [rolling] answers "which cells move on this tick?" — positionally, so `00:10:01 → 00:10:02`
 *    rolls exactly one cell and the five that did not change stay perfectly still;
 *  - [separators] are the colons: they never change value, so instead of rolling they pulse, which
 *    is the only honest way to show that a clock with a frozen minute hand is still alive;
 *  - [ticked] is the one-boolean answer used for the pulse phase and for nothing else.
 *
 * No Android type, so `UptimeTickerV249Test` pins every rule on the host JVM.
 */
object UptimeTicker {

    const val MARKER: String = "MARBLE_LIVE_TICKER_V249"

    /** How far a changed digit travels, as a fraction of its own cell height. */
    const val ROLL_TRAVEL_FRACTION: Float = 0.85f

    /** The colon's dim half of the pulse. Soft, because it runs once a second, forever. */
    const val SEPARATOR_DIM_ALPHA: Float = 0.45f

    /** A clock is drawn left-to-right even inside a Persian paragraph: `۰۰:۱۰:۰۱` never reverses. */
    const val CLOCK_IS_LEFT_TO_RIGHT: Boolean = true

    /** Characters that separate fields instead of counting them. */
    private val SEPARATOR_CHARS: Set<Char> = setOf(':', '.', '/', '٫', '،', '-')

    fun isSeparator(char: Char): Boolean = char in SEPARATOR_CHARS

    /** One cell per character, trimmed: a clock never carries padding into the drawing. */
    fun cells(clock: String): List<Char> = clock.trim().toList()

    /** The cell indexes that are separators — they pulse instead of rolling. */
    fun separators(clock: String): List<Int> =
        cells(clock).mapIndexedNotNull { index, char -> if (isSeparator(char)) index else null }

    /** True when the reading moved at all. */
    fun ticked(previous: String, next: String): Boolean = previous.trim() != next.trim()

    /**
     * Which cells roll on this tick, positionally.
     *
     * Same shape (the usual `HH:MM:SS` → `HH:MM:SS` second) means only the digits that really
     * changed move. A different shape — the first hour of a session appearing, a reading that lost
     * a field — marks every cell, because a positional diff across two shapes would be a lie.
     */
    fun rolling(previous: String, next: String): List<Boolean> {
        val from = cells(previous)
        val to = cells(next)
        if (from.size != to.size) return List(to.size) { true }
        return to.mapIndexed { index, char -> from[index] != char }
    }

    /**
     * How many cells move on a tick: the number the UI uses to decide whether the roll is worth
     * its own spring (one digit) or whether the whole reading should cross-fade (all of them).
     */
    fun rollingCount(previous: String, next: String): Int =
        rolling(previous, next).count { it }

    /** True when the whole reading changed shape and one shared transition reads better than eight. */
    fun reshaped(previous: String, next: String): Boolean =
        cells(previous).size != cells(next).size

    /**
     * The value of one digit character, in any of the scripts this app can print a clock in.
     *
     * A Persian interface shapes the reading with `۰۱۲۳۴۵۶۷۸۹`, and `String.toInt` reads ASCII
     * only — so a ticker that parsed its own digits with `toIntOrNull` would work in English and
     * silently stop pulsing in Persian. `-1` means "this character is not a digit in any script".
     */
    fun digitValue(char: Char): Int = when (char) {
        in '0'..'9' -> char - '0'
        in '\u06F0'..'\u06F9' -> char - '\u06F0'  // Persian (extended Arabic-Indic)
        in '\u0660'..'\u0669' -> char - '\u0660'  // Arabic-Indic
        else -> -1
    }

    /**
     * The seconds field of a reading, or `-1` when there is not one.
     *
     * Only the last field is read, and only when every character in it is a digit: the clock's job
     * is to be right, not to guess what a malformed string meant.
     */
    fun secondsOf(clock: String): Int {
        val field = clock.trim().split(':', '.', '\u066B', '\u06D4').lastOrNull()?.trim().orEmpty()
        if (field.isEmpty()) return -1
        var value = 0
        for (char in field) {
            val digit = digitValue(char)
            if (digit < 0) return -1
            value = value * 10 + digit
        }
        return value
    }

    /**
     * Is the separator lit on this reading?
     *
     * The pulse is *derived* from the clock rather than driven by a timer, for three reasons: it
     * cannot drift out of step with the reading it belongs to, it costs no coroutine and no infinite
     * transition, and a restored or re-composed ticker lands on the right phase immediately instead
     * of starting its own private blink. Even seconds are lit, odd seconds are dim, so the colon
     * alternates exactly once a second — which is the only honest way for a clock whose minute hand
     * is frozen to say "I am still counting".
     */
    fun pulseLit(clock: String): Boolean {
        val seconds = secondsOf(clock)
        return seconds < 0 || seconds % 2 == 0
    }
}
