package com.marbleng.app.ui

// MARBLE_SURFACE_DEPTH_V212 — one depth contract, every surface.
//
// The report: *"the app looks dry and unattractive."*
//
// Where that actually lives in the code. The Home cards left the flat plane in
// MARBLE_HOME_CLOUD_DEPTH_V191: one cool brand-tinted shadow, a gradient rim that catches light at
// the top, and a whisper wash inside the top edge. But that treatment was written *inside*
// [HomeCloudCard] — as a body, not as a contract. Every surface that is not a Home card (the server
// boxes, the settings hub's cards, the route tiles) is still `background(fill)` plus a 1 dp
// `border`, i.e. the pre-V191 plane. So one page can show a card that sits on a light and, four
// pixels below it, a card that does not — and the eye reads that inconsistency as cheapness rather
// than as two different components.
//
// This file extracts the V191 contract into a modifier so any surface can wear it, and the numbers
// live in one place ([MarbleDepthPolicy]) instead of being re-derived per call site. It adds no new
// effect: the shadow, the rim and the wash are the same three channels HomeCloudCard already uses,
// under the same rules — never a grey shadow, never a second translucent plane.
//
// What this file deliberately does *not* do:
//  - no blur, no backdrop layers, no nested translucency. V187 removed those because stacked
//    translucency on a busy page reads as fog, and that reasoning is unchanged.
//  - no per-surface tuning. A depth contract every call site can adjust is three releases from
//    being three different depths again.

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The numbers of the depth contract.
 *
 * Two lift steps is all the product has: a resting surface and a raised one. A third would need a
 * third meaning, and "slightly more raised than resting" is not one.
 */
object MarbleDepthPolicy {
    /** Elevation of a surface nobody has chosen. */
    val RestingElevation: Dp = 3.dp

    /** Elevation of a surface the user chose, or that is carrying traffic. */
    val RaisedElevation: Dp = 7.dp

    /** How far down the inside of the top edge the whisper wash reaches. */
    val WashReach: Dp = 110.dp

    /** Wash strength inside a top edge, as a fraction of the brand hue. */
    const val WashAlphaDark: Float = .055f
    const val WashAlphaLight: Float = .035f

    /** How much of the brand hue the rim picks up at the top, Dark. */
    const val LitRimDark: Float = .30f

    /** How much of white the rim picks up at the top, Light — a highlight, not a border. */
    const val LitRimLight: Float = .55f

    /** How much a surface recedes while the user is dragging it away, at the point of no return. */
    const val BackScale: Float = .08f

    /** How much it fades on the same drag. */
    const val BackFade: Float = .25f
}

/**
 * MARBLE_SURFACE_DEPTH_V212 — the Prism depth contract, applied to any surface.
 *
 * [fill] and [rim] stay the caller's: a selected card still selects in its own colour, a live one
 * still rims in emerald, and a silent server still recedes. This modifier only answers the one
 * question a call site should not have to answer: *does this box sit on a light?*
 *
 * Order matters and is part of the contract — shadow, clip, fill, wash, rim — because the shadow
 * has to be cast by the unclipped outline, and the rim has to be drawn last so it catches light on
 * top of everything else.
 *
 * @param lifted raises the surface one step, on the product's own float spec, so a selection
 *   change reads as the card lifting rather than as the card being repainted.
 */
@Composable
internal fun Modifier.marbleSurfaceDepth(
    shape: Shape,
    fill: Color,
    rim: Color,
    lifted: Boolean = false
): Modifier {
    val dark = homeCloudDark()
    val lift by animateDpAsState(
        targetValue = if (lifted) {
            MarbleDepthPolicy.RaisedElevation
        } else {
            MarbleDepthPolicy.RestingElevation
        },
        animationSpec = MarbleMotionSpecs.Dp,
        label = "marble-surface-depth-lift"
    )
    // MARBLE_THEME_COHERENCE_V202 — a wallpaper palette must not inherit the brand's fixed navy:
    // every channel below derives from the live ramp when Phone colours is selected.
    val shadowAmbient = when {
        Aether.IsDynamic -> Aether.FloatShadow.copy(alpha = if (dark) .28f else .18f)
        dark -> Color(0xFF001144).copy(alpha = .34f)
        else -> Color(0xFF0A2540).copy(alpha = .20f)
    }
    val shadowSpot = when {
        Aether.IsDynamic -> Aether.Cyan.copy(alpha = if (dark) .28f else .22f)
        dark -> HomeCloud.Accent.copy(alpha = .30f)
        else -> Color(0xFF1E5FAF).copy(alpha = .26f)
    }
    // The gradient rim catches the light at the top and fades to the caller's own hairline at the
    // bottom: light hitting an edge, not a second border.
    val litRim = if (dark) {
        lerp(rim, HomeCloud.Accent, MarbleDepthPolicy.LitRimDark)
    } else {
        lerp(rim, Color.White, MarbleDepthPolicy.LitRimLight)
    }
    val rimBrush = Brush.verticalGradient(listOf(litRim, rim))
    val washTop = when {
        Aether.IsDynamic -> Aether.Cyan.copy(alpha = if (dark) .055f else .035f)
        dark -> HomeCloud.Accent.copy(alpha = MarbleDepthPolicy.WashAlphaDark)
        else -> Color(0xFF3399FF).copy(alpha = MarbleDepthPolicy.WashAlphaLight)
    }
    val washEndPx = with(LocalDensity.current) { MarbleDepthPolicy.WashReach.toPx() }
    return this
        .shadow(
            elevation = lift,
            shape = shape,
            clip = false,
            ambientColor = shadowAmbient,
            spotColor = shadowSpot
        )
        .clip(shape)
        .background(fill)
        .background(
            Brush.verticalGradient(
                colors = listOf(washTop, Color.Transparent),
                startY = 0f,
                endY = washEndPx
            )
        )
        .border(1.dp, rimBrush, shape)
}

/**
 * MARBLE_SURFACE_DEPTH_V212 — the predictive-back treatment.
 *
 * Android's predictive back is a gesture with a *progress*, not an edge: the system hands the app
 * a 0→1 value while the finger travels. An app that ignores it snaps at the moment the gesture
 * commits while everything around it was already moving — which on this product would have been a
 * new discontinuity, because the rest of the UI animates on the shared frame clock.
 *
 * This is the platform's own answer to that progress: the surface being left recedes. It does not
 * slide sideways past its replacement, because the two are not a stack — the connection page is
 * still there, unchanged, behind the surface being dismissed, and a shared-axis slide would claim
 * otherwise.
 *
 * @param progress 0 = no gesture in flight, 1 = the point of no return.
 */
internal fun Modifier.marblePredictiveBack(progress: Float): Modifier {
    val p = progress.coerceIn(0f, 1f)
    return this.graphicsLayer {
        scaleX = 1f - MarbleDepthPolicy.BackScale * p
        scaleY = 1f - MarbleDepthPolicy.BackScale * p
        alpha = 1f - MarbleDepthPolicy.BackFade * p
    }
}
