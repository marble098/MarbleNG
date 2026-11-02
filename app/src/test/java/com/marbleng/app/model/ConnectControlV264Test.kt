package com.marbleng.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_STEADY_CONNECT_V264 — the state table behind all three connect controls, pinned on the
 * host JVM.
 *
 * The reported defects were *relationships*: the centre button moved the tabs when the state
 * changed, and its word opened on a tap. The geometry half is proven on Robolectric in
 * `SteadyConnectControlsV264Test`; this file pins the semantic half, so the dock power button, the
 * centre trigger and Home's pill can never disagree about what a tap does in any phase — including
 * the two phases (`BLOCKED` and `DISCONNECTING`) the old controls did not have.
 */
class ConnectControlV264Test {

    @Test
    fun everyRepositoryStateResolvesToExactlyOnePhase() {
        assertEquals(ConnectPhase.IDLE, ConnectControl.phaseOf("DISCONNECTED"))
        assertEquals(ConnectPhase.CONNECTING, ConnectControl.phaseOf("CONNECTING"))
        assertEquals(ConnectPhase.CONNECTED, ConnectControl.phaseOf("CONNECTED"))
        assertEquals(ConnectPhase.DISCONNECTING, ConnectControl.phaseOf("DISCONNECTING"))
        assertEquals(ConnectPhase.BLOCKED, ConnectControl.phaseOf("BLOCKED"))
        // A restored detail, a padded value or an unknown token is the safe idle phase.
        assertEquals(ConnectPhase.IDLE, ConnectControl.phaseOf(null))
        assertEquals(ConnectPhase.IDLE, ConnectControl.phaseOf(""))
        assertEquals(ConnectPhase.IDLE, ConnectControl.phaseOf("FAILED"))
        assertEquals(ConnectPhase.BLOCKED, ConnectControl.phaseOf(" blocked "))
    }

    @Test
    fun theBooleanFlavourAgreesWithTheRawStateAndHasOnePriority() {
        ConnectPhase.entries.forEach { phase ->
            val raw = phase.name
            val booleans = ConnectControl.phaseOf(
                connected = phase == ConnectPhase.CONNECTED,
                connecting = phase == ConnectPhase.CONNECTING,
                disconnecting = phase == ConnectPhase.DISCONNECTING,
                blocked = phase == ConnectPhase.BLOCKED
            )
            assertEquals("$raw did not round-trip", phase, ConnectControl.phaseOf(raw))
            assertEquals("$raw disagrees with its flags", phase, booleans)
        }
        // A closing tunnel wins over every other flag: no stale "connected" can make it clickable.
        assertEquals(
            ConnectPhase.DISCONNECTING,
            ConnectControl.phaseOf(connected = true, connecting = true, disconnecting = true)
        )
    }

    @Test
    fun aTapMeansOneVerbAndAClosingTunnelHasNone() {
        assertEquals(ConnectAction.CONNECT, ConnectControl.actionOf(ConnectPhase.IDLE))
        assertEquals(ConnectAction.CANCEL, ConnectControl.actionOf(ConnectPhase.CONNECTING))
        assertEquals(ConnectAction.DISCONNECT, ConnectControl.actionOf(ConnectPhase.CONNECTED))
        assertEquals(ConnectAction.RESET, ConnectControl.actionOf(ConnectPhase.BLOCKED))
        assertEquals(ConnectAction.NONE, ConnectControl.actionOf(ConnectPhase.DISCONNECTING))

        ConnectPhase.entries.forEach { phase ->
            assertEquals(
                "$phase must be pressable exactly when it has a verb",
                ConnectControl.actionOf(phase) != ConnectAction.NONE,
                ConnectControl.pressable(phase)
            )
        }
        assertFalse(ConnectControl.pressable(ConnectPhase.DISCONNECTING))
        assertTrue(ConnectControl.pressable(ConnectPhase.BLOCKED))
    }

    @Test
    fun bothEndsOfATransitionShowTheSameActivity() {
        assertTrue(ConnectControl.working(ConnectPhase.CONNECTING))
        assertTrue(ConnectControl.working(ConnectPhase.DISCONNECTING))
        assertFalse(ConnectControl.working(ConnectPhase.IDLE))
        assertFalse(ConnectControl.working(ConnectPhase.CONNECTED))
        assertFalse(ConnectControl.working(ConnectPhase.BLOCKED))
    }

    @Test
    fun everyPhaseOwnsAnActionWordAndAStateWord() {
        val actions = mutableSetOf<String>()
        val states = mutableSetOf<String>()
        ConnectPhase.entries.forEach { phase ->
            val action = ConnectControl.actionKey(phase)
            val state = ConnectControl.stateKey(phase)
            assertTrue("$phase has no verb", action.isNotBlank())
            assertTrue("$phase has no state", state.isNotBlank())
            actions += action
            states += state
        }
        // Five phases, five spoken states; the verbs repeat only where the timings overlap.
        assertEquals(5, states.size)
        assertTrue("a dial must not say Connect", "Connect" != ConnectControl.actionKey(ConnectPhase.CONNECTING))
        assertTrue("a kill switch must not say Connect", "Connect" != ConnectControl.actionKey(ConnectPhase.BLOCKED))
        assertTrue("a closing tunnel must not say Connect", "Connect" != ConnectControl.actionKey(ConnectPhase.DISCONNECTING))
    }

    @Test
    fun theGeometryTheDockReservesNeverDependsOnAState() {
        DockSize.entries.forEach { size ->
            val groove = DockStudio.centerTriggerSizeDp(size)
            assertEquals(groove, DockStudio.centerTriggerWidthDp(size))
            assertTrue(groove > DockStudio.restingTabSizeDp(size))
            assertTrue(DockStudio.islandWidthDp(size) <= DockStudio.islandMaxWidthDp(size))
            assertTrue(DockStudio.islandWidthDp(size) > DockStudio.islandPaddingDp(size) * 2)
        }
    }

    @Test
    fun theDockPinsItsCentreSlotAndNeitherWingBlooms() {
        val dock = java.io.File("src/main/java/com/marbleng/app/ui/MarbleLumenDock.kt").readText()
        val wings = java.io.File("src/main/java/com/marbleng/app/ui/MarbleDockWings.kt").readText()
        val studio = java.io.File("src/main/java/com/marbleng/app/model/DockStudio.kt").readText()
        assertTrue("the centre slot must be reserved", ".width(centerSlotWidth)" in dock)
        assertTrue("the centre slot must read the fixed footprint", "DockStudio.centerTriggerWidthDp(size)" in dock)
        assertTrue("the trigger must have one fixed size", ".size(width = grooveWidth, height = grooveHeight)" in wings)
        assertTrue("the island must have one fixed width", ".width(DockStudio.islandWidthDp(size).dp)" in wings)
        assertFalse("a bloom came back", "centerBloomedWidthDp" in wings)
        assertFalse("a bloom came back", "centerBloomedWidthDp" in studio)
        assertFalse("a timed word came back", "CENTER_TOOLTIP_HOLD_MS" in wings)
        assertFalse("a timed word came back", "CENTER_TOOLTIP_HOLD_MS" in studio)
        assertTrue("the state is spoken, not drawn", "stateDescription" in wings)
    }
}
