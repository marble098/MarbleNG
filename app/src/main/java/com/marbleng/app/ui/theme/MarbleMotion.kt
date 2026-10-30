package com.marbleng.app.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import kotlin.math.roundToInt

// MARBLE_M3_EXPRESSIVE_V240 — expressive spring motion, one access point.
//
// The theme installs MotionScheme.expressive(): every M3 component (buttons, sheets, menus,
// navigation indicator, FAB menu) then moves on physics springs with no per-call-site spec.
// These helpers are for the app's own animations — hero springs, deck lights, press morphs,
// list transitions — so no screen ever reaches for a raw tween for UI motion again.
//
// Two families, per the M3 motion guidance:
//   spatial springs — position, size, shape (may overshoot: arrival has energy)
//   effects springs — color, alpha (settle without overshoot: fades stay calm)
//
// Reduced motion is honoured through the platform animator-duration scale: when the user
// (or a test, or battery saver) turns animations off, looping and decorative motion
// renders its resting frame instead of animating.

@Composable
@ReadOnlyComposable
fun <T> marbleDefaultSpatialSpec(): FiniteAnimationSpec<T> =
    MaterialTheme.motionScheme.defaultSpatialSpec()

@Composable
@ReadOnlyComposable
fun <T> marbleFastSpatialSpec(): FiniteAnimationSpec<T> =
    MaterialTheme.motionScheme.fastSpatialSpec()

@Composable
@ReadOnlyComposable
fun <T> marbleSlowSpatialSpec(): FiniteAnimationSpec<T> =
    MaterialTheme.motionScheme.slowSpatialSpec()

@Composable
@ReadOnlyComposable
fun <T> marbleDefaultEffectsSpec(): FiniteAnimationSpec<T> =
    MaterialTheme.motionScheme.defaultEffectsSpec()

@Composable
@ReadOnlyComposable
fun <T> marbleFastEffectsSpec(): FiniteAnimationSpec<T> =
    MaterialTheme.motionScheme.fastEffectsSpec()

@Composable
@ReadOnlyComposable
fun <T> marbleSlowEffectsSpec(): FiniteAnimationSpec<T> =
    MaterialTheme.motionScheme.slowEffectsSpec()

/**
 * True when the platform animator-duration scale is zero — the user's "remove animations"
 * signal. Decorative loops (deck lights, breathing halos, travelling waves) check this and
 * render one calm frame instead.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        }.getOrDefault(false)
    }
}

// ---------------------------------------------------------------------------------------------
// MARBLE_MAP_MOTION_V249 — the app's own motion, in one place, with one switch.
//
// `MotionScheme.expressive()` gives every Material component its physics, and the helpers above
// give the app's own animations a spec to borrow. What neither could give is *consistency*: each
// screen picked its own duration and its own curve, so the dock slid on one timing, the hero
// sprang on another and the tabs simply swapped. Motion that disagrees between surfaces reads as
// several apps sharing a package name.
//
// This layer is the one answer. It is a CompositionLocal rather than a parameter because a motion
// style is ambient — every screen reads it and none of them owns it — and it carries two numbers
// instead of a catalogue:
//
//   [MarbleMotionStyle.scale]  multiplies every duration and every travel distance. "Calm motion"
//                              is 0.6: the same choreography, shorter and smaller, for a reader
//                              who wants the continuity without the movement.
//   [MarbleMotionStyle.still]  renders the resting frame instead of animating. It follows the
//                              platform's animator scale, so the phone's own accessibility setting
//                              keeps working through the app's motion rather than around it.
//
// The curves are Material 3's emphasized family, which is what makes a flight read as one object
// moving: it leaves quickly ([MarbleEasing.DEPARTURE]) and arrives slowly ([MarbleEasing.ARRIVAL]),
// and a pan between two pages uses both halves of the same curve ([MarbleEasing.PAN]) so the pair
// of pages accelerate and settle together instead of each running its own tween.
// ---------------------------------------------------------------------------------------------

/** The emphasized curves, as Material 3 defines them: fast out of the origin, slow into the rest. */
object MarbleEasing {
    /** Emphasized accelerate — what a page does when it leaves. */
    val DEPARTURE: Easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

