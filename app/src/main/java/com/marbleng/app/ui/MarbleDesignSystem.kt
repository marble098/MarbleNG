package com.marbleng.app.ui

// MARBLE_PRISM_DESIGN_SYSTEM_V54
// MARBLE_PRISM_POLISH_V55

// MARBLE_EXPRESSIVE_MOTION_V186 — the design system's controls answer the finger with the

// MARBLE_HOME_CLOUD_DEPTH_V191 (one cool shadow, a gradient rim and a whisper wash).

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin

internal object MarbleSpacing {
    val Micro = 4.dp
    val XS = 6.dp
    val S = 8.dp
    val SM = 12.dp
    val M = 16.dp
    val L = 24.dp
    val XL = 32.dp

    val Page = 16.dp
}

internal enum class MarbleMetricBand {
    UNKNOWN, GOOD, WARNING, POOR
}

const val HOME_HEARTBEAT_GREEN_MAX_MS = 160

const val HOME_HEARTBEAT_AMBER_MAX_MS = 250

internal fun pingMetricBand(ms: Int): MarbleMetricBand = when {
    ms <= 0 -> MarbleMetricBand.UNKNOWN
    ms < HOME_HEARTBEAT_GREEN_MAX_MS -> MarbleMetricBand.GOOD
    ms <= HOME_HEARTBEAT_AMBER_MAX_MS -> MarbleMetricBand.WARNING
    else -> MarbleMetricBand.POOR
}

internal fun jitterMetricBand(ms: Int, samples: Int): MarbleMetricBand = when {
    samples <= 0 || ms < 0 -> MarbleMetricBand.UNKNOWN
    ms < 20 -> MarbleMetricBand.GOOD
    ms <= 50 -> MarbleMetricBand.WARNING
    else -> MarbleMetricBand.POOR
}

@Composable
internal fun homeHeartbeatTone(ms: Int): Color = marbleMetricTone(pingMetricBand(ms))

internal fun qualityMetricBand(score: Int): MarbleMetricBand = when {
    score < 0 -> MarbleMetricBand.UNKNOWN
    score >= 80 -> MarbleMetricBand.GOOD
    score >= 60 -> MarbleMetricBand.WARNING
    else -> MarbleMetricBand.POOR
}

internal fun leadingFlagGlyph(text: String): String? {
    val points=text.trim().codePoints().limit(2).toArray()
    if(points.size < 2) return null
    if(points.any { it !in 0x1F1E6..0x1F1FF }) return null
    return buildString {
        append(String(Character.toChars(points[0])))
        append(String(Character.toChars(points[1])))
    }
}

internal fun stripLeadingFlag(text: String): String {
    val clean=text.trim()
    val flag=leadingFlagGlyph(clean) ?: return clean
    return clean.substring(flag.length).trimStart()
}

internal fun displayServerName(name: String, host: String, scheme: String): String {
    val clean = stripLeadingFlag(name)
    if (clean.any { it.isLetterOrDigit() }) return clean
    val endpoint = host.trim().removeSurrounding("[", "]")
    return endpoint.ifBlank { scheme.uppercase().ifBlank { "Server" } }
}

@Composable
internal fun marbleMetricTone(band: MarbleMetricBand): Color = when (band) {
    MarbleMetricBand.GOOD -> Aether.Emerald
    MarbleMetricBand.WARNING -> Aether.Amber
    MarbleMetricBand.POOR -> Aether.Danger
    MarbleMetricBand.UNKNOWN -> Aether.InkMuted
}

internal object HomeCloud {
    val LightBase = Color(0xFFF4F8FC)
    val LightBgTop = Color(0xFFF8FBFE)
    val LightBgBottom = Color(0xFFEDF4FB)

    // MARBLE_NATIVE_ANDROID_V213 — the *card* and *inset* constants are gone from this table.

    val LightCardSelectedFill = Color(0xFFE7F1FE)
    val LightCardSelectedBorder = Color(0xFF3D8BE0)

    val DarkBgTop = Color.Black
    val DarkBgBottom = Color.Black
    val DarkCardSelectedFill = Color(0xFF122A45)
    val DarkCardSelectedBorder = Color(0xFF6FB2F2)

    val BrandAccent = Color(0xFF3D8BE0)

    val Accent: Color
        @Composable get() = if (Aether.IsDynamic) Aether.Cyan else BrandAccent

    // MARBLE_NATIVE_ANDROID_V213 — the Home card corner is the platform's card corner, so the one

    val CardShape = RoundedCornerShape(MarbleNativeSpec.CardCornerDp.dp)
    val CardRadius = MarbleNativeSpec.CardCornerDp.dp
    // MARBLE_NATIVE_ANDROID_V213 — the inset corner is the platform's row corner and the two lifts

    val InsetShape = RoundedCornerShape(MarbleNativeSpec.RowCornerDp.dp)
    // MARBLE_HOME_CLOUD_DEPTH_V191 — the flat-plane experiment ends: a resting card lifts on a

    val CardElevation = MarbleDepthPolicy.RestingElevation
    val SelectedElevation = MarbleDepthPolicy.RaisedElevation
    val Hairline = 1.dp
    val SelectedHairline = 1.5.dp
}

@Composable
internal fun homeCloudDark(): Boolean = Aether.Void.luminance() < 0.5f

@Composable
internal fun homeCloudBackgroundBrush(): Brush = when {
    Aether.IsDynamic -> Brush.verticalGradient(
        colors = listOf(Aether.Void, lerp(Aether.Void, Aether.Glass, .55f))
    )
    homeCloudDark() -> Brush.verticalGradient(colors = listOf(HomeCloud.DarkBgTop, HomeCloud.DarkBgBottom))
    else -> Brush.verticalGradient(colors = listOf(HomeCloud.LightBgTop, HomeCloud.LightBgBottom))
}

/**
 * Fill of a resting Home card.
 *
 * MARBLE_NATIVE_ANDROID_V213 — the source is the platform's ladder now, not a Home-only table.
 *
 * `HomeCloud.LightCardFill` was `Color.White` and `HomeCloud.DarkCardFill` was `#0F1727`, i.e. a
 * *second* set of surface values kept for one page: under the brand palettes the difference was
 * small enough to hide, and under the explicit dark theme it was larger than a card should ever be
 * from the platform's own container. Reading the live scheme means the Home card, the settings
 * card, a sheet and a dialog are the same material by construction — and that a wallpaper palette
 * moves all of them together, which is the one thing a private table can never do.
 */
@Composable
internal fun homeCloudCardFill(): Color = marbleNativeCardFill()

@Composable
internal fun homeCloudCardBorder(): Color = marbleNativeOutline()

@Composable
internal fun homeCloudSelectedFill(): Color = when {
    Aether.IsDynamic -> Aether.Cyan.copy(alpha = if (homeCloudDark()) .18f else .10f)
        .compositeOver(Aether.VoidElevated)
    homeCloudDark() -> HomeCloud.DarkCardSelectedFill
    else -> HomeCloud.LightCardSelectedFill
}

