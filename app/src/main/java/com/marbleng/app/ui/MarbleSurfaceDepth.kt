package com.marbleng.app.ui

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

object MarbleDepthPolicy {
    val RestingElevation: Dp = 4.dp

    val RaisedElevation: Dp = 9.dp

    val ContactElevation: Dp = 2.dp
    // MARBLE_SHADOW_CALM_V220 — the contact shadow tacks the card to the page; it must not ring it.
    const val ContactAlphaLight: Float = .07f
    const val ContactAlphaDark: Float = .40f

    val WashReach: Dp = 110.dp

    const val WashAlphaDark: Float = .055f
    const val WashAlphaLight: Float = .035f

    const val LitRimDark: Float = .30f

    const val LitRimLight: Float = .55f

    const val BackScale: Float = .08f

    const val BackFade: Float = .25f
}

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

    // MARBLE_SHADOW_CALM_V220 — the depth contract keeps its lifts; the shadow ink is halved so a
    // lifted surface reads as floating, not stained.
    val shadowAmbient = when {
        Aether.IsDynamic -> Aether.FloatShadow.copy(alpha = if (dark) .16f else .10f)
        dark -> Color(0xFF001144).copy(alpha = .22f)
        else -> Color(0xFF0A2540).copy(alpha = .12f)
    }
    val shadowSpot = when {
        Aether.IsDynamic -> Aether.Cyan.copy(alpha = if (dark) .16f else .12f)
        dark -> HomeCloud.Accent.copy(alpha = .18f)
        else -> Color(0xFF1E5FAF).copy(alpha = .14f)
    }

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

    val contact = when {
        Aether.IsDynamic -> Aether.FloatShadow.copy(
            alpha = if (dark) MarbleDepthPolicy.ContactAlphaDark else MarbleDepthPolicy.ContactAlphaLight
        )
        dark -> Color.Black.copy(alpha = MarbleDepthPolicy.ContactAlphaDark)
        else -> Color(0xFF0A2540).copy(alpha = MarbleDepthPolicy.ContactAlphaLight)
    }
    return this
        .shadow(
            elevation = lift,
            shape = shape,
            clip = false,
            ambientColor = shadowAmbient,
            spotColor = shadowSpot
        )
        .shadow(
            elevation = MarbleDepthPolicy.ContactElevation,
            shape = shape,
            clip = false,
            ambientColor = contact,
            spotColor = contact
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

internal fun Modifier.marblePredictiveBack(progress: Float): Modifier {
    val p = progress.coerceIn(0f, 1f)
    return this.graphicsLayer {
        scaleX = 1f - MarbleDepthPolicy.BackScale * p
        scaleY = 1f - MarbleDepthPolicy.BackScale * p
        alpha = 1f - MarbleDepthPolicy.BackFade * p
    }
}
