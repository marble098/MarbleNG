package com.marbleng.app.ui

// MARBLE_SERVER_TILE_LAYOUT_V208 — the compact server silhouette, and the one rule that decides
// when it is used.
//
// The finding: a server has exactly three facts a user ever scans — where it is, what it is
// called, and how fast it answered. The product printed those three facts on a full-width row,
// so on a 200-node subscription the page was a wall of text with an empty middle on every line,
// and the useful information was diluted by the space around it. There was no way to see more
// than five or six servers at once, and no way to ask for more.
//
// The answer is a second silhouette, not a second feature:
//
//   * [ServerTile] is one server as a small card — flag, protocol, name, latency — at roughly a
//     third of the row's height, with the same colours, the same flags and the same measurement
//     the row already used. Nothing about *what* is shown changes; only how much room it takes.
//   * [ServerTilePolicy] decides how many fit on a line, from the width it is actually given, so
//     a phone gets two, a foldable three, a tablet four, and a tile is never squeezed below the
//     width at which its own contents stop being readable.
//   * One preference ([com.marbleng.app.model.ServerLayout]) answers for both the Servers page and
//     the Home server box, because two lists that disagree about how a server looks are two
//     products.
//
// The tile is a *presentation*: it takes the same tap semantics (select, and connect only when a
// tunnel is already up), the same selected/active predicate and the same benchmark result as the
// row, so switching the layout cannot change what a server does.

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
// `val fill by animateColorAsState(...)` needs the delegate operator in scope.
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.marbleng.app.model.BenchmarkResult
import com.marbleng.app.model.ProxyProfile

/**
 * How the tile grid is laid out — pure, so the numbers are testable without a device.
 */
object ServerTilePolicy {
    /**
     * The narrowest a tile may be, in dp.
     *
     * Below this the name ellipsises to almost nothing and the latency stops fitting next to the
     * quality bars, so a tile narrower than this is a worse read than the row it replaced. It is
     * the reason a phone gets two columns and not four.
     */
    const val MinTileWidthDp: Int = 148

    /** The gap between tiles, in dp — the same rhythm the rows already use. */
    const val TileGapDp: Int = 8

    /** Never more than this, however wide the window: a tile the width of a thumb is unreadable. */
    const val MaxColumns: Int = 4

    /** How many tiles fit on one line of [availableWidthDp]. */
    fun columnsFor(availableWidthDp: Int): Int {
        if (availableWidthDp <= 0) return 1
        // n tiles + (n - 1) gaps must fit: n * min + (n - 1) * gap <= width.
        val byWidth = (availableWidthDp + TileGapDp) / (MinTileWidthDp + TileGapDp)
        return byWidth.coerceIn(1, MaxColumns)
    }

    /** [profiles] split into rows of [columns], which is what a LazyColumn can key and recycle. */
    fun <T> chunkRows(profiles: List<T>, columns: Int): List<List<T>> =
        profiles.chunked(columns.coerceAtLeast(1))
}

/** The number of tile columns the current window fits, resolved once per recomposition. */
@Composable
internal fun rememberServerTileColumns(contentWidthDp: Int? = null): Int {
    val configuration = LocalConfiguration.current
    val available = contentWidthDp ?: configuration.screenWidthDp
    // The page body is capped at 820 dp and carries 16 dp of side padding on both edges, so the
    // tiles get less than the raw screen width on a wide window; subtracting the padding here
    // keeps the last column from being measured into existence and then clipped.
    return ServerTilePolicy.columnsFor(available - 32)
}

/**
 * One server as a small card.
 *
 * The anatomy is deliberately the row's anatomy, folded: the flag tile leads, the protocol keeps
 * its own hue as a text badge, the name takes the width it has, and the measured latency closes
 * the tile with the same quality bars the row shows. Selected and live are the same two states,
 * in the same two colours, with the same rim — so a user who knows the list knows the grid.
 */
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
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val measured = result?.takeIf { it.success > 0 && it.latencyMs >= 20 }
    val latency = measured?.latencyMs?.toInt() ?: 0
    val attempted = result != null && measured == null
    val name = displayServerName(profile.name, profile.host, profile.scheme)
    val flag = leadingFlagGlyph(profile.name)

    // Both endpoints of the tween are opaque: animating toward a translucent fill passes through
    // frames the page's aurora shines through, which is the same defect V205 removed from the rows.
    val fill by animateColorAsState(
        targetValue = when {
            active -> Aether.Emerald
                .copy(alpha = if (homeCloudDark()) .12f else .09f)
                .compositeOver(homeCloudInsetFill())
            selected -> homeCloudSelectedFill()
            else -> homeCloudInsetFill()
        },
        animationSpec = MarbleMotionSpecs.Color,
        label = "server-tile-fill"
    )
    val rim by animateColorAsState(
        targetValue = when {
            active -> Aether.Emerald.copy(alpha = .42f)
            selected -> homeCloudSelectedBorder()
            else -> homeCloudInsetBorder()
        },
        animationSpec = MarbleMotionSpecs.Color,
        label = "server-tile-rim"
    )
    val stateTone: Color? = when {
        active -> Aether.Emerald
        selected -> Aether.Cyan
        else -> null
    }
    val stateWord = when {
        active -> trx("Connected")
        selected -> trx("Selected")
        else -> ""
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 84.dp)
            .clip(shape)
            .background(fill)
            .border(1.dp, rim, shape)
            .kineticClickable(role = Role.Button, boundedShape = shape, onClick = onClick)
            .semantics {
                contentDescription = listOfNotNull(
                    stripLeadingFlag(profile.name),
                    profile.scheme.uppercase().takeIf { it.isNotBlank() }
                ).joinToString(", ")
                stateDescription = stateWord
            }
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
        }
        Text(
            text = name,
            color = Aether.Ink,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            ServerPingStat(
                latencyMs = latency,
                measured = measured != null,
                testing = testing,
                attempted = attempted,
                compact = true,
                quietFailure = true,
                modifier = Modifier.weight(1f)
            )
            if (active || selected) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(stateTone ?: Aether.Cyan)
                )
            }
        }
    }
}

/**
 * One line of tiles: each takes an equal share, and a short last line keeps the same widths as a
 * full one, so tiles never stretch to fill a gap and change size between rows.
 */
@Composable
internal fun ServerTileRow(
    profiles: List<ProxyProfile>,
    columns: Int,
    modifier: Modifier = Modifier,
    content: @Composable (ProxyProfile) -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ServerTilePolicy.TileGapDp.dp)
    ) {
        profiles.forEach { profile ->
            Box(modifier = Modifier.weight(1f)) { content(profile) }
        }
        // Pad a short line up to a full one: the empty shares hold the width the tiles above and
        // below already have, which is the whole reason a grid reads as a grid.
        repeat((columns - profiles.size).coerceAtLeast(0)) {
            Box(modifier = Modifier.weight(1f))
        }
    }
}
