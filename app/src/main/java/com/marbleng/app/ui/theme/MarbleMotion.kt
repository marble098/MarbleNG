package com.marbleng.app.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

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
