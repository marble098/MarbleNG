package com.marbleng.app.model

import com.marbleng.app.core.NotificationCopy
import com.marbleng.app.core.NotificationLanguage
import com.marbleng.app.ui.faLexiconValues
import com.marbleng.app.ui.faTranslate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_PERSIAN_JOIN_V249 — the report was one word on a settings row: «نسخهها».
 *
 * These tests hold the rule that answers it, and the three ways a rule like this goes wrong: it
 * splits a word that only looks like a plural (`تنها`), it splits a plural that never needed it
 * (`سرورها` — `ر` does not join to anything), or it fixes the dictionary and misses the sentence
 * the app assembles while it runs.
 */
class PersianOrthographyV249Test {

    private val half = "\u200C"

    @Test
    fun aGluedPluralGetsTheHalfSpaceTheReaderExpects() {
        assertEquals("نسخه" + half + "ها", PersianOrthography.normalize("نسخهها"))
        assertEquals("هسته" + half + "ها", PersianOrthography.normalize("هستهها"))
        assertEquals("برنامه" + half + "ها", PersianOrthography.normalize("برنامهها"))
        assertEquals("تب" + half + "ها", PersianOrthography.normalize("تبها"))
        assertEquals("لاگ" + half + "ها", PersianOrthography.normalize("لاگها"))
        assertEquals("اشتراک" + half + "ها", PersianOrthography.normalize("اشتراکها"))
        assertEquals("اعلان" + half + "ها", PersianOrthography.normalize("اعلانها"))
    }

    @Test
    fun theEzafePluralIsSeparatedAtTheSamePlace() {
        assertEquals("برنامه" + half + "های گوشی", PersianOrthography.normalize("برنامههای گوشی"))
        assertEquals("گزینه" + half + "های هسته", PersianOrthography.normalize("گزینههای هسته"))
        assertEquals("گره" + half + "هایی", PersianOrthography.normalize("گرههایی"))
    }

    @Test
    fun aPluralBehindALetterThatNeverJoinsIsLeftExactlyAlone() {
        // ر، د، ز، ا and و are non-joiners: the glued spelling already reads correctly, and a
        // half-space here would only add an invisible character to 62 shipped strings.
        listOf("سرورها", "سرورهای", "هشدارها", "کشورها", "کارها", "رمزها", "مقصدها", "خطاها")
            .forEach { word ->
                assertEquals(word, PersianOrthography.normalize(word))
            }
    }

    @Test
    fun wordsThatOnlyLookLikePluralsAreNeverSplit() {
        listOf("تنها", "تنهایی", "نهایی", "گیتهاب", "چهارم", "شما", "پیشنهادی", "میان")
            .forEach { word ->
                assertEquals(word, PersianOrthography.normalize(word))
            }
    }

    @Test
    fun theIndefiniteArticleIsDecidedPerStemNotByALookAlikeRule() {
        // لحظه + ای takes the half-space; برنامه + های does not, and the two are the same three
        // letters on the page — which is why this family is a table and not a pattern.
        assertEquals("لحظه" + half + "ای", PersianOrthography.normalize("لحظهای"))
        assertEquals("تازه" + half + "ای", PersianOrthography.normalize("تازهای"))
        assertEquals("آماده" + half + "ای؟", PersianOrthography.normalize("آمادهای؟"))
        assertEquals("برنامه" + half + "های", PersianOrthography.normalize("برنامههای"))
    }

    @Test
    fun theVerbPrefixIsSeparatedButNeverInsideAnotherWord() {
        assertEquals("می" + half + "شه", PersianOrthography.normalize("میشه"))
        assertEquals("می" + half + "کنم", PersianOrthography.normalize("میکنم"))
        assertEquals("می" + half + "خواد", PersianOrthography.normalize("میخواد"))
        assertEquals("همیشه", PersianOrthography.normalize("همیشه"))
        assertEquals("میزبان", PersianOrthography.normalize("میزبان"))
        assertEquals("میدان", PersianOrthography.normalize("میدان"))
    }

    @Test
    fun theCompoundStemsGetTheirOwnHalfSpace() {
        assertEquals("به" + half + "روزرسانی خودکار", PersianOrthography.normalize("بهروزرسانی خودکار"))
        assertEquals("راه" + half + "اندازی", PersianOrthography.normalize("راهاندازی"))
        assertEquals("عیب" + half + "یابی، لاگ" + half + "ها", PersianOrthography.normalize("عیبیابی، لاگها"))
    }

    @Test
    fun aWholeSentenceIsFixedWhereItNeedsItAndUntouchedWhereItDoesNot() {
        val before = "نسخهها، هستهها و سرورها رو با هم مقایسه کن"
        val after = "نسخه" + half + "ها، هسته" + half + "ها و سرورها رو با هم مقایسه کن"
        assertEquals(after, PersianOrthography.normalize(before))
    }

    @Test
    fun normalizeIsIdempotentAndAllocatesNothingWhenTheCopyIsAlreadyRight() {
        val clean = "سرورها، تست" + half + "ها و برنامه" + half + "های گوشی"
        assertSame(clean, PersianOrthography.normalize(clean))
        assertTrue(PersianOrthography.isNormalized(clean))

        val fixed = PersianOrthography.normalize("نسخهها و برنامههای گوشی")
        assertSame(fixed, PersianOrthography.normalize(fixed))
    }

    @Test
    fun latinRunsBidiIsolatesAndNumbersAreCarriedThroughUnchanged() {
        val mixed = "این کانفیگ قبلاً تو \u2068Berlin 01\u2069 هست • 42 ms"
        assertEquals(mixed, PersianOrthography.normalize(mixed))
    }

    @Test
    fun everyPersianValueTheDictionaryShipsIsAlreadyNormalized() {
        val values = faLexiconValues()
        assertTrue("the dictionary is empty", values.isNotEmpty())
        val glued = values.filter { !PersianOrthography.isNormalized(it) }
        assertEquals(glued.take(12).joinToString(" | "), 0, glued.size)
    }

    @Test
    fun theRuntimeTranslationPathObeysTheSameRule() {
        // A live message is assembled at runtime, where no source sweep can reach: the translator
        // itself is the last line of defence.
        val dynamic = faTranslate("نسخهها")
        assertEquals("نسخه" + half + "ها", dynamic)
        assertEquals("آماده" + half + "ای؟", faTranslate("Ready"))
    }

    @Test
    fun theNotificationShadeObeysTheSameRule() {
        val persian = NotificationCopy.translate("MarbleNG • Connecting", NotificationLanguage.FA)
        assertEquals(persian, PersianOrthography.normalize(persian))
        val english = NotificationCopy.translate("MarbleNG • Connecting", NotificationLanguage.EN)
        assertEquals("MarbleNG • Connecting", english)
    }
}
