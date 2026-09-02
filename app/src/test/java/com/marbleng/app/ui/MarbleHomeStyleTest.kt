package com.marbleng.app.ui

import com.marbleng.app.model.AppFont
import com.marbleng.app.model.AppLanguage
import com.marbleng.app.model.DarkOutlineStyle
import com.marbleng.app.model.HomeStyle
import com.marbleng.app.model.ProAccent
import com.marbleng.app.model.ProBannerScope
import com.marbleng.app.model.ProServerCardStyle
import com.marbleng.app.model.ProShortcut
import com.marbleng.app.model.parseAppFont
import com.marbleng.app.model.parseAppLanguage
import com.marbleng.app.model.parseDarkOutlineStyle
import com.marbleng.app.model.parseHomeStyle
import com.marbleng.app.model.parseProAccent
import com.marbleng.app.model.parseProBannerScope
import com.marbleng.app.model.parseProServerCardStyle
import com.marbleng.app.model.parseProShortcut
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/** MARBLE_HOME_STYLE_V110 / MARBLE_BILINGUAL_V110 / MARBLE_SIGNATURE_HOME_V112 */
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
        // MARBLE_SIGNATURE_HOME_V112 — the Signature studio is the product default, so an
        // unknown/legacy persisted value lands on it rather than on a classic style.
        assertEquals(HomeStyle.PRO, parseHomeStyle(""))
        assertEquals(HomeStyle.PRO, parseHomeStyle("nebula"))
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

    // ------------------------------------------------------------------ MARBLE_SIGNATURE_HOME_V112

    @Test
    fun systemFontIsASelectableTypeface() {
        AppFont.entries.forEach { font ->
            assertEquals(font, parseAppFont(font.id))
        }
        assertEquals(AppFont.SYSTEM, parseAppFont("system"))
        assertEquals(AppFont.VAZIR, parseAppFont("unknown-face"))
    }

    @Test
    fun everySignatureCustomizationRoundTrips() {
        ProServerCardStyle.entries.forEach { style ->
            assertEquals(style, parseProServerCardStyle(style.id))
        }
        assertEquals(ProServerCardStyle.GLASS, parseProServerCardStyle("junk"))

        ProAccent.entries.forEach { accent ->
            assertEquals(accent, parseProAccent(accent.id))
        }
        assertEquals(ProAccent.ELECTRIC, parseProAccent("junk"))

        ProBannerScope.entries.forEach { scope ->
            assertEquals(scope, parseProBannerScope(scope.id))
        }
        assertEquals(ProBannerScope.HOME, parseProBannerScope("junk"))

        ProShortcut.entries.forEach { shortcut ->
            assertEquals(shortcut, parseProShortcut(shortcut.id))
        }
        assertEquals(ProShortcut.LIBRARY, parseProShortcut("junk"))
    }

    @Test
    fun nightOutlineStylesRoundTrip() {
        DarkOutlineStyle.entries.forEach { style ->
            assertEquals(style, parseDarkOutlineStyle(style.id))
        }
        assertEquals(DarkOutlineStyle.SUBTLE, parseDarkOutlineStyle("junk"))
    }

    @Test
    fun signatureAccentResolvesToDistinctColors() {
        val colors = ProAccent.entries.map(::signatureAccentColor)
        assertEquals("every accent must be unique", colors.size, colors.distinct().size)
    }
}
