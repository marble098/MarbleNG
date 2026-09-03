package com.marbleng.app.ui

// MARBLE_M3_EXPRESSIVE_DESIGN_SYSTEM_V53
// MARBLE_PRISM_DESIGN_SYSTEM_V54
// MARBLE_PRISM_POLISH_V55
// MARBLE_GLOBAL_CONTROL_POLISH_DS_V60
// MARBLE_FLUID_PRISM_STATE_V62
// MARBLE_ACTIVE_NODE_HALO_DS_V64
// MARBLE_ANCHORED_STATUS_TEXT_DS_V64
// MARBLE_UNIFIED_SURFACE_SYSTEM_DS_V65
// MARBLE_PRISM_BUTTON_SYSTEM_DS_V65
// MARBLE_PRISM_RIM_AND_SEARCH_FRAME_DS_V66
// MARBLE_BUTTON_TEXT_RECT_REMOVED_DS_V68

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
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
}

internal enum class MarbleMetricBand {
    UNKNOWN, GOOD, WARNING, POOR
}

internal fun pingMetricBand(ms: Int): MarbleMetricBand = when {
    ms <= 0 -> MarbleMetricBand.UNKNOWN
    ms < 100 -> MarbleMetricBand.GOOD
    ms <= 250 -> MarbleMetricBand.WARNING
    else -> MarbleMetricBand.POOR
}

internal fun jitterMetricBand(ms: Int, samples: Int): MarbleMetricBand = when {
    samples <= 0 || ms < 0 -> MarbleMetricBand.UNKNOWN
    ms < 20 -> MarbleMetricBand.GOOD
    ms <= 50 -> MarbleMetricBand.WARNING
    else -> MarbleMetricBand.POOR
}

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

@Composable
internal fun marbleMetricTone(band: MarbleMetricBand): Color = when (band) {
    MarbleMetricBand.GOOD -> Aether.Emerald
    MarbleMetricBand.WARNING -> Aether.Amber
    MarbleMetricBand.POOR -> Aether.Danger
    MarbleMetricBand.UNKNOWN -> Aether.InkMuted
}

/**
 * MARBLE_HOME_GRADIENTS_V116 — the page-wide ambient field now belongs to the selected Home style
 * instead of one grey-blue wash: every flavor gets its own professional multi-colour gradient
 * (electric/violet/ice for Signature, emerald/amethyst/gold for the organism, gold/azure/magenta
 * for Orbit, amethyst/cyan/emerald for the nebula and slate/ice/amber for the blueprint), layered
 * softly so cards stay readable while the viewport never reads as grey.
 */
@Composable
internal fun PrismBackdrop(
    modifier: Modifier = Modifier,
    flavor: HomeFlavor = HomeFlavor.PRO
) {
    val base=Aether.Void
    val dot=Aether.InkFaint
    // Palette: [primary, secondary, tertiary] of the flavor's own multi-colour identity.
    val primary: Color
    val secondary: Color
    val tertiary: Color
    when (flavor) {
        HomeFlavor.PRO -> {
            primary = Aether.CyanBright
            secondary = Aether.Amethyst
            tertiary = Aether.Emerald
        }
        HomeFlavor.ORGANIC -> {
            primary = Aether.Emerald
            secondary = Aether.Amethyst
            tertiary = Aether.Amber
        }
        HomeFlavor.ORBIT -> {
            primary = Aether.Amber
            secondary = Aether.CyanBright
            tertiary = Aether.AmethystBright
        }
        HomeFlavor.NEBULA -> {
            primary = Aether.AmethystBright
            secondary = Aether.CyanBright
            tertiary = Aether.Emerald
        }
        HomeFlavor.BLUEPRINT -> {
            primary = Aether.SlateBright
            secondary = Aether.CyanBright
            tertiary = Aether.Amber
        }
    }

    Canvas(modifier) {
        drawRect(base)

        // One diagonal multi-stop wash ties the three hues together; a wash alone stays grey-ish,
        // so the three halos below are the actual colour story.
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    primary.copy(alpha=.075f),
                    secondary.copy(alpha=.050f),
                    tertiary.copy(alpha=.038f)
                ),
                start = Offset(0f, size.height * .12f),
                end = Offset(size.width, size.height * .92f)
            )
        )

        val primaryCenter=Offset(size.width*.86f,size.height*.06f)
        drawCircle(
            brush=Brush.radialGradient(
                colors=listOf(
                    primary.copy(alpha=.16f),
                    primary.copy(alpha=.055f),
                    Color.Transparent
                ),
                center=primaryCenter,
                radius=size.width*.74f
            ),
            radius=size.width*.74f,
            center=primaryCenter
        )

        val secondaryCenter=Offset(size.width*.05f,size.height*.46f)
        drawCircle(
            brush=Brush.radialGradient(
                colors=listOf(
                    secondary.copy(alpha=.14f),
                    secondary.copy(alpha=.045f),
                    Color.Transparent
                ),
                center=secondaryCenter,
                radius=size.width*.68f
            ),
            radius=size.width*.68f,
            center=secondaryCenter
        )

        val tertiaryCenter=Offset(size.width*.82f,size.height*.90f)
        drawCircle(
            brush=Brush.radialGradient(
                colors=listOf(
                    tertiary.copy(alpha=.12f),
                    tertiary.copy(alpha=.035f),
                    Color.Transparent
                ),
                center=tertiaryCenter,
                radius=size.width*.58f
            ),
            radius=size.width*.58f,
            center=tertiaryCenter
        )

        val step=30.dp.toPx()
        val radius=.72.dp.toPx()
        var y=step*.5f
        while(y<size.height) {
            var x=step*.5f
            while(x<size.width) {
                drawCircle(
                    color=dot.copy(alpha=.038f),
                    radius=radius,
                    center=Offset(x,y)
                )
                x+=step
            }
            y+=step
        }
    }
}

