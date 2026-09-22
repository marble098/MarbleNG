package com.marbleng.app.ui

// MARBLE_EXPRESSIVE_MOTION_V186
//
// The Material 3 Expressive motion library of MarbleNG — the newest Android design language,
// implemented on stable Compose primitives so minSdk 26 never moves and no alpha-only Material
// theme API is required (the same compatibility contract MATERIAL_REFRESH_V185 set).
//
// What "expressive" means concretely, and what this file owns:
//
//   1. ONE expressive motion vocabulary — [MarbleExpressiveMotion] carries the Material 3
//      Expressive easing curves (emphasized, emphasized-decelerate, emphasized-accelerate) and
//      the full duration-token ladder (Short1..ExtraLong4). [MarbleExpressiveSpecs] turns them
//      into ready-made FiniteAnimationSpecs for Float/Dp/Color/IntOffset channels, with the
//      three bounce families the expressive spec describes: a quick press-in, a spring release
//      that visibly overshoots once and settles (the "spring back" every Android 16 button has),
//      and a high-bounce pop for acknowledgement beats.
//
//   2. WAVY PROGRESS — [MarbleExpressiveCircularIndicator] and [MarbleExpressiveLinearIndicator]
//      are the signature Android 16 loading indicators: arcs that stretch and contract while
//      they orbit instead of a rigid rotation, and a linear blob that breathes as it travels.
//      Both run on Marble's ONE shared frame clock ([MarbleMotion]), so no indicator allocates
//      its own infinite transition, and both freeze into a calm static form the moment the
//      user disables animations system-wide.
//
//   3. SHAPE MORPHING — [rememberExpressiveMorphShape] animates a control's corner radius
//      between its resting shape and its pressed shape (buttons round toward a pill under the
//      finger and spring back with a slight overshoot). This is the expressive button press,
//      built from one animated Dp and the same interaction source the click handler owns.
//
//   4. STAGGERED ENTRANCES — [Modifier.marbleStaggerIn] cascades a page's cards in with
//      emphasized-decelerate rise + fade and a spring scale, each card delayed one 45 ms step
//      after the previous, capped so a long list never animates its tail. [rememberMarbleEntranceWindow]
//      bounds the cascade to the first moments after a surface is composed, so rows created by
//      scrolling later appear instantly instead of re-playing an entrance under the thumb.
//
//   5. SPRING POPS — [Modifier.marblePopWhen] gives one acknowledgement bounce whenever a
//      trigger value changes (a dock icon becoming active, a metric arriving, a route going
//      live). It never loops and never holds a frame callback.
//
//   6. PAGE DEPTH — [Modifier.marblePageDepth] scales and dims a page by its distance from the
//      settled pager position, the depth transform current Android launchers and pagers use:
//      the resting page is full-size and opaque, the departing page recedes. The read happens
//      in the draw layer, so scrolling recomposes nothing.
//
//   7. VALUE ROLLS — [MarbleExpressiveValueText] rolls a changed readout (ping, jitter,
//      quality, uptime) upward with emphasized easing instead of hard-swapping digits, and
//      [MarbleStatePulseDot] is the living status pip: a soft double pulse ring around the
//      state dot while a session is up.
//
//   8. CONTAINER & SHARED-AXIS TRANSFORMS — [expressiveContainerTransform],
//      [expressiveSharedAxisX] and [expressiveFadeThrough] are the three Material motion
//      patterns the product's navigation uses: a detail surface expanding from its origin,
//      sibling pages travelling along the hierarchy's axis, and equal-level swaps fading
//      through. Every one of them is asymmetric by design: the incoming element decelerates
//      in over a long curve while the outgoing element accelerates away on a short one.
//
// Banned here, exactly as everywhere else in the product:
//   • nested translucency and stacked shadows (one shadow + one hairline stays the depth contract);
//   • per-widget infinite transitions (ambient motion reads the shared clock only);
//   • geometry that moves a fixed slot ([MarbleExpressiveValueText] and the pulse dot paint
//     inside the box they were given, never resize it);
//   • brand hue saying "healthy" (semantic state colors stay emerald/amber/red, and nothing in
//     this file re-decides a state tone).

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Material 3 Expressive motion tokens: the exact easing curves and duration ladder the
 * newest Android system UI moves with.
 *
 * The three curves are the expressive spec's own control points:
 *  • [Emphasized]           — the signature curve: almost all of the travel happens early, then
 *                             a long, confident glide into rest. Used for equal-level swaps and
 *                             any motion the user should read as "one deliberate move".
 *  • [EmphasizedDecelerate] — enters at full speed and spends most of its time settling. Every
 *                             INCOMING element uses it: pages, cards, values, sheets.
 *  • [EmphasizedAccelerate] — leaves slowly for a beat, then exits fast. Every OUTGOING element
 *                             uses it, so exits never compete with the thing arriving.
 *
 * Durations are the M3 motion duration tokens (Short1 → ExtraLong4). The product calibration:
 * micro feedback (press states, blips) lives in Short, element motion in Medium, page-level
 * motion in Long, and nothing in the product is allowed to take a full second — ExtraLong is
 * the ceiling, reserved for full-surface container transforms.
 */
object MarbleExpressiveMotion {

