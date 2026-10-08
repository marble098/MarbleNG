package com.marbleng.app.ui

// MARBLE_SERVER_TILE_LAYOUT_V208 — the compact server silhouette, and the one rule that decides
// when it is used.
// MARBLE_SERVER_TILE_TRUTH_V210 — what a tile is allowed to claim: a box that answered stands
// out, a box that did not answer recedes, and a name never grows the box it lives in.
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
// `val fill by animateColorAsState(...)` needs the delegate operator in scope.
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

/**
 * MARBLE_SERVER_TILE_TRUTH_V210 — what a tile may claim about the server inside it.
 *
 * A grid of forty boxes is read as a picture, not as a list of sentences, so the only thing that
 * may separate one box from its neighbours is a fact: this one answered, that one did not. The
 * old tile drew both in the same fill with the same ink, so the one thing a user is actually
 * looking for — which of these servers is dead — was the only thing the grid did not show.
 *
 * The three answers are deliberately not three shades of one another:
 *
 *  - [Reach.ANSWERED]  a measurement exists and it cleared the product's honest latency floor;
 *  - [Reach.SILENT]    a measurement exists and it did not — the box recedes, because a box that
 *                      shouts "no route" forty times is a wall of noise, and a box that *fades*
 *                      is a picture with one dead pixel in it;
 *  - [Reach.UNMEASURED] nobody has asked yet. Absence of a measurement is not evidence, so this
 *                      box looks exactly like a working one: dimming it would punish the user
 *                      for not having run a sweep.
 *
 * Everything here is pure, so the rule is unit-testable without a device.
 */
object ServerTileTruth {
    /** What the last measurement of one server says. */
    enum class Reach { ANSWERED, SILENT, UNMEASURED }

    /**
     * The floor a sample has to clear before the product calls it a measurement, in ms.
     *
     * [ServerTile] used this number inline; it is named here because the same floor decides both
     * whether a latency is printed and whether a box fades, and two copies of a threshold drift.
     */
    const val LatencyFloorMs: Int = 20

    /** How much of a silent tile remains painted: enough to read, not enough to compete. */
    const val SilentAlpha: Float = .42f

    /** A tile nobody has measured, and a tile that answered, keep their full weight. */
    const val LiveAlpha: Float = 1f

    /** The verdict one tile must draw, from the measurement the page already holds. */
    fun reachOf(result: BenchmarkResult?, testing: Boolean): Reach {
        // A box currently being measured is not silent — nothing has failed yet, so it must not
        // begin fading halfway through the sweep that is about to clear it.
        if (testing) return Reach.UNMEASURED
        if (result == null) return Reach.UNMEASURED
        val answered = result.success > 0 && result.latencyMs >= LatencyFloorMs
        return if (answered) Reach.ANSWERED else Reach.SILENT
    }

    /** The painted weight of one verdict. */
    fun alphaOf(reach: Reach): Float = when (reach) {
        Reach.SILENT -> SilentAlpha
        Reach.ANSWERED, Reach.UNMEASURED -> LiveAlpha
    }

    /** The spoken state of one verdict, for the tile's accessibility label. */
    fun stateWord(reach: Reach): String = when (reach) {
        Reach.SILENT -> "No response"
        Reach.ANSWERED -> ""
        Reach.UNMEASURED -> ""
    }
}

/**
 * MARBLE_SERVER_TILE_TRUTH_V210 — the name slot of a tile.
 *
 * A server name is the one field in a tile whose length the product does not control: a
 * subscription ships `🇩🇪 DE-07 · Frankfurt Premium Plus [Premium]`, and a two-line slot turned
 * that into a tile one line taller than its neighbour. On a grid that is the whole defect — the
 * row grows, every other box in the row stays the same size, and the grid stops being a grid.
 *
 * So the slot is one line, always, at a fixed height, and a name that does not fit travels
 * through that slot instead of expanding it. [ServerTile] attaches the marquee only when motion
 * is enabled: with the system's "remove animations" switch on, an endlessly sliding label is the
 * one kind of motion a user with a vestibular trigger cannot switch off, and the honest fall-back
 * is the ellipsis the row already used.
 */
object ServerTileNamePolicy {
    /** The name occupies exactly this many lines, whatever it says. */
    const val MaxLines: Int = 1

    /** How fast an overflowing name travels, in dp per second. */
    const val VelocityDp: Int = 26

    /** The gap before a travelling name repeats, as a fraction of the slot's own width. */
    const val SpacingFraction: Float = .30f

