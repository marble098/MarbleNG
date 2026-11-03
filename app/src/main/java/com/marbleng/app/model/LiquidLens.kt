package com.marbleng.app.model

import kotlin.math.abs
import kotlin.math.min

/**
 * MARBLE_LIQUID_LENS_V262 — the optics, the material and the physics of the glass-lens dock.
 *
 * V261 gave the bar a travelling aperture: an accent wash inside the row's own hairline. The pass
 * after it asks for the thing that wash was standing in for — a **convex lens**: a puck of glass
 * that magnifies the glyph under it, bends the light at its rim, blurs what is behind it and
 * deforms along the row while it is thrown. That is a material with numbers, and the numbers are
 * here rather than inside the composable so `LiquidLensV262Test` can pin them on the host JVM,
 * where a typo costs a test run instead of a screenshot review.
 *
 * | question | answer |
 * |---|---|
 * | how heavy is the pill, and how thick its rim? | [PILL_SHADOW_BLUR_DP], [PILL_SHADOW_OFFSET_Y_DP], [PILL_SHADOW_ALPHA], [PILL_BORDER_DP], [PILL_BORDER_ALPHA] |
 * | how clear is the glass, and how hard does it blur? | [GLASS_ALPHA_LIGHT], [GLASS_ALPHA_DARK], [GLASS_BLUR_RADIUS_PX] |
 * | how bright is the rim, and where? | [GLASS_RIM_DP], [GLASS_RIM_START_ALPHA], [GLASS_RIM_END_ALPHA] |
 * | how much is a tab magnified under it? | [MAGNIFICATION] |
 * | how does the puck behave in flight? | [SPRING_DAMPING_RATIO], [SPRING_STIFFNESS], [deformation] |
 * | how big is the lens inside a 56 / 68 / 82 dp bar? | [lensDiameterDp] |
 * | where may its centre be? | [lensLeftPx] |
 *
 * The placement arithmetic itself is [GlassDock.apertureLeftPx] — one clamp for the whole product,
 * already pinned by `GlassDockV261Test` — so this object only publishes the *lens-specific* numbers
 * and the pure kinematics. Two clamps for one lens is how a lens ends up half outside its bar.
 */
object LiquidLens {
    const val MARKER: String = "MARBLE_LIQUID_LENS_V262"

    // ── the pill container ───────────────────────────────────────────────────────────────────

    /**
     * The lift under the capsule: a 16dp blur, pushed 4dp down, at 8 % black.
     *
     * The offset is what makes it read as *floating over* the page instead of as a border: a
     * centred shadow of the same blur looks like a glow, and a bar with a glow reads as a button.
     */
    const val PILL_SHADOW_BLUR_DP: Int = 16
    const val PILL_SHADOW_OFFSET_Y_DP: Float = 4f
    const val PILL_SHADOW_ALPHA: Float = 0.08f

    /** One continuous hairline, white at the top and nearly gone at the floor. */
    const val PILL_BORDER_DP: Float = 1f
    const val PILL_BORDER_ALPHA: Float = 0.6f
    const val PILL_BORDER_FLOOR_ALPHA: Float = 0.12f

    /** `RoundedCornerShape(percent = 50)` — a stadium, stated as the ratio it is. */
    const val PILL_CORNER_PERCENT: Int = 50

    // ── the glass ────────────────────────────────────────────────────────────────────────────

    /**
     * The pane's own opacity: `Color.White.copy(alpha = 0.55f)` on a light page.
     *
     * On a dark page the same 0.55 white is a lampshade, so the dark branch uses
     * [GLASS_ALPHA_DARK] and lets the accent wash carry the "this is glass" reading instead. Both
     * values are above [GlassDock.LENS_ALPHA] (0.16) on purpose: V261's aperture was a tint you
     * looked *through*, V262's lens is a body you look *at*, and the glyph inside it is drawn over
     * the glass rather than under it.
     */
    const val GLASS_ALPHA_LIGHT: Float = 0.55f
    const val GLASS_ALPHA_DARK: Float = 0.22f

    /** `RenderEffect.createBlurEffect(12f, 12f, CLAMP)` — the backdrop sample's radius, in pixels. */
    const val GLASS_BLUR_RADIUS_PX: Float = 12f

    /** The rim: 1.5dp, incandescent at the top-left, translucent at the bottom-right. */
    const val GLASS_RIM_DP: Float = 1.5f
    const val GLASS_RIM_START_ALPHA: Float = 0.95f
    const val GLASS_RIM_END_ALPHA: Float = 0.15f

