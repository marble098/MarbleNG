package com.marbleng.app.ui

import com.marbleng.app.model.AppFont
import com.marbleng.app.model.AppLanguage
import com.marbleng.app.model.ConnectButtonStyle
import com.marbleng.app.model.DarkOutlineStyle
import com.marbleng.app.model.HomeStyle
import com.marbleng.app.model.parseConnectButtonStyle
import com.marbleng.app.model.parseAppFont
import com.marbleng.app.model.parseAppLanguage
import com.marbleng.app.model.parseDarkOutlineStyle
import com.marbleng.app.model.parseHomeStyle
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

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
        assertEquals(HomeStyle.DEFAULT, parseHomeStyle(""))
        assertEquals(HomeStyle.DEFAULT, parseHomeStyle("nebula"))

        assertEquals(HomeStyle.ROUTE_ATELIER, HomeStyle.DEFAULT)
        assertEquals(HomeStyle.IOS_FLOATING, parseHomeStyle(HomeStyle.IOS_FLOATING.id))
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

    @Test
    fun systemFontIsASelectableTypeface() {
        AppFont.entries.forEach { font ->
            assertEquals(font, parseAppFont(font.id))
        }
        assertEquals(AppFont.SYSTEM, parseAppFont("system"))

        assertEquals(AppFont.DEFAULT, parseAppFont("unknown-face"))
        assertEquals(AppFont.GOOGLE_SANS, AppFont.DEFAULT)
    }

    @Test
    fun everyHomeStyleRendersThroughExactlyOneFlavor() {
        assertEquals(5, HomeStyle.entries.size)
        assertEquals(
            "every presentation needs its own flavor",
            HomeStyle.entries.size,
            HomeStyle.entries.map(::homeFlavorFor).distinct().size
        )
    }

    @Test
    fun retiredHomeStylesFallBackToTheDefaultPresentation() {
        assertEquals(5, HomeStyle.entries.size)
        listOf("parametric", "bioluminescent", "PARAMETRIC", "BIOLUMINESCENT").forEach { legacy ->
            assertEquals(
                "retired style $legacy must fall back",
                HomeStyle.DEFAULT,
                parseHomeStyle(legacy)
            )
        }
    }

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
}
