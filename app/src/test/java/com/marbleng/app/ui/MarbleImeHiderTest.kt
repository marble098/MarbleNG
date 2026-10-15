package com.marbleng.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
        val clock = Clock()

        var honoured = 0

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
        val clock = Clock()

        assertTrue(clock.hider.hide())
        assertFalse(clock.hider.hide())
        clock.hider.noteFocusGained()
        assertTrue(clock.hider.hide())
        assertEquals(2, clock.released)
    }
}
