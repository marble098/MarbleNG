package com.marbleng.app.ui

// MARBLE_KINETIC_GLASS_ENGINE_V34
// MARBLE_PRISM_MOTION_V54
// MARBLE_BOUNDED_RIPPLE_MOTION_V62

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
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
    private var coarseSeconds by mutableFloatStateOf(0f)

    internal fun updateElapsed(seconds: Float) {
        elapsedSeconds = if (motionEnabled) seconds.coerceAtLeast(0f) else 0f
    }

    internal fun updateCoarse(seconds: Float) {
        coarseSeconds = if (motionEnabled) seconds.coerceAtLeast(0f) else 0f
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

    /**
     * MARBLE_SMOOTH_CLOCK_V193 — the coarse loop: the same wave as [loop], sampled ~15 times a
     * second instead of every frame. Ambient effects with periods of many seconds (the page
     * backdrop's breathing glows) are visually identical at this rate, and their hosting surfaces
     * now invalidate ~15 times a second instead of 60–120.
     */
    fun coarseLoop(periodMillis: Int, offset: Float = 0f): Float {
        if (!motionEnabled) return 0f
        val periodSeconds = periodMillis.coerceAtLeast(1) / 1_000f
        val raw = coarseSeconds / periodSeconds + offset
        return ((raw % 1f) + 1f) % 1f
    }

    /** The coarse twin of [breathe], for large, slow ambient surfaces. */
    fun coarseBreathe(periodMillis: Int, offset: Float = 0f): Float {
        val phase = coarseLoop(periodMillis, offset)
        return .5f - .5f * cos(phase * 2f * PI.toFloat())
    }

    // ---------------------------------------------------------------------------------------
    // MARBLE_ROUTE_ATELIER_V207 — the three questions every animated surface asks the same way.
    // A component that decides any of them locally is how the product ended up with a setting that
    // stops the halos and keeps the waiting.
    // ---------------------------------------------------------------------------------------

    /** May this surface move at all? Reduced motion answers for entrance, ambient and press alike. */
    fun animates(): Boolean = MarbleMotionPolicy.animates(motionEnabled)

    /** How long a cascade row waits before it enters — zero under reduced motion, so nothing queues. */
    fun entranceDelayFor(index: Int): Long = MarbleMotionPolicy.entranceDelayMs(index, motionEnabled)

    /** The press travel of a control class, or 1f when the user asked for no motion. */
    fun pressScaleFor(kind: MarbleControlKind): Float = MarbleMotionPolicy.pressScale(kind, motionEnabled)

    /** May this control spend a frame on an acknowledgement beat? Reduced motion says no. */
    fun acknowledges(kind: MarbleControlKind): Boolean = kind.acknowledgesPress(motionEnabled)
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
 *
 * MARBLE_ROUTE_ATELIER_V207 — and it is honored *live*. The value is a ContentObserver-backed state
 * now, so turning animations off in the system settings while MarbleNG is open settles every
 * surface in the same frame: [MarbleMotionPolicy] is what the entrance cascade, the press travel
 * and the ambient field all consult, and it can no longer be fed a stale reading.
 *
 * MARBLE_SMOOTH_CLOCK_V193 — the clock also owns two anti-jank duties for the whole app:
 *  1. it never delivers ambient frames faster than [AMBIENT_FPS_CAP] — on a 90/120 Hz panel the
 *     old loop recomposed every ambient reader at the panel rate, doubling or quadrupling the
 *     recomposition load behind every animation for motion the eye cannot tell from 60;
 *  2. it publishes a coarse twin of the elapsed time at [COARSE_FPS], for the very large and
 *     very slow surfaces (the full-screen page backdrop's 9–15 s waves) whose draw invalidation
 *     at 60 fps bought nothing but GPU work on every frame.
 */
private const val AMBIENT_FPS_CAP = 60
private const val COARSE_FPS = 15
private const val AMBIENT_MIN_FRAME_NANOS = 1_000_000_000L / AMBIENT_FPS_CAP
private const val COARSE_STEP_NANOS = 1_000_000_000L / COARSE_FPS

/** The device's animation scale, read as "may anything move at all". */
private fun animatorMotionEnabled(context: android.content.Context): Boolean = runCatching {
    Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    ) > 0f
}.getOrDefault(true)

@Composable
fun ProvideMarbleMotion(content: @Composable () -> Unit) {
    val context = LocalContext.current
    // MARBLE_ROUTE_ATELIER_V207 — the setting is OBSERVED, not sampled once.
    //
    // The old code read the animator scale inside `remember(context)`, so an app that was built in
    // the foreground while "Remove animations" was flipped kept its first answer: a user who turns
    // animations off to stop the page moving had to kill and reopen MarbleNG. That is the exact
    // gap between "we honour the setting at boot" and "we honour the setting", and it is the reason
    // [MarbleMotionPolicy] could not promise anything: a cascade, a halo and an interaction could
    // each hold a different opinion about the same switch.
    var motionEnabled by remember {
        mutableStateOf(animatorMotionEnabled(context))
    }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            @Suppress("DEPRECATION")
            override fun onChange(selfChange: Boolean) {
                motionEnabled = animatorMotionEnabled(context)
            }
        }
        val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        runCatching { context.contentResolver.registerContentObserver(uri, false, observer) }
        onDispose { runCatching { context.contentResolver.unregisterContentObserver(observer) } }
    }
    val engine = remember(motionEnabled) { MarbleMotionState(motionEnabled) }

    androidx.compose.runtime.LaunchedEffect(engine) {
        if (!engine.motionEnabled) {
            engine.updateElapsed(0f)
            return@LaunchedEffect
        }
        var originNanos = 0L
        var lastFrameNanos = 0L
        var lastCoarseNanos = 0L
        while (currentCoroutineContext().isActive) {
            withFrameNanos { frameNanos ->
                if (originNanos == 0L) originNanos = frameNanos
                // One state write per delivered frame. A frame faster than the cap (90/120 Hz
                // panels, or a fast recomposition burst) is folded into the next delivery.
                if (frameNanos - lastFrameNanos >= AMBIENT_MIN_FRAME_NANOS) {
                    lastFrameNanos = frameNanos
                    engine.updateElapsed((frameNanos - originNanos) / 1_000_000_000f)
                }
                // The coarse twin only changes value every COARSE_STEP_NANOS, so readers of
                // [coarseLoop]/[coarseBreathe] invalidate that rarely.
                if (frameNanos - lastCoarseNanos >= COARSE_STEP_NANOS) {
                    lastCoarseNanos = frameNanos
                    engine.updateCoarse((frameNanos - originNanos) / 1_000_000_000f)
                }
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
 *
 * MARBLE_EXPRESSIVE_MOTION_V186 — two additive knobs, both defaulting to the exact V34 behavior:
 *  • [interactionSource] lets a caller OWN the gesture source so a second expressive effect can
 *    observe the same press — the shape morph of [rememberExpressiveMorphShape] must read the
 *    very press the click handler reads, and two sources on one control can disagree by a frame;
 *  • [releaseSpec] lets a caller trade the release physics without touching the press-in: the
 *    expressive controls pass the spring-back spec so the button overshoots rest once on the way
 *    out ([MarbleExpressiveSpecs.SpringReleaseFloat]), while every existing caller keeps the
 *    overshoot-free interaction spring — including the dock, where MARBLE_DOCK_STABLE_COLOR_V115
 *    bans bounce for good reason.
 */
fun Modifier.kineticClickable(
    enabled: Boolean = true,
    role: Role? = null,
    pressScale: Float = .972f,
    boundedShape: Shape = RoundedCornerShape(22.dp),
    showIndication: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    releaseSpec: FiniteAnimationSpec<Float> = MarbleMotionSpecs.InteractionFloat,
    onClick: () -> Unit
): Modifier = composed {
    val ownedSource = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by ownedSource.collectIsPressedAsState()
    // MARBLE_ROUTE_ATELIER_V207 — reduced motion removes the TRAVEL, not only the duration.
    // A scale spring that snaps to 0.94 in 0 ms is still a control that jumps under the finger, and
    // an entrance that waits 45 ms per row after the animation switch is a bug the user cannot
    // switch off. The one question goes to the one answer.
    val animates = MarbleMotion.current.animates()
    val pressedScale = if (animates) pressScale else 1f
    val scale by animateFloatAsState(
        targetValue = if (enabled && pressed) pressedScale else 1f,
        animationSpec = when {
            !animates -> snap()
            pressed -> MarbleMotionSpecs.InteractionFloat
            else -> releaseSpec
        },
        label = "kinetic-press-scale"
    )
    val lift by animateFloatAsState(
        targetValue = if (enabled && pressed && animates) 1.6f else 0f,
        animationSpec = when {
            !animates -> snap()
            pressed -> MarbleMotionSpecs.InteractionFloat
            else -> releaseSpec
        },
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
            interactionSource = ownedSource,
            indication = indication,
            enabled = enabled,
            role = role,
            onClick = onClick
        )
}

