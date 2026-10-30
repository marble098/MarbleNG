package com.marbleng.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_PERSIAN_CONNECT_DOCK_COUNCIL_V233 — the complaint was "choosing Persian does not make
 * everything Persian". This test pins the three layers that answer it: the complete literal
 * dictionary, the warm voice, and the phrase engine for live repository messages.
 */
class PersianCoverageV233Test {

    private fun isPersian(value: String): Boolean =
        value.any { it.code in 0x0600..0x06FF || it.code in 0x0750..0x077F || it.code in 0xFB50..0xFDFF }

    @Test
    fun theLabelsThatUsedToStayEnglishNowHaveRealPersianSentences() {
        val previouslyEnglish = listOf(
            "Active Route",
            "Quick Actions",
            "Scan QR",
            "Smart Rank",
            "Connection Mode",
            "Full TUN (Device VPN)",
            "Public Exit IP",
            "No matching servers",
            "Import a server to begin — paste, QR or file",
            "Allow VPN connection",
            "Allow notifications",
            "Left-to-right English interface",
            "Right-to-left Persian interface",
            "IPv4 / IPv6 Address Family",
            "Subscriptions & Auto-Refresh",
            "Run Censorship Scan",
            "No runtime log entries yet.",
            "No connection history yet.",
            "Try a shorter keyword — e.g. DNS, Theme, or Ping",
            "Versions & Cores",
            "gRPC Service Name"
        )
        previouslyEnglish.forEach { english ->
            val persian = faTranslate(english)
            assertTrue("$english stayed English", isPersian(persian))
            assertFalse("$english did not change", persian == english)
        }
    }

    @Test
    fun theVoiceIsCloseAndSecondPersonNotCeremonial() {
        assertEquals("بزن وصل شو", faTranslate("Connect"))
        assertEquals("قطعش کن", faTranslate("Disconnect"))
        assertEquals("وصل شدی!", faTranslate("Connected"))
        assertEquals("آماده‌ای؟", faTranslate("Ready"))
        assertEquals("باشه، ادامه", faTranslate("Continue"))
        assertEquals("بذار وصلت کنم", faTranslate("Allow VPN connection"))
        assertTrue(isPersian(faTranslate("Turn on Smart Event Alerts to send a test")))
    }

    @Test
    fun aLiveMessageKeepsTheServerNameAndTranslatesThePhrase() {
        val fast = faTranslate("Fast connect • Berlin 01")
        assertTrue(fast.startsWith("اتصال سریع"))
        assertTrue(fast.contains("Berlin 01"))
        assertTrue(fast.contains("\u2068") && fast.contains("\u2069"))

        val reconnect = faTranslate("Reconnect • Tehran 3")
        assertTrue(reconnect.startsWith("وصل شدن دوباره"))
        assertTrue(reconnect.contains("Tehran 3"))

        val cancelled = faTranslate("Cancelled • 12 measurements kept")
        assertTrue(cancelled.startsWith("لغو شد"))
        assertTrue(cancelled.contains("12"))

        assertEquals(
            "اتصال لغو شد • دسترسی لازم رو ندادی",
            faTranslate("Connection cancelled • required access was not granted")
        )
    }

    @Test
    fun dynamicNumbersAndNamesGetTheirOwnSentenceNotATransliteration() {
        assertTrue(faTranslate("Config already exists in My Group").contains("My Group"))
        assertTrue(isPersian(faTranslate("Config already exists in My Group")))
        assertTrue(faTranslate("Race winner: Berlin 01").startsWith("برنده مسابقه"))
        assertTrue(faTranslate("Tunnel engine set to tun2socks. Reconnect to run it.").contains("tun2socks"))
        assertEquals("همین الان", faTranslate("Just now"))
        assertEquals("هیچوقت", faTranslate("Never"))
    }

    @Test
    fun anUnknownEnglishSentenceStillStaysReadable() {
        val unknown = "A future engine diagnostic nobody has translated yet"
        assertEquals(unknown, faTranslate(unknown))
    }

    @Test
    fun theThreeConnectButtonsHaveTheirOwnPersianCopy() {
        assertEquals("هرجور دلت خواست وصل شو", faTranslate("Connect your way"))
        assertEquals("سه جور وصل شدن", faTranslate("Three ways to connect"))
        assertEquals("سریع وصل شو", faTranslate("Quick connect"))
        assertEquals("انتخاب هوشمند", faTranslate("Smart connect"))
        assertEquals("اتصال سبک", faTranslate("Light connect"))
        assertEquals("دوباره بزن تا قطع بشه", faTranslate("Tap again to stop"))
    }
}
