@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.marbleng.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.marbleng.app.model.PlanStatus
import com.marbleng.app.model.Subscription
import com.marbleng.app.ui.theme.marbleDefaultSpatialSpec
import com.marbleng.app.ui.theme.marbleMotion

/**
 * MARBLE_SERVER_PLAN_CARDS_V260 — the plan plate inside a subscription card.
 *
 * The Servers card used to answer "how much of my quota is left?" with a four-word subtitle
 * (`12 servers · 14.2 MB / 50.0 GB`) and answered "when does it stop?" not at all. Both facts are on
 * the wire — `subscription-userinfo` carries `upload`, `download`, `total` and `expire` — and the
 * model already parsed them; nothing drew them.
 *
 * This is the plate: one bar, two labels, one chip.
 *
 * ```
 *  ┌──────────────────────────────────────────────────────┐
 *  │ [→](icon) Quota             ┌ 28 % ┐                 │  ← overline + live percentage chip
 *  │ ████████████░░░░░░░░░░░░░░  6.02 GB / 21.5 GB        │  ← bar + the pair it is drawn from
 *  │ ◷ Expires in 6 days · 1405-08-16                     │  ← the day, in the reader's calendar
 *  └──────────────────────────────────────────────────────┘
 * ```
 *
 * Three rules, and each of them is a test in `PlanStatusV260Test`:
 *
 *  1. **A bar needs a ceiling.** A plan with `total == 0` draws no bar at all — a `0 %` strip beside
 *     a subscription that simply has no cap is a false statement about the plan.
 *  2. **The chip and the bar share one tone.** [PlanStatus.toneOf] decides it once, so the colour of
 *     the fill always agrees with the number printed over it, and a plan that is expiring tomorrow is
 *     as loud as a plan that is full.
 *  3. **Every number is Persian-shaped through `faDigits`** and every word is an English key `trx`
 *     translates, so the plate has one voice in both languages.
 */
@Composable
internal fun MarblePlanPlate(
    status: PlanStatus.Status,
    persian: Boolean,
    modifier: Modifier = Modifier
) {
    if (status.isEmpty) return

    val colors = MaterialTheme.colorScheme
    val tones = marbleExpressiveTones()
    val accent = planToneColor(status.tone)
    val motion = marbleMotion()
    // Persian digits are Arabic-script glyphs: a monospace face has none of them, so the numeric
    // voice is only monospaced while the reading is Latin. The rule is the product's own — numbers
    // are tabular where they are Latin and typographic where they are Persian.
    val numericStyle = if (persian) {
        MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium)
    } else {
        MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
    }
    val fill by animateFloatAsState(
        targetValue = status.usageFraction,
        animationSpec = if (motion.still) snap() else marbleDefaultSpatialSpec(),
        label = "planUsageFill"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("server-plan-plate")
            .clip(RoundedCornerShape(18.dp))
            .background(tones.plum.container.copy(alpha = 0.55f))
            // A hairline of the tone at 12 %: without it a plate on a card whose own tone is one step
            // from the sheet's reads as a gap rather than as an object.
            .border(
                width = 1.dp,
                color = accent.copy(alpha = 0.12f),
                shape = RoundedCornerShape(18.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MarbleGlyphIcon(
                glyph = MarbleGlyph.GAUGE,
                color = accent,
                modifier = Modifier.size(13.dp),
                weight = 1.15f
            )
            Text(
                text = trx("Quota"),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            if (status.hasQuota) {
                PlanChip(
                    label = faDigits(
                        PlanStatus.usagePercentLabel(status.usageFraction, status.usedBytes)
                    ),
                    tint = accent,
                    tag = "server-plan-percent"
                )
            }
        }

        if (status.hasQuota) {
            PlanUsageBar(fraction = fill, tint = accent)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = faDigits(PlanStatus.quotaPairLabel(status.usedBytes, status.totalBytes)),
                    color = colors.onSurface,
                    style = numericStyle,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${faDigits(PlanStatus.formatBytes(status.remainingBytes))} ${trx("left")}",
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
            }
        }

        if (status.hasExpiry) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("server-plan-expiry"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MarbleGlyphIcon(
                    glyph = MarbleGlyph.BELL,
                    color = accent,
                    modifier = Modifier.size(13.dp),
                    weight = 1.15f
                )
                Text(
                    text = buildString {
                        append(trx(if (status.expired) "Expired" else "Expires"))
                        append(" · ")
                        append(faDigits(PlanStatus.expiryLabel(status.remainingDays)))
                    },
                    color = if (status.expired) accent else colors.onSurface,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (status.expired) FontWeight.Bold else FontWeight.Medium
                    ),
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = faDigits(PlanStatus.expiryDate(status.expireAtMs, persian = persian)),
                    color = colors.onSurfaceVariant,
                    style = numericStyle,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * The bar: a rail in the tone at 14 % and a fill in the same tone at full strength.
 *
 * Drawn as two boxes rather than a Canvas so the fill can be a fraction of the rail with the same
 * corner radius at both ends — the fill's own curve is what makes it read as a *measure* rather than
 * a progress bar with the standard square start.
 */
@Composable
private fun PlanUsageBar(fraction: Float, tint: Color) {
    val safe = fraction.coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(7.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.14f))
            .testTag("server-plan-bar")
    ) {
        if (safe > 0f) {
            Box(
                modifier = Modifier
                    // A fill narrower than its own corner radius looks like a dot, so the floor is
                    // one point of travel rather than one percent of the rail.
                    .fillMaxWidth(safe.coerceAtLeast(0.02f))
                    .height(7.dp)
                    .clip(CircleShape)
                    .background(tint)
            )
        }
    }
}

/** A small pill: the percentage, or the state word. Tonal, never a solid fill. */
@Composable
internal fun PlanChip(
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
    tag: String = ""
) {
    Box(
        modifier = modifier
            .widthIn(min = 34.dp)
            .heightIn(min = 18.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(tint.copy(alpha = 0.16f))
            .then(if (tag.isBlank()) Modifier else Modifier.testTag(tag))
            .padding(horizontal = 7.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = tint,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            maxLines = 1
        )
    }
}

/**
 * The one place a plan's health becomes a colour.
 *
 * Calm plans read in the page's own surface ink rather than in a brand accent: a subscription with
 * plenty left should not compete with the active route's green. Warn uses the status amber, danger
 * the status red, and unknown stays quiet — the rule the whole product uses for measurement states.
 */
@Composable
internal fun planToneColor(tone: PlanStatus.Tone): Color {
    val colors = MaterialTheme.colorScheme
    return when (tone) {
        PlanStatus.Tone.CALM -> colors.primary
        PlanStatus.Tone.WARN -> colors.warning
        PlanStatus.Tone.DANGER -> colors.error
        PlanStatus.Tone.UNKNOWN -> colors.onSurfaceVariant
    }
}

/**
 * MARBLE_SERVER_PLAN_CARDS_V260 — the *identity stripe* of a subscription card.
 *
 * The report asked for one thing: every subscription should look a little different from its
 * neighbours. The temptation is a rainbow, which is worse than the sameness it fixes — four bright
 * cards in a sheet whose whole job is picking one. So the difference is a 4dp stripe on the reading
 * edge, drawn from a six-tone wheel keyed by the subscription's own id:
 *
 *  - the stripe is *stable*: the same subscription keeps its tone across restarts, refreshes and
 *    reorders, because it is a function of the id and nothing else ([stripeTone]);
 *  - the card's own ground stays the surface's, so the sheet still reads as one list;
 *  - a *selected* card keeps the primary container, which outranks the stripe — the stripe is
 *    identity, the fill is state, and state wins.
 */
@Composable
internal fun MarbleSourceStripe(
    id: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 44.dp
) {
    val tone = sourceStripeColor(stripeTone(id))
    // Read in the composition: a semantics lambda is not a composable context.
    val stripeState = trx(if (selected) "Selected source" else "Source")
    Column(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(percent = 50))
            // The wash is the card's own ground tinted by the stripe's tone: a solid 4dp bar beside a
            // card is a bookmark, and four bookmarks in one sheet is louder than the four identical
            // cards it replaced.
            .background(if (selected) Color.Transparent else tone.copy(alpha = 0.10f))
            .semantics { stateDescription = stripeState }
            .testTag("server-source-stripe-$id"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!selected) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(percent = 50))
                    .background(tone.copy(alpha = 0.85f))
            )
        }
    }
}

