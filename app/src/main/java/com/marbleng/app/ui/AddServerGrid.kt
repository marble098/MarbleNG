package com.marbleng.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marbleng.app.model.AddServerAction
import com.marbleng.app.model.IntakeKind
import com.marbleng.app.model.IntakeClassifier

/**
 * MARBLE_INTAKE_GRID_V256 — the 2×2.
 *
 * The old sheet was four tabs over four verbs, each tab a list of rows, and the reader had to
 * classify their own clipboard before the app would look at it. This is the same four verbs as four
 * cards: one tap, no tab, and the clipboard card says what it found so nobody has to guess whether
 * copying a `vmess://` link was the right thing to do.
 *
 * Each cell is a `filledCardColors` card at 16dp — quieter than a row list and squarer than the
 * 24dp the rest of the app uses, because a control that is pressed and dismissed in one second
 * should feel like a key rather than a page.
 *
 * The grid is laid out as two rows of two `weight(1f)` cells rather than a `LazyVerticalGrid`: a
 * fixed four-item grid inside a bottom sheet has no scrolling to do, and a lazy grid inside a
 * `ColumnScope` sheet would need a height it does not own.
 */
@Composable
internal fun MarbleAddServerGrid(
    clipboardKind: IntakeKind,
    onAction: (AddServerAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val cells = IntakeClassifier.gridOrder
    Column(
        modifier = modifier.fillMaxWidth().testTag("add-server-grid"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        cells.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { action ->
                    MarbleAddServerCell(
                        action = action,
                        detected = action == AddServerAction.CLIPBOARD &&
                            clipboardKind.isRecognised,
                        clipboardKind = clipboardKind,
                        onClick = { onAction(action) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * One key. The glyph is a tonal chip in the top corner, the verb is the title, and the hint is the
 * one line that tells a reader what the verb will actually consume — including the clipboard card's
 * own reading of what is on it right now.
 */
@Composable
private fun MarbleAddServerCell(
    action: AddServerAction,
    detected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    clipboardKind: IntakeKind = IntakeKind.EMPTY
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val glyph = when (action) {
        AddServerAction.CLIPBOARD -> MarbleGlyph.COPY
        AddServerAction.SCAN_QR -> MarbleGlyph.QR
        AddServerAction.IMPORT_FILE -> MarbleGlyph.FOLDER
        AddServerAction.MANUAL -> MarbleGlyph.PENCIL
    }
    val tint = when (action) {
        AddServerAction.CLIPBOARD -> colors.primary
        AddServerAction.SCAN_QR -> colors.secondary
        AddServerAction.IMPORT_FILE -> colors.tertiary
        AddServerAction.MANUAL -> colors.warning
    }
    val hint = if (action == AddServerAction.CLIPBOARD) {
        IntakeClassifier.clipboardCardHint(clipboardKind)
    } else {
        action.hint
    }

    Card(
        modifier = modifier
            .heightIn(min = 108.dp)
            .testTag("add-server-cell-${action.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.filledCardColors(
            containerColor = if (detected) colors.primaryContainer else colors.surfaceContainerHigh,
            contentColor = if (detected) colors.onPrimaryContainer else colors.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable(role = Role.Button) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                MarbleGlyphIcon(
                    glyph = glyph,
                    color = tint,
                    weight = 1.15f,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = trx(action.title),
                color = if (detected) colors.onPrimaryContainer else colors.onSurface,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = trx(hint),
                color = if (detected) {
                    colors.onPrimaryContainer.copy(alpha = 0.78f)
                } else {
                    colors.onSurfaceVariant
                },
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * The one sentence the sheet owes the reader when it already knows what they copied. A tonal pill,
 * not an outline: the app is *offering* something it found, and an offer is a surface.
 */
@Composable
internal fun MarbleClipboardNotePill(
    kind: IntakeKind,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val note = IntakeClassifier.clipboardNote(kind)
    if (note.isBlank()) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(percent = 50))
            .background(colors.secondaryContainer)
            .clickable(role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("add-server-clipboard-note"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MarbleGlyphIcon(
            glyph = MarbleGlyph.SPARK,
            color = colors.onSecondaryContainer,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = trx(note),
            color = colors.onSecondaryContainer,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