/**
 * Reserve exactly [lines] lines of [style] as a height.
 *
 * Runtime status copy changes length the moment a state flips. Unanchored, a sentence that wraps into a
 * second line pushes every control below it up and down. The conversion runs through the current density
 * so font scaling keeps the reservation honest instead of clipping the copy.
 */
@Composable
internal fun anchoredTextBlockHeight(style: TextStyle, lines: Int): Dp {
    val unit=if (style.lineHeight.value.isNaN()) style.fontSize * 1.35f else style.lineHeight
    return with(LocalDensity.current) { (unit * lines.toFloat()).toDp() }
}

/**
 * The single depth contract every container in MarbleNG is measured against.
 *
 * Before this existed, a panel could ship with a shadow, with a hairline, or with neither, and the
 * difference was decided by whoever happened to write that row. The rule is now mechanical:
 *
 *  - an **elevated** surface (card, sheet, hero portal, node row) carries exactly one soft,
 *    state-tinted shadow and one hairline;
 *  - an **inset** surface ([PrismWell]) is recessed by fill, never by shadow — that is what a
 *    metric strip, a sub-row or a status line inside a card uses;
 *  - **controls** ([PrismButton], [PrismIconButton], selection tiles) reuse the same radii and the
 *    same selected-state language as the surface they sit on.
 *
 * Nested translucency, specular edges and stacked shadows stay banned: they caused GPU compositing
 * bands on real devices, and one shadow plus one outline is enough to read depth.
 */
internal object PrismSurface {
    // MARBLE_IOS_SIMPLIFY_V81 — the whole control ramp is one step smaller and one step
    // rounder-but-flatter: iOS-like compact controls, no stacked shadows.
    val CardRadius = 20.dp
    val TileRadius = 16.dp
    val InsetRadius = 12.dp
    // Controls intentionally use a tighter radius than cards. The resulting soft squircle reads as
    // an action rather than another nested panel, while keeping Marble's rounded visual language.
    val ControlRadius = 13.dp
    val ControlHeight = 44.dp
    val CompactControlHeight = 34.dp
    val IconControlSize = 38.dp
    val Hairline = 1.dp
    val StrongHairline = 1.4.dp
    // iOS-like depth is present but deliberately quiet: one shallow shadow per elevated
    // surface, with the AMOLED palette doing most of the separation work.
    val RestingElevation = 2.dp
    val RaisedElevation = 5.dp
    val ControlElevation = 3.dp
    val PressedElevation = 1.dp
}

/**
 * Readable ink for a filled, coloured surface.
 *
 * Marble ships light, dark and Material You dynamic palettes, so "white text" is not a constant:
 * the accent itself decides. A bright fill gets deep ink, a saturated fill gets near-white.
 */
