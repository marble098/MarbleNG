package com.marbleng.app.ui

import com.marbleng.app.model.ConnectionPingState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * MARBLE_HOME_PING_COLOUR_V215 — the Home ping keeps one colour for the whole of a run.
 *
 * Every sample of a run used to recolour the reading by the band it landed in, so the two ping
 * icons changed colour on each sample. The band is a verdict on a settled run only. These tests pin
 * that: however many provisional samples arrive, while the run is in progress the colour kind is the
 * measuring one, and the band applies only once the run is MEASURED.
 */
class HomePingToneKindV215Test {

    @Test
    fun aProvisionalRunKeepsTheMeasuringToneWhateverTheSample() {
        // Fast, middling and slow samples all land on the same colour while the run is open.
        listOf(1, 20, 90, 260, 900).forEach { sample ->
            assertEquals(
                "sample $sample ms",
                HomePingToneKind.MEASURING,
                homePingToneKind(ConnectionPingState.MEASURING, sample, provisional = true)
            )
        }
    }

    @Test
    fun aProvisionalFailureIsNotYetAFailureColour() {
        assertEquals(
            HomePingToneKind.MEASURING,
            homePingToneKind(ConnectionPingState.FAILED, 120, provisional = true)
        )
    }

    @Test
    fun theBandAppliesOnlyOnceTheRunIsMeasured() {
        assertEquals(
            HomePingToneKind.BAND,
            homePingToneKind(ConnectionPingState.MEASURED, 120, provisional = false)
        )
    }

    @Test
    fun aSettledFailureIsTheFailureColour() {
        assertEquals(
            HomePingToneKind.FAILED,
            homePingToneKind(ConnectionPingState.FAILED, 0, provisional = false)
        )
        assertEquals(
            HomePingToneKind.FAILED,
            homePingToneKind(ConnectionPingState.MEASURED, 0, provisional = false)
        )
    }

    @Test
    fun idleIsIdle() {
        assertEquals(HomePingToneKind.IDLE, homePingToneKind(ConnectionPingState.IDLE, 0, provisional = false))
    }
}