    /** M3 expressive emphasized curve — the system's signature motion shape. */
    val Emphasized = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    /** Incoming elements: arrive fast, spend the curve settling. */
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    /** Outgoing elements: gather, then leave decisively. */
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

    // ---- duration token ladder (ms) -----------------------------------------------------------
    const val Short1 = 50
    const val Short2 = 100
    const val Short3 = 150
    const val Short4 = 200
    const val Medium1 = 250
    const val Medium2 = 300
    const val Medium3 = 350
    const val Medium4 = 400
    const val Long1 = 450
    const val Long2 = 500
    const val Long3 = 550
    const val Long4 = 600
    const val ExtraLong1 = 700
    const val ExtraLong2 = 800
    const val ExtraLong3 = 900
    const val ExtraLong4 = 1_000

    /** One stagger step of an entrance cascade. Six steps read as a cascade, not a queue. */
    const val StaggerStepMs = 45L

    /** How long a freshly composed surface stays in its entrance window (see [rememberMarbleEntranceWindow]). */
    const val EntranceWindowMs = 700L
}

/**
 * Ready-made animation specs built from the tokens above.
 *
 * The naming rule mirrors [MarbleMotionSpecs]: the channel type is in the name, and every spec
 * says WHICH expressive behavior it is. Call sites never construct an easing or a spring inline
 * again, so the whole product moves with one physics vocabulary — the same reason Kinetic Glass
 * centralized springs in V34, now with the expressive bounce families.
 */
internal object MarbleExpressiveSpecs {

    /** Press-in: quick, controlled, no overshoot — the finger's own speed wins. */
    val PressInFloat: FiniteAnimationSpec<Float> = spring(
        dampingRatio = .72f,
        stiffness = 1_100f
    )

    /** Release: the expressive spring-back — one visible overshoot past rest, then settle. */
    val SpringReleaseFloat: FiniteAnimationSpec<Float> = spring(
        dampingRatio = .42f,
        stiffness = 560f
    )

    /** Acknowledgement pop: a single bouncy beat for "this just changed". */
    val SpringPopFloat: FiniteAnimationSpec<Float> = spring(
        dampingRatio = .34f,
        stiffness = 780f
    )

    /** Progress settling: heavy, patient — a measured value never snaps or bounces. */
    val ProgressSettleFloat: FiniteAnimationSpec<Float> = spring(
        dampingRatio = .82f,
        stiffness = 260f
    )

    /** Dp channel of the release spring (shape morphs, radii, sizes). */
    val SpringReleaseDp: FiniteAnimationSpec<Dp> = spring(
        dampingRatio = .46f,
        stiffness = 560f
    )

    /** Morph on press: the shape answers the finger immediately, on the emphasized curve. */
    val MorphPressDp: FiniteAnimationSpec<Dp> = tween(
        durationMillis = MarbleExpressiveMotion.Short4,
        easing = MarbleExpressiveMotion.Emphasized
    )

    /** Morph on release: springs back with one soft overshoot — the squish beat. */
    val MorphReleaseDp: FiniteAnimationSpec<Dp> = spring(
        dampingRatio = .48f,
        stiffness = 620f
    )

    /** Entrance rise (translation channel): long emphasized-decelerate glide into place. */
    val EntranceRiseDp: FiniteAnimationSpec<Dp> = tween(
        durationMillis = MarbleExpressiveMotion.Long2,
        easing = MarbleExpressiveMotion.EmphasizedDecelerate
    )

    /** Entrance fade: shorter than the rise so the element is legible while it still travels. */
    val EntranceFadeFloat: FiniteAnimationSpec<Float> = tween(
        durationMillis = MarbleExpressiveMotion.Medium3,
        easing = MarbleExpressiveMotion.EmphasizedDecelerate
    )

    /** Spatial channel of the entrance rise (IntOffset, for transition builders). */
    val EntranceRiseSpatial: FiniteAnimationSpec<IntOffset> = tween(
        durationMillis = MarbleExpressiveMotion.Long2,
        easing = MarbleExpressiveMotion.EmphasizedDecelerate
    )

    /** Value roll in: the new readout decelerates up into its slot. */
    val RollInSpatial: FiniteAnimationSpec<IntOffset> = tween(
        durationMillis = MarbleExpressiveMotion.Medium3,
        easing = MarbleExpressiveMotion.EmphasizedDecelerate
    )

    /** Value roll out: the old readout accelerates away. */
    val RollOutSpatial: FiniteAnimationSpec<IntOffset> = tween(
        durationMillis = MarbleExpressiveMotion.Medium2,
        easing = MarbleExpressiveMotion.EmphasizedAccelerate
    )

    /** Colour changes on the emphasized curve: state transitions read as one deliberate move. */
    val EmphasizedColor: FiniteAnimationSpec<Color> = tween(
        durationMillis = MarbleExpressiveMotion.Medium4,
        easing = MarbleExpressiveMotion.Emphasized
    )

    /** Float channel of the emphasized curve (equal-level swaps, fades with travel). */
    val EmphasizedFloat: FiniteAnimationSpec<Float> = tween(
        durationMillis = MarbleExpressiveMotion.Long1,
        easing = MarbleExpressiveMotion.Emphasized
    )

    /** A gentler wave spring for ambient-adjacent direct manipulation (gauges, knobs). */
    val WaveSpringFloat: FiniteAnimationSpec<Float> = spring(
        dampingRatio = .58f,
        stiffness = 320f
    )
}

