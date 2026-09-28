package com.marbleng.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_IME_HYGIENE_V197 — the guard behind the keyboard fix.
 *
 * The logcat that started this chapter contained more than a hundred
 * `InsetsController.hide(ime())` calls, each answered with `PHASE_CLIENT_ALREADY_HIDDEN`, in a
 * sixteen-minute window with no crash and no user interaction with a keyboard. Those are requests
 * to hide a keyboard that is not open, and the only way to make them is to ask on a loop.
 *
 * The hygiene fixes stop the loop at its source (a surface that must never own a session, and
 * fields with no event of their own to end one). This guard is what makes a *future* loop cheap:
 * one request gets through, the repeats inside the cooldown are dropped, and the platform is never
 * asked a hundred times to do something it has already done.
 */
class MarbleImeHiderTest {

    private class Clock {
        var now = 1_000L
        var released = 0
        val hider = MarbleImeHider(release = { released += 1 }, now = { now })
    }

    @Test fun theFirstHideIsHonoured() {
        val clock = Clock()

        assertTrue(clock.hider.hide())
        assertEquals(1, clock.released)
        assertEquals(1, clock.hider.honouredHides)
        assertEquals(0, clock.hider.suppressedHides)
    }

    @Test fun aLoopInsideTheCooldownCostsOneCallInsteadOfAHundred() {
        // The shape the logcat showed: the same request, over and over, with nothing to hide.
        val clock = Clock()

        var honoured = 0
        // 100 requests at 4 ms apart span 400 ms — well inside the cooldown, so this is one
        // request the platform hears about and ninety-nine it never sees.
        repeat(100) {
            if (clock.hider.hide()) honoured += 1
            clock.now += 4L
        }

        assertEquals(1, honoured)
        assertEquals(1, clock.released)
        assertEquals(99, clock.hider.suppressedHides)
    }

    @Test fun aRealSecondPressAfterTheCooldownStillWorks() {
        val clock = Clock()

        assertTrue(clock.hider.hide())
        clock.now += MARBLE_IME_HIDE_COOLDOWN_MS
        assertTrue("a genuine second request must not be swallowed", clock.hider.hide())
        assertEquals(2, clock.released)
    }

    @Test fun focusingAFieldAgainArmsTheNextHide() {
        // A user who taps back into a field and presses Done again is not a loop: the session is
        // new, so the request is new.
        val clock = Clock()

        assertTrue(clock.hider.hide())
        assertFalse(clock.hider.hide())
        clock.hider.noteFocusGained()
        assertTrue(clock.hider.hide())
        assertEquals(2, clock.released)
    }
}