@Composable
internal fun homeCloudSelectedBorder(): Color = when {
    Aether.IsDynamic -> Aether.Cyan.copy(alpha = .70f).compositeOver(Aether.VoidElevated)
    homeCloudDark() -> HomeCloud.DarkCardSelectedBorder
    else -> HomeCloud.LightCardSelectedBorder
}

/**
 * Inset fill for chips/pills/rows that sit *inside* a Home card.
 *
 * MARBLE_NATIVE_ANDROID_V213 — the inset is the platform's container step, one rung above the card
 * it lives in, exactly as a nested Android surface is drawn. The Home-only values it replaces
 * (`LightInsetFill` / `DarkInsetFill`) were tuned against the same page, so they agreed with the
 * platform by accident on the light theme and disagreed with it on every other one.
 */
@Composable
internal fun homeCloudInsetFill(): Color = marbleNativeContainerHigh()

@Composable
internal fun homeCloudInsetBorder(): Color = marbleNativeOutline()

@Composable
internal fun homeCloudDivider(): Color = homeCloudInsetBorder()

/**
 * The one Home card container: opaque fill + one thin hairline. [selected] switches to the sky
 * fill and 1.5 dp accent rim.
 *
 * MARBLE_HOME_CLOUD_DEPTH_V191 — the cards left the flat-plane era. V187 dropped every shadow to
 * answer "crowded", but all-opaque boxes with a 1 dp border on a near-white page left every card
 * on exactly the same depth plane, which is the root of the lifeless first screen. Each card now
 * carries the Prism depth contract: ONE cool brand-tinted shadow (never a grey one), a gradient
 * hairline that catches light at the top, and a whisper-level brand wash inside the top edge.
 * Nothing is stacked, nothing is translucent — the fill stays exactly the box, and selected cards
 * lift a step so the chosen route reads as raised, not just re-tinted.
 */
@Composable
internal fun HomeCloudCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    shape: Shape = HomeCloud.CardShape,
    elevation: Dp = if (selected) HomeCloud.SelectedElevation else HomeCloud.CardElevation,
    fill: Color = if (selected) homeCloudSelectedFill() else homeCloudCardFill(),
    border: BorderStroke = BorderStroke(
        width = if (selected) HomeCloud.SelectedHairline else HomeCloud.Hairline,
        color = if (selected) homeCloudSelectedBorder() else homeCloudCardBorder()
    ),
    content: @Composable () -> Unit
) {
    // MARBLE_HOME_CLOUD_DEPTH_V191 — one soft cool shadow: navy-cast in Light (a grey shadow on a

    val lift by animateDpAsState(
        targetValue = elevation,
        animationSpec = MarbleMotionSpecs.Dp,
        label = "home-cloud-card-lift"
    )
    val dark = homeCloudDark()

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

    val rimBrush = if (selected) {
        Brush.verticalGradient(
            listOf(homeCloudSelectedBorder(), homeCloudCardBorder())
        )
    } else {
        val plainRim = homeCloudCardBorder()
        val litRim = if (dark) lerp(plainRim, HomeCloud.Accent, .30f) else lerp(plainRim, Color.White, .55f)
        Brush.verticalGradient(listOf(litRim, plainRim))
    }

    val washTop = when {
        Aether.IsDynamic -> Aether.Cyan.copy(alpha = if (dark) .055f else .035f)
        dark -> HomeCloud.Accent.copy(alpha = .055f)
        else -> Color(0xFF3399FF).copy(alpha = .035f)
    }
    val washEndPx = with(LocalDensity.current) { 130.dp.toPx() }
    Box(
        modifier = modifier
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
            // MARBLE_AURORA_SURFACE_V219 — the specular plane. At 6 % it is invisible as a shape
            // and visible as light: it is what separates a card that is *filled* from a card that
            // is *lit*, and it costs one gradient.
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (dark) .06f else .34f),
                        Color.White.copy(alpha = 0f)
                    ),
                    startY = 0f,
                    endY = with(LocalDensity.current) { 26.dp.toPx() }
                )
            )
            .border(border.width, rimBrush, shape)
    ) {
        content()
    }
}

@Composable
internal fun PrismBackdrop(
    modifier: Modifier = Modifier,
    flavor: HomeFlavor = HomeFlavor.IOS_SLIDER
) {
    val dark = homeCloudDark()
    val motion = MarbleMotion.current

    val ambient = LocalMarbleAmbientField.current
    val motionOn = motion.motionEnabled && ambient
    val dynamicPalette = Aether.IsDynamic

    val glowStart = Aether.Cyan
    val glowEnd = Aether.CyanBright

    val glowFloor = if (dynamicPalette) Aether.Amethyst else lerp(glowStart, Color.Black, .55f)
    val baseBrush = homeCloudBackgroundBrush()
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drawRect(brush = baseBrush)

        // MARBLE_AURORA_SURFACE_V219 — a diagonal aurora band. Three radial glows at the corners
        // gave the page depth at its edges and a flat middle; this one cheap linear pass puts a
        // band of light across the diagonal the eye actually travels, so a long page has a
        // horizon instead of a void.
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    glowEnd.copy(alpha = if (dark) .05f else .035f),
                    Color.Transparent
                ),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height * .78f)
            )
        )
        if (dark && !dynamicPalette) return@Canvas

        val waveA = if (motionOn) motion.coarseBreathe(11_000) else .5f
        val waveB = if (motionOn) motion.coarseBreathe(15_000, offset = .35f) else .5f
        val aFactor = .82f + .36f * waveA
        val bFactor = .82f + .36f * waveB
        fun glowAlpha(base: Float, factor: Float): Float = (base * factor).coerceIn(0f, 1f)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    glowStart.copy(alpha = glowAlpha(if (dark) .13f else .065f, aFactor)),
                    Color.Transparent
                ),
                center = Offset(w * -.08f, h * -.06f),
                radius = (w + h) * .52f
            ),
            radius = (w + h) * .52f,
            center = Offset(w * -.08f, h * -.06f)
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    glowEnd.copy(alpha = glowAlpha(if (dark) .09f else .045f, bFactor)),
                    Color.Transparent
                ),
                center = Offset(w * 1.10f, h * .04f),
                radius = (w + h) * .46f
            ),
            radius = (w + h) * .46f,
            center = Offset(w * 1.10f, h * .04f)
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    glowFloor.copy(alpha = if (dark) .12f else .05f),
                    Color.Transparent
                ),
                center = Offset(w * .5f, h * 1.16f),
                radius = (w + h) * .58f
            ),
            radius = (w + h) * .58f,
            center = Offset(w * .5f, h * 1.16f)
        )

        // MARBLE_AURORA_SURFACE_V219 — one diagonal aurora plane. Three radial glows alone give a
        // page depth at its corners and nothing in the middle; this band crosses the page where the
        // eye actually travels, at an alpha that is invisible as a shape and visible as light.
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    glowEnd.copy(alpha = if (dark) .055f else .030f),
                    Color.Transparent
                ),
                start = Offset(0f, h * .06f),
                end = Offset(w, h * .72f)
            )
        )
    }
}

