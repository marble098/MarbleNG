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
        const val NOTIFICATION_ID = 7302
        const val RESTORE_REQUEST_CODE = 73
    }
}
