package com.marbleng.app.model

import java.util.Calendar
import java.util.TimeZone

/**
 * MARBLE_PERSIAN_CALENDAR_TRUTH_V275 — the Jalali calendar, with holidays that are true.
 *
 * ## What was wrong
 *
 * The complaint was blunt and it was right: the calendar the product once carried **declared
 * 13 Aban an official holiday**. It is not — Student Day is a commemoration, and the official
 * holiday list of the Islamic Republic does not contain it. A calendar that invents a day off
 * is worse than no calendar: a person plans their week around it. The same complaint named the
 * second failure — occasions that were simply absent, so a screen that promised "today" said
 * nothing on most days.
 *
 * ## What this file is
 *
 * The whole truth in one pure object, JVM-testable, no clock and no Android:
 *
 *  - **Names**: the twelve Jalali months and the seven weekdays, Persian and English, one
 *    spelling each, so no screen can invent its own.
 *  - **Official holidays, from a verified table.** The lunar holidays move every solar year,
 *    and computing them is moon-sighting, not arithmetic — an approximation is exactly how
 *    "13 Aban is a holiday" happens in a different dress. So the years this build covers
 *    (1404–1412) carry a per-year table transcribed from a recorded official-calendar dataset
 *    (see `docs/PERSIAN_CALENDAR_TRUTH_V275.md`), including the days two events share
 *    (1405-01-01 was Nowruz *and* Eid al-Fitr). Years beyond the table answer with the
 *    solar-fixed holidays only and say so — [holidayCoverage] — because a guessed lunar date
 *    is the bug this file exists to remove.
 *  - **Fridays are the weekend.** The Islamic Republic's working week rests on Friday alone;
 *    Thursday is a working day, so the calendar does not quietly promote it.
 *  - **Occasions that are not holidays**, marked as such: 13 Aban (Student Day) first among
 *    them, because that is the day the old calendar lied about.
 *
 * [JalaliCalendar] (in `PlanStatus.kt`) remains the arithmetic; this object is the meaning on
 * top of it.
 */
object PersianCalendar {

    const val MARKER: String = "MARBLE_PERSIAN_CALENDAR_TRUTH_V275"

    /** The first Jalali year the embedded lunar-holiday table covers. */
    const val TABLE_FIRST_YEAR: Int = 1404

    /** The last Jalali year the embedded lunar-holiday table covers. */
    const val TABLE_LAST_YEAR: Int = 1412

    // ── Names ───────────────────────────────────────────────────────────────────────────────