    /** The refraction ring just inside the rim, and the caustic that lands on the perimeter. */
    const val GLASS_MENISCUS_DP: Float = 1f
    const val GLASS_INNER_GLOW_ALPHA: Float = 0.30f
    const val GLASS_CAUSTIC_ALPHA: Float = 0.24f

    /** The specular arc across the upper rim: one stroke, faded at both ends. */
    const val GLASS_SPECULAR_ALPHA: Float = 0.62f
    const val GLASS_ARC_WIDTH_DP: Float = 1.2f
    const val GLASS_ARC_INSET_DP: Float = 1f

    /**
     * The arc's own arc: 202° to 338°, i.e. the crown of the pane and not its floor.
     *
     * A catch-light that ran the whole circumference would be an outline; one that runs the upper
     * 136° is a light source. The angles are stated in the model because they are the *shape* of
     * the highlight, not a styling detail of one composable.
     */
    const val GLASS_ARC_START_DEG: Float = 202f
    const val GLASS_ARC_SWEEP_DEG: Float = 136f

    /** The middle stop of the rim gradient: the flanks of the pane, neither lit nor dark. */
    const val GLASS_RIM_MID_ALPHA: Float = 0.34f

    /** The refraction band's two stops, and how far down the pane it reaches. */
    const val GLASS_BAND_TOP_ALPHA: Float = 0.26f
    const val GLASS_BAND_FADE_ALPHA: Float = 0.06f
    const val GLASS_BAND_HEIGHT_RATIO: Float = 0.9f

    /** The one reflected room in the upper-left quadrant: its brightness, size and place. */
    const val GLASS_HOTSPOT_ALPHA: Float = 0.30f
    const val GLASS_HOTSPOT_RADIUS_RATIO: Float = 0.34f
    const val GLASS_HOTSPOT_X_RATIO: Float = 0.34f
    const val GLASS_HOTSPOT_Y_RATIO: Float = 0.36f

    /**
     * The fallback frost, for the platforms with no `RenderEffect` (API < 31, or a vendor graphics
     * stack that refuses the effect): one soft radial pool of white, offset up and left so the disc
     * still reads as a body with a light in it.
     */
    const val GLASS_FROST_ALPHA: Float = 0.18f
    const val GLASS_FROST_RADIUS_RATIO: Float = 1.15f
    const val GLASS_FROST_X_RATIO: Float = 0.2f
    const val GLASS_FROST_Y_RATIO: Float = 0.25f

    /** The selected route's colour through the pane, vivid enough to read as an active destination. */
    const val GLASS_ACCENT_ALPHA: Float = 0.22f

    /** A second palette hue in the refracted wash, for a subtle prismatic rather than flat tint. */
    const val GLASS_PRISM_ALPHA: Float = 0.14f

    /**
     * The lens is a capsule/oval, never a square: the corner is half the diameter, i.e. a circle,
     * whenever the pane is at rest. A thrown lens stretches [MAX_STRETCH] and becomes the oval.
     */
    const val LENS_CORNER_RATIO: Float = 0.5f

    // ── the optics ───────────────────────────────────────────────────────────────────────────

    /** Icon scale under the lens. 1.28 — the brief's number, and the value V248 already used. */
    const val MAGNIFICATION: Float = 1.28f

    /** What a tab is *not* under the lens: the identity, written down so nothing defaults to 1.3. */
    const val REST_SCALE: Float = 1f

    /** The band a future tweak may move inside, pinned so it cannot leave it. */
    const val MIN_MAGNIFICATION: Float = 1.2f
    const val MAX_MAGNIFICATION: Float = 1.34f

    /** The glyph gets heavier as well as larger: magnification alone reads as a bigger icon. */
    const val GLYPH_WEIGHT_ACTIVE: Float = 1.16f

    /** The connection light: 5dp at rest, half again as large under the lens, lifted 2dp. */
    const val STATUS_DOT_SIZE_DP: Float = 5f
    const val STATUS_DOT_SCALE: Float = 1.5f
    const val STATUS_DOT_LIFT_DP: Float = 2f

    // ── the physics ──────────────────────────────────────────────────────────────────────────

