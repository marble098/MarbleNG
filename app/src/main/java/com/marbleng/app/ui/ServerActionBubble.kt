package com.marbleng.app.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.marbleng.app.model.ServerMenuAction

/**
 * MARBLE_SERVER_BUBBLE_V256 — one server's six verbs, in a bubble anchored to its own ⋯.
 *
 * The complaint was exact: a three-dot button opened a **full-height bottom sheet** for actions
 * that are a single tap each. The sheet also carried nine rows, four of which (connect, inspect,
 * rename, duplicate) were already reachable from the row itself, and the whole page dimmed behind it
 * — so the reader lost the list they were working on in order to act on one item in it.
 *
 * This is the bubble instead:
 *
 *  - **anchored** — it opens at the button that asked for it, tail to tail, so the eye never
 *    travels;
 *  - **tonal** — `surfaceContainerHigh` with elevation, clipped to a 20dp squircle, no hairline;
 *  - **dense** — `PaddingValues(vertical = 4.dp)` of content padding and one line per verb, so six
 *    rows are shorter than three of the sheet's;
 *  - **unadorned** — no leading glyphs. Six icons at this size are six pieces of decoration between
 *    the reader and the word they already know; the word carries the row.
 *  - **honest about the last one** — [ServerMenuAction.destructive] paints Delete in the error tone,
 *    and nothing else in the list does.
 *
 * The state is hoisted: the row owns *whether* it is open, this owns *what* it looks like, so the
 * row can close it on a scroll or on a new selection without a second source of truth.
 */
@Composable
internal fun ServerActionBubble(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAction: (ServerMenuAction) -> Unit,
    modifier: Modifier = Modifier,
    actions: List<ServerMenuAction> = ServerMenuAction.order
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .testTag("server-action-bubble"),
        containerColor = colors.surfaceContainerHigh
    ) {
        actions.forEach { action ->
            DropdownMenuItem(
                text = {
                    Text(
                        text = trx(action.title),
                        color = if (action.destructive) colors.error else colors.onSurface,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                onClick = {
                    haptics.performHapticFeedback(
                        if (action.destructive) {
                            HapticFeedbackType.LongPress
                        } else {
                            HapticFeedbackType.TextHandleMove
                        }
                    )
                    onDismiss()
                    onAction(action)
                },
                // Dense on purpose: a 40dp row instead of the platform's 48 makes six verbs
                // shorter than three of the sheet's nine.
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .padding(horizontal = 4.dp)
                    .testTag("server-action-${action.id}")
            )
        }
    }
}
