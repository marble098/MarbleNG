package com.marbleng.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** MARBLE_HARMONY_V279 — the connected pulse is bounded paint, never a layout spring. */
class ConnectPulseV279Test {
    @Test fun onlyAConnectedLiveMotionStateBreathes() {
        assertTrue(ConnectPulse.active(ConnectPhase.CONNECTED, loops = true))
        assertFalse(ConnectPulse.active(ConnectPhase.CONNECTED, loops = false))
        ConnectPhase.entries.filter { it != ConnectPhase.CONNECTED }.forEach {
            assertFalse("$it must not pulse", ConnectPulse.active(it, loops = true))
        }
    }

    @Test fun cosineBreathIsContinuousAndAllPaintStaysBounded() {
        assertEquals(0f, ConnectPulse.breath(0f), 0.001f)
        assertEquals(1f, ConnectPulse.breath(0.5f), 0.001f)
        assertEquals(0f, ConnectPulse.breath(1f), 0.001f)
        assertEquals(0f, ConnectPulse.breath(-1f), 0.001f)
        assertEquals(0f, ConnectPulse.breath(2f), 0.001f)
        assertTrue(ConnectPulse.haloAlpha(1f) <= ConnectPulse.HALO_ALPHA)
        assertEquals(0f, ConnectPulse.haloAlpha(0.5f, presence = 0f), 0f)
        assertEquals(ConnectPulse.RING_ALPHA, ConnectPulse.ringAlpha(0f), 0f)
        assertEquals(0f, ConnectPulse.ringAlpha(1f), 0f)
        assertEquals(0.72f, ConnectPulse.ringRadiusFraction(0f), 0.001f)
        assertEquals(0.98f, ConnectPulse.ringRadiusFraction(1f), 0.001f)
    }
}
