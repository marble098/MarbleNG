package com.marbleng.app.model

/**
 * MARBLE_PERSIAN_JOIN_V249 — the one orthography rule every Persian sentence in the app obeys.
 *
 * The report was one word: «نسخهها». A Persian plural written `stem + ها` reads correctly only
 * when the stem's last letter cannot join to the `ه` that follows it. `ر` never joins, so
 * `سرورها` has always been right; `ه` does join, so `نسخهها` arrives on screen as one unreadable
 * blob where the reader expects `نسخه‌ها`.
 *
 * The separator is the **half-space** (ZWNJ, `U+200C`) — not a full space. It is what the tree
 * already used for its correct plurals (`تست‌ها`, `برنامه‌ها`, `هشدارها`) and for its verbs
 * (`می‌شود`, 199 of them), it is what Persian publishing uses, and it is what keeps `سرور ها`
 * (a full space where none belongs) off the screen. One rule, one separator, whole tree.
 *
 * Three families are joined back up, each by an explicit decision rather than a guess:
 *
 *  - **plural `ها`** — half-space, but only behind a letter that would otherwise join to it;
 *  - **indefinite `ه + ای`** — `لحظه‌ای`, decided per stem, because `برنامههای` (برنامه + های)
 *    and `لحظهای` (لحظه + ای) are the same three letters on the page and only one of them splits;
 *  - **the verbal prefix `می`** — `می‌شه`, anchored at the start of a word, because `همیشه`
 *    contains the same four letters and is not a verb.
 *
 * The tables below deliberately carry the *wrong* spellings on their left side: they are the
 * patterns this rule exists to find. `tools/persian-normalize.py` is the same rule as a build-time
 * sweep and skips this file for that reason.
 *
 * Pure Kotlin with no Android import, so `PersianOrthographyV249Test` pins every rule — including
 * the words that must never be touched — on the host JVM. [normalize] is idempotent: the shipped
 * dictionary is already normalized, so a second pass is a no-op that allocates nothing.
 */
object PersianOrthography {

    const val MARKER: String = "MARBLE_PERSIAN_JOIN_V249"

    /** The half-space: a word boundary with no visible gap of its own. */
    const val HALF_SPACE: Char = '\u200C'

    private const val HALF_SPACE_STRING: String = "\u200C"

    /** Letters that join to whatever follows them. Everything else is a non-joiner. */
    private val JOINING: Set<Char> = setOf(
        'ب', 'پ', 'ت', 'ث', 'ج', 'چ', 'ح', 'خ',
        'س', 'ش', 'ص', 'ض', 'ط', 'ظ', 'ع', 'غ',
        'ف', 'ق', 'ک', 'گ', 'ل', 'م', 'ن', 'ه',
        'ی', 'ئ', 'ؤ'
    )

    /** Whole words that end in the same letters as a suffix but carry none of its meaning. */
    val NEVER_SPLIT: Set<String> = setOf(
        "تنها", "تنهای", "تنهایی", "تنهاست", "تنهایم", "تنهایت",
        "نهایی",
        "گیتهاب", "گیتهابی", "گیتهابه",
        "شما", "شماها", "چهارها", "خواها", "ماهها"
    )

    /**
     * Compound stems the product writes glued. The half-space belongs *inside* them, which no
     * suffix rule can see: `راهاندازی` is two words, not a plural.
     */
    val COMPOUND_STEMS: Map<String, String> = mapOf(
        "راهاندازی" to "راه\u200Cاندازی",
        "جابهجایی" to "جابه\u200Cجایی",
        "بهروزرسانی" to "به\u200Cروزرسانی",
        "پیشتنظیم" to "پیش\u200Cتنظیم",
        "دستدهی" to "دست\u200Cدهی",
        "پینشده" to "پین\u200Cشده",
        "تایماوت" to "تایم\u200Cاوت",
        "میانبر" to "میان\u200Cبر",
        "عیبیابی" to "عیب\u200Cیابی",
        "بهصورت" to "به\u200Cصورت",
        "پیشفرض" to "پیش\u200Cفرض",
        "تیکخورده" to "تیک\u200Cخورده",
        "رمزنگاریشده" to "رمزنگاری\u200Cشده",
        "رانتهام" to "ران\u200Cتایم",
        "گیتهابمون" to "گیتهاب\u200Cمون"
    )

    /**
     * `ه + ای` at the end of a word: the indefinite article, which takes a half-space. Listed per
     * stem because the same letters also spell the plural ezafe (`برنامههای`), and a rule that
     * cannot tell them apart would break the plural it just fixed.
     */
    val INDEFINITE_STEMS: Map<String, String> = mapOf(
        "استفادهای" to "استفاده\u200Cای",
        "تاریخچهای" to "تاریخچه\u200Cای",
        "تازهای" to "تازه\u200Cای",
        "دامنهای" to "دامنه\u200Cای",
        "لحظهای" to "لحظه\u200Cای",
        "مرحلهای" to "مرحله\u200Cای",
        "چندمرحلهای" to "چندمرحله\u200Cای",
        "پینشدهای" to "پین\u200Cشده\u200Cای",
        "آمادهای" to "آماده\u200Cای",
        "نسخهای" to "نسخه\u200Cای",
        "خانهای" to "خانه\u200Cای",
        "نمونهای" to "نمونه\u200Cای",
        "صفحهای" to "صفحه\u200Cای",
        "جملهای" to "جمله\u200Cای",
        "پایهای" to "پایه\u200Cای",
        "قاعدهای" to "قاعده\u200Cای",
        "دادهای" to "داده\u200Cای"
    )

