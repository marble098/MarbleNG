package com.marbleng.app.ui

import com.marbleng.app.model.BenchmarkResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerTileTruthV210Test {
    private fun result(success: Int, latencyMs: Double): BenchmarkResult = BenchmarkResult(
        profileId = "node-1",
        name = "node-1",
        success = success,
        latencyMs = latencyMs,
        bytesPerSecond = 0.0,
        score = 0.0
    )

    @Test
    fun aServerNobodyHasMeasuredIsNotASilentServer() {
        assertEquals(ServerTileTruth.Reach.UNMEASURED, ServerTileTruth.reachOf(null, testing = false))

        assertEquals(
            "an unmeasured box must look exactly like a working one",
            ServerTileTruth.LiveAlpha,
            ServerTileTruth.alphaOf(ServerTileTruth.Reach.UNMEASURED),
            0f
        )
        assertEquals("", ServerTileTruth.stateWord(ServerTileTruth.Reach.UNMEASURED))
    }

    @Test
    fun aMeasurementThatClearedTheFloorAnswers() {
        val measured = result(success = 100, latencyMs = 132.0)
        assertEquals(ServerTileTruth.Reach.ANSWERED, ServerTileTruth.reachOf(measured, testing = false))
        assertEquals(
            ServerTileTruth.LiveAlpha,
            ServerTileTruth.alphaOf(ServerTileTruth.Reach.ANSWERED),
            0f
        )
    }

    @Test
    fun aMeasurementThatFailedGoesQuiet() {
        val unreachable = result(success = 0, latencyMs = 0.0)
        val implausible = result(success = 100, latencyMs = 4.0)
        assertEquals(ServerTileTruth.Reach.SILENT, ServerTileTruth.reachOf(unreachable, testing = false))
        assertEquals(ServerTileTruth.Reach.SILENT, ServerTileTruth.reachOf(implausible, testing = false))
        assertTrue(
            "the floor is one number, read from one place",
            ServerTileTruth.LatencyFloorMs == 20
        )
    }

    @Test
    fun aSilentBoxFadesButStaysReadable() {
        val alpha = ServerTileTruth.alphaOf(ServerTileTruth.Reach.SILENT)
        assertTrue("a silent box has to recede: $alpha", alpha < ServerTileTruth.LiveAlpha)
        assertTrue("a silent box still has to be readable: $alpha", alpha >= 0.35f)
        assertEquals("No response", ServerTileTruth.stateWord(ServerTileTruth.Reach.SILENT))
    }

    @Test
    fun aBoxBeingMeasuredNeverFadesHalfwayThroughItsOwnSweep() {
        val staleFailure = result(success = 0, latencyMs = 0.0)
        assertEquals(
            ServerTileTruth.Reach.UNMEASURED,
            ServerTileTruth.reachOf(staleFailure, testing = true)
        )
    }

    @Test
    fun aTravellingNameIsOneLineAndMovesAtAReadableSpeed() {
        assertEquals(1, ServerTileNamePolicy.MaxLines)

        assertTrue(
            "velocity ${ServerTileNamePolicy.VelocityDp} dp/s",
            ServerTileNamePolicy.VelocityDp in 12..60
        )

        assertTrue(
            "spacing ${ServerTileNamePolicy.SpacingFraction}",
            ServerTileNamePolicy.SpacingFraction in 0.1f..0.6f
        )
        assertTrue(ServerTileNamePolicy.InfiniteIterations > 0)
    }

    @Test
    fun theTruthRulesDoNotSmuggleInALayoutChange() {
        assertEquals(2, ServerTilePolicy.columnsFor(360 - 32))
        assertEquals(ServerTilePolicy.MaxColumns, ServerTilePolicy.columnsFor(720 - 32))
        assertEquals(ServerTilePolicy.MaxColumns, ServerTilePolicy.columnsFor(2000))

        assertEquals(1, ServerTilePolicy.columnsFor(0))
        assertTrue(
            "a tile may never be narrower than the floor it is laid out by",
            ServerTilePolicy.MinTileWidthDp >= 140
        )
    }
}