    /**
     * `dampingRatio = 0.75f` — the brief's own number, and not the token it names beside it.
     *
     * The brief asks for `Spring.DampingRatioMediumBouncy` and writes *≈0.75* in the same breath.
     * In `androidx.compose.animation.core.Spring` those are two different springs: the token is
     * **0.5**, and 0.75 is `DampingRatioLowBouncy`. At 0.5 the arrival overshoots by 16 % — on a
     * 52dp lens that is eight dp past the tab it is aiming at, which reads as a rubber band rather
     * than as a liquid settling. At 0.75 the overshoot is 2.8 %, about one and a half dp of arrival
     * energy: still a bounce you can see, nothing you can feel as a rebound.
     *
     * The number therefore wins over the token, and it wins as a number rather than as a name: the
     * test pins the value, pins that it is calmer than medium-bouncy and springier than
     * `DampingRatioNoBouncy`, and — since 0.75 happens to *be* the low-bouncy token here — pins that
     * too, so an upstream retune of the constant cannot silently re-tune the dock.
     *
     * `Spring.StiffnessMediumLow` — the other half of the pair — *is* 400f, and it is low on
     * purpose: a stiffer spring arrives too fast for the stretch below to be seen at all.
     */
    const val SPRING_DAMPING_RATIO: Float = 0.75f
    const val SPRING_STIFFNESS: Float = 400f

    /**
     * Squash and stretch: at [STRETCH_SATURATION_PX_PER_S] the lens is 15 % wider and 8 % shorter
     * than at rest, and it settles back to 1,1 as the spring's velocity dies.
     *
     * Stretch is deliberately the larger of the two numbers: a liquid drop that is thrown stretches
     * along its direction of travel and narrows against it, and the difference is what makes the
     * shape read as *volume* rather than as a rectangle being scaled.
     */
    const val MAX_STRETCH: Float = 0.15f
    const val MAX_SQUASH: Float = 0.08f
    const val STRETCH_SATURATION_PX_PER_S: Float = 2_400f

    // ── geometry ─────────────────────────────────────────────────────────────────────────────

    /**
     * The lens fills the bar's inner row: the capsule minus one inner padding above and below.
     *
     * 56 − 12 = 44dp on Slim, 68 − 16 = 52dp on Classic, 82 − 22 = 60dp on Grand. A lens that
     * touched the rim would be a highlight painted on the bar; the inset is what makes it an object
     * *inside* the glass.
     */
    fun lensDiameterDp(barHeightDp: Int, insetDp: Int): Int =
        (barHeightDp - insetDp * 2).coerceAtLeast(1)

    /** The lens corner, in dp: half the diameter, i.e. a circle at rest. */
    fun lensCornerDp(diameterDp: Float): Float = diameterDp * LENS_CORNER_RATIO

    /**
     * Where the lens's left edge is, given the measured centre of the slot it is over.
     *
     * Delegated to [GlassDock.apertureLeftPx] on purpose: the clamp (never past either rim, never
     * wider than the bar) is one piece of arithmetic in the product, and the lens and the V261
     * aperture must agree about it.
     */
    fun lensLeftPx(
        barWidthPx: Float,
        centrePx: Float,
        diameterPx: Float,
        trackInsetPx: Float
    ): Float = GlassDock.apertureLeftPx(
        barWidthPx = barWidthPx,
        centrePx = centrePx,
        apertureWidthPx = diameterPx,
        trackInsetPx = trackInsetPx
    )

    /**
     * The velocity deformation of the puck: stretched along the row, squashed against it.
     *
     * Direction-free — a throw is a throw in either direction — and continuous at zero, because the
     * first frame of a spring recovery has a velocity too and a lens that snapped back to a circle
     * while still moving would flicker.
     */
    fun deformation(velocityPxPerSecond: Float): LensDeformation {
        val speed = abs(velocityPxPerSecond)
        val slack = min(speed / STRETCH_SATURATION_PX_PER_S, 1f)
        return LensDeformation(
            scaleX = 1f + MAX_STRETCH * slack,
            scaleY = 1f - MAX_SQUASH * slack
        )
    }

    /**
     * The label's opacity, from the same 0..1 the row's expansion spring produces.
     *
     * Clamped rather than trusted: an overshooting spring passes 1 and a bouncy return passes 0,
     * and `Color.copy(alpha = 1.04f)` is an exception in some of the graphics pipeline.
     */
    fun labelAlpha(expansion: Float): Float = expansion.coerceIn(0f, 1f)

    /**
     * The connection light's lift, in dp: nothing at rest, [STATUS_DOT_LIFT_DP] under the lens.
     *
     * The dot is anchored to the slot's own bottom edge, so the lift is the *only* thing that can
     * move it into the icon it belongs to — and it is animated rather than a second layout branch,
     * because two anchors would make the light jump when the lens arrives.
     */
    fun statusDotLiftDp(selected: Boolean): Float =
        if (selected) STATUS_DOT_LIFT_DP else 0f
}
