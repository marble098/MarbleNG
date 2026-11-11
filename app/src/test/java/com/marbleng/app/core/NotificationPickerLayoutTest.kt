package com.marbleng.app.core

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import com.marbleng.app.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class NotificationPickerLayoutTest {
    @Test fun footersInflateInBothDirectionsAndDoNotCompeteWithTelemetry() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (language in NotificationLanguage.entries) {
            val rows = listOf(
                NotificationRoutePicker.PickerRow(NotificationRoutePicker.RowKind.SERVER, "Node", "select:node"),
                NotificationRoutePicker.PickerRow(NotificationRoutePicker.RowKind.CONTROL, "Next", "control:next"),
                NotificationRoutePicker.PickerRow(NotificationRoutePicker.RowKind.CONTROL, "Sources", "control:level"),
                NotificationRoutePicker.PickerRow(NotificationRoutePicker.RowKind.OPEN_APP, "Open", "open:all")
            )
            val shade = TunnelNotificationView.build(
                context, ConnectionNotificationState(status = ConnectionStatus.PROTECTED),
                language, NotificationTheme.default(), "Connected", "Node", "42 ms", "1 KB/s", "0 KB/s",
                picker = rows,
                pickerIntent = { row -> PendingIntent.getActivity(context, row.actionId.hashCode(),
                    Intent("test." + row.actionId).setPackage(context.packageName),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT) }
            )
            val root = shade.expanded.apply(context, FrameLayout(context))
            val picker = root.findViewById<LinearLayout>(R.id.notif_picker)
            assertEquals("one route and two paired footer rows", 3, picker.childCount)
            assertEquals(View.GONE, root.findViewById<View>(R.id.notif_telemetry_row).visibility)
            val footer = picker.getChildAt(1)
            assertEquals(if (language == NotificationLanguage.FA) View.LAYOUT_DIRECTION_RTL
                else View.LAYOUT_DIRECTION_LTR, footer.layoutDirection)
            assertTrue(footer.findViewById<View>(R.id.picker_control_first).hasOnClickListeners())
            assertTrue(footer.findViewById<View>(R.id.picker_control_second).hasOnClickListeners())
            assertEquals(View.INVISIBLE,
                picker.getChildAt(2).findViewById<View>(R.id.picker_control_second).visibility)
            val compact = shade.compact.apply(context, FrameLayout(context))
            assertEquals(View.GONE, compact.findViewById<View>(R.id.notif_picker).visibility)
        }
    }
}