@Composable
internal fun anchoredTextBlockHeight(style: TextStyle, lines: Int): Dp {
    val unit=if (style.lineHeight.value.isNaN()) style.fontSize * 1.35f else style.lineHeight
    return with(LocalDensity.current) { (unit * lines.toFloat()).toDp() }
}

internal object PrismSurface {
    // MARBLE_AURORA_SURFACE_V219 — the radii move onto the platform's own step (24 dp card,
    // 20 dp tile, 16 dp inset) so a Marble card and an Android card describe the same silhouette.
    val CardRadius = 24.dp
    val TileRadius = 20.dp
    val InsetRadius = 16.dp

    val ControlRadius = 15.dp

    val ControlHeight = 48.dp
    val CompactControlHeight = 36.dp
    val IconControlSize = 42.dp
    val Hairline = 1.dp
    val StrongHairline = 1.5.dp
    // MARBLE_NATIVE_ANDROID_V213 — the depth table moves onto the platform's own steps.

    val RestingElevation = 4.dp
    val RaisedElevation = 9.dp
    val ControlElevation = 3.dp
    val PressedElevation = 1.dp
}

internal fun prismOnColor(base: Color): Color {
    val luminance = .2126f * base.red + .7152f * base.green + .0722f * base.blue

    return if (luminance > .60f) Color(0xFF001144) else Color(0xFFF0F8FF)
}

@Composable
internal fun Modifier.prismElevated(
    shape: Shape,
    tone: Color,
    selected: Boolean = false,
    fill: Color = Aether.VoidElevated,
    tint: Brush? = null
): Modifier {
    val elevation by animateDpAsState(
        targetValue=if (selected) PrismSurface.RaisedElevation else PrismSurface.RestingElevation,
        animationSpec=MarbleMotionSpecs.Dp,
        label="prism-elevation"
    )
    val hairline by animateColorAsState(
        targetValue=tone.copy(alpha=if (selected) .44f else .18f),
        animationSpec=MarbleMotionSpecs.Color,
        label="prism-elevation-hairline"
    )
    // MARBLE_NATIVE_ANDROID_V213 — one shadow pair, one hue, both themes: the surface's own tone

    val nativeShadow=marbleNativeShadow(tone)
    val contactHue=if(homeCloudDark()) Color.Black else Color(0xFF0A2540)
    val contactAlpha=if(homeCloudDark()) MarbleDepthPolicy.ContactAlphaDark else MarbleDepthPolicy.ContactAlphaLight
    return this
        .shadow(
            elevation=elevation,
            shape=shape,
            clip=false,
            ambientColor=nativeShadow.ambient,
            spotColor=nativeShadow.spot
        )
        .shadow(
            elevation=MarbleDepthPolicy.ContactElevation,
            shape=shape,
            clip=false,
            ambientColor=contactHue.copy(alpha=contactAlpha),
            spotColor=contactHue.copy(alpha=contactAlpha)
        )
        .border(PrismSurface.Hairline, hairline, shape)
        .clip(shape)
        .background(fill)
        .then(if (tint == null) Modifier else Modifier.background(tint))
}

@Composable
internal fun Modifier.prismWell(
    shape: Shape,
    tone: Color = Aether.Cyan,
    selected: Boolean = false
): Modifier {
    val fill by animateColorAsState(
        targetValue=if (selected) tone.copy(alpha=.085f) else Aether.GlassStrong.copy(alpha=.45f),
        animationSpec=MarbleMotionSpecs.Color,
        label="prism-well-fill"
    )

    val hairline by animateColorAsState(
        targetValue=if (selected) tone.copy(alpha=.38f) else tone.copy(alpha=.15f),
        animationSpec=MarbleMotionSpecs.Color,
        label="prism-well-hairline"
    )
    return this
        .clip(shape)
        .background(fill)
        .border(PrismSurface.Hairline, hairline, shape)
}

@Composable
internal fun PrismPanel(
    modifier: Modifier = Modifier,
    accent: Color = Aether.Cyan,
    selected: Boolean = false,
    radius: Dp = PrismSurface.CardRadius,
    contentPadding: PaddingValues = PaddingValues(MarbleSpacing.M),
    tint: Brush? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    verticalSpacing: Dp = MarbleSpacing.S,
    // MARBLE_EXPRESSIVE_MOTION_V186 — a page that wants its cards to cascade in passes each

    entranceIndex: Int = -1,
    content: @Composable ColumnScope.() -> Unit
) {
    val selectedProgress by animateFloatAsState(
        targetValue=if(selected) 1f else 0f,
        animationSpec=MarbleMotionSpecs.ResponseFloat,
        label="prism-selected-energy"
    )
    val elevation by animateDpAsState(
        targetValue=PrismSurface.RestingElevation +
            (PrismSurface.RaisedElevation - PrismSurface.RestingElevation) * selectedProgress,
        animationSpec=MarbleMotionSpecs.Dp,
        label="prism-panel-elevation"
    )
    val shape=RoundedCornerShape(radius)
    val surface=Aether.VoidElevated
    val violet=Aether.Amethyst

    val glowAlpha=.045f + .055f*selectedProgress

    // MARBLE_NATIVE_ANDROID_V213 — the rim is a *light*, not a border.

    val outline=marbleNativeOutline()
    val darkPanel = homeCloudDark()
    // MARBLE_AURORA_SURFACE_V219 — the rim is a light source, so it is brightest at the top edge
    // and settles into the platform outline at the bottom. On a light page the lit stop is white,
    // not the accent: a blue hairline around a blue-tinted card is a border, a white one is a light.
    val borderBrush=Brush.verticalGradient(
        listOf(
            if (darkPanel) {
                lerp(outline, accent, .34f + .40f*selectedProgress)
            } else {
                lerp(outline, Color.White, .62f + .22f*selectedProgress)
            },
            lerp(outline, accent, .16f + .22f*selectedProgress),
            lerp(outline, violet, .06f + .10f*selectedProgress)
        )
    )
    val nativeShadow=marbleNativeShadow(accent)
    val contactHue=if(homeCloudDark()) Color.Black else Color(0xFF0A2540)
    val contactAlpha=if(homeCloudDark()) MarbleDepthPolicy.ContactAlphaDark else MarbleDepthPolicy.ContactAlphaLight

    Box(
        modifier=modifier

            .then(if (entranceIndex >= 0) Modifier.marbleStaggerIn(entranceIndex) else Modifier)
            .shadow(
                elevation=elevation,
                shape=shape,
                clip=false,
                ambientColor=nativeShadow.ambient,
                spotColor=nativeShadow.spot
            )

            .shadow(
                elevation=MarbleDepthPolicy.ContactElevation,
                shape=shape,
                clip=false,
                ambientColor=contactHue.copy(alpha=contactAlpha),
                spotColor=contactHue.copy(alpha=contactAlpha)
            )
            .border(PrismSurface.Hairline,borderBrush,shape)
            .clip(shape)
            .background(surface)
            .then(
                if (onClick == null) Modifier
                else Modifier.kineticClickable(enabled=enabled, role=Role.Button, boundedShape=shape, onClick=onClick)
            )

            .then(if (tint == null) Modifier else Modifier.background(tint))
            // MARBLE_AURORA_SURFACE_V219 — the specular plane every card now carries, on top of
            // its own fill so the surface stays exactly the box.
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (homeCloudDark()) .045f else .32f),
                        Color.White.copy(alpha = 0f)
                    ),
                    startY = 0f,
                    endY = 46f
                )
            )
    ) {
        Canvas(Modifier.matchParentSize()) {
            val center=Offset(size.width*.08f,size.height*.02f)
            drawCircle(
                brush=Brush.radialGradient(
                    colors=listOf(
                        accent.copy(alpha=glowAlpha),
                        Color.Transparent
                    ),
                    center=center,
                    radius=size.maxDimension*.66f
                ),
                radius=size.maxDimension*.66f,
                center=center
            )
        }
        Column(
            modifier=Modifier.padding(contentPadding),
            verticalArrangement=Arrangement.spacedBy(verticalSpacing),
            content=content
        )
    }
}