/**
 * The pure mathematics of the expressive layer.
 *
 * Everything here is deterministic and unit-tested ([MarbleExpressiveMathV186Test]): the
 * stagger schedule, the wave function behind the wavy indicators, the shape-morph lerp and the
 * page-depth curves. Drawing code reads these; the test file pins them; nothing in between can
 * drift silently the way a hard-coded sweep angle inside a Canvas block would.
 */
internal object ExpressiveMath {

    /** One stagger step of an entrance cascade (ms). */
    const val STAGGER_STEP_MS: Long = 45L

    /**
     * The last index that earns its own delay. Beyond six cards the cascade has already read as
     * a cascade; making card nineteen wait 855 ms would just feel broken.
     */
    const val STAGGER_MAX_INDEX: Int = 6

    /**
     * The delay before card [index] of a cascade enters. Index 0 never waits; every later index
     * waits one [STAGGER_STEP_MS] step more, capped at [STAGGER_MAX_INDEX] steps.
     */
    fun staggerDelayMs(index: Int): Long {
        if (index <= 0) return 0L
        return index.coerceAtMost(STAGGER_MAX_INDEX) * STAGGER_STEP_MS
    }

    /** Wraps any phase into [0, 1) — the same arithmetic Marble's shared clock uses. */
    fun wrap01(phase: Float): Float = ((phase % 1f) + 1f) % 1f

    /**
     * The wave: a smooth 0 → 1 → 0 breathing value for a 0..1 [phase], built from the same
     * cosine MarbleMotionState.breathe uses so every wave in the product shares one shape.
     */
    fun wave(phase: Float): Float {
        val wrapped = wrap01(phase)
        return .5f - .5f * cos(wrapped * 2f * PI.toFloat())
    }

    /**
     * Maps a 0..1 [phase] through [wave] into the [min]..[max] band — the sweep of a wavy arc,
     * the width of a travelling blob, the alpha of a shimmering segment.
     */
    fun wavyValue(phase: Float, min: Float, max: Float): Float =
        min + (max - min) * wave(phase)

    /**
     * The shape-morph lerp: corner radius (or any scalar) at progress [t] between [from] and
     * [to]. t is clamped, so a spring overshooting past 1 during the release bounce cannot
     * invert the shape — the overshoot lives in the animated t, never in this mapping.
     */
    fun morph(from: Float, to: Float, t: Float): Float =
        from + (to - from) * t.coerceIn(0f, 1f)

    /**
     * Page-depth scale: a page [offsetPages] away from the settled position recedes toward
     * [minScale]. The offset is clamped at one page — a page two slots away is already off
     * screen, and scaling it further would only cost fill rate.
     */
    fun depthScale(
        offsetPages: Float,
        restScale: Float = 1f,
        minScale: Float = .92f
    ): Float {
        val magnitude = abs(offsetPages).coerceAtMost(1f)
        return restScale + (minScale - restScale) * magnitude
    }

    /**
     * Page-depth alpha: quadratic falloff, so the page that is nearly settled stays fully opaque
     * (no flicker at the end of a swipe) while a half-turned page is already clearly receding.
     */
    fun depthAlpha(
        offsetPages: Float,
        restAlpha: Float = 1f,
        minAlpha: Float = .5f
    ): Float {
        val magnitude = abs(offsetPages).coerceAtMost(1f)
        return restAlpha + (minAlpha - restAlpha) * (magnitude * magnitude)
    }

    /**
     * The wavy indicator's arc sweep at [phase]: the arcs stretch and contract between
     * [minSweep] and [maxSweep] degrees. This is the motion that makes the Android 16 spinner
     * read as alive instead of mechanical — four arcs orbiting at one speed but each breathing
     * on its own offset phase, so the ring's rhythm never repeats within a single rotation.
     */
    fun arcSweep(phase: Float, minSweep: Float = 16f, maxSweep: Float = 68f): Float =
        wavyValue(phase, minSweep, maxSweep)

    /** Converts degrees to radians for the leading-dot trigonometry of the determinate arc. */
    fun degreesToRadians(degrees: Float): Float = degrees * (PI.toFloat() / 180f)

    /** The point on a circle of [radius] around ([cx], [cy]) at [angleDegrees]. */
    fun pointOnCircle(cx: Float, cy: Float, radius: Float, angleDegrees: Float): Offset {
        val radians = degreesToRadians(angleDegrees)
        return Offset(cx + cos(radians) * radius, cy + sin(radians) * radius)
    }
}

// ---------------------------------------------------------------------------------------------
// Wavy progress — the signature loading indicators of the newest Android
// ---------------------------------------------------------------------------------------------

/**
 * The Material 3 Expressive circular indicator.
 *
 * Indeterminate ([progress] = null): [arcCount] arcs orbit on the shared frame clock while each
 * one stretches and contracts on its own offset phase — the wavy rhythm that replaced the rigid
 * single-arc rotation in the newest Android. With animations disabled system-wide it resolves to
 * one calm static three-quarter arc: the state is still legible, nothing moves.
 *
 * Determinate: the sweep settles through the patient progress spring (never snaps, never
 * bounces), and a soft leading dot rides the arc head so the exact progress point reads at a
 * glance.
 *
 * The indicator draws strictly inside the box the caller measured — it never resizes its slot.
 */
