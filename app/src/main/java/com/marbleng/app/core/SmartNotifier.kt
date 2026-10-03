package com.marbleng.app.core

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.marbleng.app.MainActivity
import com.marbleng.app.R
import com.marbleng.app.model.AppSettings
import com.marbleng.app.vpn.MarbleVpnService
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

enum class SmartNotificationKind {
    CONNECTION,
    RECOVERY,
    PRIVACY,
    NETWORK,
    SUBSCRIPTION,
    CORE,
    TEST
}

/**
 * Central notification policy for MarbleNG.
 *
 * The running VPN/proxy foreground status is deliberately separate from optional event alerts.
 * Optional alerts are permission-aware, category-aware and rate-limited so route probes cannot
 * spam the notification shade during a bad underlay.
 */
class SmartNotifier(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    /**
     * MARBLE_NOTIFICATION_CHANNELS_ONCE_V206 — the channel table is a constant of this build.
     *
     * It was rebuilt on *every* settings write. [AppRepository.updateSettings] ends with a call
     * to this method, and `updateSettings` is what every control in Settings calls — so typing
     * `100-200` into the Fragment length field performed, on the main thread, nine times in
     * nine hundred milliseconds: three channel constructions, one group creation and one
     * `createNotificationChannels` binder call into `system_server`, each of which makes the
     * system process write its own XML. Dragging the Mux concurrency slider did the same at
     * frame rate. None of it could ever have changed anything: the channel set is fixed at
     * compile time, and re-creating an existing channel with identical parameters is a no-op
     * the framework still has to parse, persist and re-sort.
     *
     * The flag is set only on success, so a call that threw (a wedged NotificationManager, a
     * device still booting) is retried instead of being suppressed for the rest of the process.
     */
    private val channelsEnsured = java.util.concurrent.atomic.AtomicBoolean(false)

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < 26) return
        if (channelsEnsured.get()) return
        runCatching {
            manager.createNotificationChannelGroup(
                NotificationChannelGroup(GROUP_ID, "MarbleNG")
            )
            val connection = NotificationChannel(
                CHANNEL_CONNECTION,
                "Connection status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent VPN/proxy connection, route and live status"
                group = GROUP_ID
                setShowBadge(false)
            }
            val smart = NotificationChannel(
                CHANNEL_SMART,
                "Security & recovery",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Kill-switch, recovery, failover and optional connection events"
                group = GROUP_ID
            }
            val updates = NotificationChannel(
                CHANNEL_UPDATES,
                "Subscriptions & core updates",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Subscription refresh results and core update availability"
                group = GROUP_ID
            }
            manager.createNotificationChannels(listOf(connection, smart, updates))
            channelsEnsured.set(true)
        }
    }

    fun optionalPermissionGranted(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun connectionNotification(title: String, text: String, ongoing: Boolean): Notification {
        ensureChannels()
        return NotificationCompat.Builder(context, CHANNEL_CONNECTION)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openAppIntent())
            .addAction(R.drawable.ic_notification, "Stop", stopServiceIntent())
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun updateConnection(id: Int, title: String, text: String, ongoing: Boolean): Boolean {
        val note = connectionNotification(title, text, ongoing)
        return runCatching {
            manager.notify(id, note)
            true
        }.getOrDefault(false)
    }

    fun alert(
        kind: SmartNotificationKind,
        key: String,
        title: String,
        text: String,
        settings: AppSettings,
        minIntervalOverrideMs: Long? = null
    ): Boolean {
        if (!settings.smartNotificationsEnabled || !kindEnabled(kind, settings)) return false
        if (!optionalPermissionGranted()) return false
        ensureChannels()

        val now = System.currentTimeMillis()
        val eventKey = "${kind.name}:$key"
        val cooldown = (
            minIntervalOverrideMs
                ?: settings.notificationCooldownSec.coerceIn(5, 300) * 1000L
            ).coerceAtLeast(0L)
        val previous = lastEventAt[eventKey] ?: 0L
        if (cooldown > 0L && now - previous < cooldown) return false
        lastEventAt[eventKey] = now

        val channel = when (kind) {
            SmartNotificationKind.SUBSCRIPTION,
            SmartNotificationKind.CORE -> CHANNEL_UPDATES
            else -> CHANNEL_SMART
        }
        val priority = when (kind) {
            SmartNotificationKind.PRIVACY,
            SmartNotificationKind.RECOVERY,
            SmartNotificationKind.TEST -> NotificationCompat.PRIORITY_DEFAULT
            else -> NotificationCompat.PRIORITY_LOW
        }
        val safeHash = eventKey.hashCode().ushr(1)
        val id = OPTIONAL_ID_BASE + (safeHash % OPTIONAL_ID_RANGE)
        val note = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title.take(80))
            .setContentText(text.take(240))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.take(1000)))
            .setContentIntent(openAppIntent())
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(priority)
            .setAutoCancel(true)
            .build()

        return runCatching {
            manager.notify(id, note)
            optionalIds += id
            true
        }.getOrDefault(false)
    }

    fun cancelOptional() {
        optionalIds.toList().forEach { id -> runCatching { manager.cancel(id) } }
        optionalIds.clear()
        lastEventAt.clear()
    }

    private fun kindEnabled(kind: SmartNotificationKind, s: AppSettings): Boolean = when (kind) {
        SmartNotificationKind.CONNECTION -> s.notifyConnectionEvents
        SmartNotificationKind.RECOVERY -> s.notifyRecoveryEvents
        SmartNotificationKind.PRIVACY -> s.notifyPrivacyWarnings
        SmartNotificationKind.NETWORK -> s.notifyNetworkChanges
        SmartNotificationKind.SUBSCRIPTION -> s.notifySubscriptionEvents
        SmartNotificationKind.CORE -> s.notifyCoreUpdates
        SmartNotificationKind.TEST -> true
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            7302,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun stopServiceIntent(): PendingIntent {
        val intent = Intent(context, MarbleVpnService::class.java)
            .setAction(MarbleVpnService.ACTION_STOP)
        return PendingIntent.getService(
            context,
            7303,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val CHANNEL_CONNECTION = "marbleng-vpn"
        const val CHANNEL_SMART = "marbleng-smart"
        const val CHANNEL_UPDATES = "marbleng-updates"
        private const val GROUP_ID = "marbleng-notifications"
        private const val OPTIONAL_ID_BASE = 7600
        private const val OPTIONAL_ID_RANGE = 1200
        private val lastEventAt = ConcurrentHashMap<String, Long>()
        private val optionalIds = ConcurrentHashMap.newKeySet<Int>()

        fun formatRate(bytesPerSecond: Long): String = when {
            bytesPerSecond >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB/s", bytesPerSecond / (1024.0 * 1024.0))
            bytesPerSecond >= 1024L -> String.format(Locale.US, "%.0f KB/s", bytesPerSecond / 1024.0)
            else -> "$bytesPerSecond B/s"
        }
    }
}
