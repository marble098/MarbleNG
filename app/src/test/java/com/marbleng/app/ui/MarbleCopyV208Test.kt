package com.marbleng.app.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
        assertFalse(MarbleCopy.isOneSentence("تب چهارم مخفی است؛ برای برگرداندنش آن را روشن کنید."))

        assertEquals(
            "تب چهارم مخفی است؛",
            MarbleCopy.oneSentence("تب چهارم مخفی است؛ برای برگرداندنش آن را روشن کنید.")
        )
    }

    @Test
    fun noSettingsExplanationInTheSourceIsLongerThanOneSentence() {
        val source = sourceFile("src/main/java/com/marbleng/app/ui/Aether2026.kt")
        val offenders = mutableListOf<String>()
        LITERAL.findAll(source).forEach { match ->
            val raw = match.groupValues[1]

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
        val LITERAL = Regex("\"((?:[^\"\\\\\\n]|\\\\.)*)\"")
    }
}
