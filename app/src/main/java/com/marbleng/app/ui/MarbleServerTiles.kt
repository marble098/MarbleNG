package com.marbleng.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marbleng.app.core.ServersQuery
import com.marbleng.app.model.BenchmarkResult
import com.marbleng.app.model.ProxyProfile

object ServerTilePolicy {
    const val MinTileWidthDp: Int = 148

    const val TileGapDp: Int = 8

    const val MaxColumns: Int = 4

    fun columnsFor(availableWidthDp: Int): Int {
        if (availableWidthDp <= 0) return 1

        val byWidth = (availableWidthDp + TileGapDp) / (MinTileWidthDp + TileGapDp)
        return byWidth.coerceIn(1, MaxColumns)
    }

    fun <T> chunkRows(profiles: List<T>, columns: Int): List<List<T>> =
        profiles.chunked(columns.coerceAtLeast(1))
}

object ServerTileTruth {
    enum class Reach { ANSWERED, SILENT, UNMEASURED }

    const val LatencyFloorMs: Int = 20

    const val SilentAlpha: Float = .42f

    const val LiveAlpha: Float = 1f

    fun reachOf(result: BenchmarkResult?, testing: Boolean): Reach {
        if (testing) return Reach.UNMEASURED
        if (result == null) return Reach.UNMEASURED
        val answered = result.success > 0 && result.latencyMs >= LatencyFloorMs
        return if (answered) Reach.ANSWERED else Reach.SILENT
    }

    fun alphaOf(reach: Reach): Float = when (reach) {
        Reach.SILENT -> SilentAlpha
        Reach.ANSWERED, Reach.UNMEASURED -> LiveAlpha
    }

    fun stateWord(reach: Reach): String = when (reach) {
        Reach.SILENT -> "No response"
        Reach.ANSWERED -> ""
        Reach.UNMEASURED -> ""
    }
}

object ServerTileNamePolicy {
    const val MaxLines: Int = 1

    const val VelocityDp: Int = 26

    const val SpacingFraction: Float = .30f

    const val InfiniteIterations: Int = Int.MAX_VALUE
}

@Composable
internal fun rememberServerTileColumns(contentWidthDp: Int? = null): Int {
    val configuration = LocalConfiguration.current
    val available = contentWidthDp ?: configuration.screenWidthDp

    return ServerTilePolicy.columnsFor(available - 32)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ServerTile(
    profile: ProxyProfile,
    result: BenchmarkResult?,
    selected: Boolean,
    active: Boolean,
    testing: Boolean,
    locationCode: String,
    locationProvisional: Boolean,
    modifier: Modifier = Modifier,

    familyChip: String? = null,
    familyTone: Color? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    val shape = marbleNativeShape(MarbleNativeSpec.TileCornerDp.dp)
    val measured = result?.takeIf { it.success > 0 && it.latencyMs >= ServerTileTruth.LatencyFloorMs }
    val latency = measured?.latencyMs?.toInt() ?: 0
    val attempted = result != null && measured == null
    val name = displayServerName(profile.name, profile.host, profile.scheme)
    val endpoint = ServersQuery.address(profile)
    val flag = leadingFlagGlyph(profile.name)

    val nativeFill = marbleNativeCardFill()
    val fill by animateColorAsState(
        targetValue = when {
            active -> Aether.Emerald
                .copy(alpha = if (homeCloudDark()) .14f else .10f)
                .compositeOver(nativeFill)
            selected -> marbleNativeSelectedFill(Aether.Cyan)
            else -> nativeFill
        },
        animationSpec = MarbleMotionSpecs.Color,
        label = "server-tile-fill"
    )
    val rim by animateColorAsState(
        targetValue = when {
            active -> Aether.Emerald.copy(alpha = .45f)
            selected -> Aether.Cyan.copy(alpha = .45f)
            else -> marbleNativeOutline()
        },
        animationSpec = MarbleMotionSpecs.Color,
        label = "server-tile-rim"
    )
    val stateTone: Color? = when {
        active -> Aether.Emerald
        selected -> Aether.Cyan
        else -> null
    }

    val reach = ServerTileTruth.reachOf(result, testing)
    val tileAlpha by animateFloatAsState(
        targetValue = ServerTileTruth.alphaOf(reach),
        animationSpec = MarbleMotionSpecs.DockFloat,
        label = "server-tile-alpha"
    )
    val spokenReach = trx(ServerTileTruth.stateWord(reach))
    val stateWord = when {
        active -> trx("Connected")
        selected -> trx("Selected")
        spokenReach.isNotBlank() -> spokenReach
        else -> ""
    }

    val motion = MarbleMotion.current
    val nameModifier = if (motion.motionEnabled) {
        Modifier.basicMarquee(
            iterations = ServerTileNamePolicy.InfiniteIterations,
            velocity = ServerTileNamePolicy.VelocityDp.dp,
            spacing = MarqueeSpacing.fractionOfContainer(ServerTileNamePolicy.SpacingFraction)
        )
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .heightIn(min = MarbleNativeSpec.RowTwoLineHeightDp.dp + 16.dp)
            .marbleSurfaceDepth(shape = shape, fill = fill, rim = rim, lifted = active || selected)
            .kineticClickable(role = Role.Button, boundedShape = shape, onClick = onClick)
            .semantics {
                contentDescription = listOfNotNull(
                    stripLeadingFlag(profile.name),
                    profile.scheme.uppercase().takeIf { it.isNotBlank() }
                ).joinToString(", ")
                stateDescription = stateWord
            }
            .alpha(tileAlpha)
            .padding(horizontal = 9.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ProtocolTile(
                scheme = profile.scheme,
                size = 26.dp,
                flag = flag,
                flagCode = locationCode,
                stateTone = stateTone,
                locationTrust = marbleLocationTrustOf(
                    hasSessionReport = false,
                    hasMeasuredCode = locationCode.isNotBlank(),
                    measuredIsProvisional = locationProvisional,
                    hasLabelGlyph = flag != null
                )
            )
            ProtocolBadge(
                scheme = profile.scheme,
                modifier = Modifier.weight(1f, fill = false)
            )

            if (trailing != null) {
                Spacer(Modifier.weight(1f))
                trailing()
            }
        }

        Text(
            text = name,
            color = Aether.Ink,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = ServerTileNamePolicy.MaxLines,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .then(nameModifier)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = endpoint.ifBlank { trx("Endpoint unavailable") },
                color = marbleNativeInkVariant(),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (familyChip != null && familyTone != null) {
                ServerStateChip(trx(familyChip), familyTone)
            }
            ServerPingStat(
                latencyMs = latency,
                measured = measured != null,
                testing = testing,
                attempted = attempted,
                compact = true,
                quietFailure = true
            )
            if (active || selected) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(stateTone ?: Aether.Cyan)
                )
            }
        }
    }
}

@Composable
internal fun ServerTileRow(
    profiles: List<ProxyProfile>,
    columns: Int,
    modifier: Modifier = Modifier,
    content: @Composable (ProxyProfile) -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(ServerTilePolicy.TileGapDp.dp)
    ) {
        profiles.forEach { profile ->
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) { content(profile) }
        }

        repeat((columns - profiles.size).coerceAtLeast(0)) {
            Box(modifier = Modifier.weight(1f))
        }
    }
}
