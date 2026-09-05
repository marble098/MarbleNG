package com.marbleng.app.ui

import com.marbleng.app.model.AppFont
import com.marbleng.app.model.AppLanguage
import com.marbleng.app.model.ConnectButtonStyle
import com.marbleng.app.model.DarkOutlineStyle
import com.marbleng.app.model.HomeStyle
import com.marbleng.app.model.ProAccent
import com.marbleng.app.model.ProBannerScope
import com.marbleng.app.model.ProShortcut
import com.marbleng.app.model.parseConnectButtonStyle
import com.marbleng.app.model.parseAppFont
import com.marbleng.app.model.parseAppLanguage
import com.marbleng.app.model.parseDarkOutlineStyle
import com.marbleng.app.model.parseHomeStyle
import com.marbleng.app.model.parseProAccent
import com.marbleng.app.model.parseProBannerScope
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
    fun everyPersistedConnectButtonStyleRoundTrips() {
        ConnectButtonStyle.entries.forEach { style ->
            assertEquals(style, parseConnectButtonStyle(style.id))
            assertEquals(style, parseConnectButtonStyle(style.id.uppercase()))
        }
    }

    @Test
    fun unknownConnectButtonStyleFallsBackToTheRoundShutter() {
        assertEquals(ConnectButtonStyle.ROUND, parseConnectButtonStyle(""))
        assertEquals(ConnectButtonStyle.ROUND, parseConnectButtonStyle("marquee"))
    }

    @Test
    fun everyConnectButtonStyleOwnsExactlyOnePlacement() {
        val zones = ConnectButtonStyle.entries.associateWith(::connectControlZone)
        assertEquals(
            "two silhouettes must never compete for the same spot",
            ConnectButtonStyle.entries.size,
            zones.values.distinct().size
        )
        assertEquals(ConnectControlZone.HERO_CENTER, zones[ConnectButtonStyle.ROUND])
        assertEquals(ConnectControlZone.HERO_FLOOR, zones[ConnectButtonStyle.SLIDE])
        assertEquals(ConnectControlZone.POWER_DOCK, zones[ConnectButtonStyle.CLASSIC])
        assertEquals(ConnectControlZone.PAGE_FLOOR, zones[ConnectButtonStyle.STREAM])
        assertEquals(ConnectControlZone.PAGE_PILL, zones[ConnectButtonStyle.FLOATING])
    }

    @Test
    fun onlyTheDockedSilhouettesLeaveTheHero() {
        assertEquals(
            listOf(ConnectButtonStyle.STREAM, ConnectButtonStyle.FLOATING),
            ConnectButtonStyle.entries.filter { connectControlZone(it).isPageDocked() }
        )
        // The three hero placements stay exactly where the classic presentations expect them.
        assertEquals(
            emptyList<ConnectButtonStyle>(),
            listOf(
                ConnectButtonStyle.ROUND,
                ConnectButtonStyle.SLIDE,
                ConnectButtonStyle.CLASSIC
            ).filter { connectControlZone(it).isPageDocked() }
        )
    }

    @Test
    fun unknownHomeStyleFallsBackToTheDefaultPresentation() {
        assertEquals(HomeStyle.IOS_SLIDER, parseHomeStyle(""))
        assertEquals(HomeStyle.IOS_SLIDER, parseHomeStyle("nebula"))
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
    fun retiredHomeStylesFallBackToTheDefaultPresentation() {
        assertEquals(4, HomeStyle.entries.size)
        listOf("parametric", "bioluminescent", "PARAMETRIC", "BIOLUMINESCENT").forEach { legacy ->
            assertEquals(
                "retired style $legacy must fall back",
                HomeStyle.IOS_SLIDER,
                parseHomeStyle(legacy)
            )
        }
    }

    // MARBLE_CONNECT_BUTTON_V121 / MARBLE_CONNECT_BUTTON_STYLES_V132 — five connection controls
    // (three in the hero, two docked at the page floor), the round shutter default, and every
    // retired silhouette id resolves to it instead of an unknown state.
    @Test
    fun connectButtonStylesRoundTripAndRetiredOnesFallBack() {
        assertEquals(5, ConnectButtonStyle.entries.size)
        ConnectButtonStyle.entries.forEach { style ->
            assertEquals(style, parseConnectButtonStyle(style.id))
            assertEquals(style, parseConnectButtonStyle(style.id.uppercase()))
        }
        listOf("auto", "float", "core", "pulse", "orbit", "shield", "").forEach { legacy ->
            assertEquals(
                "retired connect button $legacy must fall back",
                ConnectButtonStyle.ROUND,
                parseConnectButtonStyle(legacy)
            )
        }
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