@Composable
internal fun PrismWell(
    modifier: Modifier = Modifier,
    tone: Color = Aether.Cyan,
    selected: Boolean = false,
    radius: Dp = PrismSurface.InsetRadius,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val shape=RoundedCornerShape(radius)

    Box(
        modifier=modifier
            .prismWell(shape=shape, tone=tone, selected=selected)
            .then(
                if (onClick == null) Modifier
                else Modifier.kineticClickable(
                    enabled=enabled,
                    role=Role.Button,
                    boundedShape=shape,
                    onClick=onClick
                )
            )
            .padding(contentPadding),
        content=content
    )
}

/**
 * The one product button.
 *
 * Every action in MarbleNG — Library deck, add/import sheet, subscription manager, dialogs, the
 * detail page — is built from this shape, so an action's importance is expressed by its variant and
 * nothing else. Primary/Danger carry one flat filled skin; Secondary is a quiet tint; Quiet is for
 * dismiss/back. Label copy is single-line and centred, and the press response is scale plus rim,
 * which stays legible at any font scale.
 *
 * MARBLE_BUTTON_TEXT_RECT_REMOVED_DS_V68
 * Built as a plain Row (same stack as [PrismSelectionTile] / [PrismIconButton]), never as an M3
 * `Button`. The Material surface still painted a second, differently-coloured rectangle behind the
 * label even with `containerColor = Transparent` — the box users saw behind every Settings and
 * Library-filter control. One fill, optional hairline, one content colour; no nested surface.
 *
 * MARBLE_EXPRESSIVE_MOTION_V186 — the button now presses like the newest Android: the corner
 * radius softens toward a pill under the finger on a short emphasized curve, the face compresses
 * to .965, and the release SPRINGS — radius and scale both overshoot rest once before settling.
 * One interaction source feeds the morph, the press scale and the click, so the three can never
 * disagree; the fill, the hairline and the label grammar are exactly the V117 flat skin.
 */
internal enum class PrismButtonVariant { Primary, Secondary, Quiet, Danger }

@Composable
internal fun PrismButton(
    label: String,
    onClick: () -> Unit,
    tone: Color,
    modifier: Modifier = Modifier,
    variant: PrismButtonVariant = PrismButtonVariant.Secondary,
    enabled: Boolean = true,
    compact: Boolean = false,
    detail: String = "",
    badge: String = "",
    icon: (@Composable () -> Unit)? = null,
    contentPadding: PaddingValues? = null
) {
    val filled=enabled && (variant == PrismButtonVariant.Primary || variant == PrismButtonVariant.Danger)
    // MARBLE_EXPRESSIVE_MOTION_V186 — the shape is no longer a constant: the button owns one

    val interactionSource = remember { MutableInteractionSource() }
    val shape = rememberExpressiveMorphShape(
        interactionSource = interactionSource,
        restRadius = if (compact) 11.dp else PrismSurface.ControlRadius,
        pressedRadius = if (compact) MarbleExpressiveShapes.CompactButtonPressRadius else MarbleExpressiveShapes.ButtonPressRadius
    )

    val accent=if (variant == PrismButtonVariant.Danger) Aether.Danger else tone
    val content=when {
        !enabled -> Aether.InkFaint
        filled -> prismOnColor(accent)
        variant == PrismButtonVariant.Quiet -> Aether.InkMuted
        else -> Aether.Ink
    }

    // MARBLE_AURORA_SURFACE_V219 — a filled button is a lit object, not a flat rectangle: the
    // accent is lightened at the top and deepened at the bottom, and a specular plane sits over it.
    val skin: Brush = when {
        !enabled -> SolidColor(Aether.GlassStrong.copy(alpha=.42f))
        filled -> Brush.verticalGradient(
            listOf(
                lerp(accent, Color.White, .20f),
                accent,
                lerp(accent, Color.Black, .14f)
            )
        )
        variant == PrismButtonVariant.Quiet -> SolidColor(Color.Transparent)
        else -> SolidColor(accent.copy(alpha=.12f))
    }
    val hairline: Color=when {
        !enabled -> Color.Transparent
        filled -> Color.Transparent
        variant == PrismButtonVariant.Quiet -> Color.Transparent
        else -> accent.copy(alpha=.26f)
    }
    val pad=contentPadding ?: PaddingValues(

        horizontal=if (compact) 13.dp else 16.dp,
        vertical=if (compact) 8.dp else 10.dp
    )

    CompositionLocalProvider(LocalContentColor provides content) {
        Row(
            modifier=modifier

                .marbleTapTarget(MarbleTapTarget.Floor, enabled = compact)
                .heightIn(
                    min=when {
                        detail.isNotBlank() && !compact -> 54.dp
                        compact -> PrismSurface.CompactControlHeight
                        else -> PrismSurface.ControlHeight
                    }
                )
                .widthIn(min=if (compact) 64.dp else 88.dp)
                .then(
                    if (filled) {
                        Modifier.shadow(
                            elevation = PrismSurface.ControlElevation,
                            shape = shape,
                            clip = false,
                            ambientColor = accent.copy(alpha = .22f),
                            spotColor = accent.copy(alpha = .34f)
                        )
                    } else {
                        Modifier
                    }
                )
                .clip(shape)
                .background(skin)
                .background(
                    if (filled) {
                        Brush.verticalGradient(
                            colors = listOf(Color.White.copy(alpha = .20f), Color.Transparent),
                            startY = 0f,
                            endY = 30f
                        )
                    } else {
                        SolidColor(Color.Transparent)
                    }
                )
                .border(PrismSurface.Hairline, hairline, shape)
                .kineticClickable(
                    enabled=enabled,
                    role=Role.Button,
                    boundedShape=shape,
                    pressScale=(if (compact) MarbleControlKind.Compact else MarbleControlKind.Standard).pressScale,
                    interactionSource=interactionSource,
                    releaseSpec=MarbleExpressiveSpecs.SpringReleaseFloat,
                    onClick=onClick
                )
                .padding(pad),
            verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.Center
        ) {
            if (icon != null) {
                icon()
                Spacer(Modifier.width(if (compact) 6.dp else 8.dp))
            }
            Column(
                modifier=Modifier.weight(1f, fill=false),
                horizontalAlignment=Alignment.Start
            ) {
                Text(
                    trx(label),
                    color=content,
                    style=if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                    fontWeight=FontWeight.Bold,
                    maxLines=1,
                    overflow=TextOverflow.Ellipsis
                )
                if (detail.isNotBlank()) {
                    Text(
                        trx(detail),
                        style=MaterialTheme.typography.labelSmall,
                        color=content.copy(alpha=.72f),
                        maxLines=1,
                        overflow=TextOverflow.Ellipsis
                    )
                }
            }
            if (badge.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    trx(badge),
                    style=MaterialTheme.typography.labelSmall.copy(fontFamily=FontFamily.Monospace),
                    fontWeight=FontWeight.Bold,
                    color=content.copy(alpha=.86f)
                )
            }
        }
    }
}