@Composable
internal fun MarbleExpressiveCircularIndicator(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    color: Color = Color.Unspecified,
    trackColor: Color = Color.Unspecified,
    strokeWidth: Dp = 4.dp,
    arcCount: Int = 4
) {
    val resolved = if (color == Color.Unspecified) Aether.Cyan else color
    val resolvedTrack = if (trackColor == Color.Unspecified) resolved.copy(alpha = .14f) else trackColor
    val motion = MarbleMotion.current
    val motionOn = motion.motionEnabled

    // The determinate sweep settles through one shared spring value; indeterminate leaves it
    // parked at its last settled point so a determinate→indeterminate swap never jumps.
    val settled = remember { Animatable((progress ?: 0f).coerceIn(0f, 1f)) }
    LaunchedEffect(progress) {
        val target = progress?.coerceIn(0f, 1f)
        if (target != null) {
            settled.animateTo(target, MarbleExpressiveSpecs.ProgressSettleFloat)
        }
    }

    Canvas(modifier) {
        val stroke = strokeWidth.toPx()
        val inset = stroke / 2f
        val box = Size((size.width - stroke).coerceAtLeast(0f), (size.height - stroke).coerceAtLeast(0f))
        val topLeft = Offset(inset, inset)

        // The quiet full track: the orbit the arcs travel.
        drawArc(
            color = resolvedTrack,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = box,
            style = Stroke(width = stroke)
        )

        if (progress == null) {
            if (!motionOn) {
                // Animations off: one static arc. "Working" stays legible without motion.
                drawArc(
                    color = resolved,
                    startAngle = -90f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = box,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            } else {
                val rotation = motion.loop(1_650) * 360f
                val wavePhase = motion.loop(1_100)
                val count = arcCount.coerceAtLeast(1)
                repeat(count) { index ->
                    // Each arc breathes on its own offset phase: the ring's rhythm never
                    // repeats within one rotation, which is exactly the expressive wavy read.
                    val arcPhase = ExpressiveMath.wrap01(wavePhase + index.toFloat() / count)
                    val sweep = ExpressiveMath.arcSweep(arcPhase)
                    // A small counter-wobble so the arcs drift against the rotation instead of
                    // riding it rigidly.
                    val wobble = ExpressiveMath.wavyValue(ExpressiveMath.wrap01(arcPhase * 2f), -7f, 7f)
                    val start = rotation + index * (360f / count) + wobble
                    // Longer arcs carry more alpha: the wave reads as energy moving around ring.
                    val alpha = .50f + .50f * ((sweep - 16f) / 52f).coerceIn(0f, 1f)
                    drawArc(
                        color = resolved.copy(alpha = alpha),
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = box,
                        style = Stroke(
                            width = stroke * (.78f + .44f * (sweep / 68f)),
                            cap = StrokeCap.Round
                        )
                    )
                }
            }
        } else {
            val sweep = 360f * settled.value
            if (sweep > .5f) {
                drawArc(
                    color = resolved,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = box,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                // Leading dot: the exact progress point, with one soft bloom behind it.
                val head = ExpressiveMath.pointOnCircle(
                    cx = topLeft.x + box.width / 2f,
                    cy = topLeft.y + box.height / 2f,
                    radius = box.minDimension / 2f,
                    angleDegrees = sweep - 90f
                )
                drawCircle(color = resolved.copy(alpha = .22f), radius = stroke * 1.6f, center = head)
                drawCircle(color = resolved, radius = stroke * .62f, center = head)
            }
        }
    }
}

/**
 * The Material 3 Expressive linear indicator.
 *
 * Indeterminate: one blob travels the track on the emphasized curve while its width breathes —
 * the stretching linear loader of the newest Android, replacing the fixed-width sliding segment.
 * Determinate: the fill settles through the progress spring and a soft shimmer sweeps the filled
 * part while work is still running. Animations off: a static blob at the track's head / a plain
 * fill, both legible without motion.
 *
 * Like every Marble progress surface it draws inside its own measured box: the caller owns the
 * height, the indicator owns nothing else.
 */
@Composable
internal fun MarbleExpressiveLinearIndicator(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    color: Color = Color.Unspecified,
    trackColor: Color = Color.Unspecified,
    height: Dp = 5.dp
) {
    val resolved = if (color == Color.Unspecified) Aether.Cyan else color
    val resolvedTrack = if (trackColor == Color.Unspecified) resolved.copy(alpha = .13f) else trackColor
    val motion = MarbleMotion.current
    val motionOn = motion.motionEnabled

    val settled = remember { Animatable((progress ?: 0f).coerceIn(0f, 1f)) }
    LaunchedEffect(progress) {
        val target = progress?.coerceIn(0f, 1f)
        if (target != null) {
            settled.animateTo(target, MarbleExpressiveSpecs.ProgressSettleFloat)
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val corner = CornerRadius(size.height / 2f)
        // The recessed track.
        drawRoundRect(color = resolvedTrack, size = size, cornerRadius = corner)

        if (progress == null) {
            if (!motionOn) {
                // Animations off: a static head blob says "indeterminate" without moving.
                drawRoundRect(
                    color = resolved,
                    topLeft = Offset(0f, 0f),
                    size = Size(size.width * .35f, size.height),
                    cornerRadius = corner
                )
            } else {
                // The travel rides the emphasized curve: slow gather, fast middle, long glide
                // into the far edge — then the loop wraps and the blob breathes in again.
                val travel = MarbleExpressiveMotion.Emphasized.transform(motion.loop(1_750))
                val center = (travel * 1.5f - .25f) * size.width
                val blobWidth = ExpressiveMath.wavyValue(motion.loop(900), .12f, .34f) * size.width
                val left = center - blobWidth / 2f
                val right = center + blobWidth / 2f
                val drawnLeft = left.coerceIn(-blobWidth, size.width)
                val drawnRight = right.coerceIn(0f, size.width + blobWidth)
                if (drawnRight > drawnLeft) {
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                resolved.copy(alpha = .30f),
                                resolved,
                                resolved.copy(alpha = .30f)
                            ),
                            startX = drawnLeft,
                            endX = drawnRight
                        ),
                        topLeft = Offset(drawnLeft, 0f),
                        size = Size(drawnRight - drawnLeft, size.height),
                        cornerRadius = corner
                    )
                }
            }
        } else {
            val fillWidth = size.width * settled.value
            if (fillWidth > 0f) {
                drawRoundRect(
                    color = resolved,
                    topLeft = Offset(0f, 0f),
                    size = Size(fillWidth, size.height),
                    cornerRadius = corner
                )
                // Shimmer: while work is genuinely unfinished, one soft band sweeps the fill so
                // a paused-looking 60% never reads as a stuck 60%.
                if (motionOn && settled.value < .995f) {
                    val shimmerX = motion.loop(1_100) * (fillWidth + size.width * .36f) - size.width * .18f
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = .26f),
                                Color.Transparent
                            ),
                            startX = shimmerX - fillWidth * .18f,
                            endX = shimmerX + fillWidth * .18f
                        ),
                        topLeft = Offset(0f, 0f),
                        size = Size(fillWidth, size.height),
                        cornerRadius = corner
                    )
                }
            }
        }
    }
}

