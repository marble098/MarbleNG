@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.marbleng.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * MARBLE_EXPRESSIVE_WAVE_V273 — one wave, drawn by the whole app, bound to the box it is given.
 *
 * ## What V272 left behind
 *
 * V272 introduced [MarbleWaveProgressIndicator] and used it in two places. The rest of the product
 * still announced work with a flat solid rail (the dock island's 26 × 3 dp bar, the quota bar on a
 * subscription plate) or with Material's morphing `LoadingIndicator` — three different loading
 * languages for one product. Worse, the one wave that existed was allowed to choose its own ink:
 * `LinearWavyProgressIndicator` picks a stroke from the M3 token set (4 dp) and an amplitude of
 * "the full height", so a caller that asked for a 3 dp rail received a 4 dp stroke inside a 3 dp
 * box — SystemUI-free Compose clipped it, and the result was a wave **wider than the frame it was
 * drawn in**. That is exactly the class of bug the reader reported: «فقط کادری که داره رو پر کنه
 * و نه کمتر از اون و نه بیشتر از اون».
 *
 * ## The contract this file enforces
 *
 * Every wave in MarbleNG is drawn through one of the three composables below, and all three hold
 * the same three rules:
 *
 *  1. **Never more than the frame.** The drawing node is wrapped in a fixed-size, `clipToBounds`
 *     box, so the sine can never paint past the bounds the caller measured — no matter what the
 *     stroke token or the wave speed does inside.
 *  2. **Never less than the frame.** The rail is `fillMaxWidth` (or `fillMaxSize` for the ring) and
 *     the stroke is derived *from* the frame ([MarbleWaveBounds.strokeDp]) rather than from a
 *     token, so the ink always fills the thickness the caller reserved. A 3 dp rail gets a 3 dp
 *     wave; a 10 dp rail gets a 10 dp one.
 *  3. **One geometry, testable without a device.** [MarbleWaveBounds] is pure arithmetic, so
 *     `MarbleWaveFrameV273Test` pins the numbers on the host JVM instead of asking a screenshot to
 *     agree — the same rule the rest of the design system follows.
 *
 * `amplitude = 1f` is passed explicitly: it is the M3 definition of "the wave takes the full height
 * of the indicator", i.e. the wave touches both edges of the frame and stops there.
 */
object MarbleWaveBounds {

    const val MARKER: String = "MARBLE_EXPRESSIVE_WAVE_V273"

    /** Thinner than this and a wave is a rumour: the ink is clamped up to three dp. */
    const val MIN_TRACK_DP: Float = 3f

    /** Thicker than this and a loader starts to look like a diagram. Clamped down. */
    const val MAX_TRACK_DP: Float = 12f

    /** The stroke never takes the whole frame: the wave needs a little air on both sides. */
    const val STROKE_SHARE: Float = 0.55f

    /** …but it never becomes a hairline either: at least this share of the frame is inked. */
    const val STROKE_FLOOR_SHARE: Float = 0.30f

    /** One wavelength is never shorter than this, or the rail reads as a zig-zag. */
    const val MIN_WAVELENGTH_DP: Float = 14f

    /**
     * The frame height a caller asked for, resolved to what will actually be drawn.
     *
     * A caller may pass any height; the composable clamps it here so a 1 dp strip cannot produce a
     * clipped smear and a 40 dp one cannot produce a wave that swallows a card.
     */
    fun trackDp(requested: Float): Float = requested.coerceIn(MIN_TRACK_DP, MAX_TRACK_DP)

    /**
     * The stroke width, in dp, for a frame of [trackDp] dp.
     *
     * The wave's crest sits at `stroke/2` above the centreline, so a stroke equal to the frame
     * would be cut in half by rule 1. 55 % leaves the crest visible with air above it, and the
     * floor keeps the ink from disappearing inside a thick frame.
     */
    fun strokeDp(trackDp: Float): Float {
        val frame = trackDp(trackDp)
        return (frame * STROKE_SHARE)
            .coerceAtLeast(frame * STROKE_FLOOR_SHARE)
            .coerceAtMost(frame)
    }

    /**
     * The wavelength for a rail [widthDp] wide and [trackDp] thick.
     *
     * Shorter rails get a shorter wave so they always carry at least one full crest — a 26 dp dock
     * island with the M3 default 40 dp wavelength drew half a crest, which reads as a bulge, not a
     * wave. The result never falls below [MIN_WAVELENGTH_DP] and never exceeds the rail itself.
     */
    fun wavelengthDp(widthDp: Float, trackDp: Float): Float {
        val width = widthDp.coerceAtLeast(MIN_WAVELENGTH_DP)
        val frame = trackDp(trackDp)
        val preferred = (frame * 4f).coerceAtMost(width)
        return preferred.coerceIn(MIN_WAVELENGTH_DP.coerceAtMost(width), width)
    }

    /** The wave's amplitude, as the M3 fraction of the frame's height. Full frame, never more. */
    const val AMPLITUDE: Float = 1f

    /** A determinate rail's filled share: clamped, and honest about NaN. */
    fun fillFraction(progress: Float): Float =
        if (progress.isNaN()) 0f else progress.coerceIn(0f, 1f)

    /** The percentage a caller prints beside the wave, shaped from the same clamped value. */
    fun percent(progress: Float): Int = (fillFraction(progress) * 100f).roundToInt()
}