@Composable
internal fun PrismIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: Color = Aether.Cyan,
    selected: Boolean = false,
    enabled: Boolean = true,
    size: Dp = PrismSurface.IconControlSize,
    descriptiveLabel: String,
    content: @Composable () -> Unit
) {
    val label = if (isFaLexiconKey(descriptiveLabel)) trx(descriptiveLabel) else descriptiveLabel
    val shape=RoundedCornerShape(size * .36f)
    val fill by animateColorAsState(
        targetValue=if (selected) tone.copy(alpha=.18f) else Aether.GlassStrong.copy(alpha=.34f),
        animationSpec=MarbleMotionSpecs.Color,
        label="icon-control-fill"
    )

    val hairline by animateColorAsState(
        targetValue=if (selected) tone.copy(alpha=.50f) else tone.copy(alpha=.18f),
        animationSpec=MarbleMotionSpecs.Color,
        label="icon-control-hairline"
    )

    Box(
        modifier=modifier

            .marbleTapTarget(MarbleTapTarget.Floor)
            .semantics { contentDescription=label }
            // MARBLE_EXPRESSIVE_MOTION_V186 — icon controls compress and spring back with the

            .kineticClickable(
                enabled=enabled,
                role=Role.Button,
                pressScale=MarbleControlKind.Icon.pressScale,
                boundedShape=shape,
                releaseSpec=MarbleExpressiveSpecs.SpringReleaseFloat,
                onClick=onClick
            ),
        contentAlignment=Alignment.Center
    ) {
        Box(
            modifier=Modifier
                .size(size)
                .clip(shape)
                .background(fill)
                .border(PrismSurface.Hairline,hairline,shape),
            contentAlignment=Alignment.Center
        ) {
            content()
        }
    }
}

@Composable
internal fun PrismSignalMeter(
    bars: Int,
    tone: Color,
    modifier: Modifier = Modifier,
    total: Int = 4,
    inactive: Color = Aether.GlassBorder
) {
    Canvas(modifier) {
        if (total <= 0) return@Canvas
        val slot=size.width / total
        val barWidth=(slot * .55f).coerceAtMost(4.5.dp.toPx())
        val corner=(barWidth / 2f)
        repeat(total) { index ->
            val ratio=(index + 1f) / total
            val height=size.height * (.44f + .56f * ratio)
            drawRoundRect(
                color=if (index < bars) tone else inactive.copy(alpha=.7f),
                topLeft=Offset(slot * index + (slot - barWidth) / 2f,size.height - height),
                size=Size(barWidth,height),
                cornerRadius=CornerRadius(corner)
            )
        }
    }
}

@Composable
internal fun PrismCheckBadge(
    tone: Color,
    modifier: Modifier = Modifier,
    diameter: Dp = 18.dp
) {
    Box(
        modifier=modifier
            .size(diameter)
            .clip(CircleShape)
            .background(tone),
        contentAlignment=Alignment.Center
    ) {
        Canvas(Modifier.size(diameter * .56f)) {
            val w=size.width
            drawPath(
                path=Path().apply {
                    moveTo(w * .08f,w * .52f)
                    lineTo(w * .38f,w * .82f)
                    lineTo(w * .94f,w * .14f)
                },
                color=prismOnColor(tone),
                style=Stroke(width=w * .22f,cap=StrokeCap.Round)
            )
        }
    }
}

