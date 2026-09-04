package com.marbleng.app.ui

// MARBLE_KINETIC_GLASS_ENGINE_V34
// MARBLE_PRISM_MOTION_V54
// MARBLE_BOUNDED_RIPPLE_MOTION_V62

import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos

/**
 * One physics vocabulary for every interactive transition in MarbleNG.
 *
 * The old UI mixed dozens of unrelated fixed-duration tweens and created independent infinite
 * transitions in individual cards. Kinetic Glass uses spring response for direct manipulation and
 * a single frame clock for ambient motion. This makes button feedback immediate, keeps page motion
 * coherent, and prevents every animated row from owning a permanent frame callback.
 */
object MarbleMotionSpecs {
    val InteractionFloat: FiniteAnimationSpec<Float> = spring(
        dampingRatio = .68f,
        stiffness = 980f
    )
    val ResponseFloat: FiniteAnimationSpec<Float> = spring(
        dampingRatio = .76f,
        stiffness = 620f
    )
    val ExitFloat: FiniteAnimationSpec<Float> = spring(
        dampingRatio = .92f,
        stiffness = 820f
    )
    val ProgressFloat: FiniteAnimationSpec<Float> = spring(
        dampingRatio = .82f,
        stiffness = 520f
    )
    val Color: FiniteAnimationSpec<Color> = spring(
        dampingRatio = .90f,
        stiffness = 560f
    )
    // MARBLE_DOCK_STABLE_COLOR_V115 — navigation chrome animates with a short overshoot-free
    // tween. A spring interpolates alpha beyond its target on the way in (underdamped), which
    // flashed the dock pill/text on every click and theme switch; tweens cannot overshoot.
    val DockColor: FiniteAnimationSpec<Color> = tween(180)
    // MARBLE_DOCK_STILL_BAR_V132 — the floating dock's geometry is fixed, so its alpha and
    // elevation channels use the same overshoot-free tween as its colours. A spring here made
    // the bar visibly bounce/settle on every page turn.
    val DockFloat: FiniteAnimationSpec<Float> = tween(180)
    val DockDp: FiniteAnimationSpec<Dp> = tween(180)
    val Dp: FiniteAnimationSpec<Dp> = spring(
        dampingRatio = .74f,
        stiffness = 650f
    )
    val Spatial: FiniteAnimationSpec<IntOffset> = spring(
        dampingRatio = .79f,
        stiffness = 520f
    )
    val SpatialExit: FiniteAnimationSpec<IntOffset> = spring(
        dampingRatio = .93f,
        stiffness = 700f
    )
    val Layout: FiniteAnimationSpec<IntSize> = spring(
        dampingRatio = .84f,
        stiffness = 560f
    )
    val HeroFloat: FiniteAnimationSpec<Float> = spring(
        dampingRatio = .70f,
        stiffness = 430f
    )
    // MARBLE_SERVERS_FAB_KINETICS_V116 — the Servers clipboard button answers the finger, not the
    // clock: a very stiff lift spring and short reveal tweens so the tap import and the hold
    // action stack feel immediate instead of floating.
    val FabLift: FiniteAnimationSpec<Float> = spring(
        dampingRatio = .68f,
        stiffness = 1500f
    )
    val QuickReveal: FiniteAnimationSpec<Float> = tween(120)
    val QuickExit: FiniteAnimationSpec<Float> = tween(80)
}

@Stable
class MarbleMotionState internal constructor(
    val motionEnabled: Boolean
) {
    private var elapsedSeconds by mutableFloatStateOf(0f)

    internal fun updateElapsed(seconds: Float) {
        elapsedSeconds = if (motionEnabled) seconds.coerceAtLeast(0f) else 0f
    }

    /** A stable 0..1 loop driven by Marble's one shared frame clock. */
    fun loop(periodMillis: Int, offset: Float = 0f): Float {
        if (!motionEnabled) return 0f
        val periodSeconds = periodMillis.coerceAtLeast(1) / 1_000f
        val raw = elapsedSeconds / periodSeconds + offset
        return ((raw % 1f) + 1f) % 1f
    }

    /** A smooth 0..1 breathing wave without allocating another infinite transition. */
    fun breathe(periodMillis: Int, offset: Float = 0f): Float {
        val phase = loop(periodMillis, offset)
        return .5f - .5f * cos(phase * 2f * PI.toFloat())
    }
}

private val LocalMarbleMotion = staticCompositionLocalOf {
    MarbleMotionState(motionEnabled = false)
}

object MarbleMotion {
    val current: MarbleMotionState
        @Composable get() = LocalMarbleMotion.current
}

/**
 * Owns the only ambient frame loop in the UI.
 *
 * Android's global animator scale is honored: when the user disables animations, ambient motion
 * freezes and direct interactions resolve immediately to their resting state.
 */
@Composable
fun ProvideMarbleMotion(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val motionEnabled = remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) > 0f
        }.getOrDefault(true)
    }
    val engine = remember(motionEnabled) { MarbleMotionState(motionEnabled) }

    androidx.compose.runtime.LaunchedEffect(engine) {
        if (!engine.motionEnabled) {
            engine.updateElapsed(0f)
            return@LaunchedEffect
        }
        var originNanos = 0L
        while (currentCoroutineContext().isActive) {
            withFrameNanos { frameNanos ->
                if (originNanos == 0L) originNanos = frameNanos
                engine.updateElapsed((frameNanos - originNanos) / 1_000_000_000f)
            }
        }
    }

    CompositionLocalProvider(LocalMarbleMotion provides engine, content = content)
}

/**
 * Physics-backed press feedback used by buttons, navigation targets and actionable cards.
 *
 * This modifier deliberately owns click semantics as well as scale/lift feedback so callers never
 * stack multiple gesture detectors on the same control.
 */
fun Modifier.kineticClickable(
    enabled: Boolean = true,
    role: Role? = null,
    pressScale: Float = .972f,
    boundedShape: Shape = RoundedCornerShape(22.dp),
    showIndication: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && pressed) pressScale else 1f,
        animationSpec = MarbleMotionSpecs.InteractionFloat,
        label = "kinetic-press-scale"
    )
    val lift by animateFloatAsState(
        targetValue = if (enabled && pressed) 1.6f else 0f,
        animationSpec = MarbleMotionSpecs.InteractionFloat,
        label = "kinetic-press-lift"
    )
    // MARBLE_SELECTION_TILE_INDICATION_REMOVED_DS_V69
    // The Material3 ripple draws a persistent focused/pressed state layer on top of the
    // surface. On selection tiles that already own their selected-state fill, that extra
    // off-white overlay composited as a visible rectangle behind the sub-text — the "white
    // box" reported under Testing & ping. Callers that paint their own selection feedback
    // pass showIndication = false to suppress it; press scale and lift still apply.
    val indication = if (showIndication) LocalIndication.current else null

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            translationY = lift
            // Ripple + press feedback are clipped at the gesture layer itself.
            shape = boundedShape
            clip = true
        }
        .clickable(
            interactionSource = interactionSource,
            indication = indication,
            enabled = enabled,
            role = role,
            onClick = onClick
        )
}

