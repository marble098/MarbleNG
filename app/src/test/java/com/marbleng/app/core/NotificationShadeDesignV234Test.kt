package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_SHADE_ATELIER_V234 — the rules the drawn notification obeys, pinned without a device.
 *
 * `RemoteViews` cannot exist on a host JVM, which is exactly why the *decisions* behind it live in
 * `NotificationShadeDesign` and the copy lives in `NotificationCopy`. What is asserted here is what
 * a screenshot would only show by accident: which state owns which badge, that a clock and a rail
 * never appear together, that rates are never printed when no traffic is moving, and that the wall
 * clock becomes a monotonic chronometer base.
 */
class NotificationShadeDesignV234Test {

    @Test
    fun everyStateOwnsItsOwnBadge() {
        assertEquals(ShadeBadge.PROTECTED, NotificationShadeDesign.badge(ConnectionStatus.PROTECTED))
        assertEquals(ShadeBadge.CONNECTING, NotificationShadeDesign.badge(ConnectionStatus.CONNECTING))
        assertEquals(ShadeBadge.RECOVERING, NotificationShadeDesign.badge(ConnectionStatus.RECOVERING))
        assertEquals(ShadeBadge.BLOCKED, NotificationShadeDesign.badge(ConnectionStatus.BLOCKED))
        assertEquals(ShadeBadge.IDLE, NotificationShadeDesign.badge(ConnectionStatus.DISCONNECTED))

        // The drawable name the builder resolves is derived from the badge, so a sixth state cannot
        // silently reuse the fifth drawing.
        assertEquals(
            ConnectionStatus.entries.size,
            ConnectionStatus.entries.map { NotificationShadeDesign.badgeId(it) }.distinct().size
        )
        assertEquals("protected", NotificationShadeDesign.badgeId(ConnectionStatus.PROTECTED))
    }

    @Test
    fun theClockAndTheRailNeverShareTheShade() {
        // A live session with the switch on: a clock, no rail.
        assertTrue(NotificationShadeDesign.clockVisible(ConnectionStatus.PROTECTED, true))
        assertFalse(NotificationShadeDesign.progressVisible(ConnectionStatus.PROTECTED))

        // A route being built: a rail, no clock — two clocks in one row is what V231 called out.
        assertFalse(NotificationShadeDesign.clockVisible(ConnectionStatus.CONNECTING, true))
        assertTrue(NotificationShadeDesign.progressVisible(ConnectionStatus.CONNECTING))
        assertTrue(NotificationShadeDesign.progressVisible(ConnectionStatus.RECOVERING))

        // The user's own switch still wins over everything.
        assertFalse(NotificationShadeDesign.clockVisible(ConnectionStatus.PROTECTED, false))
        assertFalse(NotificationShadeDesign.progressVisible(ConnectionStatus.DISCONNECTED))
    }

    @Test
    fun ratesAreOnlyDrawnWhenTrafficIsMoving() {
        assertTrue(NotificationShadeDesign.telemetryVisible(ConnectionStatus.PROTECTED, true))
        assertTrue(NotificationShadeDesign.telemetryVisible(ConnectionStatus.RECOVERING, true))
        assertFalse(NotificationShadeDesign.telemetryVisible(ConnectionStatus.PROTECTED, false))
        assertFalse(NotificationShadeDesign.telemetryVisible(ConnectionStatus.CONNECTING, true))
        assertFalse(NotificationShadeDesign.telemetryVisible(ConnectionStatus.BLOCKED, true))
        assertFalse(NotificationShadeDesign.telemetryVisible(ConnectionStatus.DISCONNECTED, true))
    }

    @Test
    fun theChronometerBaseIsMonotonicNotWallClock() {
        // A session that started 90 s ago, read at wall 1_700_000_000_000 / elapsed 5_000_000.
        val base = NotificationShadeDesign.chronometerBase(
            sessionStartedAtMs = 1_700_000_000_000L - 90_000L,
            nowWallMs = 1_700_000_000_000L,
            nowElapsedMs = 5_000_000L
        )
        assertEquals(5_000_000L - 90_000L, base)

        // No session yet: no clock, rather than a counter from the epoch.
        assertEquals(
            0L,
            NotificationShadeDesign.chronometerBase(0L, 1_700_000_000_000L, 5_000_000L)
        )

        // A clock that is somehow ahead of the session start cannot produce a future base.
        assertEquals(
            5_000_000L,
            NotificationShadeDesign.chronometerBase(
                1_700_000_120_000L,
                1_700_000_000_000L,
                5_000_000L
            )
        )
    }

    @Test
    fun theTwoRouteVerbsAndTheirTwoHonestAnswersExistInBothLanguages() {
        assertEquals("Next server", NotificationCopy.actionNextServer(NotificationLanguage.EN))
        assertEquals("سرور بعدی", NotificationCopy.actionNextServer(NotificationLanguage.FA))
        assertEquals("Next sub", NotificationCopy.actionNextSubscription(NotificationLanguage.EN))
        assertEquals("اشتراک بعدی", NotificationCopy.actionNextSubscription(NotificationLanguage.FA))

        listOf(NotificationCycleKind.SERVER, NotificationCycleKind.SUBSCRIPTION).forEach { kind ->
            val busyEnglish = NotificationCopy.cycleBusyEnglish(kind)
            val emptyEnglish = NotificationCopy.cycleEmptyEnglish(kind)

            // The English constants the service publishes must be dictionary keys, otherwise a
            // Persian app would print them untranslated on the lock screen.
            assertEquals(
                NotificationCopy.translate(busyEnglish, NotificationLanguage.FA),
                NotificationCopy.cycleBusyDetail(kind, NotificationLanguage.FA)
            )
            assertEquals(
                NotificationCopy.translate(emptyEnglish, NotificationLanguage.FA),
                NotificationCopy.cycleEmptyDetail(kind, NotificationLanguage.FA)
            )
            assertTrue(NotificationCopy.cycleBusyDetail(kind, NotificationLanguage.FA).any { it.isPersian() })
            assertTrue(NotificationCopy.cycleEmptyDetail(kind, NotificationLanguage.FA).any { it.isPersian() })
            assertEquals(busyEnglish, NotificationCopy.cycleBusyDetail(kind, NotificationLanguage.EN))
            assertEquals(emptyEnglish, NotificationCopy.cycleEmptyDetail(kind, NotificationLanguage.EN))
        }

        assertEquals(
            "No other server to switch to",
            NotificationCopy.cycleEmptyEnglish(NotificationCycleKind.SERVER)
        )
        assertEquals(
            "No other subscription to switch to",
            NotificationCopy.cycleEmptyEnglish(NotificationCycleKind.SUBSCRIPTION)
        )
    }

    @Test
    fun theVerbIdsSurviveAnIntentRoundTrip() {
        // The id travels in the PendingIntent action and in an extra, never as an ordinal.
        NotificationCycleKind.entries.forEach { kind ->
            assertEquals(kind, NotificationCycleKind.fromId(kind.id))
            assertEquals(kind, NotificationCycleKind.fromId(" ${kind.id.uppercase()} "))
        }
        assertEquals(null, NotificationCycleKind.fromId(""))
        assertEquals(null, NotificationCycleKind.fromId(null))
        assertEquals(null, NotificationCycleKind.fromId("refresh"))
    }

    private fun Char.isPersian(): Boolean = this in '\u0600'..'\u06FF' || this == '\u200C'
}