    /** Emphasized decelerate — what a page does when it arrives. */
    val ARRIVAL: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    /** Emphasized — the whole curve, for one object travelling between two resting places. */
    val PAN: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    /** Standard — for fades and tints, where a curve with energy would look like a flicker. */
    val SETTLE: Easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
}

@Immutable
data class MarbleMotionStyle(
    val scale: Float = 1f,
    val still: Boolean = false
) {
    /** Every duration in the app goes through here, so "calm" is one number and not forty edits. */
    fun duration(baseMs: Int): Int =
        if (still) 0 else (baseMs * scale).roundToInt().coerceAtLeast(0)

    /** Every travel distance goes through here, so a calm pan is a short pan, not a slow one. */
    fun travel(fraction: Float): Float = if (still) 0f else fraction * scale

    /** A looping animation is decorative by definition: calm motion and reduced motion both stop it. */
    val loops: Boolean get() = !still && scale >= 1f
}

/** "Calm motion" from the settings page: the same choreography at six tenths the distance. */
const val MARBLE_CALM_MOTION_SCALE: Float = 0.6f

/**
 * MARBLE_MAP_MOTION_V249 — how long the chrome takes to leave or return: the dock while a page is
 * scrolling, the transient message pill, the sheet handles. One number for all of them, because the
 * point of the layer is that a reader can learn the app's tempo.
 */
const val MARBLE_CHROME_FLIGHT_MS: Int = 320

val LocalMarbleMotion = staticCompositionLocalOf { MarbleMotionStyle() }

/** The motion style every screen reads. Ambient, like the colour scheme it sits beside. */
@Composable
@ReadOnlyComposable
fun marbleMotion(): MarbleMotionStyle = LocalMarbleMotion.current

@Composable
fun ProvideMarbleMotion(
    calmMotion: Boolean,
    systemReducedMotion: Boolean,
    content: @Composable () -> Unit
) {
    val style = remember(calmMotion, systemReducedMotion) {
        MarbleMotionStyle(
            scale = if (calmMotion) MARBLE_CALM_MOTION_SCALE else 1f,
            still = systemReducedMotion
        )
    }
    CompositionLocalProvider(LocalMarbleMotion provides style, content = content)
}

/**
 * The spec for something that *moves*: a page pan, a digit's roll, a dock flight, a card that
 * changes place.
 *
 * It is generic over the animated type because the same curve has to serve a `Float` alpha, an
 * `IntOffset` slide and a `Dp` inset, and three near-identical helpers would be three places for
 * the timings to drift apart. Position never overshoots here — an overshooting page looks like it
 * bounced off the next tab — so the spatial springs above stay for components and this is what
 * surfaces travel on.
 *
 * The pair below is the same rule without the CompositionLocal, for the one place a spec has to be
 * built outside a composable scope: `AnimatedContent.transitionSpec`, which learns the distance of
 * a flight only once the transition starts. Reading the style once in the body and calling
 * [MarbleMotionStyle.panSpec] inside the lambda keeps both halves of the app on one curve.
 */
fun <T> MarbleMotionStyle.panSpec(baseDurationMs: Int): FiniteAnimationSpec<T> =
    if (still) snap() else tween(duration(baseDurationMs), easing = MarbleEasing.PAN)

/** The spec for something that *fades*: a tint, an alpha, a highlight leaving a row. */
fun <T> MarbleMotionStyle.settleSpec(baseDurationMs: Int): FiniteAnimationSpec<T> =
    if (still) snap() else tween(duration(baseDurationMs), easing = MarbleEasing.SETTLE)

@Composable
@ReadOnlyComposable
fun <T> marblePanSpec(baseDurationMs: Int): FiniteAnimationSpec<T> =
    LocalMarbleMotion.current.panSpec(baseDurationMs)

@Composable
@ReadOnlyComposable
fun <T> marbleSettleSpec(baseDurationMs: Int): FiniteAnimationSpec<T> =
    LocalMarbleMotion.current.settleSpec(baseDurationMs)
