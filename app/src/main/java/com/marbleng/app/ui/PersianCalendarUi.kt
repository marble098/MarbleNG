@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.marbleng.app.ui

// MARBLE_PERSIAN_CALENDAR_TRUTH_V275 — the calendar the product never had, drawn as two objects.
//
//   * [MarbleTodayLine]   — one quiet line under the Home heading: the Jalali date, today's
//                           occasion (or nothing), and a warm dot on an official holiday.
//   * [PersianCalendarSheet] — the month behind it: a grid that colours *verified* holidays,
//                           marks commemorations without the holiday colour, names the day the
//                           reader taps, and says exactly how far its lunar table reaches.
//
// The design brief the complaint set — «راحتی چشم، عدم شلوغی» — is why the today line is one
// tonal row and not a card, and why the sheet is a grid plus one detail block: the calendar is
// reference material, and reference material is calm.

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.marbleng.app.model.JalaliCalendar
import com.marbleng.app.model.PersianCalendar
import com.marbleng.app.ui.theme.MarbleSpec

/** Whether the reader reads Persian — the one flag the calendar's labels turn on. */
@Composable
private fun calendarPersian(): Boolean = Tr.now.language == MarbleLanguage.FA

/**
 * MARBLE_PERSIAN_CALENDAR_TRUTH_V275 — the Home today line.
 *
 * One row, two tones, no card: the weekday and date on the left, the day's occasion beside it,
 * and a small warm dot only when the day is an official holiday. A day with nothing on it
 * prints nothing extra — the line stays quiet instead of inventing a label, which is how the
 * old calendar ended up declaring holidays that do not exist. Tapping opens the month.
 */
@Composable
fun MarbleTodayLine(
    nowMs: Long,
    onOpenCalendar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val persian = calendarPersian()
    val today = remember(nowMs) { PersianCalendar.today(nowMs) }
    val holidayColor = colors.error
    val dateLabel = PersianCalendar.formatFull(today.year, today.month, today.day, persian)
        .let { if (persian) faDigits(it) else it }
    val occasionLabel = today.occasions.firstOrNull()?.label(persian)?.let { trx(it) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onOpenCalendar)
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .semantics {
                contentDescription = if (occasionLabel != null) {
                    "$dateLabel, $occasionLabel"
                } else {
                    dateLabel
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MarbleGlyphIcon(
            glyph = MarbleGlyph.CALENDAR,
            color = if (today.holiday) holidayColor else colors.onSurfaceVariant,
            modifier = Modifier.size(17.dp)
        )
        Text(
            text = dateLabel,
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        if (today.holiday) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(holidayColor)
            )
        }
        if (occasionLabel != null) {
            Text(
                text = "· $occasionLabel",
                color = if (today.holiday) holidayColor else colors.primary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
        }
    }
}

/** One month cell: the number, the occasion dot, and the holiday colour when it is earned. */
@Composable
private fun CalendarDayCell(
    day: Int,
    isToday: Boolean,
    isHoliday: Boolean,
    hasOccasion: Boolean,
    isSelected: Boolean,
    persian: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val resting by animateColorAsState(
        targetValue = when {
            isToday -> colors.primary
            isHoliday -> colors.error
            else -> colors.onSurfaceVariant
        },
        animationSpec = tween(240),
        label = "calendar-day-resting"
    )
    val background by animateColorAsState(
        targetValue = when {
            isSelected -> colors.primaryContainer
            else -> Color.Transparent
        },
        animationSpec = tween(240),
        label = "calendar-day-background"
    )
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (persian) faDigits("$day") else "$day",
                color = resting,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(if (hasOccasion) resting else Color.Transparent)
            )
        }
    }
}

