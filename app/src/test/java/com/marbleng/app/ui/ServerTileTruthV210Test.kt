package com.marbleng.app.ui

import com.marbleng.app.model.BenchmarkResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_SERVER_TILE_TRUTH_V210 — what a compact server box is allowed to claim.
 *
 * Two reports, one rule each:
 *
 *  1. **"Servers that do not ping have to be distinguishable in box view."** The tile drew a
 *     dead server and a live one in the same fill with the same ink, so the one fact the grid
 *     exists to show was the one fact it hid. [ServerTileTruth] answers it, and these tests pin
 *     the answer — including the case that is *not* a verdict: a server nobody has measured is
 *     not a silent server, and dimming it would punish the user for not having run a sweep.
 *  2. **"Long names must not break the layout."** The name slot was two lines, so a 60-character
 *     subscription label made one tile taller than the three beside it and the row stopped being
 *     a row. The slot is one line now and an overflowing name travels through it
 *     ([ServerTileNamePolicy]); the layout half of that fix (equal heights, one-line slot) is
 *     pinned by the source invariants in `scripts/system-integrity-check.py`.
 */
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
        // The whole point of the third answer: no measurement is not evidence of failure.
        assertEquals(
            "an unmeasured box must look exactly like a working one",
            ServerTileTruth.LiveAlpha,
            ServerTileTruth.alphaOf(ServerTileTruth.Reach.UNMEASURED)
        )
        assertEquals("", ServerTileTruth.stateWord(ServerTileTruth.Reach.UNMEASURED))
    }

    @Test
    fun aMeasurementThatClearedTheFloorAnswers() {
        val measured = result(success = 100, latencyMs = 132.0)
        assertEquals(ServerTileTruth.Reach.ANSWERED, ServerTileTruth.reachOf(measured, testing = false))
        assertEquals(ServerTileTruth.LiveAlpha, ServerTileTruth.alphaOf(ServerTileTruth.Reach.ANSWERED))
    }

    @Test
    fun aMeasurementThatFailedGoesQuiet() {
        // The two shapes a failure takes in this product: zero successful samples, and a sample
        // under the honest latency floor (a 0 ms "answer" is not an answer).
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
        // A sweep rewrites the result list as it goes. Without this, a box that failed the last
        // sweep and is being re-measured now would sit faded while its own verdict is pending —
        // and the fade would then animate out again a second later, blinking the whole grid.
        val staleFailure = result(success = 0, latencyMs = 0.0)
        assertEquals(
            ServerTileTruth.Reach.UNMEASURED,
            ServerTileTruth.reachOf(staleFailure, testing = true)
        )
    }

    @Test
    fun aTravellingNameIsOneLineAndMovesAtAReadableSpeed() {
        // One line is the layout guarantee: a name can never add a second row to its box.
        assertEquals(1, ServerTileNamePolicy.MaxLines)
        // Slow enough to read, fast enough to see the end of a 60-character label this decade.
        assertTrue(
            "velocity ${ServerTileNamePolicy.VelocityDp} dp/s",
            ServerTileNamePolicy.VelocityDp in 12..60
        )
        // A gap wide enough to tell the end of the name from its beginning again.
        assertTrue(
            "spacing ${ServerTileNamePolicy.SpacingFraction}",
            ServerTileNamePolicy.SpacingFraction in 0.1f..0.6f
        )
        assertTrue(ServerTileNamePolicy.InfiniteIterations > 0)
    }

    @Test
    fun theGridStillFitsTheNarrowestPhone() {
        // The truth rules must not smuggle a layout change in with them: a tile is still at
        // least 148 dp wide, so a phone still gets two columns and a foldable three.
        assertEquals(2, ServerTilePolicy.columnsFor(360 - 32))
        assertEquals(3, ServerTilePolicy.columnsFor(720 - 32))
    }
}
