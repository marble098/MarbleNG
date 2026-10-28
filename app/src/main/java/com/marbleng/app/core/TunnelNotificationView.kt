package com.marbleng.app.core

import android.app.PendingIntent
import android.content.Context
import android.content.res.Configuration
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.marbleng.app.R

/**
 * MARBLE_SHADE_ATELIER_V234 — the ongoing tunnel item, drawn.
 *
 * Until V234 the shade was a `BigTextStyle` paragraph: `Protected` on one line, then a sentence of
 * route, rates and Iran-mode facts glued together with `•`. It was correct and it was dense — the
 * one notification a user sees twenty times a day had no hierarchy at all. This builder draws the
 * same state into a purpose-built view: a badge that is a different drawing per state, the state as
 * the title, `route · source` beneath it, the platform chronometer on the reading edge, and a rail
 * of measurement chips that exists only when the numbers are real.
 *
 * Three deliberate constraints:
 *
 *  - **Only RemoteViews-safe views** — LinearLayout, ImageView, TextView, Chronometer, ProgressBar.
 *    A view SystemUI cannot inflate is a blank notification, so nothing exotic is used.
 *  - **No theme attributes.** The app theme is a fixed light Material theme while the shade follows
 *    the phone's light/dark choice, so `?attr/colorSurface` would be wrong half the time. The text
 *    colours are set here from the configuration; the chip wash is a translucent brand tone that
 *    works on both surfaces.
 *  - **The RTL file, not the locale.** The app can be Persian on an English phone, a state no
 *    resource qualifier can express; `notification_tunnel_rtl.xml` is the same box with
 *    `layoutDirection="rtl"`, chosen here by the resolved [NotificationLanguage].
 *
 * `NotificationShadeDesign` owns every *when* decision (badge, clock, rail, telemetry) and is
 * host-JVM tested; this file only paints.
 */
internal data class TunnelShadeViews(
    val compact: RemoteViews,
    val expanded: RemoteViews
)

internal object TunnelNotificationView {

    fun build(
        context: Context,
        state: ConnectionNotificationState,
        language: NotificationLanguage,
        title: String,
        routeLine: String,
        ping: String,
        down: String,
        up: String,
        picker: List<NotificationRoutePicker.PickerRow> = emptyList(),
        pickerIntent: ((NotificationRoutePicker.PickerRow) -> PendingIntent?)? = null
    ): TunnelShadeViews {
        val layout = if (language == NotificationLanguage.FA) {
            R.layout.notification_tunnel_rtl
        } else {
            R.layout.notification_tunnel
        }
        val compact = RemoteViews(context.packageName, layout)
        val expanded = RemoteViews(context.packageName, layout)

        paint(context, compact, state, title, routeLine, ping, down, up, big = false)
        paint(context, expanded, state, title, routeLine, ping, down, up, big = true)
        // MARBLE_NOTIFICATION_PICKER_V238 — the picker lives only in the expanded item: the
        // collapsed view keeps one row of state, the opened shade offers the full route control.
        if (picker.isNotEmpty() && pickerIntent != null) {
            attachPicker(context, expanded, language, picker, pickerIntent)
        } else {
            expanded.setViewVisibility(R.id.notif_picker, View.GONE)
            expanded.removeAllViews(R.id.notif_picker)
        }
        return TunnelShadeViews(compact = compact, expanded = expanded)
    }

    /**
     * The picker rows are child RemoteViews added one by one — RemoteViews has no adapter, so
     * the list is explicit, bounded by [NotificationRoutePicker.DEFAULT_CAP], and every row is
     * one of the two layouts `SystemUI` can inflate without a custom class.
     */
    private fun attachPicker(
        context: Context,
        views: RemoteViews,
        language: NotificationLanguage,
        rows: List<NotificationRoutePicker.PickerRow>,
        pickerIntent: (NotificationRoutePicker.PickerRow) -> PendingIntent?
    ) {
        val night = isNight(context)
        val sectionColor = if (night) SECTION_NIGHT else SECTION_DAY
        val rowColor = if (night) TITLE_NIGHT else TITLE_DAY
        val activeColor = if (night) ACTIVE_NIGHT else ACTIVE_DAY

        views.removeAllViews(R.id.notif_picker)
        if (rows.isEmpty()) {
            views.setViewVisibility(R.id.notif_picker, View.GONE)
            return
        }
        views.setViewVisibility(R.id.notif_picker, View.VISIBLE)
        val rowLayout = if (language == NotificationLanguage.FA) {
            R.layout.notification_picker_row_rtl
        } else {
            R.layout.notification_picker_row
        }
        for (row in rows) {
            val child = RemoteViews(context.packageName, rowLayout)
            val label = NotificationCopy.ellipsize(
                NotificationRoutePicker.rowLabel(row.label),
                NotificationCopy.TEXT_MAX
            )
            child.setTextViewText(R.id.picker_label, label)
            val pending = pickerIntent(row)
            when (row.kind) {
                NotificationRoutePicker.RowKind.SECTION -> {
                    child.setTextColor(R.id.picker_label, sectionColor)
                    child.setViewVisibility(R.id.picker_dot, View.GONE)
                }
                NotificationRoutePicker.RowKind.OPEN_APP -> {
                    child.setTextColor(R.id.picker_label, sectionColor)
                    child.setViewVisibility(R.id.picker_dot, View.GONE)
                    pending?.let { child.setOnClickPendingIntent(R.id.picker_row, it) }
                }
                else -> {
                    child.setTextColor(R.id.picker_label, if (row.active) activeColor else rowColor)
                    child.setViewVisibility(
                        R.id.picker_dot,
                        if (row.active) View.VISIBLE else View.INVISIBLE
                    )
                    child.setInt(R.id.picker_dot, "setColorFilter", activeColor)
                    pending?.let { child.setOnClickPendingIntent(R.id.picker_row, it) }
                }
            }
            views.addView(R.id.notif_picker, child)
        }
    }

