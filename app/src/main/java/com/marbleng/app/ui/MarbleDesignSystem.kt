package com.marbleng.app.ui

// MARBLE_M3_EXPRESSIVE_DESIGN_SYSTEM_V53

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal object MarbleSpacing {
    val Micro = 4.dp
    val S = 8.dp
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

@Composable
internal fun marbleMetricTone(band: MarbleMetricBand): Color = when (band) {
    MarbleMetricBand.GOOD -> Aether.Emerald
    MarbleMetricBand.WARNING -> Aether.Amber
    MarbleMetricBand.POOR -> Aether.Danger
    MarbleMetricBand.UNKNOWN -> Aether.InkMuted
}

@Composable
internal fun MarbleElevatedSurface(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(MarbleSpacing.M),
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation()
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
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
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = tone.copy(alpha = .085f)
        ),
        elevation = CardDefaults.cardElevation()
    ) {
        Column(modifier = Modifier.padding(MarbleSpacing.M)) {
            Text(
                title,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                buildString {
                    append(value)
                    if (unit.isNotBlank()) {
                        append(' ')
                        append(unit)
                    }
                },
                color = tone,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            )
            if (sparkline.size >= 2) {
                MarbleSparkline(
                    samples = sparkline,
                    tone = tone,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .padding(top = MarbleSpacing.S)
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
    Canvas(modifier) {
        if (clean.size < 2) return@Canvas
        val min=clean.minOrNull()?.toFloat() ?: return@Canvas
        val max=clean.maxOrNull()?.toFloat() ?: return@Canvas
        val range=(max-min).coerceAtLeast(1f)
        val dx=size.width/(clean.size-1).coerceAtLeast(1)
        val path=Path()
        clean.forEachIndexed { index, value ->
            val x=dx*index
            val y=size.height-((value-min)/range)*size.height
            if (index == 0) path.moveTo(x,y) else path.lineTo(x,y)
        }
        drawPath(
            path=path,
            color=tone,
            style=Stroke(width=2.5.dp.toPx(), cap=StrokeCap.Round)
        )
        val last=clean.last().toFloat()
        val lastY=size.height-((last-min)/range)*size.height
        drawCircle(
            color=tone,
            radius=3.dp.toPx(),
            center=Offset(size.width,lastY)
        )
    }
}