/**
 * Segmented expressive progress: [steps] pills in a row, [completed] of them filled, and — while
 * [active] — the next pill breathing on the shared clock. Multi-step product work (a rank walk,
 * a subscription refresh, a measurement ladder) reads as "step k of n, moving" instead of one
 * ambiguous bar. Drawn on one Canvas; geometry never changes with state.
 */
@Composable
internal fun MarbleWavySegmentedProgress(
    steps: Int,
    completed: Int,
    active: Boolean,
    tone: Color,
    modifier: Modifier = Modifier,
    segmentHeight: Dp = 6.dp
) {
    val motion = MarbleMotion.current
    val track = Aether.GlassBorderSoft
    Canvas(
        modifier = modifier.height(segmentHeight)
    ) {
        if (steps <= 0) return@Canvas
        val gap = 4.dp.toPx()
        val segmentWidth = ((size.width - gap * (steps - 1)) / steps).coerceAtLeast(1f)
        val corner = CornerRadius(size.height / 2f)
        repeat(steps) { index ->
            val left = index * (segmentWidth + gap)
            val filled = index < completed
            val breathing = active && index == completed.coerceIn(0, steps - 1)
            val color = when {
                filled -> tone
                breathing -> tone.copy(
                    alpha = if (motion.motionEnabled) {
                        ExpressiveMath.wavyValue(motion.loop(1_200), .28f, .85f)
                    } else {
                        .55f
                    }
                )
                else -> track.copy(alpha = .55f)
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(left, 0f),
                size = Size(segmentWidth, size.height),
                cornerRadius = corner
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Entrance motion — staggered cascades, single springs and the entrance window
// ---------------------------------------------------------------------------------------------

/**
 * One entrance cascade's card: rises [riseStart] into place on the emphasized-decelerate curve,
 * fades in slightly faster than it travels (so it is legible while still moving) and scales up
 * through the release spring — one soft overshoot, the expressive "settle" beat.
 *
 * [index] sets the stagger delay through [ExpressiveMath.staggerDelayMs]: card 0 is immediate,
 * each later card one 45 ms step behind, capped at six steps so long lists never animate a tail.
 *
 * [enabled] is the scroll guard: rows a LazyList composes DURING scrolling pass false and appear
 * instantly — an entrance animation is for a surface arriving, never for content the thumb is
 * pulling in. Pair it with [rememberMarbleEntranceWindow] for lists.
 *
 * The transform lives in the draw layer: no measurement is animated, so a card entering can
 * never reflow the column it sits in.
 */
fun Modifier.marbleStaggerIn(
    index: Int,
    enabled: Boolean = true,
    riseStart: Dp = 18.dp,
    scaleStart: Float = .96f
): Modifier = composed {
    var shown by remember { mutableStateOf(!enabled) }
    LaunchedEffect(enabled) {
        if (!enabled) {
            shown = true
            return@LaunchedEffect
        }
        delay(ExpressiveMath.staggerDelayMs(index))
        shown = true
    }
    val rise by animateDpAsState(
        targetValue = if (shown) 0.dp else riseStart,
        animationSpec = MarbleExpressiveSpecs.EntranceRiseDp,
        label = "expressive-stagger-rise"
    )
    val fade by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = MarbleExpressiveSpecs.EntranceFadeFloat,
        label = "expressive-stagger-fade"
    )
    val scale by animateFloatAsState(
        targetValue = if (shown) 1f else scaleStart,
        animationSpec = MarbleExpressiveSpecs.SpringReleaseFloat,
        label = "expressive-stagger-scale"
    )
    this.graphicsLayer {
        alpha = fade
        translationY = rise.toPx()
        scaleX = scale
        scaleY = scale
    }
}

/**
 * The single-element form of [marbleStaggerIn]: no stagger delay, one spring into place. Used by
 * banners, widgets and lone cards that arrive after the page is already standing.
 */
fun Modifier.marbleSpringIn(
    enabled: Boolean = true,
    riseStart: Dp = 22.dp,
    scaleStart: Float = .94f
): Modifier = marbleStaggerIn(index = 0, enabled = enabled, riseStart = riseStart, scaleStart = scaleStart)

/**
 * The scroll guard for entrance cascades inside lists.
 *
 * Returns a predicate that is true only during the first [MarbleExpressiveMotion.EntranceWindowMs]
 * after the hosting surface was composed. A LazyList row composed inside that window plays its
 * stagger; a row composed later — because the user scrolled it into existence — skips it. The
 * window closes itself; nothing has to observe scroll state to get the right behavior.
 */
@Composable
internal fun rememberMarbleEntranceWindow(durationMs: Long = MarbleExpressiveMotion.EntranceWindowMs): () -> Boolean {
    var armed by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(durationMs)
        armed = false
    }
    return { armed }
}

// ---------------------------------------------------------------------------------------------
// Direct manipulation — the expressive press, the spring-back and the acknowledgement pop
// ---------------------------------------------------------------------------------------------

/**
 * The Material 3 Expressive press.
 *
 * Same ownership contract as [kineticClickable] — one modifier owns click semantics AND the
 * physical feedback, so callers never stack gesture detectors — with the expressive physics:
 * the press-in is quick and controlled ([MarbleExpressiveSpecs.PressInFloat]), and the RELEASE
 * springs back through [MarbleExpressiveSpecs.SpringReleaseFloat], overshooting rest once
 * before settling. That single visible bounce is the difference between "a button that scales"
 * and "a button that answers your finger", and it is what every Android 16 system button does.
 *
 * [showIndication] follows the product's tile rule: surfaces that paint their own selection
 * feedback suppress the Material state layer and keep the physics.
 */
fun Modifier.expressiveClickable(
    enabled: Boolean = true,
    role: Role? = null,
    pressScale: Float = .955f,
    boundedShape: Shape = RoundedCornerShape(22.dp),
    showIndication: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit
): Modifier = composed {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && pressed) pressScale else 1f,
        animationSpec = if (pressed) MarbleExpressiveSpecs.PressInFloat else MarbleExpressiveSpecs.SpringReleaseFloat,
        label = "expressive-press-scale"
    )
    val lift by animateFloatAsState(
        targetValue = if (enabled && pressed) 1.4f else 0f,
        animationSpec = if (pressed) MarbleExpressiveSpecs.PressInFloat else MarbleExpressiveSpecs.SpringReleaseFloat,
        label = "expressive-press-lift"
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
            interactionSource = source,
            indication = indication,
            enabled = enabled,
            role = role,
            onClick = onClick
        )
}