@Composable
internal fun PrismSelectionTile(
    label: String,
    selected: Boolean,
    tone: Color,
    modifier: Modifier = Modifier,
    detail: String = "",
    // MARBLE_NATIVE_ANDROID_V213 — the platform's touch floor, as the *default* this time: a

    minHeight: Dp = MarbleNativeSpec.TouchTargetDp.dp,
    alignment: Alignment = Alignment.Center,
    leading: (@Composable (() -> Unit))? = null,
    trailing: (@Composable (() -> Unit))? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    // MARBLE_NATIVE_ANDROID_V213 — and they are made of the platform's material now. The chosen

    val shape=marbleNativeShape(MarbleNativeSpec.RowCornerDp.dp)
    val tileOutline = marbleNativeOutline()
    val tileResting = marbleNativeCardFill()
    val tileChosen = marbleNativeSelectedFill(tone)

    val fill by animateColorAsState(
        targetValue=if (selected) tileChosen else tileResting,
        animationSpec=MarbleMotionSpecs.Color,
        label="selection-fill"
    )

    Row(
        modifier=modifier
            .heightIn(min=minHeight)
            .clip(shape)
            .background(fill)
            .border(
                PrismSurface.Hairline,
                if (selected) tone.copy(alpha=.34f) else tileOutline,
                shape
            )
            // MARBLE_EXPRESSIVE_MOTION_V186 — one acknowledgement pop the instant the tile

            .marblePopWhen(selected, peak = 1.045f)
            .kineticClickable(enabled=enabled, role=Role.Button, boundedShape=shape, showIndication=false, releaseSpec=MarbleExpressiveSpecs.SpringReleaseFloat, onClick=onClick)
            .padding(horizontal=13.dp,vertical=9.dp),
        verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=when (alignment) {
            Alignment.CenterStart -> Arrangement.Start
            Alignment.CenterEnd -> Arrangement.End
            else -> Arrangement.Center
        }
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(9.dp))
        }
        Column(horizontalAlignment=Alignment.Start) {
            Text(
                trx(label),
                color=if (selected) tone else Aether.Ink,
                style=MaterialTheme.typography.labelMedium,
                fontWeight=if (selected) FontWeight.Bold else FontWeight.SemiBold,
                maxLines=1,
                overflow=TextOverflow.Ellipsis
            )
            if (detail.isNotBlank()) {
                Text(
                    trx(detail),
                    color=if (selected) tone.copy(alpha=.8f) else Aether.InkFaint,
                    style=MaterialTheme.typography.labelSmall,
                    maxLines=1,
                    overflow=TextOverflow.Ellipsis
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
internal fun PrismRouteFrame(
    tone: Color,
    modifier: Modifier = Modifier,
    radius: Dp = 22.dp
) {
    val motion=MarbleMotion.current
    val accent=Aether.Cyan

    Canvas(modifier) {
        val flow=motion.loop(3_400)
        val breathe=.72f + .28f*motion.breathe(2_600)
        val ring=(1.15.dp + .75.dp*breathe).toPx()
        val inset=ring/2f
        val corner=radius.toPx()
        drawRoundRect(
            brush=Brush.linearGradient(
                colors=listOf(
                    tone.copy(alpha=.92f),
                    tone.copy(alpha=.36f),
                    accent.copy(alpha=.68f)
                ),
                start=Offset.Zero,
                end=Offset(size.width,size.height)
            ),
            topLeft=Offset(inset,inset),
            size=Size(
                (size.width-ring).coerceAtLeast(0f),
                (size.height-ring).coerceAtLeast(0f)
            ),
            cornerRadius=CornerRadius((corner-inset).coerceAtLeast(0f)),
            style=Stroke(width=ring,cap=StrokeCap.Round)
        )

        repeat(2) { index ->
            val spread=(4 + index*5).dp.toPx()
            drawRoundRect(
                color=tone.copy(alpha=if(index==0) .085f else .05f),
                topLeft=Offset(spread/2f,spread/2f),
                size=Size(
                    (size.width-spread).coerceAtLeast(0f),
                    (size.height-spread).coerceAtLeast(0f)
                ),
                cornerRadius=CornerRadius((corner+spread/2f).coerceAtLeast(0f)),
                style=Stroke(width=spread)
            )
        }

        val rail=2.6.dp.toPx()
        val railX=6.5.dp.toPx()
        val railTop=15.dp.toPx()
        val railBottom=size.height-15.dp.toPx()
        if(railBottom-railTop > rail*2f) {
            val span=railBottom-railTop
            drawRoundRect(
                brush=Brush.verticalGradient(
                    colors=listOf(
                        tone.copy(alpha=.92f),
                        tone.copy(alpha=.28f),
                        accent.copy(alpha=.80f)
                    ),
                    startY=railTop,
                    endY=railBottom
                ),
                topLeft=Offset(railX,railTop),
                size=Size(rail,span),
                cornerRadius=CornerRadius(rail/2f)
            )
            val pulseY=railTop+rail+(span-rail*2f)*flow
            drawCircle(
                color=tone.copy(alpha=.26f),
                radius=rail*3.2f,
                center=Offset(railX+rail/2f,pulseY)
            )
            drawCircle(
                color=tone,
                radius=rail*.72f,
                center=Offset(railX+rail/2f,pulseY)
            )
        }
    }
}

@Composable
internal fun PrismBadge(
    text: String,
    tone: Color,
    modifier: Modifier = Modifier,
    strong: Boolean = false
) {
    val shape=RoundedCornerShape(999.dp)
    Row(
        modifier=modifier
            .border(
                1.dp,
                tone.copy(alpha=if(strong) .48f else .28f),
                shape
            )
            .clip(shape)
            .background(tone.copy(alpha=if(strong) .11f else .065f))
            .padding(horizontal=11.dp,vertical=7.dp),
        verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(tone)
        )
        Text(
            trx(text),
            color=tone,
            style=MaterialTheme.typography.labelSmall,
            fontWeight=if(strong) FontWeight.Bold else FontWeight.SemiBold,
            maxLines=1
        )
    }
}

@Composable
internal fun MarbleElevatedSurface(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(MarbleSpacing.M),
    content: @Composable ColumnScope.() -> Unit
) {
    PrismPanel(
        modifier=modifier,
        contentPadding=contentPadding,
        content=content
    )
}

@Composable
internal fun MarbleMetricCard(
    title: String,
    value: String,
    unit: String,
    tone: Color,
    modifier: Modifier = Modifier,
    sparkline: List<Int> = emptyList(),

    flaggedSamples: List<Boolean> = emptyList()
) {
    // MARBLE_NATIVE_ANDROID_V213 — the cell is a platform card: the tile corner, the platform's

    val shape=marbleNativeShape(MarbleNativeSpec.TileCornerDp.dp)
    val bentoOutline=marbleNativeOutline()
    val bentoFloor=marbleNativeCardFill()
    val border=Brush.verticalGradient(
        listOf(
            lerp(bentoOutline,tone,.34f),
            bentoOutline
        )
    )
    Box(
        modifier=modifier
            .border(1.dp,border,shape)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        tone.copy(alpha=if (homeCloudDark()) .07f else .045f),
                        bentoFloor
                    )
                )
            )
            .padding(MarbleSpacing.M)
    ) {
        Column(
            modifier=Modifier.fillMaxSize(),
            verticalArrangement=Arrangement.spacedBy(MarbleSpacing.XS)
        ) {
            Row(
                modifier=Modifier.fillMaxWidth(),
                verticalAlignment=Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(tone)
                )
                Spacer(Modifier.width(7.dp))
                // MARBLE_NATIVE_ANDROID_V213 — the metric's name is a label, not a shout:

                Text(
                    trx(title),
                    color=marbleNativeInkVariant(),
                    style=MaterialTheme.typography.labelMedium,
                    fontWeight=FontWeight.SemiBold,
                    letterSpacing=MarbleNativeSpec.SectionLabelTrackingSp.sp
                )
            }
            Row(verticalAlignment=Alignment.Bottom) {
                // MARBLE_EXPRESSIVE_MOTION_V186 — the readout rolls: a live metric that updates

                MarbleExpressiveValueText(
                    value=value,
                    color=if(value=="—") Aether.InkMuted else tone,
                    style=MaterialTheme.typography.headlineMedium.copy(
                        fontFamily=FontFamily.Monospace,
                        fontWeight=FontWeight.Bold
                    )
                )
                if(unit.isNotBlank()) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        unit,
                        color=tone.copy(alpha=.72f),
                        style=MaterialTheme.typography.labelSmall
                    )
                }
            }
            if(sparkline.size>=3) {
                Spacer(Modifier.weight(1f))
                MarbleSparkline(
                    samples=sparkline,
                    tone=tone,
                    flagged=cleanFlags(flaggedSamples, sparkline),
                    modifier=Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                )
            }
        }
    }
}

