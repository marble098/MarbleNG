package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_NOTIFICATION_COUNCIL_V231 — the notification copy engine, on the host JVM.
 *
 * Everything asserted here used to be untestable because it lived inside a class that needs a
 * `Context`: the bidi isolation of a measurement line, the grapheme-safe ellipsis, the Persian
 * dictionary, the cooldown table and the id allocator are the parts a device test could only
 * observe indirectly.
 */
class NotificationCopyV231Test {

    @Test
    fun languageFollowsTheAppSettingAndThenTheDevice() {
        assertEquals(NotificationLanguage.FA, NotificationCopy.languageFor("fa", "en"))
        assertEquals(NotificationLanguage.FA, NotificationCopy.languageFor("PERSIAN", "en"))
        assertEquals(NotificationLanguage.EN, NotificationCopy.languageFor("english", "fa"))
        assertEquals(NotificationLanguage.FA, NotificationCopy.languageFor("system", "fa"))
        assertEquals(NotificationLanguage.FA, NotificationCopy.languageFor("", "prs"))
        assertEquals(NotificationLanguage.EN, NotificationCopy.languageFor(null, null))
        assertEquals(NotificationLanguage.EN, NotificationCopy.languageFor("system", "de"))
    }

    @Test
    fun aLatinRunIsIsolatedOnlyInAPersianNotification() {
        val segment = "42 ms · ↓ 1.2 MB/s"
        assertEquals(segment, NotificationCopy.ltrSegment(segment, NotificationLanguage.EN))
        assertEquals(
            "${NotificationCopy.FSI}$segment${NotificationCopy.PDI}",
            NotificationCopy.ltrSegment(segment, NotificationLanguage.FA)
        )
        assertEquals("", NotificationCopy.isolate(""))
        assertEquals(
            "${NotificationCopy.RLM}محافظت‌شده",
            NotificationCopy.rtlLine("محافظت‌شده", NotificationLanguage.FA)
        )
        assertEquals("Protected", NotificationCopy.rtlLine("Protected", NotificationLanguage.EN))
    }

    @Test
    fun ellipsisNeverSplitsAGraphemeAndNeverLeavesADanglingWord() {
        assertEquals("", NotificationCopy.ellipsize("", 10))
        assertEquals("short", NotificationCopy.ellipsize("short", 10))
        assertEquals("long\nline", NotificationCopy.ellipsize("long\nline", 10).replace(" ", "\n"))

        val emoji = "🛡🛡🛡🛡🛡🛡"
        val cut = NotificationCopy.ellipsize(emoji, 3)
        assertTrue(cut.endsWith("…"))
        assertEquals(4, cut.codePointCount(0, cut.length))

        val sentence = "Moving traffic to another route while Full TUN stays fail-closed"
        val shortened = NotificationCopy.ellipsize(sentence, 24)
        assertTrue(shortened.endsWith("…"))
        assertFalse(shortened.contains("fail-closed"))
        assertTrue(shortened.codePointCount(0, shortened.length) <= 25)
    }

    @Test
    fun measurementsKeepTheirUnitAndTheirOrder() {
        assertEquals("1.5 MB/s", NotificationCopy.rate(1_572_864))
        assertEquals("320 KB/s", NotificationCopy.rate(327_680))
        assertEquals("0 B/s", NotificationCopy.rate(0))
        assertEquals("42 ms", NotificationCopy.latency(42))
        assertEquals("— ms", NotificationCopy.latency(0))

        val compact = NotificationCopy.telemetryCompact(
            NotificationLanguage.FA,
            42,
            1_572_864,
            327_680
        )
        assertTrue(compact.startsWith(NotificationCopy.FSI.toString()))
        assertTrue(compact.endsWith(NotificationCopy.PDI.toString()))
        assertTrue(compact.contains("42 ms"))
        assertTrue(compact.contains("1.5 MB/s"))
        assertTrue(compact.contains("320 KB/s"))

        val expanded = NotificationCopy.telemetryExpanded(NotificationLanguage.FA, 42, 0, 0)
        assertTrue(expanded.contains("تأخیر"))
        assertTrue(expanded.contains("دانلود"))
        assertTrue(expanded.contains("آپلود"))
    }

    @Test
    fun everyStateHasItsOwnTitleInBothLanguages() {
        val english = ConnectionStatus.entries.map {
            NotificationCopy.statusTitle(it, NotificationLanguage.EN)
        }
        val persian = ConnectionStatus.entries.map {
            NotificationCopy.statusTitle(it, NotificationLanguage.FA)
        }
        assertEquals(ConnectionStatus.entries.size, english.toSet().size)
        assertEquals(ConnectionStatus.entries.size, persian.toSet().size)
        ConnectionStatus.entries.forEach { status ->
            assertNotEquals(
                NotificationCopy.statusTitle(status, NotificationLanguage.EN),
                NotificationCopy.statusTitle(status, NotificationLanguage.FA)
            )
            assertTrue(NotificationCopy.statusSubText(status, NotificationLanguage.FA).isNotEmpty())
        }
    }

