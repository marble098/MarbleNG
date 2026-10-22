package com.marbleng.app.ui

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

// MARBLE_SMOOTH_MOTION_V220 — every spring in this table is tuned for glide, not bounce: damping
// ratios near or at critical, stiffnesses low enough that a mid-range phone holds 60 fps while a
// card, a switch and a stagger all move at once. Colours are critically damped on purpose — a hue
// that overshoots its target reads as a flicker, never as physics.
object MarbleMotionSpecs {
    val InteractionFloat: FiniteAnimationSpec<Float> = spring(
        dampingRatio = .80f,
        stiffness = 850f
    )
    val ResponseFloat: FiniteAnimationSpec<Float> = spring(
        dampingRatio = .85f,
        stiffness = 520f
    )
    val ExitFloat: FiniteAnimationSpec<Float> = spring(
        dampingRatio = .95f,
        stiffness = 700f
    )
    val ProgressFloat: FiniteAnimationSpec<Float> = spring(
        dampingRatio = .90f,
        stiffness = 450f
    )
    val Color: FiniteAnimationSpec<Color> = spring(
        dampingRatio = 1f,
        stiffness = 420f
    )

    val DockColor: FiniteAnimationSpec<Color> = tween(200)

    val DockFloat: FiniteAnimationSpec<Float> = tween(200)
    val DockDp: FiniteAnimationSpec<Dp> = tween(200)
    val Dp: FiniteAnimationSpec<Dp> = spring(
        dampingRatio = .85f,
        stiffness = 550f
    )
    val Spatial: FiniteAnimationSpec<IntOffset> = spring(
        dampingRatio = .88f,
        stiffness = 480f
    )
    val SpatialExit: FiniteAnimationSpec<IntOffset> = spring(
        dampingRatio = .95f,
        stiffness = 620f
    )
    val Layout: FiniteAnimationSpec<IntSize> = spring(
        dampingRatio = .90f,
        stiffness = 480f
    )
    val HeroFloat: FiniteAnimationSpec<Float> = spring(
        dampingRatio = .82f,
        stiffness = 380f
    )

    val FabLift: FiniteAnimationSpec<Float> = spring(
        dampingRatio = .80f,
        stiffness = 1200f
    )
    val QuickReveal: FiniteAnimationSpec<Float> = tween(140)
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

    fun loop(periodMillis: Int, offset: Float = 0f): Float {
        if (!motionEnabled) return 0f
        val periodSeconds = periodMillis.coerceAtLeast(1) / 1_000f
        val raw = elapsedSeconds / periodSeconds + offset
        return ((raw % 1f) + 1f) % 1f
    }

    fun breathe(periodMillis: Int, offset: Float = 0f): Float {
        val phase = loop(periodMillis, offset)
        return .5f - .5f * cos(phase * 2f * PI.toFloat())
    }

    fun coarseLoop(periodMillis: Int, offset: Float = 0f): Float {
        if (!motionEnabled) return 0f
        val periodSeconds = periodMillis.coerceAtLeast(1) / 1_000f
        val raw = coarseSeconds / periodSeconds + offset
        return ((raw % 1f) + 1f) % 1f
    }

    fun coarseBreathe(periodMillis: Int, offset: Float = 0f): Float {
        val phase = coarseLoop(periodMillis, offset)
        return .5f - .5f * cos(phase * 2f * PI.toFloat())
    }

    fun animates(): Boolean = MarbleMotionPolicy.animates(motionEnabled)

    fun entranceDelayFor(index: Int): Long = MarbleMotionPolicy.entranceDelayMs(index, motionEnabled)

    fun pressScaleFor(kind: MarbleControlKind): Float = MarbleMotionPolicy.pressScale(kind, motionEnabled)

    fun acknowledges(kind: MarbleControlKind): Boolean = kind.acknowledgesPress(motionEnabled)
}

private val LocalMarbleMotion = staticCompositionLocalOf {
    MarbleMotionState(motionEnabled = false)
}

object MarbleMotion {
    val current: MarbleMotionState
        @Composable get() = LocalMarbleMotion.current
}

private const val AMBIENT_FPS_CAP = 60
private const val COARSE_FPS = 15
private const val AMBIENT_MIN_FRAME_NANOS = 1_000_000_000L / AMBIENT_FPS_CAP
private const val COARSE_STEP_NANOS = 1_000_000_000L / COARSE_FPS

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

                if (frameNanos - lastFrameNanos >= AMBIENT_MIN_FRAME_NANOS) {
                    lastFrameNanos = frameNanos
                    engine.updateElapsed((frameNanos - originNanos) / 1_000_000_000f)
                }

                if (frameNanos - lastCoarseNanos >= COARSE_STEP_NANOS) {
                    lastCoarseNanos = frameNanos
                    engine.updateCoarse((frameNanos - originNanos) / 1_000_000_000f)
                }
            }
        }
    }

    CompositionLocalProvider(LocalMarbleMotion provides engine, content = content)
}

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
    // MARBLE_SMOOTH_MOTION_V220 — the press answers with scale, not travel: the old 1.6 px drop
    // read as the layout jumping under the finger on dense pages.
    val lift by animateFloatAsState(
        targetValue = if (enabled && pressed && animates) 0.6f else 0f,
        animationSpec = when {
            !animates -> snap()
            pressed -> MarbleMotionSpecs.InteractionFloat
            else -> releaseSpec
        },
        label = "kinetic-press-lift"
    )

    val indication = if (showIndication) LocalIndication.current else null

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            translationY = lift

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