internal fun prismOnColor(base: Color): Color {
    val luminance = .2126f * base.red + .7152f * base.green + .0722f * base.blue
    // MARBLE_NAVY_BRAND_THEME_V77 — ink on a filled brand surface is deep navy or alice blue.
    return if (luminance > .60f) Color(0xFF001144) else Color(0xFFF0F8FF)
}

/** Shared elevation + hairline + fill stack, for containers that cannot be a [PrismPanel]. */
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
    return this
        .shadow(
            elevation=elevation,
            shape=shape,
            clip=false,
            ambientColor=tone.copy(alpha=.28f),
            spotColor=tone.copy(alpha=.36f)
        )
        .border(PrismSurface.Hairline, hairline, shape)
        .clip(shape)
        .background(fill)
        // A state tint is painted above the opaque surface and below the content, so a live row can
        // be flooded with its state colour without resizing or repainting anything inside it.
        .then(if (tint == null) Modifier else Modifier.background(tint))
}

/**
 * The recessed counterpart of [prismElevated].
 *
 * Anything that lives *inside* a card — a row, a strip, a segment, a metric — uses this instead of
 * a drop shadow. Two nested elevated surfaces always read as a rendering bug, and it is the single
 * biggest reason the app previously felt inconsistent: the cards floated, everything else was a
 * bare rectangle with a hairline on it.
 */
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
    // MARBLE_HALO_INNER_BORDER_FIX_V67
    // GlassBorderSoft at .9f alpha composited as a bright whitish ring on light themes.
    // A translucent tone border blends with the well fill and never reads as a halo.
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
    val glowAlpha=.014f + .022f*selectedProgress
    // MARBLE_BUTTON_TEXT_RECT_REMOVED_DS_V68
    // GlassBorderSoft in the panel rim composited as a pale rectangular band on light themes —
    // especially around Settings section cards and the Library filter sheet panels that host the
    // choice chips. Keep the rim as pure accent/violet so no foreign rectangle sits behind labels.
    val borderBrush=Brush.linearGradient(
        listOf(
            accent.copy(alpha=.18f + .34f*selectedProgress),
            violet.copy(alpha=.10f + .18f*selectedProgress),
            accent.copy(alpha=.08f + .12f*selectedProgress)
        )
    )

    Box(
        modifier=modifier
            .shadow(
                elevation=elevation,
                shape=shape,
                clip=false,
                ambientColor=accent.copy(alpha=.16f),
                spotColor=accent.copy(alpha=.22f)
            )
            .border(PrismSurface.Hairline,borderBrush,shape)
            .clip(shape)
            .background(surface)
            .then(
                if (onClick == null) Modifier
                else Modifier.kineticClickable(enabled=enabled, role=Role.Button, boundedShape=shape, onClick=onClick)
            )
            // State tint sits above the opaque surface and below the content: a connected row can be
            // flooded with its state color without repainting or resizing anything inside it.
            .then(if (tint == null) Modifier else Modifier.background(tint))
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

/**
 * A recessed box: the inside of a card.
 *
 * Wells never cast a shadow. Depth comes from a sunken fill plus one hairline, so an endpoint strip,
 * a metric or a settings row reads as *content of* the panel holding it instead of as a second card
 * stacked on top of the first. [selected] only warms the fill and the outline; geometry never moves.
 */
@Composable
internal fun PrismWell(
    modifier: Modifier = Modifier,
    tone: Color = Aether.Cyan,
    selected: Boolean = false,
    radius: Dp = PrismSurface.InsetRadius,
    contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
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
    val shape=RoundedCornerShape(if (compact) 11.dp else PrismSurface.ControlRadius)

    val accent=if (variant == PrismButtonVariant.Danger) Aether.Danger else tone
    val content=when {
        !enabled -> Aether.InkFaint
        filled -> prismOnColor(accent)
        variant == PrismButtonVariant.Quiet -> Aether.InkMuted
        else -> Aether.Ink
    }
    // MARBLE_IOS_BUTTON_FLAT_V81 — one flat fill, one optional hairline, zero shadows and
    // zero gradients. Importance is carried by variant only, the way iOS tinted/filled
    // buttons work, so dense Settings groups stay quiet and legible in both themes.
    val skin=when {
        !enabled -> SolidColor(Aether.GlassStrong.copy(alpha=.42f))
        filled -> SolidColor(accent)
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
        horizontal=if (compact) 12.dp else 15.dp,
        vertical=if (compact) 7.dp else 9.dp
    )

    CompositionLocalProvider(LocalContentColor provides content) {
        Row(
            modifier=modifier
                .heightIn(
                    min=when {
                        detail.isNotBlank() && !compact -> 52.dp
                        compact -> PrismSurface.CompactControlHeight
                        else -> PrismSurface.ControlHeight
                    }
                )
                .widthIn(min=if (compact) 64.dp else 88.dp)
                .clip(shape)
                .background(skin)
                .border(PrismSurface.Hairline, hairline, shape)
                .kineticClickable(
                    enabled=enabled,
                    role=Role.Button,
                    boundedShape=shape,
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

/** Pressable circular control: overflow menus, sheet closers, stepper buttons. */
@Composable
internal fun PrismIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: Color = Aether.Cyan,
    selected: Boolean = false,
    enabled: Boolean = true,
    size: Dp = PrismSurface.IconControlSize,
    descriptiveLabel: String = "",
    content: @Composable () -> Unit
) {
    val shape=RoundedCornerShape(size * .36f)
    val fill by animateColorAsState(
        targetValue=if (selected) tone.copy(alpha=.18f) else Aether.GlassStrong.copy(alpha=.34f),
        animationSpec=MarbleMotionSpecs.Color,
        label="icon-control-fill"
    )
    // MARBLE_HALO_INNER_BORDER_FIX_V67
    // Unselected hairline was Aether.GlassBorder — a light blue-gray that composited as a
    // white ring inside the circle on light themes.  Switching to a translucent accent
    // eliminates the halo in both light and dark palettes.
    val hairline by animateColorAsState(
        targetValue=if (selected) tone.copy(alpha=.50f) else tone.copy(alpha=.18f),
        animationSpec=MarbleMotionSpecs.Color,
        label="icon-control-hairline"
    )

    Box(
        modifier=modifier
            .size(size)
            .clip(shape)
            .background(fill)
            .border(PrismSurface.Hairline,hairline,shape)
            .then(
                if (descriptiveLabel.isBlank()) Modifier
                else Modifier.semantics { contentDescription=descriptiveLabel }
            )
            .kineticClickable(
                enabled=enabled,
                role=Role.Button,
                boundedShape=shape,
                onClick=onClick
            ),
        contentAlignment=Alignment.Center
    ) {
        content()
    }
}

/** Four-segment signal meter used by the Library ping readout and metric wells. */
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

/** A selected tick, drawn instead of imported so it survives every theme and font scale. */
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

/**
 * The selectable tile shared by every choice in the product: library mode, sort, resolver presets,
 * sources and settings segments. A selected tile gains a soft tone wash and tinted ink; it never
 * resizes and never casts a shadow (MARBLE_IOS_SIMPLIFY_V81).
 */
@Composable
internal fun PrismSelectionTile(
    label: String,
    selected: Boolean,
    tone: Color,
    modifier: Modifier = Modifier,
    detail: String = "",
    minHeight: Dp = 40.dp,
    alignment: Alignment = Alignment.Center,
    leading: (@Composable (() -> Unit))? = null,
    trailing: (@Composable (() -> Unit))? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val shape=RoundedCornerShape(13.dp)
    // MARBLE_BUTTON_TEXT_RECT_REMOVED_DS_V68
    // One continuous fill owns the whole tile. No second rectangle (hairline of a foreign
    // neutral, M3 indicator, nested surface) is allowed behind or around the label — selection
    // is ink colour + a soft tone wash only.
    val fill by animateColorAsState(
        targetValue=if (selected) tone.copy(alpha=.13f) else Aether.GlassStrong.copy(alpha=.32f),
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
                if (selected) tone.copy(alpha=.42f) else tone.copy(alpha=.10f),
                shape
            )
            // MARBLE_SELECTION_TILE_INDICATION_REMOVED_DS_V69
            // The tile already signals selection through fill and ink. Material3's ripple
            // state layer composited as a semi-transparent off-white rectangle behind the
            // detail text. Suppressing the indication leaves press scale intact.
            .kineticClickable(enabled=enabled, role=Role.Button, boundedShape=shape, showIndication=false, onClick=onClick)
            .padding(horizontal=12.dp,vertical=8.dp),
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

/**
 * Painted emphasis for the row that currently carries traffic.
 *
 * A Library row is a list item: giving one row a real border, radius or padding change resizes it and
 * shoves every row below it. This frame never participates in measurement — it paints on top of the
 * finished card, so a connected node keeps the exact box geometry of a disconnected one while the ring,
 * the inner bloom and the left energy rail announce the live route. Ambient motion is read once here
 * instead of per row, and callers compose this only while a route is live.
 */
@Composable
internal fun PrismRouteFrame(
    tone: Color,
    modifier: Modifier = Modifier,
    radius: Dp = 22.dp
) {
    val motion=MarbleMotion.current
    val flow=motion.loop(3_400)
    val breathe=.72f + .28f*motion.breathe(2_600)
    val accent=Aether.Cyan

    Canvas(modifier) {
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

        // Soft light spilling inward from the ring. The bloom only occupies the gutter between the edge
        // and the card padding, so text keeps its own contrast.
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
            .padding(horizontal=10.dp,vertical=6.dp),
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
    sparkline: List<Int> = emptyList()
) {
    val shape=RoundedCornerShape(20.dp)
    val border=Brush.linearGradient(
        listOf(
            tone.copy(alpha=.30f),
            tone.copy(alpha=.12f)
        )
    )
    Box(
        modifier=modifier
            .border(1.dp,border,shape)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        tone.copy(alpha=.065f),
                        Aether.VoidElevated
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
                Text(
                    trx(title).uppercase(),
                    color=Aether.InkMuted,
                    style=MaterialTheme.typography.labelSmall,
                    fontWeight=FontWeight.Bold
                )
            }
            Row(verticalAlignment=Alignment.Bottom) {
                Text(
                    value,
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
    modifier: Modifier = Modifier
) {
    val clean=samples.filter { it > 0 }.takeLast(36)
    val grid=Aether.GlassBorder
    val surface=Aether.VoidElevated
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
    }
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
    val phase=MarbleMotion.current.loop(if(connecting) 950 else 1_650)
    val breathe=MarbleMotion.current.breathe(2_400)
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
                connecting -> 110f
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
    // A controlled well instead of a bare M3 TextField. The standard field shipped its own 56dp
    // box, a hidden underline indicator and internal padding geometry, so on the Library it sat
    // a few points taller than the button beside it with no visible frame at all and the row
    // read as broken. This field owns its geometry: one exact control height, one recessed
    // fill, one hairline that answers the cursor.
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
    // MARBLE_PHONE_DYNAMIC_THEME_V113 — when true the thumbnail renders the phone's actual
    // Material You palette (surfaces + primary/secondary/tertiary dots) instead of the brand ramp.
    dynamicPreview: Boolean = false,
    onClick: () -> Unit
) {
    val shape=RoundedCornerShape(16.dp)
    // MARBLE_NAVY_BRAND_THEME_V77 — previews paint the real Marble surfaces so the
    // selector previews the identity, not a generic gray wireframe.
    val context = LocalContext.current
    val generated =
        if (dynamicPreview && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dynamicLightColorScheme(context)
        } else {
            null
        }
    val previewBg=generated?.background
        ?: if(darkPreview) Color(0xFF000033) else Color(0xFFF0F8FF)
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
                    // The dynamic thumbnail shows the wallpaper palette itself: primary,
                    // secondary and tertiary dots taken from the live Material You scheme.
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

/**
 * iOS‑like design tokens mirroring the SF‑system aesthetic, drawer geometry,
 * background gradients, dot noise and motion curves requested for the MarbleNG
 * redesign. All values are expressed in dp and can be used across composables.
 */

internal object MarbleIOSDesign {
    // Drawer width is the golden‑ratio proportion of the screen width (61.8%.
    val DrawerWidth: Dp = 61.8.dp
    // Corner radius for the drawer and any iOS‑style rounded container.
    val CornerRadius: Dp = 14.dp
    // The system font family name used everywhere; Compose resolves it to the
    // device’s San Francisco (iOS) or the closest Android equivalent.
    val FontFamily: String = "SFPro"
    // Thin weight constant for the typography scale.
    val ThinWeight = androidx.compose.ui.text.font.FontWeight.Thin
    // Vertical gradient background colours (top → bottom).
    val GradientTop = Color(0xFF000000)
    val GradientBottom = Aether.Ice
    // Motion curve: cubic‑ease‑out (start gentle, finish soft).
    val EaseOutCubic: (Float) -> Float = { t -> 1f - math.pow(1f - t, 3f) }
}