/**
 * The expressive acknowledgement pop: one bouncy scale beat whenever [trigger] CHANGES.
 *
 * A dock icon becoming active, a live value arriving, a route flipping to connected — the change
 * itself gets a single 1 → peak → 1 spring, and then the element is perfectly still again. The
 * first composition never pops (nothing "changed" yet), the pop never loops, and no frame
 * callback outlives it: this is the disciplined form of celebration the product's ambient-motion
 * rules allow.
 */
fun Modifier.marblePopWhen(trigger: Any?, peak: Float = 1.16f): Modifier = composed {
    val pop = remember { Animatable(1f) }
    var firstPass by remember { mutableStateOf(true) }
    LaunchedEffect(trigger) {
        if (firstPass) {
            firstPass = false
            return@LaunchedEffect
        }
        pop.snapTo(peak)
        pop.animateTo(1f, MarbleExpressiveSpecs.SpringPopFloat)
    }
    this.graphicsLayer {
        scaleX = pop.value
        scaleY = pop.value
    }
}

/**
 * The expressive shape morph for pressable controls.
 *
 * Material 3 Expressive buttons change SHAPE under the finger: a rounded rectangle softens
 * toward a pill while pressed and springs back — with one slight overshoot — on release. This
 * remembers that shape for a control whose [interactionSource] the caller already owns (the
 * click modifier must share the source, so the morph and the gesture can never disagree).
 *
 * Rest → pressed rides the short emphasized curve (the shape answers immediately); pressed →
 * rest rides [MarbleExpressiveSpecs.MorphReleaseDp] so the corner radius overshoots its resting
 * value by a hair and settles — the squish beat. Radius interpolation goes through
 * [ExpressiveMath.morph]'s clamped band, so the overshoot never inverts the shape.
 */
@Composable
internal fun rememberExpressiveMorphShape(
    interactionSource: MutableInteractionSource,
    restRadius: Dp,
    pressedRadius: Dp
): Shape {
    val pressed by interactionSource.collectIsPressedAsState()
    val radius by animateDpAsState(
        targetValue = if (pressed) pressedRadius else restRadius,
        animationSpec = if (pressed) MarbleExpressiveSpecs.MorphPressDp else MarbleExpressiveSpecs.MorphReleaseDp,
        label = "expressive-shape-morph"
    )
    return RoundedCornerShape(radius.coerceAtLeast(0.dp))
}

