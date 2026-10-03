package com.marbleng.app.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_SETTINGS_ONE_LINE_COPY_V208 — one option, one sentence.
 *
 * Settings grew a paragraph per row: explanations that described the whole feature, repeated what
 * the switch title already said, and pushed the control itself off the fold. On a page with forty
 * rows that is not documentation, it is noise the user scrolls past to find the switch.
 *
 * The rule is enforced in two places, and both are pinned here. [MarbleCopy] is the mechanism —
 * the settings rows draw `oneSentence(subtitle)`, so a row cannot go back to being a paragraph
 * even if the copy does. The second test is the copy itself: it scans the settings source and
 * fails on any explanation that is still more than one sentence, which is the check that keeps
 * the mechanism from quietly becoming the only thing doing the work.
 */
class MarbleCopyV208Test {

    @Test
    fun oneSentenceKeepsTheFirstSentenceAndDropsTheRest() {
        assertEquals(
            "Remember resolved addresses between runs.",
            MarbleCopy.oneSentence("Remember resolved addresses between runs. Turn off to write nothing to disk.")
        )
        assertEquals(
            "Connect first.",
            MarbleCopy.oneSentence("Connect first. Privacy audit uses the active proxy path.")
        )
    }

    @Test
    fun oneSentenceLeavesAnAlreadyShortRowAlone() {
        val text = "Split the TLS ClientHello across two packets."
        assertEquals(text, MarbleCopy.oneSentence(text))
        assertTrue(MarbleCopy.isOneSentence(text))
    }

    @Test
    fun blankStaysBlankSoAnEmptySubtitleDoesNotBecomeASpacerRow() {
        assertEquals("", MarbleCopy.oneSentence(""))
        assertEquals("", MarbleCopy.oneSentence("   "))
        assertTrue(MarbleCopy.isOneSentence(""))
    }

    @Test
    fun aSourceWrappedLiteralReadsAsOneLine() {
        // Copy in this codebase is concatenated across lines, so the flattening is not cosmetic:
        // without it the row would print the source's own line breaks.
        assertEquals(
            "Exclave core options for WARP, MASQUE and MTProxy.",
            MarbleCopy.flatten("Exclave core options\n            for WARP,   MASQUE and MTProxy.")
        )
    }

    @Test
    fun aPeriodInsideATokenNeverEndsTheSentence() {
        assertTrue(MarbleCopy.isOneSentence("Runs on sing-box 1.14.1-extended-2.7.2 today."))
        assertTrue(MarbleCopy.isOneSentence("Roughly 3.5 GB a month is enough."))
        assertTrue(MarbleCopy.isOneSentence("Use e.g. a small https page as the delay target."))
        assertTrue(MarbleCopy.isOneSentence("Hand it the vless:// link and it parses the rest."))
    }

    @Test
    fun theCutIsVisibleAsACutAndNeverOverrunsTheTwoLinesARowReserves() {
        val long = "This is one very long sentence that keeps going and going and going ".repeat(6)
        val clamped = MarbleCopy.oneSentence(long)
        assertTrue(clamped.length <= MarbleCopy.MaxDescriptionChars)
        assertTrue(clamped.endsWith(MarbleCopy.Ellipsis))
        assertFalse(MarbleCopy.isOneSentence(long.trim()))
    }

    @Test
    fun aSentenceWithNoTerminatorIsStillOneSentence() {
        val text = "Show the Customize row at the top of Home style 4"
        assertEquals(text, MarbleCopy.oneSentence(text))
        assertTrue(MarbleCopy.isOneSentence(text))
    }

    @Test
    fun thePersianTerminatorsAreSentenceEndsToo() {
        // The product ships in Persian, where ؛ and ؟ are the terminators; a rule that only knows
        // the Latin ones would let a Persian paragraph through untouched.
        assertFalse(MarbleCopy.isOneSentence("تب چهارم مخفی است؛ برای برگرداندنش آن را روشن کنید."))
        // The cut keeps the terminator it cut on: the sentence is returned as the author wrote
        // it, not re-punctuated into a Latin full stop.
        assertEquals(
            "تب چهارم مخفی است؛",
            MarbleCopy.oneSentence("تب چهارم مخفی است؛ برای برگرداندنش آن را روشن کنید.")
        )
    }

    // ── the copy itself ──────────────────────────────────────────────────────────────────────

    @Test
    fun noSettingsExplanationInTheSourceIsLongerThanOneSentence() {
        val source = sourceFile("src/main/java/com/marbleng/app/ui/Aether2026.kt")
        val offenders = mutableListOf<String>()
        LITERAL.findAll(source).forEach { match ->
            val raw = match.groupValues[1]
            // Interpolated and multi-line literals carry values, not prose: skip them.
            if (raw.length < 40 || '$' in raw || "\\n" in raw) return@forEach
            val flat = MarbleCopy.flatten(raw)
            if (flat.isEmpty()) return@forEach
            if (MarbleCopy.firstSentenceEnd(flat) < flat.length) {
                offenders.add("\"$flat\"")
            }
        }
        assertTrue(
            "these settings strings are still more than one sentence:\n" + offenders.joinToString("\n"),
            offenders.isEmpty()
        )
    }

    @Test
    fun theSettingsRowsDrawTheirSubtitleThroughTheRule() {
        // The mechanism, not the memory of it: six primitives (sub-page, hub card, hub row, hub
        // switch, section card, switch) draw the clamped text, so a new row inherits the rule.
        val source = sourceFile("src/main/java/com/marbleng/app/ui/Aether2026.kt")
        assertTrue(
            "settings rows no longer clamp their subtitles",
            source.contains("MarbleCopy.oneSentence(trx(subtitle))")
        )
        assertTrue(
            "at least the six settings primitives must clamp: found " +
                source.countOccurrences("MarbleCopy.oneSentence(trx(subtitle))"),
            source.countOccurrences("MarbleCopy.oneSentence(trx(subtitle))") >= 6
        )
    }

    private fun String.countOccurrences(needle: String): Int {
        var count = 0
        var index = indexOf(needle)
        while (index >= 0) {
            count++
            index = indexOf(needle, index + needle.length)
        }
        return count
    }

    private fun sourceFile(relative: String): String {
        val candidate = listOf(File(relative), File("../$relative"), File("app/$relative"))
            .firstOrNull { it.isFile }
        requireNotNull(candidate) { "cannot locate $relative from ${File(".").absolutePath}" }
        return candidate.readText()
    }

    private companion object {
        /** One double-quoted literal on a single source line, without walking into a comment. */
        val LITERAL = Regex("\"((?:[^\"\\\\\\n]|\\\\.)*)\"")
    }
}
