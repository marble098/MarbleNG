package com.marbleng.app.ui

import com.marbleng.app.model.AppLanguage
import com.marbleng.app.model.HomeStyle
import com.marbleng.app.model.parseAppLanguage
import com.marbleng.app.model.parseHomeStyle
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/** MARBLE_HOME_STYLE_V110 / MARBLE_BILINGUAL_V110 */
class MarbleHomeStyleTest {
    @Test
    fun everyPersistedHomeStyleRoundTrips() {
        HomeStyle.entries.forEach { style ->
            assertEquals(style, parseHomeStyle(style.id))
            assertEquals(style, parseHomeStyle(style.id.uppercase()))
        }
    }

    @Test
    fun unknownHomeStyleFallsBackToTheDefaultPresentation() {
        assertEquals(HomeStyle.BIOLUMINESCENT, parseHomeStyle(""))
        assertEquals(HomeStyle.BIOLUMINESCENT, parseHomeStyle("nebula"))
    }

    @Test
    fun everyPersistedLanguageRoundTrips() {
        AppLanguage.entries.forEach { language ->
            assertEquals(language, parseAppLanguage(language.id))
        }
        assertEquals(AppLanguage.SYSTEM, parseAppLanguage("klingon"))
    }

    @Test
    fun persianLocalesResolveToPersianCopy() {
        listOf("fa", "fa-IR", "prs-AF", "tg").forEach { tag ->
            assertEquals(
                "expected Persian copy for $tag",
                MarbleLanguage.FA,
                languageForLocale(Locale.forLanguageTag(tag))
            )
        }
    }

    @Test
    fun otherLocalesFallBackToEnglish() {
        listOf("en", "en-GB", "de", "ar", "tr").forEach { tag ->
            assertEquals(
                "expected English copy for $tag",
                MarbleLanguage.EN,
                languageForLocale(Locale.forLanguageTag(tag))
            )
        }
    }
}
