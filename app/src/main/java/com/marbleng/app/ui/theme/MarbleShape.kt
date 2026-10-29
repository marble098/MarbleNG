package com.marbleng.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// MARBLE_M3_EXPRESSIVE_V240 — the expressive shape set.
//
// Large corner radii everywhere the research points: cards and sheets open wide, controls
// stay friendly, and nothing drops below 8dp. State-change morphing (press squish on the
// hero button, corner-radius springs on toggles and the FAB menu) animates *between* these
// rungs with the motion scheme's spatial springs — see MarbleMotion and the components.

val MarbleShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
    largeIncreased = RoundedCornerShape(32.dp),
    extraLargeIncreased = RoundedCornerShape(40.dp),
    extraExtraLarge = RoundedCornerShape(48.dp)
)

/**
 * Card and list-row containers. MARBLE_EXPRESSIVE_SHELL_V241 — 24 dp, the lower rung of the
 * 24–32 dp band the expressive surfaces sit in; nothing in the app is outlined any more, so
 * the corner radius is what makes a group read as one object.
 */
val MarbleCardShape = RoundedCornerShape(MarbleSpec.CARD_CORNER_DP.dp)

/** A group that contains several rows: the wider rung of the same band. */
val MarbleGroupShape = RoundedCornerShape(MarbleSpec.GROUP_CORNER_DP.dp)

/** Row leading-icon chips. */
val MarbleRowIconShape = RoundedCornerShape(14.dp)

/** Buttons, fields and segmented controls. */
val MarbleControlShape = RoundedCornerShape(16.dp)

/** Search and pill inputs. */
val MarbleFieldShape = RoundedCornerShape(24.dp)

/** Bottom sheets: wide top rounding, square bottom edge. */
val MarbleSheetShape = RoundedCornerShape(
    topStart = MarbleSpec.GROUP_CORNER_DP.dp,
    topEnd = MarbleSpec.GROUP_CORNER_DP.dp
)