@Composable
internal fun MarbleSparkline(
    samples: List<Int>,
    tone: Color,
    modifier: Modifier = Modifier,
    flagged: List<Int> = emptyList()
) {
    val clean=samples.filter { it > 0 }.takeLast(36)
    val grid=Aether.GlassBorder
    val surface=Aether.VoidElevated

    val danger=Aether.Danger
    Canvas(modifier) {
        if(clean.size<2) return@Canvas

        val min=clean.minOrNull()?.toFloat() ?: return@Canvas
        val max=clean.maxOrNull()?.toFloat() ?: return@Canvas
        val range=(max-min).coerceAtLeast(1f)
        val dx=size.width/(clean.size-1).coerceAtLeast(1)

        repeat(3) { index ->
            val y=size.height*(index+1)/4f
            drawLine(
                color=grid.copy(alpha=.45f),
                start=Offset(0f,y),
                end=Offset(size.width,y),
                strokeWidth=1.dp.toPx()
            )
        }

        val path=Path()
        clean.forEachIndexed { index,value ->
            val x=dx*index
            val y=size.height-((value-min)/range)*size.height
            if(index==0) path.moveTo(x,y) else path.lineTo(x,y)
        }

        drawPath(
            path=path,
            color=tone.copy(alpha=.18f),
            style=Stroke(width=5.dp.toPx(),cap=StrokeCap.Round)
        )
        drawPath(
            path=path,
            color=tone,
            style=Stroke(width=2.35.dp.toPx(),cap=StrokeCap.Round)
        )

        val last=clean.last().toFloat()
        val lastY=size.height-((last-min)/range)*size.height
        drawCircle(
            color=surface,
            radius=5.dp.toPx(),
            center=Offset(size.width,lastY)
        )
        drawCircle(
            color=tone,
            radius=3.dp.toPx(),
            center=Offset(size.width,lastY)
        )

        flagged.filter { it in clean.indices }.forEach { index ->
            val x=dx*index
            val y=size.height-((clean[index]-min)/range)*size.height
            drawCircle(
                color=danger,
                radius=3.6.dp.toPx(),
                center=Offset(x,y)
            )
        }
    }
}

private fun cleanFlags(flaggedSamples: List<Boolean>, samples: List<Int>): List<Int> {
    if (flaggedSamples.isEmpty()) return emptyList()
    val pairs = samples.zip(flaggedSamples)
        .filter { it.first > 0 }
        .takeLast(36)
    return pairs.mapIndexedNotNull { index, pair -> if (pair.second) index else null }
}

@Composable
internal fun PrismConnectionStage(
    tone: Color,
    connected: Boolean,
    connecting: Boolean,
    blocked: Boolean,
    qualityScore: Int,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val surface=Aether.VoidElevated
    val outline=tone.copy(alpha=.18f)
    val muted=Aether.InkMuted

    val motion=MarbleMotion.current
    val progress=qualityScore.coerceIn(0,100)/100f
    val label=when {
        connected -> "Disconnect"
        connecting -> "Cancel"
        blocked -> "Reset"
        else -> "Connect"
    }

    Box(
        modifier=modifier
            .height(164.dp)
            .semantics { contentDescription="$label connection control" }
            .kineticClickable(
                role=Role.Button,
                pressScale=.982f,
                onClick=onToggle
            ),
        contentAlignment=Alignment.Center
    ) {
        Canvas(Modifier.matchParentSize()) {
            val phase=motion.loop(if(connecting) 950 else 1_650)
            val breathe=motion.breathe(2_400)
            val left=Offset(size.width*.16f,size.height*.43f)
            val center=Offset(size.width*.50f,size.height*.40f)
            val right=Offset(size.width*.84f,size.height*.43f)

            val route=Path().apply {
                moveTo(left.x,left.y)
                cubicTo(
                    size.width*.30f,size.height*.24f,
                    size.width*.38f,size.height*.24f,
                    center.x,center.y
                )
                cubicTo(
                    size.width*.62f,size.height*.55f,
                    size.width*.72f,size.height*.55f,
                    right.x,right.y
                )
            }

            drawPath(
                route,
                color=outline.copy(alpha=.72f),
                style=Stroke(3.dp.toPx(),cap=StrokeCap.Round)
            )

            if(connected || connecting) {
                drawPath(
                    route,
                    color=tone.copy(alpha=if(connected) .70f else .42f),
                    style=Stroke(3.dp.toPx(),cap=StrokeCap.Round)
                )

                val t=phase
                val moving=when {
                    t<.5f -> {
                        val u=t*2f
                        Offset(
                            x=left.x+(center.x-left.x)*u,
                            y=left.y+(center.y-left.y)*u -
                                sin(u*PI.toFloat())*20.dp.toPx()
                        )
                    }
                    else -> {
                        val u=(t-.5f)*2f
                        Offset(
                            x=center.x+(right.x-center.x)*u,
                            y=center.y+(right.y-center.y)*u +
                                sin(u*PI.toFloat())*15.dp.toPx()
                        )
                    }
                }
                drawCircle(
                    color=tone.copy(alpha=.18f),
                    radius=8.dp.toPx(),
                    center=moving
                )
                drawCircle(
                    color=tone,
                    radius=3.5.dp.toPx(),
                    center=moving
                )
            }

            val deviceSize=Size(34.dp.toPx(),52.dp.toPx())
            val deviceTop=Offset(
                left.x-deviceSize.width/2f,
                left.y-deviceSize.height/2f
            )
            drawRoundRect(
                color=surface,
                topLeft=deviceTop,
                size=deviceSize,
                cornerRadius=CornerRadius(10.dp.toPx())
            )
            drawRoundRect(
                color=muted.copy(alpha=.78f),
                topLeft=deviceTop,
                size=deviceSize,
                cornerRadius=CornerRadius(10.dp.toPx()),
                style=Stroke(2.dp.toPx())
            )
            drawCircle(
                color=tone,
                radius=2.4.dp.toPx(),
                center=Offset(left.x,left.y+20.dp.toPx())
            )

            repeat(3) { index ->
                val y=right.y+(index-1)*13.dp.toPx()
                val top=Offset(
                    right.x-24.dp.toPx(),
                    y-5.dp.toPx()
                )
                drawRoundRect(
                    color=surface,
                    topLeft=top,
                    size=Size(48.dp.toPx(),10.dp.toPx()),
                    cornerRadius=CornerRadius(5.dp.toPx())
                )
                drawRoundRect(
                    color=muted.copy(alpha=.72f),
                    topLeft=top,
                    size=Size(48.dp.toPx(),10.dp.toPx()),
                    cornerRadius=CornerRadius(5.dp.toPx()),
                    style=Stroke(1.6.dp.toPx())
                )
                drawCircle(
                    color=if(index==1) tone else muted.copy(alpha=.45f),
                    radius=1.8.dp.toPx(),
                    center=Offset(right.x+15.dp.toPx(),y)
                )
            }

            val orbRadius=48.dp.toPx()
            drawCircle(
                brush=Brush.radialGradient(
                    colors=listOf(
                        tone.copy(alpha=.16f+.06f*breathe),
                        tone.copy(alpha=.055f),
                        Color.Transparent
                    ),
                    center=center,
                    radius=orbRadius*1.45f
                ),
                radius=orbRadius*1.45f,
                center=center
            )
            drawCircle(color=surface,radius=orbRadius,center=center)
            drawCircle(
                color=tone.copy(alpha=.58f),
                radius=orbRadius,
                center=center,
                style=Stroke(2.4.dp.toPx())
            )
            drawCircle(
                color=outline.copy(alpha=.5f),
                radius=orbRadius+8.dp.toPx(),
                center=center,
                style=Stroke(1.2.dp.toPx())
            )

            val sweep=when {
                connected && qualityScore>=0 -> 360f*progress
                // MARBLE_EXPRESSIVE_MOTION_V186 — the securing arc is wavy now: it stretches and

                connecting -> ExpressiveMath.arcSweep(phase, 72f, 142f)
                blocked -> 300f
                else -> 78f
            }
            val start=if(connecting) -90f+phase*360f else -90f
            drawArc(
                color=tone,
                startAngle=start,
                sweepAngle=sweep,
                useCenter=false,
                topLeft=Offset(
                    center.x-orbRadius-8.dp.toPx(),
                    center.y-orbRadius-8.dp.toPx()
                ),
                size=Size(
                    (orbRadius+8.dp.toPx())*2f,
                    (orbRadius+8.dp.toPx())*2f
                ),
                style=Stroke(5.dp.toPx(),cap=StrokeCap.Round)
            )

            val pR=17.dp.toPx()
            drawArc(
                color=tone,
                startAngle=-42f,
                sweepAngle=264f,
                useCenter=false,
                topLeft=Offset(center.x-pR,center.y-pR),
                size=Size(pR*2f,pR*2f),
                style=Stroke(4.dp.toPx(),cap=StrokeCap.Round)
            )
            drawLine(
                color=tone,
                start=Offset(center.x,center.y-23.dp.toPx()),
                end=Offset(center.x,center.y+1.dp.toPx()),
                strokeWidth=4.dp.toPx(),
                cap=StrokeCap.Round
            )
        }

        Column(
            modifier=Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom=2.dp),
            horizontalAlignment=Alignment.CenterHorizontally
        ) {
            Text(
                label,
                color=tone,
                style=MaterialTheme.typography.labelMedium,
                fontWeight=FontWeight.Bold
            )
        }
    }
}

