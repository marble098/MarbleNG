package com.marbleng.app

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.marbleng.app.core.RuntimeDiagnostics
import com.marbleng.app.core.SmartNotifier
import com.marbleng.app.core.XrayManager

/**
 * MARBLE_DURABLE_TUNNEL_INTENT_V133
 *
 * Handles the process kill Android performs when this APK is replaced.
 *
 * The exit history in the attached runtime log contained thirteen
 * `killDueToPackageUpdate` records (`ApplicationExitInfo.REASON_PACKAGE_UPDATE`, reason 16). Every
 * one of them is Android terminating the process to install a new APK — including an update of the
 * Xray/HEV core modules — while a tunnel may be carrying traffic. The process gets no teardown
 * callback, so three classes of state are left behind:
 *
 *  1. a runtime config that a plain `writeText` had already truncated but not yet rewritten, which
 *     the next start would feed to the core as a corrupt document;
 *  2. a live TCP_INFO telemetry file whose timestamps belong to a dead process;
 *  3. no record anywhere that the user had asked to be connected, so the tunnel simply stayed down
 *     until the user noticed and reconnected by hand.
 *
 * JNI state needs no handling: `libmarbleng.so` and its tunnel handles die with the process and are
 * re-created by the fresh one that delivers this broadcast.
 *
 * This receiver therefore (a) discards the stale on-disk state through
 * [XrayManager.discardStaleRuntimeArtifacts], and (b) tells the user the tunnel was interrupted and
 * can be restored. It deliberately does **not** start the foreground service itself: Android 12+
 * forbids a background broadcast receiver from starting a foreground service, and a
 * `ForegroundServiceStartNotAllowedException` here would look like another crash. The tap on the
 * notification is a user-visible action, which is the supported way back into a foreground tunnel —
 * and because the durable tunnel intent survived the kill, [MainActivity] restores the exact last
 * route without the user having to pick a server again.
 */
class MarblePackageReplacedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        runCatching { handle(context) }
    }

    private fun handle(context: Context) {
        val app = context.applicationContext as? MarbleApplication
        val diagnostics = RuntimeDiagnostics(context)
        val removed = runCatching {
            (app?.xray ?: XrayManager(context)).discardStaleRuntimeArtifacts()
        }.getOrDefault(emptyList())

        val wantsTunnel = runCatching {
            (app?.repo ?: return@runCatching false).tunnelIntentActive()
        }.getOrDefault(false)

        diagnostics.event(
            "APP",
            "package-replaced",
            "staleArtifactsRemoved" to removed.joinToString(",").ifBlank { "none" },
            "tunnelIntent" to wantsTunnel
        )

        if (wantsTunnel) notifyInterruptedTunnel(context)
    }

    /**
     * One-tap restore. Opening the activity is a user-visible action, so the subsequent
     * foreground-service start is permitted on every supported Android version.
     */
    private fun notifyInterruptedTunnel(context: Context) {
        runCatching {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val open = PendingIntent.getActivity(
                context,
                RESTORE_REQUEST_CODE,
                Intent(context, MainActivity::class.java)
                    .setAction(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, SmartNotifier.CHANNEL_CONNECTION)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("MarbleNG was updated")
                .setContentText("The tunnel was stopped by the update • tap to restore the last route")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
            manager.notify(NOTIFICATION_ID, notification)
        }
    }

    private companion object {
        /** Distinct from the foreground-service notification id so the two never replace each other. */
        const val NOTIFICATION_ID = 7302
        const val RESTORE_REQUEST_CODE = 73
    }
}