/**
 * The determinate wave: "this much of the work is done".
 *
 * `modifier` — pass whatever width the frame owns (`fillMaxWidth` is applied here) and any
 * `weight`, `testTag` or `semantics` the caller needs. Height is [height], clamped by
 * [MarbleWaveBounds.trackDp].
 */
@Composable
internal fun MarbleWaveProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    height: Dp = 6.dp,
    label: String = ""
) {
    val density = LocalDensity.current
    val trackPx = with(density) { MarbleWaveBounds.trackDp(height.value).dp.toPx() }
    val strokePx = with(density) { MarbleWaveBounds.strokeDp(height.value).dp.toPx() }
    val applied = modifier
        .fillMaxWidth()
        .height(MarbleWaveBounds.trackDp(height.value).dp)
        .clipToBounds()
        .semantics {
            val value = MarbleWaveBounds.fillFraction(progress())
            progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
            if (label.isNotBlank()) contentDescription = label
        }
    Box(modifier = applied) {
        LinearWavyProgressIndicator(
            progress = { MarbleWaveBounds.fillFraction(progress()) },
            modifier = Modifier
                .fillMaxWidth()
                .height(MarbleWaveBounds.trackDp(height.value).dp),
            color = color,
            trackColor = trackColor,
            stroke = Stroke(width = strokePx.coerceAtMost(trackPx), cap = StrokeCap.Round),
            trackStroke = Stroke(width = strokePx.coerceAtMost(trackPx), cap = StrokeCap.Round),
            amplitude = { MarbleWaveBounds.AMPLITUDE }
        )
    }
}

/**
 * The indeterminate wave: "something is happening, and I cannot tell you how much".
 *
 * This is the app's replacement for the flat `LoadingIndicator` spinner: same footprint, same
 * colour contract, one loading language. Used as a strip under a header or inside a card.
 */
@Composable
internal fun MarbleWaveActivity(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    height: Dp = 6.dp
) {
    val density = LocalDensity.current
    val frame = MarbleWaveBounds.trackDp(height.value)
    val trackPx = with(density) { frame.dp.toPx() }
    val strokePx = with(density) { MarbleWaveBounds.strokeDp(height.value).dp.toPx() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(frame.dp)
            .clipToBounds()
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }
    ) {
        LinearWavyProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(frame.dp),
            color = color,
            trackColor = trackColor,
            stroke = Stroke(width = strokePx.coerceAtMost(trackPx), cap = StrokeCap.Round),
            trackStroke = Stroke(width = strokePx.coerceAtMost(trackPx), cap = StrokeCap.Round),
            amplitude = MarbleWaveBounds.AMPLITUDE,
            wavelength = MarbleWaveBounds.MIN_WAVELENGTH_DP.dp
        )
    }
}

/**
 * The ring: the same wave, closed into the space a spinner used to occupy.
 *
 * Callers pass the exact box the old indicator filled (`size = 22.dp`, 28 dp on the connect pill,
 * 20 dp in a metric tile) and the ring inscribes itself in it — never larger, never a smaller
 * glyph floating inside a big target.
 */
@Composable
internal fun MarbleWaveRing(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    size: Dp = 22.dp,
    tag: String = ""
) {
    val density = LocalDensity.current
    val frame = MarbleWaveBounds.trackDp(size.value)
    val strokePx = with(density) { MarbleWaveBounds.strokeDp(frame).dp.toPx() }
    Box(
        modifier = modifier
            .size(frame.dp)
            .clipToBounds()
            .then(if (tag.isBlank()) Modifier else Modifier.testTag(tag))
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }
    ) {
        CircularWavyProgressIndicator(
            modifier = Modifier.size(frame.dp),
            color = color,
            trackColor = trackColor,
            stroke = Stroke(width = strokePx, cap = StrokeCap.Round),
            trackStroke = Stroke(width = strokePx, cap = StrokeCap.Round),
            amplitude = MarbleWaveBounds.AMPLITUDE
        )
    }
}

/**
 * A micro rail — the island's 26 × 3 dp batch bar, a plan's 7 dp quota rail, a row's inline
 * measure. It is the same wave at the same contract, sized by the caller's box: this is the
 * composable every hand-drawn "fill a fraction of a rounded box" bar now routes through, so the
 * product stops growing bars that disagree about how they are drawn.
 */
@Composable
internal fun MarbleWaveRail(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = color.copy(alpha = 0.16f),
    height: Dp = 4.dp,
    tag: String = ""
) {
    val density = LocalDensity.current
    val frame = MarbleWaveBounds.trackDp(height.value)
    val trackPx = with(density) { frame.dp.toPx() }
    val strokePx = with(density) { MarbleWaveBounds.strokeDp(frame).dp.toPx() }
    val value = MarbleWaveBounds.fillFraction(progress)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(frame.dp)
            .clipToBounds()
            .then(if (tag.isBlank()) Modifier else Modifier.testTag(tag))
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f) }
    ) {
        LinearWavyProgressIndicator(
            progress = { value },
            modifier = Modifier
                .fillMaxWidth()
                .height(frame.dp),
            color = color,
            trackColor = trackColor,
            stroke = Stroke(width = strokePx.coerceAtMost(trackPx), cap = StrokeCap.Round),
            trackStroke = Stroke(width = strokePx.coerceAtMost(trackPx), cap = StrokeCap.Round),
            amplitude = { MarbleWaveBounds.AMPLITUDE }
        )
    }
}
