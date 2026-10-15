package com.marbleng.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarbleDesignContractV207Test {
    @Test
    fun statePrecedenceIsBlockedThenClosingThenSecuringThenConnected() {
        assertEquals(
            MarbleRouteState.BLOCKED,
            marbleRouteStateOf(
                hasRoute = true,
                connected = false,
                connecting = false,
                disconnecting = true,
                blocked = true
            )
        )
        assertEquals(
            MarbleRouteState.CLOSING,
            marbleRouteStateOf(
                hasRoute = true,
                connected = true,
                connecting = false,
                disconnecting = true,
                blocked = false
            )
        )
        assertEquals(
            MarbleRouteState.SECURING,
            marbleRouteStateOf(
                hasRoute = true,
                connected = true,
                connecting = true,
                disconnecting = false,
                blocked = false
            )
        )
        assertEquals(
            MarbleRouteState.CONNECTED,
            marbleRouteStateOf(
                hasRoute = true,
                connected = true,
                connecting = false,
                disconnecting = false,
                blocked = false
            )
        )
    }

    @Test
    fun noRouteIsItsOwnStateAndIsNotReady() {
        assertEquals(
            MarbleRouteState.NO_ROUTE,
            marbleRouteStateOf(
                hasRoute = false,
                connected = false,
                connecting = false,
                disconnecting = false,
                blocked = false
            )
        )
        assertEquals(
            MarbleRouteState.READY,
            marbleRouteStateOf(
                hasRoute = true,
                connected = false,
                connecting = false,
                disconnecting = false,
                blocked = false
            )
        )
    }

    @Test
    fun everyStateResolvesToExactlyOneVerb() {
        assertEquals(MarbleConnectVerb.ADD_ROUTE, MarbleRouteState.NO_ROUTE.connectVerb())
        assertEquals(MarbleConnectVerb.CONNECT, MarbleRouteState.READY.connectVerb())
        assertEquals(MarbleConnectVerb.CANCEL, MarbleRouteState.SECURING.connectVerb())
        assertEquals(MarbleConnectVerb.DISCONNECT, MarbleRouteState.CONNECTED.connectVerb())
        assertEquals(MarbleConnectVerb.WAIT, MarbleRouteState.CLOSING.connectVerb())
        assertEquals(MarbleConnectVerb.RESET, MarbleRouteState.BLOCKED.connectVerb())

        assertNotEquals(MarbleConnectVerb.CONNECT, MarbleRouteState.NO_ROUTE.connectVerb())
    }

    @Test
    fun onlyTheClosingStateRefusesATap() {
        MarbleRouteState.entries.forEach { state ->
            assertEquals(
                "only a tunnel mid-teardown may not be actuated again: $state",
                state != MarbleRouteState.CLOSING,
                state.isActionable()
            )
        }
    }

    @Test
    fun sixStatesOwnSixDistinctHueRoles() {
        val hues = MarbleRouteState.entries.map { it.stateHue() }
        assertEquals(
            "each state must own a distinct semantic role",
            MarbleRouteState.entries.size,
            hues.distinct().size
        )
    }

    @Test
    fun anIdlePageLightsOneNodeAndNotThree() {
        val ready = marbleRoutePathOf(
            state = MarbleRouteState.READY,
            egressResolved = false,
            egressFailed = false
        )
        assertEquals(MarbleNodeState.PROVEN, ready.device)
        assertEquals(MarbleNodeState.UNKNOWN, ready.tunnel)
        assertEquals(MarbleNodeState.UNKNOWN, ready.egress)
        assertEquals(1, ready.provenCount)
        assertFalse(ready.isComplete)

        val idle = marbleRoutePathOf(
            state = MarbleRouteState.NO_ROUTE,
            egressResolved = false,
            egressFailed = false
        )
        assertEquals(0, idle.provenCount)
    }

    @Test
    fun theExitNodeLightsOnlyOnAnAnswerItActuallyHas() {
        val noExitYet = marbleRoutePathOf(
            state = MarbleRouteState.CONNECTED,
            egressResolved = false,
            egressFailed = false
        )
        assertEquals(MarbleNodeState.PROVEN, noExitYet.tunnel)
        assertEquals(MarbleNodeState.PENDING, noExitYet.egress)
        assertEquals(2, noExitYet.provenCount)

        val complete = marbleRoutePathOf(
            state = MarbleRouteState.CONNECTED,
            egressResolved = true,
            egressFailed = false
        )
        assertEquals(3, complete.provenCount)
        assertTrue(complete.isComplete)

        val failed = marbleRoutePathOf(
            state = MarbleRouteState.BLOCKED,
            egressResolved = true,
            egressFailed = true
        )

        assertEquals(MarbleNodeState.FAILED, failed.tunnel)
        assertEquals(MarbleNodeState.FAILED, failed.egress)
    }

    @Test
    fun aFlagEmojiInANameIsNotAMeasurement() {
        val label = marbleLocationTrustOf(
            hasSessionReport = false,
            hasMeasuredCode = false,
            measuredIsProvisional = false,
            hasLabelGlyph = true
        )
        assertEquals(MarbleLocationTrust.LABEL_GUESS, label)
        assertFalse("a label may never be painted as a national flag", label.mayDrawFlag())
        assertFalse(label.isVerified())

        val nothing = marbleLocationTrustOf(
            hasSessionReport = false,
            hasMeasuredCode = false,
            measuredIsProvisional = false,
            hasLabelGlyph = false
        )
        assertEquals(MarbleLocationTrust.UNKNOWN, nothing)
        assertFalse(nothing.mayDrawFlag())
    }

    @Test
    fun oneLoneWitnessIsFlaggableButNotQuotable() {
        val lone = marbleLocationTrustOf(
            hasSessionReport = false,
            hasMeasuredCode = true,
            measuredIsProvisional = true,
            hasLabelGlyph = true
        )
        assertEquals(MarbleLocationTrust.MEASURED_PROVISIONAL, lone)
        assertTrue("a measured answer may be drawn even while it is being re-tested", lone.mayDrawFlag())
        assertFalse("…but it may not be called confirmed", lone.isVerified())

        val quorum = marbleLocationTrustOf(
            hasSessionReport = false,
            hasMeasuredCode = true,
            measuredIsProvisional = false,
            hasLabelGlyph = false
        )
        assertEquals(MarbleLocationTrust.MEASURED_VERIFIED, quorum)
        assertTrue(quorum.mayDrawFlag())
        assertTrue(quorum.isVerified())
    }

    @Test
    fun aRunningSessionOutranksTheOfflineCache() {
        val session = marbleLocationTrustOf(
            hasSessionReport = true,
            hasMeasuredCode = true,
            measuredIsProvisional = false,
            hasLabelGlyph = true
        )
        assertEquals(MarbleLocationTrust.SESSION_REPORT, session)

        assertFalse(session.isVerified())
    }

    @Test
    fun theTouchFloorIsThePlatformNumber() {
        assertEquals(48f, MarbleTapTarget.Floor.value, 0f)
        MarbleControlKind.entries.forEach { kind ->
            assertTrue(
                "a control's artwork may be smaller than the floor; its hit box may not",
                kind.minHeight.value >= 0f
            )
        }
    }

    @Test
    fun pressTravelGrowsAsTheSurfaceGrows() {
        assertTrue(
            MarbleControlKind.Icon.pressScale > MarbleControlKind.Primary.pressScale ||
                MarbleControlKind.Icon.pressScale == MarbleControlKind.Primary.pressScale
        )
        assertEquals(0.955f, MarbleControlKind.Primary.pressScale, 1e-5f)
        assertEquals(0.972f, MarbleControlKind.Compact.pressScale, 1e-5f)
        assertEquals(56f, MarbleControlKind.Primary.minHeight.value, 0f)
    }

    @Test
    fun reducedMotionRemovesTravelRatherThanFakingItWithZeroDuration() {
        MarbleControlKind.entries.forEach { kind ->
            assertEquals(
                "with motion off a control must not move at all: $kind",
                1f,
                MarbleMotionPolicy.pressScale(kind, motionEnabled = false),
                0f
            )
            assertEquals(kind.pressScale, MarbleMotionPolicy.pressScale(kind, motionEnabled = true), 0f)
            assertFalse(kind.acknowledgesPress(motionEnabled = false))
            assertTrue(kind.acknowledgesPress(motionEnabled = true))
        }
    }

    @Test
    fun theEntranceQueueIsBoundedAndSwitchOffAble() {
        assertEquals(0L, MarbleMotionPolicy.entranceDelayMs(4, motionEnabled = false))
        assertEquals(0L, MarbleMotionPolicy.entranceDelayMs(0, motionEnabled = true))
        assertEquals(45L, MarbleMotionPolicy.entranceDelayMs(1, motionEnabled = true))

        val capped = MarbleMotionPolicy.entranceDelayMs(40, motionEnabled = true)
        assertEquals(capped, MarbleMotionPolicy.entranceDelayMs(6, motionEnabled = true))
        assertEquals(6L * 45L, capped)
    }

    @Test
    fun theConnectControlNodsItDoesNotLurch() {
        assertEquals(1.02f, MarbleConnectMotion.statePopPeak, 1e-5f)
        assertTrue(
            "a state-change pop must stay under a three percent leap",
            MarbleConnectMotion.statePopPeak <= 1.03f
        )
        assertEquals(200, MarbleConnectMotion.StateColorMs)
    }

    @Test
    fun aResultStaysReadableForADwellInsteadOfBeingDeletedOnIdle() {
        assertEquals(4_500L, MarbleFeedbackPolicy.dwellMillis(actionRequired = false))
        assertEquals(0L, MarbleFeedbackPolicy.dwellMillis(actionRequired = true))
        assertEquals(MarbleFeedbackPolicy.ActionRequiredDwellMs, 0L)
        assertTrue(MarbleFeedbackPolicy.isOutcome("Clipboard is empty"))
        assertFalse("blank messages are not outcomes", MarbleFeedbackPolicy.isOutcome("   "))
    }

    @Test
    fun productCopyIsOwnedByTheLexiconAndDataIsLeftAlone() {
        listOf(
            "Slide right to connect",
            "Slide left to disconnect",
            "Change server",
            "location not verified",
            "Dismiss this message",
            "Measure the route again",
            "Ambient page motion"
        ).forEach { key ->
            assertTrue("missing Persian pair for: $key", isFaLexiconKey(key))
        }
        assertFalse(
            "a composed sentence about someone's data is not a lexicon key",
            isFaLexiconKey("More actions for Frankfurt-03")
        )
    }
}