/** One of six stable tones for an id — a pure function, so the wheel never moves under the reader. */
internal fun stripeTone(id: String): Int {
    if (id.isBlank()) return 0
    var hash = 0
    id.forEach { char -> hash = (hash * 31 + char.code) and 0x7FFF_FFFF }
    return hash % STRIPE_TONES
}

internal const val STRIPE_TONES: Int = 6

/** The six tones, in the product's own palette: primary, secondary, tertiary, success, warning, plum. */
@Composable
internal fun sourceStripeColor(index: Int): Color {
    val colors = MaterialTheme.colorScheme
    val tones = marbleExpressiveTones()
    return when (index.mod(STRIPE_TONES)) {
        0 -> colors.primary
        1 -> colors.secondary
        2 -> colors.tertiary
        3 -> colors.success
        4 -> colors.warning
        else -> tones.lavender.badge
    }
}

/**
 * The card's *own* ground, one tonal step away from its neighbours — the second half of the request
 * ("each subscription box should be a slightly different colour").
 *
 * Not a colour per card: four different container colours in a picker is a palette test, not a
 * list. What changes is the *step*: every card alternates between `surfaceContainerLow` and
 * `surfaceContainer` in the order the sheet draws them, so two neighbours always differ by one
 * elevation step, which is what "slightly different" looks like in Material. A subscription also
 * takes its own tone at 4 % — the hue of its stripe, washed into the ground — which is enough to
 * tell four libraries apart at a glance and not enough to look like four themes.
 */
@Composable
internal fun sourceCardGround(
    stripeIndex: Int,
    alternate: Boolean,
    selected: Boolean
): Color {
    val colors = MaterialTheme.colorScheme
    if (selected) return colors.primaryContainer
    val base = if (alternate) colors.surfaceContainer else colors.surfaceContainerLow
    val wash = sourceStripeColor(stripeIndex)
    // The wash is composited by hand rather than by alpha painting, because the swipe reveal behind
    // the card must never show through it: a card is an opaque object, always.
    return lerp(base, wash, 0.055f)
}

/** The subscription a card's plan plate belongs to, or null when the card has no subscription. */
internal fun planOf(subscription: Subscription?, nowMs: Long): PlanStatus.Status =
    subscription?.let { PlanStatus.of(it, nowMs) } ?: PlanStatus.unknown()