    /** A name that fits never moves; only an overflowing one is animated. */
    const val InfiniteIterations: Int = Int.MAX_VALUE
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
 *
 * MARBLE_SERVER_TILE_TRUTH_V210 — two things a tile now answers that it used to leave to the
 * reader:
 *
 *  1. **A server that did not answer recedes.** [ServerTileTruth.reachOf] decides it from the
 *     measurement the page already holds, and the whole box — fill, rim, name, address and
 *     latency — fades to [ServerTileTruth.SilentAlpha], so a dead node is the quietest thing in
 *     the grid instead of an equal peer of the live ones around it. A server nobody has measured
 *     is *not* faded: no measurement is not a verdict.
 *  2. **A name never grows its box.** The name slot is one line at a fixed height and an
 *     overflowing name travels through it ([ServerTileNamePolicy]), so a 60-character
 *     subscription label cannot make one tile taller than the three beside it.
 */
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
    // MARBLE_SERVER_TILE_PARITY_V212 — the two things the compact box was missing against the row
    // it is supposed to be an alternative to:
    //
    //  1. [familyChip] is the measured address-family verdict (`v4`, `v6`, `v4+v6`). On the row it
    //     is the answer to "can this node do IPv6?", which is the question the whole IPv6 policy
    //     depends on; a box that hides it makes the grid a worse tool than the list, and the only
    //     way to see it was to switch the layout back.
    //  2. [trailing] is the row's own menu, composed by the caller. It is a slot rather than a
    //     parameter list so the tile stays a presentation: it does not learn about renaming,
    //     moving, QR or deleting — it only reserves the place where the page's menu goes. A box
    //     with no menu was a box that could be selected and nothing else.
    familyChip: String? = null,
    familyTone: Color? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    // A smaller radius than the subscription container gives the server card its own clear level.
    val shape = RoundedCornerShape(13.dp)
    val measured = result?.takeIf { it.success > 0 && it.latencyMs >= ServerTileTruth.LatencyFloorMs }
    val latency = measured?.latencyMs?.toInt() ?: 0
    val attempted = result != null && measured == null
    val name = displayServerName(profile.name, profile.host, profile.scheme)
    val endpoint = ServersQuery.address(profile)
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
    // MARBLE_SERVER_TILE_TRUTH_V210 — the whole box carries the verdict, so it is applied once,
    // after the fill and the rim are painted: a silent server is a quieter box, not a box with a
    // quieter label. It animates on the product's own float spec so a sweep does not blink the
    // grid — it settles into its new truth.
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
    // MARBLE_SERVER_TILE_TRUTH_V210 — a long name travels inside its own slot instead of
    // expanding it. `basicMarquee` measures the text against the slot itself and only animates
    // when it does not fit, so a short name is an ordinary static label — there is no "is this
    // one long enough?" branch to get wrong.
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
            // Every tile in a line takes the line's height, so a box can never be shorter than
            // the one beside it (MARBLE_SERVER_TILE_TRUTH_V210).
            .fillMaxHeight()
            .heightIn(min = 84.dp)
            // MARBLE_SURFACE_DEPTH_V212 — the boxes were still on the pre-V191 plane: a flat fill
            // and a 1 dp hairline, while the Home cards around them sit on a light. Same fill,
            // same rim, same states — now on the one depth contract every other surface wears, so
            // the grid stops reading as a cheaper material than the page it is on.
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
            // MARBLE_SERVER_TILE_PARITY_V212 — the page's own menu, at the trailing edge. The slot
            // is only occupied when the page has actions to offer, so the Home route picker (whose
            // rows carry no menu either) keeps its quiet boxes.
            if (trailing != null) {
                Spacer(Modifier.weight(1f))
                trailing()
            }
        }
        // MARBLE_SERVER_TILE_TRUTH_V210 — one line, always. The name is the only field whose
        // length the product does not control, and a second line used to make this tile taller
        // than every other box in its row.
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
                color = Aether.InkFaint,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            // MARBLE_SERVER_TILE_PARITY_V212 — the measured address family, on the box that owns
            // it. Same rule as the row: only a real measurement earns a chip, because inventing
            // "v4" from a hostname is the guess the scan replaced.
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

/**
 * One line of tiles: each takes an equal share, and a short last line keeps the same widths as a
 * full one, so tiles never stretch to fill a gap and change size between rows.
 *
 * MARBLE_SERVER_TILE_TRUTH_V210 — the line is measured at its own intrinsic height and every
 * tile is stretched to fill it. Together with the tile's one-line name slot that is what makes
 * the grid a grid: no box can be taller or shorter than the box beside it, whatever its name,
 * its address or the width of its latency reading.
 */
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
        // Pad a short line up to a full one: the empty shares hold the width the tiles above and
        // below already have, which is the whole reason a grid reads as a grid.
        repeat((columns - profiles.size).coerceAtLeast(0)) {
            Box(modifier = Modifier.weight(1f))
        }
    }
}
