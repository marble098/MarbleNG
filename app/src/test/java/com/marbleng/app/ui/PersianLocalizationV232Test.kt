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
    fun bottomBarChoicesHaveNaturalPersianLabels() {
        assertEquals("جای نوار پایین", faTranslate("Bottom Bar Position"))
        assertEquals("چسبیده به پایین", faTranslate("Attached to bottom"))
        assertEquals("شناور بالای نوار پایین گوشی", faTranslate("Floating above bottom"))
        assertEquals(
            "نوار پایین برنامه را مطابق سلیقه‌تان بچینید و اندازه‌اش را انتخاب کنید.",
            faTranslate("The bottom bar of the app: what it shows and how much room it takes.")
        )
    }

    @Test
    fun callerWhitespaceIsPreservedAroundARealTranslation() {
        assertEquals("  چسبیده به پایین  ", faTranslate("  Attached to bottom  "))
    }
}