@Composable
internal fun PrismSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val shape=RoundedCornerShape(PrismSurface.ControlRadius)
    var focused by remember { mutableStateOf(false) }
    val frame by animateColorAsState(
        targetValue=if (focused) Aether.Cyan.copy(alpha=.65f) else Aether.GlassBorder,
        animationSpec=MarbleMotionSpecs.Color,
        label="search-frame"
    )

    Box(
        modifier=modifier
            .height(PrismSurface.ControlHeight)
            .clip(shape)
            .background(Aether.GlassStrong.copy(alpha=.55f))
            .border(PrismSurface.Hairline,frame,shape),
        contentAlignment=Alignment.Center
    ) {
        Row(
            modifier=Modifier
                .fillMaxWidth()
                .padding(horizontal=14.dp),
            verticalAlignment=Alignment.CenterVertically
        ) {
            val iconColor=if (focused) Aether.Cyan else Aether.InkMuted
            Canvas(Modifier.size(19.dp)) {
                drawCircle(
                    color=iconColor,
                    radius=size.minDimension*.28f,
                    center=Offset(size.width*.43f,size.height*.43f),
                    style=Stroke(1.8.dp.toPx(),cap=StrokeCap.Round)
                )
                drawLine(
                    color=iconColor,
                    start=Offset(size.width*.63f,size.height*.63f),
                    end=Offset(size.width*.82f,size.height*.82f),
                    strokeWidth=1.8.dp.toPx(),
                    cap=StrokeCap.Round
                )
            }
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value=value,
                onValueChange=onValueChange,
                singleLine=true,
                modifier=Modifier.weight(1f),
                textStyle=MaterialTheme.typography.bodyMedium.copy(color=Aether.Ink),
                cursorBrush=SolidColor(Aether.Cyan),
                decorationBox={ innerField ->
                    Column {
                        innerField()
                    }
                    if (value.isEmpty()) {
                        Text(
                            trx(placeholder),
                            color=Aether.InkFaint,
                            style=MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            )
        }
    }
}

@Composable
internal fun PrismThemeChoice(
    label: String,
    detail: String,
    selected: Boolean,
    darkPreview: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,

    dynamicPreview: Boolean = false,
    onClick: () -> Unit
) {
    val shape=RoundedCornerShape(18.dp)

    val context = LocalContext.current
    val generated =
        if (dynamicPreview && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dynamicLightColorScheme(context)
        } else {
            null
        }
    val previewBg=generated?.background
        ?: if(darkPreview) Color(0xFF000000) else Color(0xFFF4F8FD)
    val previewSurface=generated?.surface
        ?: if(darkPreview) Color(0xFF001144) else Color.White
    val previewText=generated?.onSurface
        ?: if(darkPreview) Color(0xFFF0F8FF) else Color(0xFF001144)
    val previewAccent = generated?.primary ?: accent
    val selectionTone = if (generated != null) generated.primary else Aether.Cyan
    val border=if(selected) selectionTone.copy(alpha=.52f) else previewAccent.copy(alpha=.20f)

    Column(
        modifier=modifier
            .heightIn(min=104.dp)
            .border(1.dp,border,shape)
            .clip(shape)
            .background(
                if(selected) selectionTone.copy(alpha=.065f)
                else Aether.VoidElevated
            )
            .kineticClickable(role=Role.Button,onClick=onClick)
            .padding(8.dp),
        verticalArrangement=Arrangement.spacedBy(7.dp)
    ) {
        Box(
            modifier=Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(previewBg)
                .padding(7.dp)
        ) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .width(28.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(previewText.copy(alpha=.78f))
            )
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(.62f)
                    .height(18.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(previewSurface)
                    .border(
                        1.dp,
                        accent.copy(alpha=.26f),
                        RoundedCornerShape(7.dp)
                    )
            )
            Row(
                Modifier.align(Alignment.BottomEnd),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (generated != null) {
                    listOf(generated.primary, generated.secondary, generated.tertiary)
                        .forEach { dot ->
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(dot)
                            )
                        }
                } else {
                    Box(
                        Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(previewAccent)
                    )
                }
            }
        }
        Text(
            trx(label),
            color=if(selected) selectionTone else Aether.Ink,
            style=MaterialTheme.typography.labelMedium,
            fontWeight=FontWeight.Bold
        )
        Text(
            trx(detail),
            color=Aether.InkFaint,
            style=MaterialTheme.typography.labelSmall,
            maxLines=1
        )
    }
}
