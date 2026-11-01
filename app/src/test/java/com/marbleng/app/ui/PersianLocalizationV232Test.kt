package com.marbleng.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PersianLocalizationV232Test {
    @Test
    fun unknownEnglishCopyIsNeverFalselyTransliterated() {
        val source = "Reconnect automatically when the route is ready"
        assertEquals(source, faTranslate(source))
        assertNotEquals("ریکنکت اوتوماتیکالی ون ده روت ایز ردی", faTranslate(source))
    }

    @Test
    fun theFloatingDockHasNaturalPersianLabels() {
        // MARBLE_FLOATING_DOCK_V258 — V232's two presentation choices are gone, so the test that
        // pinned their words now pins the words that replaced them: the three rungs, the gap and
        // the palette the whole app is repainted with.
        assertEquals("اندازهٔ کپسول", faTranslate("Capsule size"))
        assertEquals("فاصله از پایین", faTranslate("Distance from the bottom"))
        assertEquals("باریک", faTranslate("Slim"))
        assertEquals("بزرگ", faTranslate("Grand"))
        assertEquals("ماربل کلاسیک", faTranslate("Marble Classic"))
        assertEquals("شفق", faTranslate("Aurora"))
        assertEquals(
            "نوار پایین برنامه را مطابق سلیقه‌تان بچینید و اندازه‌اش را انتخاب کنید.",
            faTranslate("The bottom bar of the app: what it shows and how much room it takes.")
        )
    }

    @Test
    fun callerWhitespaceIsPreservedAroundARealTranslation() {
        assertEquals("  فاصله از پایین  ", faTranslate("  Distance from the bottom  "))
    }
}