/**
 * MARBLE_PERSIAN_CALENDAR_TRUTH_V275 — the month, as a sheet.
 *
 * The grid is the truth this file exists for: a day is coloured as a holiday only when
 * [PersianCalendar.isOfficialHoliday] says so — the verified table, the solar-fixed rules, or a
 * Friday — and a commemoration such as 13 Aban keeps its dot but never the holiday colour.
 * Beyond the table's last year the sheet says so instead of guessing the moon.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PersianCalendarSheet(
    nowMs: Long,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val persian = calendarPersian()
    val today = remember(nowMs) { PersianCalendar.today(nowMs) }

    var shownYear by remember { mutableStateOf(today.year) }
    var shownMonth by remember { mutableStateOf(today.month) }
    var selected by remember { mutableStateOf(today.packedDate) }

    val monthLength = JalaliCalendar.daysInMonth(shownYear, shownMonth)
    val firstWeekIndex = PersianCalendar.weekIndexOf(shownYear, shownMonth, 1)
    val upcoming = remember(today.year, today.month, today.day) {
        PersianCalendar.upcoming(today.year, today.month, today.day, limit = 3)
    }

    fun stepMonth(delta: Int) {
        var month = shownMonth + delta
        var year = shownYear
        if (month < 1) {
            month = 12
            year -= 1
        } else if (month > 12) {
            month = 1
            year += 1
        }
        if (year < 1300 || year > 1500) return
        shownYear = year
        shownMonth = month
        selected = -1
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = MarbleSpec.SCREEN_HORIZONTAL_PADDING_DP.dp,
                    end = MarbleSpec.SCREEN_HORIZONTAL_PADDING_DP.dp,
                    bottom = 28.dp
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── The month header: the name, and one step either way. The Row mirrors in RTL,
            // so the *first* child sits on the right for a Persian reader: there it pages back
            // (an RTL calendar flips the way the language reads) and on the left for English.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val backGlyph = if (persian) MarbleGlyph.CHEVRON else MarbleGlyph.CHEVRON_LEFT
                val forwardGlyph = if (persian) MarbleGlyph.CHEVRON_LEFT else MarbleGlyph.CHEVRON
                MarbleExpressiveIconButton(
                    glyph = backGlyph,
                    motif = MarbleExpressiveMotif.ARCH,
                    containerColor = colors.surfaceContainer,
                    contentColor = colors.onSurfaceVariant,
                    contentDescription = trx("Previous month"),
                    onClick = { stepMonth(-1) }
                )
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = PersianCalendar.formatMonthYear(shownYear, shownMonth, persian)
                            .let { if (persian) faDigits(it) else it },
                        color = colors.onSurface,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = trx(
                            if (PersianCalendar.holidayCoverage(shownYear)) {
                                "Official holidays verified " +
                                    "${PersianCalendar.TABLE_FIRST_YEAR}–${PersianCalendar.TABLE_LAST_YEAR}"
                            } else {
                                "Lunar holidays beyond ${PersianCalendar.TABLE_LAST_YEAR} are not guessed"
                            }
                        ).let { if (persian) faDigits(it) else it },
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                MarbleExpressiveIconButton(
                    glyph = forwardGlyph,
                    motif = MarbleExpressiveMotif.ARCH,
                    containerColor = colors.surfaceContainer,
                    contentColor = colors.onSurfaceVariant,
                    contentDescription = trx("Next month"),
                    onClick = { stepMonth(1) }
                )
            }

            // ── The weekday rail. The Iranian week starts on Saturday; Friday closes it.
            Row(modifier = Modifier.fillMaxWidth()) {
                (0..6).forEach { index ->
                    Text(
                        text = if (persian) {
                            // The one-letter rail a Persian calendar prints: ش ی د س چ پ ج.
                            PersianCalendar.weekdayName(index, persian = true).take(1)
                        } else {
                            PersianCalendar.weekdayName(index, persian = false).take(3)
                        },
                        color = if (index == PersianCalendar.FRIDAY_INDEX) colors.error else colors.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ── The grid. Leading empties keep day 1 under its weekday.
            val gridCells: List<CalendarGridCell> =
                (0 until firstWeekIndex).map { CalendarGridCell.Empty } +
                    (1..monthLength).map { CalendarGridCell.Day(it) }

            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(gridCells) { cell ->
                    when (cell) {
                        is CalendarGridCell.Empty -> Spacer(modifier = Modifier.size(44.dp))
                        is CalendarGridCell.Day -> {
                            val holiday = PersianCalendar.isOfficialHoliday(shownYear, shownMonth, cell.day)
                            val occasions = PersianCalendar.occasionsOn(shownYear, shownMonth, cell.day)
                            CalendarDayCell(
                                day = cell.day,
                                isToday = shownYear == today.year && shownMonth == today.month &&
                                    cell.day == today.day,
                                isHoliday = holiday,
                                hasOccasion = occasions.isNotEmpty(),
                                isSelected = selected == shownMonth * 100 + cell.day,
                                persian = persian,
                                onClick = { selected = shownMonth * 100 + cell.day }
                            )
                        }
                    }
                }
            }

            // ── The selected day, named.
            val selectedDay = selected.takeIf { it > 0 }?.let { it % 100 }
            if (selectedDay != null && selectedDay in 1..monthLength) {
                val occasions = PersianCalendar.occasionsOn(shownYear, shownMonth, selectedDay)
                val holiday = PersianCalendar.isOfficialHoliday(shownYear, shownMonth, selectedDay)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(MarbleSpec.CARD_CORNER_DP.dp))
                        .background(colors.surfaceContainerLow)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = PersianCalendar.formatFull(shownYear, shownMonth, selectedDay, persian)
                            .let { if (persian) faDigits(it) else it } +
                            "  ·  " + PersianCalendar.gregorianLabel(shownYear, shownMonth, selectedDay),
                        color = colors.onSurface,
                        style = MaterialTheme.typography.titleSmall
                    )
                    if (occasions.isEmpty()) {
                        Text(
                            text = trx("No occasion on this day"),
                            color = colors.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        occasions.forEach { occasion ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (occasion.official) colors.error else colors.primary
                                        )
                                )
                                Text(
                                    text = trx(occasion.label(persian)),
                                    color = if (occasion.official) colors.error else colors.onSurface,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                        Text(
                            text = trx(
                                if (holiday) "Official holiday" else "Commemoration — not an official holiday"
                            ),
                            color = colors.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            // ── What is coming.
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = trx("Coming up"),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium
                )
                upcoming.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = PersianCalendar.formatMonthDay(item.month, item.day, persian)
                                .let { if (persian) faDigits(it) else it },
                            color = if (item.holiday) colors.error else colors.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.width(72.dp)
                        )
                        Text(
                            text = trx(item.occasion.label(persian)),
                            color = colors.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = trx(
                                if (item.daysAway == 1) "in 1 day" else "in ${item.daysAway} days"
                            ).let { if (persian) faDigits(it) else it },
                            color = colors.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

private sealed class CalendarGridCell {
    data object Empty : CalendarGridCell()
    data class Day(val day: Int) : CalendarGridCell()
}