    private val MONTHS_FA = listOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )
    private val MONTHS_EN = listOf(
        "Farvardin", "Ordibehesht", "Khordad", "Tir", "Mordad", "Shahrivar",
        "Mehr", "Aban", "Azar", "Dey", "Bahman", "Esfand"
    )

    /** The Iranian week, Saturday first, Friday — the weekend — last. */
    private val WEEKDAYS_FA = listOf(
        "شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه"
    )
    private val WEEKDAYS_EN = listOf(
        "Saturday", "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday"
    )

    fun monthName(month: Int, persian: Boolean): String {
        val index = month - 1
        require(index in MONTHS_FA.indices) { "month $month" }
        return if (persian) MONTHS_FA[index] else MONTHS_EN[index]
    }

    /** The weekday of a Jalali date, 0 = Saturday … 6 = Friday (the weekend). */
    fun weekIndexOf(year: Int, month: Int, day: Int): Int {
        // JDN is anchored so that (jdn % 7) counts 0 = Monday; the Iranian week wants
        // 0 = Saturday, which is that scale shifted by two.
        return (((JalaliCalendar.jdnOf(year, month, day) % 7) + 2) % 7 + 7) % 7
    }

    fun weekdayName(weekIndex: Int, persian: Boolean): String {
        require(weekIndex in WEEKDAYS_FA.indices) { "weekIndex $weekIndex" }
        return if (persian) WEEKDAYS_FA[weekIndex] else WEEKDAYS_EN[weekIndex]
    }

    fun weekdayOf(year: Int, month: Int, day: Int, persian: Boolean): String =
        weekdayName(weekIndexOf(year, month, day), persian)

    /** The Iranian weekend: Friday, and only Friday. Thursday is a working day. */
    const val FRIDAY_INDEX: Int = 6

    fun isFriday(year: Int, month: Int, day: Int): Boolean =
        weekIndexOf(year, month, day) == FRIDAY_INDEX

    // ── Occasions ───────────────────────────────────────────────────────────────────────────

    /**
     * One occasion. [official] is the whole distinction the old calendar erased: an official
     * holiday closes offices; anything else is a commemoration the calendar may show but must
     * never colour as a day off.
     */
    data class Occasion(
        val key: String,
        val labelFa: String,
        val labelEn: String,
        val official: Boolean,
        /** True when the date moves every solar year (a lunar commemoration). */
        val lunar: Boolean
    ) {
        fun label(persian: Boolean): String = if (persian) labelFa else labelEn
    }

    private val NOWRUZ = Occasion("NOWRUZ", "نوروز", "Nowruz", true, false)
    private val REPUBLIC_DAY = Occasion("REPUBLIC_DAY", "روز جمهوری اسلامی", "Islamic Republic Day", true, false)
    private val NATURE_DAY = Occasion("NATURE_DAY", "روز طبیعت (سیزده‌به‌در)", "Nature Day (Sizdah bedar)", true, false)
    private val KHOMEINI = Occasion("KHOMEINI", "رحلت امام خمینی", "Demise of Imam Khomeini", true, false)
    private val KHORDAD15 = Occasion("KHORDAD15", "قیام ۱۵ خرداد", "Khordad 15 uprising", true, false)
    private val REVOLUTION = Occasion("REVOLUTION", "پیروزی انقلاب اسلامی", "Islamic Revolution victory", true, false)
    private val OIL = Occasion("OIL", "روز ملی شدن صنعت نفت", "Oil industry nationalization", true, false)
    private val LAST_DAY = Occasion("LAST_DAY", "آخرین روز سال", "Last day of the year", true, false)

    private val EID_FITR = Occasion("EID_FITR", "عید سعید فطر", "Eid al-Fitr", true, true)
    private val EID_FITR2 = Occasion("EID_FITR2", "تعطیل به مناسبت عید سعید فطر", "Eid al-Fitr holiday", true, true)
    private val IMAM_SADEQ = Occasion("IMAM_SADEQ", "شهادت امام جعفر صادق (ع)", "Martyrdom of Imam Sadeq", true, true)
    private val EID_ADHA = Occasion("EID_ADHA", "عید سعید قربان", "Eid al-Adha", true, true)
    private val EID_GHADIR = Occasion("EID_GHADIR", "عید سعید غدیر خم", "Eid al-Ghadir", true, true)
    private val TASUA = Occasion("TASUA", "تاسوعای حسینی", "Tasua", true, true)
    private val ASHURA = Occasion("ASHURA", "عاشورای حسینی", "Ashura", true, true)
    private val ARBAEEN = Occasion("ARBAEEN", "اربعین حسینی", "Arbaeen", true, true)
    private val PROPHET_DEMISE = Occasion(
        "PROPHET_DEMISE", "رحلت رسول اکرم (ص) و شهادت امام حسن مجتبی (ع)",
        "Demise of the Prophet; martyrdom of Imam Hasan", true, true
    )
    private val IMAM_REZA = Occasion("IMAM_REZA", "شهادت امام رضا (ع)", "Martyrdom of Imam Reza", true, true)
    private val IMAM_ASKARI = Occasion("IMAM_ASKARI", "شهادت امام حسن عسکری (ع)", "Martyrdom of Imam Askari", true, true)
    private val MAWLID = Occasion("MAWLID", "میلاد رسول اکرم (ص) و امام جعفر صادق (ع)", "Mawlid of the Prophet and Imam Sadeq", true, true)
    private val FATIMA = Occasion("FATIMA", "شهادت حضرت فاطمه زهرا (س)", "Martyrdom of Fatima al-Zahra", true, true)
    private val IMAM_ALI_BIRTHDAY = Occasion(
        "IMAM_ALI_BIRTHDAY", "ولادت امام علی (ع) و روز پدر",
        "Birthday of Imam Ali; Father's Day", true, true
    )
    private val MABATH = Occasion("MABATH", "مبعث رسول اکرم (ص)", "Mab'ath of the Prophet", true, true)
    private val IMAM_MAHDI = Occasion(
        "IMAM_MAHDI", "ولادت حضرت قائم (عج) و نیمه شعبان",
        "Birthday of Imam Mahdi; Nisf Sha'ban", true, true
    )
    private val IMAM_ALI_MARTYRDOM = Occasion("IMAM_ALI_MARTYRDOM", "شهادت حضرت علی (ع)", "Martyrdom of Imam Ali", true, true)

    /**
     * The occasions that are **not** holidays — the list the old calendar had almost none of.
     *
     * Every entry here is a widely established national or cultural day, and none of them is an
     * official holiday; [Occasion.official] is false for all of them by construction. 13 Aban
     * leads the list because that is the day the product used to lie about.
     */
    private val SOLAR_OCCASIONS: Map<Int, List<Occasion>> = mapOf(
        // 10 Farvardin — National Persian Gulf Day.
        10 to listOf(Occasion("PERSIAN_GULF_DAY", "روز ملی خلیج فارس", "National Persian Gulf Day", false, false)),
        // 29 Farvardin — Army Day.
        101 to listOf(Occasion("ARMY_DAY", "روز ارتش", "Army Day", false, false)),
        // 12 Ordibehesht — Teacher's Day (martyrdom of Motahhari).
        212 to listOf(Occasion("TEACHERS_DAY", "روز معلم", "Teacher's Day", false, false)),
        // 20 Mehr — Hafez Day.
        720 to listOf(Occasion("HAFEZ_DAY", "روز بزرگداشت حافظ", "Hafez Day", false, false)),
        // 13 Aban — Student Day. NOT an official holiday; the calendar must never mark it as one.
        813 to listOf(Occasion("STUDENT_DAY", "روز دانش‌آموز", "Student Day", false, false)),
        // 30 Azar — Yalda Night.
        930 to listOf(Occasion("YALDA", "شب یلدا", "Yalda Night", false, false))
    )

    /** Keyed by a packed `month * 100 + day`. */
    private val OCCASIONS_BY_KEY: Map<String, Occasion> = listOf(
        NOWRUZ, REPUBLIC_DAY, NATURE_DAY, KHOMEINI, KHORDAD15, REVOLUTION, OIL, LAST_DAY,
        EID_FITR, EID_FITR2, IMAM_SADEQ, EID_ADHA, EID_GHADIR, TASUA, ASHURA, ARBAEEN,
        PROPHET_DEMISE, IMAM_REZA, IMAM_ASKARI, MAWLID, FATIMA, IMAM_ALI_BIRTHDAY, MABATH,
        IMAM_MAHDI, IMAM_ALI_MARTYRDOM
    ).associateBy { it.key }

    // ── The verified holiday table, 1404–1412 ────────────────────────────────────────────────
    //
    // Format: one block per year, `year:MMDD:KEY+KEY;MMDD:KEY|year:…`, transcribed from the
    // recorded official-calendar dataset (see docs/PERSIAN_CALENDAR_TRUTH_V275.md). A date that
    // carries two keys (1405-01-01: NOWRUZ+EID_FITR) was two holidays on one day; both labels
    // are shown. Lunar events appear here, never computed — the table *is* the moon-sighting.
    private const val HOLIDAY_TABLE: String =
        "1404:0101:NOWRUZ;0102:NOWRUZ+IMAM_ALI_MARTYRDOM;0103:NOWRUZ;0104:NOWRUZ;0111:EID_FITR;" +
            "0112:REPUBLIC_DAY+EID_FITR2;0113:NATURE_DAY;0204:IMAM_SADEQ;0314:KHOMEINI;0315:KHORDAD15;" +
            "0317:EID_ADHA;0325:EID_GHADIR;0414:TASUA;0415:ASHURA;0524:ARBAEEN;0601:PROPHET_DEMISE;" +
            "0610:IMAM_ASKARI;0619:MAWLID;0904:FATIMA;1013:IMAM_ALI_BIRTHDAY;1027:MABATH;1115:IMAM_MAHDI;" +
            "1122:REVOLUTION;1220:IMAM_ALI_MARTYRDOM;1229:OIL" +
            "|1405:0101:NOWRUZ+EID_FITR;0102:NOWRUZ+EID_FITR2;0103:NOWRUZ;0104:NOWRUZ;0112:REPUBLIC_DAY;" +
            "0113:NATURE_DAY;0125:IMAM_SADEQ;0307:EID_ADHA;0314:KHOMEINI;0315:KHORDAD15+EID_GHADIR;" +
            "0404:TASUA;0405:ASHURA;0513:ARBAEEN;0521:PROPHET_DEMISE;0523:IMAM_REZA;0531:IMAM_ASKARI;" +
            "0609:MAWLID;0823:FATIMA;1002:IMAM_ALI_BIRTHDAY;1016:MABATH;1104:IMAM_MAHDI;1122:REVOLUTION;" +
            "1210:IMAM_ALI_MARTYRDOM;1219:EID_FITR;1220:EID_FITR2;1229:OIL" +
            "|1406:0101:NOWRUZ;0102:NOWRUZ;0103:NOWRUZ;0104:NOWRUZ;0112:REPUBLIC_DAY;0113:NATURE_DAY;" +
            "0114:IMAM_SADEQ;0227:EID_ADHA;0304:EID_GHADIR;0314:KHOMEINI;0315:KHORDAD15;0325:TASUA;" +
            "0326:ASHURA;0503:ARBAEEN;0511:PROPHET_DEMISE;0520:IMAM_ASKARI;0529:MAWLID;0812:FATIMA;" +
            "0921:IMAM_ALI_BIRTHDAY;1005:MABATH;1023:IMAM_MAHDI;1122:REVOLUTION;1129:IMAM_ALI_MARTYRDOM;" +
            "1208:EID_FITR;1209:EID_FITR2;1229:OIL" +
            "|1407:0101:NOWRUZ;0102:NOWRUZ;0103:NOWRUZ+IMAM_SADEQ;0104:NOWRUZ;0112:REPUBLIC_DAY;" +
            "0113:NATURE_DAY;0217:EID_ADHA;0225:EID_GHADIR;0314:KHOMEINI+TASUA;0315:KHORDAD15+ASHURA;" +
            "0424:ARBAEEN;0501:PROPHET_DEMISE;0510:IMAM_ASKARI;0519:MAWLID;0801:FATIMA;0911:IMAM_ALI_BIRTHDAY;" +
            "0925:MABATH;1012:IMAM_MAHDI;1118:IMAM_ALI_MARTYRDOM;1122:REVOLUTION;1127:EID_FITR;1128:EID_FITR2;" +
            "1221:IMAM_SADEQ;1229:OIL" +
            "|1408:0101:NOWRUZ;0102:NOWRUZ;0103:NOWRUZ;0104:NOWRUZ;0112:REPUBLIC_DAY;0113:NATURE_DAY;" +
            "0206:EID_ADHA;0214:EID_GHADIR;0303:TASUA;0304:ASHURA;0314:KHOMEINI;0315:KHORDAD15;0413:ARBAEEN;" +
            "0421:PROPHET_DEMISE;0423:IMAM_REZA;0431:IMAM_ASKARI;0509:MAWLID;0721:FATIMA;0830:IMAM_ALI_BIRTHDAY;" +
            "0914:MABATH;1002:IMAM_MAHDI;1107:IMAM_ALI_MARTYRDOM;1117:EID_FITR;1118:EID_FITR2;1122:REVOLUTION;" +
            "1211:IMAM_SADEQ;1229:OIL;1230:LAST_DAY" +
            "|1409:0101:NOWRUZ;0102:NOWRUZ;0103:NOWRUZ;0104:NOWRUZ;0112:REPUBLIC_DAY;0113:NATURE_DAY;" +
            "0125:EID_ADHA;0202:EID_GHADIR;0222:TASUA;0223:ASHURA;0314:KHOMEINI;0315:KHORDAD15;0401:ARBAEEN;" +
            "0409:PROPHET_DEMISE;0411:IMAM_REZA;0419:IMAM_ASKARI;0428:MAWLID;0710:FATIMA;0819:IMAM_ALI_BIRTHDAY;" +
            "0903:MABATH;0920:IMAM_MAHDI;1026:IMAM_ALI_MARTYRDOM;1105:EID_FITR;1106:EID_FITR2;1122:REVOLUTION;" +
            "1129:IMAM_SADEQ;1229:OIL" +
            "|1410:0101:NOWRUZ;0102:NOWRUZ;0103:NOWRUZ;0104:NOWRUZ;0112:REPUBLIC_DAY;0113:NATURE_DAY;" +
            "0114:EID_ADHA;0122:EID_GHADIR;0212:TASUA;0213:ASHURA;0314:KHOMEINI;0315:KHORDAD15;0321:ARBAEEN;" +
            "0329:PROPHET_DEMISE;0331:IMAM_REZA;0408:IMAM_ASKARI;0417:MAWLID;0630:FATIMA;0808:IMAM_ALI_BIRTHDAY;" +
            "0822:MABATH;0910:IMAM_MAHDI;1015:IMAM_ALI_MARTYRDOM;1025:EID_FITR;1026:EID_FITR2;1119:IMAM_SADEQ;" +
            "1122:REVOLUTION;1229:OIL" +
            "|1411:0101:NOWRUZ;0102:NOWRUZ;0103:NOWRUZ;0104:NOWRUZ+EID_ADHA;0112:REPUBLIC_DAY+EID_GHADIR;" +
            "0113:NATURE_DAY;0201:TASUA;0202:ASHURA;0310:ARBAEEN;0314:KHOMEINI;0315:KHORDAD15;" +
            "0318:PROPHET_DEMISE;0320:IMAM_REZA;0328:IMAM_ASKARI;0406:MAWLID;0619:FATIMA;0728:IMAM_ALI_BIRTHDAY;" +
            "0812:MABATH;0829:IMAM_MAHDI;1005:IMAM_ALI_MARTYRDOM;1014:EID_FITR;1015:EID_FITR2;1108:IMAM_SADEQ;" +
            "1122:REVOLUTION;1222:EID_ADHA;1229:OIL" +
            "|1412:0101:NOWRUZ+EID_GHADIR;0102:NOWRUZ;0103:NOWRUZ;0104:NOWRUZ;0112:REPUBLIC_DAY;0113:NATURE_DAY;" +
            "0122:TASUA;0123:ASHURA;0231:ARBAEEN;0308:PROPHET_DEMISE;0314:KHOMEINI;0315:KHORDAD15;" +
            "0317:IMAM_ASKARI;0326:MAWLID;0608:FATIMA;0717:IMAM_ALI_BIRTHDAY;0801:MABATH;0818:IMAM_MAHDI;" +
            "0924:IMAM_ALI_MARTYRDOM;1004:EID_FITR;1005:EID_FITR2;1028:IMAM_SADEQ;1122:REVOLUTION;1212:EID_ADHA;" +
            "1220:EID_GHADIR;1229:OIL;1230:LAST_DAY"

    /** year → (packed MMDD → occasion keys). Parsed once; immutable afterwards. */
    private val tableByYear: Map<Int, Map<Int, List<Occasion>>> by lazy {
        val parsed = mutableMapOf<Int, MutableMap<Int, List<Occasion>>>()
        for (block in HOLIDAY_TABLE.split('|')) {
            val yearPart = block.substringBefore(':')
            val year = yearPart.toIntOrNull() ?: continue
            val days = parsed.getOrPut(year) { mutableMapOf() }
            for (entry in block.substringAfter(':', "").split(';')) {
                if (entry.isBlank()) continue
                val datePart = entry.substringBefore(':')
                val keysPart = entry.substringAfter(':', "")
                val packed = datePart.toIntOrNull() ?: continue
                val occasions = keysPart.split('+')
                    .mapNotNull { OCCASIONS_BY_KEY[it.trim()] }
                if (occasions.isNotEmpty()) days[packed] = occasions
            }
        }
        parsed
    }

    /**
     * The solar-fixed official holidays, valid for every Jalali year including the ones beyond
     * the table: Nowruz (1–4 Farvardin), Islamic Republic Day (12 Farvardin), Nature Day
     * (13 Farvardin), the demise of Imam Khomeini (14 Khordad), the Khordad 15 uprising,
     * the Revolution (22 Bahman) and the oil nationalization (29 Esfand).
     */
    private val SOLAR_HOLIDAYS: Map<Int, List<Occasion>> = mapOf(
        101 to listOf(NOWRUZ), 102 to listOf(NOWRUZ), 103 to listOf(NOWRUZ), 104 to listOf(NOWRUZ),
        112 to listOf(REPUBLIC_DAY), 113 to listOf(NATURE_DAY),
        314 to listOf(KHOMEINI), 315 to listOf(KHORDAD15),
        1122 to listOf(REVOLUTION), 1229 to listOf(OIL)
    )

    /** Whether the embedded lunar table covers [year]; the UI says so when it does not. */
    fun holidayCoverage(year: Int): Boolean = year in TABLE_FIRST_YEAR..TABLE_LAST_YEAR

    /**
     * Every occasion on one Jalali day — official holidays and commemorations together, in a
     * stable order (holidays first, then the non-holiday occasions).
     */
    fun occasionsOn(year: Int, month: Int, day: Int): List<Occasion> {
        if (month !in 1..12 || day < 1 || day > JalaliCalendar.daysInMonth(year, month)) return emptyList()
        val packed = month * 100 + day
        val holidays = if (holidayCoverage(year)) {
            tableByYear[year]?.get(packed).orEmpty()
        } else {
            SOLAR_HOLIDAYS[packed].orEmpty()
        }
        val commemorations = SOLAR_OCCASIONS[packed].orEmpty()
        return holidays + commemorations
    }

    /**
     * Whether the day is an official holiday: a table (or solar-fixed) holiday, or a Friday.
     *
     * This is the one function the old calendar got wrong, so it is the one the tests pin
     * hardest: 13 Aban is **not** here — it has an occasion (Student Day) and no holiday —
     * while Nowruz, 22 Bahman, the lunar table's Eid days and every Friday are.
     */
    fun isOfficialHoliday(year: Int, month: Int, day: Int): Boolean =
        isFriday(year, month, day) ||
            occasionsOn(year, month, day).any { it.official }

    // ── Today, and what is coming ────────────────────────────────────────────────────────────

    /** What a Home "today" line prints: the date, its occasions, and the next day off. */
    data class Today(
        val year: Int,
        val month: Int,
        val day: Int,
        val weekIndex: Int,
        val occasions: List<Occasion>,
        val holiday: Boolean,
        val lunarCoverage: Boolean
    ) {
        val packedDate: Int get() = month * 100 + day
    }

    fun today(nowMs: Long, zone: TimeZone = TimeZone.getDefault()): Today {
        val date = JalaliCalendar.of(nowMs, zone)
        return Today(
            year = date.year,
            month = date.month,
            day = date.day,
            weekIndex = weekIndexOf(date.year, date.month, date.day),
            occasions = occasionsOn(date.year, date.month, date.day),
            holiday = isOfficialHoliday(date.year, date.month, date.day),
            lunarCoverage = holidayCoverage(date.year)
        )
    }

    /** One upcoming occasion worth showing: the soonest non-past day that carries one. */
    data class Upcoming(
        val year: Int,
        val month: Int,
        val day: Int,
        val daysAway: Int,
        val occasion: Occasion,
        val holiday: Boolean
    )

    /**
     * The next [limit] occasion-bearing days after today (today excluded), nearest first.
     *
     * Scans forward across month and year boundaries using the calendar's own day counts, so
     * it needs no Gregorian arithmetic and no clock. Holidays and commemorations both count —
     * the caller decides what to show; [Upcoming.holiday] says which kind each one is.
     */
    fun upcoming(fromYear: Int, fromMonth: Int, fromDay: Int, limit: Int = 3): List<Upcoming> {
        if (limit <= 0) return emptyList()
        val found = mutableListOf<Upcoming>()
        var year = fromYear
        var month = fromMonth
        var day = fromDay
        var scanned = 0
        val horizon = 400
        while (found.size < limit && scanned < horizon) {
            val monthLength = JalaliCalendar.daysInMonth(year, month)
            if (monthLength == 0) break
            day += 1
            if (day > monthLength) {
                day = 1
                month += 1
                if (month > 12) {
                    month = 1
                    year += 1
                }
            }
            scanned += 1
            val occasions = occasionsOn(year, month, day)
            if (occasions.isEmpty()) continue
            found += occasions.map { occasion ->
                Upcoming(
                    year = year,
                    month = month,
                    day = day,
                    daysAway = scanned,
                    occasion = occasion,
                    holiday = occasion.official || isFriday(year, month, day)
                )
            }
            if (found.size >= limit) break
        }
        return found.take(limit)
    }

    // ── Formatting ──────────────────────────────────────────────────────────────────────────

    /**
     * "دوشنبه ۱۷ آبان ۱۴۰۵" / "Monday, 17 Aban 1405" — the full sentence the Home line and the
     * sheet header print. Digits are plain Latin; the UI shapes them for Persian.
     */
    fun formatFull(year: Int, month: Int, day: Int, persian: Boolean): String {
        val weekday = weekdayOf(year, month, day, persian)
        val monthLabel = monthName(month, persian)
        return if (persian) {
            "$weekday $day $monthLabel $year"
        } else {
            "$weekday, $day $monthLabel $year"
        }
    }

    /** "17 Aban" / "۱۷ آبان" without the weekday or year — the month-grid header. */
    fun formatMonthDay(month: Int, day: Int, persian: Boolean): String {
        val monthLabel = monthName(month, persian)
        return if (persian) "$day $monthLabel" else "$day $monthLabel"
    }

    /** The month-and-year header of a grid: "آبان ۱۴۰۵" / "Aban 1405". */
    fun formatMonthYear(year: Int, month: Int, persian: Boolean): String =
        "${monthName(month, persian)} $year"

    /** The Gregorian date a Jalali date maps to, for the secondary line: "2026-11-04". */
    fun gregorianLabel(year: Int, month: Int, day: Int): String {
        val jdn = JalaliCalendar.jdnOf(year, month, day)
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.timeInMillis = (jdn - 2_440_588L) * 86_400_000L
        return String.format(
            java.util.Locale.US,
            "%04d-%02d-%02d",
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }
}