    @Test
    fun thePersianDictionaryTranslatesKnownCopyAndNeverTransliterates() {
        assertEquals(
            "کلید اضطراری ترافیک را نگه داشته است",
            NotificationCopy.translate("Kill switch is holding traffic", NotificationLanguage.FA)
        )
        assertEquals(
            "انتقال به Frankfurt",
            NotificationCopy.translate("Moving to Frankfurt", NotificationLanguage.FA)
        )
        assertEquals(
            "مسیر بازیابی شد • فرانکفورت",
            NotificationCopy.translate("Route recovered • فرانکفورت", NotificationLanguage.FA)
        )
        // Unknown copy stays readable English instead of becoming Persian letters.
        assertEquals(
            "Some brand new sentence",
            NotificationCopy.translate("Some brand new sentence", NotificationLanguage.FA)
        )
        assertEquals(
            "Kill switch is holding traffic",
            NotificationCopy.translate("Kill switch is holding traffic", NotificationLanguage.EN)
        )
    }

    @Test
    fun theChannelTaxonomyIsFourUniqueChannelsWithLocalizedNames() {
        val keys = NotificationChannelKey.menuOrder
        assertEquals(4, keys.size)
        assertEquals(keys.size, keys.map { it.id }.toSet().size)
        keys.forEach { key ->
            assertTrue(NotificationCopy.channelTitle(key, NotificationLanguage.EN).isNotEmpty())
            assertTrue(NotificationCopy.channelDescription(key, NotificationLanguage.FA).isNotEmpty())
            assertTrue(NotificationCopy.channelFocus(key, NotificationLanguage.EN).isNotEmpty())
        }
        assertEquals(NotificationChannelKey.CONNECTION.id, "marbleng-vpn")
        assertEquals(NotificationChannelKey.ALERTS.id, "marbleng-smart")
        assertEquals(NotificationChannelKey.PRIVACY.id, "marbleng-privacy")
        assertEquals(NotificationChannelKey.UPDATES.id, "marbleng-updates")
    }

    @Test
    fun routesRoundTripAndFallBackToTheDashboard() {
        NotificationRoute.entries.forEach { route ->
            assertEquals(route, NotificationRoute.fromId(route.id))
        }
        assertEquals(NotificationRoute.DASHBOARD, NotificationRoute.fromId(""))
        assertEquals(NotificationRoute.DASHBOARD, NotificationRoute.fromId("nonsense"))
    }

    @Test
    fun theCooldownTableIsBoundedAndTimeAware() {
        val cooldown = NotificationCooldown(maxEntries = 2, ttlMs = 100L)
        assertTrue(cooldown.allow("k", 0L, 1_000L))
        assertFalse(cooldown.allow("k", 500L, 1_000L))
        assertTrue(cooldown.allow("k", 1_200L, 1_000L))
        assertTrue(cooldown.allow("other", 1_300L, 0L))
        assertEquals(2, cooldown.size())
        // Both entries are older than the TTL, so the next write prunes them.
        assertTrue(cooldown.allow("fresh", 10_000L, 0L))
        assertEquals(1, cooldown.size())
        cooldown.clear()
        assertEquals(0, cooldown.size())
    }

    @Test
    fun everyEventKeyKeepsAStableCollisionFreeId() {
        val allocator = NotificationIdAllocator(base = 0, range = 4, maxKeys = 3)
        val first = allocator.idFor("PRIVACY:blocked:abc")
        assertEquals(first, allocator.idFor("PRIVACY:blocked:abc"))
        val ids = mutableSetOf(first)
        listOf("RECOVERY:failover", "NETWORK:underlay", "CORE:app").forEach { key ->
            ids += allocator.idFor(key)
        }
        assertEquals(4, ids.size)
        // Bound: only maxKeys keys are tracked, and evicted ids go back into the pool.
        assertEquals(3, allocator.size())
        assertEquals(3, allocator.activeIds().size)
        allocator.clear()
        assertEquals(0, allocator.size())
        assertTrue(allocator.activeIds().isEmpty())
    }

    @Test
    fun anUnchangedStatusNeverReachesSystemUi() {
        val gate = ConnectionSignatureGate(minIntervalMs = 1_000L)
        assertTrue(gate.allow("protected|frankfurt|42", 0L))
        assertFalse(gate.allow("protected|frankfurt|42", 5_000L))
        assertTrue(gate.allow("protected|frankfurt|43", 6_000L))
        assertFalse(gate.allow("protected|frankfurt|44", 6_500L))
        assertTrue(gate.allow("protected|frankfurt|44", 8_000L))
        gate.reset()
        assertTrue(gate.allow("protected|frankfurt|44", 9_000L))
    }

    @Test
    fun theStateSignatureCoversEverythingTheShadeCanShow() {
        val base = ConnectionNotificationState(
            status = ConnectionStatus.PROTECTED,
            routeName = "Frankfurt",
            pingMs = 42,
            downBps = 1_024L,
            upBps = 2_048L,
            sessionStartedAtMs = 1_000L
        )
        val language = NotificationLanguage.EN
        assertNotEquals(base.signature(language), base.copy(pingMs = 43).signature(language))
        assertNotEquals(base.signature(language), base.copy(routeName = "Tokyo").signature(language))
        assertNotEquals(base.signature(language), base.copy(status = ConnectionStatus.BLOCKED).signature(language))
        assertNotEquals(
            base.signature(language),
            base.signature(NotificationLanguage.FA)
        )
        assertTrue(base.ongoing)
        assertFalse(base.copy(status = ConnectionStatus.DISCONNECTED).ongoing)
    }
}
