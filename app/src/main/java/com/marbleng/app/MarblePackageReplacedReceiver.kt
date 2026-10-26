package com.marbleng.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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

    /**
     * MARBLE_NOTIFICATION_COUNCIL_V231 — an update that stopped the tunnel is a recovery event, so
     * it belongs on the alert channel with a Restore action, localized, grouped with the other
     * alerts. It used to be posted on the silent ongoing channel, in English, with no action and
     * an importance that could neither sound nor badge.
     */
    private fun notifyInterruptedTunnel(context: Context) {
        runCatching {
            val language = runCatching {
                (context.applicationContext as? MarbleApplication)?.repo?.settings?.appLanguage
            }.getOrNull() ?: ""
            SmartNotifier(context) { language }.restoreNotice()
        }
    }
}