    private fun paint(
        context: Context,
        views: RemoteViews,
        state: ConnectionNotificationState,
        title: String,
        routeLine: String,
        ping: String,
        down: String,
        up: String,
        big: Boolean
    ) {
        val night = isNight(context)
        val titleColor = if (night) TITLE_NIGHT else TITLE_DAY
        val secondaryColor = if (night) SECONDARY_NIGHT else SECONDARY_DAY
        val chipColor = if (night) CHIP_TEXT_NIGHT else CHIP_TEXT_DAY

        views.setImageViewResource(R.id.notif_badge, badgeDrawable(state.status))
        views.setTextViewText(R.id.notif_title, title)
        views.setTextColor(R.id.notif_title, titleColor)
        views.setTextViewText(R.id.notif_route, routeLine)
        views.setTextColor(R.id.notif_route, secondaryColor)

        // The clock: a real chronometer when a session is counting, hidden otherwise. The badge and
        // the state word already say "connecting"; a clock that counts during a handshake is a lie.
        val clock = NotificationShadeDesign.clockVisible(state.status, state.showTimer) &&
            state.sessionStartedAtMs > 0L
        views.setViewVisibility(R.id.notif_clock, if (clock) View.VISIBLE else View.GONE)
        if (clock) {
            views.setChronometer(
                R.id.notif_clock,
                NotificationShadeDesign.chronometerBase(
                    state.sessionStartedAtMs,
                    System.currentTimeMillis(),
                    SystemClock.elapsedRealtime()
                ),
                null,
                true
            )
        }

        val progress = NotificationShadeDesign.progressVisible(state.status)
        views.setViewVisibility(R.id.notif_progress, if (progress) View.VISIBLE else View.GONE)

        val telemetry = big && NotificationShadeDesign.telemetryVisible(state.status, state.showTelemetry)
        views.setViewVisibility(
            R.id.notif_telemetry_row,
            if (telemetry) View.VISIBLE else View.GONE
        )
        if (telemetry) {
            val hasPing = ping.isNotBlank() && !ping.startsWith("—")
            views.setViewVisibility(R.id.notif_ping, if (hasPing) View.VISIBLE else View.GONE)
            if (hasPing) {
                views.setTextViewText(R.id.notif_ping, ping)
                views.setTextColor(R.id.notif_ping, chipColor)
            }
            views.setTextViewText(R.id.notif_down, down)
            views.setTextColor(R.id.notif_down, chipColor)
            views.setTextViewText(R.id.notif_up, up)
            views.setTextColor(R.id.notif_up, chipColor)
        }
    }

    /** The badge is the drawing for the state, not one icon recoloured four times. */
    private fun badgeDrawable(status: ConnectionStatus): Int =
        when (NotificationShadeDesign.badge(status)) {
            ShadeBadge.PROTECTED -> R.drawable.notif_badge_protected
            ShadeBadge.CONNECTING -> R.drawable.notif_badge_connecting
            ShadeBadge.RECOVERING -> R.drawable.notif_badge_recovering
            ShadeBadge.BLOCKED -> R.drawable.notif_badge_blocked
            ShadeBadge.IDLE -> R.drawable.notif_badge_idle
        }

    /**
     * The shade follows the **phone**, not the app theme: `Theme.MarbleNG` is a fixed light
     * Material theme, so asking it would paint dark text on a dark notification surface.
     */
    private fun isNight(context: Context): Boolean {
        val mode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return mode == Configuration.UI_MODE_NIGHT_YES
    }

    private const val TITLE_DAY = 0xFF11181F.toInt()
    private const val TITLE_NIGHT = 0xFFF2F5F8.toInt()
    private const val SECONDARY_DAY = 0x9911181F.toInt()
    private const val SECONDARY_NIGHT = 0xB8F2F5F8.toInt()
    private const val CHIP_TEXT_DAY = 0xFF0B5BC4.toInt()
    private const val CHIP_TEXT_NIGHT = 0xFF8FC0FF.toInt()
    private const val SECTION_DAY = 0x8A11181F.toInt()
    private const val SECTION_NIGHT = 0x8AF2F5F8.toInt()
    private const val ACTIVE_DAY = 0xFF0B5BC4.toInt()
    private const val ACTIVE_NIGHT = 0xFF8FC0FF.toInt()
}
