package com.marbleng.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import com.marbleng.app.model.UptimeTicker
import com.marbleng.app.ui.theme.marbleMotion
import com.marbleng.app.ui.theme.marblePanSpec
import com.marbleng.app.ui.theme.marbleSettleSpec
import kotlin.math.roundToInt

// MARBLE_LIVE_TICKER_V249 — the protected-uptime clock, drawn as the instrument it is.
//
// The bug was not that the clock was ugly; it was that the clock was *one string*. Home built
// `"Protected · Uptime 00:10:01"` and handed it to the pill as its subtitle, and the pill animated
// its whole subtitle with one `AnimatedContent`. So every second — sixty times a minute, for as long
// as the tunnel was up — the state word, the separator and the label slid out and back in with the
// one digit that had actually changed. A reading that moves entirely is a reading nobody can follow,
// and the part that mattered (which digit moved) was invisible inside the noise.
//
// This ticker is the fix, and [UptimeTicker] is its arithmetic:
//
//  - one `AnimatedContent` per character, so `00:10:01 → 00:10:02` moves exactly one cell and the
//    other seven hold perfectly still — which is also what makes a minute change *read* as a minute
//    change, because six cells move at once and the eye can see that;
//  - the colons do not roll (their value never changes) so they pulse instead, on the parity of the
//    seconds: a clock whose minute hand is frozen still has to say "I am counting";
//  - when the whole reading changes shape, or every cell changes at once (`00:59:59 → 01:00:00`),
//    the eight independent rolls are replaced by one shared cross-fade, because eight things moving
//    at the same instant is not eight events, it is one;
//  - the row is forced LTR and the digits are asked for tabular figures, so a Persian reading
//    (`۰۰:۱۰:۰۱`) keeps its order in an RTL paragraph and the cells do not jiggle sideways;
//  - the whole clock is one accessibility node: a screen reader says the reading, not eight
//    characters, and says it in the order a person reads a clock.
//
// Everything that decides *how far* and *how long* comes from the app's motion style, so calm
// motion shortens the roll and the platform's "remove animations" renders the resting frame.

/** One roll of a changed digit, and the pulse that sits between two of them. */
private const val ROLL_DURATION_MS = 320
private const val PULSE_DURATION_MS = 420

/** Tabular figures: the difference between a clock and eight digits that shuffle sideways. */
private const val TABULAR_FIGURES = "tnum"

@Composable
fun MarbleRollingClock(
    clock: String,
    color: Color,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall
) {
    val motion = marbleMotion()
    val reading = clock.trim()
    val cells = remember(reading) { UptimeTicker.cells(reading) }
    val separatorCells = remember(reading) { UptimeTicker.separators(reading).toSet() }

    // The previous reading, so this composition knows whether one cell moved, all of them did, or
    // the shape itself changed. `SideEffect` writes after a successful composition, which is what
    // makes reading `.value` here the *previous* frame's value and not this one's.
    val previousReading = remember { mutableStateOf(reading) }
    val rollAsOne = UptimeTicker.reshaped(previousReading.value, reading) ||
        UptimeTicker.rollingCount(previousReading.value, reading) >= cells.size.coerceAtLeast(1)
    SideEffect { previousReading.value = reading }

    // The pulse is derived from the reading, not from a timer: even seconds are lit, odd seconds
    // are dim. It therefore cannot drift out of step with the clock it belongs to, and it costs no
    // coroutine, no infinite transition and no recomposition of its own.
    val lit = remember(reading) { UptimeTicker.pulseLit(reading) }
    val separatorAlpha by animateFloatAsState(
        targetValue = when {
            !motion.loops -> 1f
            lit -> 1f
            else -> UptimeTicker.SEPARATOR_DIM_ALPHA
        },
        animationSpec = marbleSettleSpec(PULSE_DURATION_MS),
        label = "tickerSeparatorPulse"
    )
    val clockStyle = style.copy(
        fontFeatureSettings = TABULAR_FIGURES,
        textAlign = TextAlign.Center
    )

    // The specs are built here and captured, not built inside `transitionSpec`: that lambda is not a
    // composable scope, and a motion style read there would not compile — let alone stay consistent.
    val rollSpec = marblePanSpec<IntOffset>(ROLL_DURATION_MS)
    val fadeSpec = marbleSettleSpec<Float>(ROLL_DURATION_MS)
    // A screen reader gets the reading, once, in reading order: the eight per-character nodes are
    // replaced by this one description instead of being read as "zero zero colon one…".
    val readingDescription = reading

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = modifier.clearAndSetSemantics {
                contentDescription = readingDescription
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            cells.forEachIndexed { index, cell ->
                val isSeparator = index in separatorCells
                val cellColor = if (isSeparator) color.copy(alpha = separatorAlpha) else color
                AnimatedContent(
                    targetState = cell,
                    transitionSpec = {
                        if (motion.still || rollAsOne) {
                            fadeIn(fadeSpec) togetherWith fadeOut(fadeSpec)
                        } else {
                            (
                                slideInVertically(animationSpec = rollSpec) { height ->
                                    (height * UptimeTicker.ROLL_TRAVEL_FRACTION).roundToInt()
                                } + fadeIn(fadeSpec)
                                ) togetherWith (
                                slideOutVertically(animationSpec = rollSpec) { height ->
                                    -(height * UptimeTicker.ROLL_TRAVEL_FRACTION).roundToInt()
                                } + fadeOut(fadeSpec)
                                )
                        }
                    },
                    label = "tickerCell$index"
                ) { value ->
                    Text(
                        text = value.toString(),
                        color = cellColor,
                        style = clockStyle,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
