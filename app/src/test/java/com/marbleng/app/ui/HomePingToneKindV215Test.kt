package com.marbleng.app.ui

import com.marbleng.app.model.ConnectionPingState
import org.junit.Assert.assertEquals
import org.junit.Test

class HomePingToneKindV215Test {
    @Test
    fun aProvisionalRunKeepsTheMeasuringToneWhateverTheSample() {
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