    /** The colloquial verb forms the voice uses, written the way the rest of the tree writes them. */
    val MI_VERBS: Set<String> = setOf(
        "میکنم", "میکنی", "میکنه", "میکنیم", "میکنید", "میکنن",
        "میشه", "میشی", "میشیم", "میشن",
        "میمونه", "میمونن", "میمونی", "میمونم",
        "میره", "میرن", "میری", "میریم", "میرم",
        "میده", "میدن", "میدی", "میدیم", "میدم",
        "میخواد", "میخوان", "میخوام", "میخوای",
        "میگیره", "میگیرن", "میگیری", "میگیریم",
        "میگه", "میگن", "میگی", "میگیم",
        "میفرسته", "میفرستن", "میفهمه", "میفهمی",
        "میاد", "میوفته", "میافته", "میبره", "میبرن",
        "میتونه", "میتونن", "میتونم", "میتونی",
        "میخونه", "میخونن", "میبندم", "میبنده", "میبندن"
    )

    /** The plural suffixes, longest first so `هایی` is never read as `ها` plus leftovers. */
    private val PLURAL_SUFFIXES: List<String> = listOf("هایشان", "هایی", "هاست", "های", "ها")

    /** Any character that can carry Persian text; the scanner treats everything else as a border. */
    fun isPersianLetter(char: Char): Boolean =
        char in '\u0621'..'\u064A' ||
            char in '\u066E'..'\u06D3' ||
            char == '\u06D5'

    private fun isWordChar(char: Char): Boolean =
        isPersianLetter(char) || char == HALF_SPACE

    /** True when [text] already obeys the rule — the cheap exit that keeps `trx()` allocation-free. */
    fun isNormalized(text: String): Boolean = normalize(text) === text

    /**
     * The half-spaced form of [text]. Idempotent, and it returns the *same instance* when there is
     * nothing to fix, so a screen that translates a hundred labels in a frame allocates nothing.
     */
    fun normalize(text: String): String {
        if (text.isEmpty()) return text
        var changed = false
        val builder = StringBuilder(text.length + 8)
        var index = 0
        while (index < text.length) {
            val char = text[index]
            if (!isWordChar(char)) {
                builder.append(char)
                index += 1
                continue
            }
            val end = wordEnd(text, index)
            val word = text.substring(index, end)
            val fixed = normalizeWord(word)
            if (fixed !== word) changed = true
            builder.append(fixed)
            index = end
        }
        return if (changed) builder.toString() else text
    }

    private fun wordEnd(text: String, start: Int): Int {
        var cursor = start
        while (cursor < text.length && isWordChar(text[cursor])) cursor += 1
        return cursor
    }

    /** One Persian word: never split, then compounds, then the two suffix rules. */
    fun normalizeWord(word: String): String {
        if (word.isEmpty() || word in NEVER_SPLIT) return word

        var out = word
        INDEFINITE_STEMS[out]?.let { out = it }
        for ((glued, joined) in COMPOUND_STEMS) {
            if (out.contains(glued)) out = out.replace(glued, joined)
        }
        out = separatePlural(out)
        out = separateVerbPrefix(out)
        return out
    }

    /** `نسخهها` becomes `نسخه‌ها`; `سرورها` stays, because `ر` was never going to join to it anyway. */
    fun separatePlural(word: String): String {
        var out = word
        var cursor = 0
        var changed = false
        while (cursor < out.length) {
            val suffix = PLURAL_SUFFIXES.firstOrNull { out.startsWith(it, cursor) }
            if (suffix == null) {
                cursor += 1
                continue
            }
            val after = cursor + suffix.length
            val tail = out.getOrNull(after)
            val behind = if (cursor > 0) out[cursor - 1] else ' '
            val joinsBehind = behind in JOINING && behind != HALF_SPACE
            val closed = tail == null || (!isPersianLetter(tail) && tail != HALF_SPACE)
            val stem = out.substring(0, cursor)
            if (joinsBehind && closed && stem !in NEVER_SPLIT) {
                out = out.substring(0, cursor) + HALF_SPACE_STRING + out.substring(cursor)
                cursor += HALF_SPACE_STRING.length + suffix.length
                changed = true
            } else {
                cursor = after
            }
        }
        return if (changed) out else word
    }

    /** `میشه` becomes `می‌شه`, anchored at the word's own start: `همیشه` is not a verb and stays whole. */
    fun separateVerbPrefix(word: String): String {
        if (!word.startsWith("می") || word.length <= 2) return word
        if (word in MI_VERBS) return "می" + HALF_SPACE_STRING + word.substring(2)
        return word
    }
}