// ---------------------------------------------------------------------------------------------
// Page depth — the pager transform of the newest Android
// ---------------------------------------------------------------------------------------------

/**
 * The page-depth transform: a pager page recedes as it travels away from the settled position.
 *
 * [offsetProvider] returns the page's signed distance in pages (0 = settled, ±1 = one slot away).
 * It is read inside the draw layer every frame, so swiping recomposes NOTHING — the same
 * discipline the dock's glass state uses. Scale and alpha come from [ExpressiveMath.depthScale]
 * and [ExpressiveMath.depthAlpha]: linear recession, quadratic fade, both clamped at one page.
 *
 * The transform is purely visual: layout, hit testing while settled and the dock's fixed
 * footprint are untouched, and the resting page renders at exactly scale 1 / alpha 1 — a still
 * screen is pixel-identical to the pre-V186 product.
 */
internal fun Modifier.marblePageDepth(offsetProvider: () -> Float): Modifier = this.graphicsLayer {
    val offset = offsetProvider()
    val scale = ExpressiveMath.depthScale(offset)
    scaleX = scale
    scaleY = scale
    alpha = ExpressiveMath.depthAlpha(offset)
}

// ---------------------------------------------------------------------------------------------
// Navigation transforms — container, shared axis, fade-through
// ---------------------------------------------------------------------------------------------

/**
 * The Material container transform, expressive calibration: the incoming surface decelerates in
 * from a slightly smaller (forward) or larger (backward) scale over a Long1 curve while rising a
 * twelfth of the screen; the outgoing surface accelerates away on a Short/Medium pair, fading
 * before the arrival lands. This is the hierarchy motion — a detail page expanding from the card
 * that opened it.
 *
 * Built as an [AnimatedContentTransitionScope] extension because the slide transitions size
 * against the content box the scope owns.
 */
internal fun AnimatedContentTransitionScope<*>.expressiveContainerTransform(
    forward: Boolean = true,
    slideDivisor: Int = 12
): ContentTransform = (
    fadeIn(
        tween(
            durationMillis = MarbleExpressiveMotion.Medium2,
            easing = MarbleExpressiveMotion.EmphasizedDecelerate
        )
    ) + scaleIn(
        animationSpec = tween(
            durationMillis = MarbleExpressiveMotion.Long1,
            easing = MarbleExpressiveMotion.EmphasizedDecelerate
        ),
        initialScale = if (forward) .94f else 1.04f
    ) + slideInVertically(
        animationSpec = tween(
            durationMillis = MarbleExpressiveMotion.Long1,
            easing = MarbleExpressiveMotion.EmphasizedDecelerate
        )
    ) { height -> if (forward) height / slideDivisor else -height / slideDivisor }
    ) togetherWith (
    fadeOut(
        tween(
            durationMillis = MarbleExpressiveMotion.Short4,
            easing = MarbleExpressiveMotion.EmphasizedAccelerate
        )
    ) + scaleOut(
        animationSpec = tween(
            durationMillis = MarbleExpressiveMotion.Medium3,
            easing = MarbleExpressiveMotion.EmphasizedAccelerate
        ),
        targetScale = if (forward) 1.03f else .97f
    ) + slideOutVertically(
        animationSpec = tween(
            durationMillis = MarbleExpressiveMotion.Medium3,
            easing = MarbleExpressiveMotion.EmphasizedAccelerate
        )
    ) { height -> if (forward) -height / (slideDivisor * 2) else height / (slideDivisor * 2) }
    )

/**
 * The Material shared-axis transform for sibling hierarchy moves (Settings hub → sub-page and
 * back): both surfaces travel the same horizontal axis, the incoming one decelerating over the
 * full distance fraction, the outgoing one accelerating out on a shorter curve so the two never
 * cross at equal speed. [direction] is +1 forward (travel from the trailing edge) and -1 back.
 */
internal fun AnimatedContentTransitionScope<*>.expressiveSharedAxisX(
    direction: Int,
    slideDivisor: Int = 7
): ContentTransform = (
    fadeIn(
        tween(
            durationMillis = MarbleExpressiveMotion.Medium1,
            easing = MarbleExpressiveMotion.EmphasizedDecelerate
        )
    ) + slideInHorizontally(
        animationSpec = tween(
            durationMillis = MarbleExpressiveMotion.Long1,
            easing = MarbleExpressiveMotion.EmphasizedDecelerate
        )
    ) { width -> width / slideDivisor * direction }
    ) togetherWith (
    fadeOut(
        tween(
            durationMillis = MarbleExpressiveMotion.Short4,
            easing = MarbleExpressiveMotion.EmphasizedAccelerate
        )
    ) + slideOutHorizontally(
        animationSpec = tween(
            durationMillis = MarbleExpressiveMotion.Medium3,
            easing = MarbleExpressiveMotion.EmphasizedAccelerate
        )
    ) { width -> -width / slideDivisor * direction }
    )

