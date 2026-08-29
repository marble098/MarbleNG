package com.marbleng.app.ui

// MARBLE_M3_EXPRESSIVE_DESIGN_SYSTEM_V53
// MARBLE_PRISM_DESIGN_SYSTEM_V54
// MARBLE_PRISM_POLISH_V55
// MARBLE_GLOBAL_CONTROL_POLISH_DS_V60
// MARBLE_FLUID_PRISM_STATE_V62
// MARBLE_ACTIVE_NODE_HALO_DS_V64
// MARBLE_ANCHORED_STATUS_TEXT_DS_V64

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
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

@Composable
internal fun PrismBackdrop(
    modifier: Modifier = Modifier
) {
    val base=Aether.Void
    val cyan=Aether.Cyan
    val violet=Aether.Amethyst
    val emerald=Aether.Emerald
    val dot=Aether.InkFaint

    Canvas(modifier) {
        drawRect(base)

        val cyanCenter=Offset(size.width*.84f,size.height*.05f)
        drawCircle(
            brush=Brush.radialGradient(
                colors=listOf(
                    cyan.copy(alpha=.095f),
                    cyan.copy(alpha=.035f),
                    Color.Transparent
                ),
                center=cyanCenter,
                radius=size.width*.72f
            ),
            radius=size.width*.72f,
            center=cyanCenter
        )

        val violetCenter=Offset(size.width*.06f,size.height*.45f)
        drawCircle(
            brush=Brush.radialGradient(
                colors=listOf(
                    violet.copy(alpha=.075f),
                    violet.copy(alpha=.025f),
                    Color.Transparent
                ),
                center=violetCenter,
                radius=size.width*.66f
            ),
            radius=size.width*.66f,
            center=violetCenter
        )

        val emeraldCenter=Offset(size.width*.84f,size.height*.88f)
        drawCircle(
            brush=Brush.radialGradient(
                colors=listOf(
                    emerald.copy(alpha=.055f),
                    Color.Transparent
                ),
                center=emeraldCenter,
                radius=size.width*.56f
            ),
            radius=size.width*.56f,
            center=emeraldCenter
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

@Composable
internal fun PrismPanel(
    modifier: Modifier = Modifier,
    accent: Color = Aether.Cyan,
    selected: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(MarbleSpacing.M),
    tint: Brush? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val selectedProgress by animateFloatAsState(
        targetValue=if(selected) 1f else 0f,
        animationSpec=MarbleMotionSpecs.ResponseFloat,
        label="prism-selected-energy"
    )
    val shape=RoundedCornerShape(22.dp)
    val surface=Aether.VoidElevated
    val violet=Aether.Amethyst
    val softBorder=Aether.GlassBorderSoft
    val glowAlpha=.022f + .033f*selectedProgress
    val borderBrush=Brush.linearGradient(
        listOf(
            accent.copy(alpha=.16f + .30f*selectedProgress),
            violet.copy(alpha=.08f + .15f*selectedProgress),
            softBorder.copy(alpha=.68f)
        )
    )

    Box(
        modifier=modifier
            .shadow(
                elevation=(1f + 4f*selectedProgress).dp,
                shape=shape,
                clip=false
            )
            .border(1.dp,borderBrush,shape)
            .clip(shape)
            .background(surface)
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
            verticalArrangement=Arrangement.spacedBy(MarbleSpacing.S),
            content=content
        )
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
            text,
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
            Aether.GlassBorderSoft.copy(alpha=.62f)
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
                    title.uppercase(),
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
    val grid=Aether.GlassBorderSoft
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
    val outline=Aether.GlassBorderSoft
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
    val shape=RoundedCornerShape(20.dp)
    TextField(
        value=value,
        onValueChange=onValueChange,
        modifier=modifier.heightIn(min=54.dp),
        placeholder={
            Text(
                placeholder,
                color=Aether.InkFaint,
                style=MaterialTheme.typography.bodyMedium
            )
        },
        leadingIcon={
            val iconColor=Aether.InkMuted
            Canvas(Modifier.size(20.dp)) {
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
        },
        singleLine=true,
        shape=shape,
        colors=TextFieldDefaults.colors(
            focusedTextColor=Aether.Ink,
            unfocusedTextColor=Aether.Ink,
            cursorColor=Aether.Cyan,
            focusedContainerColor=Aether.VoidElevated,
            unfocusedContainerColor=Aether.VoidElevated,
            disabledContainerColor=Aether.GlassStrong,
            focusedIndicatorColor=Color.Transparent,
            unfocusedIndicatorColor=Color.Transparent
        )
    )
}

@Composable
internal fun PrismThemeChoice(
    label: String,
    detail: String,
    selected: Boolean,
    darkPreview: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape=RoundedCornerShape(20.dp)
    val previewBg=if(darkPreview) Color(0xFF0B1320) else Color(0xFFF6F9FD)
    val previewSurface=if(darkPreview) Color(0xFF172337) else Color.White
    val previewText=if(darkPreview) Color(0xFFEAF1FB) else Color(0xFF152339)
    val selectionTone=Aether.Cyan
    val border=if(selected) selectionTone.copy(alpha=.52f) else Aether.GlassBorderSoft

    Column(
        modifier=modifier
            .heightIn(min=122.dp)
            .border(1.dp,border,shape)
            .clip(shape)
            .background(
                if(selected) selectionTone.copy(alpha=.065f)
                else Aether.VoidElevated
            )
            .kineticClickable(role=Role.Button,onClick=onClick)
            .padding(10.dp),
        verticalArrangement=Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier=Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(previewBg)
                .padding(8.dp)
        ) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .width(32.dp)
                    .height(7.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(previewText.copy(alpha=.78f))
            )
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(.62f)
                    .height(20.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(previewSurface)
                    .border(
                        1.dp,
                        accent.copy(alpha=.26f),
                        RoundedCornerShape(8.dp)
                    )
            )
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
        }
        Text(
            label,
            color=if(selected) selectionTone else Aether.Ink,
            style=MaterialTheme.typography.labelLarge,
            fontWeight=FontWeight.Bold
        )
        Text(
            detail,
            color=Aether.InkFaint,
            style=MaterialTheme.typography.labelSmall,
            maxLines=1
        )
    }
}
