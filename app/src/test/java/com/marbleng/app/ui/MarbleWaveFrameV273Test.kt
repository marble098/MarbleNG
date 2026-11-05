package com.marbleng.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * MARBLE_EXPRESSIVE_WAVE_V273 — the wave's geometry, on the host JVM.
 *
 * The report asked for one thing in two halves, and both halves are arithmetic rather than taste:
 *
 *  * the animation must be Material 3 Expressive's wave — the shape the platform's own
 *    `WavyProgressIndicator` draws — and not a hand-rolled ball, spinner or stripe;
 *  * it must occupy **exactly** the frame it is drawn into: not a 240 dp strip inside a 360 dp card
 *    (which is what a caller `fillMaxWidth()` produced over the platform composable, because that
 *    composable wraps a `Spacer` of fixed [LinearContainerWidth]), and not a stroke that leaks past
 *    a rounded corner.
 *
 * [MarbleWaveBounds] is the arithmetic that makes the second half measurable without a device, and
 * the source assertions below make the first half a property of the whole app rather than of one
 * screen: there is no Material progress indicator and no `LoadingIndicator` left anywhere under
 * `src/main/java`, so every progress surface in the product is this wave.
 */
class MarbleWaveFrameV273Test {

    private fun main(path: String): String =
        File("src/main/java/com/marbleng/app/$path").readText()

    private fun uiSources(): List<File> =
        File("src/main/java/com/marbleng/app").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

    // -------------------------------------------------------------------------------------
    // The bounds: what the wave is allowed to paint
    // -------------------------------------------------------------------------------------

    @Test
    fun theStrokeIsAReadableFractionOfTheFrameAndNeverWiderThanIt() {
        // A 3 dp frame still has to show a stroke…
        assertEquals(1.65f, MarbleWaveBounds.strokeDp(3f), 0.0001f)
        // …an 12 dp frame gets the thick end of the range…
        assertEquals(6.6f, MarbleWaveBounds.strokeDp(12f), 0.0001f)
        // …and past 12 dp the frame stops earning extra ink: a fat bar is not more Expressive.
        assertEquals(
            MarbleWaveBounds.strokeDp(12f),
            MarbleWaveBounds.strokeDp(40f),
            0.0001f
        )
        // Never wider than the frame it will actually be drawn in — the frame is [trackDp] of the
        // request, which is why a 1 dp request still gets a 3 dp box and a 1.65 dp stroke.
        listOf(0f, 0.5f, 1f, 3f, 7f, 12f, 26f, 40f, 240f).forEach { frame ->
            val drawn = MarbleWaveBounds.trackDp(frame)
            val stroke = MarbleWaveBounds.strokeDp(frame)
            assertTrue("stroke $stroke > frame $drawn", stroke <= drawn)
            assertTrue("stroke must be positive", stroke > 0f)
        }
    }

    @Test
    fun theTrackIsAsThickAsTheFrameAllowsWithoutSwallowingTheWave() {
        assertEquals(3f, MarbleWaveBounds.trackDp(0f), 0.0001f)
        assertEquals(3f, MarbleWaveBounds.trackDp(3f), 0.0001f)
        assertEquals(12f, MarbleWaveBounds.trackDp(40f), 0.0001f)
        listOf(4f, 8f, 11f).forEach { frame ->
            val track = MarbleWaveBounds.trackDp(frame)
            assertTrue("track $track > frame $frame", track <= frame)
            assertTrue("the stroke sits inside the track", MarbleWaveBounds.strokeDp(frame) <= track)
        }
    }

    @Test
    fun theWavelengthFitsTheFrameAndStaysAVisibleWave() {
        // A 26 dp dot gets one crest, not a flat line…
        assertEquals(14f, MarbleWaveBounds.wavelengthDp(26f, 3f), 0.0001f)
        // …a long rail gets one wave per four frames of depth…
        assertEquals(48f, MarbleWaveBounds.wavelengthDp(400f, 12f), 0.0001f)
        // …and the wavelength is never longer than the rail it rides on.
        listOf(14f, 16f, 60f, 240f).forEach { width ->
            val wavelength = MarbleWaveBounds.wavelengthDp(width, 6f)
            assertTrue("wavelength $wavelength > width $width", wavelength <= width)
            assertTrue("a wavelength must exist", wavelength > 0f)
        }
        // Even a rail narrower than one wavelength still gets the minimum, never a zero — a zero
        // wavelength is a straight line drawn by a divide-by-zero.
        assertEquals(
            MarbleWaveBounds.MIN_WAVELENGTH_DP,
            MarbleWaveBounds.wavelengthDp(4f, 6f),
            0.0001f
        )
    }

    // -------------------------------------------------------------------------------------
    // The frame: every wave is clipped to the box it was given
    // -------------------------------------------------------------------------------------

    @Test
    fun everyPrimitiveIsInscribedInItsBox() {
        val wave = main("ui/MarbleWave.kt")
        // Four primitives, four clips: a wave that is allowed to paint outside its bounds is
        // exactly the bug the report described (ring shading past a 22 dp circle, rail bleeding
        // past a rounded card).
        assertEquals(4, Regex("clipToBounds\\(\\)").findAll(wave).count())
        // …and each one reports itself to accessibility as a progress bar of its own box.
        assertTrue(Regex("progressBarRangeInfo").findAll(wave).count() >= 4)
        // The public surface is the primitive set, not a generic "wave anything" helper.
        assertTrue("fun MarbleWaveProgressIndicator(" in wave)
        assertTrue("fun MarbleWaveActivity(" in wave)
        assertTrue("fun MarbleWaveRing(" in wave)
        assertTrue("fun MarbleWaveRail(" in wave)
        assertTrue("object MarbleWaveBounds" in wave)
    }

    @Test
    fun theWaveIsTheOnlyProgressIdiomLeftInTheProduct() {
        val sources = uiSources()
        assertTrue("expected to read the whole tree", sources.size > 100)
        sources.forEach { file ->
            val text = file.readText()
            assertFalse(
                "${file.name} still draws Material's progress indicator",
                "LinearProgressIndicator(" in text || "CircularProgressIndicator(" in text
            )
            assertFalse(
                "${file.name} still draws the retired morphing spinner",
                "LoadingIndicator(" in text
            )
        }
    }

    @Test
    fun theplanRailAndTheDockRailAreTheWave() {
        // The two railed surfaces the report named by hand: the plan bar on a subscription card and
        // the batch bar inside the dock island. Both keep the geometry V264 measured and both draw
        // the wave between those two numbers.
        val cards = main("ui/MarblePlanCards.kt")
        val wings = main("ui/MarbleDockWings.kt")
        assertTrue("MarbleWaveRail(" in cards)
        assertTrue("MarbleWaveRail(" in wings)
        // The island's bar is still 26 × 3 dp: a wave does not get to grow the island.
        assertTrue(".width(26.dp)" in wings)
        assertTrue("height = 3.dp" in wings)
        // The wave is not re-declared anywhere: one owner, many callers.
        assertEquals(
            1,
            uiSources().count { "fun MarbleWaveProgressIndicator(" in it.readText() }
        )
    }
}