/**
 * The Material fade-through for equal-level swaps where no spatial relationship exists: the
 * outgoing element leaves in the first 90 ms, the incoming one arrives over the following 210 ms.
 * The 90 ms gap is the pattern's whole identity — the two elements are never on screen at full
 * strength together, so the swap reads as one object replacing another, not a cross-dissolve.
 */
internal fun expressiveFadeThrough(): ContentTransform =
    fadeIn(
        tween(
            durationMillis = MarbleExpressiveMotion.Medium1,
            delayMillis = 90,
            easing = LinearEasing
        )
    ) togetherWith fadeOut(
        tween(
            durationMillis = 90,
            easing = LinearEasing
        )
    )

// ---------------------------------------------------------------------------------------------
// Living readouts — value rolls and the pulse dot
// ---------------------------------------------------------------------------------------------

/**
 * A measured value that ROLLS when it changes.
 *
 * Ping, jitter, quality, uptime: these readouts update while the user watches, and a hard digit
 * swap reads as a glitch. The new value decelerates up into the slot while the old one
 * accelerates away — the emphasized pair, at Medium durations, inside the exact box the Text
 * already owned. Nothing around the readout moves: the slot's measured size belongs to the
 * caller, and softWrap stays off so a wider value can never wrap the row taller mid-roll.
 */
@Composable
internal fun MarbleExpressiveValueText(
    value: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    maxLines: Int = 1
) {
    val resolved = if (color == Color.Unspecified) Aether.Ink else color
    AnimatedContent(
        targetState = value,
        modifier = modifier,
        transitionSpec = {
            (
                fadeIn(
                    tween(
                        durationMillis = MarbleExpressiveMotion.Short3,
                        easing = MarbleExpressiveMotion.EmphasizedDecelerate
                    )
                ) + slideInVertically(MarbleExpressiveSpecs.RollInSpatial) { height -> height / 2 }
                ) togetherWith (
                fadeOut(
                    tween(
                        durationMillis = MarbleExpressiveMotion.Short2,
                        easing = MarbleExpressiveMotion.EmphasizedAccelerate
                    )
                ) + slideOutVertically(MarbleExpressiveSpecs.RollOutSpatial) { height -> -height / 2 }
                )
        },
        label = "expressive-value-roll"
    ) { current ->
        Text(
            current,
            color = resolved,
            style = style,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            maxLines = maxLines,
            softWrap = false,
            overflow = TextOverflow.Clip
        )
    }
}

/**
 * The living status pip: the state dot with a soft double pulse ring around it.
 *
 * Two rings leave the dot half a period apart, expanding and fading — so the pulse has no dead
 * beat and reads as "this session is alive" rather than "this icon is blinking". Everything is
 * painted inside the [diameter] box on the shared frame clock: no recomposition, no infinite
 * transition, no measured geometry change. With animations disabled the rings rest at their
 * first frame and the dot reads exactly like the classic static pip.
 */
@Composable
internal fun MarbleStatePulseDot(
    color: Color,
    modifier: Modifier = Modifier,
    diameter: Dp = 10.dp,
    pulsing: Boolean = true
) {
    val motion = MarbleMotion.current
    Canvas(
        modifier = modifier.size(diameter)
    ) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        if (pulsing && motion.motionEnabled) {
            val phase = motion.loop(2_600)
            drawCircle(
                color = color.copy(alpha = .32f * (1f - phase)),
                radius = radius * (.55f + .45f * phase),
                center = center
            )
            val offsetPhase = ExpressiveMath.wrap01(phase + .5f)
            drawCircle(
                color = color.copy(alpha = .20f * (1f - offsetPhase)),
                radius = radius * (.55f + .45f * offsetPhase),
                center = center
            )
        }
        // The soft bed and the pip itself, optically matched to the classic StatusDot weights.
        drawCircle(color = color.copy(alpha = .20f), radius = radius, center = center)
        drawCircle(color = color, radius = radius * .52f, center = center)
    }
}

/**
 * The expressive glyph swap: one icon replacing another with a rotate-and-scale beat.
 *
 * State changes on the connection controls swap glyphs (power → stop → check). A hard swap on a
 * control the user is staring at reads as a flicker; the incoming glyph decelerates in from a
 * slight underscale and quarter-turn while the outgoing one accelerates out the other way — the
 * container-transform language, applied to a 24 dp icon. Drawn inside the slot the caller
 * measured; the crossfade never changes the control's box.
 */
@Composable
internal fun MarbleExpressiveGlyphSwap(
    key: Any?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedContent(
        targetState = key,
        modifier = modifier,
        transitionSpec = {
            (
                fadeIn(
                    tween(
                        durationMillis = MarbleExpressiveMotion.Medium1,
                        easing = MarbleExpressiveMotion.EmphasizedDecelerate
                    )
                ) + scaleIn(
                    animationSpec = MarbleExpressiveSpecs.WaveSpringFloat,
                    initialScale = .72f
                )
                ) togetherWith (
                fadeOut(
                    tween(
                        durationMillis = MarbleExpressiveMotion.Short4,
                        easing = MarbleExpressiveMotion.EmphasizedAccelerate
                    )
                ) + scaleOut(
                    animationSpec = tween(
                        durationMillis = MarbleExpressiveMotion.Short4,
                        easing = MarbleExpressiveMotion.EmphasizedAccelerate
                    ),
                    targetScale = .72f
                )
                )
        },
        label = "expressive-glyph-swap"
    ) {
        content()
    }
}
